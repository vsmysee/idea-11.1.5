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
package com.intellij.execution.rmi;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.DefaultProgramRunner;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.rmi.PortableRemoteObject;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public abstract class RemoteProcessSupport<Target, EntryPoint, Parameters> {
  public static final Logger LOG = Logger.getInstance("#" + RemoteProcessSupport.class);

  private final Class<EntryPoint> myValueClass;
  private final HashMap<Pair<Target, Parameters>, Info> myProcMap = new HashMap<Pair<Target, Parameters>, Info>();

  static {
    RemoteServer.setupRMI();
  }

  public RemoteProcessSupport(Class<EntryPoint> valueClass) {
    myValueClass = valueClass;
  }

  protected abstract void fireModificationCountChanged();

  protected abstract String getName(Target target);

  protected void logText(Parameters configuration, ProcessEvent event, Key outputType, Object info) {
  }

  public void stopAll() {
    stopAll(false);
  }

  public void stopAll(boolean wait) {
    ArrayList<ProcessHandler> allHandlers = new ArrayList<ProcessHandler>();
    synchronized (myProcMap) {
      for (Info o : myProcMap.values()) {
        ContainerUtil.addIfNotNull(o.handler, allHandlers);
      }
    }
    for (ProcessHandler handler : allHandlers) {
      handler.destroyProcess();
    }
    if (wait) {
      for (ProcessHandler handler : allHandlers) {
        handler.waitFor();
      }
    }
  }

  public List<Parameters> getActiveConfigurations(@NotNull Target target) {
    ArrayList<Parameters> result = new ArrayList<Parameters>();
    synchronized (myProcMap) {
      for (Pair<Target, Parameters> pair : myProcMap.keySet()) {
        if (pair.first == target) {
          result.add(pair.second);
        }
      }
    }
    return result;
  }

  public EntryPoint acquire(@NotNull Target target, @NotNull Parameters configuration) throws Exception {
    ApplicationManagerEx.getApplicationEx().assertTimeConsuming();

    Ref<RunningInfo> ref = Ref.create(null);
    Pair<Target, Parameters> key = Pair.create(target, configuration);
    if (!getExistingInfo(ref, key)) {
      startProcess(target, configuration, key);
      if (ref.isNull()) {
        try {
          //noinspection SynchronizationOnLocalVariableOrMethodParameter
          synchronized (ref) {
            while (ref.isNull()) {
              ref.wait(1000);
              ProgressManager.checkCanceled();
            }
          }
        }
        catch (InterruptedException e) {
          ProgressManager.checkCanceled();
        }
      }
    }
    if (ref.isNull()) throw new RuntimeException("Unable to acquire remote proxy for: " + getName(target));
    RunningInfo info = ref.get();
    if (info.handler == null) throw new ExecutionException(info.name);
    return acquire(info);
  }

  public void release(@NotNull Target target, @Nullable Parameters configuration) {
    ArrayList<ProcessHandler> handlers = new ArrayList<ProcessHandler>();
    synchronized (myProcMap) {
      for (Pair<Target, Parameters> pair : myProcMap.keySet()) {
        if (pair.first == target && (configuration == null || pair.second == configuration)) {
          ContainerUtil.addIfNotNull(myProcMap.get(pair).handler, handlers);
        }
      }
    }
    for (ProcessHandler handler : handlers) {
      handler.destroyProcess();
    }
    fireModificationCountChanged();
  }

  private void startProcess(Target target, Parameters configuration, Pair<Target, Parameters> key) {
    ProgramRunner runner = new DefaultProgramRunner() {
      @NotNull
      public String getRunnerId() {
        return "MyRunner";
      }

      public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
        return true;
      }
    };
    Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    ProcessHandler processHandler = null;
    try {
      RunProfileState state = getRunProfileState(target, configuration, executor);
      ExecutionResult result = state.execute(executor, runner);
      //noinspection ConstantConditions
      processHandler = result.getProcessHandler();
    }
    catch (Exception e) {
      dropProcessInfo(key, ExceptionUtil.getUserStackTrace(e, LOG), processHandler);
      return;
    }
    processHandler.addProcessListener(getProcessListener(key));
    processHandler.startNotify();
  }

  protected abstract RunProfileState getRunProfileState(Target target, Parameters configuration, Executor executor)
    throws ExecutionException;

  private boolean getExistingInfo(Ref<RunningInfo> ref, Pair<Target, Parameters> key) {
    Info info;
    synchronized (myProcMap) {
      info = myProcMap.get(key);
      try {
        while (info != null && (!(info instanceof RunningInfo) ||
                                info.handler.isProcessTerminating() ||
                                info.handler.isProcessTerminated())) {
          myProcMap.wait(1000);
          ProgressManager.checkCanceled();
          info = myProcMap.get(key);
        }
      }
      catch (InterruptedException e) {
        ProgressManager.checkCanceled();
      }
      if (info == null) {
        myProcMap.put(key, new PendingInfo(ref, null));
      }
    }
    if (info instanceof RunningInfo) {
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (ref) {
        ref.set((RunningInfo)info);
        ref.notifyAll();
      }
    }
    return info != null;
  }

  private EntryPoint acquire(final RunningInfo port) throws Exception {
    EntryPoint result = RemoteUtil.executeWithClassLoader(new ThrowableComputable<EntryPoint, Exception>() {
      public EntryPoint compute() throws Exception {
        Registry registry = LocateRegistry.getRegistry("localhost", port.port);
        Remote remote = registry.lookup(port.name);
        if (Remote.class.isAssignableFrom(myValueClass)) {
          return RemoteUtil.substituteClassLoader(narrowImpl(remote, myValueClass), myValueClass.getClassLoader());
        }
        else {
          return RemoteUtil.castToLocal(remote, myValueClass);
        }
      }
    }, getClass().getClassLoader()); // should be the loader of client plugin
    // init hard ref that will keep it from DGC and thus preventing from System.exit
    port.entryPointHardRef = result;
    return result;
  }

  private static <T> T narrowImpl(Remote remote, Class<T> to) {
    //noinspection unchecked
    return (T)(to.isInstance(remote) ? remote : PortableRemoteObject.narrow(remote, to));
  }

  private ProcessListener getProcessListener(final Pair<Target, Parameters> key) {
    return new ProcessListener() {
      public void startNotified(ProcessEvent event) {
        ProcessHandler processHandler = event.getProcessHandler();
        processHandler.putUserData(ProcessHandler.SILENTLY_DESTROY_ON_CLOSE, Boolean.TRUE);
        Info o;
        synchronized (myProcMap) {
          o = myProcMap.get(key);
          if (o instanceof PendingInfo) {
            myProcMap.put(key, new PendingInfo(((PendingInfo)o).ref, processHandler));
          }
        }
      }

      public void processTerminated(ProcessEvent event) {
        if (dropProcessInfo(key, null, event.getProcessHandler())) {
          fireModificationCountChanged();
        }
      }

      public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
        if (dropProcessInfo(key, null, event.getProcessHandler())) {
          fireModificationCountChanged();
        }
      }

      public void onTextAvailable(ProcessEvent event, Key outputType) {
        String text = StringUtil.notNullize(event.getText());
        if (outputType == ProcessOutputTypes.STDERR) {
          LOG.warn(text.trim());
        }
        else {
          LOG.info(text.trim());
        }

        RunningInfo result = null;
        PendingInfo info;
        synchronized (myProcMap) {
          Info o = myProcMap.get(key);
          logText(key.second, event, outputType, o);
          if (o instanceof PendingInfo) {
            info = (PendingInfo)o;
            if (outputType == ProcessOutputTypes.STDOUT) {
              String prefix = "Port/ID:";
              if (text.startsWith(prefix)) {
                String pair = text.substring(prefix.length()).trim();
                int idx = pair.indexOf("/");
                result = new RunningInfo(info.handler, Integer.parseInt(pair.substring(0, idx)), pair.substring(idx + 1));
                myProcMap.put(key, result);
                myProcMap.notifyAll();
              }
            }
            else if (outputType == ProcessOutputTypes.STDERR) {
              info.stderr.append(text);
            }
          }
          else {
            info = null;
          }
        }
        if (result != null) {
          synchronized (info.ref) {
            info.ref.set(result);
            info.ref.notifyAll();
          }
          fireModificationCountChanged();
          try {
            RemoteDeadHand.TwoMinutesTurkish.startCooking("localhost", result.port);
          }
          catch (Exception e) {
            LOG.warn("The cook failed to start due to " + ExceptionUtil.getRootCause(e));
          }
        }
      }
    };
  }

  private boolean dropProcessInfo(Pair<Target, Parameters> key, @Nullable String errorMessage, @Nullable ProcessHandler handler) {
    Info info;
    synchronized (myProcMap) {
      info = myProcMap.get(key);
      if (info != null && (handler == null || info.handler == handler)) {
        myProcMap.remove(key);
        myProcMap.notifyAll();
      }
      else {
        // different processHandler
        info = null;
      }
    }
    if (info instanceof PendingInfo) {
      PendingInfo pendingInfo = (PendingInfo)info;
      if (pendingInfo.stderr.length() > 0 || pendingInfo.ref.isNull()) {
        if (errorMessage != null) pendingInfo.stderr.append(errorMessage);
        pendingInfo.ref.set(new RunningInfo(null, -1, pendingInfo.stderr.toString()));
      }
      synchronized (pendingInfo.ref) {
        pendingInfo.ref.notifyAll();
      }
    }
    return info != null;
  }

  private static class Info {
    final ProcessHandler handler;

    Info(ProcessHandler handler) {
      this.handler = handler;
    }
  }

  private static class PendingInfo extends Info {
    final Ref<RunningInfo> ref;
    final StringBuilder stderr = new StringBuilder();

    PendingInfo(Ref<RunningInfo> ref, ProcessHandler handler) {
      super(handler);
      this.ref = ref;
    }

  }

  private static class RunningInfo extends Info {
    final int port;
    final String name;
    Object entryPointHardRef;

    RunningInfo(ProcessHandler handler, int port, String name) {
      super(handler);
      this.port = port;
      this.name = name;
    }
  }

}
