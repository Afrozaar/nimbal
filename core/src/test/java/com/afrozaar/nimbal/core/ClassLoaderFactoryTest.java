package com.afrozaar.nimbal.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.afrozaar.nimbal.core.classloader.ClassLoaderFactory;

import org.eclipse.aether.graph.DependencyNode;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class ClassLoaderFactoryTest {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ClassLoaderFactoryTest.class);

    @Test
    public void LoadClass() throws ErrorLoadingArtifactException, ClassNotFoundException, IOException {

        MavenRepositoriesManager manager = new MavenRepositoriesManager("http://repo1.maven.org/maven2");
        manager.setRepositoryBase(System.getProperty("user.dir") + File.separator + "maven-repo");
        manager.setM2Folder(".m2");
        manager.init();

        ClassLoaderFactory classLoaderFactory = new ClassLoaderFactory(mock(IRegistry.class));
        ContextLoader loader = new ContextLoader(manager, classLoaderFactory);

        DependencyNode node = loader.refreshDependencies(new MavenCoords("com.nrinaudo", "kantan.xpath-nekohtml_2.12", "0.3.1"));

        LOG.debug("searching for annotated module");
        URL url = new URL("file", null, node.getArtifact().getFile().getAbsolutePath());
        LOG.debug("adding url {}", url);

        URL[] jars = Commons.getJars(node);

        ModuleInfo moduleInfo = new ModuleInfo();
        moduleInfo.setName(node.getArtifact().getArtifactId());
        ClassLoader classLoader = classLoaderFactory.getClassLoader(moduleInfo, jars);

        Class<?> loadClass = classLoader.loadClass("org.cyberneko.html.HTMLScanner");

        assertThat(loadClass).isNotNull();
    }

}
