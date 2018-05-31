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

import act.e2e.macro.Macro;
import org.osgl.exception.UnexpectedException;
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
    public Map<String, String> memory = new HashMap<>();

    @Override
    public void validate() throws UnexpectedException {
        E.unexpectedIf(S.blank(description), "description is blank");
        E.unexpectedIf(null == request, "request spec not specified");
        //E.unexpectedIf(null == response, "response spec not specified");
        request.validate();
        if (null != response) {
            response.validate();
        }
    }
}
