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
package com.intellij.ide.util.gotoByName;

import com.intellij.concurrency.JobUtil;
import com.intellij.ide.util.NavigationItemListCellRenderer;
import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ConcurrentHashSet;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import java.util.*;

/**
 * Contributor-based goto model
 */
public abstract class ContributorsBasedGotoByModel implements ChooseByNameModel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.gotoByName.ContributorsBasedGotoByModel");

  protected final Project myProject;
  private final ChooseByNameContributor[] myContributors;

  protected ContributorsBasedGotoByModel(Project project, ChooseByNameContributor[] contributors) {
    myProject = project;
    myContributors = contributors;
  }

  public ListCellRenderer getListCellRenderer() {
    return new NavigationItemListCellRenderer();
  }

  public String[] getNames(final boolean checkBoxState) {
    final Set<String> names = new ConcurrentHashSet<String>();

    JobUtil.invokeConcurrentlyUnderProgress(filterDumb(myContributors), ProgressManager.getInstance().getProgressIndicator(), false, new Processor<ChooseByNameContributor>() {
      @Override
      public boolean process(ChooseByNameContributor contributor) {
        try {
          if (!myProject.isDisposed()) {
            ContainerUtil.addAll(names, contributor.getNames(myProject, checkBoxState));
          }
        }
        catch (ProcessCanceledException ex) {
          // index corruption detected, ignore
        }
        catch (IndexNotReadyException ex) {
          // index corruption detected, ignore
        }
        catch (Exception ex) {
          LOG.error(ex);
        }
        return true;
      }
    });

    return ArrayUtil.toStringArray(names);
  }

  private List<ChooseByNameContributor> filterDumb(ChooseByNameContributor[] contributors) {
    if (!DumbService.getInstance(myProject).isDumb()) return Arrays.asList(contributors);
    List<ChooseByNameContributor> answer = new ArrayList<ChooseByNameContributor>(contributors.length);
    for (ChooseByNameContributor contributor : contributors) {
      if (DumbService.isDumbAware(contributor)) {
        answer.add(contributor);
      }
    }

    return answer;
  }

  /**
   * Get elements by name from contributors.
   *
   * @param name a name
   * @param checkBoxState if true, non-project files are considered as well
   * @param pattern a pattern to use
   * @return a list of navigation items from contributors for
   *  which {@link #acceptItem(NavigationItem) returns true.
   *
   */
  public Object[] getElementsByName(final String name, final boolean checkBoxState, final String pattern) {
    final List<NavigationItem> items = Collections.synchronizedList(new ArrayList<NavigationItem>());

    JobUtil.invokeConcurrentlyUnderProgress(filterDumb(myContributors), ProgressManager.getInstance().getProgressIndicator(), false, new Processor<ChooseByNameContributor>() {
      @Override
      public boolean process(ChooseByNameContributor contributor) {
        try {
          for (NavigationItem item : contributor.getItemsByName(name, pattern, myProject, checkBoxState)) {
            if (item == null) {
              LOG.error("null item from contributor " + contributor + " for name " + name);
              continue;
            }

            if (acceptItem(item)) {
              items.add(item);
            }
          }
        }
        catch (ProcessCanceledException ex) {
          // index corruption detected, ignore
        }
        catch (Exception ex) {
          LOG.error(ex);
        }
        return true;
      }
    });
    
    return ArrayUtil.toObjectArray(items);
  }

  public String getElementName(Object element) {
    return ((NavigationItem)element).getName();
  }

  public String getHelpId() {
    return null;
  }

  protected ChooseByNameContributor[] getContributors() {
    return myContributors;
  }

  /**
   * This method allows extending classes to introduce additional filtering criteria to model
   * beyond pattern and project/non-project files. The default implementation just returns true.
   *
   * @param item an item to filter
   * @return true if the item is acceptable according to additional filtering criteria.
   */
  protected boolean acceptItem(NavigationItem item) {
    return true;
  }
}
