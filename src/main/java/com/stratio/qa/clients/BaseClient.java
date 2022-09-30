/*
 * Copyright (C) 2014 Stratio (http://stratio.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.stratio.qa.clients;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.stratio.qa.models.BaseResponse;
import com.stratio.qa.models.BaseResponseList;
import com.stratio.qa.specs.CommonG;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.BoundRequestBuilder;
import org.asynchttpclient.Param;
import org.asynchttpclient.Response;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BaseClient {

    protected AsyncHttpClient httpClient;

    protected Logger log;

    protected ObjectMapper mapper = new ObjectMapper();

    private String port = "443";

    private CommonG commonG;

    protected BaseClient(CommonG common) {

        this.httpClient = common.getClient();
        this.commonG = common;
        this.log = common.getLogger();
    }


    public void setPort(String port) {
        this.port = port;
    }

    public String getPort() {
        return  this.port;
    }

    public BaseResponse map(Response response) throws Exception {
        BaseResponse r = new BaseResponse();
        r.setHttpStatus(response.getStatusCode());
        r.setRawResponse(response.getResponseBody());
        return r;
    }

    public <T extends BaseResponse> T map(Response response, Class<T> type) throws Exception {
        T r;

        try {
            r = mapper.readValue(response.getResponseBody(), type);
        } catch (Exception e) {
            log.warn(e.getMessage());
            log.warn("Error mapping response to " + type.getCanonicalName() + ". Setting empty...");
            r = type.newInstance();
        }

        r.setHttpStatus(response.getStatusCode());
        r.setRawResponse(response.getResponseBody());
        return r;
    }

    public <T> BaseResponseList<T> mapList(Response response, Class<T> type) throws Exception {
        List<T> r;
        BaseResponseList<T> rList = new BaseResponseList<>();
        TypeFactory t = TypeFactory.defaultInstance();

        try {
            r = mapper.readValue(response.getResponseBody(), t.constructCollectionType(ArrayList.class, type));
        } catch (Exception e) {
            log.warn(e.getMessage());
            log.warn("Error mapping response to " + type.getCanonicalName() + ". Setting empty...");
            r =  new ArrayList<>();
        }

        rList.setList(r);
        rList.setHttpStatus(response.getStatusCode());
        rList.setRawResponse(response.getResponseBody());
        return rList;
    }

    protected Response get(String endpoint) throws Exception {
        BoundRequestBuilder request = this.httpClient.prepareGet(endpoint);
        request = request.setCookies(commonG.getCookies());
        Response response = request.execute().get();
        this.log.debug("GET to " + response.getUri() + ":" + response.getResponseBody());
        return response;
    }

    protected Response get(String endpoint, Map<String, String> queryParams) throws Exception {
        BoundRequestBuilder request = this.httpClient.prepareGet(endpoint);
        request = request.setCookies(commonG.getCookies());
        List<Param> params = queryParams.entrySet().stream()
                .map(queryParam -> new Param(queryParam.getKey(), queryParam.getValue())).collect(Collectors.toList());
        request = request.setQueryParams(params);
        Response response = request.execute().get();
        this.log.debug("GET to " + response.getUri() + ":" + response.getResponseBody());
        return response;
    }

    protected Response delete(String endpoint) throws Exception {
        BoundRequestBuilder request = this.httpClient.prepareDelete(endpoint);
        request = request.setCookies(commonG.getCookies());
        Response response = request.execute().get();
        this.log.debug("DELETE to " + response.getUri() + ":" + response.getResponseBody());
        return response;
    }

    protected Response put(String endpoint) throws Exception {
        BoundRequestBuilder request = this.httpClient.preparePut(endpoint);
        request = request.setCookies(commonG.getCookies());
        Response response = request.execute().get();
        this.log.debug("PUT to " + response.getUri() + ":" + response.getResponseBody());
        return response;
    }

    protected Response post(String endpoint, String data) throws Exception {
        BoundRequestBuilder request = this.httpClient.preparePost(endpoint);
        request = request.setHeader("Content-Type", "application/json; charset=UTF-8");
        request = request.setBody(data);
        request = request.setCookies(commonG.getCookies());
        Response response = request.execute().get();
        this.log.debug("POST to " + response.getUri() + ":" + response.getResponseBody());
        return response;
    }

    protected Response put(String endpoint, String data) throws Exception {
        BoundRequestBuilder request = this.httpClient.preparePut(endpoint);
        request = request.setHeader("Content-Type", "application/json; charset=UTF-8");
        request = request.setBody(data);
        request = request.setCookies(commonG.getCookies());
        Response response = request.execute().get();
        this.log.debug("PUT to " + response.getUri() + ":" + response.getResponseBody());
        return response;
    }
}
