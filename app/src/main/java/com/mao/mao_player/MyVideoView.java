package com.mao.mao_player;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.VideoView;

/**
 * Created by 毛麒添 on 2016/10/14 0014.
 *
 */

public class MyVideoView extends VideoView {
    private int width;
    private int heigth;

    public MyVideoView(Context context) {
        super(context);
    }

    public MyVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MyVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MyVideoView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setMeasure(int width,int heigth){
        this.width=width;
        this.heigth=heigth;
    }
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        // 默认高度，为了自动获取到焦点
        int width=MeasureSpec.getSize(widthMeasureSpec);
        int height=width;
        // 这个之前为默认的拉伸图像
        if(this.width>0&&this.heigth>0){
            width=this.width;
            height=this.heigth;
        }
        setMeasuredDimension(width,height);
    }
}
