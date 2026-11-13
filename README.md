# iCarPlay

面向 **2020 款 SYNC+ 1.0 车机**（Android 8.1 / API 27、1920×720）的第三方接收端：
**一个 APK 内同时提供 AirPlay 接收、Android Auto 接收、Huawei HiCar 接收、CarPlay 入口和内置悬浮按钮**。

> **当前版本 v0.7.7**
>
> 单 APK 下载：[`icarplay.apk`](https://github.com/RockJesus/icarplay/releases/latest/download/icarplay.apk)
>
> 本版 Release 只打包 **arm64-v8a + armeabi-v7a**，并且只发布一个 `icarplay.apk`：CarLife+ 接收端已内嵌，不再附带第二个 APK 或 `bdcf`。
>
> 首页是**车机一屏布局**：五张主功能卡（AirPlay / CarPlay / CarLife+ / Android Auto / HiCar）+ 一行快捷操作 + 底部“返回车机主屏幕”，不再需要下拉；宽度 ≥1100px 且宽高比 ≥1.5 的横屏（1600×900、1920×720）五卡同排；接近 4:3 的窄横屏自动 3+2 两行。首次打开会自动引导悬浮窗、麦克风/位置/蓝牙/通知和电池优化权限，并默认启动内置悬浮按钮。
>
> - **v0.7.7 调整**：连接中心「网络状态」卡新增无线能力胶囊：客户端 / 自建热点 / Wi‑Fi Direct(P2P) / 5GHz，点任一胶囊弹出「无线能力与推荐方案」（说明没有外置路由器时的三条路径：手机热点 + 车机加入、车机自建热点、USB 有线兜底）；「网络体检」结论增加无线方案建议；AirPlay 页新增「热点直连向导」，给出 iPhone 个人热点 → 车机加入 → 开始接收的三步操作与 AP 隔离提示。
> - **v0.7.6 调整**：连接中心新增「外设状态」卡，与「网络状态」并排：蓝牙（开关、已配对数量、A2DP/通话是否已连）、HiCar（系统是否装 HiCar 框架、本应用入口）、USB（已识别设备数量与 VID/PID 明细）；热点失败弹窗升级为「热点开启向导」，给出系统热点/应用内热点/回本页体检三条路径；所有弹窗改为深色主题，与赛博朋克页面统一。
> - **v0.7.5 调整**：连接中心新增「网络体检」：网关可达性（ICMP/TCP，不调用系统命令）、DNS 解析、外网 HTTPS 与本机 AirPlay 端口探测，并根据结果给出 AP 隔离/外网不通的判断；新增 AirPlay 接收状态与端口显示，以及「启动 AirPlay」「停止 AirPlay」按钮；页面改为可滚动，内容增长不再挤压底部返回按钮。
> - **v0.7.4 调整**：首页新增「连接中心」入口；新增网络与连接页，只读展示 Wi‑Fi 状态、SSID、2.4/5GHz、速率、信号、IP/网关/DNS、热点与 Wi‑Fi Direct，并提供打开 Wi‑Fi/热点设置、应用内临时热点、mDNS 体检与 AirPlay/HiCar 同网指引。
> - **v0.7.3 调整**：所有二级页顶部标题改为与首页一致的居中紫青渐变，版本胶囊固定在右上角；状态胶囊移入首个状态卡右上角；AirPlay 卡片运行时状态改为「待连接」。
> - **v0.7.2 调整**：AirPlay 接收随首页启动默认开启；顶部标题改为居中并移除副标题；悬浮按钮按真实显示区与系统栏安全区限制位置，避免拖出可视屏幕。
> - **v0.7.1 调整**：Apple、百度、Android 三个品牌标也改为按内容裁边的高分辨率位图，与华为标统一到同一视觉盒和同一 64dp 尺寸，五卡品牌标视觉体量完全一致。
> - **v0.7.0 调整**：HiCar 的华为品牌标改为高分辨率透明位图，规避部分华为车机对复杂矢量路径的解析问题；其余四张卡片的居中放大品牌 logo 保持。
> - **v0.6.9 调整**：首页五张入口卡片的品牌 logo（Apple、百度、Android、华为）从标题右侧移到卡片正中并放大；HiCar 卡片补齐华为 logo。
> - **v0.6.8 调整**：悬浮按钮弹出菜单增加全屏透明触摸层，菜单展开时点击菜单以外任何位置都会自动收起，事件不再穿透到下层应用。
> - **v0.6.7 调整**：页头去掉左侧彩虹竖条；英文标题统一为紫→青渐变；悬浮按钮改为紫青渐变圆环与图标，界面名称统一改为“悬浮按钮”。
> - **v0.6.6 调整**：全套车机 UI 改为赛博朋克风格——近黑极光背景、青/紫/粉/金霓虹强调色、1dp 彩虹渐变描边、渐变标题与等宽仪表字；首页、五个二级页、认证设置、悬浮按钮面板统一换色。
> - **v0.6.5 调整**：CarLife+ 返回按钮移到底部，与其它二级页统一为整行“← 返回主界面”；首页版本号移到右上角，移除底部页脚；五张功能卡标题右侧加入对应厂商 Logo（Apple / Baidu / Android / Huawei）。
> - **v0.6.4 调整**：悬浮球拖动与恢复位置时限制在屏幕内并保留 8dp 边距，越界坐标自动写回；CarLife+ 二级页返回按钮统一为与其他二级页一致的蓝色“← 返回主界面”。
> - **v0.6.3 调整**：统一首页与五个二级页的整页车机 UI——深色玻璃面板、状态胶囊、认证/链路指标、可复制的诊断信息；首页页脚只显示当前版本号，入口卡片显示“打开  →”；AirPlay 与 HiCar 增加“返回主界面”；HiCar 开关改为纵向整行；按锐际 1920×720 与 1600×900 做分辨率自适应，1280×758 窗口仍五卡同排不裁切。
>
> - **v0.5.4 修复**：AirPlay 接收页锁定横屏，避免从车机横屏进入后翻转成竖屏、导致预览区拉长和悬浮按钮遮住右上角按钮。
>
> - **v0.5.5 调整**：悬浮按钮快捷面板最后一项由“关闭悬浮按钮”改为“车机主屏幕”，点击直接返回原车桌面，悬浮按钮保持显示。
>
> - **v0.5.6 调整**：悬浮按钮改用无字母图标，图形为“车顶接收信号 + 车头轮廓”，同时表达车机与投屏接收。
>
> - **v0.5.7 调整**：悬浮按钮快捷面板中的“主界面”改为“iCarPlay主界面”，点击仍进入 iCarPlay 首页。
>
> - **v0.5.8 调整**：车机重启后自动恢复悬浮按钮悬浮层；只恢复悬浮球，不打开 iCarPlay 主界面，不自动启动 HiCar 接收。用户在首页手动关掉悬浮按钮后，重启不会强制拉起。
>
> - **v0.6.2 调整**：CarLife+ 按真实手机实测时序收敛——认证后立即回 FeatureConfig + 编码器提议，移除会导致手机停在 `ENCODER_INIT` 的 `HU_BT_OOB_INFO`，GNSS 订阅延后到首帧协商之后；无线只扫描 Wi‑Fi/有线 LAN，两轮探测、优先复用上次手机 IP，并新增“手动 IP”和车机端会话日志。
>
> - **v0.6.1 调整**：回退 v0.7.0 的独立 CarLife 接收端方案，把 **CarLife+ 兼容接收**直接并入 iCarPlay，首页新增第五张卡；最终只发布一个 `icarplay.apk`，不附 `carlife-vehicle.apk`、不附 `bdcf`。CarLife+ 接收核心来自 GPL-3.0 的 `FF1239/Limo-carlife`，详见 [`docs/CARLIFE-PLUS.md`](docs/CARLIFE-PLUS.md)。
>
> - **v0.6.0 调整**：统一首页与二级页的深色车机 UI（渐变背景、功能图标、状态卡和统一按钮）；移除未使用的 Compose、Hilt、DataStore 依赖，删除旧 Compose 页面并改用原生单页，同时启用 R8 与资源压缩，APK 体积明显减小。
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
> - **内置悬浮按钮：已接入**。悬浮球由本 APK 自带，不需要第二个悬浮球 App。
> - **车机桌面显示**：SYNC+ 原车桌面是系统层窗口，普通悬浮窗会被它盖住；在系统「无障碍」里开启 `iCarPlay 悬浮按钮` 后，改用无障碍悬浮层，才能显示在原车机桌面上。该服务只绘制悬浮球，不读取屏幕内容。
> - **安全边界：v0.4.0 起不抢桌面；v0.6.0 起仅悬浮按钮悬浮层开机恢复，主界面和接收服务不自动启动；连续异常进安全模式**，详见 [`docs/SAFETY.md`](docs/SAFETY.md)。

## 能力矩阵

| 能力 | 状态 | 说明 |
|---|---|---|
| AirPlay 屏幕镜像 | ✅ | iOS/iPadOS/macOS 同网发现，H.264/H.265 解码 |
| AirPlay 音频 | ✅ | AAC-ELD / AAC-LC / ALAC，AudioTrack 输出 |
| AirPlay 视频投放 | ✅ | HLS / 远程视频，播放控制 |
| Android Auto 有线（USB AOA） | ✅ 代码已接入 | 手机切 AOA 配件模式，进 AAP/TLS/H.264；需 USB Host + 数据口 |
| Android Auto 无线（Headunit Server） | ⚠️ 实验 | 手机开 Headunit Server，同一 Wi-Fi，手动输入 IP:5277 |
| Huawei HiCar（华为手机） | ⚠️ 实验 | 来自 MIT 开源接收端；需手机原生 HiCar；上游按 Android 10+ 设计，8.1 未实车验证 |
| CarLife+ 兼容接收（有线 / 无线） | ⚠️ 待真机复测 | 内嵌 GPL-3.0 接收核心；v0.6.2 按真实 CarLife+ 时序修正握手，并增强无线发现/重连；不是百度官方车机端 SDK，不保证无感连接/原生小程序 |
| 内置悬浮按钮 | ✅ | 可拖动，点按弹出 AirPlay / Android Auto / HiCar / CarPlay / 认证设置 / 系统设置 / iCarPlay主界面 / 车机主屏幕 |
| CarPlay 有线入口 | ⚠️ 骨架 | USB 设备枚举 + 启动状态，协议引擎未接入 |
| CarPlay 无线入口 | ⚠️ 骨架 | Wi-Fi 状态 + 启动状态，协议引擎未接入 |
| CarPlay 认证后端 | ⚠️ 骨架 | 本地证书 / MFi 芯片 / 远程 MFI 三选一，仅保存与检查 |
| 车机安全约束 | ✅ | 不写系统分区、不申请设备管理员/系统设置权限、不抢占默认桌面；开机仅恢复悬浮按钮悬浮层，HiCar 开机接收仍默认关闭 |
| 不需要第二个 APK | ✅ | 悬浮球、AirPlay、Android Auto、HiCar、CarPlay 入口、CarLife+ 接收都在同一个包里；不再附带 `bdcf` |

## 安装（SYNC+ 车机）

1. U 盘格式 **FAT32**，把唯一的 `icarplay.apk` 放根目录；
2. 输入法漏洞进安卓原生设置 → 存储 → U 盘 → 安装；
3. 如果报 **“应用未安装”**，说明撞上 SYNC+ 的签名白名单，需要百度签名重签；
4. 装完桌面没有图标，从 **设置 → 应用 → iCarPlay → 打开** 进入；
5. 首次打开会自动弹出权限引导：允许悬浮窗，再依次允许麦克风、位置、蓝牙、通知和电池优化；悬浮按钮默认自动启动；
6. 首页点 CarLife+ → 有线连接 / 无线连接；点悬浮球 → AirPlay 开始接收；Android Auto / HiCar 进入接收页；CarPlay 进入认证/连接页。

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
│   ├── src/main/kotlin/io/github/jqssun/airplay/   # 上游 AirPlay 引擎（原生单页 UI）
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
- 主界面和接收服务默认不自启，仅悬浮按钮悬浮层开机恢复，避免 Wi-Fi Direct 导致的重启循环；
- AirPlay 长期稳定性、音频通道、1920×720 适配仍需实车验证。
