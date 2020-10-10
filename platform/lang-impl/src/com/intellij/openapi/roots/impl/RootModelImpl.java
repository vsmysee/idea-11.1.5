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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author dsl
 */
public class RootModelImpl implements ModifiableRootModel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.RootModelImpl");

  private final Set<ContentEntry> myContent = new TreeSet<ContentEntry>(ContentComparator.INSTANCE);

  private final List<OrderEntry> myOrderEntries = new Order();
  // cleared by myOrderEntries modification, see Order
  @Nullable private OrderEntry[] myCachedOrderEntries;

  @NotNull private final ModuleLibraryTable myModuleLibraryTable;
  final ModuleRootManagerImpl myModuleRootManager;
  private boolean myWritable;
  private final VirtualFilePointerManager myFilePointerManager;

  @Nullable VirtualFilePointer myExplodedDirectoryPointer;
  @Nullable private String myExplodedDirectory;
  private boolean myExcludeExploded;

  @NonNls private static final String EXPLODED_TAG = "exploded";
  @NonNls private static final String ATTRIBUTE_URL = "url";
  @NonNls private static final String URL_ATTR = ATTRIBUTE_URL;

  @NonNls private static final String EXCLUDE_EXPLODED_TAG = "exclude-exploded";

  private boolean myDisposed = false;

  private final Set<ModuleExtension> myExtensions = new TreeSet<ModuleExtension>();

  private final Map<PersistentOrderRootType, VirtualFilePointerContainer> myOrderRootPointerContainers = new HashMap<PersistentOrderRootType, VirtualFilePointerContainer>();

  private final RootConfigurationAccessor myConfigurationAccessor;

  @NonNls private static final String ROOT_ELEMENT = "root";
  private final ProjectRootManagerImpl myProjectRootManager;
  // have to register all child disposables using this fake object since all clients call just ModifiableModel.dispose()
  private final Disposable myDisposable = Disposer.newDisposable();

  RootModelImpl(@NotNull ModuleRootManagerImpl moduleRootManager, ProjectRootManagerImpl projectRootManager, VirtualFilePointerManager filePointerManager) {
    myModuleRootManager = moduleRootManager;
    myProjectRootManager = projectRootManager;
    myFilePointerManager = filePointerManager;

    myWritable = false;

    addSourceOrderEntries();
    myModuleLibraryTable = new ModuleLibraryTable(this, myProjectRootManager);

    for (ModuleExtension extension : Extensions.getExtensions(ModuleExtension.EP_NAME, moduleRootManager.getModule())) {
      ModuleExtension model = extension.getModifiableModel(false);
      Disposer.register(myDisposable, model);
      myExtensions.add(model);
    }
    myConfigurationAccessor = new RootConfigurationAccessor();
  }

  private void addSourceOrderEntries() {
    myOrderEntries.add(new ModuleSourceOrderEntryImpl(this));
  }

  RootModelImpl(@NotNull Element element,
                @NotNull ModuleRootManagerImpl moduleRootManager,
                ProjectRootManagerImpl projectRootManager,
                VirtualFilePointerManager filePointerManager) throws InvalidDataException {
    myProjectRootManager = projectRootManager;
    myFilePointerManager = filePointerManager;
    myModuleRootManager = moduleRootManager;

    myModuleLibraryTable = new ModuleLibraryTable(this, myProjectRootManager);

    final List contentChildren = element.getChildren(ContentEntryImpl.ELEMENT_NAME);
    for (Object aContentChildren : contentChildren) {
      Element child = (Element)aContentChildren;
      ContentEntryImpl contentEntry = new ContentEntryImpl(child, this);
      myContent.add(contentEntry);
    }

    final List orderElements = element.getChildren(OrderEntryFactory.ORDER_ENTRY_ELEMENT_NAME);
    boolean moduleSourceAdded = false;
    for (Object orderElement : orderElements) {
      Element child = (Element)orderElement;
      final OrderEntry orderEntry = OrderEntryFactory.createOrderEntryByElement(child, this, myProjectRootManager);
      if (orderEntry instanceof ModuleSourceOrderEntry) {
        if (moduleSourceAdded) continue;
        moduleSourceAdded = true;
      }
      myOrderEntries.add(orderEntry);
    }

    if (!moduleSourceAdded) {
      myOrderEntries.add(new ModuleSourceOrderEntryImpl(this));
    }

    myExcludeExploded = element.getChild(EXCLUDE_EXPLODED_TAG) != null;

    myExplodedDirectoryPointer = getOutputPathValue(element, EXPLODED_TAG, true);
    myExplodedDirectory = getOutputPathValue(element, EXCLUDE_EXPLODED_TAG);

    myWritable = true;

    for(PersistentOrderRootType orderRootType: OrderRootType.getAllPersistentTypes()) {
      String paths = orderRootType.getModulePathsName();
      if (paths != null) {
        final Element pathsElement = element.getChild(paths);
        if (pathsElement != null) {
          VirtualFilePointerContainer container = myFilePointerManager.createContainer(myDisposable, null);
          myOrderRootPointerContainers.put(orderRootType, container);
          container.readExternal(pathsElement, ROOT_ELEMENT);
        }
      }
    }

    RootModelImpl originalRootModel = moduleRootManager.getRootModel();
    for (ModuleExtension extension : originalRootModel.myExtensions) {
      ModuleExtension model = extension.getModifiableModel(false);
      model.readExternal(element);
      Disposer.register(myDisposable, model);
      myExtensions.add(model);
    }
    myConfigurationAccessor = new RootConfigurationAccessor();
  }

  @Override
  public boolean isWritable() {
    return myWritable;
  }

  public RootConfigurationAccessor getConfigurationAccessor() {
    return myConfigurationAccessor;
  }

  //creates modifiable model
  RootModelImpl(@NotNull RootModelImpl rootModel,
                ModuleRootManagerImpl moduleRootManager,
                final boolean writable,
                final RootConfigurationAccessor rootConfigurationAccessor,
                @NotNull VirtualFilePointerManager filePointerManager,
                ProjectRootManagerImpl projectRootManager) {
    myFilePointerManager = filePointerManager;
    myModuleRootManager = moduleRootManager;
    myProjectRootManager = projectRootManager;

    myModuleLibraryTable = new ModuleLibraryTable(this, myProjectRootManager);

    myWritable = writable;
    myConfigurationAccessor = rootConfigurationAccessor;

    setExplodedFrom(rootModel, filePointerManager);

    final Set<ContentEntry> thatContent = rootModel.myContent;
    for (ContentEntry contentEntry : thatContent) {
      if (contentEntry instanceof ClonableContentEntry) {
        myContent.add(((ClonableContentEntry)contentEntry).cloneEntry(this));
      }
    }

    setOrderEntriesFrom(rootModel);
    copyContainersFrom(rootModel);

    for (ModuleExtension extension : rootModel.myExtensions) {
      ModuleExtension model = extension.getModifiableModel(writable);
      Disposer.register(myDisposable, model);
      myExtensions.add(model);
    }
  }

  private void setExplodedFrom(@NotNull RootModelImpl rootModel, @NotNull VirtualFilePointerManager filePointerManager) {
    if (rootModel.myExplodedDirectoryPointer != null) {
      myExplodedDirectoryPointer = filePointerManager.duplicate(rootModel.myExplodedDirectoryPointer, getModule(), null);
    }
    myExplodedDirectory = rootModel.myExplodedDirectory;

    myExcludeExploded = rootModel.myExcludeExploded;
  }

  private void copyContainersFrom(@NotNull RootModelImpl rootModel) {
    myOrderRootPointerContainers.clear();
    for(PersistentOrderRootType orderRootType: OrderRootType.getAllPersistentTypes()) {
      final VirtualFilePointerContainer otherContainer = rootModel.getOrderRootContainer(orderRootType);
      if (otherContainer != null) {
        myOrderRootPointerContainers.put(orderRootType, otherContainer.clone(myDisposable, null));
      }
    }
  }

  private void setOrderEntriesFrom(@NotNull RootModelImpl rootModel) {
    myOrderEntries.clear();
    for (OrderEntry orderEntry : rootModel.myOrderEntries) {
      if (orderEntry instanceof ClonableOrderEntry) {
        myOrderEntries.add(((ClonableOrderEntry)orderEntry).cloneEntry(this, myProjectRootManager, myFilePointerManager));
      }
    }
  }

  @Nullable
  private VirtualFilePointerContainer getOrderRootContainer(PersistentOrderRootType orderRootType) {
    return myOrderRootPointerContainers.get(orderRootType);
  }

  @Override
  @NotNull
  public VirtualFile[] getOrderedRoots(OrderRootType type) {
    final ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();

    for (OrderEntry orderEntry : getOrderEntries()) {
      ContainerUtil.addAll(result, orderEntry.getFiles(type));
    }
    return ContainerUtil.toArray(result, new VirtualFile[result.size()]);
  }

  @Override
  @NotNull
  public String[] getOrderedRootUrls(OrderRootType type) {
    final ArrayList<String> result = new ArrayList<String>();

    for (OrderEntry orderEntry : getOrderEntries()) {
      ContainerUtil.addAll(result, orderEntry.getUrls(type));
    }
    return ContainerUtil.toArray(result, new String[result.size()]);
  }

  @Override
  @NotNull
  public VirtualFile[] getContentRoots() {
    final ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();

    for (ContentEntry contentEntry : myContent) {
      final VirtualFile file = contentEntry.getFile();
      if (file != null) {
        result.add(file);
      }
    }
    return ContainerUtil.toArray(result, new VirtualFile[result.size()]);
  }

  @Override
  @NotNull
  public String[] getContentRootUrls() {
    if (myContent.isEmpty()) return ArrayUtil.EMPTY_STRING_ARRAY;
    final ArrayList<String> result = new ArrayList<String>(myContent.size());

    for (ContentEntry contentEntry : myContent) {
      result.add(contentEntry.getUrl());
    }

    return ContainerUtil.toArray(result, new String[result.size()]);
  }

  @Override
  @NotNull
  public String[] getExcludeRootUrls() {
    final List<String> result = new SmartList<String>();
    for (ContentEntry contentEntry : myContent) {
      final ExcludeFolder[] excludeFolders = contentEntry.getExcludeFolders();
      for (ExcludeFolder excludeFolder : excludeFolders) {
        result.add(excludeFolder.getUrl());
      }
    }
    return ContainerUtil.toArray(result, new String[result.size()]);
  }

  @Override
  @NotNull
  public VirtualFile[] getExcludeRoots() {
    final List<VirtualFile> result = new SmartList<VirtualFile>();
    for (ContentEntry contentEntry : myContent) {
      final ExcludeFolder[] excludeFolders = contentEntry.getExcludeFolders();
      for (ExcludeFolder excludeFolder : excludeFolders) {
        final VirtualFile file = excludeFolder.getFile();
        if (file != null) {
          result.add(file);
        }
      }
    }
    return ContainerUtil.toArray(result, new VirtualFile[result.size()]);
  }

  @Override
  @NotNull
  public String[] getSourceRootUrls() {
    return getSourceRootUrls(true);
  }

  @Override
  @NotNull
  public String[] getSourceRootUrls(boolean includingTests) {
    List<String> result = new SmartList<String>();
    for (ContentEntry contentEntry : myContent) {
      final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
      for (SourceFolder sourceFolder : sourceFolders) {
        if (includingTests || !sourceFolder.isTestSource()) {
          result.add(sourceFolder.getUrl());
        }
      }
    }
    return ContainerUtil.toArray(result, new String[result.size()]);
  }

  @Override
  @NotNull
  public VirtualFile[] getSourceRoots() {
    return getSourceRoots(true);
  }

  @Override
  @NotNull
  public VirtualFile[] getSourceRoots(final boolean includingTests) {
    List<VirtualFile> result = new SmartList<VirtualFile>();
    for (ContentEntry contentEntry : myContent) {
      final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
      for (SourceFolder sourceFolder : sourceFolders) {
        final VirtualFile file = sourceFolder.getFile();
        if (file != null && (includingTests || !sourceFolder.isTestSource())) {
          result.add(file);
        }
      }
    }
    return ContainerUtil.toArray(result, new VirtualFile[result.size()]);
  }

  @Override
  public ContentEntry[] getContentEntries() {
    return myContent.toArray(new ContentEntry[myContent.size()]);
  }

  @Override
  @NotNull
  public OrderEntry[] getOrderEntries() {
    OrderEntry[] cachedOrderEntries = myCachedOrderEntries;
    if (cachedOrderEntries == null) {
      myCachedOrderEntries = cachedOrderEntries = myOrderEntries.toArray(new OrderEntry[myOrderEntries.size()]);
    }
    return cachedOrderEntries;
  }

  Iterator<OrderEntry> getOrderIterator() {
    return Collections.unmodifiableList(myOrderEntries).iterator();
  }

  @Override
  public void removeContentEntry(ContentEntry entry) {
    assertWritable();
    LOG.assertTrue(myContent.contains(entry));
    myContent.remove(entry);
  }

  @Override
  public void addOrderEntry(OrderEntry entry) {
    assertWritable();
    LOG.assertTrue(!myOrderEntries.contains(entry));
    myOrderEntries.add(entry);
  }

  @NotNull
  @Override
  public LibraryOrderEntry addLibraryEntry(Library library) {
    assertWritable();
    final LibraryOrderEntry libraryOrderEntry = new LibraryOrderEntryImpl(library, this, myProjectRootManager);
    assert libraryOrderEntry.isValid();
    myOrderEntries.add(libraryOrderEntry);
    return libraryOrderEntry;
  }

  @NotNull
  @Override
  public LibraryOrderEntry addInvalidLibrary(@NotNull String name, @NotNull String level) {
    assertWritable();
    final LibraryOrderEntry libraryOrderEntry = new LibraryOrderEntryImpl(name, level, this, myProjectRootManager);
    myOrderEntries.add(libraryOrderEntry);
    return libraryOrderEntry;
  }

  @NotNull
  @Override
  public ModuleOrderEntry addModuleOrderEntry(@NotNull Module module) {
    assertWritable();
    LOG.assertTrue(!module.equals(getModule()));
    LOG.assertTrue(Comparing.equal(myModuleRootManager.getModule().getProject(), module.getProject()));
    final ModuleOrderEntryImpl moduleOrderEntry = new ModuleOrderEntryImpl(module, this);
    myOrderEntries.add(moduleOrderEntry);
    return moduleOrderEntry;
  }

  @NotNull
  @Override
  public ModuleOrderEntry addInvalidModuleEntry(@NotNull String name) {
    assertWritable();
    LOG.assertTrue(!name.equals(getModule().getName()));
    final ModuleOrderEntryImpl moduleOrderEntry = new ModuleOrderEntryImpl(name, this);
    myOrderEntries.add(moduleOrderEntry);
    return moduleOrderEntry;
  }

  @Nullable
  @Override
  public LibraryOrderEntry findLibraryOrderEntry(@NotNull Library library) {
    for (OrderEntry orderEntry : getOrderEntries()) {
      if (orderEntry instanceof LibraryOrderEntry && library.equals(((LibraryOrderEntry)orderEntry).getLibrary())) {
        return (LibraryOrderEntry)orderEntry;
      }
    }
    return null;
  }

  @Override
  public void removeOrderEntry(OrderEntry entry) {
    assertWritable();
    removeOrderEntryInternal(entry);
  }

  private void removeOrderEntryInternal(OrderEntry entry) {
    LOG.assertTrue(myOrderEntries.contains(entry));
    myOrderEntries.remove(entry);
  }

  @Override
  public void rearrangeOrderEntries(@NotNull OrderEntry[] newEntries) {
    assertWritable();
    assertValidRearrangement(newEntries);
    myOrderEntries.clear();
    ContainerUtil.addAll(myOrderEntries, newEntries);
  }

  private void assertValidRearrangement(@NotNull OrderEntry[] newEntries) {
    String error = checkValidRearrangement(newEntries);
    LOG.assertTrue(error == null, error);
  }
  @Nullable
  private String checkValidRearrangement(@NotNull OrderEntry[] newEntries) {
    if (newEntries.length != myOrderEntries.size()) {
      return "Size mismatch: old size=" + myOrderEntries.size() + "; new size=" + newEntries.length;
    }
    Set<OrderEntry> set = new HashSet<OrderEntry>();
    for (OrderEntry newEntry : newEntries) {
      if (!myOrderEntries.contains(newEntry)) {
        return "Trying to add nonexisting order entry " + newEntry;
      }

      if (set.contains(newEntry)) {
        return "Trying to add duplicate order entry " + newEntry;
      }
      set.add(newEntry);
    }
    return null;
  }

  @Override
  public void clear() {
    final Sdk jdk = getSdk();
    myContent.clear();
    myOrderEntries.clear();
    setSdk(jdk);
    addSourceOrderEntries();
  }

  @Override
  public void commit() {
    myModuleRootManager.commitModel(this);
    myWritable = false;
  }

  public void docommit() {
    assert isWritable();

    if (!vptrEqual(myExplodedDirectoryPointer, getSourceModel().myExplodedDirectoryPointer)) {
      getSourceModel().setExplodedDirectory(getExplodedDirectoryUrl());
    }

    getSourceModel().myExcludeExploded = myExcludeExploded;

    if (areOrderEntriesChanged()) {
      getSourceModel().setOrderEntriesFrom(this);
    }

    if (areContentEntriesChanged()) {
      getSourceModel().myContent.clear();
      for (ContentEntry contentEntry : myContent) {
        getSourceModel().myContent.add(((ClonableContentEntry)contentEntry).cloneEntry(getSourceModel()));
      }
    }

    if (areOrderRootPointerContainersChanged()) {
      getSourceModel().copyContainersFrom(this);
    }

    getSourceModel().setExplodedFrom(this, myFilePointerManager);

    for (ModuleExtension extension : myExtensions) {
      if (extension.isChanged()) {
        extension.commit();
      }
    }
  }

  @Override
  @NotNull
  public LibraryTable getModuleLibraryTable() {
    return myModuleLibraryTable;
  }

  @Override
  public <R> R processOrder(RootPolicy<R> policy, R initialValue) {
    R result = initialValue;
    for (OrderEntry orderEntry : getOrderEntries()) {
      result = orderEntry.accept(policy, result);
    }
    return result;
  }

  @NotNull
  @Override
  public OrderEnumerator orderEntries() {
    return new ModuleOrderEnumerator(this, null);
  }

  @Override
  public Project getProject() {
    return myProjectRootManager.getProject();
  }

  @Override
  @NotNull
  public ContentEntry addContentEntry(@NotNull VirtualFile file) {
    return addContentEntry(new ContentEntryImpl(file, this));
  }

  @Override
  @NotNull
  public ContentEntry addContentEntry(@NotNull String url) {
    return addContentEntry(new ContentEntryImpl(url, this));
  }

  @Override
  public boolean isDisposed() {
    return myDisposed;
  }

  @NotNull
  private ContentEntry addContentEntry(@NotNull ContentEntry e) {
    if (myContent.contains(e)) {
      for (ContentEntry contentEntry : getContentEntries()) {
        if (ContentComparator.INSTANCE.compare(contentEntry, e) == 0) return contentEntry;
      }
    }
    myContent.add(e);
    return e;
  }

  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    for (ModuleExtension extension : myExtensions) {
      extension.writeExternal(element);
    }

    if (myExplodedDirectory != null) {
      final Element pathElement = new Element(EXPLODED_TAG);
      pathElement.setAttribute(URL_ATTR, myExplodedDirectory);
      element.addContent(pathElement);
    }

    if (myExcludeExploded) {
      element.addContent(new Element(EXCLUDE_EXPLODED_TAG));
    }

    for (ContentEntry contentEntry : myContent) {
      if (contentEntry instanceof ContentEntryImpl) {
        final Element subElement = new Element(ContentEntryImpl.ELEMENT_NAME);
        ((ContentEntryImpl)contentEntry).writeExternal(subElement);
        element.addContent(subElement);
      }
    }

    for (OrderEntry orderEntry : getOrderEntries()) {
      if (orderEntry instanceof WritableOrderEntry) {
        ((WritableOrderEntry)orderEntry).writeExternal(element);
      }
    }

    for(PersistentOrderRootType orderRootType: myOrderRootPointerContainers.keySet()) {
      VirtualFilePointerContainer container = myOrderRootPointerContainers.get(orderRootType);
      if (container != null && container.size() > 0) {
        final Element javaDocPaths = new Element(orderRootType.getModulePathsName());
        container.writeExternal(javaDocPaths, ROOT_ELEMENT);
        element.addContent(javaDocPaths);
      }
    }
  }

  @Override
  public void setSdk(@Nullable Sdk jdk) {
    assertWritable();
    final JdkOrderEntry jdkLibraryEntry;
    if (jdk != null) {
      jdkLibraryEntry = new ModuleJdkOrderEntryImpl(jdk, this, myProjectRootManager);
    }
    else {
      jdkLibraryEntry = null;
    }
    replaceEntryOfType(JdkOrderEntry.class, jdkLibraryEntry);

  }

  @Override
  public void setInvalidSdk(String jdkName, String jdkType) {
    assertWritable();
    replaceEntryOfType(JdkOrderEntry.class, new ModuleJdkOrderEntryImpl(jdkName, jdkType, this, myProjectRootManager));
  }

  @Override
  public void inheritSdk() {
    assertWritable();
    replaceEntryOfType(JdkOrderEntry.class, new InheritedJdkOrderEntryImpl(this, myProjectRootManager));
  }


  @Override
  public <T extends OrderEntry> void replaceEntryOfType(@NotNull Class<T> entryClass, @Nullable final T entry) {
    assertWritable();
    for (int i = 0; i < myOrderEntries.size(); i++) {
      OrderEntry orderEntry = myOrderEntries.get(i);
      if (entryClass.isInstance(orderEntry)) {
        myOrderEntries.remove(i);
        if (entry != null) {
          myOrderEntries.add(i, entry);
        }
        return;
      }
    }

    if (entry != null) {
      myOrderEntries.add(0, entry);
    }
  }

  @Override
  public Sdk getSdk() {
    for (OrderEntry orderEntry : getOrderEntries()) {
      if (orderEntry instanceof JdkOrderEntry) {
        return ((JdkOrderEntry)orderEntry).getJdk();
      }
    }
    return null;
  }

  @Override
  public boolean isSdkInherited() {
    for (OrderEntry orderEntry : getOrderEntries()) {
      if (orderEntry instanceof InheritedJdkOrderEntry) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String getSdkName() {
    for (OrderEntry orderEntry : getOrderEntries()) {
      if (orderEntry instanceof JdkOrderEntry) {
        return ((JdkOrderEntry)orderEntry).getJdkName();
      }
    }
    return null;
  }

  public void assertWritable() {
    LOG.assertTrue(myWritable);
  }

  public boolean isDependsOn(final Module module) {
    for (OrderEntry entry : getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry) {
        final Module module1 = ((ModuleOrderEntry)entry).getModule();
        if (module1 == module) {
          return true;
        }
      }
    }
    return false;
  }

  public void projectOpened() {
    for (ContentEntry contentEntry : myContent) {
      ((RootModelComponentBase)contentEntry).projectOpened();
    }
    for (OrderEntry orderEntry : myOrderEntries) {
      ((RootModelComponentBase)orderEntry).projectOpened();
    }
  }

  public void projectClosed() {
    for (ContentEntry contentEntry : myContent) {
      ((RootModelComponentBase)contentEntry).projectClosed();
    }
    for (OrderEntry orderEntry : myOrderEntries) {
      ((RootModelComponentBase)orderEntry).projectClosed();
    }
  }

  public boolean isOrderEntryDisposed() {
    for (OrderEntry entry : myOrderEntries) {
      if (entry instanceof RootModelComponentBase && ((RootModelComponentBase)entry).isDisposed()) return true;
    }
    return false;
  }

  private static class ContentComparator implements Comparator<ContentEntry> {
    public static final ContentComparator INSTANCE = new ContentComparator();

    @Override
    public int compare(@NotNull final ContentEntry o1, @NotNull final ContentEntry o2) {
      return o1.getUrl().compareTo(o2.getUrl());
    }
  }

  @Override
  public VirtualFile getExplodedDirectory() {
    return myExplodedDirectoryPointer == null ? null : myExplodedDirectoryPointer.getFile();
  }

  @Override
  public void setExplodedDirectory(@Nullable VirtualFile file) {
    setExplodedDirectory(file == null ? null : file.getUrl());
  }

  @Override
  public void setExplodedDirectory(@Nullable String url) {
    myExplodedDirectory = url;
    myExplodedDirectoryPointer = url == null ? null : myFilePointerManager.create(url, myDisposable, null);
  }

  @Override
  @NotNull
  public Module getModule() {
    return myModuleRootManager.getModule();
  }

  @Override
  public String getExplodedDirectoryUrl() {
    return myExplodedDirectoryPointer == null ? null : myExplodedDirectoryPointer.getUrl();
  }

  private static boolean vptrEqual(@Nullable VirtualFilePointer p1, @Nullable VirtualFilePointer p2) {
    if (p1 == null && p2 == null) return true;
    if (p1 == null || p2 == null) return false;
    return Comparing.equal(p1.getUrl(), p2.getUrl());
  }


  @Override
  public boolean isChanged() {
    if (!myWritable) return false;

    if (!vptrEqual(myExplodedDirectoryPointer, getSourceModel().myExplodedDirectoryPointer)) {
      return true;
    }

    for (ModuleExtension moduleExtension : myExtensions) {
      if (moduleExtension.isChanged()) return true;
    }

    return myExcludeExploded != getSourceModel().myExcludeExploded ||
           areOrderEntriesChanged() ||
           areContentEntriesChanged() || areOrderRootPointerContainersChanged();
  }

  private boolean areOrderRootPointerContainersChanged() {
    if (myOrderRootPointerContainers.size() != getSourceModel().myOrderRootPointerContainers.size()) return true;
    for (final OrderRootType type : myOrderRootPointerContainers.keySet()) {
      final VirtualFilePointerContainer container = myOrderRootPointerContainers.get(type);
      final VirtualFilePointerContainer otherContainer = getSourceModel().myOrderRootPointerContainers.get(type);
      if (container == null || otherContainer == null) {
        if (container != otherContainer) return true;
      } else {
        final String[] urls = container.getUrls();
        final String[] otherUrls = otherContainer.getUrls();
        if (urls.length != otherUrls.length) return true;
        for (int i = 0; i < urls.length; i++) {
          if (!Comparing.strEqual(urls[i], otherUrls[i])) return true;
        }
      }
    }
    return false;
  }

  private boolean areContentEntriesChanged() {
    return ArrayUtil.lexicographicCompare(getContentEntries(), getSourceModel().getContentEntries()) != 0;
  }

  private boolean areOrderEntriesChanged() {
    OrderEntry[] orderEntries = getOrderEntries();
    OrderEntry[] sourceOrderEntries = getSourceModel().getOrderEntries();
    if (orderEntries.length != sourceOrderEntries.length) return true;
    for (int i = 0; i < orderEntries.length; i++) {
      OrderEntry orderEntry = orderEntries[i];
      OrderEntry sourceOrderEntry = sourceOrderEntries[i];
      if (!orderEntriesEquals(orderEntry, sourceOrderEntry)) {
        return true;
      }
    }
    return false;
  }

  private static boolean orderEntriesEquals(@NotNull OrderEntry orderEntry1, @NotNull OrderEntry orderEntry2) {
    if (!((OrderEntryBaseImpl)orderEntry1).sameType(orderEntry2)) return false;
    if (orderEntry1 instanceof JdkOrderEntry) {
      if (!(orderEntry2 instanceof JdkOrderEntry)) return false;
      if (orderEntry1 instanceof InheritedJdkOrderEntry && orderEntry2 instanceof ModuleJdkOrderEntry) {
        return false;
      }
      if (orderEntry2 instanceof InheritedJdkOrderEntry && orderEntry1 instanceof ModuleJdkOrderEntry) {
        return false;
      }
      if (orderEntry1 instanceof ModuleJdkOrderEntry && orderEntry2 instanceof ModuleJdkOrderEntry) {
        String name1 = ((ModuleJdkOrderEntry)orderEntry1).getJdkName();
        String name2 = ((ModuleJdkOrderEntry)orderEntry2).getJdkName();
        if (!Comparing.strEqual(name1, name2)) {
          return false;
        }
      }
    }
    if (orderEntry1 instanceof ExportableOrderEntry) {
      if (!(((ExportableOrderEntry)orderEntry1).isExported() == ((ExportableOrderEntry)orderEntry2).isExported())) {
        return false;
      }
      if (!(((ExportableOrderEntry)orderEntry1).getScope() == ((ExportableOrderEntry)orderEntry2).getScope())) {
        return false;
      }
    }
    if (orderEntry1 instanceof ModuleOrderEntry) {
      LOG.assertTrue(orderEntry2 instanceof ModuleOrderEntry);
      final String name1 = ((ModuleOrderEntry)orderEntry1).getModuleName();
      final String name2 = ((ModuleOrderEntry)orderEntry2).getModuleName();
      return Comparing.equal(name1, name2);
    }

    if (orderEntry1 instanceof LibraryOrderEntry) {
      LOG.assertTrue(orderEntry2 instanceof LibraryOrderEntry);
      LibraryOrderEntry libraryOrderEntry1 = (LibraryOrderEntry)orderEntry1;
      LibraryOrderEntry libraryOrderEntry2 = (LibraryOrderEntry)orderEntry2;
      boolean equal = Comparing.equal(libraryOrderEntry1.getLibraryName(), libraryOrderEntry2.getLibraryName())
                      && Comparing.equal(libraryOrderEntry1.getLibraryLevel(), libraryOrderEntry2.getLibraryLevel());
      if (!equal) return false;
    }

    final OrderRootType[] allTypes = OrderRootType.getAllTypes();
    for (OrderRootType type : allTypes) {
      final String[] orderedRootUrls1 = orderEntry1.getUrls(type);
      final String[] orderedRootUrls2 = orderEntry2.getUrls(type);
      if (!Arrays.equals(orderedRootUrls1, orderedRootUrls2)) {
        return false;
      }
    }
    return true;
  }

  void makeExternalChange(@NotNull Runnable runnable) {
    if (myWritable || myDisposed) return;
    myModuleRootManager.makeRootsChange(runnable);
  }

  @Override
  public void dispose() {
    assert !myDisposed;
    Disposer.dispose(myDisposable);
    myExtensions.clear();
    myWritable = false;
    myDisposed = true;
  }

  @Override
  public boolean isExcludeExplodedDirectory() {
    return myExcludeExploded;
  }

  @Override
  public void setExcludeExplodedDirectory(boolean excludeExplodedDir) {
    myExcludeExploded = excludeExplodedDir;
  }

  private class Order extends ArrayList<OrderEntry> {
    @Override
    public void clear() {
      super.clear();
      clearCachedEntries();
    }

    @NotNull
    @Override
    public OrderEntry set(int i, @NotNull OrderEntry orderEntry) {
      super.set(i, orderEntry);
      ((OrderEntryBaseImpl)orderEntry).setIndex(i);
      clearCachedEntries();
      return orderEntry;
    }

    @Override
    public boolean add(@NotNull OrderEntry orderEntry) {
      super.add(orderEntry);
      ((OrderEntryBaseImpl)orderEntry).setIndex(size() - 1);
      clearCachedEntries();
      return true;
    }

    @Override
    public void add(int i, OrderEntry orderEntry) {
      super.add(i, orderEntry);
      clearCachedEntries();
      setIndicies(i);
    }

    @Override
    public OrderEntry remove(int i) {
      OrderEntry entry = super.remove(i);
      setIndicies(i);
      clearCachedEntries();
      return entry;
    }

    @Override
    public boolean remove(Object o) {
      int index = indexOf(o);
      if (index < 0) return false;
      remove(index);
      clearCachedEntries();
      return true;
    }

    @Override
    public boolean addAll(Collection<? extends OrderEntry> collection) {
      int startSize = size();
      boolean result = super.addAll(collection);
      setIndicies(startSize);
      clearCachedEntries();
      return result;
    }

    @Override
    public boolean addAll(int i, Collection<? extends OrderEntry> collection) {
      boolean result = super.addAll(i, collection);
      setIndicies(i);
      clearCachedEntries();
      return result;
    }

    @Override
    public void removeRange(int i, int i1) {
      super.removeRange(i, i1);
      clearCachedEntries();
      setIndicies(i);
    }

    @Override
    public boolean removeAll(Collection<?> collection) {
      boolean result = super.removeAll(collection);
      setIndicies(0);
      clearCachedEntries();
      return result;
    }

    @Override
    public boolean retainAll(Collection<?> collection) {
      boolean result = super.retainAll(collection);
      setIndicies(0);
      clearCachedEntries();
      return result;
    }

    private void clearCachedEntries() {
      myCachedOrderEntries = null;
    }
    private void setIndicies(int startIndex) {
      for (int j = startIndex; j < size(); j++) {
        ((OrderEntryBaseImpl)get(j)).setIndex(j);
      }
    }
  }

  @Override
  @NotNull
  public String[] getDependencyModuleNames() {
    List<String> result = orderEntries().withoutSdk().withoutLibraries().withoutModuleSourceEntries().process(new CollectDependentModules(), new ArrayList<String>());
    return ContainerUtil.toArray(result, new String[result.size()]);
  }

  @Override
  @NotNull
  public VirtualFile[] getRootPaths(final OrderRootType rootType) {
    final VirtualFilePointerContainer container = myOrderRootPointerContainers.get(rootType);
    if (container != null) return container.getFiles();
    for (ModuleExtension extension : myExtensions) {
      final VirtualFile[] files = extension.getRootPaths(rootType);
      if (files != null) return files;
    }
    return VirtualFile.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public String[] getRootUrls(final OrderRootType rootType) {
    final VirtualFilePointerContainer container = myOrderRootPointerContainers.get(rootType);
    if (container != null) return container.getUrls();
    for (ModuleExtension extension : myExtensions) {
      final String[] urls = extension.getRootUrls(rootType);
      if (urls != null) return urls;
    }
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  @NotNull
  public Module[] getModuleDependencies() {
    return getModuleDependencies(true);
  }

  @Override
  @NotNull
  public Module[] getModuleDependencies(boolean includeTests) {
    final List<Module> result = new ArrayList<Module>();

    for (OrderEntry entry : getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry) {
        ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)entry;
        final DependencyScope scope = moduleOrderEntry.getScope();
        if (!includeTests && !scope.isForProductionCompile() && !scope.isForProductionRuntime()) {
          continue;
        }
        final Module module1 = moduleOrderEntry.getModule();
        if (module1 != null) {
          result.add(module1);
        }
      }
    }

    return ContainerUtil.toArray(result, new Module[result.size()]);
  }

  private RootModelImpl getSourceModel() {
    assertWritable();
    return myModuleRootManager.getRootModel();
  }

  private static class CollectDependentModules extends RootPolicy<List<String>> {
    @NotNull
    @Override
    public List<String> visitModuleOrderEntry(@NotNull ModuleOrderEntry moduleOrderEntry, @NotNull List<String> arrayList) {
      arrayList.add(moduleOrderEntry.getModuleName());
      return arrayList;
    }
  }

  @Override
  public void setRootUrls(final OrderRootType orderRootType, @NotNull final String[] urls) {
    assertWritable();
    VirtualFilePointerContainer container = myOrderRootPointerContainers.get(orderRootType);
    if (container == null) {
      container = myFilePointerManager.createContainer(myDisposable, null);
      myOrderRootPointerContainers.put((PersistentOrderRootType) orderRootType, container);
    }
    container.clear();
    for (final String url : urls) {
      container.add(url);
    }
  }

  @Nullable
  private VirtualFilePointer getOutputPathValue(@NotNull Element element, String tag, final boolean createPointer) {
    final Element outputPathChild = element.getChild(tag);
    VirtualFilePointer vptr = null;
    if (outputPathChild != null && createPointer) {
      String outputPath = outputPathChild.getAttributeValue(ATTRIBUTE_URL);
      vptr = myFilePointerManager.create(outputPath, myDisposable, null);
    }
    return vptr;
  }

  @Nullable
  private static String getOutputPathValue(@NotNull Element element, String tag) {
    final Element outputPathChild = element.getChild(tag);
    if (outputPathChild != null) {
      return outputPathChild.getAttributeValue(ATTRIBUTE_URL);
    }
    return null;
  }

  @Nullable
  @Override
  public <T> T getModuleExtension(@NotNull final Class<T> klass) {
    for (ModuleExtension extension : myExtensions) {
      if (klass.isAssignableFrom(extension.getClass())) return (T)extension;
    }
    return null;
  }

  void registerOnDispose(@NotNull Disposable disposable) {
    Disposer.register(myDisposable, disposable);
  }
}
