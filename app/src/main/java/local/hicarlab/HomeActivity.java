package local.hicarlab;

import com.rockjesus.icarplay.IcarLauncherActivity;
import com.rockjesus.icarplay.PageUi;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.*;

/** Daily receiver controls. Protocol experiments remain in MainActivity. */
public final class HomeActivity extends ThemeActivity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView status,detail,micStatus;
    private Switch micSwitch;
    private Button start,stop,retry;
    private boolean pending;
    private boolean updatingAutoStart;
    private static final int PERMISSIONS=60,ENABLE=61,DISCOVER=62,MICROPHONE=63,NOTIFICATIONS=64;
    private ReceiverEngine engine(){return ((LabApplication)getApplication()).engine();}
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
    private final Runnable update=new Runnable(){public void run(){
        if(status==null)return;
        boolean active=engine().isActive();
        ReceiverStatus.Snapshot state=engine().displayStatus();
        micStatus.setText(engine().microphoneStatus());
        status.setText(state.title);detail.setText(state.detail);retry.setVisibility(active||state.problem?View.VISIBLE:View.GONE);
        start.setText(active?"返回接收画面":"开始连接");
        start.setEnabled(!pending);stop.setEnabled(active);handler.postDelayed(this,700);
    }};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);if(state!=null)pending=state.getBoolean("pending");
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PageUi.BG);
        root.setPadding(PageUi.dp(this,18),PageUi.dp(this,10),PageUi.dp(this,18),PageUi.dp(this,8));
        setContentView(root);

        root.addView(PageUi.header(this,"HiCar 接收端","实验版 0.11.1 · v0.5.0"),PageUi.matchWrap(this));

        TextView help=PageUi.text(this,"开启手机蓝牙和 WLAN，点“开始连接”；首次连接按手机提示输入六位码。",10.5f,PageUi.MUTED,false);
        LinearLayout.LayoutParams helpLp=PageUi.matchWrap(this);PageUi.margin(helpLp,this,0,3,0,6);root.addView(help,helpLp);

        LinearLayout statusCard=PageUi.card(this,PageUi.BORDER,12,8);
        status=PageUi.text(this,"正在读取状态…",18f,PageUi.GREEN,true);
        statusCard.addView(status,PageUi.matchWrap(this));
        detail=PageUi.text(this,"",10.5f,PageUi.MUTED,false);
        statusCard.addView(detail,PageUi.matchWrap(this));
        root.addView(statusCard,PageUi.matchWrap(this));

        LinearLayout grid=new LinearLayout(this);grid.setOrientation(LinearLayout.VERTICAL);
        grid.setMinimumHeight(PageUi.dp(this,120));
        LinearLayout.LayoutParams gridLp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f);
        PageUi.margin(gridLp,this,0,8,0,0);root.addView(grid,gridLp);

        start=PageUi.button(this,"开始连接",13f,PageUi.GREEN);
        stop=PageUi.button(this,"停止连接",13f,PageUi.RED);
        retry=PageUi.button(this,"重新连接",12f,PageUi.BLUE);
        retry.setVisibility(View.GONE);
        Button trial=PageUi.button(this,"试运行自动接收",12f,PageUi.BLUE);
        Button diag=PageUi.button(this,"设置与诊断",12f,PageUi.BORDER);
        Button settings=PageUi.button(this,"系统后台与通知",12f,PageUi.BORDER);

        grid.addView(row(start,stop),new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        LinearLayout row2=row(retry,trial);
        LinearLayout.LayoutParams row2Lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f);
        PageUi.margin(row2Lp,this,0,6,0,0);grid.addView(row2,row2Lp);
        LinearLayout row3=row(diag,settings);
        LinearLayout.LayoutParams row3Lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f);
        PageUi.margin(row3Lp,this,0,6,0,0);grid.addView(row3,row3Lp);

        LinearLayout switchCard=PageUi.card(this,PageUi.BORDER,10,5);
        switchCard.setOrientation(LinearLayout.HORIZONTAL);
        micSwitch=new Switch(this);micSwitch.setText("麦克风语音");micSwitch.setTextColor(PageUi.TEXT);PageUi.textSize(micSwitch,10f);
        micSwitch.setChecked(MicrophoneSender.enabled(this));
        micSwitch.setPadding(0,0,PageUi.dp(this,4),0);
        LinearLayout.LayoutParams micLp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);
        switchCard.addView(micSwitch,micLp);
        Switch autoStart=new Switch(this);autoStart.setText("开机自动接收");autoStart.setTextColor(PageUi.TEXT);PageUi.textSize(autoStart,10f);
        autoStart.setChecked(AutoStart.enabled(this));
        LinearLayout.LayoutParams autoLp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);
        switchCard.addView(autoStart,autoLp);
        root.addView(switchCard,PageUi.matchWrap(this));

        micStatus=PageUi.text(this,"",9.5f,PageUi.MUTED,false);
        root.addView(micStatus,PageUi.matchWrap(this));

        TextView autoHelp=PageUi.text(this,"开机自动接收默认关闭；开启后只后台等待已配对手机，不会自动弹画面。重复异常时安全模式会自动关闭。",9f,PageUi.MUTED,false);
        autoHelp.setPadding(PageUi.dp(this,8),PageUi.dp(this,3),PageUi.dp(this,8),PageUi.dp(this,3));
        autoHelp.setBackground(PageUi.rounded(this,PageUi.CARD,PageUi.BORDER,8));
        root.addView(autoHelp,PageUi.matchWrap(this));

        micSwitch.setOnCheckedChangeListener((button,checked)->{
            if(checked&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){micSwitch.setChecked(false);requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MICROPHONE);return;}
            getSharedPreferences("ui",0).edit().putBoolean("microphone",checked).apply();
            if(!checked)engine().disableMicrophone();
            else if(engine().isActive())Toast.makeText(this,"重新连接后启用；仅在手机请求语音时采集",Toast.LENGTH_LONG).show();
        });
        autoStart.setOnCheckedChangeListener((button,checked)->{
            if(updatingAutoStart)return;
            if(!checked){
                AutoStart.setEnabled(this,false);
                Toast.makeText(this,"已关闭开机自动接收；当前连接不受影响",Toast.LENGTH_LONG).show();
                return;
            }
            String problem=AutoStart.problem(this);
            if(problem!=null){
                updatingAutoStart=true;autoStart.setChecked(false);updatingAutoStart=false;
                Toast.makeText(this,problem,Toast.LENGTH_LONG).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("确认开启开机自动接收？")
                    .setMessage("这是会影响车机启动体验的可选功能。开启后仅在后台等待已配对手机，不会自动弹出画面；若连续异常，安全模式会自动关闭它。请确认后再开启。")
                    .setNegativeButton("取消",(dialog,which)->{
                        updatingAutoStart=true;autoStart.setChecked(false);updatingAutoStart=false;
                    })
                    .setPositiveButton("确认开启",(dialog,which)->{
                        AutoStart.setEnabled(this,true);
                        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)
                            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},NOTIFICATIONS);
                        Toast.makeText(this,"已启用，下次开机自动等待连接；请在系统中允许自启动",Toast.LENGTH_LONG).show();
                    })
                    .setOnCancelListener(dialog->{
                        updatingAutoStart=true;autoStart.setChecked(false);updatingAutoStart=false;
                    })
                    .show();
        });

        trial.setOnClickListener(v->{
            if(!AutoStart.enabled(this)){Toast.makeText(this,"请先开启“开机自动接收”",Toast.LENGTH_LONG).show();return;}
            String problem=AutoStart.problem(this);if(problem!=null){Toast.makeText(this,problem,Toast.LENGTH_LONG).show();return;}
            if(engine().isActive()){Toast.makeText(this,"请先停止当前连接，再试运行自动接收",Toast.LENGTH_LONG).show();return;}
            try{startForegroundService(new Intent(this,ReceiverService.class).setAction("AUTO_START"));Toast.makeText(this,"正在试运行：等待手机连接，点击接收通知打开画面；本轮不启用麦克风",Toast.LENGTH_LONG).show();}
            catch(RuntimeException e){Toast.makeText(this,"系统未允许自动接收，请检查后台运行设置",Toast.LENGTH_LONG).show();}
        });
        start.setOnClickListener(v->{if(engine().isActive())openVideo();else{pending=true;continueStart();}});
        retry.setOnClickListener(v->{engine().stop(true);pending=true;continueStart();});
        stop.setOnClickListener(v->{pending=false;engine().stop(true);status.setText("已停止");});
        diag.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class)));
        settings.setOnClickListener(v->{
            try{startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())));}
            catch(RuntimeException e){Toast.makeText(this,"请在系统设置中打开本应用详情",Toast.LENGTH_LONG).show();}
        });

        Button carHome=PageUi.button(this,"← 返回主界面",12f,PageUi.BLUE);
        LinearLayout.LayoutParams carHomeLp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,PageUi.dp(this,38));
        PageUi.margin(carHomeLp,this,0,6,0,0);
        carHome.setOnClickListener(v->{
            startActivity(new Intent(this,IcarLauncherActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });
        root.addView(carHome,carHomeLp);

        TextView note=PageUi.text(this,"请停车后操作。",9.5f,PageUi.MUTED,false);
        note.setGravity(android.view.Gravity.CENTER);
        root.addView(note,PageUi.matchWrap(this));
        if(getIntent().getBooleanExtra("retry",false)){getIntent().removeExtra("retry");handler.post(()->{pending=true;continueStart();});}
    }
    private LinearLayout row(Button left,Button right){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1f);
        PageUi.margin(lp,this,4,0,4,0);row.addView(left,lp);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1f);
        PageUi.margin(rp,this,4,0,4,0);row.addView(right,rp);
        return row;
    }
    private void message(String value){pending=false;engine().reportProblem(value);Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    private void continueStart(){
        if(!pending)return;
        if(engine().isActive()){pending=false;openVideo();return;}
        List<String> missing=new ArrayList<>();
        if(Build.VERSION.SDK_INT>=31)for(String p:new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_ADVERTISE})if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)missing.add(p);
        String wifi=Build.VERSION.SDK_INT>=33?"android.permission.NEARBY_WIFI_DEVICES":Manifest.permission.ACCESS_FINE_LOCATION;
        if(checkSelfPermission(wifi)!=PackageManager.PERMISSION_GRANTED)missing.add(wifi);
        if(!missing.isEmpty()){requestPermissions(missing.toArray(new String[0]),PERMISSIONS);return;}
        BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
        if(adapter==null){message("这台设备没有可用蓝牙适配器");return;}
        try{
            if(!adapter.isEnabled()){startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),ENABLE);return;}
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,120),DISCOVER);
        }catch(RuntimeException e){message("系统无法打开蓝牙连接请求，请到设置与诊断查看");}
    }
    private void openVideo(){startActivity(new Intent(this,VideoActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==NOTIFICATIONS){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)Toast.makeText(this,"通知未开启，自动接收时需从应用入口打开画面",Toast.LENGTH_LONG).show();return;}
        if(request==MICROPHONE){
            boolean granted=checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
            getSharedPreferences("ui",0).edit().putBoolean("microphone",granted).apply();micSwitch.setChecked(granted);
            Toast.makeText(this,granted?"已启用语音实验；连接后在 HiCar 中启动语音":"未启用麦克风，画面和播放不受影响",Toast.LENGTH_LONG).show();return;
        }
        if(request==PERMISSIONS){if(results.length==0){message("已取消权限申请");return;}for(int result:results)if(result!=PackageManager.PERMISSION_GRANTED){message("需要蓝牙及附近设备权限才能开始连接");return;}continueStart();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(!pending)return;
        if(request==ENABLE){if(result==RESULT_OK)continueStart();else message("蓝牙尚未开启");}
        if(request==DISCOVER){
            pending=false;if(result<=0){message("已取消开始连接");return;}
            String model=getSharedPreferences("identity",0).getString("wire_model","00048000");
            if(model==null || !model.matches("[0-9A-Fa-f]{8}")){message("接收设置无效，请到设置与诊断检查");return;}
            try{startForegroundService(new Intent(this,ReceiverService.class).putExtra("model",model).putExtra("network",true).putExtra("video",true));openVideo();}
            catch(RuntimeException e){message("接收服务启动失败，请查看诊断页");}
        }
    }
    @Override protected void onStart(){super.onStart();handler.removeCallbacks(update);handler.post(update);}
    @Override protected void onStop(){handler.removeCallbacks(update);super.onStop();}
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("pending",pending);super.onSaveInstanceState(state);}
}
