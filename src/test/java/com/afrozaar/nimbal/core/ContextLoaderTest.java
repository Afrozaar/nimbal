package com.afrozaar.nimbal.core;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.StreamSupport;

public class ContextLoaderTest {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ContextLoaderTest.class);

    @Test
    public void Go() throws ErrorLoadingArtifactException, IOException {

        Path path = Paths.get(System.getProperty("user.dir"), "maven-repo", "m2");
        try {
            Path realPath = path.toRealPath();
            MoreFiles.deleteDirectoryContents(realPath, RecursiveDeleteOption.ALLOW_INSECURE);
        } catch (NoSuchFileException e) {
            // the first time this is run, this will occur
        }

        MavenRepositoriesManager manager = new MavenRepositoriesManager("http://repo1.maven.org/maven2");
        manager.setRepositoryBase(System.getProperty("user.dir") + File.separator + "maven-repo");
        manager.setM2Folder("m2");
        manager.init();

        ContextLoader loader = new ContextLoader(manager);

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
}
