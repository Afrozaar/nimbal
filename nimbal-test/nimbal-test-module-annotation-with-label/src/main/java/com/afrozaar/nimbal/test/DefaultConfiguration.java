package com.afrozaar.nimbal.test;

import com.afrozaar.nimbal.annotations.Module;

import org.springframework.context.annotation.Bean;

@Module("ModuleInfoWithName")
public class DefaultConfiguration {

    @Bean
    public SpringManagedObject getBean() {
        return new SpringManagedObject();
    }
}
