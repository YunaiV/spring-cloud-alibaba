/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.alibaba.dubbo.bootstrap;

import com.alibaba.dubbo.config.annotation.Reference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.alibaba.dubbo.annotation.DubboTransported;
import org.springframework.cloud.alibaba.dubbo.service.EchoService;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dubbo Spring Cloud Bootstrap
 */
@EnableDiscoveryClient // 开启注册发现
@EnableAutoConfiguration // 开启自动配置
@EnableFeignClients // 开启 Feign Client
@RestController
public class DubboSpringCloudBootstrap {

    @Reference(version = "1.0.0")
    private EchoService echoService;

    @Autowired
    @Lazy
    private FeignEchoService feignEchoService;

    @Autowired
    @Lazy
    private DubboFeignEchoService dubboFeignEchoService;

    @GetMapping(value = "/dubbo/call/echo")
    public String dubboEcho(@RequestParam("message") String message) {
        return echoService.echo(message);
    }

    @GetMapping(value = "/feign/call/echo")
    public String feignEcho(@RequestParam("message") String message) {
        return feignEchoService.echo(message);
    }

    @GetMapping(value = "/feign-dubbo/call/echo")
    public String feignDubboEcho(@RequestParam("message") String message) {
        return dubboFeignEchoService.echo(message);
    }

    @FeignClient("spring-cloud-alibaba-dubbo")
    public interface FeignEchoService {

        @GetMapping(value = "/echo")
        String echo(@RequestParam("message") String message);

    }

    /**
     * 相比 {@link FeignEchoService} 来说，多了 @DubboTransported 注解
     */
    @FeignClient("spring-cloud-alibaba-dubbo")
    public interface DubboFeignEchoService {

        @GetMapping(value = "/echo")
        @DubboTransported
        String echo(@RequestParam("message") String message);

        @PostMapping(value = "/plus")
        @DubboTransported
        String plus(@RequestParam("a") int a, @RequestParam("b") int b);

    }

    @Bean
    public ApplicationRunner applicationRunner() {
        return arguments -> {
            // Dubbo Service call
            System.out.println(echoService.echo("mercyblitz：1"));
            // Spring Cloud Open Feign REST Call
            System.out.println(feignEchoService.echo("mercyblitz：2"));
            // Spring Cloud Open Feign REST Call (Dubbo Transported)
            System.out.println(dubboFeignEchoService.echo("mercyblitz：3"));
            System.out.println(dubboFeignEchoService.plus(1, 2));
        };
    }

    public static void main(String[] args) {
        new SpringApplicationBuilder(DubboSpringCloudBootstrap.class)
                .run(args);
    }

}



