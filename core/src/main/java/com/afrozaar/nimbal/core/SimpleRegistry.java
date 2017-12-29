/*******************************************************************************
 * Nimbal Module Manager 
 * Copyright (c) 2017 Afrozaar.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License 2.0
 * which accompanies this distribution and is available at https://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/
package com.afrozaar.nimbal.core;

import org.springframework.context.ConfigurableApplicationContext;

import java.util.LinkedHashMap;
import java.util.Map;

public class SimpleRegistry implements IRegistry {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SimpleRegistry.class);

    Map<String, Module> map = new LinkedHashMap<>();

    @Override
    public Module registerModule(ModuleInfo moduleInfo, ConfigurableApplicationContext context, ClassLoader classLoader) throws ModuleLoadException {
        Module value = new Module(moduleInfo.name(), classLoader, context);

        if (moduleInfo.parentModule() != null) {
            Module parent = map.get(moduleInfo.parentModule());
            boolean addChild = parent.addChild(value);
            if (addChild) {
                value.setParent(parent);
            } else {
                throw new ModuleLoadException("parent module {} already has this module {} as a child", moduleInfo.parentModule(), moduleInfo.name());
            }
        }
        map.put(moduleInfo.name(), value);
        return value;

    }

    @Override
    public Module getModule(String name) {
        return map.get(name);
    }

    @Override
    public boolean deregister(String moduleName) {
        Module module = map.get(moduleName);
        // remove myself from my parent
        Module parent = module.getParent();
        if (parent != null) {
            parent.removeChild(module);
            module.setParent(null);
        }
        return map.remove(moduleName) != null;
    }

}
