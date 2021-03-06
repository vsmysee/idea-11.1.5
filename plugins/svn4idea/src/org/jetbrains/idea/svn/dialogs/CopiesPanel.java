/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.DottedBorder;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.table.JBTable;
import com.intellij.util.containers.Convertor;
import com.intellij.util.io.EqualityPolicy;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.idea.svn.NestedCopyType;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.actions.CleanupWorker;
import org.jetbrains.idea.svn.actions.SelectBranchPopup;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;
import org.jetbrains.idea.svn.checkout.SvnCheckoutProvider;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.*;
import java.util.List;

public class CopiesPanel {
  private final Project myProject;
  private MessageBusConnection myConnection;
  private SvnVcs myVcs;
  private JPanel myPanel;
  private JComponent myHolder;
  private LinkLabel myRefreshLabel;
  // updated only on AWT
  private List<OverrideEqualsWrapper<WCInfo>> myCurrentInfoList;
  private int myTextHeight;

  private final static String CHANGE_FORMAT = "CHANGE_FORMAT";
  private final static String CLEANUP = "CLEANUP";
  private final static String FIX_DEPTH = "FIX_DEPTH";
  private final static String CONFIGURE_BRANCHES = "CONFIGURE_BRANCHES";
  private final static String MERGE_FROM = "MERGE_FROM";

  public CopiesPanel(final Project project) {
    myProject = project;
    myConnection = myProject.getMessageBus().connect(myProject);
    myVcs = SvnVcs.getInstance(myProject);
    myCurrentInfoList = null;

    final Runnable focus = new Runnable() {
      public void run() {
        IdeFocusManager.getInstance(myProject).requestFocus(myRefreshLabel, true);
      }
    };
    final Runnable refreshView = new Runnable() {
      public void run() {
        final List<WCInfo> infoList = myVcs.getAllWcInfos();
        Runnable runnable = new Runnable() {
          public void run() {
            if (myCurrentInfoList != null) {
              final List<OverrideEqualsWrapper<WCInfo>> newList =
                ObjectsConvertor.convert(infoList, new Convertor<WCInfo, OverrideEqualsWrapper<WCInfo>>() {
                  public OverrideEqualsWrapper<WCInfo> convert(WCInfo o) {
                    return new OverrideEqualsWrapper<WCInfo>(InfoEqualityPolicy.getInstance(), o);
                  }
                }, ObjectsConvertor.NOT_NULL);

              if (Comparing.haveEqualElements(newList, myCurrentInfoList)) {
                myRefreshLabel.setEnabled(true);
                return;
              }
              myCurrentInfoList = newList;
            }
            Collections.sort(infoList, WCComparator.getInstance());
            updateList(infoList);
            myRefreshLabel.setEnabled(true);
            SwingUtilities.invokeLater(focus);
          }
        };
        ApplicationManager.getApplication().invokeLater(runnable, ModalityState.NON_MODAL);
      }
    };
    final Runnable refreshOnPooled = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().executeOnPooledThread(refreshView);
      }
    };
    myConnection.subscribe(SvnVcs.ROOTS_RELOADED, refreshOnPooled);

    final JPanel holderPanel = new JPanel(new BorderLayout());
    FontMetrics fm = holderPanel.getFontMetrics(holderPanel.getFont());
    myTextHeight = (int)(fm.getHeight() * 1.3);
    myPanel = new JPanel(new GridBagLayout());
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(myPanel, BorderLayout.NORTH);
    holderPanel.add(panel, BorderLayout.WEST);
    myRefreshLabel = new MyLinkLabel(myTextHeight, "Refresh", new LinkListener() {
      public void linkSelected(LinkLabel aSource, Object aLinkData) {
        if (myRefreshLabel.isEnabled()) {
          myVcs.invokeRefreshSvnRoots(true);
          myRefreshLabel.setEnabled(false);
        }
      }
    });
    final JScrollPane pane = ScrollPaneFactory.createScrollPane(holderPanel);
    myHolder = pane;
    final JScrollBar vBar = pane.getVerticalScrollBar();
    vBar.setBlockIncrement(vBar.getBlockIncrement() * 5);
    vBar.setUnitIncrement(vBar.getUnitIncrement() * 5);
    myHolder.setBorder(null);
    setFocusableForLinks(myRefreshLabel);
    refreshOnPooled.run();
    initView();
  }

  public JComponent getPreferredFocusedComponent() {
    return myRefreshLabel;
  }

  private void updateList(final List<WCInfo> infoList) {
    myPanel.removeAll();
    final Insets nullIndent = new Insets(1, 3, 1, 0);
    final GridBagConstraints gb =
      new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(2, 2, 0, 0), 0, 0);
    gb.insets.left = 4;
    myPanel.add(myRefreshLabel, gb);
    gb.insets.left = 1;

    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    final Insets topIndent = new Insets(10, 3, 0, 0);
    for (final WCInfo wcInfo : infoList) {
      final VirtualFile vf = lfs.refreshAndFindFileByIoFile(new File(wcInfo.getPath()));
      final VirtualFile root = (vf == null) ? wcInfo.getVcsRoot() : vf;

      final JEditorPane editorPane = new JEditorPane(UIUtil.HTML_MIME, "");
      editorPane.setEditable(false);
      editorPane.setFocusable(true);
      editorPane.setBackground(UIUtil.getPanelBackground());
      editorPane.setOpaque(false);
      editorPane.addHyperlinkListener(new HyperlinkListener() {
        @Override
        public void hyperlinkUpdate(HyperlinkEvent e) {
          if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            if (CONFIGURE_BRANCHES.equals(e.getDescription())) {
              if (! checkRoot(root, wcInfo.getPath(), " invoke Configure Branches")) return;
              BranchConfigurationDialog.configureBranches(myProject, root, true);
            } else if (FIX_DEPTH.equals(e.getDescription())) {
              final int result =
                Messages.showOkCancelDialog(myVcs.getProject(), "You are going to checkout into '" + wcInfo.getPath() + "' with 'infinity' depth.\n" +
                                                        "This will update your working copy to HEAD revision as well.",
                                    "Set working copy infinity depth",
                                    Messages.getWarningIcon());
              if (result == 0) {
                // update of view will be triggered by roots changed event
                SvnCheckoutProvider.checkout(myVcs.getProject(), new File(wcInfo.getPath()), wcInfo.getRootUrl(), SVNRevision.HEAD,
                                             SVNDepth.INFINITY, false, null, wcInfo.getFormat());
              }
            } else if (CHANGE_FORMAT.equals(e.getDescription())) {
              changeFormat(wcInfo);
            } else if (MERGE_FROM.equals(e.getDescription())) {
              if (! checkRoot(root, wcInfo.getPath(), " invoke Merge From")) return;
              mergeFrom(wcInfo, root, editorPane);
            } else if (CLEANUP.equals(e.getDescription())) {
              if (! checkRoot(root, wcInfo.getPath(), " invoke Cleanup")) return;
              new CleanupWorker(new VirtualFile[] {root}, myVcs.getProject(), "action.Subversion.cleanup.progress.title").execute();
            }
          }
        }

        private boolean checkRoot(VirtualFile root, final String path, final String actionName) {
          if (root == null) {
            Messages.showWarningDialog(myProject, "Invalid working copy root: " + path, "Can not " + actionName);
            return false;
          }
          return true;
        }
      });
      editorPane.setBorder(null);
      editorPane.setText(formatWc(wcInfo));

      final JPanel copyPanel = new JPanel(new GridBagLayout());

      final GridBagConstraints gb1 =
        new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, nullIndent, 0, 0);
      gb1.insets.top = 1;
      gb1.gridwidth = 3;

      gb.insets = topIndent;
      gb.fill = GridBagConstraints.HORIZONTAL;
      ++ gb.gridy;

      final JPanel contForCopy = new JPanel(new BorderLayout());
      contForCopy.add(copyPanel, BorderLayout.WEST);
      myPanel.add(contForCopy, gb);

      copyPanel.add(editorPane, gb1);
      gb1.insets = nullIndent;
    }

    myPanel.revalidate();
    myPanel.repaint();
  }

  private String formatWc(WCInfo info) {
    final StringBuilder sb = new StringBuilder().append("<html><head>").append(UIUtil.getCssFontDeclaration(UIUtil.getLabelFont()))
      .append("</head><body><table bgColor=\"").append(ColorUtil.toHex(UIUtil.getPanelBackground())).append("\">");

    sb.append("<tr valign=\"top\"><td colspan=\"3\"><b>").append(info.getPath()).append("</b></td></tr>");
    sb.append("<tr valign=\"top\"><td>URL:</td><td colspan=\"2\">").append(info.getRootUrl()).append("</td></tr>");
    if (! WorkingCopyFormat.ONE_DOT_SEVEN.equals(info.getFormat())) {
      // can convert
      sb.append("<tr valign=\"top\"><td>Format:</td><td>").append(info.getFormat().getName()).append("</td><td><a href=\"").
        append(CHANGE_FORMAT).append("\">Change</a></td></tr>");
    } else {
      sb.append("<tr valign=\"top\"><td>Format:</td><td colspan=\"2\">").append(info.getFormat().getName()).append("</td></tr>");
    }

    if (! SVNDepth.INFINITY.equals(info.getStickyDepth())) {
      // can fix
      sb.append("<tr valign=\"top\"><td>Depth:</td><td>").append(info.getStickyDepth().getName()).append("</td><td><a href=\"").
        append(FIX_DEPTH).append("\">Fix</a></td></tr>");
    } else {
      sb.append("<tr valign=\"top\"><td>Depth:</td><td colspan=\"2\">").append(info.getStickyDepth().getName()).append("</td></tr>");
    }

    final NestedCopyType type = info.getType();
    if (NestedCopyType.external.equals(type) || NestedCopyType.switched.equals(type)) {
      sb.append("<tr valign=\"top\"><td colspan=\"3\"><i>").append(type.getName()).append("</i></td></tr>");
    }
    if (info.isIsWcRoot()) {
      sb.append("<tr valign=\"top\"><td colspan=\"3\"><i>").append("Working copy root</i></td></tr>");
    }
    if (WorkingCopyFormat.ONE_DOT_SEVEN.equals(info.getFormat())) {
      sb.append("<tr valign=\"top\"><td colspan=\"3\"><a href=\"").append(CLEANUP).append("\">Cleanup</a></td></tr>");
    }
    sb.append("<tr valign=\"top\"><td colspan=\"3\"><a href=\"").append(CONFIGURE_BRANCHES).append("\">Configure Branches</a></td></tr>");
    sb.append("<tr valign=\"top\"><td colspan=\"3\"><a href=\"").append(MERGE_FROM).append("\"><b>Merge From...</b></a></i></td></tr>");

    sb.append("</table></body></html>");
    return sb.toString();
  }

  private void mergeFrom(final WCInfo wcInfo, final VirtualFile root, final Component mergeLabel) {
    SelectBranchPopup.showForBranchRoot(myProject, root, new SelectBranchPopup.BranchSelectedCallback() {
      public void branchSelected(Project project, SvnBranchConfigurationNew configuration, String url, long revision) {
        new QuickMerge(project, url, wcInfo, SVNPathUtil.tail(url), root).execute();
      }
    }, "Select branch", mergeLabel);
  }

  private void setFocusableForLinks(final LinkLabel label) {
    final Border border = new DottedBorder(new Insets(1,2,1,1), Color.black);
    label.setFocusable(true);
    label.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        super.focusGained(e);
        label.setBorder(border);
      }

      @Override
      public void focusLost(FocusEvent e) {
        super.focusLost(e);
        label.setBorder(null);
      }
    });
    label.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (KeyEvent.VK_ENTER == e.getKeyCode()) {
          label.doClick();
        }
      }
    });
  }

  private void changeFormat(final WCInfo wcInfo) {
    ChangeFormatDialog dialog = new ChangeFormatDialog(myProject, new File(wcInfo.getPath()), false, ! wcInfo.isIsWcRoot());
    dialog.setData(true, wcInfo.getFormat().getOption());
    dialog.show();
    if (! dialog.isOK()) {
      return;
    }
    final String newMode = dialog.getUpgradeMode();
    if (! wcInfo.getFormat().getOption().equals(newMode)) {
      final WorkingCopyFormat newFormat = WorkingCopyFormat.getInstance(newMode);
      ApplicationManager.getApplication().saveAll();
      final Task.Backgroundable task = new SvnFormatWorker(myProject, newFormat, wcInfo) {
        @Override
        public void onSuccess() {
          super.onSuccess();
          myRefreshLabel.doClick();
        }
      };
      ProgressManager.getInstance().run(task);
    }
  }

  private void initView() {
    myRefreshLabel.doClick();
  }

  public JComponent getComponent() {
    return myHolder;
  }

  public static class OverrideEqualsWrapper<T> {
    private final EqualityPolicy<T> myPolicy;
    private final T myT;

    public OverrideEqualsWrapper(EqualityPolicy<T> policy, T t) {
      myPolicy = policy;
      myT = t;
    }

    public T getT() {
      return myT;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final OverrideEqualsWrapper<T> that = (OverrideEqualsWrapper<T>) o;

      return myPolicy.isEqual(myT, that.getT());
    }

    @Override
    public int hashCode() {
      return myPolicy.getHashCode(myT);
    }
  }

  private static class InfoEqualityPolicy implements EqualityPolicy<WCInfo> {
    private final static InfoEqualityPolicy ourInstance = new InfoEqualityPolicy();

    public static InfoEqualityPolicy getInstance() {
      return ourInstance;
    }

    private static class HashCodeBuilder {
      private int myCode;

      private HashCodeBuilder() {
        myCode = 0;
      }

      public void append(final Object o) {
        myCode = 31 * myCode + (o != null ? o.hashCode() : 0);
      }

      public int getCode() {
        return myCode;
      }
    }

    public int getHashCode(WCInfo value) {
      final HashCodeBuilder builder = new HashCodeBuilder();
      builder.append(value.getPath());
      builder.append(value.getUrl());
      builder.append(value.getFormat());
      builder.append(value.getType());
      builder.append(value.getStickyDepth());

      return builder.getCode();
    }

    public boolean isEqual(WCInfo val1, WCInfo val2) {
      if (val1 == val2) return true;
      if (val1 == null || val2 == null || val1.getClass() != val2.getClass()) return false;

      if (! Comparing.equal(val1.getFormat(), val2.getFormat())) return false;
      if (! Comparing.equal(val1.getPath(), val2.getPath())) return false;
      if (! Comparing.equal(val1.getStickyDepth(), val2.getStickyDepth())) return false;
      if (! Comparing.equal(val1.getType(), val2.getType())) return false;
      if (! Comparing.equal(val1.getUrl(), val2.getUrl())) return false;

      return true;
    }
  }

  private static class WCComparator implements Comparator<WCInfo> {
    private final static WCComparator ourComparator = new WCComparator();

    public static WCComparator getInstance() {
      return ourComparator;
    }

    public int compare(WCInfo o1, WCInfo o2) {
      return o1.getPath().compareTo(o2.getPath());
    }
  }

  private static class MyLinkLabel extends LinkLabel {
    private final int myHeight;

    public MyLinkLabel(final int height, final String text, final LinkListener linkListener) {
      super(text, null, linkListener);
      myHeight = height;
    }

    @Override
    public Dimension getPreferredSize() {
      final Dimension preferredSize = super.getPreferredSize();
      return new Dimension(preferredSize.width, myHeight);
    }
  }
}
