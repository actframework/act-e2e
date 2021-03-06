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

import org.osgl.exception.FastRuntimeException;

public class ErrorMessage extends FastRuntimeException {
    public ErrorMessage(String message) {
        super(message);
    }

    public ErrorMessage(String message, Object... args) {
        super(message, args);
    }

    public ErrorMessage(Throwable cause) {
        super(cause);
    }

    public ErrorMessage(Throwable cause, String message, Object... args) {
        super(cause, message, args);
    }

    public static void errorIf(boolean test, String message, Object... args) {
        if (test) {
            error(message, args);
        }
    }

    public static void errorIfNot(boolean test, String message, Object... args) {
        errorIf(!test, message, args);
    }

    public static ErrorMessage error(String message, Object... args) {
        throw new ErrorMessage(message, args);
    }

    public static ErrorMessage error(Throwable cause, String message, Object... args) {
        throw new ErrorMessage(cause, message, args);
    }
}
