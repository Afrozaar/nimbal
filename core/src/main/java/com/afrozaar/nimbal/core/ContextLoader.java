package com.afrozaar.nimbal.core;

import static java.util.Optional.empty;
import static java.util.Optional.of;

import com.afrozaar.nimbal.annotations.Module;
import com.afrozaar.nimbal.core.classloader.ClassLoaderFactory;

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

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.Optional;
import java.util.Set;

public class ContextLoader {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ContextLoader.class);

    private MavenRepositoriesManager repositoriesManager;
    private ClassLoaderFactory classLoaderFactory;

    public ContextLoader(MavenRepositoriesManager repositoriesManager, ClassLoaderFactory classLoaderFactory) {
        super();
        this.repositoriesManager = repositoriesManager;
        this.classLoaderFactory = classLoaderFactory;
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

}
