package com.afrozaar.nimbal.core;

import org.springframework.context.ConfigurableApplicationContext;

public interface IRegistry {

    Module getModule(String name);

    Module registerModule(ModuleInfo moduleInfo, ConfigurableApplicationContext refreshContext, ClassLoader classLoader) throws ModuleLoadException;

    boolean deregister(String moduleName);

}
