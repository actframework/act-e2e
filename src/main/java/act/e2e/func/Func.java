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
import org.osgl.util.C;
import org.osgl.util.E;
import org.osgl.util.N;
import org.osgl.util.S;
import org.osgl.util.converter.TypeConverterRegistry;

import java.util.List;

public abstract class Func<T extends Func> extends NamedLogic<T> {

    @Override
    protected Class<? extends NamedLogic> type() {
        return Func.class;
    }

    public abstract Object apply();

    public static class SubStr extends Func<SubStr> {

        private String targetStr;

        @Override
        public void init(Object param) {
            E.illegalArgumentIfNot(param instanceof List, "At least 2 parameters expected for subStr function");
            List<String> params = (List<String>) param;
            targetStr = S.ensure(params.get(0)).strippedOff(S.DOUBLE_QUOTES);
            String sBegin = params.get(1);
            E.illegalArgumentIfNot(S.isInt(sBegin), "the 2nd parameter must be valid integer");
            int begin = Integer.parseInt(sBegin);
            E.illegalArgumentIf(begin < 0, "the 2nd parameter must be valid integer");
            int end = -1;
            if (params.size() > 2) {
                String sEnd = params.get(2);
                E.illegalArgumentIfNot(S.isInt(sEnd), "the 3nd parameter must be valid integer");
                end = Integer.parseInt(sEnd);
                E.illegalArgumentIf(end < begin, "the 3nd parameter not be less than the 2nd parameter");
                if (end > targetStr.length()) {
                    end = targetStr.length();
                }
            }
            targetStr = -1 == end ? targetStr.substring(begin) : targetStr.substring(begin, end);
        }

        @Override
        public Object apply() {
            return targetStr;
        }

        @Override
        protected List<String> aliases() {
            return C.list("subString", "substr");
        }
    }

    public static class RandomStr extends Func<RandomStr> {
        @Override
        public Object apply() {
            int length = 0;
            if (null != initVal) {
                try {
                    length = $.convert(initVal).toInt();
                } catch (Exception e) {
                    warn(e, "RandomStr func init value (max length) shall be evaluated to an integer, found: " + initVal);
                }
            }
            if (length < 1) {
                length = 5 + N.randInt(10);
            }
            return S.random(length);
        }

        @Override
        protected List<String> aliases() {
            return C.list("randStr", "randomString", "randString");
        }
    }

    public static class RandomInt extends Func<RandomInt> {
        @Override
        public Object apply() {
            int max = 0;
            boolean positive = true;
            if (null != initVal) {
                try {
                    max = $.convert(initVal).toInt();
                    if (max < 0) {
                        positive = false;
                        max = -max;
                    }
                } catch (Exception e) {
                    warn(e, "RandomInt func init value (max) shall be evaluated to an integer, found: " + initVal);
                }
            }
            if (max == 0) {
                max = 100;
            }
            int retVal = N.randInt(max);
            if (!positive) {
                retVal = -retVal;
            }
            return retVal;
        }

        @Override
        protected List<String> aliases() {
            return C.list("randInt", "randomInteger", "randInteger");
        }
    }

    public static class RandomBoolean extends Func<RandomBoolean> {
        @Override
        public Object apply() {
            return $.random(true, false);
        }

        @Override
        protected List<String> aliases() {
            return C.list("randBoolean", "randomBool", "randBool");
        }
    }

    public static class RandomLong extends Func<RandomLong> {
        @Override
        public Object apply() {
            long max = 0;
            boolean positive = true;
            if (null != initVal) {
                try {
                    max = $.convert(initVal).toLong();
                    if (max < 0) {
                        positive = false;
                        max = -max;
                    }
                } catch (Exception e) {
                    warn(e, "RandomLong func init value (max) shall be evaluated to an long, found: " + initVal);
                }
            }
            if (max == 0) {
                max = 100000L;
            }
            long retVal = N.randLong(max);
            if (!positive) {
                retVal = -retVal;
            }
            return retVal;
        }

        @Override
        protected List<String> aliases() {
            return C.list("randLong");
        }
    }

    public static void registerTypeConverters() {
        TypeConverterRegistry.INSTANCE.register(new FromLinkedHashMap(Func.class));
        TypeConverterRegistry.INSTANCE.register(new FromString(Func.class));
    }

}
