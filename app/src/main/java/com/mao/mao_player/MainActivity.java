package com.mao.mao_player;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.mao.mao_player.log.LogcatFileManager;
import com.mao.mao_player.manager.DirectoryManager;
import com.mao.mao_player.manager.ScheduleFileManager;
import com.mao.mao_player.model.AssetModel;
import com.mao.mao_player.util.FormatUtil;
import com.mao.mao_player.util.LogUtil;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;

/**
 * 1.standby模式：循环播放，接受按键切换到demo模式。
 * 2.demo模式：播放结束切换到wait模式。如果接受按键，则直接播放下一曲
 * 3.wait模式：如果没有接受按键，一直播放下一个图片，播放完就切换stanby模式。如果接受按键是切换demo模式
 */
public class MainActivity extends AppCompatActivity {
    //三种状态
    private final int MODE_STANDBY = 1;
    private final int MODE_DEMO = 2;
    private final int MODE_WAIT = 3;

    private final int TYPE_VIDE = 1;
    private final int TYPE_IMAGE = 2;

    //WAIT等待状态时间
    private final int DEFAULT_WAIT_TIME = 10 * 1000;
    private final int DEFAULT_SW_KEYCODE = KeyEvent.KEYCODE_VOLUME_DOWN;

    private static final String DEFAULT_LAUNCHER_TAP_PASSWORD = "4321";
    private static final long MINIMUE_SECONDS_TAP = 3000;

    private RelativeLayout.LayoutParams params;
    private RelativeLayout assetsContainerView;//容器view对象
    private ScheduleFileManager scheduleManager;

    //资源实体对象
    private AssetModel[] mAssetsStandby;
    private AssetModel[] mAssetsDemo;
    private AssetModel[] mAssetsWait;

    private MyVideoView videoView;
    private View previousAssetView;
    private ImageView imageView;

    private Handler handler = new Handler();
    private Handler handlerHid = new Handler();

    private int mCurMode = MODE_STANDBY;
    private int mCurPlayIndex = 0;
    private float xTouch;
    private float yTouch;
    private long touchStartTime;
    private String tapValue;

    private static final String ACTION_USB_PERMISSION = "com.mao.mao_player.USB_PERMISSION";

    //开启子线程来进行模式的选择操作
    private Runnable assetSelector = new Runnable() {
        @Override
        public void run() {
            switch (mCurMode) {
                case MODE_STANDBY:
                    standbyModePro();
                    break;
                case MODE_DEMO:
                    demoModePro();
                    break;
                case MODE_WAIT:
                    waitModePro();
                    break;
            }
            sendModeText();
        }
    };

    private TextView tvTip;
    private PendingIntent pendingIntent;
    private UsbManager usbManager;
    private UsbDevice mUsbDevice;
    private UsbInterface mInterface;
    private UsbDeviceConnection mDeviceConnection;
    //通信输入输出端口
    private UsbEndpoint epOut;
    private UsbEndpoint epIn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //设置全屏
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        //子线程中判断连接权限，打开设备，找到端口
        new Thread(new Runnable() {
            @Override
            public void run() {
                checkUsbpermission();
                openUsbDevice();
                findIntfAndEpt();
            }
        }).start();

        DirectoryManager.initializeKncDirectoryPath();//初始化路径
        LogcatFileManager.getInstance().start(DirectoryManager.LOG_DIR_PATH);

        scheduleManager = new ScheduleFileManager(this);
        initLayoutParam();
        initView();
        LogUtil.i("################################################################");
        LogUtil.i("################################################################");
        LogUtil.i("################################################################");
        LogUtil.i("Power ON");
        getAPPVersionFromAPP(this);
        LogUtil.i("Standby Mode");
    }

    private void openUsbDevice() {
        //获取设备
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            return;
        } else {
            LogUtil.i("usb设备：" + usbManager.toString());
        }
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        LogUtil.i("usb设备：" + deviceList.size());
        //迭代器
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while (deviceIterator.hasNext()) {
            UsbDevice device = deviceIterator.next();
            LogUtil.i("USB设备信息DeviceInfo: " + device.getVendorId() + " , "
                    + device.getProductId());
            //添加设备处理代码
            if (device.getVendorId() == 1240 && device.getProductId() == 63) {
                mUsbDevice = device;
                LogUtil.i("找到USB设备");
            }
        }
    }

    //查找询问是否允许权限，注册广播
    private void checkUsbpermission() {
        pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);
    }

    //寻找接口和分配结点
    private void findIntfAndEpt() {
        if (mUsbDevice == null) {
            LogUtil.i("没有找到USB设备");
            return;}
        for (int i = 0; i < mUsbDevice.getInterfaceCount(); ) {
             /*获取设备接口，一般都是一个接口，你可以打印getInterfaceCount()方法查看接
             口的个数，在这个接口上有两个端点，OUT 和 IN*/
            UsbInterface intf = mUsbDevice.getInterface(i);
            LogUtil.i(i + "  " + intf);
            //得到接口
            mInterface = intf;
            break;}
        if (mInterface != null) {
            UsbDeviceConnection connection = null;
            //判断是否有权限
            if (usbManager.hasPermission(mUsbDevice)) {
                //打开设备。获取UsbDeviceConnection对象，连接设备，作为后面通讯用
                connection = usbManager.openDevice(mUsbDevice);
                if (connection == null) {
                    return;}
                if (connection.claimInterface(mInterface, true)) {
                    LogUtil.i("找到USB设备接口");
                    mDeviceConnection = connection;
                    // 用UsbDeviceConnection 与 UsbInterface 进行端点设置和通讯
                    getEndpoint(mDeviceConnection, mInterface);
                } else {
                    connection.close();}
            } else {
                LogUtil.i("没有权限");
                usbManager.requestPermission(mUsbDevice, pendingIntent);// 该代码执行后，系统弹出一个对话框
                // 询问用户是否授予程序操作USB设备的权限
            }
        } else {
            LogUtil.i("没有找到接口");}
    }

    // 用UsbDeviceConnection 与 UsbInterface 进行端点设置和通讯
    private void getEndpoint(UsbDeviceConnection connection, UsbInterface intf) {
        if (intf.getEndpoint(1) != null) {
            epOut = intf.getEndpoint(1);
        }
        if (intf.getEndpoint(0) != null) {
            epIn = intf.getEndpoint(0);
            //直接在主线程中执行
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    handlerHid.postDelayed(usbHidReader, 100);
                }
            });
            //发送模式状态信号到HID设备
            sendModeTOHid();
        }
    }

    //发送模式状态信号到HID设备
    private void sendModeTOHid() {
        LogUtil.i("sendModeTOHid mCurMode = " + mCurMode);
        String strSend = "";
        if (mCurMode == MODE_STANDBY) {
            LogUtil.i("Standby Mode");
            strSend = "82";
        } else if (mCurMode == MODE_DEMO) {
            LogUtil.i("Demo Mode");
            strSend = "83";
        } else {
            LogUtil.i("Wait Mode");
            strSend = "84";
        }
        final String strHidSend = strSend;
        new Thread(new Runnable() {
            @Override
            public void run() {
                //发送状态信息到HID设备
                sendMessageToHid(strHidSend, true);
            }
        }).start();
    }


    /**
     * 获取Apk版本号
     */
    public int getAPPVersionFromAPP(Context context) {
        int currentVersionCode = 0;
        PackageManager manager = context.getPackageManager();
        try {
            PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);
            String appVersionName = info.versionName;//版本名
            currentVersionCode = info.versionCode;
            LogUtil.i("VersionCode = " + currentVersionCode + " VersionName = " + appVersionName);

        } catch (PackageManager.NameNotFoundException e) {
            // TODO Auto-generated catch blockd
            e.printStackTrace();
        }
        return currentVersionCode;
    }

    private void initView() {
        //初始化控件
        assetsContainerView = (RelativeLayout) findViewById(R.id.assetViewContainer);
        tvTip = (TextView) findViewById(R.id.idTip);
        tvTip.setVisibility(View.VISIBLE);
    }

    //查看播放状态
    private void sendModeText() {
        String strTip = "";
        int length = 0;
        switch (mCurMode) {
            case MODE_STANDBY:
                length = mAssetsStandby.length;
                strTip = "当前为standy模式！\n播放第" + (mCurPlayIndex) + "文件.一共" + mAssetsStandby.length + "个文件";
                break;
            case MODE_DEMO:
                length = mAssetsDemo.length;
                strTip = "当前为demo模式！\n播放第" + (mCurPlayIndex) + "文件.一共" + mAssetsDemo.length + "个文件";
                break;
            case MODE_WAIT:
                length = mAssetsWait.length;
                strTip = "当前为wait模式！\n播放第" + (mCurPlayIndex) + "文件.一共" + mAssetsWait.length + "个文件";
                break;
            default:
                break;
        }
        tvTip.setText(strTip);
        LogUtil.i("当期播放的文件编号 = " + mCurPlayIndex);
        LogUtil.i("文件总个数 = " + length);
    }

    //布局参数初始化
    private void initLayoutParam() {
        this.params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.FILL_PARENT);
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
    }

    @Override
    protected void onResume() {
        mCurPlayIndex = 0;
        tapValue = "";
        //activity 加载完毕开始初始化资源，并开始播放
        initialize();
        super.onResume();
    }

    /**
     * 初始化播放资源文件
     */
    private void initialize() {
        LogUtil.i("从待机文件夹获取文件");
        checkSchedule(DirectoryManager.STANDBY_DIR_PATH, TYPE_IMAGE);
        mAssetsStandby = scheduleManager.getSchedule(DirectoryManager.STANDBY_DIR_PATH).getAssets();

        LogUtil.i("从演示文件夹获取文件");
        checkSchedule(DirectoryManager.DEMO_DIR_PATH, TYPE_VIDE);
        mAssetsDemo = scheduleManager.getSchedule(DirectoryManager.DEMO_DIR_PATH).getAssets();

        LogUtil.i("从等待文件夹获取文件");
        checkSchedule(DirectoryManager.WAIT_DIR_PATH, TYPE_IMAGE);
        mAssetsWait = scheduleManager.getSchedule(DirectoryManager.WAIT_DIR_PATH).getAssets();
        //播放当前文件
        playAsset();
    }

    private void checkSchedule(String dirPath, int type) {
        //判断如果路径存在
        if (ScheduleFileManager.checkDirectory(new File(dirPath))) {
            //如果文件存在
            if (DirectoryManager.checkIfFileExist(new File(dirPath + File.separator + ScheduleFileManager.scheduleFilename))) {
                File file = new File(dirPath + File.separator + ScheduleFileManager.scheduleFilename);
                file.deleteOnExit();//虚拟机退出时候删除临时文件
            }
            AssetModel[] assets = scheduleManager.getAssets(dirPath, type);
            scheduleManager.saveAssets(assets, dirPath);
            //完成JSON文件
        }
    }

    //播放当前文件
    private void playAsset() {
        if ((mAssetsStandby != null) && (mAssetsStandby.length > 0)) {
            //设置屏幕亮度
            setBacklight(255);
            //开始播放
            runAssetSelector(0);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    //发送信息，按钮灯亮
                    sendMessageToHid("82", true);
                }
            }).start();
        } else {
            //若果没有文件则显示dialog布局文件，提示没有文件夹或者文件夹中没有内容
            setContentView(R.layout.dialog);
            TextView dialogMessage = (TextView) findViewById(R.id.dialog_msg);
            dialogMessage.setText("待机文件的内容为空！\n待机只能使用图像");
            //清屏，设置
            removePreviousAssetView();
            //设置屏幕亮度最低
            setBacklight(0.0f);
        }
    }

    private void runAssetSelector(long delayMillis) {
        //删除刚才的状态
        handler.removeCallbacks(assetSelector);
        //马上开始新的状态
        handler.postDelayed(assetSelector, delayMillis);
    }

    /*
     * 设置屏幕亮度
     */
    private void setBacklight(float v) {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = v;
        getWindow().setAttributes(params);
    }

    //删除以前的view
    private void removePreviousAssetView() {
        if (previousAssetView != null) {
            previousAssetView.clearAnimation();
            assetsContainerView.removeView(previousAssetView);
            previousAssetView = null;
        }
    }

    /*
     *  standby模式的处理，standby模式，接受按键切换到demo模式，如果播放结束，则播放下一文件，循环播放
     */
    private void standbyModePro() {
        if (mCurPlayIndex == mAssetsStandby.length) {
            mCurPlayIndex = 0;
        }
        AssetModel assetModel = mAssetsStandby[mCurPlayIndex];
        String str = DirectoryManager.STANDBY_DIR_PATH + File.separator + assetModel.getSrc();
        mCurPlayIndex += 1;
        LogUtil.i("播放待机文件");
        if (assetModel.getType().equals(AssetModel.TAG_PHOTO)) {
            // displayVideoAsset(str);
            displayImageAsset(str);
        } else {
            //当前不是图片，直接查找下一文件
            runAssetSelector(0);
        }
    }

    /*
   * demo模式的处理，demo模式，接受按键则播放下一个文件，如果播放结束，则播放下一文件，如果文件播放完，切换为wait模式
   */
    private void demoModePro() {
        if (mCurPlayIndex == mAssetsDemo.length) {
            //改变播放状态
            changeMode(MODE_WAIT);
            return;
        }
        AssetModel assetModel = mAssetsDemo[mCurPlayIndex];
        String str = DirectoryManager.DEMO_DIR_PATH + File.separator + assetModel.getSrc();
        mCurPlayIndex += 1;
        LogUtil.i("播放示例文件");
        if (assetModel.getType().equals(AssetModel.TAG_VIDEO)) {
            displayVideoAsset(str);
        } else {
            //当前不是视频，直接查找下一文件
            runAssetSelector(0);
        }
    }

    //wait模式：如果没有接受按键，只播放当前的第一个图片文件，播放完就切换stanby模式。如果接受按键是切换demo模式
    private void waitModePro() {
        if (mCurPlayIndex == mAssetsWait.length) {
            //改变播放状态
            changeMode(MODE_STANDBY);
            return;
        }
        AssetModel assetModel = mAssetsWait[mCurPlayIndex];
        String str = DirectoryManager.WAIT_DIR_PATH + File.separator + assetModel.getSrc();
        mCurPlayIndex += 1;
        LogUtil.i("播放等待文件");
        if (assetModel.getType().equals(AssetModel.TAG_PHOTO)) {
            displayImageAsset(str);
        } else {
            runAssetSelector(0);
        }
    }

    //改变播放状态
    private void changeMode(int mMode) {
        mCurPlayIndex = 0;
        mCurMode = mMode;
        String strSend = "";
        if (mCurMode == MODE_STANDBY) {
            LogUtil.i("Standby Mode");
            strSend = "82";
            if (mAssetsStandby == null || mAssetsStandby.length == 0) {
                LogUtil.i("待机文件夹为空，停止");
                playAsset();
                return;}
        } else if (mCurMode == MODE_DEMO) {
            LogUtil.i("Demo Mode");
            strSend = "83";
            if (mAssetsDemo == null || mAssetsDemo.length == 0) {
                LogUtil.i("待机文件为空 ,改变为等待");
                changeMode(MODE_WAIT);
                return;}
        } else {
            LogUtil.i("Wait Mode");
            strSend = "84";
            if (mAssetsDemo == null || mAssetsDemo.length == 0) {
                LogUtil.i("等待文件为空 ,改变为待机");
                changeMode(MODE_STANDBY);
                return;
            }
        }
        final String strHidSend = strSend;
        new Thread(new Runnable() {
            @Override
            public void run() {
                sendMessageToHid(strHidSend, true);
            }
        }).start();
        //新状态继续播放
        runAssetSelector(0);
    }

    //播放图片方法
    private void displayImageAsset(String path) {
        imageView = new ImageView(this);
        imageView.setLayoutParams(params);
        imageView.setScaleType(ImageView.ScaleType.FIT_XY);//设置图片位置
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 2;
        imageView.setImageBitmap(BitmapFactory.decodeFile(path, options));
        removePreviousAssetView();
        assetsContainerView.addView(imageView);
        previousAssetView = imageView;
        //图片等待时间10S
        runAssetSelector(DEFAULT_WAIT_TIME);
    }

    //播放视频方法
    private void displayVideoAsset(final String path) {
        videoView = new MyVideoView(this);
        videoView.setLayoutParams(params);
        String utf8Path = null;
        try {
            utf8Path = new String(path.getBytes(), "utf-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        File f = new File(path);
        videoView.setVideoPath(utf8Path);
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer player) {
                player.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
                    @Override
                    public void onVideoSizeChanged(MediaPlayer mediaPlayer, int width, int height) {
                        //获取视频资源的宽度
                        int mVideoWidth = mediaPlayer.getVideoWidth();
                        //获取视频资源的高度
                        int mVideoHeiht = mediaPlayer.getVideoHeight();
                        //获取屏幕的宽度
                        DisplayMetrics display = getResources().getDisplayMetrics();
                        //在资源尺寸可以播放时处理
                        if (mVideoWidth > 0 && mVideoHeiht > 0) {
                            //拉伸比例
                            float scale = (float) mVideoWidth / (float) mVideoHeiht;
                            //视频资源拉伸至屏幕宽度，横屏竖屏需结合传感器等特殊处理
                            mVideoWidth = display.widthPixels;
                            //拉伸VideoView高度
                            mVideoHeiht = (int) (mVideoWidth / scale);//FixMe 设置surfaceview画布大小
                            videoView.getHolder().setFixedSize(mVideoWidth, mVideoHeiht);
                            //重绘VideoView大小，这个方法是在重写VideoView时对外抛出方法
                            videoView.setMeasure(mVideoWidth, mVideoHeiht);
                            //请求调整
                            videoView.requestLayout();
                        }
                    }
                });
                player.start();
            }
        });
        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                LogUtil.i("播放结束");
                //播放结束，如果是demo模式，则直接切换wait模式
                if (mCurMode == MODE_DEMO) {
                    mCurPlayIndex = mAssetsDemo.length;
                }
                runAssetSelector(0);
            }
        });
        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                LogUtil.e("无法播放该视频" + path);
                runAssetSelector(0);
                return true;
            }
        });
        //视频加入到布局中播放
        assetsContainerView.addView(videoView);
        removePreviousAssetView();
        previousAssetView = videoView;
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(assetSelector);
        removePreviousAssetView();
        super.onPause();
    }

    /**
     * 按键按下的处理逻辑
     */
    private void swKeyPushPro() {
          LogUtil.i("Push Button");
          switch (mCurMode){
              case MODE_STANDBY:
                  //切换到下一个模式，当前模式停止
                  changeMode(MODE_DEMO);
                  break;
              case MODE_DEMO:
                  //播放下一个文件
                  runAssetSelector(0);
                  break;
              case MODE_WAIT:
                  changeMode(MODE_DEMO);
                  break;
              default:
                  break;
          }
    }

    //与USBHID设备通信方法
    private void sendMessageToHid(String testString, boolean bReceive) {
        int ret = -100;
        byte[] Sendbytes;//发送信息字节
        byte[] Receivebytes;//接收信息字节
        if (epIn == null) {
            LogUtil.i("输入接口为空");
            return;}
        byte[] bt = FormatUtil.hexStringToBytes(testString);
        Sendbytes = Arrays.copyOf(bt, bt.length);//发送的信息装换成字节数组
        //发送准备命令
        ret = mDeviceConnection.bulkTransfer(epOut, Sendbytes, Sendbytes.length, 5000);
        LogUtil.i("已经发送字符" + testString);
        if(!bReceive){
            return;}
        //接收发送成功的消息
        Receivebytes=new byte[64];
        ret=mDeviceConnection.bulkTransfer(epIn,Receivebytes,Receivebytes.length,10000);
        LogUtil.i("接收返回值"+ret);
        LogUtil.i("接收的数据为"+FormatUtil.bytesToHexString(Receivebytes));
    }

    @Override
    protected void onDestroy() {
        //销毁Activity发送结束消息
        new Thread(new Runnable(){
            @Override
            public void run() {
                sendMessageToHid("90",true);
            }
        }).start();
        LogUtil.i("################################################################");
        LogUtil.i("################################################################");
        LogUtil.i("################################################################");
        LogcatFileManager.getInstance().stop();
        unregisterReceiver(mUsbReceiver);//撤销广播
        super.onDestroy();
    }
    //创建一个广播接收器，接受请求权限的广播
    private final BroadcastReceiver mUsbReceiver=new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action=intent.getAction();
            Log.e("action",action);
           if(ACTION_USB_PERMISSION.equals(action)){
               synchronized (this){
                  mUsbDevice=intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                   if(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false)){
                     findIntfAndEpt();
                   }else {
                       Log.d("denied","拒绝通信设备"
                               + mUsbDevice);
                   }
               }
           }
        }
    };
    private long lastReceivetime = 0;
    //开启线程线程处理usbHID返回的数据
    private Runnable usbHidReader=new Runnable() {
        @Override
        public void run() {
            if(epIn!=null){
                //读取数据
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        LogUtil.e("读取数据");
                        int inMax=epIn.getMaxPacketSize();
                        ByteBuffer byteBuffer=ByteBuffer.allocate(inMax);
                        UsbRequest usbRequest=new UsbRequest();
                        usbRequest.initialize(mDeviceConnection,epIn);
                        usbRequest.queue(byteBuffer,inMax);
                        if(mDeviceConnection.requestWait()==usbRequest){
                            LogUtil.e("读取数据 2");
                            byte[] retData=byteBuffer.array();
                            LogUtil.i(FormatUtil.bytesToHexString(retData));
                            if(retData[0]!=0x0){
                                LogUtil.i("监听接收数据为:" + FormatUtil.bytesToHexString(retData));}
                            if(retData[0]==(byte)0x85){
                                LogUtil.i("接受到按键");
                                LogUtil.e("curtime = " + System.currentTimeMillis());
                                LogUtil.e("lastReceivetime = " + lastReceivetime);
                                long timediff=System.currentTimeMillis()-lastReceivetime;
                                LogUtil.e("timediff = " + timediff);
                                LogUtil.e("timediff1 = " + timediff / (1000 * 1));
                                if(timediff/(1000*1)>0){
                                    lastReceivetime=System.currentTimeMillis();
                                    swKeyPushPro();
                                }
                            }
                            //每隔0.1秒监听一次是否有按键再次按下
                            handlerHid.postDelayed(usbHidReader,100);
                        }
                    }
                }).start();
            }
        }
    };

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:{
                xTouch=event.getX();
                yTouch=event.getY();
                touchStartTime= Calendar.getInstance().getTimeInMillis();
                handler.postDelayed(updateTimer,1000);
                return true;
            }
            case MotionEvent.ACTION_UP:{
               float touchTime=(float) (Calendar.getInstance().getTimeInMillis()-touchStartTime);
                handler.removeCallbacks(updateTimer);
                float finalX=event.getX();
                if(xTouch>(200.0f + finalX)){

                    tapValue="";
                    LogUtil.i("### RESET SECRET TAP VALUE");
                    return true;
                }
                checkSecretTap(touchTime);
                return true;
            }
        }
        return super.onTouchEvent(event);
    }
    private long updateTimeTouch;
    private Runnable updateTimer=new Runnable() {
        @Override
        public void run() {
            updateTimeTouch=(Calendar.getInstance().getTimeInMillis()-touchStartTime);
            if(updateTimeTouch>=MINIMUE_SECONDS_TAP){
                MainActivity.this.updateTimeTouch=0;
                return;
            }
            handler.postDelayed(this,1000);
        }
    };

    private void checkSecretTap(float touchTime) {
        Display display=getWindowManager().getDefaultDisplay();
        Point point=new Point();
        display.getSize(point);
        int maxX=point.x/2;
        int maxY=point.y/2;
        if((xTouch<(float) maxX)&&(yTouch<(float)maxY)){
           tapValue=tapValue.concat("1");
        }else if((xTouch>=(float) maxX)&&(yTouch<(float)maxY)){
            tapValue=tapValue.concat("2");
        }else if((xTouch < (float) maxX) && (yTouch >= (float) maxY)){
            tapValue=tapValue.concat("3");
        }else if((xTouch >= (float) maxX) && (yTouch >= (float) maxY)){
            tapValue=tapValue.concat("4");
        }
         if(DEFAULT_LAUNCHER_TAP_PASSWORD.equals(tapValue)){
             LogUtil.i("### Launching default launcher.");
             Intent intentLauncher=new Intent("android.intent.action.MAIN");
             startActivity(intentLauncher);
         }
    }

}
