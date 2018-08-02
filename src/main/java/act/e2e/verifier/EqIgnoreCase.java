package act.e2e.verifier;

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

import org.osgl.util.C;
import org.osgl.util.S;

import java.util.List;

public class EqIgnoreCase extends Verifier<EqIgnoreCase> {

    @Override
    public boolean verify(Object value) {
        return S.eq(S.string(value).toUpperCase(), S.string(initVal).toUpperCase(), S.IGNORECASE);
    }

    @Override
    protected List<String> aliases() {
        return C.list("eqi", "equalsIgnoreCase");
    }
}
