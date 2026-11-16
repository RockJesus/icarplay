package com.rockjesus.icarplay;

import android.app.Activity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import io.github.jqssun.airplay.service.AirPlayService;

/**
 * AirPlay 镜像的全屏承载面。
 *
 * <p>手机真正开始镜像后，这里铺满整屏只放画面：不叠任何本 App 的控件、不弹窗、不留状态条，
 * 结束镜像后自动收起、恢复原来的页面。没有这一层时解码器照样在跑，但画面无处落地，
 * 表现就是“音频能听、镜像没反应”。
 */
public class MirrorOverlay implements SurfaceHolder.Callback {

    private final Activity activity;
    private final View uiRoot;
    private final SurfaceView surfaceView;
    private AirPlayService service;
    private boolean surfaceReady;
    private boolean showing;

    public MirrorOverlay(Activity activity, View uiRoot) {
        this.activity = activity;
        this.uiRoot = uiRoot;
        ViewGroup content = activity.findViewById(android.R.id.content);
        surfaceView = new SurfaceView(activity);
        surfaceView.setBackgroundColor(0xFF000000);
        // 画面必须压在窗口之上：默认的“窗口后面 + 打洞”在动态加进来的 SurfaceView 上
        // 不一定能合出来，表现就是连接成功、日志在画帧、屏幕却是纯黑
        surfaceView.setZOrderOnTop(true);
        surfaceView.getHolder().setFormat(android.graphics.PixelFormat.OPAQUE);
        surfaceView.setVisibility(View.GONE);
        if (content != null) {
            content.addView(surfaceView, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        surfaceView.getHolder().addCallback(this);
    }

    public void setService(AirPlayService service) {
        if (this.service == service) return;
        detach();
        this.service = service;
        if (showing) attach();
    }

    public boolean isShowing() {
        return showing;
    }

    /** 有画面时铺满全屏并隐藏原页面，没有画面时收起。 */
    public void setMirroring(boolean mirroring) {
        if (showing == mirroring) return;
        showing = mirroring;
        if (mirroring) {
            surfaceView.bringToFront();
            surfaceView.setVisibility(View.VISIBLE);
            if (uiRoot != null) uiRoot.setVisibility(View.GONE);
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            attach();
        } else {
            detach();
            surfaceView.setVisibility(View.GONE);
            if (uiRoot != null) uiRoot.setVisibility(View.VISIBLE);
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void attach() {
        if (!showing || service == null || !surfaceReady) {
            android.util.Log.i("MirrorOverlay", "attach skipped showing=" + showing
                    + " service=" + (service != null) + " surfaceReady=" + surfaceReady);
            return;
        }
        try {
            android.util.Log.i("MirrorOverlay", "attach surface valid="
                    + surfaceView.getHolder().getSurface().isValid());
            service.setVideoSurface(surfaceView.getHolder().getSurface());
        } catch (Throwable t) {
            android.util.Log.w("MirrorOverlay", "attach failed", t);
        }
    }

    private void detach() {
        if (service == null || !surfaceReady) return;
        try {
            service.clearVideoSurface(surfaceView.getHolder().getSurface());
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        android.util.Log.i("MirrorOverlay", "surfaceCreated");
        attach();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        attach();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        detach();
        surfaceReady = false;
    }
}
