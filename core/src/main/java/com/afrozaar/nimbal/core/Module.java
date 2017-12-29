package com.afrozaar.nimbal.core;

import org.springframework.context.ConfigurableApplicationContext;

public class Module {
    private ClassLoader classLoader;
    private ConfigurableApplicationContext context;
    private String name;

    public Module(String name, ClassLoader classLoader, ConfigurableApplicationContext context) {
        super();
        this.name = name;
        this.classLoader = classLoader;
        this.context = context;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public ConfigurableApplicationContext getContext() {
        return context;
    }

    public String getName() {
        return name;
    }
}