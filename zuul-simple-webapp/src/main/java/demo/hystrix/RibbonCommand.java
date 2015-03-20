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

package demo.hystrix;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import com.google.common.reflect.TypeToken;
import com.netflix.client.ClientException;
import com.netflix.client.http.HttpHeaders;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpRequest.Builder;
import com.netflix.client.http.HttpRequest.Verb;
import com.netflix.client.http.HttpResponse;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.niws.client.http.RestClient;
import com.netflix.zuul.constants.ZuulConstants;
import com.netflix.zuul.context.Debug;
import com.netflix.zuul.context.RequestContext;


@SuppressWarnings("deprecation")
public abstract class RibbonCommand extends HystrixCommand<HttpResponse> {

	private RestClient restClient;

	private Verb verb;

	private URI uri;
	
	private Boolean retryable;

	private Map<String, String> headers;

	private Map<String, String> params;

	private InputStream requestEntity;

	public RibbonCommand(RestClient restClient, Verb verb, String uri,
			Boolean retryable,
			Map<String, String> headers,
            Map<String, String> params,
            InputStream requestEntity,
            String url1,
            String url2
    )
			throws URISyntaxException {
		this("default", restClient, verb, uri, retryable , headers, params, requestEntity, url1, url2);
	}

	public RibbonCommand(String commandKey, RestClient restClient, Verb verb, String uri,
			Boolean retryable,
            Map<String, String> headers,
            Map<String, String> params, 
            InputStream requestEntity,
            String url1,
            String url2
            )
			throws URISyntaxException {
		super(getSetter(commandKey));
		this.restClient = restClient;
		this.verb = verb;
		this.uri = new URI(uri);
		this.retryable = retryable;
		this.headers = headers;
		this.params = params;
		this.requestEntity = requestEntity;
	}

	private static Setter getSetter(String commandKey) {
		// we want to default to semaphore-isolation since this wraps
		// 2 others commands that are already thread isolated
		String name = ZuulConstants.ZUUL_EUREKA + commandKey + ".semaphore.maxSemaphores";
		DynamicIntProperty value = DynamicPropertyFactory.getInstance().getIntProperty(
				name, 100);
		HystrixCommandProperties.Setter setter = HystrixCommandProperties.Setter()
				.withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE)
				.withExecutionIsolationSemaphoreMaxConcurrentRequests(value.get());
		return Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("RibbonCommand"))
				.andCommandKey(HystrixCommandKey.Factory.asKey(commandKey))
				.andCommandPropertiesDefaults(setter);
	}

	@Override
	protected HttpResponse run() throws Exception {
		return forward();
	}

    
    abstract ILoadBalancer getLoadBalancerClient();

    private HttpResponse forward() throws Exception {
        RequestContext context = RequestContext.getCurrentContext();
		Builder builder = HttpRequest.newBuilder().verb(this.verb).uri(this.uri)
				.entity(this.requestEntity);

		if(retryable != null) {
			builder.setRetriable(retryable);
		}
		
		for (String name : this.headers.keySet()) {
			String value = this.headers.get(name);
			builder.header(name, value);

		}
		for (String name : this.params.keySet()) {
			String value = this.params.get(name);
			builder.queryParams(name, value);
		}
		HttpRequest httpClientRequest = builder.build();
        
        this.restClient.setLoadBalancer(getLoadBalancerClient());
        
        HttpResponse response = this.restClient
				.executeWithLoadBalancer(httpClientRequest);
        Debug.addRequestDebug("after exec ---");

        context.set("ribbonResponse", response);
        
        if(response.getStatus() != 200){
            throw new Exception("Service Error != 200");
        }
        
		return response;
	}

}
