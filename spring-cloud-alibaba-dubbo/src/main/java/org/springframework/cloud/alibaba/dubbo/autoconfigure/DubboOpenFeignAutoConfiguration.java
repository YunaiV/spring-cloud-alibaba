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
package org.springframework.cloud.alibaba.dubbo.autoconfigure;

import feign.Contract;
import feign.Feign;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.alibaba.dubbo.metadata.repository.DubboServiceMetadataRepository;
import org.springframework.cloud.alibaba.dubbo.metadata.resolver.DubboServiceBeanMetadataResolver;
import org.springframework.cloud.alibaba.dubbo.metadata.resolver.MetadataResolver;
import org.springframework.cloud.alibaba.dubbo.openfeign.TargeterBeanPostProcessor;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;


/**
 * Dubbo Feign Auto-{@link Configuration Configuration}
 *
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 */
@ConditionalOnClass(value = Feign.class) // 存在 Feign 类的时候，即存在 feign 依赖
@AutoConfigureAfter(FeignAutoConfiguration.class) // 在 FeignAutoConfiguration 配置类之后初始化
@Configuration
public class DubboOpenFeignAutoConfiguration {

    @Value("${spring.application.name}")
    private String currentApplicationName;

    @Bean // 创建 DubboServiceBeanMetadataResolver 对象
    @ConditionalOnMissingBean
    public MetadataResolver metadataJsonResolver(ObjectProvider<Contract> contract) {
        return new DubboServiceBeanMetadataResolver(currentApplicationName, contract);
    }

    @Bean // 创建 TargeterBeanPostProcessor 对象
    public TargeterBeanPostProcessor targeterBeanPostProcessor(Environment environment,
                                                               DubboServiceMetadataRepository dubboServiceMetadataRepository) {
        return new TargeterBeanPostProcessor(environment, dubboServiceMetadataRepository);
    }

}