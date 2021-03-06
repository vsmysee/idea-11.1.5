/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.psi.impl.file;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.NonPhysicalFileSystem;
import com.intellij.openapi.vfs.VfsBundle;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChangeUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiFileSystemItemProcessor;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Map;

public class PsiDirectoryImpl extends PsiElementBase implements PsiDirectory, Queryable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.file.PsiDirectoryImpl");

  private final PsiManagerImpl myManager;
  private final VirtualFile myFile;

  public PsiDirectoryImpl(PsiManagerImpl manager, @NotNull VirtualFile file) {
    myManager = manager;
    myFile = file;
  }

  @Override
  @NotNull
  public VirtualFile getVirtualFile() {
    return myFile;
  }

  @Override
  public boolean isDirectory() {
    return true;
  }

  @Override
  public boolean isValid() {
    return myFile.isValid();
  }

  @Override
  @NotNull
  public Language getLanguage() {
    return Language.ANY;
  }

  @Override
  public PsiManager getManager() {
    return myManager;
  }

  @Override
  @NotNull
  public String getName() {
    return myFile.getName();
  }

  @Override
  @NotNull
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    checkSetName(name);

    /*
    final String oldName = myFile.getName();
    PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(myManager);
    event.setElement(this);
    event.setPropertyName(PsiTreeChangeEvent.PROP_DIRECTORY_NAME);
    event.setOldValue(oldName);
    myManager.beforePropertyChange(event);
    */

    try {
      myFile.rename(myManager, name);
    }
    catch (IOException e) {
      throw new IncorrectOperationException(e.toString());
    }

    /*
    PsiUndoableAction undoableAction = new PsiUndoableAction(){
      public void undo() throws IncorrectOperationException {
        if (!PsiDirectoryImpl.this.isValid()){
          throw new IncorrectOperationException();
        }
        setName(oldName);
      }
    };
    */

    /*
    event = new PsiTreeChangeEventImpl(myManager);
    event.setElement(this);
    event.setPropertyName(PsiTreeChangeEvent.PROP_DIRECTORY_NAME);
    event.setOldValue(oldName);
    event.setNewValue(name);
    event.setUndoableAction(undoableAction);
    myManager.propertyChanged(event);
    */
    return this;
  }

  @Override
  public void checkSetName(String name) throws IncorrectOperationException {
    //CheckUtil.checkIsIdentifier(name);
    CheckUtil.checkWritable(this);
    VirtualFile parentFile = myFile.getParent();
    if (parentFile == null) {
      throw new IncorrectOperationException(VfsBundle.message("cannot.rename.root.directory"));
    }
    VirtualFile child = parentFile.findChild(name);
    if (child != null && !child.equals(myFile)) {
      throw new IncorrectOperationException(VfsBundle.message("file.already.exists.error", child.getPresentableUrl()));
    }
  }

  @Override
  public PsiDirectory getParentDirectory() {
    VirtualFile parentFile = myFile.getParent();
    if (parentFile == null) return null;
    return myManager.findDirectory(parentFile);
  }

  @Override
  @NotNull
  public PsiDirectory[] getSubdirectories() {
    VirtualFile[] files = myFile.getChildren();
    ArrayList<PsiDirectory> dirs = new ArrayList<PsiDirectory>();
    for (VirtualFile file : files) {
      PsiDirectory dir = myManager.findDirectory(file);
      if (dir != null) {
        dirs.add(dir);
      }
    }
    return dirs.toArray(new PsiDirectory[dirs.size()]);
  }

  @Override
  @NotNull
  public PsiFile[] getFiles() {
    LOG.assertTrue(myFile.isValid());
    VirtualFile[] files = myFile.getChildren();
    ArrayList<PsiFile> psiFiles = new ArrayList<PsiFile>();
    for (VirtualFile file : files) {
      PsiFile psiFile = myManager.findFile(file);
      if (psiFile != null) {
        psiFiles.add(psiFile);
      }
    }
    return PsiUtilCore.toPsiFileArray(psiFiles);
  }

  @Override
  public PsiDirectory findSubdirectory(@NotNull String name) {
    VirtualFile childVFile = myFile.findChild(name);
    if (childVFile == null) return null;
    return myManager.findDirectory(childVFile);
  }

  @Override
  public PsiFile findFile(@NotNull String name) {
    VirtualFile childVFile = myFile.findChild(name);
    if (childVFile == null) return null;
    return myManager.findFile(childVFile);
  }

  @Override
  public boolean processChildren(PsiElementProcessor<PsiFileSystemItem> processor) {
    checkValid();
    ProgressIndicatorProvider.checkCanceled();

    for (VirtualFile vFile : myFile.getChildren()) {
      boolean isDir = vFile.isDirectory();
      if (processor instanceof PsiFileSystemItemProcessor &&
          !((PsiFileSystemItemProcessor)processor).acceptItem(vFile.getName(), isDir)) {
        continue;
      }
      if (isDir) {
        PsiDirectory dir = myManager.findDirectory(vFile);
        if (dir != null) {
          if (!processor.execute(dir)) return false;
        }
      }
      else {
        PsiFile file = myManager.findFile(vFile);
        if (file != null) {
          if (!processor.execute(file)) return false;
        }
      }
    }
    return true;
  }

  @Override
  @NotNull
  public PsiElement[] getChildren() {
    checkValid();

    VirtualFile[] files = myFile.getChildren();
    final ArrayList<PsiElement> children = new ArrayList<PsiElement>(files.length);
    processChildren(new PsiElementProcessor<PsiFileSystemItem>() {
      @Override
      public boolean execute(@NotNull final PsiFileSystemItem element) {
        children.add(element);
        return true;
      }
    });

    return PsiUtilCore.toPsiElementArray(children);
  }

  private void checkValid() {
    if (!isValid()) {
      throw new PsiInvalidElementAccessException(this);
    }
  }

  @Override
  public PsiDirectory getParent() {
    return getParentDirectory();
  }

  @Override
  public PsiFile getContainingFile() {
    return null;
  }

  @Override
  public TextRange getTextRange() {
    return null;
  }

  @Override
  public int getStartOffsetInParent() {
    return -1;
  }

  @Override
  public int getTextLength() {
    return -1;
  }

  @Override
  public PsiElement findElementAt(int offset) {
    return null;
  }

  @Override
  public int getTextOffset() {
    return -1;
  }

  @Override
  public String getText() {
    return ""; // TODO throw new InsupportedOperationException()
  }

  @Override
  @NotNull
  public char[] textToCharArray() {
    return ArrayUtil.EMPTY_CHAR_ARRAY; // TODO throw new InsupportedOperationException()
  }

  @Override
  public boolean textMatches(@NotNull CharSequence text) {
    return false;
  }

  @Override
  public boolean textMatches(@NotNull PsiElement element) {
    return false;
  }

  @Override
  public final boolean isWritable() {
    return myFile.isWritable();
  }

  @Override
  public boolean isPhysical() {
    return !(myFile.getFileSystem() instanceof NonPhysicalFileSystem) && !(myFile.getFileSystem().getProtocol().equals("temp"));
  }

  /**
   * @not_implemented
   */
  @Override
  public PsiElement copy() {
    LOG.error("not implemented");
    return null;
  }


  @Override
  @NotNull
  public PsiDirectory createSubdirectory(@NotNull String name) throws IncorrectOperationException {
    checkCreateSubdirectory(name);

    try {
      VirtualFile file = getVirtualFile().createChildDirectory(myManager, name);
      PsiDirectory directory = myManager.findDirectory(file);
      if (directory == null) throw new IncorrectOperationException("Cannot find directory in '" + file.getPresentableUrl() + "'");
      return directory;
    }
    catch (IOException e) {
      throw new IncorrectOperationException(e.toString());
    }
  }

  @Override
  public void checkCreateSubdirectory(@NotNull String name) throws IncorrectOperationException {
    // TODO : another check?
    //CheckUtil.checkIsIdentifier(name);
    VirtualFile existingFile = getVirtualFile().findChild(name);
    if (existingFile != null) {
      throw new IncorrectOperationException(VfsBundle.message("file.already.exists.error", existingFile.getPresentableUrl()));
    }
    CheckUtil.checkWritable(this);
  }

  @Override
  @NotNull
  public PsiFile createFile(@NotNull String name) throws IncorrectOperationException {
    checkCreateFile(name);

    try {
      VirtualFile vFile = getVirtualFile().createChildData(myManager, name);
      return myManager.findFile(vFile);
    }
    catch (IOException e) {
      throw new IncorrectOperationException(e.toString());
    }
  }

  @Override
  @NotNull
  public PsiFile copyFileFrom(@NotNull String newName, @NotNull PsiFile originalFile) throws IncorrectOperationException {
    checkCreateFile(newName);

    final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(originalFile);
    if (document != null) {
      FileDocumentManager.getInstance().saveDocument(document);
    }

    final VirtualFile parent = getVirtualFile();
    try {
      final VirtualFile vFile = originalFile.getVirtualFile();
      if (vFile == null) throw new IncorrectOperationException("Cannot copy nonphysical file");
      VirtualFile copyVFile;
      if (parent.getFileSystem() == vFile.getFileSystem()) {
        copyVFile = vFile.copy(this, parent, newName);
      }
      else if (vFile instanceof LightVirtualFile) {
        copyVFile = parent.createChildData(this, newName);
        copyVFile.setBinaryContent(originalFile.getText().getBytes(copyVFile.getCharset()));
      }
      else {
        copyVFile = VfsUtilCore.copyFile(this, vFile, parent, newName);
      }
      final PsiFile copyPsi = myManager.findFile(copyVFile);
      if (copyPsi == null) {
        LOG.error("Could not find file '" + copyVFile + "' after copying '" + vFile + "'");
      }
      updateAddedFile(copyPsi);
      return copyPsi;
    }
    catch (IOException e) {
      throw new IncorrectOperationException(e.toString(), e);
    }
  }

  private static void updateAddedFile(PsiFile copyPsi) throws IncorrectOperationException {
    final UpdateAddedFileProcessor processor = UpdateAddedFileProcessor.forElement(copyPsi);
    if (processor != null) {
      final TreeElement tree = (TreeElement)SourceTreeToPsiMap.psiElementToTree(copyPsi);
      if (tree != null) {
        ChangeUtil.encodeInformation(tree);
      }
      processor.update(copyPsi, null);
      if (tree != null) {
        ChangeUtil.decodeInformation(tree);
      }
    }
  }

  @Override
  public void checkCreateFile(@NotNull String name) throws IncorrectOperationException {
    VirtualFile existingFile = getVirtualFile().findChild(name);
    if (existingFile != null) {
      throw new IncorrectOperationException(VfsBundle.message("file.already.exists.error", existingFile.getPresentableUrl()));
    }
    CheckUtil.checkWritable(this);
  }


  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    checkAdd(element);
    if (element instanceof PsiDirectory) {
      LOG.error("not implemented");
      return null;
    }
    else if (element instanceof PsiFile) {
      PsiFile originalFile = (PsiFile)element;

      try {
        VirtualFile newVFile;
        final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(myManager.getProject());
        if (originalFile instanceof PsiFileImpl) {
          newVFile = myFile.createChildData(myManager, originalFile.getName());
          String text = originalFile.getText();
          final PsiFile psiFile = getManager().findFile(newVFile);
          final Document document = psiFile == null ? null : psiDocumentManager.getDocument(psiFile);
          final FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
          if (document != null) {
            document.setText(text);
            fileDocumentManager.saveDocument(document);
          }
          else {
            String lineSeparator = fileDocumentManager.getLineSeparator(newVFile, getProject());
            if (!lineSeparator.equals("\n")) {
              text = StringUtil.convertLineSeparators(text, lineSeparator);
            }

            final Writer writer = LoadTextUtil.getWriter(getProject(), newVFile, myManager, text, -1);
            try {
              writer.write(text);
            }
            finally {
              writer.close();
            }
          }
        }
        else {
          byte[] storedContents = ((PsiBinaryFileImpl)originalFile).getStoredContents();
          if (storedContents != null) {
            newVFile = myFile.createChildData(myManager, originalFile.getName());
            newVFile.setBinaryContent(storedContents);
          }
          else {
            newVFile = VfsUtilCore.copyFile(null, originalFile.getVirtualFile(), myFile);
          }
        }
        psiDocumentManager.commitAllDocuments();

        PsiFile newFile = myManager.findFile(newVFile);
        updateAddedFile(newFile);

        return newFile;
      }
      catch (IOException e) {
        throw new IncorrectOperationException(e.toString(), e);
      }
    }
    else {
      LOG.assertTrue(false);
      return null;
    }
  }

  @Override
  public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    if (element instanceof PsiDirectory) {
      String name = ((PsiDirectory)element).getName();
      PsiDirectory[] subpackages = getSubdirectories();
      for (PsiDirectory dir : subpackages) {
        if (Comparing.strEqual(dir.getName(), name)) {
          throw new IncorrectOperationException(VfsBundle.message("dir.already.exists.error", dir.getVirtualFile().getPresentableUrl()));
        }
      }
    }
    else if (element instanceof PsiFile) {
      String name = ((PsiFile)element).getName();
      PsiFile[] files = getFiles();
      for (PsiFile file : files) {
        if (Comparing.strEqual(file.getName(), name, SystemInfo.isFileSystemCaseSensitive)) {
          throw new IncorrectOperationException(VfsBundle.message("file.already.exists.error", file.getVirtualFile().getPresentableUrl()));
        }
      }
    }
    else {
      throw new IncorrectOperationException();
    }
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public void delete() throws IncorrectOperationException {
    checkDelete();
    //PsiDirectory parent = getParentDirectory();

    /*
    PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(myManager);
    event.setParent(parent);
    event.setChild(this);
    myManager.beforeChildRemoval(event);
    */

    try {
      myFile.delete(myManager);
    }
    catch (IOException e) {
      throw new IncorrectOperationException(e.toString(), e);
    }

    /*
    //TODO : allow undo
    PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
    treeEvent.setParent(parent);
    treeEvent.setChild(this);
    treeEvent.setUndoableAction(null);
    myManager.childRemoved(treeEvent);
    */
  }

  @Override
  public void checkDelete() throws IncorrectOperationException {
    CheckUtil.checkDelete(myFile);
  }

  /**
   * @not_implemented
   */
  @Override
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    LOG.error("not implemented");
    return null;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitDirectory(this);
  }

  public String toString() {
    return "PsiDirectory:" + myFile.getPresentableUrl();
  }

  @Override
  public ASTNode getNode() {
    return null;
  }

  @Override
  public boolean canNavigateToSource() {
    return false;
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProviders.getItemPresentation(this);
  }

  @Override
  public void navigate(boolean requestFocus) {
    PsiNavigationSupport.getInstance().navigateToDirectory(this, requestFocus);
  }

  @Override
  protected Icon getElementIcon(final int flags) {
    return PlatformIcons.DIRECTORY_CLOSED_ICON;
  }

  @Override
  public void putInfo(@NotNull Map<String, String> info) {
    info.put("fileName", getName());
  }
}
