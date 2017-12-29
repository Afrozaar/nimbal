/*******************************************************************************
 * Nimbal Module Manager 
 * Copyright (c) 2017 Afrozaar.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License 2.0
 * which accompanies this distribution and is available at https://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/
package com.afrozaar.nimbal.test.parent;

import java.util.function.Supplier;

public class ParentObject implements Supplier<String> {

    @Override
    public String get() {
        return this.getClass().getName();
    }

}
