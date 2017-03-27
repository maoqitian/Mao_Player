package com.mao.mao_player.model;

/**
 * Created by 毛麒添 on 2016/10/13 0013.
 * 资源模型类
 */

public class AssetModel {
    public static final String TAG_VIDEO = "video";
    public static final String TAG_PHOTO = "photo";
    private String src;//文件名称
    private String type;//文件类型

    public String getSrc() {
        return src;
    }

    public void setSrc(String src) {
        this.src = src;
    }
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
