/*******************************************************************************
 * Nimbal Module Manager 
 * Copyright (c) 2017 Afrozaar.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License 2.0
 * which accompanies this distribution and is available at https://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/
package com.afrozaar.nimbal.core;

import static java.util.stream.Collectors.joining;

import org.springframework.context.ConfigurableApplicationContext;

import java.util.HashSet;
import java.util.Set;

public class Module {
    private ClassLoader classLoader;
    private ConfigurableApplicationContext context;
    private String name;

    private Set<Module> children = new HashSet<>();
    private Module parent;

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

    public Set<Module> getChildren() {
        return children;
    }

    public boolean addChild(Module module) {
        return this.children.add(module);
    }

    public boolean removeChild(Module module) {
        return this.children.remove(module);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Module other = (Module) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "Module [name=" + name + ", classLoader=" + classLoader + ", context=" + context + ", children=" + children.stream().map(Module::getName)
                .collect(joining(",", "(", ")")) + "]";
    }

    public Module getParent() {
        return parent;
    }

    public void setParent(Module parent) {
        this.parent = parent;
    }
}