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
package org.springframework.cloud.alibaba.dubbo.metadata.resolver;

import feign.Contract;
import feign.Feign;
import org.springframework.cloud.alibaba.dubbo.annotation.DubboTransported;
import org.springframework.cloud.alibaba.dubbo.metadata.DubboTransportedMethodMetadata;
import org.springframework.cloud.alibaba.dubbo.metadata.MethodMetadata;
import org.springframework.cloud.alibaba.dubbo.metadata.RequestMetadata;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.PropertyResolver;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link MethodMetadata} Resolver for the {@link DubboTransported}  annotated classes or methods in client side.
 *
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 * @see DubboTransportedMethodMetadata
 */
public class DubboTransportedMethodMetadataResolver {

    private static final Class<DubboTransported> DUBBO_TRANSPORTED_CLASS = DubboTransported.class;

    /**
     * 属性解析器
     */
    private final PropertyResolver propertyResolver;
    /**
     * Feign Contract 对象
     */
    private final Contract contract;

    public DubboTransportedMethodMetadataResolver(PropertyResolver propertyResolver, Contract contract) {
        this.propertyResolver = propertyResolver;
        this.contract = contract;
    }

    public Map<DubboTransportedMethodMetadata, RequestMetadata> resolve(Class<?> targetType) {
        // 获得指定类的 DubboTransportedMethodMetadata 集合
        Set<DubboTransportedMethodMetadata> dubboTransportedMethodMetadataSet = resolveDubboTransportedMethodMetadataSet(targetType);
        // 获得指定类的 RequestMetadata 映射。其中，KEY 为 configKey
        Map<String, RequestMetadata> requestMetadataMap = resolveRequestMetadataMap(targetType);
        // 转换成 DubboTransportedMethodMetadata 和 RequestMetadata 的映射
        return dubboTransportedMethodMetadataSet
                .stream()
                .collect(Collectors.toMap(methodMetadata -> methodMetadata, methodMetadata ->
                        requestMetadataMap.get(Feign.configKey(targetType, methodMetadata.getMethod()))
                ));
    }

    protected Set<DubboTransportedMethodMetadata> resolveDubboTransportedMethodMetadataSet(Class<?> targetType) {
        // The public methods of target interface
        Method[] methods = targetType.getMethods();
        // 创建 DubboTransportedMethodMetadata 数组
        Set<DubboTransportedMethodMetadata> methodMetadataSet = new LinkedHashSet<>();
        // 遍历方法
        for (Method method : methods) {
            // 如果有 @DubboTransported 注解
            DubboTransported dubboTransported = resolveDubboTransported(method); // ①
            // 如果有，则创建成  DubboTransportedMethodMetadata 对象，并添加到 methodMetadataSet 中
            if (dubboTransported != null) {
                // 创建 ②
                DubboTransportedMethodMetadata methodMetadata = createDubboTransportedMethodMetadata(method, dubboTransported);
                // 添加
                methodMetadataSet.add(methodMetadata);
            }
        }
        return methodMetadataSet;
    }

    private Map<String, RequestMetadata> resolveRequestMetadataMap(Class<?> targetType) {
        return contract.parseAndValidatateMetadata(targetType) // 获得指定类的 Feign MethodMetadata 集合
                .stream().collect(Collectors.toMap(feign.MethodMetadata::configKey, this::requestMetadata)); // 创建 RequestMetadata 对象
    }

    private RequestMetadata requestMetadata(feign.MethodMetadata methodMetadata) {
        return new RequestMetadata(methodMetadata.template());
    }

    // ②
    private DubboTransportedMethodMetadata createDubboTransportedMethodMetadata(Method method, DubboTransported dubboTransported) {
        // 创建 DubboTransportedMethodMetadata 对象
        DubboTransportedMethodMetadata methodMetadata = new DubboTransportedMethodMetadata(method);
       // 解析属性，并设置到 methodMetadata 中
        String protocol = propertyResolver.resolvePlaceholders(dubboTransported.protocol());
        String cluster = propertyResolver.resolvePlaceholders(dubboTransported.cluster());
        methodMetadata.setProtocol(protocol);
        methodMetadata.setCluster(cluster);
        return methodMetadata;
    }

    // ①
    private DubboTransported resolveDubboTransported(Method method) {
        // 先从方法上，获得 @DubboTransported 注解
        DubboTransported dubboTransported = AnnotationUtils.findAnnotation(method, DUBBO_TRANSPORTED_CLASS);
        // 如果获得不到，则从类上，获得 @DubboTransported 注解
        if (dubboTransported == null) { // Attempt to find @DubboTransported in the declaring class
            Class<?> declaringClass = method.getDeclaringClass();
            dubboTransported = AnnotationUtils.findAnnotation(declaringClass, DUBBO_TRANSPORTED_CLASS);
        }
        return dubboTransported;
    }

}