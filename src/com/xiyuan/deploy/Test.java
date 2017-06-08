package com.xiyuan.deploy;

import com.xiyuan.deploy.util.FileUtil;
import com.xiyuan.deploy.util.Md5Util;

import java.nio.charset.StandardCharsets;

/**
 * Created by xiyuan_fengyu on 2017/6/8.
 */
public class Test {

    public static void main(String[] args) {
        System.out.println(Md5Util.get(FileUtil.get("D:\\SoftwareForCode\\MyEclipseProject\\DeployTool\\src\\com\\xiyuan\\deploy\\util\\Md5Util.java", StandardCharsets.UTF_8)));
    }

}