package act.e2e.func;

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

import act.e2e.util.NamedLogic;
import org.osgl.$;
import org.osgl.util.N;
import org.osgl.util.S;
import org.osgl.util.converter.TypeConverterRegistry;

public abstract class Func<T extends Func> extends NamedLogic<T> {

    @Override
    protected Class<? extends NamedLogic> type() {
        return Func.class;
    }

    public abstract Object apply();

    public static class RandomStr extends Func<RandomStr> {
        @Override
        public Object apply() {
            int length = 0;
            if (null != initVal) {
                try {
                    length = $.convert(initVal).toInt();
                } catch (Exception e) {
                    warn(e, "RandomStr func init value shall be evauated to an integer, found: " + initVal);
                }
            }
            if (length < 1) {
                length = 5 + N.randInt(10);
            }
            return S.random(length);
        }
    }

    public static void registerTypeConverters() {
        TypeConverterRegistry.INSTANCE.register(new FromLinkedHashMap(Func.class));
        TypeConverterRegistry.INSTANCE.register(new FromString(Func.class));
    }

}
