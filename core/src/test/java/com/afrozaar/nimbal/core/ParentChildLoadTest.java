/*******************************************************************************
 * Nimbal Module Manager 
 * Copyright (c) 2017 Afrozaar.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License 2.0
 * which accompanies this distribution and is available at https://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/
package com.afrozaar.nimbal.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.joor.Reflect.on;

import com.afrozaar.nimbal.core.classloader.ClassLoaderFactory;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import org.junit.Before;
import org.junit.Test;

import java.util.function.Supplier;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.stream.Collectors;

public class ParentChildLoadTest {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ParentChildLoadTest.class);

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

    private MavenRepositoriesManager setupDefaultMavenRepo() {
        MavenRepositoriesManager manager = new MavenRepositoriesManager("http://repo1.maven.org/maven2");
        manager.setM2Folder(".m2");
        manager.init();
        return manager;
    }

    @SuppressWarnings("unchecked")
    @Test
    public void LoadParentAndChild() throws MalformedURLException, ClassNotFoundException, ErrorLoadingArtifactException, IOException, ModuleLoadException {

        loader.loadContext(new MavenCoords("com.afrozaar.nimbal.test", "nimbal-test-parent", "1.0.0-SNAPSHOT"));

        Module loadContext = loader.loadContext(new MavenCoords("com.afrozaar.nimbal.test", "nimbal-test-child", "1.0.0-SNAPSHOT"));

        Object bean = loadContext.getContext().getBean("child");

        assertThat(bean).isNotNull();
        Supplier<String> supplier = on(bean).call("getSupplier").get();

        // this supplier that is returned is sourced form the parent context
        assertThat(supplier.get()).isEqualTo("com.afrozaar.nimbal.test.parent.ParentObject");

        Module module = registry.getModule("ParentModule");
        assertThat(module).isNotNull();
        assertThat(module.getChildren()).extracting(Module::getName).containsOnly("ChildModule");

        try {
            loader.unloadModule("ParentModule");
            failBecauseExceptionWasNotThrown(ModuleLoadException.class);
        } catch (ModuleLoadException mle) {
            assertThat(mle.getMessage()).isEqualTo("cannot unloaded module ParentModule as it has dependent children " + registry.getModule("ParentModule")
                    .getChildren()
                    .stream().map(Module::getName).collect(Collectors.joining(",", "[", "]")));
        }

        loader.unloadModule("ChildModule");
        loader.unloadModule("ParentModule");
    }

}
