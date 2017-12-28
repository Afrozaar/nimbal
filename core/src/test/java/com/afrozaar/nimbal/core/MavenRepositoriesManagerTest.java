package com.afrozaar.nimbal.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.afrozaar.nimbal.core.MavenRepositoriesManager;

import org.eclipse.aether.repository.RemoteRepository;
import org.junit.Test;

public class MavenRepositoriesManagerTest {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(MavenRepositoriesManagerTest.class);

    @Test
    public void ReleaseRepo() {
        MavenRepositoriesManager manager = new MavenRepositoriesManager("http://repo1.maven.org/maven2");
        manager.init();
        RemoteRepository releaseRepository = manager.getReleaseRepository();
        assertThat(releaseRepository.getPolicy(true).isEnabled()).isFalse();
        assertThat(releaseRepository.getUrl()).isEqualTo("http://repo1.maven.org/maven2");
        assertThat(manager.getSnapshotRepository()).isEmpty();
    }

    @Test
    public void SnapshotRepo() {
        String snapshotUrl = "http://repo.spring.io/snapshot";
        MavenRepositoriesManager manager = new MavenRepositoriesManager("http://repo1.maven.org/maven2", snapshotUrl, null, null);

        manager.init();
        RemoteRepository snapshotRepository = manager.getSnapshotRepository().orElse(null);
        assertThat(snapshotRepository).isNotNull();
        assertThat(snapshotRepository.getPolicy(true).isEnabled()).isTrue();

        assertThat(snapshotRepository.getUrl()).isEqualTo(snapshotUrl);
    }
}
