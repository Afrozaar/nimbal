package com.afrozaar.nimbal.core;

import static java.lang.String.format;
import static java.lang.System.getProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

public class ModuleManager implements ApplicationContextAware, ModuleInitialise {
    static final Logger LOG = LoggerFactory.getLogger(ModuleManager.class);

    @Value("${poolName}")
    private String environmentName;
    @Value("${loadModulesFromFileSystem}")
    private boolean loadModulesFromFileSystem;
    @Value("${localMavenRepositoryPath}")
    private String localMavenRepository = buildPath(File.separator, getProperty("user.home"), ".m2", "repository");
    @Inject
    private IRegistry registry;
    @Value("${defaultModules}")
    private String defaultModules;
    @Value("${moduleRepository}")
    private String moduleRepository;
    @Value("${snapshotModuleRepository}")
    private String snapshotModuleRepository;
    @Value("${snapshotModulesEnabled}")
    private boolean snapshotModulesEnabled;
    @Value("${moduleRepositoryUsername}")
    private String moduleRepositoryUsername;
    @Value("${moduleRepositoryPassword}")
    private String moduleRepositoryPassword;
    @Value("${localRepositoryBase}")
    private String repositoryBase = System.getProperty("user.home");

    @Inject
    private TenantLocal tenantLocal;
    @Inject
    private TenantRepository tenantRepository;
    @Inject
    private ModuleLoadStateRepository repository;
    @Inject
    private ModuleStateService service;
    /*@Inject
    @Named("moduleRegistrar")*/
    private AbstractModuleRegistrar moduleRegistrar;
    @Inject
    @Named("remoteServiceInjectionProcessor")
    private RemoteServiceInjectionPostProcessor postProcessor;

    private RegisterServicePostProcessor registerServicePostProcessor;

    public ModuleManager(@Qualifier("moduleRegistrar") AbstractModuleRegistrar moduleRegistrar) {
        this.moduleRegistrar = moduleRegistrar;
    }

    @Inject
    public void setInstanceProvider(InstanceProvider provider) {
        this.instanceId = provider.getInstanceId();
        this.instanceProvider = provider;
        LOG.info("setting instance id to {}", this.instanceId);
    }

    InstanceProvider instanceProvider;

    @Inject
    public void setEventBus(EventBus eventBus) {
        LOG.info("registering {} on {}", this, eventBus);
        this.eventBus = eventBus;
        eventBus.register(this);
    }

    private ApplicationContext context;
    private Map<String, ConfigurableApplicationContext> modules = new LinkedHashMap<>();
    private Map<String, ModuleMetaData> modulesMeta = new LinkedHashMap<>();
    private EventBus eventBus;
    IModuleLoader loader;
    private String instanceId;
    private Map<String, AshesModule> moduleManagers = Maps.newHashMap();
    private LoadModuleFunction loadModuleWithAether;

    @Inject
    private ServiceRegistry serviceRegistry;

    @Inject
    private ModuleLoadStateRepository moduleStateRepository;

    @PostConstruct
    public void setupModuleLoader() throws MalformedURLException {
        // @formatter:off
        this.loader = new Builder()
                .withMavenRepository(moduleRepository)
                .withSnapshotMavenRepository(snapshotModuleRepository)
                .withPassword(moduleRepositoryPassword)
                .withUsername(moduleRepositoryUsername)
                .enabledForSnapshot(snapshotModulesEnabled)
                .build();
        // @formatter:on
        loadModuleWithAether = new AetherModuleContextLoader(this.loader, registry);

        registerServicePostProcessor = new RegisterServicePostProcessor(serviceRegistry);

        service = new ModuleStateService(environmentName, moduleStateRepository);
    }

    public static class ModuleLoadRequest extends LocalEvent implements Serializable {

        public ModuleLoadRequest(Long loadStateId, String groupId, String artifactId, String version, String packaging) {
            super();
            this.loadStateId = loadStateId;
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.packaging = packaging;
        }

        private Long loadStateId;
        private String groupId;
        private String artifactId;
        private String version;
        private String packaging;
    }

    public static class ModulesInitialisedEvent implements Serializable {

    }

    public static class ModuleUnloadRequest extends LocalEvent implements Serializable {

        public ModuleUnloadRequest(String moduleName) {
            super();
            this.moduleName = moduleName;
        }

        private String moduleName;
    }

    @Subscribe
    public void requestLoad(ModuleLoadRequest request) {
        try {
            ModuleLoadState loadState = repository.findOne(request.loadStateId);
            // loadModule0(request.groupId, request.artifactId, request.version, request.packaging);
            loadModule0(loadState);
        } catch (Exception e) {
            LOG.error("error loading module {}", request, e);
        }
    }

    @Subscribe
    public void requestUnload(ModuleUnloadRequest request) {
        try {
            unLoadModule0(request.moduleName, CloseOperation.UNLOAD);
        } catch (Exception e) {
            LOG.error("error unloading module {}", request, e);
        }
    }

    /**
     * this is the dynamic module loader call...
     * <p>
     * This is the one used by the REST service only... don't use this unless
     * you're calling from rest service
     * </p>
     */
    public ModuleMetaData loadModule(String groupId, String artifactId, String version, String packaging) throws FileNotFoundException, ClassNotFoundException,
                                                                                                                 IOException {

        ModuleMetaData metaData = null;
        if ((metaData = canLoad(groupId, artifactId)) != null) {
            return metaData;
        }

        ModuleLoadState loadState = service.setState(groupId, artifactId, version, packaging, null, instanceId, LoadState.NOT_LOADED);
        ModuleMetaData loadModule0 = null;
        try {
            loadModule0 = loadModule0(loadState);
        } catch (ErrorLoadingArtifactException e) {
            LOG.error("error loading artifact {} removing entry in module state table and not broadcast load request", e.getMessage(), e);
            service.delete(loadState);
            return getErrorModuleMetaData(groupId, artifactId, version, e);
        }
        if (loadModule0 == null) {
            return getErrorModuleMetaData(groupId, artifactId, version, null);
        }
        BroadcastEvent.broadcastEvent(eventBus, true, new ModuleLoadRequest(loadState.getId(), groupId, artifactId, version, packaging));
        // ModuleMetaData loadModule0 = loadModule0(groupId, artifactId, version, packaging);
        loadModule0.responseCode = 200; // OK
        service.update(loadState, loadModule0);
        loadModule0.instanceId = instanceId;
        loadModule0.loadState = service.getLoadState(loadState, instanceId);
        return loadModule0;
    }

    @Transactional
    private ModuleMetaData canLoad(String groupId, String artifactId) {
        ModuleMetaData metaData = null;
        ModuleLoadState moduleLoadState = service.getState(groupId, artifactId);
        {
            if (moduleLoadState != null) {
                LoadState loadState = service.getLoadState(moduleLoadState, instanceId);
                if ((loadState == LoadState.LOADING || loadState == LoadState.LOADED)) {
                    metaData = moduleLoadState.getMetaData(instanceId);
                }
            }
        }
        return metaData;
    }

    private ModuleMetaData getErrorModuleMetaData(String groupId, String artifactId, String version, ErrorLoadingArtifactException e) {
        ModuleMetaData moduleMetaData = new ModuleMetaData(groupId, artifactId, version, environmentName);
        moduleMetaData.moduleName = e != null ? e.getError() : "ERROR LOADING ARTIFACT";
        if (e != null) {
            moduleMetaData.exceptionStackFrames = Arrays.stream(e.getStackTrace()).map(StackTraceElement::toString).collect(Collectors.toList());
            moduleMetaData.error = e.getMessage();
            moduleMetaData.responseCode = 400; // TODO: we can expand the use of the response code to have finer grained messages.
        } else {
            moduleMetaData.responseCode = 500;
        }
        return moduleMetaData;
    }

    private ModuleMetaData loadModule0(ModuleLoadState moduleLoadState) throws ErrorLoadingArtifactException {
        LOG.info("At {}:{} Loading module {}:{}:{}", this.instanceId, instanceId, moduleLoadState.getGroupId(), moduleLoadState.getArtifactId(), moduleLoadState
                .getVersion());

        service.setState(moduleLoadState, instanceId, LoadState.LOADING);
        try {
            ModuleMetaData loadModule0 = loadModule0(moduleLoadState.getGroupId(), moduleLoadState.getArtifactId(), moduleLoadState.getVersion(),
                                                     moduleLoadState.getPackaging(), moduleLoadState.getModuleClass());
            service.setState(moduleLoadState, instanceId, LoadState.LOADED);
            service.setModuleState(moduleLoadState, ModuleState.ACTIVE);
            return loadModule0;
        } catch (ClassNotFoundException | IOException e) {
            LOG.error("error loading {}", moduleLoadState, e);
            service.setState(moduleLoadState, instanceId, LoadState.ERROR_LOADING);
            return null;
        } catch (SnapshotVersionException sne) {
            LOG.error("error loading  {}", moduleLoadState, sne);
            service.setState(moduleLoadState, instanceId, LoadState.SNAPSHOTS_NOT_ALLOWED);
            throw sne;
        } catch (ErrorLoadingArtifactException e) {
            throw e;
        } catch (Throwable e) {
            LOG.error("error loading  {}", moduleLoadState, e);
            service.setState(moduleLoadState, instanceId, LoadState.ERROR_LOADING);
            return null;
        }

    }

    /**
     * an extension of Function that adds the exceptions I need...
     */
    interface LoadModuleFunction extends java.util.function.Function<MavenCoords, com.afrozaar.ashes.core.model.modules.ModuleMetaData> {

        ModuleMetaData applyWithExceptions(MavenCoords t) throws FileNotFoundException, IOException, ClassNotFoundException, SnapshotVersionException,
                                                                 ErrorLoadingArtifactException;

        @Override
        default ModuleMetaData apply(MavenCoords t) {
            throw new UnsupportedOperationException("do not call this apply as it does not throw the requisite exceptions");
        }
    }

    public ModuleMetaData loadModule0(String groupId, String artifactId, String version, String packaging, String devModeModuleClass)
            throws FileNotFoundException, IOException, ClassNotFoundException, ErrorLoadingArtifactException, SnapshotVersionException {
        MavenCoords mavenCoords = new MavenCoords(groupId, artifactId, version, packaging);
        return loadModuleWithAether.applyWithExceptions(mavenCoords);
    }

    public static class MavenCoords {
        public String groupId;
        public String artifactId;
        public String version;
        public String packaging;

        public MavenCoords(String groupId, String artifactId, String version, String packaging) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.packaging = packaging;
        }

        public String getAsString() {
            //com.afrozaar.ashes.module:ashes-sbp-scheduled:jar:module:0.4.0-SNAPSHOT
            return format("%s:%s:%s", groupId, artifactId, version);
        }

        @Override
        public String toString() {
            return "MavenCoords [groupId=" + groupId + ", artifactId=" + artifactId + ", version=" + version + ", packaging=" + packaging + "]";
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((artifactId == null) ? 0 : artifactId.hashCode());
            result = prime * result + ((groupId == null) ? 0 : groupId.hashCode());
            result = prime * result + ((version == null) ? 0 : version.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            MavenCoords other = (MavenCoords) obj;
            if (artifactId == null) {
                if (other.artifactId != null)
                    return false;
            } else if (!artifactId.equals(other.artifactId))
                return false;
            if (groupId == null) {
                if (other.groupId != null)
                    return false;
            } else if (!groupId.equals(other.groupId))
                return false;
            if (version == null) {
                if (other.version != null)
                    return false;
            } else if (!version.equals(other.version))
                return false;
            return true;
        }

        public boolean isStable() {
            return !isSnapshot();
        }

        public boolean isSnapshot() {
            return version != null && version.endsWith("SNAPSHOT");
        }
    }

    /**
     * I realise this creates a thread but I don't want to slow down the calling
     * context at all - thus the thread.
     * <p>
     * <p>
     * The reason I need a thread is because I want to only call this method
     * once the event - ModuleRegistered has been fully processed and since it
     * is an asynchronous event I need to give it a bit of time.
     * <p>
     * And since it's asynchronous the wait is fine
     */
    protected void invokePostModuleLoad(Entry<String, AshesModule> first) {
        new Thread() {

            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                first.getValue().postModuleLoad();
            }
        }.start();
    }

    public ModuleMetaData loadVersion(Properties p) {
        String property = p.getProperty(ModuleMetaData.ORDER_INDEX);
        Integer index = property != null ? Integer.parseInt(property) : null;
        ModuleMetaData meta = ModuleMetaData.Builder.aModuleMetaData().withGroupId(p).withArtifactId(p).withVersion(p).withOrder(index).withModuleName(p)
                .withModuleClass(p).withEnvironment(p).build();

        LOG.info("loaded version info: module={}, groupId={}, artifactId={}, version={}", meta.moduleName, meta.groupId, meta.artifactId, meta.version);
        return meta;
    }

    public ModuleMetaData loadVersion(ModuleInfo moduleInfo, MavenCoords mavenCoords) {
        Integer index = moduleInfo.order();
        ModuleMetaData meta = new ModuleMetaData(mavenCoords.groupId, mavenCoords.artifactId, mavenCoords.version, null, index, moduleInfo.name(), moduleInfo
                .moduleClass(), new Date(), null);
        LOG.info("loaded version info: module={}, groupId={}, artifactId={}, version={}", moduleInfo.name(), mavenCoords.groupId, mavenCoords.artifactId,
                 mavenCoords.version);
        return meta;
    }

    private ByteSource getFileByteSource(final String groupId, final String artifactId, final String version, final String packaging) {
        final String ARTIFACT_FILE_STANDARD = "%s-%s.%s";
        final String ARTIFACT_FILE_MODULE = "%-%s-baobab-module.%s";
        final Function<String, String> artifactPath = artifactFileFormatString -> new StringJoiner("/").add(localMavenRepository).add(groupId.replace('.', '/'))
                .add(artifactId).add(version).add(format(artifactFileFormatString, artifactId, version, packaging)).toString();

        final Function<File, ByteSource> byteSourceFactory = file -> new ByteSource() {
            @Override
            public InputStream openStream() throws IOException {
                return new FileInputStream(file);
            }
        };

        final File standardArtifact = new File(artifactPath.apply(ARTIFACT_FILE_STANDARD));
        if (standardArtifact.exists()) {
            return byteSourceFactory.apply(standardArtifact);
        } else {
            final File moduleArtifact = new File(artifactPath.apply(ARTIFACT_FILE_MODULE));
            return byteSourceFactory.apply(moduleArtifact);
        }

    }

    public Map<String, Object> unLoadModule(String moduleName) {
        ModuleLoadState module = service.getModule(moduleName);
        if (module == null) {
            return ImmutableMap.of("error", format("Modulename '%s' not found", moduleName));
        }
        BroadcastEvent.broadcastEvent(eventBus, false, new ModuleUnloadRequest(moduleName));
        return ImmutableMap.of(ModuleMetaData.MODULE_NAME, moduleName, "message", "unload request submitted", "module (before unload)", module);
    }

    public void unLoadModule0(String moduleName, CloseOperation closeOperation) {
        try {
            service.setState(moduleName, instanceId, LoadState.UNLOADING);
            closeModule(moduleName, closeOperation);
            ModuleMetaData moduleMetaData = modulesMeta.get(moduleName);
            if (moduleMetaData != null) {
                loader.cleanClassLoaderFolder(moduleMetaData.getClassLoader());
            }
            ModuleMetaData remove = modulesMeta.remove(moduleName);
            if (remove != null) {
                eventBus.post(new ModuleUnRegisteredEvent(remove));
            }
            service.setState(moduleName, instanceId, LoadState.UNLOADED);
        } catch (Exception e) {
            LOG.error("Error unloading module={}", moduleName, e);
            service.setState(moduleName, instanceId, LoadState.ERROR_UNLOADING);
        }
    }

    private void closeModule(String moduleName, CloseOperation closeOperation) {
        ConfigurableApplicationContext applicationContext = modules.get(moduleName);
        if (applicationContext == null) {
            LOG.error("attempted to close module {} but it is not found, modules available are {}", moduleName, modules.keySet());
        } else {
            AshesModule ashesModule = moduleManagers.get(moduleName);
            if (ashesModule != null) {
                try {
                    closeOperation.accept(ashesModule);
                } catch (Exception e) {
                    LOG.warn("error closing {} with operation {}", ashesModule, closeOperation);
                }
            } else {
                LOG.warn("cannot find module manager {} in map {}, cannot call close", moduleName, moduleManagers);
            }
            service.setModuleState(moduleName, closeOperation.getTargetModuleState(service.getModule(moduleName).getModuleState()));
            moduleManagers.remove(moduleName);

            applicationContext.close();
            ConfigurableApplicationContext remove = modules.remove(moduleName);
            LOG.debug("{} removed. Left over modules = {}", remove, modules);
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;

    }

    @Transactional
    public List<ModuleLoadState> getModules(boolean extended) {
        ModuleStateDto modules0 = getModules0(extended);
        return modules0.modules;
    }

    @Transactional
    public ModuleStateDto getModules0(boolean extended) {

        ModuleStateDto dto = new ModuleStateDto();
        final String instanceId = instanceProvider.getInstanceId();
        dto.requestedNodeId = instanceId;
        List<ModuleLoadState> findAll = service.findAll(Direction.ASC);
        dto.modules = findAll;
        Set<String> instanceIds = Sets.newLinkedHashSet();

        Predicate<Date> dateNull = Objects::isNull;
        Predicate<Date> olderThanAWeek = date -> DateUtils.isDateDeltaBefore(date, Calendar.DAY_OF_YEAR, -7);

        findAll.forEach(moduleLoadState -> {
            Map<String, MetaLoadState> map = Maps.newHashMap();
            moduleLoadState.setExtendedLoadState(map);
            moduleLoadState.getLoadState().forEach((key, value) -> {
                MetaLoadState metaLoadState = new MetaLoadState();
                map.put(key, metaLoadState);
                instanceIds.add(key);
                metaLoadState.state = value;
            });

            moduleLoadState.getLoadDate().forEach((key, value) -> {
                MetaLoadState metaLoadState = map.get(key);
                if (metaLoadState == null) {
                    map.put(key, metaLoadState = new MetaLoadState());
                }
                metaLoadState.date = value;
                instanceIds.add(key);
            });

            MetaLoadState metaLoadState = map.get(instanceId);

            if (metaLoadState == null) {
                map.put(instanceId, metaLoadState = new MetaLoadState());
            }
            metaLoadState.status = NodeStatus.ALIVE;
            AshesModule ashesModule = moduleManagers.get(moduleLoadState.getModuleName());
            if (ashesModule != null) {
                metaLoadState.inMemory = true;
            }
        });

        if (extended) {
            Set<String> updateRemoteStates0 = updateRemoteStates(findAll);
            Set<String> updateRemoteStates = Sets.newHashSet(updateRemoteStates0);
            updateRemoteStates.add(instanceId);
            findAll.forEach(state -> {

                // set all the status to ALIVE
                state.getExtendedLoadState().forEach((key, value) -> value.status = NodeStatus.ALIVE);

                // set the ones that are _NOT_ found in the active set to DEAD
                // Sets.newLinkedHashSet(instanceId, updateRemoteStates);

                SetView<String> difference = Sets.difference(state.getExtendedLoadState().keySet(), updateRemoteStates);

                difference.forEach(inactiveInstance -> {
                    MetaLoadState metaLoadState = state.getExtendedLoadState().get(inactiveInstance);
                    if (metaLoadState != null) {
                        metaLoadState.status = NodeStatus.DEAD;
                    }
                });
            });
        }

        dto.modules.forEach(state -> {
            // @formatter:off
            Map<String, MetaLoadState> collect = state.getExtendedLoadState().entrySet().stream()
                    .filter(metaLoadState -> dateNull.or(olderThanAWeek).test(metaLoadState.getValue().date))
                    .collect(AfrozaarCollectors.toMap());
            // @formatter:on
            state.setExtendedLoadState(collect);
        });

        return dto;
    }

    public static class ModuleStateRequest extends LocalEvent implements Serializable {

        public ModuleStateRequest() {
        }

    }

    public static class ModuleStateResponse extends LocalEvent implements Serializable {

        private String instanceId;
        private Set<String> modules;

        public ModuleStateResponse(String instanceId, Set<String> modules) {
            super();
            this.instanceId = instanceId;
            this.modules = new LinkedHashSet<>(modules);
        }

    }

    public abstract static class ModuleStateResponseListener {
        @Subscribe
        public void moduleStateREsponse(ModuleStateResponse response) {
            process(response);
        }

        public abstract void process(ModuleStateResponse response);

    }

    private Set<String> updateRemoteStates(List<ModuleLoadState> findAll) {
        // send a broadcast event, each node will receive this broadcast event and respond with it's module state, once all nodes have responded (give it 5
        // seconds, continue)
        SetMultimap<String, String> states = Multimaps.synchronizedSetMultimap(LinkedHashMultimap.create());
        Set<String> activeInstances = new HashSet<>();
        ModuleStateResponseListener listener = new ModuleStateResponseListener() {

            @Override
            @Subscribe
            public void process(ModuleStateResponse response) {
                states.putAll(response.instanceId, response.modules);
                activeInstances.add(response.instanceId);
            }
        };
        eventBus.register(listener);
        BroadcastEvent.broadcastEvent(eventBus, true, new ModuleStateRequest());

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            LOG.warn("interrupted exception ", e);
        }
        eventBus.unregister(listener);

        // in states we now have instanceId -> Modules but in module load state we have a list and each module load state contains a module

        LinkedHashMultimap<String, String> moduleToInstanceState = LinkedHashMultimap.create();

        states.entries().forEach(state -> moduleToInstanceState.put(state.getValue(), state.getKey()));

        findAll.forEach(moduleLoadState -> {
            moduleToInstanceState.get(moduleLoadState.getModuleName()).forEach(instanceId -> {
                MetaLoadState metaLoadState = moduleLoadState.getExtendedLoadState().get(instanceId);
                if (metaLoadState != null) {
                    metaLoadState.inMemory = true;
                } else {
                    LOG.warn("meta Load state not found for instance {} on moduleLoadState {} and extended load state {}", instanceId, moduleLoadState,
                             moduleLoadState.getExtendedLoadState());
                }
            });
        });

        return activeInstances;
    }

    @Subscribe
    public void moduleStateRequest(ModuleStateRequest request) {
        ModuleStateResponse response = new ModuleStateResponse(instanceProvider.getInstanceId(), moduleManagers.keySet());
        BroadcastEvent.broadcastEvent(eventBus, response, request.getSourceInstanceId());
    }

    /**
     * initialise on first startup.
     * <ol>
     * <li>read state from database
     * <li>initialise class loaders and modules from what is read on database
     * and startup modules
     * </ol>
     */
    @Override
    public void contextInitialized() {

        LOG.debug("initialsing context in node {}", instanceId);
        setTenantLocal();
        setupDefaults();

        List<ModuleLoadState> modules = service.findAll(Direction.ASC);

        try {
            if (LOG.isDebugEnabled()) {
                modules.forEach(module -> LOG.debug("found existing module {}", module));
            }
            for (ModuleLoadState moduleLoadState : modules) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("moduleState(module={})={}", moduleLoadState.getArtifactId(), moduleLoadState.getModuleState());
                }

                if (moduleLoadState.getModuleState() == ModuleState.ACTIVE) {
                    LOG.info("found module to load on startup {}", moduleLoadState);
                    try {
                        LOG.info("loading module {} on Startup", moduleLoadState);
                        ModuleMetaData loadModule0 = loadModule0(moduleLoadState);
                        LOG.info("module {} loaded on Startup", moduleLoadState);
                        if (loadModule0 != null) {
                            service.update(moduleLoadState, loadModule0);
                        } else {
                            LOG.info("error loading module {} see previous error for details", moduleLoadState);
                        }
                    } catch (ErrorLoadingArtifactException e) {
                        LOG.error("error finding artifact {}", moduleLoadState, e);
                        service.setState(moduleLoadState, instanceId, LoadState.NOT_FOUND);
                    } catch (Exception e) {
                        LOG.error("error loading artifact {}", moduleLoadState, e);
                        service.setState(moduleLoadState, instanceId, LoadState.ERROR_LOADING);
                    }
                } else {
                    LOG.info("not loading module {} on startup as it is not in Closed or Loaded State", moduleLoadState.getModuleName());
                }
            }
        } catch (Throwable t) {
            LOG.error("error initialising context", t);
        } finally {
            unsetTenantLocal();
        }
        eventBus.post(new ModulesInitialisedEvent());
    }

    private void setTenantLocal() {
        Page<Tenant> tenant = tenantRepository.findAll(new PageRequest(0, 1));
        if (tenant.hasContent()) {
            tenantLocal.set(tenant.getContent().get(0));
        }
    }

    private void unsetTenantLocal() {
        tenantLocal.remove();
    }

    private void setupDefaults() {
        LOG.debug("setting up default modules from config {}", defaultModules);
        Iterable<String> split = Splitter.on(",").trimResults().split(defaultModules);
        LOG.debug("found default  modules {}", split);
        for (String string : split) {
            List<String> splitToList = Splitter.on(":").trimResults().splitToList(string);
            if (splitToList.size() >= 4) {
                String group = splitToList.get(0);
                String artifactId = splitToList.get(1);
                String version = splitToList.get(2);
                String packaging = splitToList.get(3);
                Integer order = splitToList.size() >= 5 ? Integer.parseInt(splitToList.get(4)) : null;
                service.insertModuleState(group, artifactId, version, packaging, instanceId, ModuleState.ACTIVE, LoadState.CLOSED, order);
            }
        }
    }

    public enum CloseOperation implements Consumer<AshesModule> {
        SHUTDOWN(AshesModule::onShutdown), UNLOAD(AshesModule::onUnload);

        private Consumer<AshesModule> closer;

        CloseOperation(Consumer<AshesModule> closer) {
            this.closer = closer;
        }

        @Override
        public void accept(AshesModule t) {
            closer.accept(t);
        }

        public ModuleState getTargetModuleState(ModuleState moduleState) {
            switch (this) {
            case SHUTDOWN:
                return moduleState;
            case UNLOAD:
                return ModuleState.INACTIVE;
            default:
                return null;
            }
        }
    }

    @Override
    public void contextDestroyed() {
        setTenantLocal();
        try {
            List<ModuleLoadState> modules = service.findAll(Direction.DESC);
            setModuleLoadState(modules);
        } catch (Throwable t) {
            LOG.error(" error closing ", t);
        } finally {
            unsetTenantLocal();
        }
    }

    private void setModuleLoadState(List<ModuleLoadState> modules) {
        modules.stream().forEach(moduleLoadState -> {
            if (service.getLoadState(moduleLoadState, instanceId) == LoadState.LOADED) {
                unLoadModule0(moduleLoadState.getModuleName(), CloseOperation.SHUTDOWN);
                service.setState(moduleLoadState, instanceId, LoadState.CLOSED);
            } else {
                LOG.info("found Not Loaded Module, not closing {}", moduleLoadState.getModuleName());
            }
        });
    }

    public AshesModule getModule(String moduleName) {
        return moduleManagers.get(moduleName);
    }

    private static final RepositoryPolicy ENABLED_RELEASE_POLICY = new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_NEVER,
                                                                                        RepositoryPolicy.CHECKSUM_POLICY_WARN);
    private static final RepositoryPolicy ENABLED_SNAPSHOT_POLICY = new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_ALWAYS,
                                                                                         RepositoryPolicy.CHECKSUM_POLICY_WARN);
    private static final RepositoryPolicy DISABLED_REPOSITORY_POLICY = new RepositoryPolicy(false, RepositoryPolicy.UPDATE_POLICY_DAILY,
                                                                                            RepositoryPolicy.UPDATE_POLICY_DAILY);

    private String buildPath(String separator, String... parts) {
        return Joiner.on(File.separator).join(parts);
    }

    class AetherModuleContextLoader implements LoadModuleFunction {

        private final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AetherModuleContextLoader.class);

        private RepositorySystem repoSystem;

        private RemoteRepository releaseRepository;
        private RemoteRepository snapshotRepository;

        private IModuleLoader loader;
        private IRegistry registry;

        public AetherModuleContextLoader(IModuleLoader loader, IRegistry registry) {
            this.registry = registry;
            this.loader = loader;
            LOG.debug("initialising repo system");
            repoSystem = initRepositorySystem();
            // @formatter:off
            Authentication authentication = new AuthenticationBuilder()
                    .addUsername(moduleRepositoryUsername)
                    .addPassword(moduleRepositoryPassword)
                    .build();
            releaseRepository = new RemoteRepository.Builder("afrozaar-release", "default", moduleRepository)
                    .setSnapshotPolicy(DISABLED_REPOSITORY_POLICY)
                    .setReleasePolicy(ENABLED_RELEASE_POLICY)
                    .setAuthentication(authentication)
                    .build();
            snapshotRepository = new RemoteRepository.Builder("afrozaar-snapshot", "default", snapshotModuleRepository)
                    .setSnapshotPolicy(ENABLED_SNAPSHOT_POLICY)
                    .setReleasePolicy(DISABLED_REPOSITORY_POLICY)
                    .setAuthentication(authentication)
                    .build();
            LOG.debug("done initialising repo system");
            // @formatter:on

        }

        @Override
        public ModuleMetaData applyWithExceptions(MavenCoords mavenCoords) throws FileNotFoundException, IOException, ClassNotFoundException,
                                                                                  ErrorLoadingArtifactException {

            LOG.debug("initialising repo session");
            RepositorySystemSession session = newSession(repoSystem, mavenCoords);

            LOG.debug("collecting dependencies for {}", mavenCoords);
            Dependency dependency = new Dependency(new DefaultArtifact(mavenCoords.getAsString()), JavaScopes.COMPILE);

            CollectRequest collectRequest = new CollectRequest();
            collectRequest.setRoot(dependency);
            //collectRequest.addRepository(central);
            collectRequest.addRepository(releaseRepository);
            collectRequest.addRepository(snapshotRepository);
            DependencyNode node;
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

            //ClassLoader classLoader = getClassLoader(node);

            LOG.debug("searching for annotated module");
            URL url = new URL("file", null, node.getArtifact().getFile().getAbsolutePath());
            LOG.debug("adding url {}", url);

            URL[] jars = getJars(node);
            ModuleInfo module = getModuleAnnotation(mavenCoords, url, jars);

            LOG.info("found module class {}", module);

            ParentContext parentContext = getParentContext(module, mavenCoords, jars);
            //
            //
            ConfigurableApplicationContext loadModule = loader.loadModule(parentContext.getClassLoader(), module.moduleClass(), module.name(),
                                                                          parentContext.getApplicationContext(), new Properties(),
                                                                          Lists.<BeanPostProcessor>newArrayList(postProcessor,
                                                                                                                registerServicePostProcessor));

            Map<String, AshesModule> beansOfType = loadModule.getBeansOfType(AshesModule.class);
            Entry<String, AshesModule> first = beansOfType.entrySet().stream().findFirst().orElse(null);

            ModuleMetaData metaData = loadVersion(module, mavenCoords);
            first.getValue().setMetaData(metaData);
            metaData.packaging = mavenCoords.packaging;
            metaData.environment = environmentName;
            metaData.setClassLoader(parentContext.getClassLoader());
            modules.put(metaData.moduleName, loadModule);
            moduleManagers.put(metaData.moduleName, first.getValue());
            modulesMeta.put(metaData.moduleName, metaData);

            moduleRegistrar.registerModule(first.getValue(), loadModule);

            // tell context of a new module so they can register the class loader it provides
            eventBus.post(new ModuleRegisteredEvent(metaData, loadModule));

            invokePostModuleLoad(first);
            LOG.info("loaded WITH AETHER module {}:{}:{} to key {}", mavenCoords.groupId, mavenCoords.artifactId, mavenCoords.version, module.name);
            return metaData;
        }

        /**
         * Responsible for applying the logic of enabling a different parent
         * classloader while not enabling a different parent bean factory
         *
         * @param module
         * @param mavenCoords
         * @param jars
         * @return the class loader and the parent module
         * @throws ErrorLoadingArtifactException
         */
        class ParentContext extends Two<ClassLoader, ApplicationContext> {

            public ParentContext(ClassLoader classLoader, ApplicationContext applicationContext) {
                super(classLoader, applicationContext);
            }

            public ClassLoader getClassLoader() {
                return get1();
            }

            public ApplicationContext getApplicationContext() {
                return get2();
            }
        }

        private ParentContext getParentContext(ModuleInfo module, MavenCoords mavenCoords, URL[] jars) throws ErrorLoadingArtifactException {
            ClassLoader classLoader = getClassLoader(mavenCoords.artifactId, module, jars);

            String parentName = module.parentModule();
            ApplicationContext parentContext = parentName != null ? registry.getContext(parentName) : context;
            if (parentName != null && parentContext == null) {
                throw new ErrorLoadingArtifactException("module {} required parent module {} but is not present.", module.name(), parentName);
            }
            return new ParentContext(classLoader, parentContext);

        }

        class ModuleLoad extends Two<String, Module> {

            public ModuleLoad(String one, Module two) {
                super(one, two);
            }

            public String getModuleClass() {
                return get1();
            }

            public Module getModuleAnnotation() {
                return get2();
            }
        }

        class ModuleInfo {
            private Integer order;
            private String name;
            private String parentModule;
            private String parentModuleClassesOnly;
            private String[] ringFencedFilters;
            private String moduleClass;

            public ModuleInfo(Properties properties) {
                Object object = properties.get(ModuleMetaData.RING_FENCE_CLASS_BLACK_LIST);
                if (object != null) {
                    this.ringFencedFilters = Splitter.on(";").splitToList(object.toString()).toArray(new String[0]);
                } else {
                    this.ringFencedFilters = new String[] {};
                }
                String orderProperty = properties.getProperty(ModuleMetaData.ORDER_INDEX);
                this.order = orderProperty != null ? Integer.parseInt(orderProperty) : null;

                this.moduleClass = properties.getProperty(ModuleMetaData.MODULE_CLASS);
                this.parentModule = properties.getProperty(ModuleMetaData.PARENT_MODULE);
                this.name = properties.getProperty(ModuleMetaData.MODULE_NAME);
            }

            public ModuleInfo(Module module, Class<? extends AshesModule> moduleClass) {
                this.order = module.order() == Integer.MIN_VALUE ? null : module.order();
                this.name = module.name().equals("") ? moduleClass.getSimpleName() : module.name();
                this.parentModule = StringUtils.stripToNull(module.parentModule());
                if (parentModule == null) {
                    this.parentModuleClassesOnly = StringUtils.stripToNull(module.parentModuleClassesOnly());
                }
                this.ringFencedFilters = module.ringFenceClassBlackListRegex();
                this.moduleClass = moduleClass.getName();
            }

            Integer order() {
                return order;
            }

            String name() {
                return name;
            }

            String parentModule() {
                return parentModule;
            }

            String[] ringFenceClassBlackListRegex() {
                return ringFencedFilters;
            }

            public String moduleClass() {
                return moduleClass;
            }

            @Override
            public String toString() {
                return "ModuleInfo [name=" + name + ", parentModule=" + parentModule + ", order=" + order + ", ringFencedFilters=" + Arrays.toString(
                        ringFencedFilters)
                        + ", moduleClass=" + moduleClass + "]";
            }

            public String parentModuleClassesOnly() {
                return parentModuleClassesOnly;
            }

        }

        public ModuleInfo getModuleAnnotation(MavenCoords t, URL mainJar, URL[] jars) throws IOException, ClassNotFoundException,
                                                                                             ErrorLoadingArtifactException {
            ClassLoader loader = getClassLoader("x", new ModuleInfo(new Properties()), jars);

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
                Optional<URL> foundUrl = Collections.list(resourceAsStream).stream().filter(u -> u.toString().contains(t.artifactId)).findFirst();

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

        }

        private ClassLoader getClassLoader(String artifactId, ModuleInfo moduleInfo, URL[] urls) throws ErrorLoadingArtifactException {
            String parentName = moduleInfo.parentModule();
            if (parentName == null) {
                parentName = StringUtils.stripToNull(moduleInfo.parentModuleClassesOnly());
            }

            if (parentName != null && registry.getClassLoader(parentName) == null) {
                throw new ErrorLoadingArtifactException("no parent class loader found for parent {}", parentName);
            }
            LOG.debug("creating class loader for {}, artifactId:{} with parent ='{}' ringFenceBlackList:{} and jar list {}", moduleInfo.name, artifactId,
                      moduleInfo.parentModule, Arrays.asList(moduleInfo.ringFencedFilters), Arrays.asList(urls));
            ClassLoader classLoader = loader.getClassLoader(urls, parentName != null ? registry.getClassLoader(parentName) : this.getClass().getClassLoader(),
                                                            artifactId, moduleInfo.ringFenceClassBlackListRegex());

            return classLoader;
        }

        public URL[] getJars(DependencyNode node) {
            PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
            node.accept(nlg);
            URL[] urls = nlg.getFiles().stream().map(file -> {
                try {
                    return new URL("file", null, file.getAbsolutePath());
                } catch (MalformedURLException e) {
                    LOG.info("error creating url file {}", file, e);
                    return null;
                }
            }).filter(Objects::nonNull).toArray(URL[]::new);
            return urls;
        }

        Set<MavenCoords> snapshotVersionsLoaded = new HashSet();

        RepositorySystemSession snapshotSession;
        RepositorySystemSession stabilisedSession;

        private RepositorySystemSession newSession(RepositorySystem system, MavenCoords mavenCoords) {
            if (mavenCoords.isSnapshot()) {
                // then we check if the version is in the loaded snapshots - if it we need to create a new session, otherwise we can reuse thes napshot session
                if (snapshotVersionsLoaded.contains(mavenCoords)) {
                    snapshotVersionsLoaded.add(mavenCoords);
                    LOG.info("request for session for {}, already loaded so returning new session", mavenCoords);
                    this.snapshotSession = getSession(system);
                    snapshotVersionsLoaded.clear();
                    return this.snapshotSession;
                } else {
                    LOG.info("request for session for {}, not yet loaded so using exsiting session", mavenCoords);
                    return this.snapshotSession = snapshotSession == null ? getSession(system) : snapshotSession;
                }
            } else {
                LOG.info("request for stabilised {}, using existing session", mavenCoords);
                return this.stabilisedSession = stabilisedSession == null ? getSession(system) : stabilisedSession;
            }
        }

        private RepositorySystemSession getSession(RepositorySystem system) {
            DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
            final String repository = buildPath(File.separator, repositoryBase, ".m2", "repository");
            LOG.info("Adding Local Maven Repo with path {}", repository);
            LocalRepository localRepo = new LocalRepository(repository);
            session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
            return session;
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

    }
}
