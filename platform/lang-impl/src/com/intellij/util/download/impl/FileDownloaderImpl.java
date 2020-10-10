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

package com.intellij.util.download.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.FileDownloader;
import com.intellij.util.io.UrlConnectionUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.IOExceptionDialog;
import com.intellij.util.net.NetUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class FileDownloaderImpl implements FileDownloader {
  private static final int CONNECTION_TIMEOUT = 60*1000;
  private static final int READ_TIMEOUT = 60*1000;
  @NonNls private static final String LIB_SCHEMA = "lib://";

  private List<? extends DownloadableFileDescription> myFileDescriptions;
  private JComponent myParent;
  private @Nullable Project myProject;
  private String myDirectoryForDownloadedFilesPath;
  private String myDialogTitle;

  public FileDownloaderImpl(final List<? extends DownloadableFileDescription> fileDescriptions,
                            final @Nullable Project project,
                            JComponent parent,
                            @NotNull String presentableDownloadName) {
    myProject = project;
    myFileDescriptions = fileDescriptions;
    myParent = parent;
    myDialogTitle = IdeBundle.message("progress.download.0.title", StringUtil.capitalize(presentableDownloadName));
  }

  @NotNull
  @Override
  public FileDownloader toDirectory(@NotNull String directoryForDownloadedFilesPath) {
    myDirectoryForDownloadedFilesPath = directoryForDownloadedFilesPath;
    return this;
  }

  @Override
  public VirtualFile[] download() {
    VirtualFile dir = null;
    if (myDirectoryForDownloadedFilesPath != null) {
      File ioDir = new File(FileUtil.toSystemDependentName(myDirectoryForDownloadedFilesPath));
      ioDir.mkdirs();
      dir = LocalFileSystem.getInstance().refreshAndFindFileByPath(myDirectoryForDownloadedFilesPath);
    }

    if (dir == null) {
      dir = chooseDirectoryForFiles();
    }

    if (dir != null) {
      return doDownload(dir);
    }
    return null;
  }

  @Nullable
  private VirtualFile[] doDownload(final VirtualFile dir) {
    HttpConfigurable.getInstance().setAuthenticator();
    final List<Pair<DownloadableFileDescription, File>> downloadedFiles = new ArrayList<Pair<DownloadableFileDescription, File>>();
    final List<File> existingFiles = new ArrayList<File>();
    final Ref<Exception> exceptionRef = Ref.create(null);
    final Ref<DownloadableFileDescription> currentFile = new Ref<DownloadableFileDescription>();
    final File ioDir = VfsUtil.virtualToIoFile(dir);

    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        try {
          for (int i = 0; i < myFileDescriptions.size(); i++) {
            DownloadableFileDescription description = myFileDescriptions.get(i);
            currentFile.set(description);
            if (indicator != null) {
              indicator.checkCanceled();
              indicator.setText(IdeBundle.message("progress.downloading.0.of.1.file.text", i+1, myFileDescriptions.size()));
            }

            final File existing = new File(ioDir, description.getDefaultFileName());
            long size = existing.exists() ? existing.length() : -1;

            if (!download(description, size, downloadedFiles)) {
              existingFiles.add(existing);
            }
          }
        }
        catch (ProcessCanceledException e) {
          exceptionRef.set(e);
        }
        catch (IOException e) {
          exceptionRef.set(e);
        }
      }
    }, myDialogTitle, true, myProject, myParent);

    Exception exception = exceptionRef.get();
    if (exception != null) {
      deleteFiles(downloadedFiles);
      if (exception instanceof IOException) {
        String message = IdeBundle.message("error.file.download.failed", exception.getMessage());
        if (currentFile.get() != null) {
          message += ": " + currentFile.get().getDownloadUrl();
        }
        final boolean tryAgain = IOExceptionDialog.showErrorDialog(myDialogTitle, message);
        if (tryAgain) {
          return doDownload(dir);
        }
      }
      return null;
    }

    try {
      return moveToDir(existingFiles, downloadedFiles, dir);
    }
    catch (IOException e) {
      if (myProject != null) {
        Messages.showErrorDialog(myProject, myDialogTitle, e.getMessage());
      }
      else {
        Messages.showErrorDialog(myParent, myDialogTitle, e.getMessage());
      }
      return null;
    }
  }

  @Nullable
  private VirtualFile chooseDirectoryForFiles() {
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    descriptor.setTitle(IdeBundle.message("dialog.directory.for.downloaded.files.title"));

    final VirtualFile file;
    if (myProject != null) {
      file = FileChooser.chooseFile(myProject, descriptor, myProject.getBaseDir());
    }
    else {
      file = FileChooser.chooseFile(myParent, descriptor, null);
    }

    return file;
  }

  @NotNull
  private static VirtualFile[] moveToDir(final List<File> existingFiles, final List<Pair<DownloadableFileDescription, File>> downloadedFiles, final VirtualFile dir) throws IOException {
    List<VirtualFile> files = new ArrayList<VirtualFile>();

    final File ioDir = VfsUtil.virtualToIoFile(dir);
    for (Pair<DownloadableFileDescription, File> pair : downloadedFiles) {
      final DownloadableFileDescription description = pair.getFirst();
      final boolean dontTouch = description.getDownloadUrl().startsWith(LIB_SCHEMA) || description.getDownloadUrl().startsWith(LocalFileSystem.PROTOCOL_PREFIX);
      final File toFile = dontTouch? pair.getSecond() : generateName(description, ioDir);
      if (!dontTouch) {
        FileUtil.rename(pair.getSecond(), toFile);
      }
      VirtualFile file = new WriteAction<VirtualFile>() {
        @Override
        protected void run(final Result<VirtualFile> result) {
          final String url = VfsUtil.getUrlForLibraryRoot(toFile);
          LocalFileSystem.getInstance().refreshAndFindFileByIoFile(toFile);
          result.setResult(VirtualFileManager.getInstance().refreshAndFindFileByUrl(url));
        }
      }.execute().getResultObject();
      if (file != null) {
        files.add(file);
      }
    }

    for (final File file : existingFiles) {
      VirtualFile libraryRootFile = new WriteAction<VirtualFile>() {
        @Override
        protected void run(final Result<VirtualFile> result) {
          final String url = VfsUtil.getUrlForLibraryRoot(file);
          result.setResult(VirtualFileManager.getInstance().refreshAndFindFileByUrl(url));
        }

      }.execute().getResultObject();
      if (libraryRootFile != null) {
        files.add(libraryRootFile);
      }
    }
    return VfsUtil.toVirtualFileArray(files);
  }

  private static File generateName(DownloadableFileDescription info, final File dir) {
    final String fileName = info.generateFileName(new Condition<String>() {
      @Override
      public boolean value(String s) {
        return !new File(dir, s).exists();
      }
    });
    return new File(dir, fileName);
  }

  private static void deleteFiles(final List<Pair<DownloadableFileDescription, File>> pairs) {
    for (Pair<DownloadableFileDescription, File> pair : pairs) {
      FileUtil.delete(pair.getSecond());
    }
    pairs.clear();
  }

  private static boolean download(final DownloadableFileDescription fileDescription,
                                  final long existingFileSize,
                                  final List<Pair<DownloadableFileDescription, File>> downloadedFiles) throws IOException {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    final String presentableUrl = fileDescription.getPresentableDownloadUrl();
    final String url = fileDescription.getDownloadUrl();
    if (url.startsWith(LIB_SCHEMA)) {
      indicator.setText2(IdeBundle.message("progress.locate.file.text", fileDescription.getPresentableFileName()));
      final String path = FileUtil.toSystemDependentName(StringUtil.trimStart(url, LIB_SCHEMA));
      final File file = PathManager.findFileInLibDirectory(path);
      downloadedFiles.add(Pair.create(fileDescription, file));
    }
    else if (url.startsWith(LocalFileSystem.PROTOCOL_PREFIX)) {
      String path = FileUtil.toSystemDependentName(StringUtil.trimStart(url, LocalFileSystem.PROTOCOL_PREFIX));
      File file = new File(path);
      if (file.exists()) {
        downloadedFiles.add(Pair.create(fileDescription, file));
      }
    }
    else {
      indicator.setText2(IdeBundle.message("progress.connecting.to.download.file.text", presentableUrl));
      indicator.setIndeterminate(true);
      HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
      connection.setConnectTimeout(CONNECTION_TIMEOUT);
      connection.setReadTimeout(READ_TIMEOUT);

      InputStream input = null;
      BufferedOutputStream output = null;

      boolean deleteFile = true;
      File tempFile = null;
      try {
        final int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
          throw new IOException(IdeBundle.message("error.connection.failed.with.http.code.N", responseCode));
        }

        final int size = connection.getContentLength();
        if (size != -1 && size == existingFileSize) {
          return false;
        }

        tempFile = FileUtil.createTempFile("downloaded", "file");
        input = UrlConnectionUtil.getConnectionInputStreamWithException(connection, indicator);
        output = new BufferedOutputStream(new FileOutputStream(tempFile));
        indicator.setText2(IdeBundle.message("progress.download.file.text", fileDescription.getPresentableFileName(), presentableUrl));
        indicator.setIndeterminate(size == -1);

        NetUtils.copyStreamContent(indicator, input, output, size);

        deleteFile = false;
        downloadedFiles.add(Pair.create(fileDescription, tempFile));
      }
      finally {
        if (input != null) {
          input.close();
        }
        if (output != null) {
          output.close();
        }
        if (deleteFile && tempFile != null) {
          FileUtil.delete(tempFile);
        }
        connection.disconnect();
      }
    }
    return true;
  }
}
