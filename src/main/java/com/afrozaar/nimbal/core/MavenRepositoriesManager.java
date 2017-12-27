package com.afrozaar.nimbal.core;

import com.afrozaar.nimbal.legacy.ModuleManager.MavenCoords;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.internal.impl.slf4j.Slf4jLoggerFactory;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RemoteRepository.Builder;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.transport.wagon.WagonTransporterFactory;
import org.eclipse.aether.util.repository.AuthenticationBuilder;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

public class MavenRepositoriesManager {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(MavenRepositoriesManager.class);

    private RepositorySystem repoSystem;

    private String moduleRepositoryUsername;
    private String moduleRepositoryPassword;
    private String releaseModuleRepositoryUrl;
    private String snapshotModuleRepositoryUrl;

    private RemoteRepository releaseRepository;
    private RemoteRepository snapshotRepository;

    private String repositoryBase = System.getProperty("user.home");

    private static final RepositoryPolicy ENABLED_RELEASE_POLICY = new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_NEVER,
            RepositoryPolicy.CHECKSUM_POLICY_WARN);
    private static final RepositoryPolicy ENABLED_SNAPSHOT_POLICY = new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_ALWAYS,
            RepositoryPolicy.CHECKSUM_POLICY_WARN);
    private static final RepositoryPolicy DISABLED_REPOSITORY_POLICY = new RepositoryPolicy(false, RepositoryPolicy.UPDATE_POLICY_DAILY,
            RepositoryPolicy.UPDATE_POLICY_DAILY);

    Set<MavenCoords> snapshotVersionsLoaded = new HashSet();

    RepositorySystemSession snapshotSession;
    RepositorySystemSession stabilisedSession;

    public MavenRepositoriesManager() {

    }

    /**
     * the minimum required setup is the release module repository url
     * @param releaseModuleRepositoryUrl
     */
    public MavenRepositoriesManager(String releaseModuleRepositoryUrl) {
        super();
        this.releaseModuleRepositoryUrl = releaseModuleRepositoryUrl;
    }

    public MavenRepositoriesManager(String releaseModuleRepositoryUrl, String snapshotModuleRepositoryUrl, String moduleRepositoryUsername,
            String moduleRepositoryPassword) {
        super();
        this.releaseModuleRepositoryUrl = releaseModuleRepositoryUrl;
        this.snapshotModuleRepositoryUrl = snapshotModuleRepositoryUrl;
        this.moduleRepositoryUsername = moduleRepositoryUsername;
        this.moduleRepositoryPassword = moduleRepositoryPassword;
    }

    @PostConstruct
    public void init() {
        repoSystem = initRepositorySystem();

        Authentication authentication = null;
        if (moduleRepositoryUsername != null && moduleRepositoryPassword != null) {
            authentication = new AuthenticationBuilder()
                    .addUsername(moduleRepositoryUsername)
                    .addPassword(moduleRepositoryPassword)
                    .build();
        }

        if (releaseModuleRepositoryUrl == null) {
            throw new IllegalArgumentException("cannot have a null release module url");
        }
        {
            Builder builder = new RemoteRepository.Builder("release", "default", releaseModuleRepositoryUrl)
                    .setSnapshotPolicy(DISABLED_REPOSITORY_POLICY)
                    .setReleasePolicy(ENABLED_RELEASE_POLICY);
            if (authentication != null) {
                builder.setAuthentication(authentication);
            }
            releaseRepository = builder.build();
        }

        if (snapshotModuleRepositoryUrl != null) {
            Builder builder = new RemoteRepository.Builder("snapshot", "default", snapshotModuleRepositoryUrl)
                    .setSnapshotPolicy(ENABLED_SNAPSHOT_POLICY)
                    .setReleasePolicy(DISABLED_REPOSITORY_POLICY);
            if (authentication != null) {
                builder.setAuthentication(authentication);
            }
            snapshotRepository = builder.build();
        }
        LOG.debug("done initialising repo system");
    }

    public RepositorySystem initRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        locator.addService(TransporterFactory.class, WagonTransporterFactory.class);
        locator.setService(org.eclipse.aether.spi.log.LoggerFactory.class, Slf4jLoggerFactory.class);

        return locator.getService(RepositorySystem.class);

    }

    private RepositorySystemSession newSession(MavenCoords mavenCoords) {
        if (mavenCoords.isSnapshot()) {
            // then we check if the version is in the loaded snapshots - if it we need to create a new session, otherwise we can reuse thes napshot session
            if (snapshotVersionsLoaded.contains(mavenCoords)) {
                snapshotVersionsLoaded.add(mavenCoords);
                LOG.info("request for session for {}, already loaded so returning new session", mavenCoords);
                this.snapshotSession = getSession(repoSystem);
                snapshotVersionsLoaded.clear();
                return this.snapshotSession;
            } else {
                LOG.info("request for session for {}, not yet loaded so using exsiting session", mavenCoords);
                return this.snapshotSession = snapshotSession == null ? getSession(repoSystem) : snapshotSession;
            }
        } else {
            LOG.info("request for stabilised {}, using existing session", mavenCoords);
            return this.stabilisedSession = stabilisedSession == null ? getSession(repoSystem) : stabilisedSession;
        }
    }

    private RepositorySystemSession getSession(RepositorySystem system) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        String[] parts = { repositoryBase, ".m2", "repository" };
        final String repository = Arrays.stream(parts).collect(Collectors.joining(File.separator));
        LOG.info("Adding Local Maven Repo with path {}", repository);
        LocalRepository localRepo = new LocalRepository(repository);
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        return session;
    }

    public RemoteRepository getReleaseRepository() {
        return releaseRepository;
    }

    public RemoteRepository getSnapshotRepository() {
        return snapshotRepository;
    }

}
