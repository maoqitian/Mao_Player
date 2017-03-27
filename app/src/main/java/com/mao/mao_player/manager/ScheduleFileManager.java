package com.mao.mao_player.manager;

import android.content.Context;
import android.widget.Toast;

import com.google.gson.Gson;
import com.mao.mao_player.model.AssetModel;
import com.mao.mao_player.model.ScheduleModel;
import com.mao.mao_player.util.LogUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by 毛麒添 on 2016/10/14 0014.
 * 播放文件顺序生成类
 * 使用JSON数据格式
 */

public class ScheduleFileManager {
    public static final String scheduleFilename = "schedule.json";
    private final Context context;
    private final ScheduleModel schedule;
    private final Gson gson;

    public ScheduleFileManager(Context context) {
        this.context = context;
        schedule = new ScheduleModel();
        gson = new Gson();
    }

    /**
     * 获取视频资源或者图片资源
     */
    public AssetModel[] getAssets(String sourceDir, int filetype) {
        AssetModel[] assets = null;
        try {
            ArrayList<String> filters = new ArrayList<String>();
            //视频格式
            if (filetype == 1) {
                filters.add("mp4");
                filters.add("mpg");
                filters.add("avi");} else {
                //图片
                filters.add("jpg");
                filters.add("jpeg");
                filters.add("gif");
                filters.add("png");}
            //获得文件
            File[] files = DirectoryManager.getFileFilterType(sourceDir, filters);
            //排升序
            Arrays.sort(files);
            ArrayList<AssetModel> arrayList = new ArrayList<AssetModel>();
            //遍历文件名字数组设置状态
            for (int i = 0; i < files.length; i++) {
                File file = files[i];
                String filename = file.getName();
                String type = null;
                filename = filename.toLowerCase();
                if (filename.endsWith("mp4") || filename.endsWith("mpg")
                        || filename.endsWith("avi")) {
                    type = AssetModel.TAG_VIDEO;
                } else if (filename.endsWith("jpg") || filename.endsWith("gif")
                        || filename.endsWith("png")) {
                    type = AssetModel.TAG_PHOTO;
                }
                if (type != null) {
                    AssetModel asset = new AssetModel();
                    asset.setSrc(file.getName());
                    asset.setType(type);
                    arrayList.add(asset);
                }
            }
          assets=arrayList.toArray(new AssetModel[arrayList.size()]);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return assets;
    }
    //设置资源
    public void saveAssets(AssetModel[] assets,String path){
        schedule.setAssets(assets);
        //JSON序列化
        String scheduleString=gson.toJson(schedule,ScheduleModel.class);
        write(scheduleString,path);
    }
    public void write(String scheduleData, String dirPath) {
        try {
            DirectoryManager.writeFileOnSDCard(scheduleData,dirPath,
                    "schedule.json");
        }catch (Exception e){
            LogUtil.e(dirPath+File.separator+ "schedule.json");
            Toast.makeText(context, dirPath + File.separator + "schedule.json",
                    Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }
    //获得资源
    public ScheduleModel getSchedule(String dirPath){
        ScheduleModel eventsSchedule=new ScheduleModel();
        String scheduleString =read(dirPath);
        if(scheduleString!=null){
            //反序列化
            eventsSchedule=gson.fromJson(scheduleString,ScheduleModel.class);
        }
        return eventsSchedule;
    }
    public String read(String dirPath) {
        String scheduleString=null;
        try {
            scheduleString = DirectoryManager.readFileFromSDCard(dirPath, scheduleFilename);
        }catch (FileNotFoundException e){
            LogUtil.e(e.toString());
        } catch (Exception e) {
            LogUtil.e(dirPath + File.separator + scheduleFilename);
            Toast.makeText(context,
                    dirPath + File.separator + scheduleFilename,
                    Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
        return scheduleString;
    }



     //检查目录
    public static boolean checkDirectory(File dir){
        return dir.exists()&&dir.isDirectory();
    }
}
