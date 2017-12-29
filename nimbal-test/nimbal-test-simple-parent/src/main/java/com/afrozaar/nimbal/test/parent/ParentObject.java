package com.afrozaar.nimbal.test.parent;

import java.util.function.Supplier;

public class ParentObject implements Supplier<String> {

    @Override
    public String get() {
        return this.getClass().getName();
    }

}
