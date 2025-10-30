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

import java.net.URI;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

  private static final String BASE = "http://127.0.0.1:12721/";
  private WebView web;
  private LocalHttpServer server;

  @SuppressLint("SetJavaScriptEnabled")
  @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // 启动本地 HTTP 服务器（assets -> http://127.0.0.1:12721/）
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

    // 建议开启硬件加速（默认开启），4.4 也支持
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
        // 降级：把 window.open/target=_blank 统一在当前页打开，避免“回到主页”
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
        // 允许 http/https 本地与外网
        if (url.startsWith("http://") || url.startsWith("https://")) {
          v.loadUrl(url);
          return true;
        }
        if (url.startsWith("#") || url.startsWith("about:")) {
          // 锚点留在当前页
          return true;
        }
        // 相对路径 -> 以本地站点为基准进行解析，避免 file:// 回首页
        try {
          URL base = new URL(BASE);
          URL abs = new URL(base, url);
          v.loadUrl(abs.toString());
          return true;
        } catch (Exception e) {
          // 兜底：直接拦截
          return true;
        }
      }
    });

    web.setWebChromeClient(new WebChromeClient() {
      @Override
      public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
        // 统一在当前 WebView 打开（window.open/target=_blank）
        try {
          // 有些内核通过 WebViewTransport 传递新 URL，这里简单粗暴：读取当前最新的 URL 不可靠，仍依赖注入 JS 兜底
          return false;
        } catch (Exception ignored) {
          return false;
        }
      }
    });

    web.loadUrl(BASE + "index.html");
  }

  @Override protected void onDestroy() {
    try { if (server != null) server.stop(); } catch (Exception ignored) {}
    super.onDestroy();
  }
}
