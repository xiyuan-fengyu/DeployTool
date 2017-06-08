package com.xiyuan.deploy.util;

import com.intellij.openapi.util.text.StringUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Created by xiyuan_fengyu on 2017/6/7.
 */
public class FileUtil {

    public static String get(String path) {
        return get(path, StandardCharsets.UTF_8);
    }

    public static String get(String path, Charset charset) {
        byte[] bytes = getBytes(path);
        return bytes == null ? null : new String(bytes, charset);
    }

    public static byte[] getBytes(String path) {
        try (InputStream in = new FileInputStream(new File(path))) {
            byte[] bytes = new byte[in.available()];
            in.read(bytes);
            return bytes;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String toSystemIndependentPaths(String paths) {
        String[] splitPaths = paths.trim().split(";");
        for (int i = 0; i < splitPaths.length; i++) {
            splitPaths[i] = com.intellij.openapi.util.io.FileUtil.toSystemIndependentName(splitPaths[i]);
        }
        return StringUtil.join(splitPaths, ";");
    }

}
