# iCarPlay

面向 **2020 款 SYNC+ 1.0 车机**（Android 8.1 / API 27、1920×720）的第三方接收端：
**一个 APK 内同时提供 AirPlay 接收、Android Auto 接收、Huawei HiCar 接收、CarPlay 入口和内置小白点**。

> **当前版本 v0.5.8**
>
> 下载：[`icarplay-v0.5.8.apk`](https://github.com/RockJesus/icarplay/releases/download/v0.5.8/icarplay-v0.5.8.apk)
>
> 本版 Release 只打包 **arm64-v8a + armeabi-v7a**，去掉车机用不到的 x86_64，APK 从约 50.8 MB 降到约 41 MB。
>
> 首页是**车机一屏布局**：2×2 主功能卡 + 一行快捷操作 + 底部“返回车机主屏幕”，不再需要下拉；字号、间距和卡片列数按屏幕 dp 尺寸自适应，矮屏/高密度 1920×720 车机会自动切成 4 列一行。首次打开会自动引导悬浮窗、麦克风/位置/蓝牙/通知和电池优化权限，并默认启动内置小白点。
>
> - **v0.5.4 修复**：AirPlay 接收页锁定横屏，避免从车机横屏进入后翻转成竖屏、导致预览区拉长和小白点遮住右上角按钮。
>
> - **v0.5.5 调整**：小白点快捷面板最后一项由“关闭小白点”改为“车机主屏幕”，点击直接返回原车桌面，小白点保持显示。
>
> - **v0.5.6 调整**：小白点改用无字母图标，图形为“车顶接收信号 + 车头轮廓”，同时表达车机与投屏接收。
>
> - **v0.5.7 调整**：小白点快捷面板中的“主界面”改为“iCarPlay主界面”，点击仍进入 iCarPlay 首页。
>
> - **v0.5.8 调整**：车机重启后自动恢复小白点悬浮层；只恢复悬浮球，不打开 iCarPlay 主界面，不自动启动 HiCar 接收。用户在首页手动关掉小白点后，重启不会强制拉起。
>
>
> - **AirPlay：已接入**。基于 GPLv3 的 UxPlay / `jqssun/android-airplay-server` 协议栈，
>   支持 iPhone / iPad / Mac 的屏幕镜像与音频，Android 7.0+ 可运行。
> - **Android Auto：已接入协议层，待实车验证**。USB AOA 有线 + 手机 Headunit Server
>   手动无线（TCP 5277）；H.264 解码、音频、触控、定位均已在代码里接通。
> - **Huawei HiCar：已接入实验接收端**。来自 MIT 项目 `huducat586/hicar-android-receiver`，
>   华为手机使用系统自带 HiCar，手机端不需要第二个 APK；无线实现原按 Android 10+ 设计，
>   在 SYNC+ Android 8.1 车机上属于**未验证的实验路径**，详见 [`docs/HICAR.md`](docs/HICAR.md)。
> - **CarPlay：接入层已搭建**。认证后端、Apple USB 枚举、有线/无线入口、设置页已完成；
>   **真正的 iAP2 / MFi / AirPlay 隧道尚未接入**，没有 Apple MFi 认证材料时 iPhone 不会完成配对。
> - **内置小白点：已接入**。悬浮球由本 APK 自带，不需要第二个悬浮球 App。
> - **车机桌面显示**：SYNC+ 原车桌面是系统层窗口，普通悬浮窗会被它盖住；在系统「无障碍」里开启 `iCarPlay 小白点` 后，改用无障碍悬浮层，才能显示在原车机桌面上。该服务只绘制悬浮球，不读取屏幕内容。
> - **安全边界：v0.4.0 起不抢桌面；v0.5.8 起仅小白点悬浮层开机恢复，主界面和接收服务不自动启动；连续异常进安全模式**，详见 [`docs/SAFETY.md`](docs/SAFETY.md)。

## 能力矩阵

| 能力 | 状态 | 说明 |
|---|---|---|
| AirPlay 屏幕镜像 | ✅ | iOS/iPadOS/macOS 同网发现，H.264/H.265 解码 |
| AirPlay 音频 | ✅ | AAC-ELD / AAC-LC / ALAC，AudioTrack 输出 |
| AirPlay 视频投放 | ✅ | HLS / 远程视频，播放控制 |
| Android Auto 有线（USB AOA） | ✅ 代码已接入 | 手机切 AOA 配件模式，进 AAP/TLS/H.264；需 USB Host + 数据口 |
| Android Auto 无线（Headunit Server） | ⚠️ 实验 | 手机开 Headunit Server，同一 Wi-Fi，手动输入 IP:5277 |
| Huawei HiCar（华为手机） | ⚠️ 实验 | 来自 MIT 开源接收端；需手机原生 HiCar；上游按 Android 10+ 设计，8.1 未实车验证 |
| 内置小白点 | ✅ | 可拖动，点按弹出 AirPlay / Android Auto / HiCar / CarPlay / 设置 / 主界面 / 关闭 |
| CarPlay 有线入口 | ⚠️ 骨架 | USB 设备枚举 + 启动状态，协议引擎未接入 |
| CarPlay 无线入口 | ⚠️ 骨架 | Wi-Fi 状态 + 启动状态，协议引擎未接入 |
| CarPlay 认证后端 | ⚠️ 骨架 | 本地证书 / MFi 芯片 / 远程 MFI 三选一，仅保存与检查 |
| 车机安全约束 | ✅ | 不写系统分区、不申请设备管理员/系统设置权限、不抢占默认桌面；开机仅恢复小白点悬浮层，HiCar 开机接收仍默认关闭 |
| 不需要第二个 APK | ✅ | 悬浮球、AirPlay、Android Auto、HiCar、CarPlay 入口都在同一个包里 |

## 安装（SYNC+ 车机）

1. U 盘格式 **FAT32**，把 `icarplay-v0.5.8.apk` 放根目录；
2. 输入法漏洞进安卓原生设置 → 存储 → U 盘 → 安装；
3. 如果报 **“应用未安装”**，说明撞上 SYNC+ 的签名白名单，需要百度签名重签；
4. 装完桌面没有图标，从 **设置 → 应用 → iCarPlay → 打开** 进入；
5. 首次打开会自动弹出权限引导：允许悬浮窗，再依次允许麦克风、位置、蓝牙、通知和电池优化；小白点默认自动启动；
6. 点悬浮球 → AirPlay 开始接收；Android Auto / HiCar 进入接收页；CarPlay 进入认证/连接页。

详细步骤与判读见 [`docs/INSTALL.md`](docs/INSTALL.md)。

## 两个核心的边界

### AirPlay

- 不需要 MFi，iPhone 控制中心选“屏幕镜像”即可发现 `iCarPlay`；
- 车机与 iPhone 必须在同一局域网（推荐外接 5GHz 路由，SYNC+ 自带热点 iPhone 搜不到）；
- **单向输出**：没有触控回传，不能在车机上反向控制 iPhone；
- DRM 内容（Netflix、Apple TV+）黑屏；
- 1920×720 是 8:3，16:9 的 iPhone 画面会留黑边。

### Android Auto

- **有线是主路径**：USB 口必须支持 USB Host 和数据；手机必须安装 Android Auto；
  首次连接在手机上确认 USB 权限。手机切到 AOA accessory 模式后，
  `AndroidAutoUsbReceiver` 会直接启动协议服务。
- **无线是手动模式**：手机 Android Auto 开发者选项 → `Start headunit server`，
  手机与车机同一 Wi-Fi，在车机里输入手机 IP，端口 `5277`。不做蓝牙 + Wi-Fi Direct
  自动握手（SYNC+ 上曾触发 Wi-Fi Direct 重启循环，且 AA 17.4+ 对传统触发有限制）。
- **没有 GMS 不影响接收端**，但手机端 Android Auto 被厂商裁剪时仍然连不上。
- 1920×720 是 8:3，16:9 画面会左右留黑边；未在真车验证，详见
  [`docs/ANDROID-AUTO.md`](docs/ANDROID-AUTO.md)。

### Huawei HiCar

- 华为手机必须支持**原生 HiCar**（不是所有华为/荣耀机型都有）；手机端不需要安装辅助 APK；
- 车机需要蓝牙/BLE、WLAN 热点或 Wi-Fi Direct；2020 款 SYNC+ Android 8.1 车机上属于**实验路径**，
  上游按 Android 10+ 设计，重启回连问题也未解决；
- 首次先在系统蓝牙里普通配对，再进 HiCar 页面点“开始连接”，按手机提示输入六位码；
- 没有画面时先看诊断日志，不要反复删除系统蓝牙配对；详见
  [`docs/HICAR.md`](docs/HICAR.md)。

### CarPlay

- 需要 Apple MFi 认证身份，三选一：本地 `.p7b` + `.pk8`、MFi 芯片、远程 MFI 服务；
- 当前仓库只完成接入层，不含 iAP2 / MFi 握手 / CarPlay AirPlay 隧道；
- 没有认证材料时，`CarPlayActivity` 的有线/无线按钮只会返回“未配置认证”；
- 接入协议栈的路线见 [`docs/CARPLAY.md`](docs/CARPLAY.md)。

## 目录

```
icarplay/
├── app/
│   ├── src/main/kotlin/io/github/jqssun/airplay/   # 上游 AirPlay 引擎与 Compose UI
│   ├── src/main/java/com/rockjesus/icarplay/       # iCarPlay 壳层
│   │   ├── IcarLauncherActivity.java
│   │   ├── FloatService.java
│   │   ├── AndroidAutoActivity.java
│   │   ├── AndroidAutoUsbReceiver.java
│   │   ├── CarPlayActivity.java
│   │   ├── CarPlayCore.java
│   │   ├── HomeSafety.java
│   │   └── AuthSettingsActivity.java
│   ├── src/main/java/local/hicarlab/               # Huawei HiCar 实验接收端 + SafetyGuard
│   ├── src/main/jniLibs/                           # HiCar 原生库（arm64-v8a / armeabi-v7a）
│   ├── src/main/java/info/anodsplace/headunit/    # Android Auto AAP/TLS/解码/触控协议层
│   │   └── aap/protocol/proto/                    # 离线生成的 protobuf-lite Java 类
│   ├── src/main/cpp/                               # UxPlay / FFmpeg / OpenSSL 原生栈
│   └── src/main/AndroidManifest.xml
├── docs/
│   ├── INSTALL.md
│   ├── AIRPLAY.md
│   ├── ANDROID-AUTO.md
│   ├── HICAR.md
│   ├── CARPLAY.md
│   ├── SAFETY.md
│   └── BUILD.md
├── scripts/preflight-safety.sh                     # 发布前车机安全检查
├── .github/workflows/apk.yml                       # GitHub Actions 构建
└── README.md
```

## 构建

CI（推荐）：

```bash
gh workflow run apk.yml -R RockJesus/icarplay --ref main
```

构建前会运行 `scripts/preflight-safety.sh`；本机也可以先执行它。

本地构建需要 JDK 21、Android SDK 37、NDK `27.0.12077973`，并初始化子模块：

```bash
git submodule update --init --recursive
./gradlew assembleRelease
```

详见 [`docs/BUILD.md`](docs/BUILD.md)。

## 合规与来源

- 本仓库包含 **AGPL-3.0** 的 Android Auto 协议层，组合作品按 AGPL-3.0 分发；
  AGPL 全文见 [`LICENSE-AGPL-3.0.txt`](LICENSE-AGPL-3.0.txt)，来源与版权见 [`NOTICE`](NOTICE)。
- AirPlay 协议栈来自 [`jqssun/android-airplay-server`](https://github.com/jqssun/android-airplay-server)，
  其底层为 [`FDH2/UxPlay`](https://github.com/FDH2/UxPlay)，并保留上游版权与许可。
- Android Auto 协议层来自 [`harryjph/android-auto-headunit`](https://github.com/harryjph/android-auto-headunit)
  （AGPL-3.0），保留上游版权与许可；不含 Google 私有 Android Auto APK。
- Huawei HiCar 接收端来自 [`huducat586/hicar-android-receiver`](https://github.com/huducat586/hicar-android-receiver)
  （MIT，0.11.1 Preview）；原生库取自其官方 release APK，第三方许可证随包放在
  `app/src/main/assets/hicar-licenses/`，来源见 [`THIRD_PARTY_NOTICES-HICAR.txt`](THIRD_PARTY_NOTICES-HICAR.txt)。
- CarPlay 接入层的设计参考了 DiPlay / xcertplay / PlayPort 的公开架构，但**未包含**
  任何 Apple 认证材料、私钥或他人提取的配件身份。
- 本项目与 Apple、Ford、Baidu 均无关联。

## 风险

- SYNC+ 有签名白名单，自签名包可能直接装不上；
- 主界面和接收服务默认不自启，仅小白点悬浮层开机恢复，避免 Wi-Fi Direct 导致的重启循环；
- AirPlay 长期稳定性、音频通道、1920×720 适配仍需实车验证。
