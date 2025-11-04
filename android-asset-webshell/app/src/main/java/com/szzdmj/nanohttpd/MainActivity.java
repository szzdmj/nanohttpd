package com.szzdmj.nanohttpd;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.SslErrorHandler;
import android.net.http.SslError;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

// 注意：R 属于 app 的 namespace（build.gradle 配置为 com.szzdmj.nanohttpd.webshell）
import com.szzdmj.nanohttpd.webshell.R;

// 需要该 import 才能使用 SOCKET_READ_TIMEOUT 常量
import fi.iki.elonen.NanoHTTPD;

import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

  private static final String TAG = "WebShell/MainActivity";
  private static final String BASE = "http://127.0.0.1:12721/";
  private static final String ASSET_INDEX = "file:///android_asset/index.html";

  private WebView web;
  private LocalHttpServer server;
  private boolean serverStarted = false;

  @SuppressLint("SetJavaScriptEnabled")
  @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.i(TAG, "onCreate() enter, sdk=" + Build.VERSION.SDK_INT);

    // 初始化日志与全局崩溃捕获
    CrashLogger.init(getApplicationContext());
    Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
      @Override public void uncaughtException(Thread t, Throwable e) {
        CrashLogger.err("FATAL crash in thread=" + t.getName(), e);
      }
    });
    Log.i(TAG, "Crash log path = " + CrashLogger.getLogPath());

    // 启用 WebView 远程调试（4.4+）
    try {
      if (Build.VERSION.SDK_INT >= 19) {
        WebView.setWebContentsDebuggingEnabled(true);
        Log.i(TAG, "WebContentsDebuggingEnabled = true");
      }
    } catch (Throwable t) {
      Log.w(TAG, "Enable debugging failed", t);
    }

    // 权限检测（INTERNET 为 normal 权限，安装即赋予；若清单未声明，这里会返回 DENIED）
    boolean hasInternet = ContextCompat.checkSelfPermission(
        this, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED;
    Log.i(TAG, "permission INTERNET granted=" + hasInternet);
    if (!hasInternet) {
      // 清单未声明时才会发生；直接提示并走本地 assets 兜底，避免“一闪即退”
      Toast.makeText(this, "未声明网络权限：将改用本地页面", Toast.LENGTH_LONG).show();
    }

    // 尝试启动本地 HTTP 服务器（需要 INTERNET）
    if (hasInternet) {
      try {
        server = new LocalHttpServer(getApplicationContext(), 12721);
        try {
          server.start();
          serverStarted = true;
          Log.i(TAG, "LocalHttpServer.start() ok @ " + BASE);
        } catch (Throwable t1) {
          Log.w(TAG, "server.start() failed, retry with timeout/daemon", t1);
          try {
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);
            serverStarted = true;
            Log.i(TAG, "LocalHttpServer.start(timeout,daemon) ok");
          } catch (Throwable t2) {
            serverStarted = false;
            CrashLogger.err("LocalHttpServer start() failed", t2);
            Toast.makeText(this, "本地服务器启动失败，改用本地页面", Toast.LENGTH_LONG).show();
          }
        }
      } catch (Throwable t) {
        serverStarted = false;
        CrashLogger.err("Create LocalHttpServer failed", t);
        Toast.makeText(this, "创建本地服务器失败，改用本地页面", Toast.LENGTH_LONG).show();
      }
    }

    // 核心资源自检：assets/index.html 与 id-shim.js
    verifyAssets("index.html");
    verifyAssets("id-shim.js");

    setContentView(R.layout.activity_main);
    web = findViewById(R.id.web);

    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setDatabaseEnabled(true);
    s.setSupportMultipleWindows(true);
    s.setJavaScriptCanOpenWindowsAutomatically(true);
    s.setMediaPlaybackRequiresUserGesture(false);
    Log.i(TAG, "WebSettings ready: JS=" + s.getJavaScriptEnabled()
        + ", DOMStorage=" + s.getDomStorageEnabled()
        + ", DB=" + s.getDatabaseEnabled());

    if (Build.VERSION.SDK_INT >= 19) {
      web.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);
      Log.i(TAG, "WebView layer=HARDWARE");
    }

    web.setWebViewClient(new WebViewClient() {
      @Override
      @SuppressWarnings("deprecation") // 为旧签名去掉弃用警告（兼容 4.4.4）
      public boolean shouldOverrideUrlLoading(WebView view, String url) {
        Log.i(TAG, "shouldOverrideUrlLoading(deprecated): " + url);
        return handleUrl(view, url);
      }

      @Override
      public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        if (Build.VERSION.SDK_INT >= 21) {
          String u = String.valueOf(request.getUrl());
          Log.i(TAG, "shouldOverrideUrlLoading: " + u);
          return handleUrl(view, u);
        }
        return false;
      }

      @Override
      public void onPageFinished(WebView view, String url) {
        Log.i(TAG, "onPageFinished: " + url);
        super.onPageFinished(view, url);
        injectJsHooks(view);
      }

      @Override
      @SuppressWarnings("deprecation")
      public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        Log.e(TAG, "onReceivedError(deprecated): code=" + errorCode
            + ", desc=" + description + ", url=" + failingUrl);
        super.onReceivedError(view, errorCode, description, failingUrl);
      }

      @Override
      public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
        if (Build.VERSION.SDK_INT >= 23) {
          Log.e(TAG, "onReceivedError: code=" + error.getErrorCode()
              + ", desc=" + error.getDescription()
              + ", url=" + request.getUrl());
        } else {
          Log.e(TAG, "onReceivedError(<23 shim)");
        }
        super.onReceivedError(view, request, error);
      }

      @Override
      public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        Log.e(TAG, "onReceivedSslError: " + error);
        super.onReceivedSslError(view, handler, error);
      }

      @Override
      public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
        if (Build.VERSION.SDK_INT >= 26) {
          Log.e(TAG, "onRenderProcessGone: didCrash=" + detail.didCrash()
              + ", rendererPriorityAtExit=" + detail.rendererPriorityAtExit());
        } else {
          Log.e(TAG, "onRenderProcessGone(<26 shim)");
        }
        return super.onRenderProcessGone(view, detail);
      }

      private boolean handleUrl(WebView v, String url) {
        if (url == null || url.length() == 0) return true;
        try {
          if (url.startsWith("http://") || url.startsWith("https://")) {
            Log.i(TAG, "navigate external http(s): " + url);
            v.loadUrl(url);
            return true;
          }
          if (url.startsWith("#") || url.startsWith("about:")) {
            Log.i(TAG, "ignore special: " + url);
            return true;
          }
          // 相对路径 -> 以本地站点为基准解析
          String abs = BASE + url.replaceFirst("^/+", "");
          Log.i(TAG, "navigate relative -> " + abs);
          v.loadUrl(abs);
          return true;
        } catch (Throwable t) {
          CrashLogger.err("handleUrl failed for: " + url, t);
          return true;
        }
      }
    });

    web.setWebChromeClient(new WebChromeClient() {
      @Override
      public boolean onConsoleMessage(ConsoleMessage cm) {
        String line = "JSConsole[" + cm.messageLevel() + "] (" + cm.sourceId()
            + ":" + cm.lineNumber() + "): " + cm.message();
        switch (cm.messageLevel()) {
          case ERROR:  Log.e(TAG, line); break;
          case WARNING:Log.w(TAG, line); break;
          default:     Log.i(TAG, line); break;
        }
        return super.onConsoleMessage(cm);
      }

      @Override
      public boolean onJsAlert(WebView view, String url, String message, android.webkit.JsResult result) {
        Log.i(TAG, "onJsAlert: url=" + url + ", msg=" + message);
        return super.onJsAlert(view, url, message, result);
      }

      @Override
      public boolean onJsConfirm(WebView view, String url, String message, android.webkit.JsResult result) {
        Log.i(TAG, "onJsConfirm: url=" + url + ", msg=" + message);
        return super.onJsConfirm(view, url, message, result);
      }
    });

    // 首次加载：优先本地服务器，否则直接从 assets 加载
    if (serverStarted) {
      String first = BASE + "index.html";
      Log.i(TAG, "web.loadUrl => " + first);
      web.loadUrl(first);
    } else {
      Log.w(TAG, "server not started, fallback to assets: " + ASSET_INDEX);
      web.loadUrl(ASSET_INDEX);
    }
  }

  private void injectJsHooks(WebView view) {
    try {
      String js = ""
          + "(function(){try{"
          + "  if(!window.__webshellHooked){"
          + "    window.__webshellHooked = true;"
          + "    var _e = window.onerror;"
          + "    window.onerror = function(m,src,ln,col,err){"
          + "      try{console.error('JS-ERROR: '+m+' @ '+src+':'+ln+':'+(col||0));}catch(_){}"
          + "      if(typeof _e==='function'){try{_e.apply(this,arguments);}catch(_){}}"
          + "    };"
          + "  }"
          + "}catch(e){console.error('INJECT-HOOK-FAIL:'+e);}})();";
      if (Build.VERSION.SDK_INT >= 19) view.evaluateJavascript(js, null);
      else view.loadUrl("javascript:" + js);
      Log.i(TAG, "injectJsHooks() done");
    } catch (Throwable t) {
      CrashLogger.err("injectJsHooks failed", t);
    }
  }

  private void verifyAssets(String name) {
    InputStream in = null;
    try {
      in = getAssets().open(name);
      int total = 0;
      byte[] buf = new byte[4096];
      int n;
      while ((n = in.read(buf)) > 0) total += n;
      Log.i(TAG, "asset ok: " + name + " (" + total + " bytes)");
    } catch (Throwable t) {
      CrashLogger.err("asset missing or open failed: " + name, t);
    } finally {
      try { if (in != null) in.close(); } catch (Throwable ignore) {}
    }
  }

  @Override protected void onStart()   { super.onStart();   Log.i(TAG, "onStart()"); }
  @Override protected void onResume()  { super.onResume();  Log.i(TAG, "onResume()"); }
  @Override protected void onPause()   { super.onPause();   Log.i(TAG, "onPause()"); }
  @Override protected void onStop()    { super.onStop();    Log.i(TAG, "onStop()"); }

  @Override protected void onDestroy() {
    Log.i(TAG, "onDestroy()");
    try { if (serverStarted && server != null) { server.stop(); Log.i(TAG, "server.stop() ok"); } }
    catch (Throwable t) { CrashLogger.err("server.stop() failed", t); }
    super.onDestroy();
  }
}
