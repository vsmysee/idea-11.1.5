package com.intellij.execution.rmi;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.containers.ConcurrentFactoryMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.rmi.Remote;
import java.util.Map;

/**
 * @author Gregory.Shrago
 */
public class RemoteUtil {
  RemoteUtil() {
  }

  private static final ConcurrentFactoryMap<Pair<Class<?>, Class<?>>, Map<Method, Method>> ourRemoteToLocalMap =
    new ConcurrentFactoryMap<Pair<Class<?>, Class<?>>, Map<Method, Method>>() {
      @Override
      protected Map<Method, Method> create(Pair<Class<?>, Class<?>> key) {
        final THashMap<Method, Method> map = new THashMap<Method, Method>();
        for (Method method : key.second.getMethods()) {
          Method m = null;
          main:
          for (Method candidate : key.first.getMethods()) {
            if (!candidate.getName().equals(method.getName())) continue;
            Class<?>[] cpts = candidate.getParameterTypes();
            Class<?>[] mpts = method.getParameterTypes();
            if (cpts.length != mpts.length) continue;
            for (int i = 0; i < mpts.length; i++) {
              Class<?> mpt = mpts[i];
              Class<?> cpt = cpts[i];
              if (!cpt.isAssignableFrom(mpt)) continue main;
            }
            m = candidate;
            break;
          }
          if (m != null) map.put(method, m);
        }
        return map;
      }
    };

  @Nullable
  public static <T> T castToRemote(final Object object, final Class<T> clazz) {
    if (!Proxy.isProxyClass(object.getClass())) return null;
    final InvocationHandler handler = Proxy.getInvocationHandler(object);
    if (handler instanceof RemoteInvocationHandler) {
      final RemoteInvocationHandler rih = (RemoteInvocationHandler)handler;
      if (clazz.isInstance(rih.myRemote)) {
        return (T)rih.myRemote;
      }
    }
    return null;
  }

  public static <T> T castToLocal(final Object remote, final Class<T> clazz) {
    final ClassLoader loader = clazz.getClassLoader();
    //noinspection unchecked
    return (T)Proxy.newProxyInstance(loader, new Class[]{clazz}, new RemoteInvocationHandler(remote, clazz, loader));
  }

  private static Class<?> tryFixReturnType(Object result, Class<?> returnType, ClassLoader loader) throws Exception {
    if (returnType.isInterface()) return returnType;
    if (result instanceof RemoteCastable) {
      final String className = ((RemoteCastable)result).getCastToClassName();
      return Class.forName(className, true, loader);
    }
    return returnType;
  }

  public static <T> T substituteClassLoader(final T remote, final ClassLoader classLoader) throws Exception {
    return executeWithClassLoader(new ThrowableComputable<T, Exception>() {
      public T compute() {
        Object proxy = Proxy.newProxyInstance(classLoader, remote.getClass().getInterfaces(), new InvocationHandler() {
          public Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
            return executeWithClassLoader(new ThrowableComputable<Object, Exception>() {
              public Object compute() throws Exception {
                try {
                  final Object result = method.invoke(remote, args);
                  if (result instanceof Remote) {
                    if (result instanceof RemoteCastable) {
                      return castToLocal(result, tryFixReturnType(result, method.getReturnType(), classLoader));
                    }
                    return substituteClassLoader(result, classLoader);
                  }
                  return result;
                }
                catch (InvocationTargetException e) {
                  Throwable cause = e.getCause();
                  if (cause instanceof RuntimeException) throw (RuntimeException)cause;
                  if (cause instanceof Error) throw (Error)cause;
                  if (canThrow(cause, method)) throw (Exception)cause;
                  throw new RuntimeException(cause);
                }
              }
            }, classLoader);
          }
        });
        return (T)proxy;
      }
    }, classLoader);
  }

  private static boolean canThrow(Throwable cause, Method method) {
    for (Class<?> each : method.getExceptionTypes()) {
      if (each.isInstance(cause)) return true;
    }
    return false;
  }

  public static <T> T executeWithClassLoader(final ThrowableComputable<T, Exception> action, final ClassLoader classLoader)
    throws Exception {
    final Thread thread = Thread.currentThread();
    final ClassLoader prev = thread.getContextClassLoader();
    try {
      thread.setContextClassLoader(classLoader);
      return action.compute();
    }
    finally {
      thread.setContextClassLoader(prev);
    }
  }

  /**
   * There is a possible case that remotely called code throws exception during processing. That exception is wrapped at different
   * levels then - {@link InvocationTargetException}, {@link UndeclaredThrowableException} etc.
   * <p/>
   * This method tries to extract the 'real exception' from the given potentially wrapped one.
   * 
   * @param e  exception to process
   * @return   extracted 'real exception' if any; given exception otherwise
   */
  @NotNull
  public static Throwable unwrap(@NotNull Throwable e) {
    for (Throwable candidate = e; candidate != null; candidate = candidate.getCause()) {
      Class<? extends Throwable> clazz = candidate.getClass();
      if (clazz != InvocationTargetException.class && clazz != UndeclaredThrowableException.class) {
        return candidate;
      }
    }
    return e;
  }

  private static class RemoteInvocationHandler implements InvocationHandler {
    private final Object myRemote;
    private final Class<?> myClazz;
    private final ClassLoader myLoader;

    public RemoteInvocationHandler(Object remote, Class<?> clazz, ClassLoader loader) {
      myRemote = remote;
      myClazz = clazz;
      myLoader = loader;
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (method.getDeclaringClass() == Object.class) {
        return method.invoke(myRemote, args);
      }
      else {
        Method m = ourRemoteToLocalMap.get(Pair.<Class<?>, Class<?>>create(myRemote.getClass(), myClazz)).get(method);
        if (m == null) throw new NoSuchMethodError(method.getName() + " in " + myRemote.getClass());
        try {
          Object result = m.invoke(myRemote, args);
          if (result instanceof Remote) {
            return castToLocal(result, tryFixReturnType(result, method.getReturnType(), myLoader));
          }
          return result;
        }
        catch (InvocationTargetException e) {
          Throwable cause = e.getCause();
          if (cause instanceof RuntimeException) throw cause;
          if (cause instanceof Error) throw cause;
          if (canThrow(cause, method)) throw cause;
          throw new RuntimeException(cause);
        }
      }
    }
  }
}
