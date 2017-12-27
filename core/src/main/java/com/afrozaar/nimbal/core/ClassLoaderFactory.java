package com.afrozaar.nimbal.core;

import org.apache.commons.lang3.StringUtils;

import java.net.URL;
import java.util.Arrays;

public class ClassLoaderFactory {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ClassLoaderFactory.class);

    private IRegistry registry;

    private ClassLoader getClassLoader(String artifactId, ModuleInfo moduleInfo, URL[] urls) throws ErrorLoadingArtifactException {
        String parentName = moduleInfo.parentModule();
        if (parentName == null) {
            parentName = StringUtils.stripToNull(moduleInfo.parentModuleClassesOnly());
        }

        if (parentName != null && registry.getClassLoader(parentName) == null) {
            throw new ErrorLoadingArtifactException("no parent class loader found for parent {}", parentName);
        }
        LOG.debug("creating class loader for {}, artifactId:{} with parent ='{}' ringFenceBlackList:{} and jar list {}", moduleInfo.name(), artifactId,
                moduleInfo.parentModule(), Arrays.asList(moduleInfo.ringFenceFilters()), Arrays.asList(urls));
        
        ClassLoader classLoader = loader.getClassLoader(urls, parentName != null ? registry.getClassLoader(parentName) : this.getClass().getClassLoader(),
                artifactId, moduleInfo.ringFenceClassBlackListRegex());

        return classLoader;
    }
}
