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
import act.e2e.macro.Macro;
import act.e2e.req_modifier.RequestModifier;
import act.e2e.util.*;
import act.e2e.verifier.Verifier;
import act.job.OnAppStart;
import act.sys.Env;
import act.util.LogSupport;
import org.osgl.$;
import org.osgl.logging.LogManager;
import org.osgl.logging.Logger;
import org.osgl.mvc.annotation.DeleteAction;
import org.osgl.mvc.annotation.PostAction;
import org.osgl.util.C;
import org.osgl.util.S;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

@Env.RequireMode(Act.Mode.DEV)
public class E2E extends LogSupport {

    static final Logger LOGGER = LogManager.get(E2E.class);

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
                try {
                    toBeDeleted.add(dbServiceManager.dao(entityClass));
                } catch (IllegalArgumentException e) {
                    if (e.getMessage().contains("Cannot find out Dao for model type")) {
                        // ignore - must be caused by MappedSuperClass
                        logger.debug(e, "error getting dao for %s", entityClass);
                        continue;
                    }
                }
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
                    TxScope.enter();
                    dao.drop();
                    try {
                        TxScope.commit();
                    } catch (Exception e) {
                        continue;
                    }
                    toBeDeleted.remove(dao);
                } catch (Exception e) {
                    // ignore and try next dao
                } finally {
                    TxScope.clear();
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
        int exitCode = 0;
        app.captchaManager().disable();
        try {
            registerTypeConverters();
            RequestTemplateManager requestTemplateManager = new RequestTemplateManager();
            requestTemplateManager.load();
            final ScenarioManager scenarioManager = new ScenarioManager();
            Map<String, Scenario> scenarios = scenarioManager.load();
            if (scenarios.isEmpty()) {
                LOGGER.warn("No scenario defined.");
            } else {
                C.List<Scenario> list = C.list(scenarios.values()).sorted(new ScenarioComparator(scenarioManager));
                for (Scenario scenario : list) {
                    scenario.start(scenarioManager, requestTemplateManager);
                }
            }
            List<Scenario> list = new ArrayList<>();
            for (Scenario scenario : scenarios.values()) {
                addToList(scenario, list, scenarioManager);
            }
            if (shutdownApp) {
                for (Scenario scenario : list) {
                    if (!scenario.status.pass()) {
                        exitCode = -1;
                    }
                    output(scenario);
                }
            }
            return list;
        } catch (Exception e) {
            exitCode = -1;
            throw e;
        } finally {
            if (shutdownApp) {
                app.shutdown(exitCode);
            } else {
                app.captchaManager().enable();
            }
        }
    }

    private void output(Scenario scenario) {
        printBanner(scenario);
        printInteractions(scenario);
        printFooter();
    }

    private void printBanner(Scenario scenario) {
        printDoubleDashedLine();
        info(scenario.title());
        printDashedLine();
    }

    private void printInteractions(Scenario scenario) {
        for (Interaction interaction : scenario.interactions) {
            String msg = S.concat("[", interaction.status, "]", interaction.description);
            info(msg);
        }
    }

    private void printFooter() {
        println();
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
        if (!list.contains(scenario)) {
            list.add(scenario);
        }
    }

}
