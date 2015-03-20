package demo.hystrix;

import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import com.netflix.client.ClientException;
import com.netflix.client.http.HttpHeaders;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerBuilder;
import com.netflix.loadbalancer.Server;
import com.netflix.niws.client.http.RestClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Created by mcorrea on 3/15/15.
 */
public class SecondLevelRibbonCommand extends RibbonCommand {

    private RestClient restClient;
    private HttpRequest.Verb verb;
    private URI uri;
    private Boolean retryable;
    private Map<String, String> headers;
    private Map<String, String> params;
    private InputStream requestEntity;
    private String host1;
    private String host2;
    String cache;
    
    public SecondLevelRibbonCommand(RestClient restClient, HttpRequest.Verb verb,
                                    String uri, Boolean retryable, Map<String, String> headers,
                                    Map<String, String> params, InputStream requestEntity,
                                    String host1, String host2, String cache
    ) throws URISyntaxException {
        this("default", restClient, verb, uri, retryable, headers, params, requestEntity, host1, host2, cache);
    }

    public SecondLevelRibbonCommand(String commandKey, RestClient restClient, HttpRequest.Verb verb,
                                    String uri, Boolean retryable, Map<String, String> headers,
                                    Map<String, String> params, InputStream requestEntity,
                                    String host1, String host2, String cache
    ) throws URISyntaxException {
        super(commandKey, restClient, verb, uri, retryable, headers, params, requestEntity, host1, host2);
        this.restClient = restClient;
        this.verb = verb;
        this.uri = new URI(uri);
        this.retryable = retryable;
        this.headers = headers;
        this.params = params;
        this.requestEntity = requestEntity;
        this.host1 = host1;
        this.host2 = host2;
        this.cache = cache;
    }

    @Override
    ILoadBalancer getLoadBalancerClient() {
        ILoadBalancer loadBalancer;
        List<Server> serverList =  Lists.newArrayList(
                new Server(host2, 9393));

        loadBalancer = LoadBalancerBuilder.newBuilder().buildFixedServerListLoadBalancer(serverList);
        return loadBalancer;
    }
    /**
     * Static content second level fallback
     * @return
     */

    @Override
    protected HttpResponse getFallback() {
        final String str = cache;
        final  InputStream stream = new ByteArrayInputStream(str.getBytes());
        HttpResponse resp = new HttpResponse() {

            @Override
            public int getStatus() {
                return 200;
            }

            @Override
            public String getStatusLine() {
                return null;
            }

            @Override
            public Map<String, Collection<String>> getHeaders() {
                Map<String, Collection<String>> headers = new HashMap<String, Collection<String>>();
                headers.put("Content-Type", Lists.newArrayList("application/json"));
                return headers;
            }

            @Override
            public HttpHeaders getHttpHeaders() {


                final HttpHeaders httpheaders = new HttpHeaders() {
                    @Override
                    public String getFirstValue(String s) {

                        try{
                            return ""+str.getBytes("UTF-8").length;
                        }catch(Exception e){
                            return "1024";
                        }
                    }

                    @Override
                    public List<String> getAllValues(String s) {
                        return null;
                    }

                    @Override
                    public List<Map.Entry<String, String>> getAllHeaders() {
                        return null;
                    }

                    @Override
                    public boolean containsHeader(String s) {
                        return true;
                    }
                };

                return httpheaders;
            }

            @Override
            public void close() {

            }

            @Override
            public InputStream getInputStream() {

                return stream;
            }

            @Override
            public boolean hasEntity() {
                return true;
            }

            @Override
            public <T> T getEntity(Class<T> aClass) throws Exception {
                return null;
            }

            @Override
            public <T> T getEntity(Type type) throws Exception {
                return null;
            }

            @Override
            public <T> T getEntity(TypeToken<T> typeToken) throws Exception {
                return null;
            }

            @Override
            public Object getPayload() throws ClientException {
                return null;
            }

            @Override
            public boolean hasPayload() {
                return false;
            }

            @Override
            public boolean isSuccess() {
                return true;
            }

            @Override
            public URI getRequestedURI() {
                return null;
            }
        };

        return resp;
    }
}
