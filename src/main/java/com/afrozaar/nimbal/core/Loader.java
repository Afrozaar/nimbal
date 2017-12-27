package com.afrozaar.nimbal.core;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import java.util.function.Consumer;

public class Loader {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(Loader.class);

    public ConfigurableApplicationContext loadModule(ClassLoader classLoader, String moduleClass, ApplicationContext parent,
            Consumer<AnnotationConfigApplicationContext> preRefresh) throws ClassNotFoundException {
        LOG.info("Loading Ashes External Module {}", moduleClass);
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(classLoader);
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.setParent(parent);
        context.register(classLoader.loadClass(moduleClass));
        context.setDisplayName(moduleClass);
        context.setClassLoader(classLoader);
        context.refresh();
        LOG.info("Module {} Loaded", moduleClass);
        Thread.currentThread().setContextClassLoader(contextClassLoader);
        return context;

    }
}
