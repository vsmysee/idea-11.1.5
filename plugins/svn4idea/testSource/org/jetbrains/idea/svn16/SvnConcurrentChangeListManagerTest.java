package org.jetbrains.idea.svn16;

import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.changes.pending.DuringChangeListManagerUpdateTestScheme;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Test;

import java.util.Collection;

public class SvnConcurrentChangeListManagerTest extends SvnTestCase {
  private DuringChangeListManagerUpdateTestScheme myScheme;
  private String myDefaulListName;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myDefaulListName = VcsBundle.message("changes.default.changlist.name");

    myScheme = new DuringChangeListManagerUpdateTestScheme(myProject, myTempDirFixture.getTempDirPath());
  }

  @Test
  public void testRenameList() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);

    final LocalChangeList list = changeListManager.addChangeList("test", null);
    changeListManager.moveChangesTo(list, new Change[] {changeListManager.getChange(file)});

    final String newName = "renamed";

    myScheme.doTest(new Runnable() {
      @Override
      public void run() {
        changeListManager.editName(list.getName(), newName);
        checkFilesAreInList(new VirtualFile[] {file}, newName, changeListManager);
      }
    });

    checkFilesAreInList(new VirtualFile[] {file}, newName, changeListManager);

    changeListManager.ensureUpToDate(false);
    checkFilesAreInList(new VirtualFile[] {file}, newName, changeListManager);
  }

  @Test
  public void testEditComment() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);

    final String listName = "test";
    final LocalChangeList list = changeListManager.addChangeList(listName, null);
    changeListManager.moveChangesTo(list, new Change[] {changeListManager.getChange(file)});

    final String finalText = "final text";

    myScheme.doTest(new Runnable() {
      @Override
      public void run() {
        final String intermediate = "intermediate text";
        changeListManager.editComment(list.getName(), intermediate);
        assert changeListManager.findChangeList(listName) != null;
        LocalChangeList list = changeListManager.findChangeList(listName);
        assert intermediate.equals(list.getComment());

        changeListManager.editComment(list.getName(), finalText);
        list = changeListManager.findChangeList(listName);
        assert finalText.equals(list.getComment());
      }
    });

    LocalChangeList changedList = changeListManager.findChangeList(listName);
    assert finalText.equals(changedList.getComment());

    changeListManager.ensureUpToDate(false);
    changedList = changeListManager.findChangeList(listName);
    assert finalText.equals(changedList.getComment());
  }

  @Test
  public void testMove() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);

    final LocalChangeList list = changeListManager.addChangeList("test", null);
    final LocalChangeList target = changeListManager.addChangeList("target", null);
    changeListManager.moveChangesTo(list, new Change[] {changeListManager.getChange(file)});

    myScheme.doTest(new Runnable() {
      @Override
      public void run() {
        changeListManager.moveChangesTo(target, new Change[] {changeListManager.getChange(file)});
        checkFilesAreInList(new VirtualFile[] {file}, target.getName(), changeListManager);
      }
    });

    checkFilesAreInList(new VirtualFile[] {file}, target.getName(), changeListManager);

    changeListManager.ensureUpToDate(false);
    checkFilesAreInList(new VirtualFile[] {file}, target.getName(), changeListManager);
  }

  @Test
  public void testSetActive() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);

    final LocalChangeList list = changeListManager.addChangeList("test", null);
    final LocalChangeList target = changeListManager.addChangeList("target", null);
    changeListManager.moveChangesTo(list, new Change[] {changeListManager.getChange(file)});

    myScheme.doTest(new Runnable() {
      @Override
      public void run() {
        changeListManager.setDefaultChangeList(target);
        assert changeListManager.getDefaultChangeList().getName().equals(target.getName());
      }
    });

    assert changeListManager.getDefaultChangeList().getName().equals(target.getName());

    changeListManager.ensureUpToDate(false);
    assert changeListManager.getDefaultChangeList().getName().equals(target.getName());
  }

  @Test
  public void testRemove() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    final VirtualFile fileB = createFileInCommand("b.txt", "old content");
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);

    final LocalChangeList list = changeListManager.addChangeList("test", null);
    changeListManager.moveChangesTo(list, new Change[] {changeListManager.getChange(file)});

    myScheme.doTest(new Runnable() {
      @Override
      public void run() {
        changeListManager.removeChangeList(list.getName());
        assert changeListManager.findChangeList(list.getName()) == null;
        checkFilesAreInList(new VirtualFile[] {file, fileB}, myDefaulListName, changeListManager);
      }
    });

    assert changeListManager.findChangeList(list.getName()) == null;
    checkFilesAreInList(new VirtualFile[] {file, fileB}, myDefaulListName, changeListManager);

    changeListManager.ensureUpToDate(false);
    assert changeListManager.findChangeList(list.getName()) == null;
    checkFilesAreInList(new VirtualFile[] {file, fileB}, myDefaulListName, changeListManager);
  }

  @Test
  public void testDoubleMove() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);

    final LocalChangeList list = changeListManager.addChangeList("test", null);
    final LocalChangeList target = changeListManager.addChangeList("target", null);
    final LocalChangeList target2 = changeListManager.addChangeList("target2", null);
    changeListManager.moveChangesTo(list, new Change[] {changeListManager.getChange(file)});

    myScheme.doTest(new Runnable() {
      @Override
      public void run() {
        changeListManager.moveChangesTo(target, new Change[] {changeListManager.getChange(file)});
        checkFilesAreInList(new VirtualFile[] {file}, target.getName(), changeListManager);
        changeListManager.moveChangesTo(target2, new Change[] {changeListManager.getChange(file)});
        checkFilesAreInList(new VirtualFile[] {file}, target2.getName(), changeListManager);
      }
    });

    checkFilesAreInList(new VirtualFile[] {file}, target2.getName(), changeListManager);

    changeListManager.ensureUpToDate(false);
    checkFilesAreInList(new VirtualFile[] {file}, target2.getName(), changeListManager);
  }

  @Test
  public void testDoubleMoveBack() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);

    final LocalChangeList list = changeListManager.addChangeList("test", null);
    final LocalChangeList target = changeListManager.addChangeList("target", null);
    changeListManager.moveChangesTo(list, new Change[] {changeListManager.getChange(file)});

    myScheme.doTest(new Runnable() {
      @Override
      public void run() {
        changeListManager.moveChangesTo(target, new Change[] {changeListManager.getChange(file)});
        checkFilesAreInList(new VirtualFile[] {file}, target.getName(), changeListManager);
        changeListManager.moveChangesTo(list, new Change[] {changeListManager.getChange(file)});
        checkFilesAreInList(new VirtualFile[] {file}, list.getName(), changeListManager);
      }
    });

    checkFilesAreInList(new VirtualFile[] {file}, list.getName(), changeListManager);

    changeListManager.ensureUpToDate(false);
    checkFilesAreInList(new VirtualFile[] {file}, list.getName(), changeListManager);
  }

  @Test
  public void testAddPlusMove() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);

    final LocalChangeList list = changeListManager.addChangeList("test", null);
    changeListManager.moveChangesTo(list, new Change[] {changeListManager.getChange(file)});

    final String targetName = "target";

    myScheme.doTest(new Runnable() {
      @Override
      public void run() {
        final LocalChangeList target = changeListManager.addChangeList(targetName, null);
        changeListManager.moveChangesTo(target, new Change[] {changeListManager.getChange(file)});
        checkFilesAreInList(new VirtualFile[] {file}, targetName, changeListManager);
      }
    });

    checkFilesAreInList(new VirtualFile[] {file}, targetName, changeListManager);

    changeListManager.ensureUpToDate(false);
    checkFilesAreInList(new VirtualFile[] {file}, targetName, changeListManager);
  }

  @Test
  public void testAddListBySvn() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);

    final String targetName = "target";
    // not parralel, just test of correct detection
    runSvn("changelist", targetName, file.getPath());

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);
    checkFilesAreInList(new VirtualFile[] {file}, targetName, changeListManager);
  }

  @Test
  public void testComplex() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    final VirtualFile fileB = createFileInCommand("b.txt", "old content");
    final VirtualFile fileC = createFileInCommand("c.txt", "old content");
    final VirtualFile fileD = createFileInCommand("d.txt", "old content");

    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);

    final LocalChangeList list = changeListManager.addChangeList("test", null);
    final LocalChangeList toBeDeletedList = changeListManager.addChangeList("toBeDeletedList", null);
    changeListManager.moveChangesTo(list, new Change[] {changeListManager.getChange(file), changeListManager.getChange(fileB)});
    changeListManager.moveChangesTo(toBeDeletedList, new Change[] {changeListManager.getChange(fileC), changeListManager.getChange(fileD)});
    changeListManager.ensureUpToDate(false);

    final String targetName = "target";
    final String finalName = "final list name";

    myScheme.doTest(new Runnable() {
      @Override
      public void run() {
        final LocalChangeList target = changeListManager.addChangeList(targetName, null);
        changeListManager.moveChangesTo(target, new Change[] {changeListManager.getChange(file), changeListManager.getChange(fileB)});
        checkFilesAreInList(new VirtualFile[] {file, fileB}, targetName, changeListManager);
        changeListManager.editName(targetName, finalName);
        checkFilesAreInList(new VirtualFile[] {file, fileB}, finalName, changeListManager);
        changeListManager.removeChangeList(toBeDeletedList.getName());
        checkFilesAreInList(new VirtualFile[] {fileC, fileD}, myDefaulListName, changeListManager);
        changeListManager.moveChangesTo(LocalChangeList.createEmptyChangeList(myProject, finalName),
                                        new Change[] {changeListManager.getChange(fileC)});
        checkFilesAreInList(new VirtualFile[] {file, fileB, fileC}, finalName, changeListManager);
        checkFilesAreInList(new VirtualFile[] {fileD}, myDefaulListName, changeListManager);
      }
    });

    checkFilesAreInList(new VirtualFile[] {file, fileB, fileC}, finalName, changeListManager);
    checkFilesAreInList(new VirtualFile[] {fileD}, myDefaulListName, changeListManager);

    changeListManager.ensureUpToDate(false);
    checkFilesAreInList(new VirtualFile[] {file, fileB, fileC}, finalName, changeListManager);
    checkFilesAreInList(new VirtualFile[] {fileD}, myDefaulListName, changeListManager);
  }

  private void checkFilesAreInList(final VirtualFile[] files, final String listName, final ChangeListManager manager) {
    System.out.println("Checking files for list: " + listName);
    assert manager.findChangeList(listName) != null;
    final Collection<Change> changes = manager.findChangeList(listName).getChanges();
    assert changes.size() == files.length;

    for (Change change : changes) {
      final VirtualFile vf = change.getAfterRevision().getFile().getVirtualFile();
      boolean found = false;
      for (VirtualFile file : files) {
        if (file.equals(vf)) {
          found = true;
          break;
        }
      }
      assert found == true;
    }
  }
}
