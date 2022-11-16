package com.sneva.spng;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Slogger {

    private static final int STACKTRACE_LIMIT = 50;

    private static String sLogFilePath;
    private static String sLogFileName;

    private static boolean sEnableLogcat = true;
    private static boolean sEnableSystemOut = false;
    private static boolean sEnableFileLog = false;

    public static final DateFormat sFileLogDateFormat = new SimpleDateFormat("yyyy-dd-MM HH:mm:ss.SSS", Locale.ENGLISH);

    public static enum LogType {
        ERROR,
        WARNING,
        DEBUG,
        INFO,
        VERBOSE;
    }

    public static void setEnableLogCat(boolean enable) {
        sEnableLogcat = enable;
    }

    public static void setEnableSystemOut(boolean enable) {
        sEnableSystemOut = enable;
    }

    public static void setEnableFileLog(boolean enable, String path, String fileName) {
        sEnableFileLog = enable;
        if (enable && path != null && fileName != null) {
            sLogFilePath = path.trim();
            sLogFileName = fileName.trim();

            if (!sLogFilePath.endsWith("/")) {
                sLogFilePath = sLogFilePath + "/";
            }
        }
    }

    public static String getLogFilePaht() {
        return sLogFilePath;
    }

    public static String getLogFileName() {
        return sLogFileName;
    }

    public static synchronized void v(String format, Object... args) {
        String msg = String.format(format, args);
        String tag = null;
        StackTraceElement element = getStackTraceElement();
        if (element != null) {
            tag = getTag(element);
            msg = getPrettyLog(element, msg);
        }
        if (sEnableLogcat) Log.v(tag, msg);
        if (sEnableSystemOut) System.out.println(msg);
        if (sEnableFileLog) writeLogToFile(LogType.VERBOSE, tag, msg);
    }

    public static synchronized void v(String msg, Throwable e) {
        String tag = null;
        StackTraceElement element = getStackTraceElement();

        if (element != null) {
            tag = getTag(element);
            msg = getPrettyLog(element, msg);
        }

        if (sEnableLogcat) Log.v(tag, msg, e);
        if (sEnableSystemOut) { System.out.println(msg); e.printStackTrace(); }
        if (sEnableFileLog) writeLogToFile(LogType.VERBOSE, tag, msg, e);
    }

    public static synchronized void d(String format, Object... args) {
        String msg = String.format(format, args);
        String tag = null;
        StackTraceElement element = getStackTraceElement();
        if (element != null) {
            tag = getTag(element);
            msg = getPrettyLog(element, msg);
        }
        if (sEnableLogcat) Log.d(tag, msg);
        if (sEnableSystemOut) System.out.println(msg);
        if (sEnableFileLog) writeLogToFile(LogType.DEBUG, tag, msg);
    }

    public static synchronized void d(String msg, Throwable e) {
        String tag = null;
        StackTraceElement element = getStackTraceElement();
        if (element != null) {
            tag = getTag(element);
            msg = getPrettyLog(element, msg);
        }
        if (sEnableLogcat) Log.d(tag, msg, e);
        if (sEnableSystemOut) { System.out.println(msg); e.printStackTrace(); }
        if (sEnableFileLog) writeLogToFile(LogType.DEBUG, tag, msg, e);
    }

    public static synchronized void i(String format, Object... args) {
        String msg = String.format(format, args);
        String tag = null;
        StackTraceElement element = getStackTraceElement();

        if (element != null) {
            tag = getTag(element);
            msg = getPrettyLog(element, msg);
        }
        if (sEnableLogcat) Log.i(tag, msg);
        if (sEnableSystemOut) System.out.println(msg);
        if (sEnableFileLog) writeLogToFile(LogType.INFO, tag, msg);
    }

    public static synchronized void i(String msg, Throwable e) {
        String tag = null;
        StackTraceElement element = getStackTraceElement();
        if (element != null) {
            tag = getTag(element);
            msg = getPrettyLog(element, msg);
        }
        if (sEnableLogcat) Log.i(tag, msg, e);
        if (sEnableSystemOut) { System.out.println(msg); e.printStackTrace(); }
        if (sEnableFileLog) writeLogToFile(LogType.INFO, tag, msg, e);
    }

    public static synchronized void w(String format, Object... args) {
        String msg = String.format(format, args);
        String tag = null;
        StackTraceElement element = getStackTraceElement();
        if (element != null) {
            tag = getTag(element);
            msg = getPrettyLog(element, msg);
        }
        if (sEnableLogcat) Log.w(tag, msg);
        if (sEnableSystemOut) System.out.println(msg);
        if (sEnableFileLog) writeLogToFile(LogType.WARNING, tag, msg);
    }

    public static synchronized void w(String msg, Throwable e) {
        String tag = null;
        StackTraceElement element = getStackTraceElement();
        if (element != null) {
            tag = getTag(element);
            msg = getPrettyLog(element, msg);
        }
        if (sEnableLogcat) Log.w(tag, msg, e);
        if (sEnableSystemOut) { System.out.println(msg); e.printStackTrace(); }
        if (sEnableFileLog) writeLogToFile(LogType.WARNING, tag, msg, e);
    }

    public static synchronized void e(String format, Object... args) {
        String msg = String.format(format, args);
        String tag = null;
        StackTraceElement element = getStackTraceElement();

        if (element != null) {
            tag = getTag(element);
            msg = getPrettyLog(element, msg);
        }

        if (sEnableLogcat) Log.e(tag, msg);
        if (sEnableSystemOut) System.out.println(msg);
        if (sEnableFileLog) writeLogToFile(LogType.ERROR, tag, msg);
    }

    public static synchronized void e(String msg, Throwable e) {
        String tag = null;
        StackTraceElement element = getStackTraceElement();
        if (element != null) {
            tag = getTag(element);
            msg = getPrettyLog(element, msg);
        }
        if (sEnableLogcat) Log.e(tag, msg, e);
        if (sEnableSystemOut) { System.out.println(msg); e.printStackTrace(); }
        if (sEnableFileLog) writeLogToFile(LogType.ERROR, tag, msg, e);
    }

    private static synchronized void writeLogToFile(LogType level, String tag, String msg, Throwable e) {
        String stacktrace = getStackTraceLog(msg, e);
        writeLogToFile(level, tag, stacktrace);
    }

    private static synchronized void writeLogToFile(LogType level, String tag, String msg) {
        if (sLogFilePath != null && !sLogFilePath.equals("") && sLogFileName != null && !sLogFileName.equals("")) {

            File f = new File(sLogFilePath);
            if (!f.exists()) {
                try {
                    f.mkdirs();
                } catch (SecurityException e) {

                }
            }

            if (f.canWrite()) {
                String log = getLogDisplay(level, tag, msg);
                f = new File(sLogFilePath + sLogFileName);
                try {
                    BufferedReader reader = new BufferedReader(new StringReader(log), 256);
                    BufferedWriter writer = new BufferedWriter(new FileWriter(f, true), 256);
                    String line;
                    while ((line = reader.readLine()) != null) {
                        writer.append(line);
                        writer.append("\r\n");
                    }
                    writer.flush();
                    writer.close();
                } catch (IOException e) {

                }
            }
        }
    }

    public static String getStackTraceLog(String msg, Throwable e) {
        StringBuilder b = new StringBuilder();
        b.append(msg).append("\n");
        if (e != null) {
            b.append(e.toString()).append("\n");
            StackTraceElement[] elements = e.getStackTrace();
            StackTraceElement element;
            if (elements != null) {
                for (int i = 0; i < elements.length && i < STACKTRACE_LIMIT; i++) {
                    element = elements[i];
                    if (element != null) {
                        b.append("\tat ").append(element.toString()).append("\n");
                    }
                }
                int more = elements.length - STACKTRACE_LIMIT;
                if (more > 0) {
                    b.append("\t... ").append(more).append(" more").append("\n");
                }
            }
        }
        return b.toString();
    }

    public static String getLogDisplay(LogType level, String tag, String msg) {
        StringBuilder builder = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new StringReader(msg), 256);
            Date date = new Date();
            date.setTime(System.currentTimeMillis());
            String time = sFileLogDateFormat.format(date);
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(String.format("%s: ", time));
                builder.append(String.format("%s/%s(%d): ", level, tag, android.os.Process.myPid()));
                builder.append(line);
                builder.append("\r\n");
            }
        } catch (IOException e) { /* ignore */ }
        return builder.toString();
    }

    private static StackTraceElement getStackTraceElement() {
        StackTraceElement element = null;
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        if (elements != null && elements.length >= 5) {
            element = elements[4];
        }
        return element;
    }

    private static String getTag(StackTraceElement element) {
        if (element == null) return null;
        String className = element.getClassName();
        if (className != null) {
            String[] paths = className.split("\\.");
            className = paths.length > 0 ? paths[paths.length - 1] : className;
        }
        return className;
    }

    private static String getPrettyLog(StackTraceElement element, String msg) {
        if (element == null) return msg;
        return String.format("%s # %s", element.getMethodName(), msg);
    }
}