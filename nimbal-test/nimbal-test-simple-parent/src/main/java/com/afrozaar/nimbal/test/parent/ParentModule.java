package com.afrozaar.nimbal.test.parent;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ParentModule {

    @Bean
    public ParentObject parent() {
        return new ParentObject();
    }
}
