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

import act.data.annotation.Data;
import act.e2e.req_modifier.RequestModifier;
import act.util.SimpleBean;
import com.alibaba.fastjson.JSON;
import org.osgl.http.H;
import org.osgl.util.C;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class RequestSpec implements SimpleBean {

    public static final RequestSpec RS_CLEAR_FIXTURE = clearFixture();

    public H.Method method;
    public String url;
    public List<RequestModifier> modifiers = new ArrayList<>();
    public Map<String, Object> params = new HashMap<>();
    public Map<String, String> headers = new HashMap<>();
    public String jsonBody;

    public RequestSpec() {}

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }

    private static RequestSpec clearFixture() {
        RequestSpec rs = new RequestSpec();
        rs.method = H.Method.DELETE;
        rs.url = "/~/e2e/fixtures";
        return rs;
    }

    public static RequestSpec loadFixtures(List<String> fixtures) {
        RequestSpec rs = new RequestSpec();
        rs.method = H.Method.POST;
        rs.url = "/~/e2e/fixtures";
        rs.jsonBody = JSON.toJSONString(C.Map("fixtures", fixtures));
        return rs;
    }
}
