package com.szzdmj.nanohttpd;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

class LocalHttpServer extends NanoHTTPD {
  private static final String TAG = "WebShell/LocalHttpServer";
  private final AssetManager am;

  LocalHttpServer(Context ctx, int port) {
    super("127.0.0.1", port);
    this.am = ctx.getAssets();
    Log.i(TAG, "constructed, port=" + port);
  }

  @Override
  public Response serve(IHTTPSession session) {
    String path = session.getUri(); // 形如 /index.html
    Log.i(TAG, "serve() uri=" + path + ", method=" + session.getMethod());
    if ("/".equals(path)) path = "/index.html";
    try {
      if ("/__shim__/id-shim.js".equals(path)) {
        Log.i(TAG, "hit id-shim.js");
        InputStream in = am.open("id-shim.js");
        return NanoHTTPD.newChunkedResponse(Response.Status.OK, "application/javascript", in);
      }

      if (path.endsWith(".html")) {
        InputStream inRaw = am.open(stripLeadingSlash(path));
        String html = readAll(inRaw, "UTF-8");
        String token = "<script type=\"text/javascript\" src=\"webjs.js\"></script>";
        if (html.contains(token)) {
          Log.i(TAG, "inject id-shim before webjs.js");
          html = html.replace(token,
            "<script type=\"text/javascript\" src=\"/__shim__/id-shim.js\"></script>\n" + token);
        } else {
          Log.i(TAG, "inject id-shim near </head>");
          html = html.replace("</head>",
            "  <script type=\"text/javascript\" src=\"/__shim__/id-shim.js\"></script>\n</head>");
        }
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html);
      }

      InputStream in = am.open(stripLeadingSlash(path));
      String mime = guessMime(path);
      Log.i(TAG, "static file: " + path + ", mime=" + mime);
      return NanoHTTPD.newChunkedResponse(Response.Status.OK, mime, in);

    } catch (IOException e) {
      String msg = "404 Not Found (local-only): " + path;
      Log.w(TAG, msg, e);
      return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain; charset=utf-8", msg);
    } catch (Throwable t) {
      Log.e(TAG, "serve() fatal for path=" + path, t);
      String msg = "500 Internal Server Error: " + t;
      return NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain; charset=utf-8", msg);
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
