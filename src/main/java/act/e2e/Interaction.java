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

import act.e2e.E2E.Status;
import act.e2e.macro.Macro;
import okhttp3.Response;
import org.osgl.exception.UnexpectedException;
import org.osgl.http.H;
import org.osgl.util.E;
import org.osgl.util.S;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Interaction implements ScenarioPart {
    public List<Macro> preActions = new ArrayList<>();
    public String description;
    public RequestSpec request;
    public ResponseSpec response;
    public List<Macro> postActions = new ArrayList<>();
    public Map<String, String> cache = new HashMap<>();
    public String errorMessage;
    public Throwable cause;
    public Status status = PENDING;

    @Override
    public void validate(Scenario scenario) throws UnexpectedException {
        E.unexpectedIf(S.blank(description), "description is blank");
        E.unexpectedIf(null == request, "request spec not specified");
        //E.unexpectedIf(null == response, "response spec not specified");
        scenario.resolveRequest(request);
        request.validate(scenario);
        if (null != response) {
            response.validate(scenario);
        }
        reset();
    }

    public boolean run() {
        boolean pass = run(preActions) && verify() && run(postActions);
        status = Status.of(pass);
        return pass;
    }

    private void reset() {
        errorMessage = null;
        cause = null;
    }

    private boolean verify() {
        try {
            Response resp = Scenario.get().sendRequest(request);
            doVerify(resp);
            return true;
        } catch (Exception e) {
            errorMessage = e.getMessage();
            cause = e.getCause();
            return false;
        }
    }

    private void doVerify(Response resp) throws Exception {
        verifyStatus(resp);
        verifyHeaders(resp);
        verifyBody(resp);
    }

    private boolean run(List<Macro> macros) {
        for (Macro macro : macros) {
            boolean okay = run(macro);
            if (!okay) {
                return false;
            }
        }
        return true;
    }

    private boolean run(Macro macro) {
        try {
            macro.run(Scenario.get());
            return true;
        } catch (Exception e) {
            errorMessage = e.getMessage();
            cause = causeOf(e);
            return false;
        }
    }

    private void verifyStatus(Response resp) {
        H.Status expected = expectedStatus();
        if (null == expected) {
            if (!resp.isSuccessful()) {
                error("Status verification failure. Expected: successful, Found: " + resp.code());
            }
        } else {
            if (expected.code() != resp.code()) {
                error("Status verification failure. Expected: %s, Found: %s", expected.code(), resp.code());
            }
        }
    }

    private void verifyHeaders(Response resp) {
        for (Map.Entry<String, Object> entry : response.headers.entrySet()) {
            String headerName = entry.getKey();
            String headerVal = resp.header(headerName);
            try {
                Scenario.get().verifyValue(headerVal, entry.getValue());
            } catch (Exception e) {
                error(e, S.concat("Failed verifying header[", headerName, "]: ", e.getMessage()));
            }
        }
        Scenario.get().lastHeaders.set(resp.headers());
    }

    private void verifyBody(Response rs) throws Exception {
        String bodyString = S.string(rs.body().string()).trim();
        Scenario.get().verifyBody(bodyString, response);
    }

    private H.Status expectedStatus() {
        return null == response ? null : response.status;
    }

    private static Throwable causeOf(Exception e) {
        Throwable cause = e.getCause();
        return null == cause ? e : cause;
    }
}
