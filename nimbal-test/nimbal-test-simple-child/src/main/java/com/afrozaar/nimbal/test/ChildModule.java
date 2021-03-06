/*******************************************************************************
 * Nimbal Module Manager 
 * Copyright (c) 2017 Afrozaar.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License 2.0
 * which accompanies this distribution and is available at https://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/
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
