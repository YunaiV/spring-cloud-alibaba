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
package org.springframework.cloud.alibaba.dubbo.openfeign;


import com.alibaba.dubbo.config.spring.ReferenceBean;
import com.alibaba.dubbo.rpc.service.GenericService;
import feign.Contract;
import feign.Target;
import org.springframework.cloud.alibaba.dubbo.metadata.DubboTransportedMethodMetadata;
import org.springframework.cloud.alibaba.dubbo.metadata.RequestMetadata;
import org.springframework.cloud.alibaba.dubbo.metadata.repository.DubboServiceMetadataRepository;
import org.springframework.cloud.alibaba.dubbo.metadata.resolver.DubboTransportedMethodMetadataResolver;
import org.springframework.cloud.openfeign.FeignContext;
import org.springframework.core.env.Environment;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * org.springframework.cloud.openfeign.Targeter {@link InvocationHandler}
 *
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 */
class TargeterInvocationHandler implements InvocationHandler {

    private final Object bean;

    private final Environment environment;

    private final DubboServiceMetadataRepository dubboServiceMetadataRepository;

    TargeterInvocationHandler(Object bean, Environment environment,
                              DubboServiceMetadataRepository dubboServiceMetadataRepository) {
        this.bean = bean;
        this.environment = environment;
        this.dubboServiceMetadataRepository = dubboServiceMetadataRepository;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        /**
         * args[0]: FeignClientFactoryBean factory
         * args[1]: Feign.Builder feign
         * args[2]: FeignContext context
         * args[3]: Target.HardCodedTarget<T> target
         */
        FeignContext feignContext = cast(args[2]);
        Target.HardCodedTarget<?> target = cast(args[3]);

        // 先调用原有 target 方法，返回默认的代理对象
        // Execute Targeter#target method first
        method.setAccessible(true);
        // Get the default proxy object
        Object defaultProxy = method.invoke(bean, args);
        // Create Dubbo Proxy if required
        // 如果符合创建 Dubbo 代理对象，则创建 Dubbo 代理对象。
        // 否则，使用默认的 defaultProxy 代理
        return createDubboProxyIfRequired(feignContext, target, defaultProxy);
    }

    private Object createDubboProxyIfRequired(FeignContext feignContext, Target target, Object defaultProxy) {
        // 尝试创建 DubboInvocationHandler
        DubboInvocationHandler dubboInvocationHandler = createDubboInvocationHandler(feignContext, target, defaultProxy);
        // 如果未创建成功，说明不符合条件，则返回默认的 defaultProxy 代理
        if (dubboInvocationHandler == null) {
            return defaultProxy;
        }
        // 如果创建成功，说明符合条件，则创建使用 dubboInvocationHandler 的动态代理
        return Proxy.newProxyInstance(target.type().getClassLoader(), new Class<?>[]{target.type()}, dubboInvocationHandler);
    }

    private DubboInvocationHandler createDubboInvocationHandler(FeignContext feignContext, Target target, Object defaultFeignClientProxy) {
        // Service name equals @FeignClient.name()
        String serviceName = target.name();
        Class<?> targetType = target.type();

        // Get Contract Bean from FeignContext
        // 获得 Feign Contract
        Contract contract = feignContext.getInstance(serviceName, Contract.class);

        // 创建 DubboTransportedMethodMetadataResolver 对象
        DubboTransportedMethodMetadataResolver resolver = new DubboTransportedMethodMetadataResolver(environment, contract);
        // 解析指定类，获得其 DubboTransportedMethodMetadata 和 RequestMetadata 的映射
        Map<DubboTransportedMethodMetadata, RequestMetadata> methodRequestMetadataMap = resolver.resolve(targetType);
        // 如果为空，则返回，说明不符合条件
        if (methodRequestMetadataMap.isEmpty()) { // @DubboTransported method was not found
            return null;
        }

        // Update Metadata
        // 初始化指定 `serviceName` 的元数据。此处，会从配置中心，获得元数据
        dubboServiceMetadataRepository.updateMetadata(serviceName);

        Map<Method, org.springframework.cloud.alibaba.dubbo.metadata.MethodMetadata> methodMetadataMap = new HashMap<>();
        Map<Method, GenericService> genericServicesMap = new HashMap<>();
        // 遍历 methodRequestMetadataMap 集合，初始化其 GenericService
        methodRequestMetadataMap.forEach((dubboTransportedMethodMetadata, requestMetadata) -> {
            // 获得 ReferenceBean 对象，并初始化其属性
            ReferenceBean<GenericService> referenceBean = dubboServiceMetadataRepository.getReferenceBean(serviceName, requestMetadata);
            referenceBean.setProtocol(dubboTransportedMethodMetadata.getProtocol());
            referenceBean.setCluster(dubboTransportedMethodMetadata.getCluster());
            // 添加到 genericServicesMap 中
            genericServicesMap.put(dubboTransportedMethodMetadata.getMethod(), referenceBean.get());
            // 获得 MethodMetadata 对象
            org.springframework.cloud.alibaba.dubbo.metadata.MethodMetadata methodMetadata = dubboServiceMetadataRepository.getMethodMetadata(serviceName, requestMetadata);
            // 添加到 methodMetadataMap 中
            methodMetadataMap.put(dubboTransportedMethodMetadata.getMethod(), methodMetadata);
        });

        // 获得默认的 defaultFeignClientProxy 中，默认的 InvocationHandler 对象
        InvocationHandler defaultFeignClientInvocationHandler = Proxy.getInvocationHandler(defaultFeignClientProxy);
        // 创建 DubboInvocationHandler 对象
        return new DubboInvocationHandler(genericServicesMap, methodMetadataMap, defaultFeignClientInvocationHandler);
    }

    private static <T> T cast(Object object) {
        return (T) object;
    }

}
