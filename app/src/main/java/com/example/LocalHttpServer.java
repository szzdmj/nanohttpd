// 以 NanoHTTPD 为例：implementation 'org.nanohttpd:nanohttpd:2.3.1'
public class LocalHttpServer extends NanoHTTPD {
  private final AssetManager am;

  public LocalHttpServer(Context ctx, int port) {
    super("127.0.0.1", port);
    this.am = ctx.getAssets();
  }

  @Override public Response serve(IHTTPSession session) {
    String path = session.getUri(); // 例如 /index.html
    if ("/".equals(path)) path = "/index.html";
    try {
      if ("/__shim__/id-shim.js".equals(path)) {
        InputStream in = am.open("id-shim.js");
        return NanoHTTPD.newChunkedResponse(Response.Status.OK, "application/javascript", in);
      }
      InputStream in = am.open(path.startsWith("/") ? path.substring(1) : path);
      // 如果是 index.html，这里把 id-shim 插到 webjs.js 之前
      if (path.endsWith("/index.html") || path.equals("/index.html")) {
        String html = readAll(in, "UTF-8");
        html = html.replace(
          "<script type=\"text/javascript\" src=\"webjs.js\"></script>",
          "<script type=\"text/javascript\" src=\"/__shim__/id-shim.js\"></script>\n" +
          "<script type=\"text/javascript\" src=\"webjs.js\"></script>"
        );
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html);
      }
      String mime = guessMime(path);
      return NanoHTTPD.newChunkedResponse(Response.Status.OK, mime, in);
    } catch (IOException e) {
      return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404 Not Found");
    }
  }

  private static String guessMime(String path) {
    String p = path.toLowerCase();
    if (p.endsWith(".html")||p.endsWith(".htm")) return "text/html; charset=utf-8";
    if (p.endsWith(".js")) return "application/javascript";
    if (p.endsWith(".css")) return "text/css";
    if (p.endsWith(".png")) return "image/png";
    if (p.endsWith(".jpg")||p.endsWith(".jpeg")) return "image/jpeg";
    if (p.endsWith(".gif")) return "image/gif";
    if (p.endsWith(".svg")) return "image/svg+xml";
    if (p.endsWith(".mp4")) return "video/mp4";
    if (p.endsWith(".m3u8")) return "application/vnd.apple.mpegurl";
    if (p.endsWith(".ts")) return "video/mp2t";
    return "application/octet-stream";
  }

  private static String readAll(InputStream in, String enc) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    int n;
    while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
    return bos.toString(enc);
  }
}
