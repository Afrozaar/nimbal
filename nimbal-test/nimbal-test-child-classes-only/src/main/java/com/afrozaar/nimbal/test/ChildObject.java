/*******************************************************************************
 * Nimbal Module Manager 
 * Copyright (c) 2017 Afrozaar.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License 2.0
 * which accompanies this distribution and is available at https://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/
package com.afrozaar.nimbal.test;

import com.afrozaar.nimbal.test.parent.ParentObject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.function.Supplier;

public class ChildObject {

    @Autowired(required = false)

    @Qualifier("parent")
    private Object object;

    public Supplier<String> getSupplier() {
        return (Supplier<String>) object;
    }

    public Supplier<String> getParentObject() {
        return new ParentObject();
    }

}
