# iCarPlay

面向 **2020 款福特锐际 SYNC+ 1.0 车机**（Android 8.1 / API 27、1920×720）的第三方接收端：
**一个 APK 内同时提供 AirPlay 接收、CarPlay 入口和内置小白点**。

> **当前版本 v0.1.0**
>
> - **AirPlay：已接入**。基于 GPLv3 的 UxPlay / `jqssun/android-airplay-server` 协议栈，
>   支持 iPhone / iPad / Mac 的屏幕镜像与音频，Android 7.0+ 可运行。
> - **CarPlay：接入层已搭建**。认证后端、Apple USB 枚举、有线/无线入口、设置页已完成；
>   **真正的 iAP2 / MFi / AirPlay 隧道尚未接入**，没有 Apple MFi 认证材料时 iPhone 不会完成配对。
> - **内置小白点：已接入**。悬浮球由本 APK 自带，不需要第二个悬浮球 App。

## 能力矩阵

| 能力 | 状态 | 说明 |
|---|---|---|
| AirPlay 屏幕镜像 | ✅ | iOS/iPadOS/macOS 同网发现，H.264/H.265 解码 |
| AirPlay 音频 | ✅ | AAC-ELD / AAC-LC / ALAC，AudioTrack 输出 |
| AirPlay 视频投放 | ✅ | HLS / 远程视频，播放控制 |
| 内置小白点 | ✅ | 可拖动，点按弹出 AirPlay / CarPlay / 设置 / 主界面 / 关闭 |
| CarPlay 有线入口 | ⚠️ 骨架 | USB 设备枚举 + 启动状态，协议引擎未接入 |
| CarPlay 无线入口 | ⚠️ 骨架 | Wi-Fi 状态 + 启动状态，协议引擎未接入 |
| CarPlay 认证后端 | ⚠️ 骨架 | 本地证书 / MFi 芯片 / 远程 MFI 三选一，仅保存与检查 |
| 不需要第二个 APK | ✅ | 悬浮球、AirPlay、CarPlay 入口都在同一个包里 |

## 安装（SYNC+ 车机）

1. U 盘格式 **FAT32**，把 `icarplay-v0.1.0.apk` 放根目录；
2. 输入法漏洞进安卓原生设置 → 存储 → U 盘 → 安装；
3. 如果报 **“应用未安装”**，说明撞上 SYNC+ 的签名白名单，需要百度签名重签；
4. 装完桌面没有图标，从 **设置 → 应用 → iCarPlay → 打开** 进入；
5. 授予“显示在上层 / 悬浮窗”，点“开启内置小白点”；
6. 点悬浮球 → AirPlay 开始接收；CarPlay 进入认证/连接页。

详细步骤与判读见 [`docs/INSTALL.md`](docs/INSTALL.md)。

## 两个核心的边界

### AirPlay

- 不需要 MFi，iPhone 控制中心选“屏幕镜像”即可发现 `iCarPlay`；
- 车机与 iPhone 必须在同一局域网（推荐外接 5GHz 路由，SYNC+ 自带热点 iPhone 搜不到）；
- **单向输出**：没有触控回传，不能在车机上反向控制 iPhone；
- DRM 内容（Netflix、Apple TV+）黑屏；
- 1920×720 是 8:3，16:9 的 iPhone 画面会留黑边。

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
│   │   ├── CarPlayActivity.java
│   │   ├── CarPlayCore.java
│   │   └── AuthSettingsActivity.java
│   ├── src/main/cpp/                               # UxPlay / FFmpeg / OpenSSL 原生栈
│   └── src/main/AndroidManifest.xml
├── docs/
│   ├── INSTALL.md
│   ├── AIRPLAY.md
│   ├── CARPLAY.md
│   └── BUILD.md
├── .github/workflows/apk.yml                       # GitHub Actions 构建
└── README.md
```

## 构建

CI（推荐）：

```bash
gh workflow run apk.yml -R RockJesus/icarplay --ref main
```

本地构建需要 JDK 21、Android SDK 37、NDK `27.0.12077973`，并初始化子模块：

```bash
git submodule update --init --recursive
./gradlew assembleRelease
```

详见 [`docs/BUILD.md`](docs/BUILD.md)。

## 合规与来源

- 本仓库是 **GPL-3.0** 衍生作品，分发/修改需遵守 GPLv3。
- AirPlay 协议栈来自 [`jqssun/android-airplay-server`](https://github.com/jqssun/android-airplay-server)，
  其底层为 [`FDH2/UxPlay`](https://github.com/FDH2/UxPlay)，并保留上游版权与许可。
- CarPlay 接入层的设计参考了 DiPlay / xcertplay / PlayPort 的公开架构，但**未包含**
  任何 Apple 认证材料、私钥或他人提取的配件身份。
- 本项目与 Apple、Ford、Baidu 均无关联。

## 风险

- SYNC+ 有签名白名单，自签名包可能直接装不上；
- 车机默认不自启，避免 Wi-Fi Direct 导致的重启循环；
- AirPlay 长期稳定性、音频通道、1920×720 适配仍需实车验证。
