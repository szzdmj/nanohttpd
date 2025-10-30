package com.szzdmj.nanohttpd;

import android.content.Context;
import android.content.res.AssetManager;

import org.nanohttpd.protocols.http.NanoHTTPD;
import org.nanohttpd.protocols.http.response.Response;
import org.nanohttpd.protocols.http.response.FixedStatusCode;
import org.nanohttpd.protocols.http.response.Status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

class LocalHttpServer extends NanoHTTPD {
  private final AssetManager am;

  LocalHttpServer(Context ctx, int port) {
    super("127.0.0.1", port);
    this.am = ctx.getAssets();
  }

  @Override
  public Response serve(IHTTPSession session) {
    String path = session.getUri(); // 形如 /index.html
    if ("/".equals(path)) path = "/index.html";
    try {
      if ("/__shim__/id-shim.js".equals(path)) {
        InputStream in = am.open("id-shim.js");
        return Response.newChunkedResponse(Status.OK, "application/javascript", in);
      }

      // 强制仅用本地 assets，绝不远程兜底
      // 如果 webjs.js 或 sw.js 不存在于 assets，这里直接 404，避免任何外网访问
      if (path.endsWith(".html")) {
        InputStream inRaw = am.open(stripLeadingSlash(path));
        String html = readAll(inRaw, "UTF-8");
        // 在 webjs.js 之前强制注入 id-shim（确保 4.4.4 先装配 ID 变量）
        String token = "<script type=\"text/javascript\" src=\"webjs.js\"></script>";
        if (html.contains(token)) {
          html = html.replace(token,
            "<script type=\"text/javascript\" src=\"/__shim__/id-shim.js\"></script>\n" + token);
        } else {
          // 若找不到 token，则尽量在 </head> 前注入
          html = html.replace("</head>",
            "  <script type=\"text/javascript\" src=\"/__shim__/id-shim.js\"></script>\n</head>");
        }
        return Response.newFixedLengthResponse(Status.OK, "text/html; charset=utf-8", html);
      }

      // 其它静态资源：assets 直读（不存在则抛出 -> 404）
      InputStream in = am.open(stripLeadingSlash(path));
      return Response.newChunkedResponse(Status.OK, guessMime(path), in);

    } catch (IOException e) {
      // 严格返回 404，不做任何网络请求
      String msg = "404 Not Found (local-only): " + path;
      return Response.newFixedLengthResponse(FixedStatusCode.NOT_FOUND, "text/plain; charset=utf-8", msg);
    }
  }

  private String stripLeadingSlash(String p) {
    return p.startsWith("/") ? p.substring(1) : p;
  }

  private String guessMime(String path) {
    String p = path.toLowerCase();
    if (p.endsWith(".html")||p.endsWith(".htm")) return "text/html; charset=utf-8";
    if (p.endsWith(".js"))   return "application/javascript";
    if (p.endsWith(".css"))  return "text/css";
    if (p.endsWith(".svg"))  return "image/svg+xml";
    if (p.endsWith(".png"))  return "image/png";
    if (p.endsWith(".jpg")||p.endsWith(".jpeg")) return "image/jpeg";
    if (p.endsWith(".gif"))  return "image/gif";
    if (p.endsWith(".webp")) return "image/webp";
    if (p.endsWith(".mp4"))  return "video/mp4";
    if (p.endsWith(".m3u8")) return "application/vnd.apple.mpegurl";
    if (p.endsWith(".ts"))   return "video/mp2t";
    return "application/octet-stream";
  }

  private String readAll(InputStream in, String enc) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    int n;
    while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
    return bos.toString(enc);
  }
}
