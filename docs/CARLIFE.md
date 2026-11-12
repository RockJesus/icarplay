# CarLife 接收端

iCarPlay 首页的 CarLife 卡是**入口**，真正的接收端是独立的 `carlife-vehicle.apk`。这样不会把百度车机端的旧 support-v4、AOA/USB 逻辑和 iCarPlay 的 Android Auto 服务混在同一个进程里。

## 安装

1. 安装 `icarplay-v0.7.0.apk`；
2. 安装同版本 Release 里的 `carlife-vehicle.apk`（包名 `com.baidu.carlifevehicle`）；
3. 首次使用把 `bdcf` 推到车机：

```bash
adb push bdcf /data/local/tmp/bdcf
adb shell chmod 644 /data/local/tmp/bdcf
```

车机没有 adb 时，可在 Root/工程模式下用文件管理器把 `bdcf` 放到 `/data/local/tmp/`；路径必须是 `/data/local/tmp/bdcf`，不能只放 APK 的 assets 里。

4. 回到 iCarPlay 首页，点 CarLife 卡。若卡上显示“需安装接收端”，说明系统没查到 `com.baidu.carlifevehicle`。

## 连接

- **Android 手机**：手机安装 CarLife 手机端，USB 数据线接车机数据口，手机端选择 CarLife/文件传输；接收端进入“请连接手机和车载系统”后会自动等 AOA。
- **iPhone**：开源车机端缺少 `libiPhone_ean.so`，也没有内置 usbmuxd 端口转发，当前不保证可用。
- **无线**：开源代码没有无线实现；量产 channel ID 需向百度申请，仓库里的 `20022100` 只是调试值。
- CarLife 与 Android Auto 都占用 USB Host/AOA，不能同时连接；切换前先在 iCarPlay 里停止 Android Auto。

## 构建来源

- 上游：`https://gitee.com/gileyang/apollo-DuerOS` 的 `CarLife-Android-Vehicle`，Apache-2.0；
- 本 Release 的接收端做了现代化构建：AGP 8.7.3 / Gradle 8.9 / compileSdk 33 / minSdk 23 / targetSdk 33；
- 补了 `CarlifeActivity exported=true`、`buildFeatures aidl`，并把 `Settings.System.putInt` 包了 try/catch，避免 Android 12 上因 `WRITE_SETTINGS` 崩溃；
- 使用与 iCarPlay 相同的 `syncplus` 证书签名，便于覆盖安装。

## 已验证范围

- MuMu Android 12 x86_64：安装、启动、读取 `bdcf`、进入“请连接手机和车载系统”，无崩溃；
- 尚未用真实手机完成 CarLife 配对；Android AOA 与 iPhone 两条路径都还需要实车验证。
