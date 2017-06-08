package com.xiyuan.deploy.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.xiyuan.deploy.dialog.main.MainDialog;

/**
 * Created by xiyuan_fengyu on 2017/6/7.
 */
public class OpenDeployToolAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        MainDialog dialog = new MainDialog(e.getProject(), e.getPresentation().getText());
        dialog.show();
    }

}
