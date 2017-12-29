package com.afrozaar.nimbal.core;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

public interface IRegistry {

    ClassLoader getClassLoader(String parentName);

    ApplicationContext getContext(String parentName);

    Module registerModule(String name, ConfigurableApplicationContext refreshContext, ClassLoader classLoader);

}
