package com.afrozaar.nimbal.test;

import java.util.function.Supplier;

public class SpringManagedObject implements Supplier<String> {

    @Override
    public String get() {
        return "foo";
    }

}
