package com.afrozaar.nimbal.core;

import static java.util.Optional.empty;
import static java.util.Optional.of;

import com.afrozaar.nimbal.annotations.Module;
import com.afrozaar.nimbal.core.ContextFactory.ParentContext;
import com.afrozaar.nimbal.core.classloader.ClassLoaderFactory;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ConfigurationBuilder;

import java.util.function.BiFunction;
import java.util.function.Consumer;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ContextLoader {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ContextLoader.class);

    private MavenRepositoriesManager repositoriesManager;
    private ClassLoaderFactory classLoaderFactory;
    private IRegistry registry;
    private ContextFactory contextFactory;

    public ContextLoader(MavenRepositoriesManager repositoriesManager, ClassLoaderFactory classLoaderFactory, IRegistry registry,
            ContextFactory contextFactory) {
        super();
        this.repositoriesManager = repositoriesManager;
        this.classLoaderFactory = classLoaderFactory;
        this.registry = registry;
        this.contextFactory = contextFactory;
    }

    public DependencyNode refreshDependencies(MavenCoords mavenCoords) throws ErrorLoadingArtifactException {
        LOG.debug("initialising repo session");
        RepositorySystemSession session = repositoriesManager.newSession(mavenCoords);

        LOG.debug("collecting dependencies for {}", mavenCoords);
        Dependency dependency = new Dependency(new DefaultArtifact(mavenCoords.getAsString()), JavaScopes.COMPILE);

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot(dependency);
        //collectRequest.addRepository(central);
        collectRequest.addRepository(repositoriesManager.getReleaseRepository());
        repositoriesManager.getSnapshotRepository().ifPresent(collectRequest::addRepository);
        DependencyNode node;
        RepositorySystem repoSystem = repositoriesManager.getRepoSystem();
        try {
            node = repoSystem.collectDependencies(session, collectRequest).getRoot();
        } catch (DependencyCollectionException e) {
            LOG.error("dependency error ", e);
            throw new ErrorLoadingArtifactException(e.getMessage(), e);
        }

        DependencyRequest dependencyRequest = new DependencyRequest();
        dependencyRequest.setRoot(node);

        try {
            repoSystem.resolveDependencies(session, dependencyRequest);

        } catch (DependencyResolutionException e) {
            LOG.error("dependency error ", e);
            throw new ErrorLoadingArtifactException(e.getMessage(), e);
        }
        return node;

    }

    public static class ModuleInfoAndClassLoader {
        private ClassLoader classLoader;
        private ModuleInfo moduleInfo;

        public ModuleInfoAndClassLoader(ClassLoader classLoader, ModuleInfo moduleInfo) {
            super();
            this.classLoader = classLoader;
            this.moduleInfo = moduleInfo;
        }

        public ClassLoader getClassLoader() {
            return classLoader;
        }

        public void setClassLoader(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        public ModuleInfo getModuleInfo() {
            return moduleInfo;
        }

        public void setModuleInfo(ModuleInfo moduleInfo) {
            this.moduleInfo = moduleInfo;
        }

    }

    public ModuleInfoAndClassLoader getModuleAnnotation(String artifactId, URL mainJar, URL[] jars) throws IOException,
            ErrorLoadingArtifactException {
        ClassLoader loader = classLoaderFactory.getClassLoader(new ModuleInfo(artifactId), jars);

        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .setUrls(mainJar)
                .addClassLoader(loader)
                .addScanners(new TypeAnnotationsScanner(), new SubTypesScanner()));

        // can't use anything more fancy here as I need the strong typing to ensure the correct new ModuleInfo method is called.
        // thus what looks like it could be not repeated needs to be
        {
            Optional<ModuleInfo> moduleInfo = getModuleInfo(reflections, Module.class, (annotation, clazz) -> new ModuleInfo(annotation, clazz));
            if (moduleInfo.isPresent()) {
                return new ModuleInfoAndClassLoader(loader, moduleInfo.get());
            }
        }
        {
            Optional<ModuleInfo> moduleInfo = getModuleInfo(reflections, Configuration.class, (annotation, clazz) -> new ModuleInfo(annotation, clazz));
            if (moduleInfo.isPresent()) {
                return new ModuleInfoAndClassLoader(loader, moduleInfo.get());
            }
        }
        throw new ErrorLoadingArtifactException("no class annotated with Configuration or Module was found, cannot load this as a module");

    }

    private <T extends Annotation> Optional<ModuleInfo> getModuleInfo(Reflections reflections, Class<T> annotationClazz,
            BiFunction<T, Class<?>, ModuleInfo> moduleInfoProvider) {
        Set<Class<?>> typesAnnotatedWith = reflections.getTypesAnnotatedWith(annotationClazz);
        LOG.debug("annotated with module {}", typesAnnotatedWith);
        Optional<Class<?>> potentialModuleClass = typesAnnotatedWith.stream().findFirst();

        if (potentialModuleClass.isPresent()) {
            LOG.info("found configuration annotated class {}", potentialModuleClass);
            T annotation = potentialModuleClass.get().getAnnotation(annotationClazz);
            return of(moduleInfoProvider.apply(annotation, potentialModuleClass.get()));
        } else {
            return empty();
        }
    }

    public com.afrozaar.nimbal.core.Module loadContext(MavenCoords mavenCoords, Consumer<ConfigurableApplicationContext>... preRefresh)
            throws ErrorLoadingArtifactException, ModuleLoadException,
            MalformedURLException, IOException,
            ClassNotFoundException {

        DependencyNode node = refreshDependencies(mavenCoords);

        URL[] jars = Commons.getJars(node);

        ModuleInfoAndClassLoader moduleInfoAndClassLoader = getModuleAnnotation(node.getArtifact().getArtifactId(), new URL("file", null, node
                .getArtifact().getFile()
                .getAbsolutePath()), jars);

        if (registry.getModule(moduleInfoAndClassLoader.getModuleInfo().name()) != null) {
            throw new ModuleLoadException("module {} is already loaded, cannot reload until existing module is unloaded", moduleInfoAndClassLoader
                    .getModuleInfo().name());
        }
        // if we discover meta data on the module that is only available in the construct of a class loader (parent class loader for example) we need to recreate the class
        // loader with this info
        ClassLoader classLoader = moduleInfoAndClassLoader.getModuleInfo().isReloadRequired() ? classLoaderFactory.getClassLoader(moduleInfoAndClassLoader
                .getModuleInfo(), jars)
                : moduleInfoAndClassLoader.getClassLoader();

        ModuleInfo moduleInfo = moduleInfoAndClassLoader.getModuleInfo();

        ParentContext parentContext = contextFactory.getParentContext(moduleInfo);

        ConfigurableApplicationContext context = contextFactory.createContext(classLoader, moduleInfo.moduleClass(), moduleInfo.name(), parentContext
                .getApplicationContext());

        if (preRefresh != null) {
            Arrays.stream(preRefresh).forEach(c -> c.accept(context));
        }

        ConfigurableApplicationContext refreshContext = contextFactory.refreshContext(classLoader, context);

        return registry.registerModule(moduleInfo, refreshContext, classLoader);
    }

    public void unloadModule(String moduleName) throws ModuleLoadException {
        com.afrozaar.nimbal.core.Module module = registry.getModule(moduleName);
        if (!module.getChildren().isEmpty()) {
            throw new ModuleLoadException("cannot unloaded module {} as it has dependent children {}", moduleName, module.getChildren()
                    .stream().map(com.afrozaar.nimbal.core.Module::getName).collect(Collectors.joining(",", "[", "]")));
        }
        module.getContext().close();
        registry.deregister(moduleName);
    }

}
