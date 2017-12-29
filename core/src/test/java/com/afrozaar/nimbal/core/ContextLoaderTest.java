/*******************************************************************************
 * Nimbal Module Manager 
 * Copyright (c) 2017 Afrozaar.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License 2.0
 * which accompanies this distribution and is available at https://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/
package com.afrozaar.nimbal.core;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.afrozaar.nimbal.core.ContextLoader.ModuleInfoAndClassLoader;
import com.afrozaar.nimbal.core.classloader.ClassLoaderFactory;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;

import org.eclipse.aether.graph.DependencyNode;
import org.junit.Before;
import org.junit.Test;

import java.util.function.Supplier;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.StreamSupport;

public class ContextLoaderTest {

    @SuppressWarnings("unused")
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ContextLoaderTest.class);

    private Path path;

    @Before
    public void clearMavenFolder() throws IOException {
        path = Paths.get(System.getProperty("user.dir"), "maven-repo", "m2");
        try {
            Path realPath = path.toRealPath();
            MoreFiles.deleteDirectoryContents(realPath, RecursiveDeleteOption.ALLOW_INSECURE);
        } catch (NoSuchFileException e) {
            // the first time this is run, this will occur
        }

    }

    @Test
    public void CheckDependenciesGetLoaded() throws ErrorLoadingArtifactException, IOException {

        MavenRepositoriesManager manager = new MavenRepositoriesManager("http://repo1.maven.org/maven2");
        manager.setRepositoryBase(System.getProperty("user.dir") + File.separator + "maven-repo");
        manager.setM2Folder("m2");
        manager.init();

        ContextLoader loader = new ContextLoader(manager, null, null, null);

        loader.refreshDependencies(new MavenCoords("com.google.guava", "guava", "23.5-jre"));

        Iterable<Path> breadthFirst = MoreFiles.fileTraverser().breadthFirst(path);

        Builder<Path> builder = ImmutableSet.builder();
        builder.add(Paths.get(System.getProperty("user.dir"), "maven-repo", "m2", "repository", "com"));
        builder.add(Paths.get(System.getProperty("user.dir"), "maven-repo", "m2", "repository", "com", "google", "guava"));
        builder.add(Paths.get(System.getProperty("user.dir"), "maven-repo", "m2", "repository", "com", "google", "j2objc", "j2objc-annotations"));
        builder.add(Paths.get(System.getProperty("user.dir"), "maven-repo", "m2", "repository", "com", "google", "errorprone", "error_prone_annotations"));
        builder.add(Paths.get(System.getProperty("user.dir"), "maven-repo", "m2", "repository", "com", "google", "code", "findbugs", "jsr305"));

        Set<Path> collect = StreamSupport.stream(breadthFirst.spliterator(), false).collect(toSet());

        assertThat(collect).containsAll(builder.build());
    }

    @Test
    public void LoadSimpleContext() throws ErrorLoadingArtifactException, MalformedURLException, IOException {

        MavenRepositoriesManager manager = setupDefaultMavenRepo();

        IRegistry registry = mock(IRegistry.class);
        ClassLoaderFactory factory = new ClassLoaderFactory(registry);
        ContextLoader loader = new ContextLoader(manager, factory, registry, null);

        DependencyNode node = loader.refreshDependencies(new MavenCoords("com.afrozaar.nimbal.test", "nimbal-test-simple", "1.0.0-SNAPSHOT"));

        URL[] jars = Commons.getJars(node);

        ModuleInfoAndClassLoader moduleInfoAndClassLoader = loader.getModuleAnnotation(node.getArtifact().getArtifactId(), new URL("file", null, node
                .getArtifact().getFile()
                .getAbsolutePath()), jars);

        ModuleInfo moduleInfo = moduleInfoAndClassLoader.getModuleInfo();

        assertThat(moduleInfo.name()).isEqualTo("DefaultConfiguration");
        assertThat(moduleInfo.moduleClass()).isEqualTo("com.afrozaar.nimbal.test.DefaultConfiguration");
        assertThat(moduleInfo.isReloadRequired()).isFalse();
    }

    @Test
    public void LoadSimpleContextWithLabel() throws ErrorLoadingArtifactException, MalformedURLException, IOException {

        MavenRepositoriesManager manager = setupDefaultMavenRepo();

        IRegistry registry = mock(IRegistry.class);
        ClassLoaderFactory factory = new ClassLoaderFactory(registry);
        ContextLoader loader = new ContextLoader(manager, factory, registry, null);

        DependencyNode node = loader.refreshDependencies(new MavenCoords("com.afrozaar.nimbal.test", "nimbal-test-simple-with-label", "1.0.0-SNAPSHOT"));

        URL[] jars = Commons.getJars(node);

        ModuleInfoAndClassLoader moduleInfoAndClassLoader = loader.getModuleAnnotation(node.getArtifact().getArtifactId(), new URL("file", null, node
                .getArtifact().getFile()
                .getAbsolutePath()), jars);

        ModuleInfo moduleInfo = moduleInfoAndClassLoader.getModuleInfo();
        assertThat(moduleInfo.name()).isEqualTo("ParentConfig");
        assertThat(moduleInfo.moduleClass()).isEqualTo("com.afrozaar.nimbal.test.DefaultConfiguration");
        assertThat(moduleInfo.isReloadRequired()).isFalse();
    }

    @Test
    public void LoadContextWithModuleAnnotation() throws ErrorLoadingArtifactException, MalformedURLException, IOException {

        MavenRepositoriesManager manager = setupDefaultMavenRepo();

        IRegistry registry = mock(IRegistry.class);
        ClassLoaderFactory factory = new ClassLoaderFactory(registry);
        ContextLoader loader = new ContextLoader(manager, factory, registry, null);

        DependencyNode node = loader.refreshDependencies(new MavenCoords("com.afrozaar.nimbal.test", "nimbal-test-module-annotation", "1.0.0-SNAPSHOT"));

        URL[] jars = Commons.getJars(node);

        ModuleInfoAndClassLoader moduleInfoAndClassLoader = loader.getModuleAnnotation(node.getArtifact().getArtifactId(), new URL("file", null, node
                .getArtifact().getFile()
                .getAbsolutePath()), jars);

        ModuleInfo moduleInfo = moduleInfoAndClassLoader.getModuleInfo();
        assertThat(moduleInfo.name()).isEqualTo("DefaultConfiguration");
        assertThat(moduleInfo.moduleClass()).isEqualTo("com.afrozaar.nimbal.test.DefaultConfiguration");
        assertThat(moduleInfo.isReloadRequired()).isFalse();
    }

    private MavenRepositoriesManager setupDefaultMavenRepo() {
        MavenRepositoriesManager manager = new MavenRepositoriesManager("http://repo1.maven.org/maven2");
        manager.setM2Folder(".m2");
        manager.init();
        return manager;
    }

    @Test
    public void ClassLoaderModuleSimple() throws ErrorLoadingArtifactException, MalformedURLException, IOException, ClassNotFoundException,
            InstantiationException, IllegalAccessException {

        MavenRepositoriesManager manager = setupDefaultMavenRepo();

        IRegistry registry = mock(IRegistry.class);
        ClassLoaderFactory factory = new ClassLoaderFactory(registry);
        ContextLoader loader = new ContextLoader(manager, factory, registry, null);

        DependencyNode node = loader.refreshDependencies(new MavenCoords("com.afrozaar.nimbal.test", "nimbal-test-simple", "1.0.0-SNAPSHOT"));

        URL[] jars = Commons.getJars(node);

        ModuleInfoAndClassLoader moduleInfo = loader.getModuleAnnotation(node.getArtifact().getArtifactId(), new URL("file", null, node.getArtifact().getFile()
                .getAbsolutePath()), jars);

        Class<?> loadClass = moduleInfo.getClassLoader().loadClass("com.afrozaar.nimbal.test.SpringManagedObject");
        Supplier<String> newInstance = (Supplier<String>) loadClass.newInstance();
        assertThat(newInstance.get()).isEqualTo("from test simple");
    }

    @Test
    public void ClassLoaderModuleSimpleWithLabel() throws ErrorLoadingArtifactException, MalformedURLException, IOException, ClassNotFoundException,
            InstantiationException, IllegalAccessException {

        MavenRepositoriesManager manager = setupDefaultMavenRepo();

        IRegistry registry = mock(IRegistry.class);
        ClassLoaderFactory factory = new ClassLoaderFactory(registry);
        ContextLoader loader = new ContextLoader(manager, factory, registry, null);

        DependencyNode node = loader.refreshDependencies(new MavenCoords("com.afrozaar.nimbal.test", "nimbal-test-simple-with-label", "1.0.0-SNAPSHOT"));

        URL[] jars = Commons.getJars(node);

        ModuleInfoAndClassLoader moduleInfo = loader.getModuleAnnotation(node.getArtifact().getArtifactId(), new URL("file", null, node.getArtifact().getFile()
                .getAbsolutePath()), jars);

        Class<?> loadClass = moduleInfo.getClassLoader().loadClass("com.afrozaar.nimbal.test.SpringManagedObject");
        @SuppressWarnings("unchecked")
        Supplier<String> newInstance = (Supplier<String>) loadClass.newInstance();
        assertThat(newInstance.get()).isEqualTo("nimal test with label");
    }

    @Test
    public void LoadContextWithModuleAnnotationAndLabel() throws ErrorLoadingArtifactException, MalformedURLException, IOException {

        MavenRepositoriesManager manager = setupDefaultMavenRepo();

        IRegistry registry = mock(IRegistry.class);
        ClassLoaderFactory factory = new ClassLoaderFactory(registry);
        ContextLoader loader = new ContextLoader(manager, factory, registry, null);

        DependencyNode node = loader.refreshDependencies(new MavenCoords("com.afrozaar.nimbal.test", "nimbal-test-module-annotation-with-label",
                "1.0.0-SNAPSHOT"));

        URL[] jars = Commons.getJars(node);

        ModuleInfoAndClassLoader moduleInfoAndClassLoader = loader.getModuleAnnotation(node.getArtifact().getArtifactId(), new URL("file", null, node
                .getArtifact().getFile()
                .getAbsolutePath()), jars);

        ModuleInfo moduleInfo = moduleInfoAndClassLoader.getModuleInfo();
        assertThat(moduleInfo.name()).isEqualTo("ModuleInfoWithName");
        assertThat(moduleInfo.moduleClass()).isEqualTo("com.afrozaar.nimbal.test.DefaultConfiguration");
        assertThat(moduleInfo.isReloadRequired()).isFalse();
    }

    @Test
    public void LoadContextWithModuleComplexAnnotation() throws ErrorLoadingArtifactException, MalformedURLException, IOException {

        MavenRepositoriesManager manager = setupDefaultMavenRepo();

        IRegistry registry = mock(IRegistry.class);
        ClassLoaderFactory factory = new ClassLoaderFactory(registry);
        ContextLoader loader = new ContextLoader(manager, factory, registry, null);

        DependencyNode node = loader.refreshDependencies(new MavenCoords("com.afrozaar.nimbal.test", "nimbal-test-module-complex-annotation-for-test",
                "1.0.0-SNAPSHOT"));

        URL[] jars = Commons.getJars(node);

        ModuleInfoAndClassLoader moduleInfoAndClassLoader = loader.getModuleAnnotation(node.getArtifact().getArtifactId(), new URL("file", null, node
                .getArtifact().getFile()
                .getAbsolutePath()), jars);

        ModuleInfo moduleInfo = moduleInfoAndClassLoader.getModuleInfo();
        assertThat(moduleInfo.name()).isEqualTo("ComplexModule");
        assertThat(moduleInfo.order()).isEqualTo(51);
        assertThat(moduleInfo.parentModule()).isEqualTo("bar");
        assertThat(moduleInfo.parentModuleClassesOnly()).isTrue();
        assertThat(moduleInfo.ringFenceFilters()).containsOnly("foo", "bar");
        assertThat(moduleInfo.moduleClass()).isEqualTo("com.afrozaar.nimbal.test.DefaultConfiguration");
        assertThat(moduleInfo.isReloadRequired()).isTrue();
    }

}
