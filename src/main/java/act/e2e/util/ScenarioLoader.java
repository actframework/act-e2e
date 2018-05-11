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

import act.app.DaoLocator;
import act.e2e.Scenario;
import org.osgl.$;

import java.util.Map;

public class ScenarioLoader extends YamlLoader {

    private static DaoLocator NULL_DAO = new NullDaoLocator();

    public ScenarioLoader() {
        super("act.e2e");
        setFixtureFolder("/e2e/");
    }

    public ScenarioLoader(String modelPackage, String... modelPackages) {
        super();
        addModelPackages("act.e2e");
        addModelPackages(modelPackage, modelPackages);
        setFixtureFolder("/e2e");
    }

    public Map<String, Scenario> load() {
        Map<String, Object> map = super.loadFixture("scenarios.yml", NULL_DAO);
        Map<String, Scenario> loaded = $.cast(map);
        for (Map.Entry<String, Scenario> entry : loaded.entrySet()) {
            entry.getValue().name = entry.getKey();
        }
        return loaded;
    }
}
