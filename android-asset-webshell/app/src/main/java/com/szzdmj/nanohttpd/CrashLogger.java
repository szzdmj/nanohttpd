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
 * 轻量日志工具：把关键日志写入
 * 1) 内部 files/crash.log（无需任何权限）
 * 2) 外部私有目录 /sdcard/Android/data/<pkg>/files/crash.log（同样无需权限）
 */
public final class CrashLogger {
  private static final String TAG = "WebShell/CrashLogger";
  private static volatile File internalLogFile;
  private static volatile File externalLogFile;

  private CrashLogger(){}

  public static void init(Context ctx) {
    if (internalLogFile != null) return;
    try {
      File dir = ctx.getFilesDir(); // /data/data/<pkg>/files
      internalLogFile = new File(dir, "crash.log");
      try {
        File ext = ctx.getExternalFilesDir(null); // /sdcard/Android/data/<pkg>/files
        if (ext != null) {
          externalLogFile = new File(ext, "crash.log");
        }
      } catch (Throwable t) {
        Log.w(TAG, "external files dir not available", t);
      }
      i("CrashLogger initialized. internal=" + internalLogFile.getAbsolutePath()
          + (externalLogFile != null ? (", external=" + externalLogFile.getAbsolutePath()) : ", external=(null)"));
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
    String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    writeOne(internalLogFile, ts, head, t);
    writeOne(externalLogFile, ts, head, t);
  }

  private static void writeOne(File f, String ts, String head, Throwable t) {
    if (f == null) return;
    FileWriter fw = null;
    PrintWriter pw = null;
    try {
      fw = new FileWriter(f, true);
      fw.write(ts + " " + head + "\n");
      if (t != null) {
        pw = new PrintWriter(fw);
        t.printStackTrace(pw);
        pw.flush();
      }
      fw.flush();
    } catch (Throwable e) {
      Log.e(TAG, "write failed (" + f + ")", e);
    } finally {
      try { if (pw != null) pw.close(); } catch (Throwable ignore) {}
      try { if (fw != null) fw.close(); } catch (Throwable ignore) {}
    }
  }

  public static String getLogPath() {
    return (internalLogFile != null ? internalLogFile.getAbsolutePath() : "(not-initialized)")
        + (externalLogFile != null ? (" | " + externalLogFile.getAbsolutePath()) : "");
  }
}
