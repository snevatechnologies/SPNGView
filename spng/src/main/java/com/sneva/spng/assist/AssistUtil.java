package com.sneva.spng.assist;

import static com.sneva.spng.ApngImageLoader.enableDebugLog;
import static com.sneva.spng.ApngImageLoader.enableVerboseLog;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.sneva.spng.Slogger;

import java.io.File;
import java.security.MessageDigest;
import java.util.Arrays;

import ar.com.hjg.pngj.PngReaderApng;

public class AssistUtil {

    public static final long MAX_SIZE = 5*1000*1000;

    private AssistUtil() {

    }

    public static void checkCahceSize(File cacheDir, long maxSize) {
        long cacheSize = getDirSize(cacheDir);
        if (enableVerboseLog) Slogger.v("checkCacheSize: %d", cacheSize);
        if (maxSize < 1 && cacheSize >= MAX_SIZE) {
            cleanDir(cacheDir, cacheSize - MAX_SIZE);
        } else if (maxSize > 0 && cacheSize >= maxSize) {
            cleanDir(cacheDir, cacheSize - maxSize);
        }
    }

    private static void cleanDir(File dir, long bytes) {
        long bytesDeleted = 0;
        File[] files = listFilesSortingByDate(dir);
        for (File file : files) {
            bytesDeleted += file.length();
            boolean isSuccess = file.delete();
            if (enableVerboseLog) Slogger.v("Delete(%s): %s", isSuccess ? "success" : "failed", file.getPath());
            if (bytesDeleted >= bytes) {
                break;
            }
        }
    }

    private static long getDirSize(File dir) {
        long size = 0;
        File[] files = listFilesSortingByDate(dir);
        for (File file : files) {
            if (file.isFile()) {
                size += file.length();
            }
        }
        return size;
    }

    public static File[] listFilesSortingByDate(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            Pair[] pairs = new Pair[0];
            pairs = new Pair[files.length];
            for (int i = 0; i < files.length; i++) {
                pairs[i] = new Pair(files[i]);
            }
            Arrays.sort(pairs);
            for (int i = 0; i < files.length; i++) {
                files[i] = pairs[i].f;
            }
        }
        return files;
    }

    public static boolean isApng(File file) {
        boolean isApng = false;
        try {
            PngReaderApng reader = new PngReaderApng(file);
            reader.end();
            int apngNumFrames = reader.getApngNumFrames();
            isApng = apngNumFrames > 1;
        } catch (Exception e) {
            if (enableDebugLog) Slogger.w("Error: %s", e.toString());
        }
        return isApng;
    }

    public static File getWorkingDir(Context context) {
        File workingDir = null;
        File cacheDir = context.getExternalCacheDir();
        if (cacheDir == null) {
            cacheDir = context.getCacheDir();
        }
        if (cacheDir != null) {
            workingDir = new File(String.format("%s/apng/.nomedia/", cacheDir.getPath()));
            if (!workingDir.exists()) {
                workingDir.mkdirs();
            }
        }
        return workingDir;
    }

    public static File getCopiedFile(Context context, String imageUri) {
        String filename;
        try {
            filename = String.format("%s.png", md5(imageUri));
        } catch (Exception e) {
            filename = Uri.parse(imageUri).getLastPathSegment();
        }
        File workingDir = getWorkingDir(context);
        File f = null;
        if (workingDir != null && workingDir.exists()) {
            f = new File(workingDir, filename);
        }
        return f;
    }

    private static final char[] HEX_ARRAY = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};

    private static String md5(String message) throws Exception {
        MessageDigest md = MessageDigest.getInstance("md5");
        return bytesToHex(md.digest(message.getBytes("utf-8")));
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for ( int j = 0; j < bytes.length; j++ ) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    static class Pair implements Comparable<Pair> {
        public long t;
        public File f;

        public Pair(File file) {
            f = file;
            t = file.lastModified();
        }

        public int compareTo(@NonNull Pair o) {
            long u = o.t;
            return t < u ? -1 : t == u ? 0 : 1;
        }
    }
}
