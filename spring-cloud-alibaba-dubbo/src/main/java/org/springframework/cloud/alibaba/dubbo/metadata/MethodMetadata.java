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
package org.springframework.cloud.alibaba.dubbo.metadata;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.ClassUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * {@link Method} Metadata
 *
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 */
public class MethodMetadata {

    /**
     * 方法名
     */
    private String name;
    /**
     * 返回类型
     */
    private String returnType;
    /**
     * 方法参数元数据的数组
     */
    private List<MethodParameterMetadata> params;
    /**
     * 方法
     */
    @JsonIgnore // 不存储到配置中心
    private Method method;

    public MethodMetadata() {
        this.params = new LinkedList<>();
    }

    public MethodMetadata(Method method) {
        this.name = method.getName();
        // 获得返回类型
        this.returnType = ClassUtils.getName(method.getReturnType());
        // 初始化 params
        this.params = initParameters(method);
        this.method = method;
    }

    private List<MethodParameterMetadata> initParameters(Method method) {
        // 获得参数数量
        int parameterCount = method.getParameterCount();
        // 如果参数不存在，则返回空数组
        if (parameterCount < 1) {
            return Collections.emptyList();
        }
        // 创建 MethodParameterMetadata 数组
        List<MethodParameterMetadata> params = new ArrayList<>(parameterCount);
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameterCount; i++) {
            // 获得 Parameter 对象
            Parameter parameter = parameters[i];
            // 转换成 MethodParameterMetadata 对象
            MethodParameterMetadata param = toMethodParameterMetadata(i, parameter);
            // 添加到 params 中
            params.add(param);
        }
        return params;
    }

    private MethodParameterMetadata toMethodParameterMetadata(int index, Parameter parameter) {
        // 创建 MethodParameterMetadata 对象
        MethodParameterMetadata metadata = new MethodParameterMetadata();
        metadata.setIndex(index); // 方法参数的位置
        metadata.setName(parameter.getName()); // 方法参数的名字
        metadata.setType(parameter.getType().getTypeName()); // 方法参数的类型
        return metadata;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public List<MethodParameterMetadata> getParams() {
        return params;
    }

    public void setParams(List<MethodParameterMetadata> params) {
        this.params = params;
    }

    public Method getMethod() {
        return method;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodMetadata that = (MethodMetadata) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(returnType, that.returnType) &&
                Objects.equals(params, that.params);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, returnType, params);
    }
}
