package com.afrozaar.nimbal.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.afrozaar.nimbal.core.ContextLoader.ModuleInfoAndClassLoader;
import com.afrozaar.nimbal.core.classloader.ClassLoaderFactory;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import org.eclipse.aether.graph.DependencyNode;
import org.junit.Before;
import org.junit.Test;

import java.util.function.Supplier;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class OrchestrationTest {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(OrchestrationTest.class);
    private ApplicationContext testApplicationContext;

    @Before
    public void setupTestAppContext() {
        this.testApplicationContext = new AnnotationConfigApplicationContext(TestConfiguration.class);
    }

    @Test
    public void LoadSimpleContext() throws ErrorLoadingArtifactException, MalformedURLException, IOException, ClassNotFoundException {

        MavenRepositoriesManager manager = setupDefaultMavenRepo();

        IRegistry registry = mock(IRegistry.class);
        ClassLoaderFactory factory = new ClassLoaderFactory(registry);
        ContextLoader loader = new ContextLoader(manager, factory);

        DependencyNode node = loader.refreshDependencies(new MavenCoords("com.afrozaar.nimbal.test", "nimbal-test-simple", "1.0.0-SNAPSHOT"));

        URL[] jars = Commons.getJars(node);

        ModuleInfoAndClassLoader moduleInfoAndClassLoader = loader.getModuleAnnotation(node.getArtifact().getArtifactId(), new URL("file", null, node
                .getArtifact().getFile()
                .getAbsolutePath()), jars);

        ModuleInfo moduleInfo = moduleInfoAndClassLoader.getModuleInfo();

        assertThat(moduleInfo.name()).isEqualTo("DefaultConfiguration");
        assertThat(moduleInfo.moduleClass()).isEqualTo("com.afrozaar.nimbal.test.DefaultConfiguration");
        assertThat(moduleInfo.isReloadRequired()).isFalse();

        ContextFactory contextFactory = new ContextFactory(factory, registry);
        contextFactory.setApplicationContext(testApplicationContext);

        contextFactory.getParentContext(moduleInfo);

        ClassLoader classLoader = factory.getClassLoader(moduleInfo, jars);
        ConfigurableApplicationContext context = contextFactory.createContext(classLoader, moduleInfo.moduleClass(), moduleInfo
                .name(), testApplicationContext);

        context = contextFactory.refreshContext(classLoader, context);

        //LOG.info("beans now present {}", context.getBeanDefinitionNames());
        Object bean = context.getBean("getBean");

        assertThat(bean).isNotNull();
        String x = ((Supplier<String>) bean).get();
        assertThat(x).isEqualTo("from test simple");
        assertThat(bean.getClass().getName()).isEqualTo("com.afrozaar.nimbal.test.SpringManagedObject");

    }

    private MavenRepositoriesManager setupDefaultMavenRepo() {
        MavenRepositoriesManager manager = new MavenRepositoriesManager("http://repo1.maven.org/maven2");
        manager.setM2Folder(".m2");
        manager.init();
        return manager;
    }

}
