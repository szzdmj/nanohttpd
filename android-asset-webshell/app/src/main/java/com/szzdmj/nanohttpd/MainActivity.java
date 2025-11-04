package com.szzdmj.nanohttpd;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.szzdmj.nanohttpd.webshell.R;
import fi.iki.elonen.NanoHTTPD;

import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

  private static final String TAG = "WebShell/MainActivity";
  private static final String BASE = "http://127.0.0.1:12721/";
  private static final String ASSET_INDEX = "file:///android_asset/index.html";

  // 错误码（退出前抛回桌面）
  private static final int EC_NO_INTERNET_PERMISSION = 9001;
  private static final int EC_SERVER_START_FAIL      = 9002;
  private static final int EC_INDEX_LOAD_ERROR       = 9101;
  private static final int EC_RENDER_GONE            = 9201;
  private static final int EC_UNCAUGHT               = 9999;

  private static final boolean EXIT_ON_FATAL = true;

  private WebView web;
  private LocalHttpServer server;
  private boolean serverStarted = false;

  @SuppressLint("SetJavaScriptEnabled")
  @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.i(TAG, "onCreate() enter, sdk=" + Build.VERSION.SDK_INT);

    CrashLogger.init(getApplicationContext());
    Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
      @Override public void uncaughtException(Thread t, Throwable e) {
        // 最后一层兜底：回桌面 + 退出码
        fatalExit(EC_UNCAUGHT, "Uncaught in thread=" + t.getName(), e);
      }
    });
    Log.i(TAG, "Crash log path = " + CrashLogger.getLogPath());

    try {
      if (Build.VERSION.SDK_INT >= 19) {
        WebView.setWebContentsDebuggingEnabled(true);
        Log.i(TAG, "WebContentsDebuggingEnabled = true");
      }
    } catch (Throwable t) {
      Log.w(TAG, "Enable debugging failed", t);
    }

    boolean hasInternet = ContextCompat.checkSelfPermission(
        this, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED;
    Log.i(TAG, "permission INTERNET granted=" + hasInternet);

    // 尝试启动本地 HTTP 服务器
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
            if (EXIT_ON_FATAL) {
              fatalExit(EC_SERVER_START_FAIL, "Local server failed to start", t2);
              return;
            } else {
              Toast.makeText(this, "本地服务器启动失败，改用本地页面", Toast.LENGTH_LONG).show();
            }
          }
        }
      } catch (Throwable t) {
        serverStarted = false;
        CrashLogger.err("Create LocalHttpServer failed", t);
        if (EXIT_ON_FATAL) {
          fatalExit(EC_SERVER_START_FAIL, "Create LocalHttpServer failed", t);
          return;
        } else {
          Toast.makeText(this, "创建本地服务器失败，改用本地页面", Toast.LENGTH_LONG).show();
        }
      }
    } else {
      // 若清单没声明 INTERNET，这里会是 false（老版本曾缺失）
      CrashLogger.w("INTERNET permission NOT granted (manifest missing?)", null);
      Toast.makeText(this, "未声明网络权限：优先改用本地页面", Toast.LENGTH_LONG).show();
    }

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
      @SuppressWarnings("deprecation")
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
        // 仅对主文档失败触发退出（避免子资源 404 误判）
        if (isLikelyMainDoc(failingUrl)) {
          fatalExit(EC_INDEX_LOAD_ERROR, "Main page load failed: " + description, null);
        }
        super.onReceivedError(view, errorCode, description, failingUrl);
      }

      @Override
      public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
        if (Build.VERSION.SDK_INT >= 23) {
          Log.e(TAG, "onReceivedError: code=" + error.getErrorCode()
              + ", desc=" + error.getDescription()
              + ", url=" + request.getUrl());
          if (request.isForMainFrame()) {
            fatalExit(EC_INDEX_LOAD_ERROR, "Main page load failed: " + error.getDescription(), null);
          }
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
        fatalExit(EC_RENDER_GONE, "WebView render process gone", null);
        return true; // 我们接管退出
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
          fatalExit(EC_INDEX_LOAD_ERROR, "handleUrl failed: " + url, t);
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

  private boolean isLikelyMainDoc(String url) {
    if (url == null) return false;
    String u = url.toLowerCase();
    return u.contains("index.html") || u.equals(BASE.toLowerCase()) || u.equals(ASSET_INDEX.toLowerCase());
  }

  private void injectJsHooks(WebView view) {
    try {
      String js = "(function(){try{if(!window.__webshellHooked){window.__webshellHooked=true;"
          + "var _e=window.onerror;window.onerror=function(m,src,ln,col,err){try{console.error('JS-ERROR: '+m+' @ '+src+':'+ln+':'+(col||0));}catch(_){}};"
          + "}}catch(e){console.error('INJECT-HOOK-FAIL:'+e);}})();";
      if (Build.VERSION.SDK_INT >= 19) view.evaluateJavascript(js, null);
      else view.loadUrl("javascript:" + js);
      Log.i(TAG, "injectJsHooks() done");
    } catch (Throwable t) {
      fatalExit(EC_INDEX_LOAD_ERROR, "injectJsHooks failed", t);
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
      fatalExit(EC_INDEX_LOAD_ERROR, "asset missing/open fail: " + name, t);
    } finally {
      try { if (in != null) in.close(); } catch (Throwable ignore) {}
    }
  }

  private void fatalExit(final int code, final String msg, final Throwable t) {
    // 统一处理：写日志 + Toast + 回桌面 + 进程退出
    CrashLogger.err("FATAL[" + code + "]: " + msg, t);
    Log.e(TAG, "FATAL[" + code + "]: " + msg, t);
    try {
      Handler h = new Handler(Looper.getMainLooper());
      h.post(new Runnable() {
        @Override public void run() {
          try {
            Toast.makeText(MainActivity.this, "错误码: " + code + "（即将返回桌面）", Toast.LENGTH_LONG).show();
          } catch (Throwable ignore) {}
          try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
          } catch (Throwable ignore) {}
          try { finishAffinity(); } catch (Throwable ignore) {}
          h.postDelayed(new Runnable() {
            @Override public void run() {
              try { System.exit(code); } catch (Throwable ignore) {}
            }
          }, 400);
        }
      });
    } catch (Throwable e) {
      // 退而求其次
      try { System.exit(code); } catch (Throwable ignore) {}
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
