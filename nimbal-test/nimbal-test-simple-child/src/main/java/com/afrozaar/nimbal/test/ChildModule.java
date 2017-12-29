package com.afrozaar.nimbal.test;

import com.afrozaar.nimbal.annotations.Module;

import org.springframework.context.annotation.Bean;

@Module(parentModule = "ParentModule")
public class ChildModule {

    @Bean("child")
    public ChildObject getChildObject() {
        return new ChildObject();
    }
}
