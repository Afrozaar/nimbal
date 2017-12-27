package com.afrozaar.nimbal.legacy;

import com.afrozaar.nimbal.core.ErrorLoadingArtifactException;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.io.ByteProcessor;
import com.google.common.io.ByteSource;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.SmartClassLoader;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.util.AntPathMatcher;

import org.eclipse.aether.metadata.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Predicate;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.WeakHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ModuleLoader implements IModuleLoader {

    public class UnableToFindSnapshotVersionException extends SnapshotVersionException {

        UnableToFindSnapshotVersionException(String message, Object... args) {
            super(message, args);
        }

    }

    public static class SnapshotVersionException extends ErrorLoadingArtifactException {

        SnapshotVersionException(String message, Object... args) {
            super(message, args);
        }

        @Override
        public String getError() {
            return "SNAPSHOTS NOT ALLOWED";
        }

    }

    public static class SnapshotVersionsAreNotAllowedException extends SnapshotVersionException {

        SnapshotVersionsAreNotAllowedException(String message, Object... args) {
            super(message, args);
        }

    }

    public static int count = 1;

    public static final class URLClassLoaderExtension extends URLClassLoader implements SmartClassLoader {

        private final String artifactId;
        Logger LOG = LoggerFactory.getLogger(this.getClass());

        private List<String> ringFencedFilters = Lists.newLinkedList();

        public URLClassLoaderExtension(URL[] urls, ClassLoader parent, String artifactId) {
            super(urls, parent);
            this.artifactId = artifactId;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            LOG.trace("in find class {} class {}", this, name);
            return super.findClass(name);
        }

        @Override
        public URL findResource(String name) {
            LOG.trace("in find resource {}  {} ", this, name);
            return super.findResource(name);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            LOG.trace("in load class {} {}", this, name);
            return super.loadClass(name);
        }

        private static final AntPathMatcher antPathMatcher = new AntPathMatcher(".");

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            LOG.trace("in load class {} {}, {}", artifactId, this, name, resolve);

            boolean loadClassLocally = isLoadClassLocally(name, ringFencedFilters);

            if (loadClassLocally) {
                LOG.debug("loading class {} from module and not asking parent", name);
                Class<?> findClass = findLoadedClass(name);
                if (findClass == null) {
                    findClass = findClass(name);
                }
                if (resolve) {
                    resolveClass(findClass);
                }
                LOG.debug("class {} successfully loaded from module", name);
                return findClass;
            } else {
                return super.loadClass(name, resolve);
            }
        }

        static boolean isLoadClassLocally(String name, List<String> ringFencedFilters) {
            Predicate<String> regexFilter = filter -> {
                try {
                    return Pattern.matches(filter, name);
                } catch (PatternSyntaxException pse) {
                    /* we auto select between regex compliant filters and "ant path matchers" so we need to silently swallow invalid regular expressions */
                    return false;
                }
            };
            Predicate<String> antPathFilter = filter -> antPathMatcher.match(filter, name);

            return ringFencedFilters.stream().anyMatch(antPathFilter.or(regexFilter));
        }

        int id = count++;

        @Override
        public String toString() {
            return "[ URL Class Loader - " + artifactId + " " + id + " ]";
        }

        @Override
        public boolean isClassReloadable(Class<?> clazz) {
            LOG.debug("is class reloadable called for {}, returning true", clazz);
            return true;
        }

        public List<String> getRingFencedFilters() {
            return ringFencedFilters;
        }

        public void setRingFencedFilters(List<String> ringFencedFilters) {
            this.ringFencedFilters = ringFencedFilters;
        }
    }

    Map<ClassLoader, File> classLoaderBaseDir = new WeakHashMap<>();
    /**
     * Logger for this class
     */
    private static final Logger LOG = LoggerFactory.getLogger(ModuleLoader.class);

    private String mavenRepository = "https://maven-repository.afrozaar.com/artifactory/libs-release";

    private String username = "user";
    private String password = "v}GB8(w^6e+,\"$+";

    private String snapshotMavenRepository;
    private boolean snapshotsEnabled;

    private static ITempDirProvider tempDirProvider = () -> System.getProperty("java.io.tmpdir") + File.separator + "classloader" + File.separator;

    public ModuleLoader(String mavenRepository, String username, String password) {
        super();
        if (mavenRepository != null) {
            try {
                new URL(mavenRepository);
                this.mavenRepository = mavenRepository;
                this.username = username;
                this.password = password;
            } catch (MalformedURLException e) {
                LOG.warn("given maven repository invalid, using default= {} and default username and password", this.mavenRepository);
            }
        }
    }

    /**
     * constructor can be used when loading from local maven repository
     */
    public ModuleLoader() {

    }

    public ClassLoader loadFromMaven(String groupId, String artifactId, String version, String packaging, ClassLoader parentClassLoader)
            throws IOException, ErrorLoadingArtifactException, SnapshotVersionException {

        ByteSource source = getRemoteByteSource(groupId, artifactId, version, packaging);

        return getClassLoader(source, parentClassLoader, groupId, artifactId, version, packaging);

    }

    /* (non-Javadoc)
     * @see com.afrozaar.util.spring.IModuleLoader#getRemoteByteSource(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public ByteSource getRemoteByteSource(String groupId, String artifactId, String version, String extension) throws SnapshotVersionException, IOException {
        /*
         * final HttpClient client = new HttpClient();
        client.getParams().setAuthenticationPreemptive(true);
        client.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(user.getUsername(), user.getPassword()));
        final GetMethod method = new GetMethod(uri);
        client.executeMethod(method);
         */
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new Credentials() {

            @Override
            public Principal getUserPrincipal() {
                return new BasicUserPrincipal(username);
            }

            @Override
            public String getPassword() {
                return password;
            }
        });

        HttpClientBuilder builder = HttpClientBuilder.create().setDefaultCredentialsProvider(credentialsProvider);
        try (CloseableHttpClient build = builder.build()) {

            String uriBaobabModule0 = null;
            try {
                uriBaobabModule0 = getArtifactUri(groupId, artifactId, version, extension, "baobab-module", build);
            } catch (UnableToFindSnapshotVersionException e) {
                LOG.warn("UnableToFindSnapshotVersionException ", e);
            }
            String uri0 = null;
            ;
            try {
                uri0 = getArtifactUri(groupId, artifactId, version, extension, "", build);
            } catch (UnableToFindSnapshotVersionException e) {
                LOG.warn("UnableToFindSnapshotVersionException ", e.getMessage());
            }

            final String uriBaobabModule = uriBaobabModule0;
            final String uri = uri0;

            ByteSource source = new ByteSource() {

                private CloseableHttpResponse execute;

                @Override
                public InputStream openStream() throws IOException {
                    return uriBaobabModule != null ? getStream(uriBaobabModule) : getStream(uri);
                }

                private InputStream getStream(String uri2) throws IllegalStateException, IOException {
                    LOG.debug("loading {}", uri2);
                    final HttpGet get = new HttpGet(uri2);
                    execute = build.execute(get);
                    if (execute.getStatusLine().getStatusCode() == 200) {
                        return execute.getEntity().getContent();
                    } else {
                        EntityUtils.consumeQuietly(execute.getEntity());
                        return null;
                    }
                }

                @Override
                public InputStream openBufferedStream() throws IOException {
                    return new BufferedInputStream(openStream(), 1000000);
                }
            };

            CachingByteSource cachingByteSource = new CachingByteSource(source);

            int x = 0;
            ByteProcessor<String> processor = new ByteProcessor<String>() {

                @Override
                public boolean processBytes(byte[] buf, int off, int len) throws IOException {
                    return true;
                }

                @Override
                public String getResult() {
                    // TODO Auto-generated method stub
                    return null;
                }
            };
            cachingByteSource.read(processor);
            return cachingByteSource;

        }
    }

    protected String getArtifactUri(String groupId, String artifactId, String version, String extension, String classifier, final CloseableHttpClient build)
            throws SnapshotVersionException {
        classifier = classifier == null ? "" : classifier;
        String fileVersion = version;
        String repository = null;
        if (version.endsWith("SNAPSHOT")) {
            if (!snapshotsEnabled) {
                throw new SnapshotVersionsAreNotAllowedException("Snapshots are not enabled, unable to load snapshot dependency, {}-{}-{}", groupId, artifactId, version);
            }
            SnapshotVersion snapshotVersion = getAbsoluteSnapshotVersion(build, groupId, artifactId, version, classifier, extension);
            return snapshotMavenRepository + "/" + groupId.replace(".", "/") + "/" + artifactId + "/" + version + "/" + artifactId + "-" + snapshotVersion.getVersion()
                    + (snapshotVersion.getClassifier().equals("") ? "" : "-" + snapshotVersion.getClassifier()) + "." + extension;
        } else {
            return mavenRepository + "/" + groupId.replace(".", "/") + "/" + artifactId + "/" + version + "/" + artifactId + "-" + fileVersion
                    + (classifier.equals("") ? "" : "-" + classifier) + "." + extension;
        }
        //System.out.println("https://maven-repository.afrozaar.com/artifactory/libs-release/com/afrozaar/ashes/ashes-wordpress/2.3.9/ashes-wordpress-2.3.9.jar");

    }

    private SnapshotVersion getAbsoluteSnapshotVersion(CloseableHttpClient build, String groupId, String artifactId, String version, String classifier, String extension)
            throws SnapshotVersionException {
        if (snapshotMavenRepository == null) {
            throw new SnapshotVersionsAreNotAllowedException("requested version {} of {}:{} but snapshot repository is not set so snapshot versions are not allowed");
        }

        final String uri = snapshotMavenRepository + "/" + groupId.replace(".", "/") + "/" + artifactId + "/" + version + "/maven-metadata.xml";

        HttpGet get = new HttpGet(uri);

        try (CloseableHttpResponse execute = build.execute(get)) {

            if (execute.getStatusLine().getStatusCode() == 200) {
                Metadata read = new MetadataXpp3Reader().read(execute.getEntity().getContent());
                Optional<SnapshotVersion> zipVersion = read.getVersioning()
                        .getSnapshotVersions()
                        .stream()
                        .filter(snapshotVersion -> snapshotVersion.getExtension().equals("zip") && Objects.equals(snapshotVersion.getClassifier(), classifier))
                        .findAny();
                if (zipVersion.isPresent()) {
                    return zipVersion.get();
                }
                ;
            } else {
                throw new SnapshotVersionException("status code returned form {}-{}-{} = {}", groupId, artifactId, version, execute.getStatusLine().getStatusCode());
            }
        } catch (IOException | IllegalStateException | XmlPullParserException e) {
            LOG.error("Exception caught: {}", e, e);
        }
        throw new UnableToFindSnapshotVersionException("snapshot version requested but cannot find meta data: snapshotRepo={}, groupId={}, artifactId={}, version={}, extension={}",
                snapshotMavenRepository, groupId, artifactId, version, extension);

    }

    /* (non-Javadoc)
     * @see com.afrozaar.util.spring.IModuleLoader#getClassLoader(com.google.common.io.ByteSource, java.lang.ClassLoader, java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public ClassLoader getClassLoader(ByteSource source, ClassLoader parentClassLoader, String groupId, final String artifactId, String version, String packaging)
            throws ErrorLoadingArtifactException {
        //IOUtils.copy(execute.getEntity().getContent(), new FileOutputStream("x.zip"));

        String tempDir = tempDirProvider.getTempDir();
        File basedir2 = new File(tempDir + artifactId + "-" + version);
        try {
            FileUtils.cleanDirectory(basedir2);
        } catch (Exception e) {
            LOG.warn("error when cleaning directory {}, {}", basedir2, e.getMessage());
        }
        try {
            basedir2.mkdirs();
        } catch (Exception e) {
            LOG.error("error when making directory {}, {}", basedir2, e.getMessage());
        }
        try (InputStream openBufferedStream = source.openBufferedStream()) {
            Iterable<String> jarFiles = ClassLoaderUtil.getJarFiles(basedir2, openBufferedStream);
            //Syste
            LOG.info("found jars {} in {}", jarFiles, groupId + "/" + artifactId + "-" + version + "." + packaging);

            URLClassLoaderExtension loader = new URLClassLoaderExtension(getUrls(jarFiles), parentClassLoader, artifactId);

            Properties properties = getModuleProperties(loader);
            Object object = properties.get("ringFenceClassBlackList");
            if (object != null) {
                loader.ringFencedFilters = Splitter.on(";").splitToList(object.toString());
            }

            return loader;
        } catch (IOException e) {
            throw new ErrorLoadingArtifactException(
                    Messages.formatToString("failed to load module group:{}, artifact:{}, version:{} and packaging:{}", groupId, artifactId, version, packaging), e);
        }
    }

    @Override
    public ClassLoader getClassLoader(URL[] urls, ClassLoader parentClassLoader, String artifactId, String[] ringFenceBlackList) {
        URLClassLoaderExtension loader = new URLClassLoaderExtension(urls, parentClassLoader, artifactId);
        loader.ringFencedFilters = Arrays.asList(ringFenceBlackList);
        return loader;
    }

    public interface ITempDirProvider {
        String getTempDir();
    }

    private static URL[] getUrls(Iterable<String> fileList) {
        List<URL> urls = new ArrayList<>();
        for (String string : fileList) {
            try {
                urls.add(new URL("file", null, string));
            } catch (MalformedURLException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return urls.toArray(new URL[urls.size()]);
    }

    @Override
    public void cleanClassLoaderFolder(ClassLoader classLoader) {
        File file = classLoaderBaseDir.get(classLoader);
        if (file != null) {
            try {
                FileUtils.cleanDirectory(file);
                FileUtils.forceDelete(file);
            } catch (IOException e) {
                LOG.error("error cleaning and deleting folder {}", file);
            }
        }
    }

    /* (non-Javadoc)
     * @see com.afrozaar.util.spring.IModuleLoader#getModuleProperties(java.lang.ClassLoader)
     */
    @Override
    public Properties getModuleProperties(ClassLoader classLoader) throws IOException {
        InputStream is = getModuleInf(classLoader);

        Properties p = new Properties();
        try (InputStream resourceAsStream = is) {
            p.load(resourceAsStream);
        } catch (IOException ioe) {
            throw ioe;
        }
        return p;
    }

    public static InputStream getModuleInf(ClassLoader classLoader) throws IOException {
        InputStream is;
        if (classLoader instanceof URLClassLoader) {
            URLClassLoader urlClassLoader = (URLClassLoader) classLoader;
            URL findResource = urlClassLoader.findResource("module.inf");
            is = findResource.openStream();
        } else {
            is = classLoader.getResourceAsStream("module.inf");
        }
        return is;
    }

    @Override
    public ConfigurableApplicationContext loadModule(ClassLoader classLoader, String moduleClass, String moduleName, ApplicationContext parent, Properties properties,
            List<BeanPostProcessor> beanPostProcessors) throws ClassNotFoundException {
        LOG.info("Loading Ashes External Module {}", moduleClass);
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(classLoader);
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        String[] staticProfiles = ApplicationContextFactory.getStaticProfiles();
        if (staticProfiles != null) {
            List<String> asList = new ArrayList<>(Arrays.asList(staticProfiles));
            asList.add("embedded");
            context.getEnvironment().setActiveProfiles(asList.toArray(new String[asList.size()]));
        }
        //        MutablePropertySources sources = env.getPropertySources();
        //        sources.addFirst(new PropertiesPropertySource("tcp-ip",

        beanPostProcessors.stream().forEach((postProcessor) -> context.getBeanFactory().addBeanPostProcessor(postProcessor));

        properties.put("moduleName", moduleName);
        context.getEnvironment().getPropertySources().addFirst(new PropertiesPropertySource("modulemeta", properties));
        context.setParent(parent);
        context.register(classLoader.loadClass(moduleClass));
        context.setDisplayName(moduleClass);
        context.setClassLoader(classLoader);
        context.refresh();
        LOG.info("Module {} Loaded", moduleClass);
        Thread.currentThread().setContextClassLoader(contextClassLoader);
        return context;

    }

    public static class Builder {
        private String mavenRepository;
        private String username;
        private String password;
        private String snapshotMavenRepository;
        private boolean snapshotsEnabled;

        public Builder() {
        }

        public Builder(ModuleLoader bean) {
            this.mavenRepository = bean.mavenRepository;
            this.username = bean.username;
            this.password = bean.password;
            this.snapshotMavenRepository = bean.snapshotMavenRepository;
        }

        public Builder withMavenRepository(String mavenRepository) {
            this.mavenRepository = mavenRepository;
            return this;
        }

        public Builder withUsername(String username) {
            this.username = username;
            return this;
        }

        public Builder withPassword(String password) {
            this.password = password;
            return this;
        }

        public Builder withSnapshotMavenRepository(String snapshotMavenRepository) throws MalformedURLException {
            new URL(snapshotMavenRepository);
            this.snapshotMavenRepository = snapshotMavenRepository;
            return this;
        }

        public IModuleLoader build() {
            ModuleLoader moduleLoader = new ModuleLoader(mavenRepository, username, password);
            moduleLoader.snapshotMavenRepository = snapshotMavenRepository;
            moduleLoader.snapshotsEnabled = snapshotsEnabled;
            LOG.debug("creating loader: repository={}, snapshotRepository={} user='{}', password='{}', SnapshotEnabled={}", mavenRepository, snapshotMavenRepository, username,
                    password, snapshotsEnabled);
            return moduleLoader;

        }

        public Builder enabledForSnapshot(boolean snapshotModulesEnabled) {
            this.snapshotsEnabled = snapshotModulesEnabled;
            return this;
        }
    }

    public static Builder moduleLoader() {
        return new Builder();
    }

    @Override
    public void setSnapshotsEnabled(boolean snapshotsEnabled) {
        this.snapshotsEnabled = snapshotsEnabled;
    }

    public static class ModulePropertiesNotInZipException extends IOException {

    }

    /* (non-Javadoc)
     * @see com.afrozaar.util.spring.IModuleLoader#getModuleProperties(com.google.common.io.ByteSource)
     */
    @Override
    public Properties getModuleProperties(ByteSource source) throws ModulePropertiesNotInZipException, IOException {
        Properties p = null;
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(source.openBufferedStream()))) {

            ZipEntry entry = null;
            while ((entry = zis.getNextEntry()) != null) {

                if (entry.getName().equals("module.inf")) {
                    p = new Properties();
                    p.load(zis);
                }
            }
        }
        if (p != null) {
            return p;
        } else {
            throw new ModulePropertiesNotInZipException();
        }
    }

}
