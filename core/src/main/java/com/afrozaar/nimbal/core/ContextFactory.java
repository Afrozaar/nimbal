package com.afrozaar.nimbal.core;

import com.afrozaar.nimbal.core.classloader.ClassLoaderFactory;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class ContextFactory implements ApplicationContextAware {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ContextFactory.class);
    private ClassLoaderFactory classLoaderFactory;
    private IRegistry registry;
    private ParentContext defaultParentContext;
    private ClassLoader defaultClassLoader = this.getClass().getClassLoader();

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

    /**
     * this method might be called after the setAppicationcontext or before. AWe must cope with both scenarios.
     * 
     * @param defaultClassLoader
     */
    public void setDefaultClassLoader(ClassLoader defaultClassLoader) {
        this.defaultClassLoader = defaultClassLoader;
        if (defaultParentContext != null) { // set application context has already happened
            this.defaultParentContext = new ParentContext(defaultClassLoader, defaultParentContext.getApplicationContext());
        }
    }

}
