package act.e2e.macro;

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

import act.e2e.Scenario;
import act.e2e.util.NamedLogic;
import org.osgl.$;
import org.osgl.util.C;
import org.osgl.util.E;
import org.osgl.util.IO;
import org.osgl.util.S;
import org.osgl.util.converter.TypeConverterRegistry;

import java.io.File;
import java.util.List;

public abstract class Macro<T extends Macro> extends NamedLogic<T> {

    public abstract void run(Scenario scenario);

    @Override
    protected final Class<? extends NamedLogic> type() {
        return Macro.class;
    }

    public static class ClearFixture extends Macro<ClearFixture> {

        @Override
        public void run(Scenario scenario) {
            scenario.clearFixtures();
        }

        @Override
        protected List<String> aliases() {
            return C.listOf("clear-data", "reset");
        }
    }

    public static class ClearSession extends Macro<ClearSession> {
        @Override
        public void run(Scenario scenario) {
            scenario.clearSession();
        }
    }

    /**
     * Read file content into a string and cache the string
     * with the underscore style of the file name.
     */
    public static class ReadContent extends Macro<ReadContent> {
        @Override
        public void run(Scenario scenario) {
            String fileName = (String) initVal;
            File file = new File(fileName);
            E.unexpectedIf(!file.exists() || !file.canRead(), "File not exists or not readable: " + fileName);
            String content = IO.read(file).toString();
            scenario.cache(S.underscore(fileName), content);
        }
    }

    public static class Pause extends Macro<Pause> {

        long time;

        @Override
        protected void init(Object param) {
            time = $.convert(param).toLong();
            E.illegalArgumentIf(time <=0);
        }

        @Override
        public void run(Scenario scenario) {
            try {
                Thread.sleep(time);
            } catch (InterruptedException e) {
                throw E.unexpected(e);
            }
        }
    }

    public static void registerTypeConverters() {
        TypeConverterRegistry.INSTANCE.register(new FromLinkedHashMap(Macro.class));
        TypeConverterRegistry.INSTANCE.register(new FromString(Macro.class));
    }

    public static void registerActions() {
        new ClearFixture().register();
        new ClearSession().register();
        new ReadContent().register();
        new Pause().register();
    }
}
