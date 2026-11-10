package com.rockjesus.icarplay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import androidx.core.content.ContextCompat;

import info.anodsplace.headunit.aap.AapService;

/**
 * 手机切到 AOA accessory 模式后会重新枚举为 0x18D1:0x2D00/0x2D01。
 * 这个接收器只在配件模式下启动 Android Auto 协议服务，避免插 U 盘就误启动。
 */
public class AndroidAutoUsbReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) return;
        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (device == null) return;
        if (device.getVendorId() != 0x18D1) return;
        int pid = device.getProductId();
        if (pid != 0x2D00 && pid != 0x2D01) return;

        ContextCompat.startForegroundService(context, AapService.createIntent(device, context));
    }
}
