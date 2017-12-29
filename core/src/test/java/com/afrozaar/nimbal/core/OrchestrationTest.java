package com.afrozaar.nimbal.core;

import static org.assertj.core.api.Assertions.assertThat;
import com.afrozaar.nimbal.core.classloader.ClassLoaderFactory;
import com.afrozaar.nimbal.core.classloader.URLClassLoaderExtension;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import org.junit.Before;
import org.junit.Test;

import java.util.function.Supplier;

import java.io.IOException;
import java.net.MalformedURLException;

public class OrchestrationTest {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(OrchestrationTest.class);
    private ApplicationContext testApplicationContext;
    private ContextLoader loader;

    IRegistry registry = new SimpleRegistry();

    @Before
    public void setupTestAppContext() {
        this.testApplicationContext = new AnnotationConfigApplicationContext(TestConfiguration.class);
        MavenRepositoriesManager manager = setupDefaultMavenRepo();

        ClassLoaderFactory factory = new ClassLoaderFactory(registry);
        ContextFactory contextFactory = new ContextFactory(factory, registry);
        contextFactory.setApplicationContext(testApplicationContext);
        loader = new ContextLoader(manager, factory, registry, contextFactory);

    }

    @Test
    public void LoadSimpleContext() throws ErrorLoadingArtifactException, MalformedURLException, IOException, ClassNotFoundException, ModuleLoadException {

        ApplicationContext context = loader.loadContext(new MavenCoords("com.afrozaar.nimbal.test", "nimbal-test-simple", "1.0.0-SNAPSHOT")).getContext();

        //LOG.info("beans now present {}", context.getBeanDefinitionNames());
        Object bean = context.getBean("getBean");

        assertThat(bean).isNotNull();
        String x = ((Supplier<String>) bean).get();
        assertThat(x).isEqualTo("from test simple");
        assertThat(bean.getClass().getName()).isEqualTo("com.afrozaar.nimbal.test.SpringManagedObject");
    }

    @Test
    public void LoadComplexContext() throws ErrorLoadingArtifactException, MalformedURLException, IOException, ClassNotFoundException, ModuleLoadException {

        ApplicationContext context = loader.loadContext(new MavenCoords("com.afrozaar.nimbal.test", "nimbal-test-module-complex-annotation", "1.0.0-SNAPSHOT"))
                .getContext();

        //LOG.info("beans now present {}", context.getBeanDefinitionNames());
        Object bean = context.getBean("getBean");

        assertThat(bean).isNotNull();
        String x = ((Supplier<String>) bean).get();
        assertThat(x).isEqualTo("from test simple");
        assertThat(bean.getClass().getName()).isEqualTo("com.afrozaar.nimbal.test.SpringManagedObject");

        URLClassLoaderExtension extensionClassLoader = (URLClassLoaderExtension) bean.getClass().getClassLoader();

        assertThat(extensionClassLoader.getRingFencedFilters()).containsOnly("foo", "bar");
        assertThat(extensionClassLoader.getName()).isEqualTo("ComplexModule");
    }

    private MavenRepositoriesManager setupDefaultMavenRepo() {
        MavenRepositoriesManager manager = new MavenRepositoriesManager("http://repo1.maven.org/maven2");
        manager.setM2Folder(".m2");
        manager.init();
        return manager;
    }

    @Test
    public void LoadAndReloadWithChange() throws ErrorLoadingArtifactException, MalformedURLException, IOException, ClassNotFoundException,
            ModuleLoadException {

        String name = loader.loadContext(new MavenCoords("com.afrozaar.nimbal.test", "nimbal-test-module-annotation", "1.0.0-SNAPSHOT")).getName();
        {
            ApplicationContext context = registry.getModule(name).getContext();

            //LOG.info("beans now present {}", context.getBeanDefinitionNames());
            Object bean = context.getBean("getBean");

            assertThat(bean).isNotNull();
            String x = ((Supplier<String>) bean).get();
            assertThat(x).isEqualTo("from test simple 1");

            assertThat(bean.getClass().getName()).isEqualTo("com.afrozaar.nimbal.test.SpringManagedObject");

            URLClassLoaderExtension extensionClassLoader = (URLClassLoaderExtension) bean.getClass().getClassLoader();

            assertThat(extensionClassLoader.getName()).isEqualTo("nimbal-test-module-annotation");
            assertThat(name).isEqualTo("DefaultConfiguration");
        }
        // unload module
        loader.unloadModule(name);

        loader.loadContext(new MavenCoords("com.afrozaar.nimbal.test", "nimbal-test-module-annotation", "1.1.0-SNAPSHOT"));
        {
            Object bean = registry.getModule(name).getContext().getBean("getBean");

            assertThat(bean).isNotNull();
            String x = ((Supplier<String>) bean).get();
            assertThat(x).isEqualTo("from test simple 2");
        }
    }

}
