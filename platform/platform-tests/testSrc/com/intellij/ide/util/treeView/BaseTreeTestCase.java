package com.intellij.ide.util.treeView;

import com.intellij.openapi.util.*;
import com.intellij.testFramework.FlyIdeaTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.WaitFor;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.ComparableObject;
import com.intellij.util.ui.update.MergingUpdateQueue;
import junit.framework.Assert;
import junit.framework.AssertionFailedError;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

abstract class BaseTreeTestCase<StructureElement> extends FlyIdeaTestCase {

  private BaseTreeBuilder myBuilder;
  Tree myTree;

  Throwable myCancelRequest;
  boolean myReadyRequest;

  private boolean myYieldingUiBuild;
  private boolean myBgStructureBuilding;
  protected Set<Object> myForegroundLoadingNodes = new HashSet<Object>();

  private boolean myPassthroughMode;

  final Set<StructureElement> myAutoExpand = new HashSet<StructureElement>();
  final Set<StructureElement> myAlwaysShowPlus = new HashSet<StructureElement>();
  boolean mySmartExpand;
  private Thread myTestThread;
  protected Validator myValidator;

  protected BaseTreeTestCase(boolean passthrougth) {
    this(false, false);
    myPassthroughMode = passthrougth;
  }

  protected BaseTreeTestCase(boolean yieldingUiBuild, boolean bgStructureBuilding) {
    myYieldingUiBuild = yieldingUiBuild;
    myBgStructureBuilding = bgStructureBuilding;
  }

  void doAndWaitForBuilder(final Runnable runnable, final Condition condition) throws Exception {
    final Ref<Boolean> started = new Ref<Boolean>();
    invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        started.set(true);
        runnable.run();
      }
    });

    waitBuilderToCome(new Condition() {
      @Override
      public boolean value(Object o) {
        return started.get() && condition.value(null);
      }
    });
  }

  void waitBuilderToCome()  {
    try {
      waitBuilderToCome(Condition.TRUE);
    }
    catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  void waitBuilderToCome(final Condition<Object> condition) throws Exception {
    boolean success = new WaitFor(600000) {
      @Override
      protected boolean condition() {
        final boolean[] ready = new boolean[]{false};
        invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            AbstractTreeUi ui = getBuilder().getUi();
            if (ui == null) {
              ready[0] = true;
              return;
            }

            ready[0] = (myCancelRequest != null || myReadyRequest) || (condition.value(null) && (ui.isReady()));
          }
        });

        return ready[0];
      }
    }.isConditionRealized();

    if (myCancelRequest != null) {
      throw new Exception(myCancelRequest);
    }

    if (myCancelRequest == null && !myReadyRequest) {
      if (!getBuilder().isDisposed()) {
        Assert.assertTrue(getBuilder().getUi().getNodeActions().isEmpty());
      }
    }

    Assert.assertTrue(success);
  }

  void expand(final TreePath p) throws Exception {
    invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        myTree.expandPath(p);
      }
    });
    waitBuilderToCome();
  }

  void collapsePath(final TreePath p) throws Exception {
    invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        myTree.collapsePath(p);
      }
    });
    waitBuilderToCome();
  }

  AbstractTreeBuilder getBuilder() {
    return myBuilder;
  }

  protected void initBuilder(BaseTreeBuilder builder) {
    myBuilder = builder;
    myBuilder.setCanYieldUpdate(myYieldingUiBuild);
    myBuilder.setPassthroughMode(myPassthroughMode);
  }


  class BaseTreeBuilder extends AbstractTreeBuilder {
    volatile boolean myWasCleanedUp;

    public BaseTreeBuilder(JTree tree,
                           DefaultTreeModel treeModel,
                           AbstractTreeStructure treeStructure,
                           @Nullable Comparator<NodeDescriptor> comparator) {
      super(tree, treeModel, treeStructure, comparator);
    }

    public BaseTreeBuilder(JTree tree,
                           DefaultTreeModel treeModel,
                           AbstractTreeStructure treeStructure,
                           @Nullable Comparator<NodeDescriptor> comparator,
                           boolean updateIfInactive) {
      super(tree, treeModel, treeStructure, comparator, updateIfInactive);
    }

    public BaseTreeBuilder() {
    }


    @Override
    protected final boolean updateNodeDescriptor(NodeDescriptor descriptor) {
      checkThread(descriptor.getElement());

      int delay = getNodeDescriptorUpdateDelay();
      if (delay > 0) {
        try {
          Thread.currentThread().sleep(delay);
        }
        catch (InterruptedException e) {
          e.printStackTrace();
        }
      }

      if (getUi() == null) return false;

      return super.updateNodeDescriptor(descriptor);
    }

    @Override
    protected boolean isAutoExpandNode(final NodeDescriptor nodeDescriptor) {
      return myAutoExpand.contains(nodeDescriptor.getElement());
    }

    @Override
    protected boolean isSmartExpand() {
      return mySmartExpand;
    }


    @Override
    protected boolean isAlwaysShowPlus(NodeDescriptor descriptor) {
      return myAlwaysShowPlus.contains(descriptor.getElement());
    }

    @Override
    protected AbstractTreeUpdater createUpdater() {
      return _createUpdater(this);
    }

    @Override
    protected void updateAfterLoadedInBackground(Runnable runnable) {
      _updateAfterLoadedInBackground(runnable);
    }

    @Override
    protected void runBackgroundLoading(Runnable runnable) {
      _runBackgroundLoading(runnable);
    }

    @Override
    protected boolean validateNode(Object child) {
      return myValidator != null ? myValidator.isValid(child) : super.validateNode(child);
    }

    @Override
    public void cleanUp() {
      super.cleanUp();
      myWasCleanedUp = true;
    }


    @Override
    protected AbstractTreeUi createUi() {
      return _createUi();
    }
  }

  void _updateAfterLoadedInBackground(Runnable runnable) {
    SwingUtilities.invokeLater(runnable);
  }

  void _runBackgroundLoading(Runnable runnable) {
    try {
      Thread.currentThread().sleep(getChildrenLoadingDelay());
      runnable.run();
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }


  AbstractTreeUi _createUi() {
    return new AbstractTreeUi() {
      @Override
      protected void yield(Runnable runnable) {
        SimpleTimer.getInstance().setUp(runnable, 100);
      }

      @Override
      protected boolean canYield() {
        return myYieldingUiBuild;
      }

      @Override
      protected void runOnYieldingDone(Runnable onDone) {
        SwingUtilities.invokeLater(onDone);
      }
    };
  }

  AbstractTreeUpdater _createUpdater(AbstractTreeBuilder builder) {
    final AbstractTreeUpdater updater = new AbstractTreeUpdater(builder) {
      @Override
      protected void invokeLater(Runnable runnable) {
        runnable.run();
      }

      @Override
      protected boolean isEdt() {
        return SwingUtilities.isEventDispatchThread();
      }
    };
    updater.setModalityStateComponent(MergingUpdateQueue.ANY_COMPONENT);
    return updater;
  }

  abstract class BaseStructure extends AbstractTreeStructure {

    @Override
    public boolean isToBuildChildrenInBackground(Object element) {
      return myBgStructureBuilding && !myForegroundLoadingNodes.contains(element);
    }

    @Override
    public void commit() {
    }

    @Override
    public boolean hasSomethingToCommit() {
      return false;
    }

    @NotNull
    @Override
    public final NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
      return doCreateDescriptor(element, parentDescriptor);
    }

    @NotNull
    public abstract NodeDescriptor doCreateDescriptor(Object element, NodeDescriptor parentDescriptor);

    @Override
    public final Object[] getChildElements(Object element) {
      return _getChildElements(element, true);
    }

    public final Object[] _getChildElements(Object element, boolean checkThread) {
      if (checkThread) {
        checkThread(element);
      }

      return doGetChildElements(element);
    }

    public abstract Object[] doGetChildElements(Object element);
  }


  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myValidator = null;
    myCancelRequest = null;
    myReadyRequest = false;
    mySmartExpand = false;
    myAutoExpand.clear();
    myAlwaysShowPlus.clear();
    myForegroundLoadingNodes.clear();
    myTestThread = Thread.currentThread();
  }

  @Override
  protected void tearDown() throws Exception {
    invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (getBuilder() != null) {
          Disposer.dispose(getBuilder());
        }
      }
    });

    new WaitFor(6000) {
      @Override
      protected boolean condition() {
        return getBuilder() == null || getBuilder().getUi() == null;
      }
    };

    super.tearDown();
  }

  void assertTree(final String expected) throws Exception {
    waitBuilderToCome();
    assertTreeNow(expected);
  }

  void assertTreeNow(String expected) {
    Assert.assertEquals(expected, PlatformTestUtil.print(myTree, true));
  }

  void doAndWaitForBuilder(final Runnable runnable) throws Exception {
    doAndWaitForBuilder(runnable, Condition.TRUE);
  }


  void updateFromRoot() throws Exception {
    updateFromRoot(true);
  }

  void updateFromRoot(final boolean withStructure) throws Exception {
    doAndWaitForBuilder(new Runnable() {
      @Override
      public void run() {
        getBuilder().queueUpdate(withStructure);
      }
    });
  }

  void updateFrom(final NodeElement element) throws Exception {
    updateFrom(element, false);
  }

  void updateFrom(final NodeElement element, final boolean forceResort) throws Exception {
    doAndWaitForBuilder(new Runnable() {
      @Override
      public void run() {
        getBuilder().queueUpdateFrom(element, forceResort);
      }
    });
  }

  void showTree() throws Exception {
    doAndWaitForBuilder(new Runnable() {
      @Override
      public void run() {
        getBuilder().getUi().activate(true);
      }
    });
  }

  void select(final Object element, final boolean addToSelection) throws Exception {
    select(new Object[] {element}, addToSelection);
  }
  
  void select(final Object[] elements, final boolean addToSelection) throws Exception {
    select(elements, addToSelection, false);
  }

  void select(final Object[] elements, final boolean addToSelection, final boolean canBeInterrupted) throws Exception {
    final Ref<Boolean> done = new Ref<Boolean>(false);
    doAndWaitForBuilder(new Runnable() {
      @Override
      public void run() {
        getBuilder().select(elements, new Runnable() {
          @Override
          public void run() {
            done.set(true);
          }
        }, addToSelection);
      }
    }, new Condition() {
      @Override
      public boolean value(Object o) {
        return done.get() || (canBeInterrupted && getBuilder().getUi().isCancelledReady());
      }
    });
  }

  protected final boolean isYieldingUiBuild() {
    return myYieldingUiBuild;
  }

  protected final boolean isBgStructureBuilding() {
    return myBgStructureBuilding;
  }

  protected int getChildrenLoadingDelay() {
    return 0;
  }

  protected int getNodeDescriptorUpdateDelay() {
    return 0;
  }

  private void checkThread(@Nullable Object element) {
    String message = "Wrong thread used for query structure, thread=" + Thread.currentThread() + " element=" + element;

    if (!myPassthroughMode) {
      if (isBgStructureBuilding()) {
        if (myForegroundLoadingNodes.contains(element)) {
          Assert.assertTrue(message, EventQueue.isDispatchThread());
        } else {
          Assert.assertFalse(message, EventQueue.isDispatchThread());
        }
      } else {
        Assert.assertTrue(message, EventQueue.isDispatchThread());
      }

    }
  }

  protected final void invokeLaterIfNeeded(Runnable runnable) {
    if (myPassthroughMode) {
      runnable.run();
    } else {
      UIUtil.invokeLaterIfNeeded(runnable);
    }
  }

  protected final void invokeAndWaitIfNeeded(Runnable runnable) {
    if (myPassthroughMode) {
      runnable.run();
    } else {
      UIUtil.invokeAndWaitIfNeeded(runnable);
    }
  }

  protected final void assertEdt() {
    if (myPassthroughMode) {
      checkThread(null);
    } else if (!EventQueue.isDispatchThread()) {
      myCancelRequest = new AssertionFailedError("Must be event dispatch thread");
      throw new RuntimeException(myCancelRequest);
    }
  }

  public static class NodeElement extends ComparableObject.Impl implements Comparable<NodeElement>{

    String myName;
    private NodeElement myForcedParent;
    private String myPresentableName;

    public NodeElement(String name) {
      super(name);
      myName = name;
    }

    public String toString() {
      return myPresentableName != null ? myPresentableName : myName;
    }

    @Override
    public int compareTo(NodeElement o) {
      return myName.compareTo(o.myName);
    }

    public NodeElement getForcedParent() {
      return myForcedParent;
    }

    public void setForcedParent(NodeElement forcedParent) {
      myForcedParent = forcedParent;
    }

    public void setName(String name) {
      myName = name;
    }

    public void setPresentableName(String name) {
      myPresentableName = name;
    }
  }

  protected interface Validator {
    boolean isValid(Object element);
  }

}
