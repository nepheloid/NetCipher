/*
 * Copyright 2012-2016 Nathan Freitas
 * Copyright 2015 str4d
 * Portions Copyright (c) 2016 CommonsWare, LLC
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

package info.guardianproject.netcipher.client;

import android.content.Context;
import android.content.Intent;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import info.guardianproject.netcipher.proxy.OrbotHelper;

/**
 * Builds an HttpUrlConnection that connects via Tor through
 * Orbot.
 */
abstract public class
  StrongBuilderBase<T extends StrongBuilderBase, C>
  implements StrongBuilder<T, C> {
  private final static String PROXY_HOST="127.0.0.1";
  protected final Context ctxt;
  protected Proxy.Type proxyType;
  protected SSLContext sslContext=null;
  protected boolean useWeakCiphers=false;

  /**
   * Standard constructor.
   *
   * @param ctxt any Context will do; the StrongBuilderBase
   *             will hold onto the Application singleton
   */
  public StrongBuilderBase(Context ctxt) {
    this.ctxt=ctxt.getApplicationContext();
  }

  /**
   * Copy constructor.
   *
   * @param original builder to clone
   */
  public StrongBuilderBase(StrongBuilderBase original) {
    this.ctxt=original.ctxt;
    this.proxyType=original.proxyType;
    this.sslContext=original.sslContext;
    this.useWeakCiphers=original.useWeakCiphers;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public T withBestProxy() {
    if (supportsSocksProxy()) {
      return(withSocksProxy());
    }
    else {
      return(withHttpProxy());
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean supportsHttpProxy() {
    return(true);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public T withHttpProxy() {
    proxyType=Proxy.Type.HTTP;

    return((T)this);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean supportsSocksProxy() {
    return(false);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public T withSocksProxy() {
    proxyType=Proxy.Type.SOCKS;

    return((T)this);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public T withTrustManagers(TrustManager[] trustManagers)
    throws NoSuchAlgorithmException, KeyManagementException {

    sslContext=SSLContext.getInstance("TLSv1");
    sslContext.init(null, trustManagers, null);

    return((T)this);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public T withWeakCiphers() {
    useWeakCiphers=true;

    return((T)this);
  }

  public SSLContext getSSLContext() {
    return(sslContext);
  }

  public int getSocksPort(Intent status) {
    if (status.getStringExtra(OrbotHelper.EXTRA_STATUS)
      .equals(OrbotHelper.STATUS_ON)) {
      return(status.getIntExtra(OrbotHelper.EXTRA_PROXY_PORT_SOCKS,
        9050));
    }

    return(-1);
  }

  public int getHttpPort(Intent status) {
    if (status.getStringExtra(OrbotHelper.EXTRA_STATUS)
      .equals(OrbotHelper.STATUS_ON)) {
      return(status.getIntExtra(OrbotHelper.EXTRA_PROXY_PORT_HTTP,
        8118));
    }

    return(-1);
  }

  protected SSLSocketFactory buildSocketFactory() {
    if (sslContext==null) {
      return(null);
    }

    SSLSocketFactory result=
      new TlsOnlySocketFactory(sslContext.getSocketFactory(),
        useWeakCiphers);

    return(result);
  }

  public Proxy buildProxy(Intent status) {
    Proxy result=null;

    if (status.getStringExtra(OrbotHelper.EXTRA_STATUS)
      .equals(OrbotHelper.STATUS_ON)) {
      if (proxyType==Proxy.Type.SOCKS) {
        result=new Proxy(Proxy.Type.SOCKS,
          new InetSocketAddress(PROXY_HOST, getSocksPort(status)));
      }
      else if (proxyType==Proxy.Type.HTTP) {
        result=new Proxy(Proxy.Type.HTTP,
          new InetSocketAddress(PROXY_HOST, getHttpPort(status)));
      }
    }

    return(result);
  }

  @Override
  public void build(final Callback<C> callback) {
    OrbotHelper.get(ctxt).addStatusCallback(
      new OrbotHelper.SimpleStatusCallback() {
        @Override
        public void onEnabled(Intent statusIntent) {
          OrbotHelper.get(ctxt).removeStatusCallback(this);

          try {
            callback.onConnected(build(statusIntent));
          }
          catch (Exception e) {
            callback.onConnectionException(e);
          }
        }

        @Override
        public void onNotYetInstalled() {
          OrbotHelper.get(ctxt).removeStatusCallback(this);
          callback.onTimeout();
        }

        @Override
        public void onStatusTimeout() {
          OrbotHelper.get(ctxt).removeStatusCallback(this);
          callback.onTimeout();
        }
      });
  }
}
