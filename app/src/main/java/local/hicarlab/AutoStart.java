package local.hicarlab;

import android.Manifest;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;

/** Opt-in boot reception for an already configured receiver; never enables a radio silently. */
final class AutoStart {
    private static final String COMPONENT = "local.hicarlab.BootReceiver";

    static boolean enabled(Context c){return c.getSharedPreferences("ui",0).getBoolean("auto_start",false);}

    /**
     * The manifest receiver is disabled by default. Turning the user preference on is the only
     * way it becomes able to receive BOOT_COMPLETED, so a fresh install cannot auto-start HiCar
     * after a reboot.
     */
    static void setEnabled(Context c,boolean enabled){
        c.getSharedPreferences("ui",0).edit().putBoolean("auto_start",enabled).commit();
        applyBootReceiver(c,enabled);
    }

    /** Reconcile the disabled-by-default component with the stored preference after upgrades. */
    static void syncBootReceiver(Context c){
        applyBootReceiver(c,enabled(c));
    }

    static void applyBootReceiver(Context c,boolean enabled){
        try{
            ComponentName component=new ComponentName(c.getPackageName(),COMPONENT);
            int state=enabled
                    ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            c.getPackageManager().setComponentEnabledSetting(component,state,PackageManager.DONT_KILL_APP);
        }catch(RuntimeException ignored){
            // Some vendor PackageManager implementations reject this for a manifest receiver.
            // The receiver also re-checks the preference, so failing here never turns it on.
        }
    }

    static String problem(Context c){
        if(Build.VERSION.SDK_INT>=31)for(String p:new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_ADVERTISE})
            if(c.checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)return "请先手动连接一次，允许蓝牙及附近设备权限。";
        String wifi=Build.VERSION.SDK_INT>=33?"android.permission.NEARBY_WIFI_DEVICES":Manifest.permission.ACCESS_FINE_LOCATION;
        if(c.checkSelfPermission(wifi)!=PackageManager.PERMISSION_GRANTED)return "请先手动连接一次，允许无线连接权限。";
        String wire=c.getSharedPreferences("identity",0).getString("wire_id","");
        String model=c.getSharedPreferences("identity",0).getString("wire_model","");
        if(wire==null||!wire.matches("[0-9A-Fa-f]{64}")||model==null||!model.matches("[0-9A-Fa-f]{8}"))
            return "请先在设置与诊断中保存本机蓝牙身份，并完成一次手机连接。";
        return null;
    }
    static boolean bluetoothReady(Context c){
        try{BluetoothManager m=(BluetoothManager)c.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter a=m==null?null:m.getAdapter();return a!=null&&a.isEnabled();
        }catch(RuntimeException e){return false;}
    }
}
