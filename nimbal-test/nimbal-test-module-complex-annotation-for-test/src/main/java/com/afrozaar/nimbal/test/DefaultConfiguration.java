package com.afrozaar.nimbal.test;

import com.afrozaar.nimbal.annotations.Module;

import org.springframework.context.annotation.Bean;

@Module(name = "ComplexModule", order = 51, parentModule = "bar", parentModuleClassesOnly = true, ringFenceClassBlackListRegex = { "foo", "bar" })
public class DefaultConfiguration {

    @Bean
    public SpringManagedObject getBean() {
        return new SpringManagedObject();
    }
}
