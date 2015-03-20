/*
 * Copyright 2013 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */

import com.netflix.zuul.ZuulFilter
import com.netflix.zuul.context.RequestContext

/**
 * @author mhawthorne
 */
class PreDecorationFilter extends ZuulFilter {

    @Override
    int filterOrder() {
        return 5
    }

    @Override
    String filterType() {
        return "pre"
    }

    @Override
    boolean shouldFilter() {
        return true;
    }

    @Override
    Object run() {
        RequestContext ctx = RequestContext.getCurrentContext()

        //all microservices lives in localhost
        ctx.put("host1", "localhost")
        ctx.put("host2", "localhost")
        
        // sets origin
        if(ctx.getRequest().getRequestURI().matches("/[a-zA-Z]*[/]?service/jobs[?]?.*")) {
            ctx.put("service", "jobs")
            ctx.put("cache", "[{\"jobid\":\"123\", \"resume\": \"Im a job cached\"}]")
            
        }else if(ctx.getRequest().getRequestURI().matches("/[a-zA-Z]*[/]?service/resumes[?]?.*")) {
            ctx.put("service", "resumes")
            ctx.put("cache", "[{\"name\":\"Im a cache\", \"resume\": \"Im a resume cached\"}]")
            
        }else{
            ctx.setSendZuulResponse(false)
            
        }

        // sets custom header to send to the origin
        ctx.addOriginResponseHeader("cache-control", "max-age=3600");
    }

}
