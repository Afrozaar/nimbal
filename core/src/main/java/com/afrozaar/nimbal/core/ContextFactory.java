package com.afrozaar.nimbal.core;

import com.afrozaar.nimbal.core.classloader.ClassLoaderFactory;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class ContextFactory implements ApplicationContextAware {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ContextFactory.class);
    private ClassLoaderFactory classLoaderFactory;
    private IRegistry registry;
    private ParentContext defaultParentContext;
    private ClassLoader defaultClassLoader;

    public static class ParentContext {

        private ClassLoader classLoader;
        private ApplicationContext applicationContext;

        public ParentContext(ClassLoader classLoader, ApplicationContext applicationContext) {
            this.classLoader = classLoader;
            this.applicationContext = applicationContext;

        }

        public ClassLoader getClassLoader() {
            return classLoader;
        }

        public ApplicationContext getApplicationContext() {
            return applicationContext;
        }
    }

    public ContextFactory(ClassLoaderFactory classLoaderFactory, IRegistry registry) {
        this.classLoaderFactory = classLoaderFactory;
        this.registry = registry;
        this.defaultClassLoader = classLoaderFactory.getDefaultClassLoader();
    }

    ParentContext getParentContext(ModuleInfo module) throws ErrorLoadingArtifactException {

        String parentName = module.parentModule();
        if (parentName != null) {
            ApplicationContext parentContext = registry.getContext(parentName);
            if (parentContext == null && !module.parentModuleClassesOnly()) {
                throw new ErrorLoadingArtifactException("module {} required parent module {} but is not present.", module.name(), parentName);
            }
            ClassLoader classLoader = registry.getClassLoader(parentName);
            if (classLoader == null) {
                throw new ErrorLoadingArtifactException("loading module {} requires class loader for {} and it was not found.", parentName);
            }
            if (module.parentModuleClassesOnly()) {
                parentContext = defaultParentContext.getApplicationContext();
            }
            return new ParentContext(classLoader, parentContext);
        } else {
            return defaultParentContext;
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.defaultParentContext = new ParentContext(defaultClassLoader, applicationContext);
    }

    public ConfigurableApplicationContext createContext(ClassLoader classLoader, String moduleClass, String moduleName, ApplicationContext parent)
            throws ClassNotFoundException {
        LOG.info("Loading Ashes External Module {}", moduleClass);
        //ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader(); must do this before we call refresh
        //Thread.currentThread().setContextClassLoader(classLoader);
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.setDisplayName(moduleName);
        context.setParent(parent);
        context.register(classLoader.loadClass(moduleClass));
        context.setClassLoader(classLoader);
        LOG.info("Module {} Loaded", moduleClass);
        //Thread.currentThread().setContextClassLoader(contextClassLoader); must call this after refresh (although we could use a separate thread and wait for the 
        // thread to finish in which case it won't be so complex
        return context;

    }

    public ConfigurableApplicationContext refreshContext(ClassLoader classLoader, ConfigurableApplicationContext context) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader(); //must do this before we call refresh
        Thread.currentThread().setContextClassLoader(classLoader);
        context.refresh();
        Thread.currentThread().setContextClassLoader(contextClassLoader);
        return context;

    }

}
