package com.szzdmj.nanohttpd;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 轻量日志工具：把关键日志写入内部 files/crash.log（无需任何存储权限），同时输出到 Logcat。
 */
public final class CrashLogger {
  private static final String TAG = "WebShell/CrashLogger";
  private static volatile File logFile;

  private CrashLogger(){}

  public static void init(Context ctx) {
    if (logFile != null) return;
    try {
      File dir = ctx.getFilesDir(); // /data/data/<pkg>/files
      logFile = new File(dir, "crash.log");
      i("CrashLogger initialized. file=" + logFile.getAbsolutePath());
    } catch (Throwable t) {
      Log.e(TAG, "init failed", t);
    }
  }

  public static void i(String msg) {
    Log.i(TAG, msg);
    write("[INFO ] " + msg, null);
  }

  public static void w(String msg, Throwable t) {
    Log.w(TAG, msg, t);
    write("[WARN ] " + msg, t);
  }

  public static void err(String msg, Throwable t) {
    Log.e(TAG, msg, t);
    write("[ERROR] " + msg, t);
  }

  private static synchronized void write(String head, Throwable t) {
    if (logFile == null) return;
    FileWriter fw = null;
    PrintWriter pw = null;
    try {
      String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
      fw = new FileWriter(logFile, true);
      fw.write(ts + " " + head + "\n");
      if (t != null) {
        pw = new PrintWriter(fw);
        t.printStackTrace(pw);
        pw.flush();
      }
      fw.flush();
    } catch (Throwable e) {
      Log.e(TAG, "write failed", e);
    } finally {
      try { if (pw != null) pw.close(); } catch (Throwable ignore) {}
      try { if (fw != null) fw.close(); } catch (Throwable ignore) {}
    }
  }

  public static String getLogPath() {
    return logFile != null ? logFile.getAbsolutePath() : "(not-initialized)";
  }
}
