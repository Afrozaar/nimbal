package com.afrozaar.nimbal.core.classloader;

import com.afrozaar.nimbal.core.ErrorLoadingArtifactException;
import com.afrozaar.nimbal.core.IRegistry;
import com.afrozaar.nimbal.core.ModuleInfo;

import java.net.URL;
import java.util.Arrays;

public class ClassLoaderFactory {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ClassLoaderFactory.class);

    private IRegistry registry;
    private ClassLoader defaultParentClassLoader = this.getClass().getClassLoader();

    public ClassLoaderFactory(IRegistry registry) {
        this.registry = registry;
    }

    public void setDefaultParentClassLoader(ClassLoader defaultParentClassLoader) {
        this.defaultParentClassLoader = defaultParentClassLoader;
    }

    public ClassLoader getClassLoader(ModuleInfo moduleInfo, URL[] urls) throws ErrorLoadingArtifactException {
        String parentName = moduleInfo.parentModule();

        ClassLoader parentClassLoader = getParentClassLoader(parentName);
        LOG.debug("creating class loader for {},  with parent ='{}' ringFenceBlackList:{} and jar list {}", moduleInfo.name(),
                moduleInfo.parentModule(), moduleInfo.ringFenceFilters(), Arrays.asList(urls));

        URLClassLoaderExtension classLoader = new URLClassLoaderExtension(urls, parentClassLoader, moduleInfo.name());
        classLoader.setRingFencedFilters(moduleInfo.ringFenceFilters());

        return classLoader;
    }

    private ClassLoader getParentClassLoader(String parentName) throws ErrorLoadingArtifactException {
        if (parentName != null) {
            ClassLoader parentClassLoader = registry.getModule(parentName).getClassLoader();
            if (parentClassLoader == null) {
                throw new ErrorLoadingArtifactException("no parent class loader found for parent {}", parentName);
            }
            return parentClassLoader;
        } else {
            return defaultParentClassLoader;
        }
    }

    public ClassLoader getDefaultClassLoader() {
        return defaultParentClassLoader;
    }

}
