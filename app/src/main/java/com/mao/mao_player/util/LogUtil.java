package com.mao.mao_player.util;

import android.annotation.TargetApi;
import android.util.Log;

/**
 * Created by 毛麒添 on 2016/10/12 0012.
 * 日志打印类
 */

public class LogUtil {
    private static final String TGA="毛麒添";
    private static boolean toggle=true;//切换
    private static boolean flogToggle=false;

    public LogUtil(){

    }
    public static final void i(String msg){
        Log.i(TGA,msg);
    }
    public static final void e(String msg){
        Log.e(TGA,msg);
    }
    public static final void d(String msg){
        Log.d(TGA,msg);
    }
    public static final void w(String msg){
        Log.w(TGA,msg);
    }
    @TargetApi(8)//版本上下兼容
    public static  final  void wtf(String msg){
        Log.wtf(TGA,msg);
    }
    public static final void il(String msg){
        i(msg);
    }
    public static final void setToggle(int flag){
        if(flag == -1) {
            toggle = false;
        }else{
            toggle=true;
        }
        switch(flag){
            case 0:
                flogToggle=true;
                break;
            case 1:
                flogToggle=false;
                break;
            case 2:
                flogToggle=true;
                break;
        }
    }
}
