package org.jetbrains.plugins.gradle.util;

import com.intellij.execution.rmi.RemoteUtil;
import com.intellij.ide.actions.OpenProjectFileChooserDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileTypeDescriptor;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.WeakHashMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.config.GradleSettings;
import org.jetbrains.plugins.gradle.model.gradle.GradleProject;
import org.jetbrains.plugins.gradle.model.id.GradleEntityId;
import org.jetbrains.plugins.gradle.model.id.GradleSyntheticId;
import org.jetbrains.plugins.gradle.model.intellij.IntellijEntityVisitor;
import org.jetbrains.plugins.gradle.model.intellij.ModuleAwareContentRoot;
import org.jetbrains.plugins.gradle.remote.GradleApiException;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureTreeModel;
import org.jetbrains.plugins.gradle.task.GradleResolveProjectTask;
import org.jetbrains.plugins.gradle.ui.*;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Holds miscellaneous utility methods.
 * 
 * @author Denis Zhdanov
 * @since 8/25/11 1:19 PM
 */
public class GradleUtil {

  public static final String PATH_SEPARATOR = "/";

  private static final Map<Project, List<Balloon>>            PROJECT_BALLOONS     = new WeakHashMap<Project, List<Balloon>>();
  private static final List<Balloon>                          APPLICATION_BALLOONS = new CopyOnWriteArrayList<Balloon>();
  private static final NotNullLazyValue<GradleLibraryManager> LIBRARY_MANAGER      = new NotNullLazyValue<GradleLibraryManager>() {
    @NotNull
    @Override
    protected GradleLibraryManager compute() {
      return ServiceManager.getService(GradleLibraryManager.class);
    }
  };

  private GradleUtil() {
  }

  /**
   * Allows to retrieve file chooser descriptor that filters gradle scripts.
   * <p/>
   * <b>Note:</b> we want to fall back to the standard {@link FileTypeDescriptor} when dedicated gradle file type
   * is introduced (it's processed as groovy file at the moment). We use open project descriptor here in order to show
   * custom gradle icon at the file chooser ({@link GradleIcons#GRADLE_ICON}, is used at the file chooser dialog via 
   * the dedicated gradle project open processor).
   */
  public static FileChooserDescriptor getFileChooserDescriptor() {
    return DescriptorHolder.GRADLE_BUILD_FILE_CHOOSER_DESCRIPTOR;
  }

  /**
   * @param path    target path
   * @return        absolute path that points to the same location as the given one and that uses only slashes
   */
  @NotNull
  public static String toCanonicalPath(@NotNull String path) {
    return PathUtil.getCanonicalPath(new File(path).getAbsolutePath());
  }

  /**
   * Asks to show balloon that contains information related to the given component.
   *  
   * @param component    component for which we want to show information
   * @param messageType  balloon message type
   * @param message      message to show
   */
  public static void showBalloon(@NotNull JComponent component, @NotNull MessageType messageType, @NotNull String message) {
    final BalloonBuilder delegate = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(message, messageType, null);
    BalloonBuilder balloonBuilder = new GradleBalloonBuilder(delegate, APPLICATION_BALLOONS);
    ApplicationBalloonsDisposeActivator.ensureActivated();
    Balloon balloon = balloonBuilder.setFadeoutTime(TimeUnit.SECONDS.toMillis(1)).createBalloon();
    Dimension size = component.getSize();
    Balloon.Position position;
    int x;
    int y;
    if (size == null) {
      x = y = 0;
      position = Balloon.Position.above;
    }
    else {
      x = Math.min(10, size.width / 2);
      y = size.height;
      position = Balloon.Position.below;
    }
    balloon.show(new RelativePoint(component, new Point(x, y)), position);
  }

  /**
   * Delegates to the {@link #refreshProject(Project, String, Ref, Ref, boolean, boolean)} with the following defaults:
   * <pre>
   * <ul>
   *   <li>target gradle project path is retrieved from the {@link GradleSettings gradle settings} associated with the given project;</li>
   *   <li>refresh process is run in background;</li>
   *   <li>any problem occurred during the refresh is reported to the {@link GradleLog#LOG};</li>
   * </ul>
   * </pre>
   * 
   * @param project  target intellij project to use
   */
  public static void refreshProject(@NotNull Project project) {
    final GradleSettings settings = GradleSettings.getInstance(project);
    final String linkedProjectPath = settings.getLinkedProjectPath();
    if (StringUtil.isEmpty(linkedProjectPath)) {
      return;
    }
    assert linkedProjectPath != null;
    Ref<String> errorMessageHolder = new Ref<String>();
    Ref<String> errorDetailsHolder = new Ref<String>();
    refreshProject(project, linkedProjectPath, errorMessageHolder, errorDetailsHolder, true, false);
    final String error = errorDetailsHolder.get();
    if (!StringUtil.isEmpty(error)) {
      GradleLog.LOG.warn(error);
    }
  }

  /**
   * {@link RemoteUtil#unwrap(Throwable) unwraps} given exception if possible and builds error message for it.
   *
   * @param e  exception to process
   * @return   error message for the given exception
   */
  @SuppressWarnings({"ThrowableResultOfMethodCallIgnored", "IOResourceOpenedButNotSafelyClosed"})
  @NotNull
  public static String buildErrorMessage(@NotNull Throwable e) {
    Throwable unwrapped = RemoteUtil.unwrap(e);
    String reason = unwrapped.getLocalizedMessage();
    if (!StringUtil.isEmpty(reason)) {
      return reason;
    }
    else if (unwrapped.getClass() == GradleApiException.class) {
      return String.format("gradle api threw an exception: %s", ((GradleApiException)unwrapped).getOriginalReason());
    }
    else {
      StringWriter writer = new StringWriter();
      unwrapped.printStackTrace(new PrintWriter(writer));
      return writer.toString();
    }
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  @Nullable
  private static String extractDetails(@NotNull Throwable e) {
    final Throwable unwrapped = RemoteUtil.unwrap(e);
    if (unwrapped instanceof GradleApiException) {
      return ((GradleApiException)unwrapped).getOriginalReason();
    }
    return null;
  }
  
  /**
   * Queries slave gradle process to refresh target gradle project.
   * 
   * @param project            target intellij project to use
   * @param gradleProjectPath  path of the target gradle project's file
   * @param errorMessageHolder holder for the error message that describes a problem occurred during the refresh (if any)
   * @param errorDetailsHolder holder for the error details of the problem occurred during the refresh (if any)
   * @param resolveLibraries   flag that identifies whether gradle libraries should be resolved during the refresh
   * @return                   the most up-to-date gradle project (if any)
   */
  @Nullable
  public static GradleProject refreshProject(@NotNull final Project project,
                                             @NotNull final String gradleProjectPath,
                                             @NotNull final Ref<String> errorMessageHolder,
                                             @NotNull final Ref<String> errorDetailsHolder,
                                             final boolean resolveLibraries,
                                             final boolean modal)
  {
    final Ref<GradleProject> gradleProject = new Ref<GradleProject>();
    final TaskUnderProgress task = new TaskUnderProgress() {
      @SuppressWarnings({"ThrowableResultOfMethodCallIgnored", "IOResourceOpenedButNotSafelyClosed"})
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        GradleResolveProjectTask task = new GradleResolveProjectTask(project, gradleProjectPath, resolveLibraries);
        task.execute(indicator);
        gradleProject.set(task.getGradleProject());
        final Throwable error = task.getError();
        if (error == null) {
          return;
        }
        final String message = buildErrorMessage(error);
        if (StringUtil.isEmpty(message)) {
          errorMessageHolder.set(String.format("Can't resolve gradle project at '%s'. Reason: %s", gradleProjectPath, message));
        }
        else {
          errorMessageHolder.set(message);
        }
        errorDetailsHolder.set(extractDetails(error));
      }
    };
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (modal) {
          ProgressManager.getInstance().run(new Task.Modal(project, GradleBundle.message("gradle.import.progress.text"), false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              task.execute(indicator);
            }
          });
        }
        else {
          ProgressManager.getInstance().run(new Task.Backgroundable(project, GradleBundle.message("gradle.sync.progress.text")) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              task.execute(indicator);
            }
          });
        }
      }
    });
    return gradleProject.get();
  }
  
  /**
   * Tries to dispatch given entity via the given visitor.
   * 
   * @param entity   intellij project entity candidate to dispatch
   * @param visitor  dispatch callback to use for the given entity
   */
  public static void dispatch(@Nullable Object entity, @NotNull IntellijEntityVisitor visitor) {
    if (entity instanceof Project) {
      visitor.visit(((Project)entity));
    }
    else if (entity instanceof Module) {
      visitor.visit(((Module)entity));
    }
    else if (entity instanceof ModuleAwareContentRoot) {
      visitor.visit(((ModuleAwareContentRoot)entity));
    }
    else if (entity instanceof LibraryOrderEntry) {
      visitor.visit(((LibraryOrderEntry)entity));
    }
    else if (entity instanceof ModuleOrderEntry) {
      visitor.visit(((ModuleOrderEntry)entity));
    }
    else if (entity instanceof Library) {
      visitor.visit(((Library)entity));
    }
  }

  @NotNull
  public static <T extends GradleEntityId> GradleProjectStructureNodeDescriptor<T> buildDescriptor(@NotNull T id, @NotNull String name) {
    return new GradleProjectStructureNodeDescriptor<T>(id, name, id.getType().getIcon());
  }
  
  @NotNull
  public static GradleProjectStructureNodeDescriptor<GradleSyntheticId> buildSyntheticDescriptor(@NotNull String text) {
    return buildSyntheticDescriptor(text, null);
  }
  
  public static GradleProjectStructureNodeDescriptor<GradleSyntheticId> buildSyntheticDescriptor(@NotNull String text, @Nullable Icon icon) {
    return new GradleProjectStructureNodeDescriptor<GradleSyntheticId>(new GradleSyntheticId(text), text, icon);
  }

  @NotNull
  public static String getLocalFileSystemPath(@NotNull VirtualFile file) {
    if (file.getFileType() == FileTypes.ARCHIVE) {
      final VirtualFile jar = JarFileSystem.getInstance().getVirtualFileForJar(file);
      if (jar != null) {
        return jar.getPath();
      }
    }
    return file.getPath();
  }

  /**
   * Tries to calculate the position to use for showing hint for the given node of the given tree.
   * 
   * @param node  target node for which a hint should be shown
   * @param tree  target tree that contains given node
   * @return      preferred hint position (in coordinates relative to the given tree) if it's possible to calculate the one;
   *              <code>null</code> otherwise
   */
  @Nullable
  public static Point getHintPosition(@NotNull GradleProjectStructureNode<?> node, @NotNull Tree tree) {
    final Rectangle bounds = tree.getPathBounds(new TreePath(node.getPath()));
    if (bounds == null) {
      return null;
    }
    final Icon icon = ((GradleProjectStructureNode)node).getDescriptor().getOpenIcon();
    int xAdjustment = 0;
    if (icon != null) {
      xAdjustment = icon.getIconWidth();
    }
    return new Point(bounds.x + xAdjustment, bounds.y + bounds.height);
  }

  @NotNull
  public static String getLibraryName(@NotNull Library library) {
    final String result = library.getName();
    if (result != null) {
      return result;
    }
    String[] endingsToStrip = { "/", "!", ".jar" };
    StringBuilder buffer = new StringBuilder();
    for (OrderRootType type : OrderRootType.getAllTypes()) {
      for (String url : library.getUrls(type)) {
        buffer.setLength(0);
        buffer.append(url);
        for (String ending : endingsToStrip) {
          if (buffer.lastIndexOf(ending) == buffer.length() - ending.length()) {
            buffer.setLength(buffer.length() - ending.length());
          }
        }
        final int i = buffer.lastIndexOf(PATH_SEPARATOR);
        if (i < 0 || i >= buffer.length() - 1) {
          continue;
        }
        String candidate = buffer.substring(i + 1);
        if (!StringUtil.isEmpty(candidate)) {
          return candidate;
        }
      }
    }
    assert false;
    return "unknown-lib";
  }

  /**
   * Tries to find the current {@link GradleProjectStructureTreeModel} instance.
   * 
   * @param context  target context (if defined)
   * @return         current {@link GradleProjectStructureTreeModel} instance (if any has been found); <code>null</code> otherwise
   */
  @Nullable
  public static GradleProjectStructureTreeModel getProjectStructureTreeModel(@Nullable DataContext context) {
    if (context != null) {
      final GradleProjectStructureTreeModel model = GradleDataKeys.SYNC_TREE_MODEL.getData(context);
      if (model != null) {
        return model;
      }
    }

    if (context == null) {
      return null;
    }
    
    final Project project = PlatformDataKeys.PROJECT.getData(context);
    if (project == null) {
      return null;
    }

    return getProjectStructureTreeModel(project);
  }

  @Nullable
  public static GradleProjectStructureTreeModel getProjectStructureTreeModel(@NotNull Project project) {
    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    final ToolWindow toolWindow = toolWindowManager.getToolWindow(GradleConstants.TOOL_WINDOW_ID);
    if (toolWindow == null) {
      return null;
    }

    final ContentManager contentManager = toolWindow.getContentManager();
    if (contentManager == null) {
      return null;
    }

    for (Content content : contentManager.getContents()) {
      final JComponent component = content.getComponent();
      if (component instanceof DataProvider) {
        final Object data = ((DataProvider)component).getData(GradleDataKeys.SYNC_TREE_MODEL.getName());
        if (data instanceof GradleProjectStructureTreeModel) {
          return (GradleProjectStructureTreeModel)data;
        }
      }
    }
    return null;
  }

  /**
   * @return    {@link MatrixControlBuilder} with predefined set of columns ('gradle' and 'intellij')
   */
  @NotNull
  public static MatrixControlBuilder getConflictChangeBuilder() {
    final String gradle = GradleBundle.message("gradle.name");
    final String intellij = GradleBundle.message("gradle.ide");
    return new MatrixControlBuilder(gradle, intellij);
  }
  
  /**
   * Wraps {@link JBPopupFactory#createBalloonBuilder(JComponent) default api} in order to take care of automatic balloon disposing
   * on project close.
   * 
   * @param content  target balloon content
   * @param project  project that should be used to bound target balloon's lifecycle to 
   * @return         balloon builder to use.
   */
  @NotNull
  public static BalloonBuilder getBalloonBuilder(@NotNull JComponent content, @NotNull final Project project) {
    List<Balloon> balloons = PROJECT_BALLOONS.get(project);
    if (balloons == null) {
      PROJECT_BALLOONS.put(project, balloons = new CopyOnWriteArrayList<Balloon>());
      final List<Balloon> b = balloons;
      Disposer.register(project, new Disposable() {
        @Override
        public void dispose() {
          for (Balloon balloon : b) {
            if (!balloon.isDisposed()) {
              Disposer.dispose(balloon);
            }
          }
          PROJECT_BALLOONS.remove(project);
        }
      });
    }
    return new GradleBalloonBuilder(JBPopupFactory.getInstance().createBalloonBuilder(content), balloons);
  }

  public static boolean isGradleAvailable() {
    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    final Project project;
    if (projects.length == 1) {
      project = projects[0];
    }
    else {
      project = null;
    }
    return isGradleAvailable(project);
  }
  
  public static boolean isGradleAvailable(@Nullable Project project) {
    return LIBRARY_MANAGER.getValue().getGradleHome(project) != null;
  }
  
  private interface TaskUnderProgress {
    void execute(@NotNull ProgressIndicator indicator);
  }
  
  /**
   * We use this class in order to avoid static initialisation of the wrapped object - it loads number of pico container-based
   * dependencies that are unavailable to the slave gradle project, so, we don't want to get unexpected NPE there.
   */
  private static class DescriptorHolder {
    public static final FileChooserDescriptor GRADLE_BUILD_FILE_CHOOSER_DESCRIPTOR = new OpenProjectFileChooserDescriptor(true) {
      @Override
      public boolean isFileSelectable(VirtualFile file) {
        return GradleConstants.DEFAULT_SCRIPT_NAME.equals(file.getName());
      }

      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        if (!super.isFileVisible(file, showHiddenFiles)) {
          return false;
        }
        return file.isDirectory() || GradleConstants.DEFAULT_SCRIPT_NAME.equals(file.getName());
      }
    };
  }

  /**
   * Serves the same purpose as the {@link DescriptorHolder} but for application-level balloons releasing.
   */
  private static class ApplicationBalloonsDisposeActivator {
    static {
      Disposer.register(ApplicationManager.getApplication(), new Disposable() {
        @Override
        public void dispose() {
          for (Balloon balloon : APPLICATION_BALLOONS) {
            if (!balloon.isDisposed()) {
              Disposer.dispose(balloon);
            }
          }
        }
      });
    }
    static void ensureActivated() { /* the real job is at the static init block. */ }
  }
}
