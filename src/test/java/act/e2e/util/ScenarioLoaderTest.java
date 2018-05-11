package act.e2e.util;

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

import act.e2e.*;
import act.e2e.macro.Macro;
import act.e2e.req_modifier.RequestModifier;
import act.e2e.verifier.Contains;
import act.e2e.verifier.Eq;
import act.e2e.verifier.Exists;
import act.e2e.verifier.Gt;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgl.http.H;
import osgl.ut.TestBase;

import java.util.Map;

public class ScenarioLoaderTest extends TestBase {

    private ScenarioLoader loader = new ScenarioLoader("act.e2e.model");

    @BeforeClass
    public static void prepare() {
        new Contains().register();
        new Eq().register();
        new Exists().register();
        new Gt().register();
        E2E.registerTypeConverters();
        RequestModifier.registerModifiers();
        Macro.registerActions();
    }

    @Test
    public void test() {
        Map<String, Scenario> map = loader.load();
        no(map.isEmpty());
        Scenario createTask = map.get("create-task");
        verifyCreateTask(createTask);
        String s = createTask.depends.get(0);
        Scenario signIn = loader.get(s);
        notNull(signIn);
        s = signIn.depends.get(0);
        Scenario signUp = loader.get(s);
        verifySignUp(signUp);
    }

    private void verifySignUp(Scenario signUp) {
        notNull(signUp);
        eq(1, signUp.fixtures.size());
        String fixture = signUp.fixtures.get(0);
        eq("init-data.yml", fixture);
        eq(1, signUp.interactions.size());
        Interaction interaction = signUp.interactions.get(0);
        notNull(interaction);
        RequestSpec req = interaction.request;
        eq(2, req.modifiers.size());
        RequestModifier json = req.modifiers.get(0);
        eq("accept-json", json.toString());
        RequestModifier ip = req.modifiers.get(1);
        eq("remote-address[127.0.0.2]", ip.toString());
        eq(H.Method.POST, req.method);
        eq("/sign_up", req.url);
        eq(3, req.params.size());
        Map<String, Object> params = req.params;
        eq("test@123.com", params.get("email"));
        eq("abc", params.get("password"));
        eq(1, params.get("value"));
        ResponseSpec resp = interaction.response;
        eq(H.Status.CREATED, resp.status);
    }

    private void verifyCreateTask(Scenario createTask) {
        notNull(createTask);
    }

}
