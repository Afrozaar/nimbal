package com.afrozaar.nimbal.core;

import org.springframework.context.ApplicationContext;

public interface IRegistry {

    ClassLoader getClassLoader(String parentName);

    ApplicationContext getContext(String parentName);

}
