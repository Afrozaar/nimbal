package com.afrozaar.nimbal.core;

import com.afrozaar.nimbal.core.ModuleLoader.ModulePropertiesNotInZipException;
import com.afrozaar.nimbal.core.ModuleLoader.SnapshotVersionException;

import com.google.common.io.ByteSource;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Properties;

public interface IModuleLoader {

    ByteSource getRemoteByteSource(String groupId, String artifactId, String version, String extension) throws SnapshotVersionException, IOException;

    ClassLoader getClassLoader(ByteSource source, ClassLoader parentClassLoader, String groupId, String artifactId, String version, String packaging)
            throws ErrorLoadingArtifactException;

    Properties getModuleProperties(ClassLoader classLoader) throws IOException;

    /**
     * Tries to get the module properties via the top level file in the zip
     * 
     * @param source
     * @return
     * @throws ModulePropertiesNotInZipException
     */
    Properties getModuleProperties(ByteSource source) throws ModulePropertiesNotInZipException, IOException;

    ConfigurableApplicationContext loadModule(ClassLoader classLoader, String moduleClass, String moduleName, ApplicationContext parent, Properties properties,
            List<BeanPostProcessor> beanPostProcessors) throws ClassNotFoundException;

    void cleanClassLoaderFolder(ClassLoader classLoader);

    void setSnapshotsEnabled(boolean snapshotsEnabled);

    ClassLoader getClassLoader(URL[] urls, ClassLoader classLoader, String artifactId, String[] ringFenceClassBlackListRegex);

}