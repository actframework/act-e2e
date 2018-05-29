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

import act.Act;
import act.app.App;
import act.app.DbServiceManager;
import act.db.Dao;
import act.db.DbService;
import act.db.sql.tx.TxContext;
import act.e2e.macro.Macro;
import act.e2e.req_modifier.RequestModifier;
import act.e2e.util.RequestTemplateManager;
import act.e2e.util.ScenarioManager;
import act.e2e.util.YamlLoader;
import act.e2e.verifier.Verifier;
import act.job.OnAppStart;
import act.sys.Env;
import act.util.LogSupport;
import org.osgl.$;
import org.osgl.logging.LogManager;
import org.osgl.logging.Logger;
import org.osgl.mvc.annotation.DeleteAction;
import org.osgl.mvc.annotation.PostAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

@Env.RequireMode(Act.Mode.DEV)
public class E2E extends LogSupport {

    private static final Logger LOGGER = LogManager.get(E2E.class);

    @Inject
    private DbServiceManager dbServiceManager;

    @Inject
    private YamlLoader yamlLoader;

    /**
     * Load fixture data for testing.
     *
     * Requested by e2e executor and executed on test host to
     * setup testing data
     */
    @PostAction("e2e/fixtures")
    public void loadFixtures(List<String> fixtures) {
        for (String fixture : fixtures) {
            yamlLoader.loadFixture(fixture, dbServiceManager);
        }
    }

    /**
     * Clear fixture data.
     *
     * Requested by e2e executor and executed on test host to
     * setup testing data
     */
    @DeleteAction("e2e/fixtures")
    public void clearFixtures() {
        List<Dao> toBeDeleted = new ArrayList<>();
        for (DbService svc : dbServiceManager.registeredServices()) {
            for (Class entityClass : svc.entityClasses()) {
                toBeDeleted.add(dbServiceManager.dao(entityClass));
            }
        }
        /*
         * The following logic is to deal with the case where two
         * models have relationship, and the one that is not the owner
         * has been called to delete first, in which case it will
         * fail because reference exists in other table(s). so
         * we want to ignore that case and keep removing other tables.
         * Hopefully when the owner model get removed eventually and
         * back to the previous model, it will be good to go.
         */
        int count = 1000;
        while (!toBeDeleted.isEmpty() && count-- > 0) {
            List<Dao> list = new ArrayList<>(toBeDeleted);
            for (Dao dao : list) {
                try {
                    TxContext.enterTxScope(false);
                    dao.drop();
                    try {
                        TxContext.exitTxScope();
                    } catch (Exception e) {
                        continue;
                    }
                    toBeDeleted.remove(dao);
                } catch (Exception e) {
                    TxContext.exitTxScope(e);
                } finally {
                    TxContext.clear();
                }
            }
        }
    }

    // wait 1 seconds to allow app setup the network
    @OnAppStart(delayInSeconds = 1)
    public void run(App app) {
        boolean run = $.bool(app.config().get("e2e.run")) || "e2e".equalsIgnoreCase(Act.profile());
        if (run) {
            run(app, true);
        }
    }

    public List<Scenario> run(App app, boolean shutdownApp) {
        info("Start running E2E test scenarios\n");
        try {
            registerTypeConverters();
            RequestTemplateManager requestTemplateManager = new RequestTemplateManager();
            requestTemplateManager.load();
            ScenarioManager scenarioManager = new ScenarioManager();
            Map<String, Scenario> scenarios = scenarioManager.load();
            if (scenarios.isEmpty()) {
                LOGGER.warn("No scenario defined.");
            } else {
                for (Scenario scenario : scenarios.values()) {
                    scenario.start(scenarioManager, requestTemplateManager);
                }
            }
            List<Scenario> list = new ArrayList<>();
            for (Scenario scenario : scenarios.values()) {
                addToList(scenario, list, scenarioManager);
            }
            return list;
        } finally {
            if (shutdownApp) {
                app.shutdown();
            }
        }
    }

    public static void registerTypeConverters() {
        Verifier.registerTypeConverters();
        Macro.registerTypeConverters();
        RequestModifier.registerTypeConverters();
    }

    private static void addToList(Scenario scenario, List<Scenario> list, ScenarioManager manager) {
        for (String s : scenario.depends) {
            Scenario dep = manager.get(s);
            addToList(dep, list, manager);
        }
        list.add(scenario);
    }

}
