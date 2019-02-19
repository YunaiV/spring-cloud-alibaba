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
package org.springframework.cloud.alibaba.dubbo.metadata.service;

import org.springframework.cloud.alibaba.dubbo.metadata.ServiceRestMetadata;

import java.util.Set;

/**
 * Config Service for Metadata
 *
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 */
public interface MetadataConfigService {

    /**
     * 发布指定服务的 Rest 元数据
     *
     * @param serviceName 服务名
     * @param serviceRestMetadata ServiceRestMetadata 集合
     */
    void publishServiceRestMetadata(String serviceName, Set<ServiceRestMetadata> serviceRestMetadata);

    /**
     * 获得指定服务的 Rest 元数据
     *
     * @param serviceName 服务名
     * @return ServiceRestMetadata 集合
     */
    Set<ServiceRestMetadata> getServiceRestMetadata(String serviceName);

}