public class MainActivity extends Activity {
  private InAppWebView mWebView; // 若不用 Flutter，可用 android.webkit.WebView
  private LocalHttpServer server;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // 启动本地服务
    server = new LocalHttpServer(this, 8080);
    try { server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false); } catch (IOException e) {}

    WebView web = new WebView(this);
    setContentView(web);
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setDatabaseEnabled(true);
    s.setAllowFileAccess(true); // 仅读取 assets，不再用 file:// 导航
    s.setSupportMultipleWindows(true);

    web.setWebViewClient(new WebViewClient() {
      @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
        // 相对链接自动补全为站点前缀，防止跳 file:///… 回主页
        if (!url.startsWith("http") && !url.startsWith("file:") && !url.startsWith("#")) {
          url = "http://127.0.0.1:8080/" + url.replaceFirst("^/+", "");
        }
        view.loadUrl(url);
        return true; // 我们接管
      }
    });

    web.loadUrl("http://127.0.0.1:8080/index.html");
  }

  @Override protected void onDestroy() {
    if (server != null) server.stop();
    super.onDestroy();
  }
}
