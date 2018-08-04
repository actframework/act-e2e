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

    /**
     * Apply subString function to given string.
     *
     * If one param is provided it must be an integer which specify the
     * begin index of the substr.
     *
     * If two parameters are provided, the second one specify the end
     * index (exclusive) of the substr.
     *
     * @see String#substring(int)
     * @see String#substring(int, int)
     */
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

    /**
     * Random pick up from a list of parameters.
     */
    public static class RandomOf extends Func<RandomOf> {

        private boolean isList;
        private List list;

        @Override
        public void init(Object param) {
            super.init(param);
            isList = param instanceof List;
            if (isList) {
                list = (List) param;
            }
        }

        @Override
        public Object apply() {
            return isList ? $.random(list) : initVal;
        }

        @Override
        protected List<String> aliases() {
            return C.list("randOf", "randSelect", "randomSelect", "pickOne");
        }
    }

    /**
     * Generate random string.
     *
     * If initVal is provided then it must be a positive integer which indicate
     * the length of the random string. Otherwise the length will be any
     * where between 5 and 15.
     */
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

    /**
     * Generate random int value.
     *
     * If initVal is provided then
     * - if there is 1 init val, it specify the ceiling of the random integer
     * - if there are 2 values, the first is the bottom of the random val and the second is the ceiling of the val
     */
    public static class RandomInt extends Func<RandomInt> {
        @Override
        public Object apply() {
            int max = 0;
            boolean positive = true;
            int min = 0;
            if (null != initVal) {
                Object ceilling = initVal;
                if (initVal instanceof List) {
                    List list = (List) initVal;
                    Object bottom = list.get(0);
                    min = $.convert(bottom).toInt();
                    ceilling = list.get(1);
                }
                try {
                    max = $.convert(ceilling).toInt();
                    if (max < 0) {
                        positive = false;
                        if (max > min) {
                            int tmp = min;
                            min = max;
                            max = tmp;
                        }
                        max = min - max;
                    } else {
                        if (max < min) {
                            int tmp = min;
                            min = max;
                            max = tmp;
                        }
                        max = max - min;
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
            retVal += min;
            return retVal;
        }

        @Override
        protected List<String> aliases() {
            return C.list("randInt", "randomInteger", "randInteger");
        }
    }

    /**
     * Generate random `true`, `false`
     */
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

    /**
     * Generate random long value.
     *
     * If initVal is provided then
     * - if there is 1 init val, it specify the ceiling of the random long value
     * - if there are 2 values, the first is the bottom of the random val and the second is the ceiling of the val
     */
    public static class RandomLong extends Func<RandomLong> {
        @Override
        public Object apply() {
            long max = 0;
            long min = 0;
            boolean positive = true;
            if (null != initVal) {
                Object ceilling = initVal;
                if (initVal instanceof List) {
                    List list = (List) initVal;
                    Object bottom = list.get(0);
                    min = $.convert(bottom).toLong();
                    ceilling = list.get(1);
                }
                try {
                    max = $.convert(ceilling).toLong();
                    if (max < 0) {
                        positive = false;
                        if (max > min) {
                            long tmp = min;
                            min = max;
                            max = tmp;
                        }
                        max = min - max;
                    } else {
                        if (max < min) {
                            long tmp = min;
                            min = max;
                            max = tmp;
                        }
                        max = max - min;
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
            retVal += min;
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
