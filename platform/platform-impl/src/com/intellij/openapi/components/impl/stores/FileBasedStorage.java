/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.components.impl.stores;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.StateStorageException;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.StreamProvider;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.tracker.VirtualFileTracker;
import com.intellij.util.io.fs.IFile;
import com.intellij.util.messages.MessageBus;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.util.io.fs.FileSystem.FILE_SYSTEM;

public class FileBasedStorage extends XmlElementStorage {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.FileBasedStorage");

  private final String myFilePath;
  private final IFile myFile;
  protected final String myRootElementName;

  private static boolean myConfigDirectoryRefreshed = false;

  public FileBasedStorage(@Nullable TrackingPathMacroSubstitutor pathMacroManager,
                          StreamProvider streamProvider,
                          final String filePath,
                          final String fileSpec,
                          String rootElementName,
                          @NotNull Disposable parentDisposable,
                          PicoContainer picoContainer,
                          ComponentRoamingManager componentRoamingManager, ComponentVersionProvider localComponentVersionProvider) {
    super(pathMacroManager, parentDisposable, rootElementName, streamProvider,  fileSpec, componentRoamingManager, localComponentVersionProvider);
    Application app = ApplicationManager.getApplication();

    if (!myConfigDirectoryRefreshed && (app.isUnitTestMode() || app.isDispatchThread())) {
      try {
        syncRefreshPathRecursively(PathManager.getConfigPath(true), "componentVersions");
      }
      finally {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        myConfigDirectoryRefreshed = true;
      }
    }

    myRootElementName = rootElementName;
    myFilePath = filePath;
    myFile = FILE_SYSTEM.createFile(myFilePath);

    VirtualFileTracker virtualFileTracker = (VirtualFileTracker)picoContainer.getComponentInstanceOfType(VirtualFileTracker.class);
    MessageBus messageBus = (MessageBus)picoContainer.getComponentInstanceOfType(MessageBus.class);


    if (virtualFileTracker != null && messageBus != null) {
      final String path = myFile.getAbsolutePath();
      final String fileUrl = LocalFileSystem.PROTOCOL_PREFIX + path.replace(File.separatorChar, '/');

      final Listener listener = messageBus.syncPublisher(STORAGE_TOPIC);
      virtualFileTracker.addTracker(fileUrl, new VirtualFileAdapter() {
        public void contentsChanged(final VirtualFileEvent event) {
          if (!isDisposed()) listener.storageFileChanged(event, FileBasedStorage.this);
        }
      }, false, this);
    }
  }

  public static void syncRefreshPathRecursively(String configDirectoryPath, @Nullable final String excludeDir) {
    VirtualFile configDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(configDirectoryPath);

    if (configDir != null) {
      requestAllChildren(configDir, excludeDir);

      if (configDir instanceof NewVirtualFile) {
        ((NewVirtualFile)configDir).markDirtyRecursively();
      }

      configDir.refresh(false, true);
    }
  }

  private static void requestAllChildren(final VirtualFile configDir, @Nullable final String excludeDir) {
    if (excludeDir == null || !excludeDir.equals(configDir.getName())) {
      for (VirtualFile file : configDir.getChildren()) {
        requestAllChildren(file, excludeDir);
      }
    }
  }

  protected MySaveSession createSaveSession(final MyExternalizationSession externalizationSession) {
    return new FileSaveSession(externalizationSession);
  }

  public void resetProviderCache() {
    myProviderUpToDateHash = null;
    myProviderVersions = null;
  }


  protected class FileSaveSession extends MySaveSession {
    protected FileSaveSession(MyExternalizationSession externalizationSession) {
      super(externalizationSession);
    }

    @Override
    protected boolean physicalContentNeedsSave() {
      if (!myFile.exists()) return !myStorageData.isEmpty();
      return !StorageUtil.contentEquals(getDocumentToSave(), myFile);
    }

    @Override
    protected Integer calcHash() {
      int hash = myStorageData.getHash();

      if (myPathMacroSubstitutor != null) {
        hash = 31*hash + myPathMacroSubstitutor.hashCode();
      }
      return hash;
    }

    protected void doSave() throws StateStorageException {
      if (!myBlockSavingTheContent) {
        if (ApplicationManager.getApplication().isUnitTestMode() && myFile != null && StringUtil.startsWithChar(myFile.getPath(), '$')) {
          throw new StateStorageException("It seems like some macros were not expanded for path: " + myFile.getPath());
        }

        StorageUtil.save(myFile, getDocumentToSave(), this);
      }
    }

    public Collection<IFile> getStorageFilesToSave() throws StateStorageException {
      boolean needsSave = needsSave();
      if (needsSave) {
        if (LOG.isDebugEnabled()) {
          LOG.info("File " + myFileSpec + " needs save; hash=" + myUpToDateHash + "; currentHash=" + calcHash() + "; content needs save=" + physicalContentNeedsSave());
        }
        return getAllStorageFiles();
      }
      else {
        return Collections.emptyList();
      }
    }

    public List<IFile> getAllStorageFiles() {
      return Collections.singletonList(myFile);
    }

  }

  protected void loadState(final StorageData result, final Element element) throws StateStorageException {
    ((FileStorageData)result).myFileName = myFile.getAbsolutePath();
    ((FileStorageData)result).myFilePath = myFile.getAbsolutePath();
    super.loadState(result, element);
  }

  @NotNull
  protected StorageData createStorageData() {
    return new FileStorageData(myRootElementName);
  }

  public static class FileStorageData extends StorageData {
    String myFilePath;
    String myFileName;

    public FileStorageData(final String rootElementName) {
      super(rootElementName);
    }

    protected FileStorageData(FileStorageData storageData) {
      super(storageData);
      myFileName = storageData.myFileName;
      myFilePath = storageData.myFilePath;
    }

    public StorageData clone() {
      return new FileStorageData(this);
    }

    public String toString() {
      return "FileStorageData[" + myFileName + "]";
    }
  }

  @Nullable
  public VirtualFile getVirtualFile() {
    return StorageUtil.getVirtualFile(myFile);
  }

  public File getFile() {
    return new File(myFile.getPath());
  }

  @Nullable
  protected Document loadDocument() throws StateStorageException {
    myBlockSavingTheContent = false;
    try {
      VirtualFile file = getVirtualFile();
      if (file == null || file.isDirectory()) {
        LOG.info("Document was not loaded for " + myFileSpec + " file is " + (file == null ? "null" : "directory"));        
        return null;
      }
      else if (file.getLength() == 0) {
        return processReadException(null);
      }
      else {
        return loadDocumentImpl(file);
      }
    }
    catch (final JDOMException e) {
      return processReadException(e);
    }
    catch (final IOException e) {
      return processReadException(e);
    }
  }

  @Nullable
  private Document processReadException(@Nullable final Exception e) {
    boolean contentTruncated = e == null;
    myBlockSavingTheContent = isProjectOrModuleFile() && !contentTruncated;
    if (!ApplicationManager.getApplication().isUnitTestMode() && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      if (e != null) {
        LOG.info(e);
      }
      final String message = "Cannot load settings from file '" + myFile.getPath() + "': " + (e == null ? "content truncated" : e.getLocalizedMessage()) + "\n" +
                             getInvalidContentMessage(contentTruncated);
      Notifications.Bus.notify(
        new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, "Load Settings", message, NotificationType.WARNING));
    }

    return null;
  }

  private boolean isProjectOrModuleFile() {
    return myIsProjectSettings || myFileSpec.equals("$MODULE_FILE$");
  }

  private String getInvalidContentMessage(boolean contentTruncated) {
    return isProjectOrModuleFile() && !contentTruncated ? "Please correct the file content" : "File content will be recreated";
  }

  private static Document loadDocumentImpl(final VirtualFile file) throws IOException, JDOMException {
    InputStream stream = file.getInputStream();
    try {
      return JDOMUtil.loadDocument(stream);
    }
    finally {
      stream.close();
    }
  }

  public String getFileName() {
    return myFile.getName();
  }

  public String getFilePath() {
    return myFilePath;
  }

  public void setDefaultState(final Element element) {
    element.setName(myRootElementName);
    super.setDefaultState(element);
  }

  protected boolean physicalContentNeedsSave(final Document doc) {
    if (!myFile.exists()) return true;
    return !StorageUtil.contentEquals(doc, myFile);
  }

  @Nullable
  public File updateFileExternallyFromStreamProviders() throws IOException {
    StorageData loadedData = loadData(true, myListener);
    Document document = getDocument(loadedData);
    if (physicalContentNeedsSave(document)) {
      File file = new File(myFile.getAbsolutePath());
      JDOMUtil.writeDocument(document, file, "\n");
      return file;

    }

    return null;
  }
}
