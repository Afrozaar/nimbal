package com.afrozaar.nimbal.test;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DefaultConfiguration {

    @Bean
    public SpringManagedObject getBean() {
        return new SpringManagedObject();
    }
}
