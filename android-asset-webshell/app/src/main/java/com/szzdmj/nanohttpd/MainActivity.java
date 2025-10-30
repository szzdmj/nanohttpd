package com.szzdmj.nanohttpd;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

  private static final String BASE = "http://127.0.0.1:12721/";
  private WebView web;
  private LocalHttpServer server;

  @SuppressLint("SetJavaScriptEnabled")
  @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    server = new LocalHttpServer(getApplicationContext(), 12721);
    try { server.start(); } catch (Exception ignored) {}

    setContentView(R.layout.activity_main);
    web = findViewById(R.id.web);

    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setDatabaseEnabled(true);
    s.setSupportMultipleWindows(true);
    s.setJavaScriptCanOpenWindowsAutomatically(true);
    s.setMediaPlaybackRequiresUserGesture(false);

    if (Build.VERSION.SDK_INT >= 19) {
      web.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);
    }

    web.setWebViewClient(new WebViewClient() {
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return handleUrl(view, url);
      }
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        if (Build.VERSION.SDK_INT >= 21) {
          return handleUrl(view, String.valueOf(request.getUrl()));
        }
        return false;
      }

      @Override
      public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        // 降级兜底：把 window.open/_blank 统一在当前 WebView 打开
        String js =
          "(function(){try{" +
            "window.open=function(u){if(u){location.href=u;}};" +
            "var as=document.querySelectorAll('a[target=\"_blank\"]');" +
            "for(var i=0;i<as.length;i++){as[i].setAttribute('target','_self');}" +
          "}catch(e){}})();";
        if (Build.VERSION.SDK_INT >= 19) view.evaluateJavascript(js, null);
        else view.loadUrl("javascript:" + js);
      }

      private boolean handleUrl(WebView v, String url) {
        if (url == null || url.length() == 0) return true;
        if (url.startsWith("http://") || url.startsWith("https://")) {
          v.loadUrl(url);
          return true;
        }
        if (url.startsWith("#") || url.startsWith("about:")) {
          return true;
        }
        // 相对路径 -> 以本地站点为基准解析
        try {
          String abs = BASE + url.replaceFirst("^/+", "");
          v.loadUrl(abs);
          return true;
        } catch (Exception e) {
          return true;
        }
      }
    });

    web.setWebChromeClient(new WebChromeClient() {
      @Override
      public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
        // 统一在当前 WebView 打开（主要依赖 onPageFinished 注入脚本）
        return false;
      }
    });

    web.loadUrl(BASE + "index.html");
  }

  @Override protected void onDestroy() {
    try { if (server != null) server.stop(); } catch (Exception ignored) {}
    super.onDestroy();
  }
}
