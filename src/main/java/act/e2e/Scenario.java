package act.e2e;

/*-
 * #%L
 * ACT E2E Plugin
 * %%
 * Copyright (C) 2018 ActFramework
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import static act.e2e.E2E.Status.PENDING;
import static act.e2e.util.ErrorMessage.error;
import static act.e2e.util.ErrorMessage.errorIf;
import static act.e2e.util.ErrorMessage.errorIfNot;
import static org.osgl.http.H.Header.Names.ACCEPT;
import static org.osgl.http.H.Header.Names.X_REQUESTED_WITH;

import act.Act;
import act.app.App;
import act.e2e.E2E.Status;
import act.e2e.req_modifier.RequestModifier;
import act.e2e.util.CookieStore;
import act.e2e.util.JSONTraverser;
import act.e2e.util.RequestTemplateManager;
import act.e2e.util.ScenarioManager;
import act.e2e.verifier.Verifier;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.osgl.$;
import org.osgl.exception.UnexpectedException;
import org.osgl.http.H;
import org.osgl.logging.Logger;
import org.osgl.util.Codec;
import org.osgl.util.E;
import org.osgl.util.IO;
import org.osgl.util.S;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

public class Scenario implements ScenarioPart {

    private static final Logger LOGGER = E2E.LOGGER;

    private static final ThreadLocal<Scenario> current = new ThreadLocal<>();

    private static final RequestBody EMPTY_BODY = RequestBody.create(null, "");

    private class RequestBuilder {

        private Request.Builder builder;

        RequestBuilder(RequestSpec requestSpec) {
            builder = new Request.Builder();
            if ($.bool(requestSpec.json)) {
                builder.addHeader(ACCEPT, H.Format.JSON.contentType());
            }
            if ($.bool(requestSpec.ajax)) {
                builder.addHeader(X_REQUESTED_WITH, "XMLHttpRequest");
            }
            for (RequestModifier modifier : requestSpec.modifiers) {
                modifier.modifyRequest(builder);
            }
            for (Map.Entry<String, Object> entry : requestSpec.headers.entrySet()) {
                String headerName = entry.getKey();
                String headerVal = S.string(entry.getValue());
                if (headerVal.startsWith("last:") || headerVal.startsWith("last|")) {
                    String payload = headerVal.substring(5);
                    if (S.blank(payload)) {
                        payload = headerName;
                    }
                    headerVal = S.string(lastHeaders.get().get(payload));
                }
                builder.addHeader(headerName, headerVal);
            }
            String url = S.concat("http://localhost:", port, S.ensure(processStringSubstitution(requestSpec.url)).startWith("/"));
            boolean hasParams = !requestSpec.params.isEmpty();
            if (hasParams) {
                processParamSubstitution(requestSpec.params);
            }
            switch (requestSpec.method) {
                case GET:
                case HEAD:
                    if (hasParams) {
                        S.Buffer buf = S.buffer(url);
                        if (!url.contains("?")) {
                            buf.a("?__nil__=nil");
                        }
                        for (Map.Entry<String, Object> entry : requestSpec.params.entrySet()) {
                            String paramName = Codec.encodeUrl(entry.getKey());
                            String paramVal = Codec.encodeUrl(S.string(entry.getValue()));
                            buf.a("&").a(paramName).a("=").a(paramVal);
                        }
                        url = buf.toString();
                    }
                case DELETE:
                    builder.method(requestSpec.method.name(), null);
                    break;
                case POST:
                case PUT:
                case PATCH:
                    RequestBody body = EMPTY_BODY;
                    String jsonBody = verifyJsonBody(requestSpec.jsonBody);
                    if (S.notBlank(requestSpec.jsonBody)) {
                        body = RequestBody.create(MediaType.parse("application/json"), jsonBody);
                    } else if (hasParams) {
                        MultipartBody.Builder formBuilder = new MultipartBody.Builder();
                        for (Map.Entry<String, Object> entry : requestSpec.params.entrySet()) {
                            formBuilder.addFormDataPart(entry.getKey(), S.string(entry.getValue()));
                        }
                        body = formBuilder.build();
                    }
                    builder.method((requestSpec.method.name()), body);
                    break;
                default:
                    throw E.unexpected("HTTP method not supported: " + requestSpec.method);
            }
            builder.url(url);
        }

        private void processParamSubstitution(Map<String, Object> params) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                Object val = entry.getValue();
                if (val instanceof String) {
                    String sVal = (String) val;
                    if (sVal.startsWith("last:") || sVal.startsWith("last|")) {
                        String ref = sVal.substring(5);
                        entry.setValue(getLastVal(ref));
                    } else if (sVal.contains("${")) {
                        sVal = processStringSubstitution(sVal);
                        entry.setValue(sVal);
                    }
                }
            }
        }

        private String processStringSubstitution(String s) {
            int n = s.indexOf("${");
            if (n < 0) {
                return s;
            }
            int a = 0;
            int z = n;
            S.Buffer buf = S.buffer();
            while (true) {
                buf.append(s.substring(a, z));
                n = s.indexOf("}", z);
                a = n;
                E.illegalArgumentIf(n < -1, "Invalid string: " + s);
                String part = s.substring(z + 2, a);
                String key = part;
                String payload = "";
                if (part.contains(":")) {
                    S.Binary binary = S.binarySplit(part, ':');
                    key = binary.first();
                    payload = binary.second();
                }
                buf.append(getVal(key, payload));
                n = s.indexOf("${", a);
                if (n < 0) {
                    buf.append(s.substring(a + 1));
                    return buf.toString();
                }
                z = n;
            }
        }

        Request build() {
            return builder.build();
        }

        private String verifyJsonBody(String jsonBody) {
            final String origin = jsonBody;
            if (S.blank(jsonBody)) {
                return "";
            }
            if (jsonBody.startsWith("resource:")) {
                jsonBody = S.ensure(jsonBody.substring(9).trim()).startWith("/");
                URL url = Act.getResource(jsonBody);
                E.unexpectedIf(null == url, "Cannot find JSON body: " + origin);
                jsonBody = IO.read(url).toString();
            }
            try {
                JSON.parse(jsonBody);
            } catch (Exception e) {
                E.unexpected(e, "Invalid JSON body: " + origin);
            }
            return jsonBody;
        }

    }

    private int port = 5460;

    private OkHttpClient http;

    private CookieStore cookieStore;

    private App app;


    public String name;
    public String description;
    public List<String> fixtures = new ArrayList<>();
    public List<String> depends = new ArrayList<>();
    public List<Interaction> interactions = new ArrayList<>();
    public Status status = PENDING;
    public String errorMessage;
    public Throwable cause;

    $.Var<Object> lastData = $.var();
    $.Var<Headers> lastHeaders = $.var();

    ScenarioManager scenarioManager;
    RequestTemplateManager requestTemplateManager;

    private Map<String, Object> cache = new HashMap<>();

    public Scenario() {
        app = Act.app();
        if (null != app) {
            port = app.config().httpPort();
        }
    }

    public String title() {
        return S.blank(description) ? name : description;
    }

    public void cache(String name, Object payload) {
        cache.put(name, payload);
    }

    public Object cached(String name) {
        return cache.get(name);
    }

    public Status statusOf(Interaction interaction) {
        return interaction.status;
    }

    public String errorMessageOf(Interaction interaction) {
        return interaction.errorMessage;
    }

    @Override
    public void validate(Scenario scenario) throws UnexpectedException {
        errorIf(interactions.isEmpty(), "No interactions");
        errorIf(S.blank(name), "Scenario name not defined");
        for (Interaction interaction : interactions) {
            interaction.validate(scenario);
        }
    }

    public void start(ScenarioManager scenarioManager, RequestTemplateManager requestTemplateManager) {
        this.scenarioManager = $.requireNotNull(scenarioManager);
        this.requestTemplateManager = $.requireNotNull(requestTemplateManager);
        this.status = PENDING;
        current.set(this);
        validate(this);
        prepareHttp();
        boolean pass = reset() && run();
        this.status = Status.of(pass);
    }

    public void clearSession() {
        cookieStore().clear();
    }

    public boolean clearFixtures() {
        return verify(RequestSpec.RS_CLEAR_FIXTURE, "clearing fixtures");
    }

    public String causeStackTrace() {
        return null == cause ? null: E.stackTrace(cause);
    }

    void resolveRequest(RequestSpec req) {
        req.resolveParent(requestTemplateManager);
    }

    Response sendRequest(RequestSpec req) throws IOException {
        Request httpRequest = new RequestBuilder(req).build();
        return http.newCall(httpRequest).execute();
    }

    private boolean createFixtures() {
        if (fixtures.isEmpty()) {
            return true;
        }
        RequestSpec req = RequestSpec.loadFixtures(fixtures);
        return verify(req, "creating fixtures");
    }

    private boolean verify(RequestSpec req, String operation) {
        boolean pass = true;
        try {
            Response resp = sendRequest(req);
            if (!resp.isSuccessful()) {
                pass = false;
                errorMessage = "Fixtures loading failure";
            }
            return pass;
        } catch (IOException e) {
            errorMessage = "Error " + operation;
            LOGGER.error(e, errorMessage);
            return false;
        }
    }

    private void prepareHttp() {
        http = new OkHttpClient.Builder()
                .cookieJar(cookieStore())
                .build();
    }

    private boolean reset() {
        errorMessage = null;
        clearSession();
        return clearFixtures() && createFixtures();
    }

    private boolean run() {
        if (status.finished()) {
            return status.pass();
        }
        return runDependents() && runInteractions();
    }

    private boolean runDependents() {
        for (String dependent : depends) {
            Scenario scenario = scenarioManager.get(dependent);
            if (!scenario.run()) {
                errorMessage = "dependency failure: " + dependent;
                return false;
            }
        }
        return true;
    }

    private boolean runInteractions() {
        for (Interaction interaction : interactions) {
            boolean pass = run(interaction);
            if (!pass) {
                errorMessage = S.fmt("interaction[%s] failure", interaction.description);
                return false;
            }
        }
        return true;
    }

    private boolean run(Interaction interaction) {
        boolean okay = interaction.run();
        if (!okay) {
            return false;
        }
        for (Map.Entry<String, String> entry : interaction.cache.entrySet()) {
            String ref = entry.getValue();
            Object value = getLastVal(ref);
            if (null != value) {
                cache.put(entry.getKey(), value);
            }
        }
        return true;
    }

    private synchronized CookieStore cookieStore() {
        if (null == cookieStore) {
            App app = Act.app();
            cookieStore = null == app ? new CookieStore() : app.getInstance(CookieStore.class);
        }
        return cookieStore;
    }

    void verifyBody(String bodyString, ResponseSpec spec) {
        lastData.set(bodyString);
        if (null == spec) {
            if (bodyString.startsWith("[")) {
                JSONArray array = JSON.parseArray(bodyString);
                lastData.set(array);
            } else if (bodyString.startsWith("{")) {
                JSONObject obj = JSON.parseObject(bodyString);
                lastData.set(obj);
            }
            return;
        }
        if (null != spec.text) {
            verifyValue(bodyString, spec.text);
        } else if (null != spec && null != spec.json && !spec.json.isEmpty()) {
            if (bodyString.startsWith("[")) {
                JSONArray array = JSON.parseArray(bodyString);
                lastData.set(array);
                verifyList(array, spec.json);
            } else if (bodyString.startsWith("{")) {
                JSONObject obj = JSON.parseObject(bodyString);
                lastData.set(obj);
                verifyJsonObject(obj, spec.json);
            } else {
                error("Unknown JSON string: \n%s", bodyString);
            }
        } else if (null != spec.html && !spec.html.isEmpty()) {
            lastData.set(bodyString);
            Document doc = Jsoup.parse(bodyString, S.concat("http://localhost:", port, "/"));
            for (Map.Entry<String, Object> entry : spec.html.entrySet()) {
                String path = entry.getKey();
                Elements elements = doc.select(path);
                verifyValue(elements, entry.getValue());
            }
        }
    }

    void verifyList(List array, Map<String, Object> spec) {
        for (Map.Entry<String, Object> entry : spec.entrySet()) {
            String key = entry.getKey();
            Object test = entry.getValue();
            Object value = null;
            if ("size".equals(key) || "len".equals(key) || "length".equals(key)) {
                value = array.size();
            } else if ("toString".equals(key) || "string".equals(key) || "str".equals(key)) {
                value = JSON.toJSONString(array);
            } else if ("?".equals(key) || "<any>".equalsIgnoreCase(key)) {
                for (Object arrayElement : array) {
                    try {
                        verifyValue(arrayElement, test);
                        return;
                    } catch (Exception e) {
                        // try next one
                    }
                }
            } else if (S.isInt(key)) {
                int id = Integer.parseInt(key);
                value = array.get(id);
            } else {
                if (key.contains(".")) {
                    String id = S.cut(key).beforeFirst(".");
                    String prop = S.cut(key).afterFirst(".");
                    if ("?".equals(id) || "<any>".equalsIgnoreCase(id)) {
                        for (Object arrayElement : array) {
                            if (!(arrayElement instanceof JSONObject)) {
                                continue;
                            }
                            try {
                                verifyValue(((JSONObject) arrayElement).get(prop), test);
                                return;
                            } catch (Exception e) {
                                // try next one
                            }
                        }
                    } else if (S.isInt(id)) {
                        int i = Integer.parseInt(id);
                        Object o = array.get(i);
                        if (o instanceof JSONObject) {
                            JSONObject json = (JSONObject) o;
                            value = json.get(prop);
                        }
                    }
                }
                if (null == value) {
                    throw error("Unknown attribute of array verification: %s", key);
                }
            }
            verifyValue(value, test);
        }
    }

    void verifyJsonObject(JSONObject obj, Map<String, Object> jsonSpec) {
        for (Map.Entry<String, Object> entry : jsonSpec.entrySet()) {
            String key = entry.getKey();
            Object value = $.getProperty(obj, key);
            verifyValue(value, entry.getValue());
        }
    }

    void verifyValue(Object value, Object test) {
        if (test instanceof List) {
            verifyValue_(value, (List) test);
        } else if (value instanceof List && test instanceof Map) {
            verifyList((List) value, (Map) test);
        } else {
            if (matches(value, test)) {
                return;
            }
            if (value instanceof JSONObject) {
                errorIfNot(test instanceof Map, "Cannot verify value[%s] with test [%s]", value, test);
                JSONObject json = (JSONObject) value;
                Map<String, ?> testMap = (Map) test;
                for (Map.Entry<?, ?> entry : testMap.entrySet()) {
                    Object testKey = entry.getKey();
                    Object testValue = entry.getValue();
                    Object attr = json.get(testKey);
                    verifyValue(attr, testValue);
                }
            } else if (value instanceof Elements) {
                if (test instanceof Map) {
                    verifyList((Elements)value, (Map)test);
                } else {
                    Elements elements = (Elements) value;
                    if (elements.isEmpty()) {
                        value = null;
                    } else {
                        value = elements.first();
                    }
                    verifyValue(value, test);
                }
            } else if (test instanceof String) {
                if (null != value && ("*".equals(test) || "...".equals(test) || "<any>".equals(test))) {
                    return;
                }
                try {
                    Pattern p = Pattern.compile((String) test);
                    errorIfNot(p.matcher((String) value).matches(), "Cannot verify value[%s] with test [%s]", value, test);
                    return;
                } catch (Exception e) {
                    // ignore
                }
                Verifier v = tryLoadVerifier((String) test);
                if (null != v && v.verify(value)) {
                    return;
                }
                error("Cannot verify value[%s] with test [%s]", value, test);
            } else {
                error("Cannot verify value[%s] with test [%s]", value, test);
            }
        }
    }

    private void verifyValue_(Object value, List tests) {
        // try to do the literal match
        if (value instanceof List) {
            List found = (List) value;
            boolean ok = found.size() == tests.size();
            if (ok) {
                for (int i = 0; i < found.size(); ++i) {
                    Object foundElement = found.get(i);
                    Object testElement = tests.get(i);
                    if (!matches(foundElement, testElement)) {
                        ok = false;
                        break;
                    }
                }
            }
            if (ok) {
                return;
            }
        }
        // now try verifiers
        if (value instanceof Elements) {
            Elements elements = (Elements) value;
            if (elements.size() > 0) {
                value = elements.first();
            } else {
                value = null;
            }
        }
        for (Object test : tests) {
            errorIfNot(test instanceof Map, "Cannot verify value[%s] against test[%s]", value, test);
            Map<?, ?> map = (Map) test;
            errorIfNot(map.size() == 1, "Cannot verify value[%s] against test[%s]", value, test);
            Verifier v = $.convert(map).to(Verifier.class);
            errorIf(null == v, "Cannot verify value[%s] against test[%s]", value, test);
            errorIf(!verify(v, value), "Cannot verify value[%s] against test[%s]", value, v);
        }
    }

    private boolean verify(Verifier test, Object value) {
        if (test.verify(value)) {
            return true;
        }
        if (value instanceof Element) {
            Element e = (Element) value;
            if (test.verify(e.val())) {
                return true;
            }
            if (test.verify(e.text())) {
                return true;
            }
            if (test.verify(e.html())) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(Object a, Object b) {
        if ($.eq(a, b)) {
            return true;
        }
        if (!((b instanceof String) && (a instanceof Element))) {
            return false;
        }
        String test = S.string(b);
        Element element = (Element) a;
        // try html
        String html = element.html();
        if (S.eq(html, test, S.IGNORECASE)) {
            return true;
        }
        // try text
        String text = element.text();
        if (S.eq(text, test, S.IGNORECASE)) {
            return true;
        }
        // try val
        String val = element.val();
        if (S.eq(val, test, S.IGNORECASE)) {
            return true;
        }
        return false;
    }

    private Class<?> tryLoadClass(String name) {
        try {
            return null != app ? app.classForName(name) : $.classForName(name);
        } catch (Exception e) {
            return null;
        }
    }

    private Verifier tryLoadVerifier(String name) {
        Class<?> c = tryLoadClass(name);
        if (null != c) {
            if (Verifier.class.isAssignableFrom(c)) {
                return (Verifier) (null != app ? app.getInstance(c) : $.newInstance(c));
            } else {
                throw new UnexpectedException("Class not supported: " + name);
            }
        }
        return null;
    }

    private Object getVal(String key, String ref) {
        Object stuff = "last".equals(key) ? lastData.get() : cache.get(key);
        return S.blank(ref) ? stuff : JSONTraverser.traverse(stuff, ref);
    }

    private Object getLastVal(String ref) {
        return getVal("last", ref);
    }

    static Scenario get() {
        return current.get();
    }
}
