/*
 * Copyright 2013-2015 the original author or authors.
 *
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
 */

package route

import com.google.common.collect.Lists
import com.netflix.config.ConfigurationManager
import com.netflix.loadbalancer.ILoadBalancer
import com.netflix.loadbalancer.LoadBalancerBuilder
import com.netflix.zuul.context.Debug
import demo.hystrix.FirstLevelRibbonCommand
import demo.hystrix.RibbonCommand
import org.apache.http.Header

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.netflix.loadbalancer.Server;

import com.netflix.client.ClientException;
import com.netflix.client.ClientFactory;
import com.netflix.client.http.HttpRequest.Verb;
import com.netflix.client.http.HttpResponse;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.netflix.niws.client.http.RestClient;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.exception.ZuulException

import java.util.zip.GZIPInputStream;


public class RibbonRoutingFilter extends ZuulFilter {

    public static final String CONTENT_ENCODING = "Content-Encoding";

    public RibbonRoutingFilter() {
    }

    @Override
    public String filterType() {
        return "route";
    }

    @Override
    public int filterOrder() {
        return 10;
    }

    @Override
    public boolean shouldFilter() {
        RequestContext ctx = RequestContext.getCurrentContext();
        return true;
    }

    @Override
    public Object run() {
        RequestContext context = RequestContext.getCurrentContext();
        HttpServletRequest request = context.getRequest();

		Map<String, String> headers = new HashMap<String, String>();
		Map<String, String> params = new HashMap<String, String>();

        Verb verb = getVerb(request);
        InputStream requestEntity = getRequestBody(request);

        String serviceId = (String) context.get("serviceId");
        Boolean retryable = (Boolean) context.get("retryable");

        ConfigurationManager.loadPropertiesFromResources("sample-client.properties");
        RestClient restClient = (RestClient) ClientFactory.getNamedClient("sample-client");
        Debug.addRequestDebug("CLIETN NAME==> "+restClient.getClientName());

        String uri = request.getRequestURI();
        if (context.get("requestURI") != null) {
            uri = (String) context.get("requestURI");
        }
        // remove double slashes
        uri = uri.replace("//", "/");
        String service = (String) context.get("serviceId");

        try {
            HttpResponse response = forward(restClient, service, verb, uri, retryable, headers, params,
                    requestEntity);
            setResponse(response);
            return response;
        }
        catch (Exception ex) {
            context.set("error.status_code", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            context.set("error.exception", ex);
            ex.printStackTrace();
            Debug.addRequestDebug(ex.getLocalizedMessage())
        }
        return null;
    }

    private HttpResponse forward(RestClient restClient, String service, Verb verb, String uri, Boolean retryable,
                                 Map<String, String> headers, Map<String, String> params,
                                 InputStream requestEntity) throws Exception {

        String host1  = "localhost";
        String host2 = "localhost";
        
        RibbonCommand command = new FirstLevelRibbonCommand("PonyService", restClient, verb, uri, retryable,
                headers, params, requestEntity, host1, host2);
        try {
            HttpResponse response = command.execute();

            return response;
        }
        catch (HystrixRuntimeException ex) {

            if (ex.getFallbackException() != null
                    && ex.getFallbackException().getCause() != null
                    && ex.getFallbackException().getCause() instanceof ClientException) {
                ClientException cause = (ClientException) ex.getFallbackException()
                        .getCause();
                throw new ZuulException(cause, "Forwarding error", 500, cause
                        .getErrorType().toString());
            }
            throw new ZuulException(ex, "Forwarding error", 500, ex.getFailureType()
                    .toString());
        }

    }

    private Map<String, String> revertHeaders(
            Map<String, Collection<String>> headers) {
        Map<String, String> map = new HashMap<String, String>();
//		for (Entry<String, Collection<String>> entry : headers.entrySet()) {
//			map.put(entry.getKey(), new ArrayList<String>(entry.getValue()));
//		}
        return map;
    }

    private InputStream getRequestBody(HttpServletRequest request) {
        InputStream requestEntity = null;
        if (request.getMethod().equals("DELETE")) {
            return null;
        }
        try {
            requestEntity = (InputStream) RequestContext.getCurrentContext().get(
                    "requestEntity");
            if (requestEntity == null) {
                requestEntity = request.getInputStream();
            }
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
        return requestEntity;
    }

    private Verb getVerb(HttpServletRequest request) {
        String sMethod = request.getMethod();
        return getVerb(sMethod);
    }

    private Verb getVerb(String sMethod) {
        if (sMethod == null)
            return Verb.GET;
        try {
            return Verb.valueOf(sMethod.toUpperCase());
        }
        catch (IllegalArgumentException e) {
            return Verb.GET;
        }
    }

    private void setResponse(HttpResponse resp) throws ClientException, IOException {
        RequestContext context = RequestContext.getCurrentContext();
        RequestContext.getCurrentContext().setResponseStatusCode(resp.getStatus());
        //RequestContext.getCurrentContext().setResponseDataStream(!resp.hasEntity() ? null : resp.getInputStream());

        byte[] origBytes = resp.getInputStream().bytes
        ByteArrayInputStream byteStream = new ByteArrayInputStream(origBytes)
        InputStream inputStream = byteStream
        if (RequestContext.currentContext.responseGZipped) {
            inputStream = new GZIPInputStream(byteStream);
        }

        context.setResponseDataStream(new ByteArrayInputStream(origBytes))

        if (resp.getHttpHeaders().containsHeader("content-length")){
            //context.addZuulResponseHeader("content-length", resp.getHttpHeaders().getFirstValue("content-length"));
        }

    }

}
