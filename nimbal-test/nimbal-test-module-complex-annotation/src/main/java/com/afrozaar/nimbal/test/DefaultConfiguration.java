package com.afrozaar.nimbal.test;

import com.afrozaar.nimbal.annotations.Module;

import org.springframework.context.annotation.Bean;

/**
 * This is a real world example of a module that has a ring fence black list
 * @author michael
 *
 */
@Module(name = "ComplexModule", order = 51, ringFenceClassBlackListRegex = { "foo", "bar" })
public class DefaultConfiguration {

    @Bean
    public SpringManagedObject getBean() {
        return new SpringManagedObject();
    }
}
