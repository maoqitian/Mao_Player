package com.mao.mao_player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by 毛麒添 on 2016/10/14 0014.
 * 监听开机启动广播
 * 开机启动应用
 */

public class BootBroadcastReceiver extends BroadcastReceiver {
    static final String ACTION = "android.intent.action.BOOT_COMPLETED";
    @Override
    public void onReceive(Context context, Intent intent) {
       if(intent.getAction().equals(ACTION)){
           Intent mainIntent=new Intent(context,MainActivity.class);//启动要启动的Activity
           mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
           context.startActivity(mainIntent);
       }
    }
}
