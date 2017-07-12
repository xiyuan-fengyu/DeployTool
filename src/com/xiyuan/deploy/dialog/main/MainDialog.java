package com.xiyuan.deploy.dialog.main;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.*;
import com.intellij.util.ui.UIUtil;
import com.jcraft.jsch.JSchException;
import com.xiyuan.deploy.keys.Keys;
import com.xiyuan.deploy.util.FileUtil;
import com.xiyuan.deploy.util.LinuxUtil;
import com.xiyuan.deploy.util.Md5Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

public class MainDialog extends DialogWrapper {
    private JPanel contentPane;
    private com.intellij.openapi.ui.TextFieldWithBrowseButton localPath;
    private CheckboxTree localFileTree;
    private JTextField remoteIpInput;
    private JTextField remoteUserInput;
    private JPasswordField remotePasswordInput;
    private JTextField remotePathInput;
    private JCheckBox autoRestartServer;
    private JTextField stopServerCmdInput;
    private JTextArea logs;
    private JTextField startServerCmdInput;
    private JTextField remotePortInput;

    private Project project;

    private PropertiesComponent propertiesComponent;

    private HashSet<String> curSelectedFiles = new HashSet<>();

    public MainDialog(Project project, String title) {
        super(project);

        this.project = project;
        propertiesComponent = PropertiesComponent.getInstance(project);
        init();
        setTitle(title);

        setOKActionEnabled(true);
    }

    @Override
    protected void doOKAction() {
        String ip = remoteIpInput.getText();
        if (ip.equals("")) {
            logs.append("请输入远程服务器的ip\n");
            return;
        }

        String portStr = remotePortInput.getText();
        if (portStr.equals("")) {
            logs.append("请输入远程服务器的port\n");
            return;
        }
        else if (!portStr.matches("\\d+")) {
            logs.append("远程服务器的port应该为整数\n");
            return;
        }
        int port = Integer.parseInt(portStr);

        String user = remoteUserInput.getText();
        if (user.equals("")) {
            logs.append("请输入登录远程服务器所需的用户名\n");
            return;
        }

        String password = String.valueOf(remotePasswordInput.getPassword());
        if (password.equals("")) {
            logs.append("请输入登录远程服务器所需的密码\n");
            return;
        }

        String remotePath = remotePathInput.getText();
        if (remotePath.equals("")) {
            logs.append("请输入上传远程服务器的路径\n");
            return;
        }

        if (autoRestartServer.isSelected()) {
            if (stopServerCmdInput.getText().equals("")) {
                logs.append("请输入停止服务器的命令\n");
                return;
            }

            if (startServerCmdInput.getText().equals("")) {
                logs.append("请输入启动服务器的命令\n");
                return;
            }
        }

        if (curSelectedFiles.size() == 0) {
            logs.append("没有选中任何文件\n");
            return;
        }

        setOKActionEnabled(false);

        //获取服务器上 remotePath 目录下的有所文件及其md5码,并与本地选中的文件作对比，然后上传改动的文件
        logs.append("正在获取服务器端文件列表\n");
        new Thread(() -> {
            HashMap<String, String> remoteFileMd5s = null;
            try {
                remoteFileMd5s = LinuxUtil.listFilesMd5(ip, port, user, password, remotePath);
            } catch (IOException | JSchException e) {
                logs.append(e.toString());
                return;
            }

            String localPathStr = localPath.getText();
            HashSet<String> changedFiles = new HashSet<>();
            for (String selectedFile : curSelectedFiles) {
                String cutSelectedFile = selectedFile.substring(localPathStr.length() + (localPathStr.endsWith("/") ? 0 : 1));
                String remoteMd5 = remoteFileMd5s.get(cutSelectedFile);
                if (remoteMd5 == null) {
                    changedFiles.add(cutSelectedFile);
                }
                else {
                    String localMd5 = Md5Util.get(FileUtil.getBytes(selectedFile));
                    if (!remoteMd5.equals(localMd5)) {
                        changedFiles.add(cutSelectedFile);
                    }
                }
            }

            if (changedFiles.size() == 0) {
                logs.append("所选文件中没有发生改变\n");
                return;
            }

            logs.append("所选文件中共 " + changedFiles.size() + " 个需要上传\n\n");
            logs.append("开始上传文件\n\n");
            int[] result = new int[0];
            try {
                result = LinuxUtil.uploadFiles(ip, user, password, localPathStr, changedFiles, remotePath, new LinuxUtil.UploadListener() {
                    @Override
                    public void onStart(int cur, int total, Object userData) {
                        logs.append("正在上传 " + userData + "    " + cur + " / " + total + "\n");
                    }

                    @Override
                    public void onSuccess(int cur, int total, Object userData) {
                        logs.append("上传成功 " + cur + " / " + total + "\n");
                    }

                    @Override
                    public void onFail(int cur, int total, Object userData) {
                        logs.append("上传失败 " + cur + " / " + total + "\t" + userData +  "\n");
                    }
                });
            } catch (JSchException e) {
                logs.append(e.toString());
                return;
            }

            logs.append("\n文件上传全部完成\n全部：" + result[0] + "\t成功：" + result[1] + "\t失败：" + result[2] + "\n\n");

            if (autoRestartServer.isSelected()) {
                String stopCmd = stopServerCmdInput.getText();
                if (!"".equals(stopCmd)) {
                    logs.append("正在停止服务器\n");
                    try {
                        LinuxUtil.execute(ip, port, user, password, stopCmd);
                    } catch (IOException | JSchException e) {
                        logs.append(e.toString());
                        return;
                    }
                    logs.append("服务器已停止\n\n");
                }

                String startCmd = startServerCmdInput.getText();
                if (!"".equals(startCmd)) {
                    logs.append("正在启动服务器\n");
                    try {
                        LinuxUtil.execute(ip, port, user, password, startCmd);
                    } catch (IOException | JSchException e) {
                        logs.append(e.toString());
                        return;
                    }
                    logs.append("服务器已启动\n\n");
                }
            }
        }).start();
    }

    @Override
    public void doCancelAction() {
        saveUserDataDelay(0);
        super.doCancelAction();
    }

    @Override
    protected void init() {
        super.init();

        initLocalPath();
        initLocalFileTree();

        remoteIpInput.setText(propertiesComponent.getValue(Keys.remoteIp, ""));
        remotePortInput.setText(propertiesComponent.getValue(Keys.remotePort, "22"));
        remoteUserInput.setText(propertiesComponent.getValue(Keys.remoteUser, ""));
        remotePasswordInput.setText(propertiesComponent.getValue(Keys.remotePassword, ""));
        remotePathInput.setText(propertiesComponent.getValue(Keys.remotePath, ""));
        autoRestartServer.setSelected(propertiesComponent.getBoolean(Keys.autoRestartServer));
        stopServerCmdInput.setText(propertiesComponent.getValue(Keys.stopServerCmd, ""));
        startServerCmdInput.setText(propertiesComponent.getValue(Keys.startServerCmd, ""));

        logs.setAutoscrolls(true);
    }

    private void initLocalPath() {
        String oldLocalPath = propertiesComponent.getValue(Keys.localPath);
        if (oldLocalPath == null) {
            oldLocalPath = project.getBasePath();
        }
        localPath.setText(oldLocalPath);
        localPath.addBrowseFolderListener(new TextBrowseFolderListener(new FileChooserDescriptor(false, true, false, false, false, false), project) {
            @Override
            protected void onFileChosen(@NotNull VirtualFile chosenFile) {
                String newLocalPath = FileUtil.toSystemIndependentPaths(chosenFile.getPath());
                localPath.setText(newLocalPath);
                updateLocalFileTree();
            }
        });
        localPath.getTextField().getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateLocalFileTree();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateLocalFileTree();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateLocalFileTree();
            }
        });
    }

    private void initLocalFileTree() {
        CheckboxTree.CheckboxTreeCellRenderer renderer = new CheckboxTree.CheckboxTreeCellRenderer() {
            @Override
            public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                if(value instanceof CheckedTreeNode) {
                    // Fix GTK background
                    if (UIUtil.isUnderGTKLookAndFeel()) {
                        final Color background = selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground();
                        UIUtil.changeBackGround(this, background);
                    }
                    final CheckedTreeNode node = (CheckedTreeNode)value;
                    final Object userObject = node.getUserObject();
                    String text = null;
                    SimpleTextAttributes attributes = null;
                    Icon icon = null;
                    if (userObject == null) {
                        text = "ERROR";
                        attributes = SimpleTextAttributes.ERROR_ATTRIBUTES;
                        icon = null;
                    }
                    else if (userObject instanceof VirtualFile) {
                        VirtualFile file = (VirtualFile) userObject;
                        text = file.getName();
                        attributes = file.isDirectory() ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES;
                        icon = file.isDirectory() ? AllIcons.Nodes.Folder : file.getFileType().getIcon();
                    }
                    else if (userObject instanceof String) {
                        text = (String)userObject;
                        attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
                        icon = AllIcons.FileTypes.Unknown;
                    }

                    final ColoredTreeCellRenderer textRenderer = getTextRenderer();
                    if (icon != null) {
                        textRenderer.setIcon(icon);
                    }
                    if (text != null) {
                        textRenderer.append(text, attributes);
                    }
                }
            }
        };
        localFileTree.setCellRenderer(renderer);
        localFileTree.addCheckboxTreeListener(new CheckboxTreeListener() {
            @Override
            public void mouseDoubleClicked(@NotNull CheckedTreeNode checkedTreeNode) {
            }

            @Override
            public void nodeStateChanged(@NotNull CheckedTreeNode checkedTreeNode) {
                VirtualFile virtualFile = (VirtualFile) checkedTreeNode.getUserObject();
                if (!virtualFile.isDirectory()) {
                    String path = FileUtil.toSystemIndependentPaths(virtualFile.getPath());
                    if (checkedTreeNode.isChecked()) {
                        curSelectedFiles.add(path);
                    }
                    else {
                        curSelectedFiles.remove(path);
                    }
                    saveUserDataDelay(1000);
                }
            }

            @Override
            public void beforeNodeStateChanged(@NotNull CheckedTreeNode checkedTreeNode) {
            }
        });

        updateLocalFileTree();
    }

    private void updateLocalFileTree() {
        curSelectedFiles.clear();
        try {
            String curLocalPath = localPath.getText();
            VirtualFile root = VirtualFileManager.getInstance().findFileByUrl("file://" + curLocalPath);
            if (root != null && root.exists() && root.isDirectory()) {
                String oldLocalPath = propertiesComponent.getValue(Keys.localPath);
                if (curLocalPath.equals(oldLocalPath)) {
                    String[] oldSelectedFiles = propertiesComponent.getValues(Keys.selectedFiles);
                    if (oldSelectedFiles != null) {
                        for (String file : oldSelectedFiles) {
                            if (!file.equals("")) {
                                curSelectedFiles.add(file);
                            }
                        }
                    }
                }
                localFileTree.setModel(new DefaultTreeModel(dicToTreeNode(root)));
            }
            else {
                localFileTree.setModel(new DefaultTreeModel(null));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        saveUserDataDelay(1000);
    }

    private DelyThread saveUserDataThread;

    private void saveUserDataDelay(long delay) {
        long lastSaveTime = System.currentTimeMillis();
        if (saveUserDataThread == null || lastSaveTime > saveUserDataThread.fromTime + saveUserDataThread.delay) {
            if (saveUserDataThread != null) {
                saveUserDataThread.cancle();
            }
            saveUserDataThread = new DelyThread(lastSaveTime, delay) {
                @Override
                public void run() {
                    try {
                        if (delay > 0) {
                            Thread.sleep(delay);
                        }
                        if (!cancled) {
                            propertiesComponent.setValue(Keys.localPath, localPath.getText());

                            String[] files = new String[curSelectedFiles.size()];
                            curSelectedFiles.toArray(files);
                            propertiesComponent.setValues(Keys.selectedFiles, files);

                            propertiesComponent.setValue(Keys.remoteIp, remoteIpInput.getText());
                            propertiesComponent.setValue(Keys.remoteUser, remoteUserInput.getText());
                            propertiesComponent.setValue(Keys.remotePort, remotePortInput.getText());
                            propertiesComponent.setValue(Keys.remotePassword, String.valueOf(remotePasswordInput.getPassword()));
                            propertiesComponent.setValue(Keys.remotePath, remotePathInput.getText());
                            propertiesComponent.setValue(Keys.autoRestartServer, autoRestartServer.isSelected());
                            propertiesComponent.setValue(Keys.stopServerCmd, stopServerCmdInput.getText());
                            propertiesComponent.setValue(Keys.startServerCmd, startServerCmdInput.getText());
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            };
            saveUserDataThread.setDaemon(true);
            saveUserDataThread.start();
        }
    }

    private CheckedTreeNode dicToTreeNode(VirtualFile dic) {
        CheckedTreeNode dicNode = new CheckedTreeNode(dic);
        dicNode.setChecked(false);
        VirtualFile[] files = dic.getChildren();
        if (files != null) {
            for (VirtualFile file : files) {
                if (file.isDirectory()) {
                    dicNode.add(dicToTreeNode(file));
                }
                else {
                    CheckedTreeNode node = new CheckedTreeNode(file);
                    node.setChecked(curSelectedFiles.contains(FileUtil.toSystemIndependentPaths(file.getPath())));
                    dicNode.add(node);
                }
            }
        }
        return dicNode;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return contentPane;
    }

    private class DelyThread extends Thread {

        final long fromTime;

        final long delay;

        boolean cancled = false;

        private DelyThread(long fromTime, long delay) {
            this.fromTime = fromTime;
            this.delay = delay;
        }

        private void cancle() {
            this.cancled = true;
        }

    }

}
