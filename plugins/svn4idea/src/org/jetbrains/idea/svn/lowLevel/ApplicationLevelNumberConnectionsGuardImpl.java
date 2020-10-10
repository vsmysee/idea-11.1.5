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
package org.jetbrains.idea.svn.lowLevel;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.containers.hash.HashSet;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 7/31/12
 * Time: 6:19 PM
 */
public class ApplicationLevelNumberConnectionsGuardImpl implements Disposable, ApplicationLevelNumberConnectionsGuard {
  public static final int DELAY = 20000;

  private final Object myLock;
  private final Set<CachingSvnRepositoryPool> mySet;
  private int myCurrentlyActiveConnections;
  private boolean myDisposed;

  private int myInstanceCount;  // refreshable instances
  private final ScheduledExecutorService myService;
  private ScheduledFuture<?> myFuture;
  private final Runnable myRecheck;
  private int myDelay;

  public ApplicationLevelNumberConnectionsGuardImpl() {
    myDelay = DELAY;
    mySet = new HashSet<CachingSvnRepositoryPool>();
    myService = Executors.newSingleThreadScheduledExecutor();
    myLock = new Object();
    myDisposed = false;
    myRecheck = new Runnable() {
      @Override
      public void run() {
        HashSet<CachingSvnRepositoryPool> pools = new HashSet<CachingSvnRepositoryPool>();
        synchronized (myLock) {
          pools.addAll(mySet);
        }
        for (CachingSvnRepositoryPool pool : pools) {
          pool.check();
        }
      }
    };
  }

  public void setDelay(int delay) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    myDelay = delay;
    myFuture.cancel(true);
    myFuture = myService.scheduleWithFixedDelay(myRecheck, myDelay, myDelay, TimeUnit.MILLISECONDS);
  }

  public int getCurrentlyActiveConnections() {
    synchronized (myLock) {
      assert ApplicationManager.getApplication().isUnitTestMode();
      return myCurrentlyActiveConnections;
    }
  }

  public int getInstanceCount() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    return myInstanceCount;
  }

  public void addRepositoryPool(final CachingSvnRepositoryPool pool) {
    synchronized (myLock) {
      mySet.add(pool);
      ++ myInstanceCount;
      if (myFuture == null) {
        myFuture = myService.scheduleWithFixedDelay(myRecheck, myDelay, myDelay, TimeUnit.MILLISECONDS);
      }
    }
  }

  public void removeRepositoryPool(final CachingSvnRepositoryPool pool) {
    synchronized (myLock) {
      mySet.remove(pool);
      -- myInstanceCount;
      if (myInstanceCount == 0) {
        myFuture.cancel(true);
        myFuture = null;
      }
    }
  }

  public void connectionOpened() {
    synchronized (myLock) {
      ++ myCurrentlyActiveConnections;
    }
  }

  public void connectionClosed() {
    synchronized (myLock) {
      -- myCurrentlyActiveConnections;
      myLock.notifyAll();
    }
  }

  @Override
  public void waitForTotalNumberOfConnectionsOk() throws SVNException {
    synchronized (myLock) {
      if (myCurrentlyActiveConnections >= CachingSvnRepositoryPool.ourMaxTotal) {
        waitForFreeConnections();
        return;
      }
      int cntTotal = getTotalRepositories();
      if (cntTotal >= CachingSvnRepositoryPool.ourMaxTotal) {
        for (CachingSvnRepositoryPool pool : mySet) {
          pool.closeInactive();
        }
        if (myCurrentlyActiveConnections >= CachingSvnRepositoryPool.ourMaxTotal) {
          waitForFreeConnections();
        }
      }
    }
  }

  private void waitForFreeConnections() throws SVNException {
    synchronized (myLock) {
      while (myCurrentlyActiveConnections >= CachingSvnRepositoryPool.ourMaxTotal && ! myDisposed) {
        try {
          myLock.wait(500);
        }
        catch (InterruptedException e) {
          //
        }
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator != null && indicator.isCanceled()) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.CANCELLED));
        }
      }
    }
  }

  @Override
  public boolean shouldKeepConnectionLocally() {
    synchronized (myLock) {
      if (myCurrentlyActiveConnections > CachingSvnRepositoryPool.ourMaxTotal) return false;
      int cntTotal = getTotalRepositories();
      return cntTotal <= CachingSvnRepositoryPool.ourMaxTotal;
    }
  }

  private int getTotalRepositories() {
    synchronized (myLock) {
      int cntTotal = myCurrentlyActiveConnections;
      for (CachingSvnRepositoryPool pool : mySet) {
        cntTotal += pool.getNumberInactiveConnections();
      }
      return cntTotal;
    }
  }

  @Override
  public void dispose() {
    synchronized (myLock) {
      myDisposed = true;
      myLock.notifyAll();
    }
  }
}
