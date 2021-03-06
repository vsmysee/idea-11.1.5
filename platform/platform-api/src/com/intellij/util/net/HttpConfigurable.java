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
package com.intellij.util.net;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.WaitForProgressToShow;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Transient;
import org.apache.commons.codec.binary.Base64;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Oct 7, 2003
 * Time: 3:58:23 PM
 * To change this template use Options | File Templates.
 */
@State(
  name = "HttpConfigurable",
  storages = {
    @Storage( file = "$APP_CONFIG$/other.xml")
  }
)
public class HttpConfigurable implements PersistentStateComponent<HttpConfigurable>, JDOMExternalizable {
  public boolean USE_HTTP_PROXY = false;
  public String PROXY_HOST = "";
  public int PROXY_PORT = 80;

  public boolean PROXY_AUTHENTICATION = false;
  public String PROXY_LOGIN = "";
  public String PROXY_PASSWORD_CRYPT = "";
  public boolean KEEP_PROXY_PASSWORD = false;

  public static HttpConfigurable getInstance() {
    return ServiceManager.getService(HttpConfigurable.class);
  }

  public static boolean editConfigurable(final JComponent parent) {
    return ShowSettingsUtil.getInstance().editConfigurable(parent, new HTTPProxySettingsPanel(getInstance()));
  }

  @Override
  public HttpConfigurable getState() {
    final HttpConfigurable state = new HttpConfigurable();
    XmlSerializerUtil.copyBean(this, state);
    if (!KEEP_PROXY_PASSWORD) {
      state.PROXY_PASSWORD_CRYPT = "";
    }
    return state;
  }

  @Override
  public void loadState(HttpConfigurable state) {
    XmlSerializerUtil.copyBean(state, this);
    if (!KEEP_PROXY_PASSWORD) {
      PROXY_PASSWORD_CRYPT = "";
    }
  }

  @Transient
  public String getPlainProxyPassword() {
    return new String(new Base64().decode(PROXY_PASSWORD_CRYPT.getBytes()));
  }

  @Transient
  public void setPlainProxyPassword (String password) {
    PROXY_PASSWORD_CRYPT = new String(new Base64().encode(password.getBytes()));
  }

  public PasswordAuthentication getPromptedAuthentication(final String host, final String prompt) {
    if (PROXY_AUTHENTICATION && !KEEP_PROXY_PASSWORD) {
      Runnable runnable = new Runnable() {
        public void run() {
          AuthenticationDialog dlg = new AuthenticationDialog(host, prompt);
          dlg.show();
        }
      };
      try {
        WaitForProgressToShow.runOrInvokeAndWaitAboveProgress(runnable, ModalityState.any());
      }
      catch (Exception e) {
        // ignore
      }
    }
    return new PasswordAuthentication(PROXY_LOGIN, getPlainProxyPassword().toCharArray());
  }

  private Authenticator getAuthenticator () {
    return new Authenticator () {
      protected PasswordAuthentication getPasswordAuthentication() {
        return getPromptedAuthentication(getRequestingHost(), getRequestingPrompt());
      }
    };
  }

  //these methods are preserved for compatibility
  @Override
  public void readExternal(Element element) throws InvalidDataException {
    loadState(XmlSerializer.deserialize(element, HttpConfigurable.class));
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    XmlSerializer.serializeInto(getState(), element);
  }

  // @todo [all] Call this function before every HTTP connection.
  /**
   * Call this function before every HTTP connection.
   * If system configured to use HTTP proxy, this function
   * checks all required parameters and ask password if
   * required.
   * @param url URL for HTTP connection
   * @throws IOException
   */
  public void prepareURL (String url) throws IOException {
    setAuthenticator();

    URLConnection connection = new URL (url).openConnection();
    connection.setConnectTimeout(3 * 1000);
    connection.setReadTimeout(3 * 1000);
    connection.connect();
    try {
      connection.getInputStream();
    }
    catch (Throwable e) {
      if (e instanceof IOException) {
        throw (IOException)e;
      }
    }
    if (connection instanceof HttpURLConnection) {
      ((HttpURLConnection)connection).disconnect();
    }
  }

  /**
   * Opens HTTP connection to a given location using configured proxy settings.
   * @param location url to connect to
   * @return instance of {@link HttpURLConnection}
   * @throws IOException in case of any I/O troubles or if created connection isn't instance of HttpURLConnection.
   */
  @NotNull
  public HttpURLConnection openHttpConnection(@NotNull String location) throws IOException {
    setAuthenticator();
    URL url = new URL(location);
    final URLConnection urlConnection;
    if (USE_HTTP_PROXY) {
      InetSocketAddress proxyAddress = new InetSocketAddress(InetAddress.getByName(PROXY_HOST), PROXY_PORT);
      Proxy proxy = new Proxy(Proxy.Type.HTTP, proxyAddress);
      urlConnection = url.openConnection(proxy);
    }
    else {
      urlConnection = url.openConnection();
    }
    if (urlConnection instanceof HttpURLConnection) {
      return (HttpURLConnection) urlConnection;
    }
    else {
      throw new IOException("Expected " + HttpURLConnection.class + ", got " + url.getClass());
    }
  }

  public void setAuthenticator() {
    if (USE_HTTP_PROXY) {
      System.setProperty("proxySet", "true");
      System.setProperty("http.proxyHost", PROXY_HOST);
      System.setProperty("http.proxyPort", Integer.toString (PROXY_PORT));
      Authenticator.setDefault(getAuthenticator());
    } else {
      System.setProperty("proxySet", "false");
      System.clearProperty("http.proxyHost");
      System.clearProperty("http.proxyPort");
      Authenticator.setDefault(null);
    }
  }

  public static List<String> getProxyCmdLineProperties() {
    List<String> proxy = new ArrayList<String>();
    HttpConfigurable httpConfigurable = getInstance();
    if (httpConfigurable.USE_HTTP_PROXY) {
      proxy.add("-DproxySet=true");
      proxy.add("-Dhttp.proxyHost=" + httpConfigurable.PROXY_HOST);
      proxy.add("-Dhttp.proxyPort=" + httpConfigurable.PROXY_PORT);
      proxy.add("-Dhttps.proxyHost=" + httpConfigurable.PROXY_HOST);
      proxy.add("-Dhttps.proxyPort=" + httpConfigurable.PROXY_PORT);

      if (httpConfigurable.KEEP_PROXY_PASSWORD && StringUtil.isNotEmpty(httpConfigurable.PROXY_LOGIN)) {
        proxy.add("-Dproxy.authentication.username=" + httpConfigurable.PROXY_LOGIN);
        proxy.add("-Dproxy.authentication.password=" + httpConfigurable.getPlainProxyPassword());
      }

    }
    return proxy;
  }
}
