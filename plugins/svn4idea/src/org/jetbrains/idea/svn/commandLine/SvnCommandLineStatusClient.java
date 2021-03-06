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
package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.containers.Convertor;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.portable.PortableStatus;
import org.jetbrains.idea.svn.portable.SvnExceptionWrapper;
import org.jetbrains.idea.svn.portable.SvnStatusClientI;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/25/12
 * Time: 5:21 PM
 */
public class SvnCommandLineStatusClient implements SvnStatusClientI {
  private final Project myProject;
  private final SvnCommandLineInfoClient myInfoClient;

  public SvnCommandLineStatusClient(Project project) {
    myProject = project;
    myInfoClient = new SvnCommandLineInfoClient(project);
  }

  @Override
  public long doStatus(File path, boolean recursive, boolean remote, boolean reportAll, boolean includeIgnored, ISVNStatusHandler handler)
    throws SVNException {
    return doStatus(path, recursive, remote, reportAll, includeIgnored, false, handler);
  }

  @Override
  public long doStatus(File path,
                       boolean recursive,
                       boolean remote,
                       boolean reportAll,
                       boolean includeIgnored,
                       boolean collectParentExternals,
                       ISVNStatusHandler handler) throws SVNException {
    return doStatus(path, SVNRevision.UNDEFINED, recursive, remote, reportAll, includeIgnored, collectParentExternals, handler);
  }

  @Override
  public long doStatus(File path,
                       SVNRevision revision,
                       boolean recursive,
                       boolean remote,
                       boolean reportAll,
                       boolean includeIgnored,
                       boolean collectParentExternals,
                       ISVNStatusHandler handler) throws SVNException {
    return doStatus(path, revision, recursive ? SVNDepth.INFINITY : SVNDepth.EMPTY, remote, reportAll, includeIgnored,
                    collectParentExternals, handler, null);
  }

  @Override
  public long doStatus(final File path,
                       final SVNRevision revision,
                       final SVNDepth depth,
                       boolean remote,
                       boolean reportAll,
                       boolean includeIgnored,
                       boolean collectParentExternals,
                       final ISVNStatusHandler handler,
                       final Collection changeLists) throws SVNException {
    final File base = path.isDirectory() ? path : path.getParentFile();

    final SVNInfo infoBase = myInfoClient.doInfo(base, revision);

    // todo can not understand why revision can be used here
    final SvnSimpleCommand command = new SvnSimpleCommand(myProject, base, SvnCommandName.st);

    if (depth != null) {
      command.addParameters("--depth", depth.getName());
    }
    if (remote) {
      command.addParameters("-u");
    }
    if (reportAll) {
      command.addParameters("-v");
    }
    if (includeIgnored) {
      command.addParameters("--no-ignore");
    }
    // no way in interface to ignore externals
    /*if (! collectParentExternals) {
      command.addParameters("--ignore-externals");
    }*/

    //--changelist (--cl) ARG
    changelistsToCommand(changeLists, command);
    command.addParameters("--xml");

    final String[] changelistName = new String[1];
    final SvnStatusHandler[] svnHandl = new SvnStatusHandler[1];
    svnHandl[0] = new SvnStatusHandler(new SvnStatusHandler.ExternalDataCallback() {
      @Override
      public void switchPath() {
        final PortableStatus pending = svnHandl[0].getPending();
        pending.setChangelistName(changelistName[0]);
        try {
          if (infoBase != null) {
            final String append = SVNPathUtil.append(infoBase.getURL().toString(), FileUtil.toSystemIndependentName(pending.getPath()));
            pending.setURL(SVNURL.parseURIEncoded(append));
          }
          handler.handleStatus(pending);
        }
        catch (SVNException e) {
          throw new SvnExceptionWrapper(e);
        }
      }

      @Override
      public void switchChangeList(String newList) {
        changelistName[0] = newList;
      }
    }, base, new Convertor<File, SVNInfo>() {
      @Override
      public SVNInfo convert(File o) {
        try {
          return myInfoClient.doInfo(o, revision);
        }
        catch (SVNException e) {
          throw new SvnExceptionWrapper(e);
        }
      }
    });

    try {
      final String result = command.run();
      SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
      parser.parse(new ByteArrayInputStream(result.getBytes(CharsetToolkit.UTF8_CHARSET)), svnHandl[0]);
      if (! svnHandl[0].isAnythingReported()) {
        if (! SvnUtil.isSvnVersioned(myProject, path)) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY));
        }
      }
    }
    catch (SvnExceptionWrapper e) {
      throw (SVNException) e.getCause();
    } catch (IOException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR), e);
    }
    catch (ParserConfigurationException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR), e);
    }
    catch (SAXException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR), e);
    }
    catch (VcsException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR), e);
    }
    return 0;
  }

  public static void changelistsToCommand(Collection changeLists, SvnSimpleCommand command) {
    if (changeLists != null) {
      for (Object o : changeLists) {
        final String name = (String) o;
        command.addParameters("--cl", name);
      }
    }
  }

  @Override
  public SVNStatus doStatus(File path, boolean remote) throws SVNException {
    return doStatus(path, remote, false);
  }

  @Override
  public SVNStatus doStatus(File path, boolean remote, boolean collectParentExternals) throws SVNException {
    final SVNStatus[] svnStatus = new SVNStatus[1];
    doStatus(path, SVNRevision.UNDEFINED, SVNDepth.EMPTY, remote, false, false, collectParentExternals, new ISVNStatusHandler() {
      @Override
      public void handleStatus(SVNStatus status) throws SVNException {
        svnStatus[0] = status;
      }
    }, null);
    return svnStatus[0];
  }
}
