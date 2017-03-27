package com.mao.mao_player.manager;

import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by 毛麒添 on 2016/10/14 0014.
 * 目录管理类
 */

public class DirectoryManager {

    public static final String DEFAULT_KNC_DIR_PATH = Environment.getExternalStorageDirectory().getPath() +
            File.separator
            + "ONKYO";
    public static String WAIT_DIR_PATH = null;
    public static String DEMO_DIR_PATH = null;
    public static String STANDBY_DIR_PATH = null;
    public static String KNC_DIR_PATH = null;
    public static String LOG_DIR_PATH = null;

    //获取文件
    public static File[] getFiles(String dirPath) throws Exception {
        return new File(dirPath).listFiles();
    }
    /**
     * 获得资源文件集合
     */
    public static File[] getFileFilterType(String dirPath,
                                           List<String> extensionFilters) throws Exception {
        ArrayList<File> filesTemp = new ArrayList<File>();
        if (new File(dirPath).exists()) {
            File[] files = getFiles(dirPath);
            for (int i = 0; i < files.length; i++) {
                String name = files[i].getName();
                //截取文件后缀名
                String extension = name.substring(name.lastIndexOf(".") + 1).toLowerCase();
                if (extensionFilters != null && extensionFilters.contains(extension)) {
                    filesTemp.add(files[i]);
                }
            }
        }
        return filesTemp.toArray(new File[filesTemp.size()]);
    }

    /**
     * 判断sdcard是否可读
     */
    public static boolean isSDReadable() {
        try {
            return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 写入sdcard
     */
    public static boolean writeFileOnSDCard(String dataStr, String dirPath, String fileName) {
        try {
            if (isSDReadable()) {
                File file = new File(dirPath);
                if (!file.exists()) {
                    file.mkdirs();
                }
                FileOutputStream fos = new FileOutputStream(new File(
                        file + File.separator + fileName));
                byte[] bytes = dataStr.getBytes();
                fos.write(bytes);
                fos.close();
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 从sdcard中读取文件
     */
    public static String readFileFromSDCard(File file) throws Exception {
        String stringContent = "";
        if (isSDReadable()) {
            FileInputStream fis = new FileInputStream(file);
            if (fis != null) {
                InputStreamReader inputStreamReader = new InputStreamReader(fis);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                StringBuilder stringBuilder = new StringBuilder();
                String readString = "";
                while ((readString = bufferedReader.readLine()) != null) {
                    stringBuilder.append(readString);
                }
                bufferedReader.close();
                inputStreamReader.close();
                stringContent = stringBuilder.toString();
            }
            fis.close();
        }
        return stringContent;
    }

    public static String readFileFromSDCard(String dirPath, String fileName) throws Exception {
        String stringContent = "";
        if (isSDReadable()) {
            stringContent = readFileFromSDCard(new File(dirPath, fileName));
        }
        return stringContent;
    }

    /**
     * 检查文件是否存在
     */
    public static boolean checkIfFileExist(File file) {
        return file.exists();
    }

    /**
     * 目录
     */
    public static void setDefaultKncDirectory() {
        KNC_DIR_PATH = DEFAULT_KNC_DIR_PATH;
        STANDBY_DIR_PATH = KNC_DIR_PATH + File.separator + "standby";
        DEMO_DIR_PATH = KNC_DIR_PATH + File.separator + "demo";
        WAIT_DIR_PATH = KNC_DIR_PATH + File.separator + "wait";
        LOG_DIR_PATH = KNC_DIR_PATH + File.separator + "log";
    }


    public static void initializeKncDirectoryPath() {
        setDefaultKncDirectory();
    }
}
