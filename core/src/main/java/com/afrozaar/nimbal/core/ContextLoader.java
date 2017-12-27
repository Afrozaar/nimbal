package com.afrozaar.nimbal.core;

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

    public ContextLoader(MavenRepositoriesManager repositoriesManager) {
        super();
        this.repositoriesManager = repositoriesManager;
    }

    public void refreshDependencies(MavenCoords mavenCoords) throws ErrorLoadingArtifactException {
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

    }
}
