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

import static org.osgl.http.H.Header.Names.ACCEPT;
import static org.osgl.http.H.Header.Names.X_REQUESTED_WITH;

import act.Act;
import act.app.App;
import act.e2e.macro.Macro;
import act.e2e.req_modifier.RequestModifier;
import act.e2e.util.CookieStore;
import act.e2e.util.JSONTraverser;
import act.e2e.util.RequestTemplateManager;
import act.e2e.util.ScenarioManager;
import act.e2e.verifier.Verifier;
import act.util.LogSupport;
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
import org.osgl.util.Codec;
import org.osgl.util.E;
import org.osgl.util.IO;
import org.osgl.util.S;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

public class Scenario extends LogSupport implements ScenarioPart {

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
    public boolean finished;
    public Map<Interaction, String> errorMessages;

    $.Var<Object> lastData = $.var();
    $.Var<Map<String, Object>> lastHeaders = $.var();

    private ScenarioManager scenarioManager;
    private RequestTemplateManager requestTemplateManager;
    private transient Interaction currentInteraction;

    private Map<String, Object> memory = new HashMap<>();

    public Scenario() {
        app = Act.app();
        if (null != app) {
            port = app.config().httpPort();
        }
    }

    public void remember(String name, Object payload) {
        memory.put(name, payload);
    }

    public Object memory(String name) {
        return memory.get(name);
    }

    public boolean pass(Interaction interaction) {
        return !errorMessages.containsKey(interaction);
    }

    public String errorMessageOf(Interaction interaction) {
        return errorMessages.get(interaction);
    }

    @Override
    public void validate() throws UnexpectedException {
        E.unexpectedIf(interactions.isEmpty(), "No interactions defined");
        E.unexpectedIf(S.blank(name), "name is blank");
        for (Interaction interaction : interactions) {
            interaction.validate();
        }
    }

    public void start(ScenarioManager scenarioManager, RequestTemplateManager requestTemplateManager) {
        if (finished) {
            return;
        }
        this.errorMessages = new IdentityHashMap<>();
        this.scenarioManager = $.requireNotNull(scenarioManager);
        this.requestTemplateManager = $.requireNotNull(requestTemplateManager);
        current.set(this);
        validate();
        long ms = $.ms();
        printBanner();
        prepareHttp();
        reset();
        run();
        printFooter($.ms() - ms);
    }

    public void clearSession() {
        cookieStore().clear();
    }

    public void clearFixtures() {
        run(RequestSpec.RS_CLEAR_FIXTURE, null);
    }

    protected void error(String format, Object... args) {
        E.illegalStateIf(null == currentInteraction, "no current interaction");
        String errorMessage = S.fmt(format, args);
        super.error(errorMessage);
        errorMessages.put(currentInteraction, errorMessage);
    }

    protected void error(Throwable t, String format, Object... args) {
        E.illegalStateIf(null == currentInteraction, "no current interaction");
        String errorMessage = S.fmt(format, args);
        super.error(t, errorMessage);
        errorMessages.put(currentInteraction, errorMessage);
    }

    private void createFixtures() {
        if (fixtures.isEmpty()) {
            return;
        }
        RequestSpec req = RequestSpec.loadFixtures(fixtures);
        run(req, null);
    }

    private void prepareHttp() {
        http = new OkHttpClient.Builder().cookieJar(cookieStore()).build();
    }

    private void reset() {
        clearSession();
        clearFixtures();
        createFixtures();
    }

    private boolean run() {
        boolean ok = false;
        try {
            ok = runDependents();
            if (ok) {
                runInteractions();
            }
            ok = true;
        } catch ($.Break b) {
            Exception e = b.get();
            error(e, "error running scenario: " + name);
        } catch (Exception e) {
            error(e, "error running scenario: " + name);
        }
        finished = true;
        return ok;
    }

    private boolean runDependents() {
        for (String dependent : depends) {
            if (!scenarioManager.get(dependent).run()) {
                return false;
            }
        }
        return true;
    }

    private void runInteractions() {
        for (Interaction interaction : interactions) {
            run(interaction);
        }
    }

    private void run(Interaction interaction) {
        currentInteraction = interaction;
        for (Macro macro : interaction.preActions) {
            macro.run(this);
        }
        try {
            run(interaction.request, interaction.response);
            info("[PASS] " + interaction.description);
        } catch (RuntimeException e) {
            error("[FAIL] " + interaction.description);
            throw e;
        }
        for (Macro macro : interaction.postActions) {
            macro.run(this);
        }
        for (Map.Entry<String, String> entry : interaction.memory.entrySet()) {
            String ref = entry.getValue();
            Object value = getLastVal(ref);
            if (null != value) {
                memory.put(entry.getKey(), value);
            }
        }
    }

    private void printBanner() {
        printDoubleDashedLine();
        info(name.toUpperCase());
        if (S.notBlank(description)) {
            println();
            info(S.string(description));
        }
        printDashedLine();
    }

    private void printFooter(long duration) {
        printDashedLine();
        info("It takes %ss to run this scenario.\n", duration / 1000);
    }

    private synchronized CookieStore cookieStore() {
        if (null == cookieStore) {
            App app = Act.app();
            cookieStore = null == app ? new CookieStore() : app.getInstance(CookieStore.class);
        }
        return cookieStore;
    }

    private void run(RequestSpec req, ResponseSpec rs) {
        req.resolveParent(requestTemplateManager);
        Request httpRequest = new RequestBuilder(req).build();
        try {
            Response httpResponse = http.newCall(httpRequest).execute();
            verify(httpResponse, rs);
        } catch (IOException e) {
            throw E.ioException(e);
        }
    }

    private void verify(Response rs, ResponseSpec spec) throws IOException {
        if (null == spec) {
            E.unexpectedIfNot(rs.isSuccessful());
            return;
        }
        verifyStatus(rs, spec);
        verifyHeaders(rs, spec);
        verifyBody(rs, spec);
    }

    private void verifyStatus(Response rs, ResponseSpec spec) {
        H.Status expectedStatus = spec.status;
        if (null == expectedStatus) {
            E.unexpectedIfNot(rs.isSuccessful(), "Error status returned");
        } else {
            E.unexpectedIfNot(rs.code() == expectedStatus.code(), "expected status: %s, found status: %s", expectedStatus.code(), rs.code());
        }
    }

    private void verifyHeaders(Response rs, ResponseSpec spec) {
        for (Map.Entry<String, Object> entry : spec.headers.entrySet()) {
            String headerName = entry.getKey();
            String headerVal = rs.header(headerName);
            try {
                verifyValue(headerVal, entry.getValue());
            } catch (Exception e) {
                E.unexpected(e, "failed verify header[%s]", headerName);
            }
        }
        lastHeaders.set(spec.headers);
    }

    private void verifyBody(Response rs, ResponseSpec spec) throws IOException {
        String bodyString = S.string(rs.body().string()).trim();
        if (null != spec.text) {
            lastData.set(bodyString);
            verifyValue(bodyString, spec.text);
        } else if (null != spec.json && !spec.json.isEmpty()) {
            if (bodyString.startsWith("[")) {
                JSONArray array = JSON.parseArray(bodyString);
                lastData.set(array);
                verifyList(array, spec.json);
            } else if (bodyString.startsWith("{")) {
                JSONObject obj = JSON.parseObject(bodyString);
                lastData.set(obj);
                verifyJsonObject(obj, spec.json);
            } else {
                E.unexpected("Unknown JSON string: \n%s", bodyString);
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

    private void verifyList(List array, Map<String, Object> spec) {
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
                    throw E.unexpected("Unknown attribute of array verification: %s", key);
                }
            }
            verifyValue(value, test);
        }
    }

    private void verifyJsonObject(JSONObject obj, Map<String, Object> jsonSpec) {
        for (Map.Entry<String, Object> entry : jsonSpec.entrySet()) {
            String key = entry.getKey();
            Object value = $.getProperty(obj, key);
            verifyValue(value, entry.getValue());
        }
    }

    private void verifyValue(Object value, Object test) {
        if (test instanceof List) {
            verifyValue_(value, (List) test);
        } else if (value instanceof List && test instanceof Map) {
            verifyList((List) value, (Map) test);
        } else {
            if (matches(value, test)) {
                return;
            }
            if (value instanceof JSONObject) {
                E.unexpectedIfNot(test instanceof Map, "Cannot verify value[%s] with test [%s]", value, test);
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
                    E.unexpectedIfNot(p.matcher((String) value).matches(), "Cannot verify value[%s] with test [%s]", value, test);
                    return;
                } catch (Exception e) {
                    // ignore
                }
                Verifier v = tryLoadVerifier((String) test);
                if (null != v && v.verify(value)) {
                    return;
                }
                E.unexpected("Cannot verify value[%s] with test [%s]", value, test);
            } else {
                E.unexpected("Cannot verify value[%s] with test [%s]", value, test);
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
            E.unexpectedIfNot(test instanceof Map, "Cannot verify value[%s] against test[%s]", value, test);
            Map<?, ?> map = (Map) test;
            E.unexpectedIfNot(map.size() == 1, "Cannot verify value[%s] against test[%s]", value, test);
            Verifier v = $.convert(map).to(Verifier.class);
            E.unexpectedIf(null == v, "Cannot verify value[%s] against test[%s]", value, test);
            E.unexpectedIf(!verify(v, value), "Cannot verify value[%s] against test[%s]", value, v);
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
        Object stuff = "last".equals(key) ? lastData.get() : memory.get(key);
        return S.blank(ref) ? stuff : JSONTraverser.traverse(stuff, ref);
    }

    private Object getLastVal(String ref) {
        return getVal("last", ref);
    }
}
