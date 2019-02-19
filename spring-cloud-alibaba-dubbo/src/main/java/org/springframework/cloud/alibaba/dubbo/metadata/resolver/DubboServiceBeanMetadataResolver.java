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

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.config.spring.ServiceBean;

import feign.Contract;
import feign.Feign;
import feign.MethodMetadata;
import feign.Util;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.cloud.alibaba.dubbo.metadata.RequestMetadata;
import org.springframework.cloud.alibaba.dubbo.metadata.RestMethodMetadata;
import org.springframework.cloud.alibaba.dubbo.metadata.ServiceRestMetadata;
import org.springframework.cloud.alibaba.dubbo.registry.SpringCloudRegistry;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The metadata resolver for {@link Feign} for {@link ServiceBean Dubbo Service Bean} in the provider side.
 *
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 */
public class DubboServiceBeanMetadataResolver implements BeanClassLoaderAware, SmartInitializingSingleton,
        MetadataResolver {

    private static final String[] CONTRACT_CLASS_NAMES = {
            "feign.jaxrs2.JAXRS2Contract",
            "org.springframework.cloud.openfeign.support.SpringMvcContract",
    };

    /**
     * 当前应用名
     *
     * 不过，目前暂时并未用该参数
     */
    private final String currentApplicationName;
    /**
     * 当前类加载器
     */
    private ClassLoader classLoader;
    private final ObjectProvider<Contract> contract;

    /**
     * Feign Contract 数组
     *
     * https://www.jianshu.com/p/6582f8319f72
     */
    private Collection<Contract> contracts;

    public DubboServiceBeanMetadataResolver(String currentApplicationName, ObjectProvider<Contract> contract) {
        this.currentApplicationName = currentApplicationName;
        this.contract = contract;
    }

    @Override
    public void afterSingletonsInstantiated() {
        // 创建 Feign Contract 数组
        LinkedList<Contract> contracts = new LinkedList<>();

        // Add injected Contract if available, for example SpringMvcContract Bean under Spring Cloud Open Feign
        // 如果 contract 存在，则添加到 contracts 中
        contract.ifAvailable(contracts::add);

        // 遍历 CONTRACT_CLASS_NAMES 数组，创建对应的 Contract 对象，添加到 contracts 中
        Stream.of(CONTRACT_CLASS_NAMES)
                .filter(this::isClassPresent) // filter the existed classes
                .map(this::loadContractClass) // load Contract Class
                .map(this::createContract)    // create instance by the specified class
                .forEach(contracts::add);     // add the Contract instance into contracts

        // 赋值给 contracts 中
        this.contracts = Collections.unmodifiableCollection(contracts);
    }

    // 创建 Contract 对象
    private Contract createContract(Class<?> contractClassName) {
        return (Contract) BeanUtils.instantiateClass(contractClassName);
    }

    // 加载 contractClassName 对应的 Contract 实现类
    private Class<?> loadContractClass(String contractClassName) {
        return ClassUtils.resolveClassName(contractClassName, classLoader);
    }

    // 判断指定 className 类是否存在
    private boolean isClassPresent(String className) {
        return ClassUtils.isPresent(className, classLoader);
    }

    @Override
    public Set<ServiceRestMetadata> resolveServiceRestMetadata(ServiceBean serviceBean) {
        // 获得应用的 Bean 对象
        Object bean = serviceBean.getRef();
        // 获得 Bean 类型
        Class<?> beanType = bean.getClass();
        // 解析 Bean 类型对应的 RestMethodMetadata 集合
        Set<RestMethodMetadata> methodRestMetadata = resolveMethodRestMetadata(beanType);

        // 创建 ServiceRestMetadata 数组
        Set<ServiceRestMetadata> serviceRestMetadata = new LinkedHashSet<>();
        // 获得 ServiceBean 暴露的 URL 集合
        List<URL> urls = serviceBean.getExportedUrls();
        // 遍历 URL 集合，将 RestMethodMetadata 集合，封装成 ServiceRestMetadata 对象，然后添加到 serviceRestMetadata 中，最后返回。
        urls.stream()
                .map(SpringCloudRegistry::getServiceName)
                .forEach(serviceName -> {
                    ServiceRestMetadata metadata = new ServiceRestMetadata();
                    metadata.setName(serviceName);
                    metadata.setMeta(methodRestMetadata);
                    serviceRestMetadata.add(metadata);
                });
        return serviceRestMetadata;
    }

    @Override
    public Set<RestMethodMetadata> resolveMethodRestMetadata(Class<?> targetType) {
        // 获得 Method 集合
        List<Method> feignContractMethods = selectFeignContractMethods(targetType);
        // 转换成 RestMethodMetadata 集合
        return contracts.stream() // 遍历 contracts 数组
                .map(contract -> contract.parseAndValidatateMetadata(targetType)) // 返回目标类型的 Feign MethodMetadata 数组
                .flatMap(Collection::stream)
                .map(methodMetadata -> resolveMethodRestMetadata(methodMetadata, targetType, feignContractMethods)) // 将 Feign MethodMetadata 转换成 RestMethodMetadata 对象
                .collect(Collectors.toSet()); // 转换成 Set ，可以带来排重的效果
    }

    /**
     * Select feign contract methods
     * <p>
     * extract some code from {@link Contract.BaseContract#parseAndValidatateMetadata(java.lang.Class)}
     *
     * @param targetType
     * @return non-null
     */
    private List<Method> selectFeignContractMethods(Class<?> targetType) {
        List<Method> methods = new LinkedList<>();
        // 遍历目标的方法
        for (Method method : targetType.getMethods()) {
            // 忽略
            if (method.getDeclaringClass() == Object.class || // Object 声明的方法，例如说 equals 方法
                    (method.getModifiers() & Modifier.STATIC) != 0 || // 静态方法
                    Util.isDefault(method)) { // Feign 默认方法
                continue;
            }
            methods.add(method);
        }
        return methods;
    }

    protected RestMethodMetadata resolveMethodRestMetadata(MethodMetadata methodMetadata, // Feign MethodMetadata
                                                           Class<?> targetType,
                                                           List<Method> feignContractMethods) {
        // 获得 configKey 。例如说：DefaultEchoService#echo(String)
        String configKey = methodMetadata.configKey();
        // 获得匹配的 Method
        Method feignContractMethod = getMatchedFeignContractMethod(targetType, feignContractMethods, configKey);
        // 创建 RestMethodMetadata 对象，并设置其属性
        RestMethodMetadata metadata = new RestMethodMetadata();
        metadata.setRequest(new RequestMetadata(methodMetadata.template()));
        metadata.setMethod(new org.springframework.cloud.alibaba.dubbo.metadata.MethodMetadata(feignContractMethod));
        metadata.setIndexToName(methodMetadata.indexToName());
        return metadata;
    }

    private Method getMatchedFeignContractMethod(Class<?> targetType, List<Method> methods, String expectedConfigKey) {
        Method matchedMethod = null;
        // 遍历 Method 集合
        for (Method method : methods) {
            // 获得该方法的 configKey
            String configKey = Feign.configKey(targetType, method);
            // 如果相等，则进行返回。
            if (expectedConfigKey.equals(configKey)) {
                matchedMethod = method;
                break;
            }
        }
        return matchedMethod;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

}