/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 *  @author dsl
 */
class LibraryOrderEntryImpl extends LibraryOrderEntryBaseImpl implements LibraryOrderEntry, ClonableOrderEntry, WritableOrderEntry {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.LibraryOrderEntryImpl");
  private Library myLibrary;
  @Nullable private String myLibraryName; // is non-null if myLibrary == null
  @Nullable private String myLibraryLevel; // is non-null if myLibraryLevel == null
  private boolean myExported;
  @NonNls static final String ENTRY_TYPE = "library";
  @NonNls private static final String NAME_ATTR = "name";
  @NonNls private static final String LEVEL_ATTR = "level";
  private final MyOrderEntryLibraryTableListener myLibraryListener = new MyOrderEntryLibraryTableListener();
  @NonNls private static final String EXPORTED_ATTR = "exported";
  private LibraryType myLibraryType;

  LibraryOrderEntryImpl(@NotNull Library library, @NotNull RootModelImpl rootModel, @NotNull ProjectRootManagerImpl projectRootManager) {
    super(rootModel, projectRootManager);
    LOG.assertTrue(library.getTable() != null);
    myLibrary = library;
    if (myLibrary instanceof LibraryEx) {
      myLibraryType = ((LibraryEx)myLibrary).getType();
    }
    addListeners();
    init();
  }

  LibraryOrderEntryImpl(@NotNull Element element, @NotNull RootModelImpl rootModel, @NotNull ProjectRootManagerImpl projectRootManager) throws InvalidDataException {
    super(rootModel, projectRootManager);
    LOG.assertTrue(ENTRY_TYPE.equals(element.getAttributeValue(OrderEntryFactory.ORDER_ENTRY_TYPE_ATTR)));
    myExported = element.getAttributeValue(EXPORTED_ATTR) != null;
    myScope = DependencyScope.readExternal(element);
    String level = element.getAttributeValue(LEVEL_ATTR);
    String name = element.getAttributeValue(NAME_ATTR);
    if (name == null) throw new InvalidDataException();
    if (level == null) throw new InvalidDataException();
    searchForLibrary(name, level);
    addListeners();
    init();
  }

  private LibraryOrderEntryImpl(@NotNull LibraryOrderEntryImpl that, @NotNull RootModelImpl rootModel, @NotNull ProjectRootManagerImpl projectRootManager) {
    super (rootModel, projectRootManager);
    if (that.myLibrary == null) {
      myLibraryName = that.myLibraryName;
      myLibraryLevel = that.myLibraryLevel;
    }
    else {
      myLibrary = that.myLibrary;
      myLibraryType = that.myLibraryType;
    }
    myExported = that.myExported;
    myScope = that.myScope;
    addListeners();
    init();
  }

  public LibraryOrderEntryImpl(@NotNull String name,
                               @NotNull String level,
                               @NotNull RootModelImpl rootModel,
                               @NotNull ProjectRootManagerImpl projectRootManager) {
    super(rootModel, projectRootManager);
    searchForLibrary(name, level);
    addListeners();
  }

  private void searchForLibrary(@NotNull String name, @NotNull String level) {
    if (myLibrary != null) return;
    final LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(level, getRootModel().getModule().getProject());
    final Library library = libraryTable != null ? libraryTable.getLibraryByName(name) : null;
    if (library == null) {
      myLibraryName = name;
      myLibraryLevel = level;
      myLibrary = null;
    }
    else {
      myLibraryName = null;
      myLibraryLevel = null;
      myLibrary = library;
      if (library instanceof LibraryEx) {
        myLibraryType = ((LibraryEx)library).getType();
      }
    }
  }

  @Override
  public boolean isExported() {
    return myExported;
  }

  @Override
  public void setExported(boolean exported) {
    myExported = exported;
  }

  @Override
  @NotNull
  public DependencyScope getScope() {
    return myScope;
  }

  @Override
  public void setScope(@NotNull DependencyScope scope) {
    myScope = scope;
  }

  @Override
  @Nullable
  public Library getLibrary() {
    Library library = getRootModel().getConfigurationAccessor().getLibrary(myLibrary, myLibraryName, myLibraryLevel);
    if (library != null) { //library was not deleted
      return library;
    }
    if (myLibrary != null) {
      myLibraryName = myLibrary.getName();
      myLibraryLevel = myLibrary.getTable().getTableLevel();
    }
    myLibrary = null;
    return null;
  }

  @Override
  public boolean isModuleLevel() {
    return false;
  }

  @Override
  @Nullable
  public String getPresentableName() {
    return getLibraryName();
  }

  @Override
  @Nullable
  protected RootProvider getRootProvider() {
    return myLibrary == null ? null : myLibrary.getRootProvider();
  }

  @Override
  public boolean isValid() {
    if (isDisposed()) {
      return false;
    }
    Library library = getLibrary();
    return library != null && !((LibraryEx)library).isDisposed();
  }

  @Override
  public <R> R accept(@NotNull RootPolicy<R> policy, R initialValue) {
    return policy.visitLibraryOrderEntry(this, initialValue);
  }

  @Override
  @NotNull
  public OrderEntry cloneEntry(@NotNull RootModelImpl rootModel,
                               ProjectRootManagerImpl projectRootManager,
                               VirtualFilePointerManager filePointerManager) {
    ProjectRootManagerImpl rootManager = ProjectRootManagerImpl.getInstanceImpl(getRootModel().getModule().getProject());
    return new LibraryOrderEntryImpl(this, rootModel, rootManager);
  }

  @Override
  public void writeExternal(@NotNull Element rootElement) throws WriteExternalException {
    final Element element = OrderEntryFactory.createOrderEntryElement(ENTRY_TYPE);
    final String libraryLevel = getLibraryLevel();
    if (myExported) {
      element.setAttribute(EXPORTED_ATTR, "");
    }
    myScope.writeExternal(element);
    element.setAttribute(NAME_ATTR, getLibraryName());
    element.setAttribute(LEVEL_ATTR, libraryLevel);
    rootElement.addContent(element);
  }

  @Override
  @Nullable
  public String getLibraryLevel() {
    if (myLibrary != null) {
      final LibraryTable table = myLibrary.getTable();
      return table.getTableLevel();
    } else {
      return myLibraryLevel;
    }
  }

  @Override
  public String getLibraryName() {
    return myLibrary == null ? myLibraryName : myLibrary.getName();
  }

  private void addListeners () {
    final String libraryLevel = getLibraryLevel();
    final LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(libraryLevel, getRootModel().getModule().getProject());
    if (libraryTable != null) {
      myProjectRootManagerImpl.addListenerForTable(myLibraryListener, libraryTable);
    }
  }


  @Override
  public boolean isSynthetic() {
    return false;
  }

  @Override
  public void dispose() {
    super.dispose();
    final LibraryTable libraryTable =
      LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(getLibraryLevel(), getRootModel().getModule().getProject());
    if (libraryTable != null) {
      myProjectRootManagerImpl.removeListenerForTable(myLibraryListener, libraryTable);
    }
  }


  private void afterLibraryAdded(@NotNull Library newLibrary) {
    if (myLibrary == null) {
      if (Comparing.equal(myLibraryName, newLibrary.getName())) {
        myLibrary = newLibrary;
        myLibraryName = null;
        myLibraryLevel = null;
        if (newLibrary instanceof LibraryEx && myLibraryType == null) {
          myLibraryType = ((LibraryEx)newLibrary).getType();
        }
        updateFromRootProviderAndSubscribe();
      }
    }
  }

  private void beforeLibraryRemoved(Library library) {
    if (library == myLibrary) {
      myLibraryName = myLibrary.getName();
      myLibraryLevel = myLibrary.getTable().getTableLevel();
      myLibrary = null;
      updateFromRootProviderAndSubscribe();
    }
  }

  private class MyOrderEntryLibraryTableListener implements LibraryTable.Listener {
    public MyOrderEntryLibraryTableListener() {
    }

    @Override
    public void afterLibraryAdded(@NotNull Library newLibrary) {
      LibraryOrderEntryImpl.this.afterLibraryAdded(newLibrary);
    }

    @Override
    public void afterLibraryRenamed(@NotNull Library library) {
      afterLibraryAdded(library);
    }

    @Override
    public void beforeLibraryRemoved(Library library) {
      LibraryOrderEntryImpl.this.beforeLibraryRemoved(library);
    }

    @Override
    public void afterLibraryRemoved(Library library) {
    }
  }

  @Override
  protected VirtualFile[] filterDirectories(@NotNull VirtualFile[] files) {
    if (myLibraryType != null && myLibraryType.isFileBased()) {
      return files;
    }
    return super.filterDirectories(files);
  }
}
