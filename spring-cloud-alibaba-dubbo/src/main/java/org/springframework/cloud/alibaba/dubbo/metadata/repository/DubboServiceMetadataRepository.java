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
package org.springframework.cloud.alibaba.dubbo.metadata.repository;

import com.alibaba.dubbo.config.spring.ReferenceBean;
import com.alibaba.dubbo.rpc.service.GenericService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.alibaba.dubbo.metadata.MethodMetadata;
import org.springframework.cloud.alibaba.dubbo.metadata.RequestMetadata;
import org.springframework.cloud.alibaba.dubbo.metadata.ServiceRestMetadata;
import org.springframework.cloud.alibaba.dubbo.metadata.service.MetadataConfigService;
import org.springframework.cloud.alibaba.dubbo.registry.SpringCloudRegistry;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Dubbo Service Metadata {@link Repository}
 *
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 */
@Repository
public class DubboServiceMetadataRepository {

    /**
     * RequestMetadata 和 ReferenceBean 的映射
     *
     * Key is application name
     * Value is  Map<RequestMetadata, ReferenceBean<GenericService>>
     */
    private Map<String, Map<RequestMetadata, ReferenceBean<GenericService>>> referenceBeansRepository = new HashMap<>();

    /**
     * RequestMetadata 和 MethodMetadata 的映射
     *
     * Key is application name
     */
    private Map<String, Map<RequestMetadata, MethodMetadata>> methodMetadataRepository = new HashMap<>();

    @Autowired
    private MetadataConfigService metadataConfigService;

    public void updateMetadata(String serviceName) {
        // 获得 serviceName 对应的 RequestMetadata 和 ReferenceBean 的映射
        Map<RequestMetadata, ReferenceBean<GenericService>> genericServicesMap = referenceBeansRepository.computeIfAbsent(serviceName, k -> new HashMap<>());
        // 获得 serviceName 对应的 RequestMetadata 和 MethodMetadata 的映射
        Map<RequestMetadata, MethodMetadata> methodMetadataMap = methodMetadataRepository.computeIfAbsent(serviceName, k -> new HashMap<>());
        // 获得 serviceName 对应的  ServiceRestMetadata 集合
        Set<ServiceRestMetadata> serviceRestMetadataSet = metadataConfigService.getServiceRestMetadata(serviceName);

        // 遍历 ServiceRestMetadata 集合，创建对应的 ReferenceBean ，获得对应的 MethodMetadata 对象
        for (ServiceRestMetadata serviceRestMetadata : serviceRestMetadataSet) {
            // 创建对应的 ReferenceBean
            ReferenceBean<GenericService> referenceBean = adaptReferenceBean(serviceRestMetadata);
            // 遍历 RestMethodMetadata 集合，添加到 genericServicesMap 和 methodMetadataMap 中，进行缓存
            serviceRestMetadata.getMeta().forEach(restMethodMetadata -> {
                RequestMetadata requestMetadata = restMethodMetadata.getRequest();
                genericServicesMap.put(requestMetadata, referenceBean);
                methodMetadataMap.put(requestMetadata, restMethodMetadata.getMethod());
            });
        }
    }

    public ReferenceBean<GenericService> getReferenceBean(String serviceName, RequestMetadata requestMetadata) {
        return getReferenceBeansMap(serviceName).get(requestMetadata);
    }

    public MethodMetadata getMethodMetadata(String serviceName, RequestMetadata requestMetadata) {
        return getMethodMetadataMap(serviceName).get(requestMetadata);
    }

    private ReferenceBean<GenericService> adaptReferenceBean(ServiceRestMetadata serviceRestMetadata) {
        // 获得相应的属性
        String dubboServiceName = serviceRestMetadata.getName();
        String[] segments = SpringCloudRegistry.getServiceSegments(dubboServiceName);
        String interfaceName = SpringCloudRegistry.getServiceInterface(segments);
        String version = SpringCloudRegistry.getServiceVersion(segments);
        String group = SpringCloudRegistry.getServiceGroup(segments);

        // 创建 ReferenceBean 对象，并设置相关属性
        ReferenceBean<GenericService> referenceBean = new ReferenceBean<GenericService>();
        referenceBean.setGeneric(true);
        referenceBean.setInterface(interfaceName);
        referenceBean.setVersion(version);
        referenceBean.setGroup(group);
        return referenceBean;
    }

    private Map<RequestMetadata, ReferenceBean<GenericService>> getReferenceBeansMap(String serviceName) {
        return referenceBeansRepository.getOrDefault(serviceName, Collections.emptyMap());
    }

    private Map<RequestMetadata, MethodMetadata> getMethodMetadataMap(String serviceName) {
        return methodMetadataRepository.getOrDefault(serviceName, Collections.emptyMap());
    }
}
