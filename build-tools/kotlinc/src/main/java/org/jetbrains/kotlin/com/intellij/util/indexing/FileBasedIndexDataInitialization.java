//package org.jetbrains.kotlin.com.intellij.util.indexing;
//
//import org.jetbrains.annotations.NotNull;
//import org.jetbrains.kotlin.com.intellij.diagnostic.Activity;
//import org.jetbrains.kotlin.com.intellij.diagnostic.StartUpMeasurer;
//import org.jetbrains.kotlin.com.intellij.openapi.Disposable;
//import org.jetbrains.kotlin.com.intellij.openapi.application.ApplicationListener;
//import org.jetbrains.kotlin.com.intellij.openapi.application.ApplicationManager;
//import org.jetbrains.kotlin.com.intellij.openapi.application.PathManager;
//import org.jetbrains.kotlin.com.intellij.openapi.diagnostic.Logger;
//import org.jetbrains.kotlin.com.intellij.openapi.extensions.impl.ExtensionPointImpl;
//import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer;
//import org.jetbrains.kotlin.com.intellij.openapi.util.io.FileUtil;
//import org.jetbrains.kotlin.com.intellij.openapi.util.registry.Registry;
//import org.jetbrains.kotlin.com.intellij.openapi.vfs.newvfs.ManagingFS;
//import org.jetbrains.kotlin.com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
//import org.jetbrains.kotlin.com.intellij.psi.search.FilenameIndex;
//import org.jetbrains.kotlin.com.intellij.util.ThrowableRunnable;
//import org.jetbrains.kotlin.com.intellij.util.containers.ContainerUtil;
//import org.jetbrains.kotlin.com.intellij.util.containers.IntSetUtils;
//import org.jetbrains.kotlin.com.intellij.util.io.DataOutputStream;
//import org.jetbrains.kotlin.com.intellij.util.io.IOUtil;
//import org.jetbrains.kotlin.it.unimi.dsi.fastutil.ints.IntOpenHashSet;
//import org.jetbrains.kotlin.it.unimi.dsi.fastutil.ints.IntSet;
//
//import java.io.BufferedInputStream;
//import java.io.BufferedOutputStream;
//import java.io.DataInputStream;
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.util.*;
//
//final class FileBasedIndexDataInitialization extends IndexDataInitializer<IndexConfiguration> {
//  private static final Logger LOG = Logger.getInstance(FileBasedIndexDataInitialization.class);
//
//  private boolean myCurrentVersionCorrupted;
//
//  @NotNull
//  private final FileBasedIndexImpl myFileBasedIndex;
//  @NotNull
//  private final RegisteredIndexes myRegisteredIndexes;
//  @NotNull
//  private final IntSet myStaleIds = IntSetUtils.synchronize(new IntOpenHashSet());
//  @NotNull
//  private final IntSet myDirtyFileIds = IntSetUtils.synchronize(new IntOpenHashSet());
//  @NotNull
//  private final IndexVersionRegistrationSink myRegistrationResultSink = new IndexVersionRegistrationSink();
//  @NotNull
//  private final IndexConfiguration myState = new IndexConfiguration();
//
//  FileBasedIndexDataInitialization(@NotNull FileBasedIndexImpl index, @NotNull RegisteredIndexes registeredIndexes) {
//    myFileBasedIndex = index;
//    myRegisteredIndexes = registeredIndexes;
//  }
//
//  private @NotNull Collection<ThrowableRunnable<?>> initAssociatedDataForExtensions() {
//    Activity activity = StartUpMeasurer.startActivity("file index extensions iteration");
//    ExtensionPointImpl<FileBasedIndexExtension<?, ?>> extPoint =
//      (ExtensionPointImpl<FileBasedIndexExtension<?, ?>>)FileBasedIndexExtension.EXTENSION_POINT_NAME.getPoint();
//    Iterator<FileBasedIndexExtension<?, ?>> extensions = extPoint.iterator();
//    List<ThrowableRunnable<?>> tasks = new ArrayList<>(extPoint.size());
//
//    myDirtyFileIds.addAll(PersistentDirtyFilesQueue.INSTANCE.readIndexingQueue());
//    // todo: init contentless indices first ?
//    while (extensions.hasNext()) {
//      FileBasedIndexExtension<?, ?> extension = extensions.next();
//      if (extension == null) {
//        break;
//      }
//      RebuildStatus.registerIndex(extension.getName());
//
//      myRegisteredIndexes.registerIndexExtension(extension);
//
//      tasks.add(() -> {
//        if (IOUtil.isSharedCachesEnabled()) {
//          IOUtil.OVERRIDE_BYTE_BUFFERS_USE_NATIVE_BYTE_ORDER_PROP.set(false);
//        }
//        try {
//          FileBasedIndexImpl.registerIndexer(extension,
//                                             myState,
//                                             myRegistrationResultSink,
//                                             myStaleIds,
//                                             myDirtyFileIds);
//        }
//        catch (IOException io) {
//          throw io;
//        }
//        catch (Throwable t) {
//          handleComponentError(t, extension.getClass().getName(), null);
//        }
//        finally {
//          IOUtil.OVERRIDE_BYTE_BUFFERS_USE_NATIVE_BYTE_ORDER_PROP.remove();
//        }
//      });
//    }
//
//    myRegisteredIndexes.extensionsDataWasLoaded();
//    activity.end();
//
//    return tasks;
//  }
//
//  @Override
//  protected @NotNull Collection<ThrowableRunnable<?>> prepareTasks() {
//    // PersistentFS lifecycle should contain FileBasedIndex lifecycle, so,
//    // 1) we call for it's instance before index creation to make sure it's initialized
//    // 2) we dispose FileBasedIndex before PersistentFS disposing
//    PersistentFSImpl fs = (PersistentFSImpl) ManagingFS.getInstance();
//    FileBasedIndexImpl fileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
//    // anonymous class is required to make sure the new instance is created
//    Disposable disposable = new Disposable() {
//      @Override
//      public void dispose() {
//        new FileBasedIndexImpl.MyShutDownTask().run();
//      }
//    };
//    ApplicationManager.getApplication().addApplicationListener(new MyApplicationListener(fileBasedIndex), disposable);
//    Disposer.register(fs, disposable);
//    myFileBasedIndex.setUpShutDownTask();
//
//    Collection<ThrowableRunnable<?>> tasks = initAssociatedDataForExtensions();
//
//    PersistentIndicesConfiguration.loadConfiguration();
//
//    myCurrentVersionCorrupted = CorruptionMarker.requireInvalidation();
//    boolean storageLayoutChanged = FileBasedIndexLayoutSettings.INSTANCE.loadUsedLayout();
//    for (FileBasedIndexInfrastructureExtension ex : FileBasedIndexInfrastructureExtension.EP_NAME.getExtensions()) {
//      FileBasedIndexInfrastructureExtension.InitializationResult result = ex.initialize(DefaultIndexStorageLayout.getUsedLayoutId());
//      myCurrentVersionCorrupted = myCurrentVersionCorrupted ||
//                                result == FileBasedIndexInfrastructureExtension.InitializationResult.INDEX_REBUILD_REQUIRED;
//    }
//    myCurrentVersionCorrupted = myCurrentVersionCorrupted || storageLayoutChanged;
//
//    if (myCurrentVersionCorrupted) {
//      CorruptionMarker.dropIndexes();
//    }
//
//    return tasks;
//  }
//
//  @Override
//  protected @NotNull IndexConfiguration finish() {
//    try {
//      myState.finalizeFileTypeMappingForIndices();
//
//      showChangedIndexesNotification();
//
//      myRegistrationResultSink.logChangedAndFullyBuiltIndices(
//        FileBasedIndexImpl.LOG,
//        "Indexes to be rebuilt after version change:",
//        myCurrentVersionCorrupted ? "Indexes to be rebuilt after corruption:" : "Indices to be built:"
//      );
//
//      myState.freeze();
//      myRegisteredIndexes.setState(myState); // memory barrier
//      // check if rebuild was requested for any index during registration
//      for (ID<?, ?> indexId : myState.getIndexIDs()) {
//        try {
//          RebuildStatus.clearIndexIfNecessary(indexId, () -> myFileBasedIndex.clearIndex(indexId));
//        }
//        catch (StorageException e) {
//          myFileBasedIndex.requestRebuild(indexId);
//          FileBasedIndexImpl.LOG.error(e);
//        }
//      }
//
//      return myState;
//    }
//    finally {
//      //CorruptionMarker.markIndexesAsDirty();
//      FileBasedIndexImpl.setupWritingIndexValuesSeparatedFromCounting();
//      FileBasedIndexImpl.setupWritingIndexValuesSeparatedFromCountingForContentIndependentIndexes();
//      myFileBasedIndex.addStaleIds(myStaleIds);
//      myFileBasedIndex.addStaleIds(myDirtyFileIds);
//      myFileBasedIndex.setUpFlusher();
//      myFileBasedIndex.setUpHealthCheck();
//      myRegisteredIndexes.ensureLoadedIndexesUpToDate();
//      myRegisteredIndexes.markInitialized();  // this will ensure that all changes to component's state will be visible to other threads
//      saveRegisteredIndicesAndDropUnregisteredOnes(myState.getIndexIDs());
//      PersistentDirtyFilesQueue.INSTANCE.removeCurrentFile();
//    }
//  }
//
//  private void showChangedIndexesNotification() {
//    if (ApplicationManager.getApplication().isHeadlessEnvironment() || !Registry.is("ide.showIndexRebuildMessage", false)) {
//      return;
//    }
//
//    String rebuildNotification;
//    if (myCurrentVersionCorrupted) {
//      rebuildNotification = "Index corrupted";
//    }
//    else if (myRegistrationResultSink.hasChangedIndexes()) {
//      rebuildNotification =  "Indices changed: " + myRegistrationResultSink.changedIndices();
//    }
//    else {
//      return;
//    }
//
////    NotificationGroupManager.getInstance().getNotificationGroup("IDE Caches")
////      .createNotification(IndexingBundle.message("index.rebuild.notification.title"), rebuildNotification, NotificationType.INFORMATION)
////      .notify(null);
//  }
//
//  @NotNull
//  @Override
//  protected String getInitializationFinishedMessage(@NotNull IndexConfiguration initializationResult) {
//    return "Initialized indexes: " + initializationResult.getIndexIDs() + ".";
//  }
//
//  private static void saveRegisteredIndicesAndDropUnregisteredOnes(@NotNull Collection<? extends ID<?, ?>> ids) {
//    if (ApplicationManager.getApplication().isDisposed() || !IndexInfrastructure.hasIndices()) {
//      return;
//    }
//
//    final Path registeredIndicesFile = PathManager.getIndexRoot().toPath().resolve("registered");
//    final Set<String> indicesToDrop = new HashSet<>();
//    boolean exceptionThrown = false;
//    if (Files.exists(registeredIndicesFile)) {
//      try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(registeredIndicesFile)))) {
//        int size = in.readInt();
//        for (int idx = 0; idx < size; idx++) {
//          indicesToDrop.add(IOUtil.readString(in));
//        }
//      }
//      catch (Throwable e) {
//        // workaround for IDEA-194253
//        LOG.info(e);
//        exceptionThrown = true;
//        ids.stream().map(ID::getName).forEach(indicesToDrop::add);
//      }
//    }
//
//    boolean dropFilenameIndex = FileBasedIndexExtension.USE_VFS_FOR_FILENAME_INDEX &&
//                                indicesToDrop.contains(FilenameIndex.NAME.getName());
//    if (!exceptionThrown) {
//      for (ID<?, ?> key : ids) {
//          if (dropFilenameIndex && key == FilenameIndex.NAME) {
//              continue;
//          }
//        indicesToDrop.remove(key.getName());
//      }
//    }
//
//    if (!indicesToDrop.isEmpty()) {
//      Collection<String> filtered = !dropFilenameIndex ? indicesToDrop :
//                                    ContainerUtil.filter(indicesToDrop, o -> !FilenameIndex.NAME.getName().equals(o));
//        if (!filtered.isEmpty()) {
//            LOG.info("Dropping indices:" + String.join(",", filtered));
//        }
//      for (String s : indicesToDrop) {
//        try {
//          FileUtil.deleteWithRenaming(IndexInfrastructure.getFileBasedIndexRootDir(s).toFile());
//        }
//        catch (IOException e) {
//          LOG.warn(e);
//        }
//      }
//    }
//
//    try {
//      Files.createDirectories(registeredIndicesFile.getParent());
//      try (DataOutputStream os = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(registeredIndicesFile)))) {
//        os.writeInt(ids.size());
//        for (ID<?, ?> id : ids) {
//          IOUtil.writeString(id.getName(), os);
//        }
//      }
//    }
//    catch (IOException e) {
//      LOG.warn(e);
//    }
//  }
//
//  private static final class MyApplicationListener implements ApplicationListener {
//    private final FileBasedIndexImpl myFileBasedIndex;
//
//    MyApplicationListener(FileBasedIndexImpl fileBasedIndex) {
//      myFileBasedIndex = fileBasedIndex;
//    }
//
//    @Override
//    public void writeActionStarted(@NotNull Object action) {
//      myFileBasedIndex.clearUpToDateIndexesForUnsavedOrTransactedDocs();
//    }
//  }
//}