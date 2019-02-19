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

import com.alibaba.dubbo.rpc.service.GenericService;
import org.springframework.cloud.alibaba.dubbo.metadata.MethodMetadata;
import org.springframework.cloud.alibaba.dubbo.metadata.MethodParameterMetadata;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Dubbo {@link GenericService} for {@link InvocationHandler}
 *
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 */
public class DubboInvocationHandler implements InvocationHandler {

    private final Map<Method, GenericService> genericServicesMap;

    private final Map<Method, MethodMetadata> methodMetadata;

    private final InvocationHandler defaultInvocationHandler;

    public DubboInvocationHandler(Map<Method, GenericService> genericServicesMap,
                                  Map<Method, MethodMetadata> methodMetadata,
                                  InvocationHandler defaultInvocationHandler) {
        this.genericServicesMap = genericServicesMap;
        this.methodMetadata = methodMetadata;
        this.defaultInvocationHandler = defaultInvocationHandler;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 获得 GenericService 对象
        GenericService genericService = genericServicesMap.get(method);
        // 获得 MethodMetadata 对象
        MethodMetadata methodMetadata = this.methodMetadata.get(method);

        // 情况一，如果任一不存在，使用默认的 defaultInvocationHandler
        if (genericService == null || methodMetadata == null) {
            return defaultInvocationHandler.invoke(proxy, method, args);
        }

        // 情况二，执行泛化调用
        String methodName = methodMetadata.getName(); // 方法名
        String[] parameterTypes = methodMetadata
                .getParams()
                .stream()
                .map(MethodParameterMetadata::getType)
                .toArray(String[]::new); // 参数类型
        return genericService.$invoke(methodName, parameterTypes, args);
    }

}