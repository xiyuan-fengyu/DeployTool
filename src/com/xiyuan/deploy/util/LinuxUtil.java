package com.xiyuan.deploy.util;

import com.jcraft.jsch.*;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Created by xiyuan_fengyu on 2017/6/7.
 */
public class LinuxUtil {

    private static final JSch jsch = new JSch();

    //基于 jsch-0.1.54.jar 的实现
    public static ArrayList<String> execute(String ip, int port, String user, String password, String cmd) throws IOException, JSchException {
        ArrayList<String> stds = new ArrayList<>();

        Session session = null;
        ChannelExec channelExec = null;
        try {
            session = jsch.getSession(user, ip, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            channelExec = (ChannelExec) session.openChannel("exec");
            channelExec.setCommand(cmd);
            channelExec.setInputStream(null);
            channelExec.setErrStream(null);
            channelExec.connect();

            BufferedReader reader = new BufferedReader(new InputStreamReader(channelExec.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                stds.add(line);
            }
            reader.close();
        } finally {
            if (session != null) {
                session.disconnect();
            }
            if (channelExec != null) {
                channelExec.disconnect();
            }
        }

        return stds;
    }


//    //基于 ganymed-ssh2-build209.jar 的实现
//    public static ArrayList<String> execute(String ip, String user, String password, String cmd) {
//        ArrayList<String> stds = new ArrayList<>();
//
//        Connection connection = null;
//        Session session = null;
//        BufferedReader stdOut = null;
//        BufferedReader stdError = null;
//        try {
//            connection = new Connection(ip);
//            connection.connect();
//            if (connection.authenticateWithPassword(user, password)) {
//                session = connection.openSession();
//                session.execCommand(cmd);
//
//                stdOut = new BufferedReader(new InputStreamReader(session.getStdout()));
//                stdError = new BufferedReader(new InputStreamReader(session.getStderr()));
//
//                BufferedReader finalStdOut = stdOut;
//                Thread stdOutThread = new Thread(() -> {
//                    String line;
//                    try {
//                        while ((line = finalStdOut.readLine()) != null) {
//                            stds.add(line);
//                        }
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                });
//
//                BufferedReader finalStdError = stdError;
//                Thread stdErrorThread = new Thread(() -> {
//                    String line;
//                    try {
//                        while ((line = finalStdError.readLine()) != null) {
//                            stds.add(line);
//                        }
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                });
//
//                stdOutThread.start();
//                stdErrorThread.start();
//                stdOutThread.join();
//                stdErrorThread.join();
//            }
//        }
//        catch (Exception e) {
//            e.printStackTrace();
//            close(stdError);
//            close(stdOut);
//            close(session);
//            close(connection);
//        }
//
//        return stds;
//    }

    public static HashMap<String, String> listFilesMd5(String ip, int port, String user, String password, String path) throws IOException, JSchException {
        ArrayList<String> stds = execute(
                ip,
                port,
                user,
                password,
                "if [ ! -x \"/tmp/listFilesMd5.sh\" ]; then\necho '#!/bin/sh\nfunction listFiles() {\nfor file in ` ls $1$2 `\ndo\nif [ -d $1$2\"/\"$file ]\nthen\nlistFiles $1 $2\"/\"$file\nelse\nmd5=`md5sum $1$2\"/\"$file | cut -d \" \" -f1`\npre=$2\"/\"\necho -e ${pre:1}$file\"\\t\"$md5\nfi\ndone\n}\nlistFiles $1 \"\"' > /tmp/listFilesMd5.sh\nchmod +x /tmp/listFilesMd5.sh\nfi; /tmp/listFilesMd5.sh " + path + ";"
        );
        HashMap<String, String> md5s = new HashMap<>();
        stds.forEach(std -> {
            String[] split = std.split("\t");
            if (split.length == 2) {
                md5s.put(split[0], split[1]);
            }
        });
        return md5s;
    }

//    public static int createDic(String ip, String user, String password, String path) {
//        ArrayList<String> stds = execute(
//                ip,
//                user,
//                password,
//                "if [ ! -d \"" + path + "\" ]; then\nmkdir -p \"" + path + "\"\necho 1\nelse\necho 0\nfi"
//        );
//        if (stds.size() > 0 && stds.get(0).matches("0|1")) {
//            return Integer.parseInt(stds.get(0));
//        }
//        else return -1;
//    }

    public interface UploadListener {

        void onStart(int cur, int total, Object userData);

        void onSuccess(int cur, int total, Object userData);

        void onFail(int cur, int total, Object userData);

    }

    public static int[] uploadFiles(String ip, String user, String password, String localRoot, HashSet<String> localFiles, String remoteRoot, UploadListener listener) throws JSchException {
        int total = localFiles.size();
        int cur = 0;
        int success = 0;
        int fail = 0;

        Session session = null;
        ChannelSftp channelSftp = null;
        try {
            session = jsch.getSession(user, ip);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect();

            {
                String[] split = remoteRoot.split("/");
                String tempDir = "";
                for (int i = 0, len = split.length; i < len; i++) {
                    tempDir += "/" + split[i];
                    try {
                        channelSftp.mkdir(tempDir);
                    }
                    catch (Exception e) {
//                        e.printStackTrace();
                    }
                }

            }

            HashSet<String> dirs = new HashSet<>();

            for (String localFile : localFiles) {
                cur++;
                if (listener != null) {
                    listener.onStart(cur, total, localFile);
                }
                try {
                    String[] split = localFile.split("/");
                    String tempDir = "";
                    for (int i = 0, len = split.length; i < len - 1; i++) {
                        tempDir += "/" + split[i];
                        if (!dirs.contains(tempDir)) {
                            dirs.add(tempDir);
                            try {
                                channelSftp.mkdir(remoteRoot + tempDir);
                            }
                            catch (Exception e) {
//                                e.printStackTrace();
                            }
                        }
                    }

                    channelSftp.put(localRoot + "/" + localFile, remoteRoot + "/" + localFile);
                    success++;
                    if (listener != null) {
                        listener.onSuccess(success, total, localFile);
                    }
                }
                catch (Exception e) {
                    fail++;
                    if (listener != null) {
                        listener.onFail(fail, total, e.getMessage());
                    }
                }
            }
        } finally {
            if (session != null) {
                session.disconnect();
            }
            if (channelSftp != null) {
                channelSftp.disconnect();
            }
        }

        return new int[] {total, success, fail};
    }

//    private static void close(Object object) {
//        if (object != null) {
//            try {
//                Method closeMethod = object.getClass().getMethod("close");
//                closeMethod.invoke(object);
//            }
//            catch (Exception e) {}
//        }
//    }

}
