package ru.ladon.remote;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("deprecation")
public class QrScannerActivity extends Activity implements SurfaceHolder.Callback {
    Camera camera;
    SurfaceView preview;
    TextView hint;
    Button light;
    boolean surfaceReady=false,resumed=false,found=false,torch=false;
    int previewWidth,previewHeight;
    final Handler ui=new Handler(Looper.getMainLooper());
    final ExecutorService decoder=Executors.newSingleThreadExecutor();
    final AtomicBoolean busy=new AtomicBoolean(false);
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        preview=new SurfaceView(this);root.addView(preview,new FrameLayout.LayoutParams(-1,-1));preview.getHolder().addCallback(this);
        LinearLayout overlay=new LinearLayout(this);overlay.setOrientation(LinearLayout.VERTICAL);overlay.setPadding(24,20,24,20);overlay.setBackgroundColor(0xD918222E);
        hint=new TextView(this);hint.setText("Наведите камеру на QR-код в окне ClickMate на ПК");hint.setTextColor(Color.WHITE);hint.setTextSize(18);hint.setGravity(Gravity.CENTER);overlay.addView(hint);
        LinearLayout buttons=new LinearLayout(this);Button close=new Button(this);close.setText("Назад");close.setAllCaps(false);close.setOnClickListener(v->finish());buttons.addView(close,new LinearLayout.LayoutParams(0,-2,1));
        light=new Button(this);light.setText("Фонарик");light.setAllCaps(false);light.setEnabled(false);light.setOnClickListener(v->toggleLight());buttons.addView(light,new LinearLayout.LayoutParams(0,-2,1));overlay.addView(buttons);
        FrameLayout.LayoutParams bottom=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);root.addView(overlay,bottom);
    }
    @Override protected void onResume(){super.onResume();resumed=true;if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},10);else openCamera();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==10){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)openCamera();else hint.setText("Камера не разрешена. Разрешите её в настройках телефона или вернитесь и введите код вручную.");}}
    @Override public void surfaceCreated(SurfaceHolder holder){surfaceReady=true;openCamera();}
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){}
    @Override public void surfaceDestroyed(SurfaceHolder holder){surfaceReady=false;closeCamera();}
    void openCamera(){
        if(!resumed||!surfaceReady||camera!=null||found||checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return;
        try{
            int id=-1;Camera.CameraInfo info=new Camera.CameraInfo();for(int i=0;i<Camera.getNumberOfCameras();i++){Camera.getCameraInfo(i,info);if(info.facing==Camera.CameraInfo.CAMERA_FACING_BACK){id=i;break;}}if(id<0)id=0;
            Camera.getCameraInfo(id,info);camera=Camera.open(id);Camera.Parameters p=camera.getParameters();
            Camera.Size best=null;for(Camera.Size s:p.getSupportedPreviewSizes())if(s.width<=1280&&(best==null||s.width*s.height>best.width*best.height))best=s;
            if(best!=null)p.setPreviewSize(best.width,best.height);p.setPreviewFormat(ImageFormat.NV21);
            List<String> modes=p.getSupportedFocusModes();if(modes!=null&&modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            camera.setParameters(p);Camera.Size size=camera.getParameters().getPreviewSize();previewWidth=size.width;previewHeight=size.height;
            int rotation=getWindowManager().getDefaultDisplay().getRotation(),degrees=rotation==Surface.ROTATION_90?90:rotation==Surface.ROTATION_180?180:rotation==Surface.ROTATION_270?270:0;
            camera.setDisplayOrientation(info.facing==Camera.CameraInfo.CAMERA_FACING_FRONT?(360-(info.orientation+degrees)%360)%360:(info.orientation-degrees+360)%360);
            camera.setPreviewDisplay(preview.getHolder());camera.setPreviewCallback((data,source)->decode(data));camera.startPreview();
            List<String> flash=p.getSupportedFlashModes();light.setEnabled(flash!=null&&flash.contains(Camera.Parameters.FLASH_MODE_TORCH));
        }catch(Exception ex){closeCamera();hint.setText("Не удалось открыть камеру. Закройте другие приложения с камерой или используйте вход вручную.");}
    }
    void decode(byte[] data){
        if(found||!resumed||!busy.compareAndSet(false,true))return;
        final byte[] frame=data.clone();final int width=previewWidth,height=previewHeight;
        try{decoder.execute(()->{
            String value=null;
            try{MultiFormatReader reader=new MultiFormatReader();Map<DecodeHintType,Object> hints=new EnumMap<>(DecodeHintType.class);hints.put(DecodeHintType.POSSIBLE_FORMATS,Collections.singletonList(BarcodeFormat.QR_CODE));hints.put(DecodeHintType.TRY_HARDER,Boolean.TRUE);reader.setHints(hints);
                PlanarYUVLuminanceSource source=new PlanarYUVLuminanceSource(frame,width,height,0,0,width,height,false);
                value=reader.decodeWithState(new BinaryBitmap(new HybridBinarizer(source))).getText();
            }catch(Exception ignored){}finally{busy.set(false);}
            if(value!=null){final String result=value;ui.post(()->{
                if(found||!resumed)return;
                if(PairingLink.parse(result)==null){hint.setText("Это не код подключения ClickMate. Наведите камеру на QR в окне ПК.");return;}
                found=true;closeCamera();setResult(RESULT_OK,new Intent().putExtra("pairing",result));finish();
            });}
        });}catch(RejectedExecutionException ex){busy.set(false);}
    }
    void toggleLight(){if(camera==null)return;try{Camera.Parameters p=camera.getParameters();torch=!torch;p.setFlashMode(torch?Camera.Parameters.FLASH_MODE_TORCH:Camera.Parameters.FLASH_MODE_OFF);camera.setParameters(p);light.setText(torch?"Выключить фонарик":"Фонарик");}catch(RuntimeException ex){torch=false;}}
    void closeCamera(){if(camera!=null){try{camera.setPreviewCallback(null);camera.stopPreview();}catch(RuntimeException ignored){}camera.release();camera=null;}torch=false;}
    @Override protected void onPause(){resumed=false;closeCamera();super.onPause();}
    @Override protected void onDestroy(){decoder.shutdownNow();ui.removeCallbacksAndMessages(null);super.onDestroy();}
}
