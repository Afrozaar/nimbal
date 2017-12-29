package com.afrozaar.nimbal.test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.function.Supplier;

public class ChildObject {

    @Autowired
    @Qualifier("parent")
    private Object object;

    public Supplier<String> getSupplier() {
        return (Supplier<String>) object;
    }

}
