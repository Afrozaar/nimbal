package com.afrozaar.nimbal.core;

import static java.util.Optional.ofNullable;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.LinkedHashMap;
import java.util.Map;

public class SimpleRegistry implements IRegistry {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SimpleRegistry.class);

    Map<String, Module> map = new LinkedHashMap<>();

    @Override
    public ClassLoader getClassLoader(String name) {
        return ofNullable(map.get(name)).map(Module::getClassLoader).orElse(null);
    }

    @Override
    public ApplicationContext getContext(String name) {
        return ofNullable(map.get(name)).map(Module::getContext).orElse(null);
    }

    @Override
    public Module registerModule(String name, ConfigurableApplicationContext context, ClassLoader classLoader) {
        Module value = new Module(name, classLoader, context);
        map.put(name, value);
        return value;

    }

}
