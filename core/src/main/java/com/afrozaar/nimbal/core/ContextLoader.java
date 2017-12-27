package com.afrozaar.nimbal.core;

import com.afrozaar.nimbal.core.classloader.ClassLoaderFactory;

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

/*    public void doYourThing(DependencyNode node) {
        LOG.debug("searching for annotated module");
        URL url = new URL("file", null, node.getArtifact().getFile().getAbsolutePath());
        LOG.debug("adding url {}", url);

        URL[] jars = getJars(node);
        ModuleInfo module = getModuleAnnotation(mavenCoords, url, jars);
    }
*/

/*    public ModuleInfo getModuleAnnotation(String artifactId, URL mainJar, URL[] jars) throws IOException, ClassNotFoundException,
            ErrorLoadingArtifactException {
        ClassLoader loader = classLoaderFactory.getClassLoader(artifactId, new ModuleInfo(), jars);

        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .setUrls(mainJar)
                .addClassLoader(loader)
                .addScanners(new TypeAnnotationsScanner(), new SubTypesScanner()));
        Set<Class<?>> typesAnnotatedWith = reflections.getTypesAnnotatedWith(Module.class);
        LOG.debug("annotated with module {}", typesAnnotatedWith);
        Optional<Class<?>> potentialModuleClass = typesAnnotatedWith.stream().findFirst();

        if (potentialModuleClass.isPresent()) {
            LOG.info("found module annotated class {}", potentialModuleClass);
            Class<? extends AshesModule> moduleClass = (Class<? extends AshesModule>) potentialModuleClass.get();
            Module annotation = moduleClass.getAnnotation(Module.class);
            return new ModuleInfo(annotation, moduleClass);
        } else {
            // need to use module.inf to find module class name
            LOG.info("no module annotation found, using module.inf to lookup module class");
            Enumeration<URL> resourceAsStream = loader.getResources("module.inf");
            Optional<URL> foundUrl = Collections.list(resourceAsStream).stream().filter(u -> u.toString().contains(artifactId)).findFirst();

            if (foundUrl.isPresent()) {
                try (InputStream openStream = foundUrl.get().openStream()) {
                    Properties p = new Properties();
                    p.load(openStream);
                    return new ModuleInfo(p);
                }
            } else {
                throw new ErrorLoadingArtifactException("no module.inf file or no class annotated with {} is found for maven coords {}", Module.class, t);
            }
        }

    }*/

}
