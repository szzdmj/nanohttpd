package com.szzdmj.nanohttpd;

import android.content.Context;
import android.content.res.AssetManager;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Status;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;

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

      // 强制本地：不存在就 404
      if (path.endsWith(".html")) {
        InputStream inRaw = am.open(stripLeadingSlash(path));
        String html = readAll(inRaw, "UTF-8");
        // 在 webjs.js 之前强制注入 id-shim（确保 4.4.4 先装配 ID 变量）
        String token = "<script type=\"text/javascript\" src=\"webjs.js\"></script>";
        if (html.contains(token)) {
          html = html.replace(token,
            "<script type=\"text/javascript\" src=\"/__shim__/id-shim.js\"></script>\n" + token);
        } else {
          html = html.replace("</head>",
            "  <script type=\"text/javascript\" src=\"/__shim__/id-shim.js\"></script>\n</head>");
        }
        return Response.newFixedLengthResponse(Status.OK, "text/html; charset=utf-8", html);
      }

      InputStream in = am.open(stripLeadingSlash(path));
      return Response.newChunkedResponse(Status.OK, guessMime(path), in);

    } catch (IOException e) {
      String msg = "404 Not Found (local-only): " + path;
      return Response.newFixedLengthResponse(Status.NOT_FOUND, "text/plain; charset=utf-8", msg);
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
    try {
      while ((n = in.read(buf)) > 0) {
        bos.write(buf, 0, n);
      }
    } finally {
      try { in.close(); } catch (Throwable ignore) {}
    }
    return bos.toString(enc);
  }
}
