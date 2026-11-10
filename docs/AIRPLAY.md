# AirPlay 接收说明

## 协议来源

AirPlay 核心来自 [`jqssun/android-airplay-server`](https://github.com/jqssun/android-airplay-server)，
它是 [UxPlay](https://github.com/FDH2/UxPlay) 的 Android 移植：

- C/C++ 原生栈负责 RAOP / AirPlay 信令、FairPlay、H.264/H.265 解复用；
- Kotlin 层负责 MediaCodec 解码、SurfaceView 渲染、AudioTrack 输出；
- 支持 AirPlay 2、屏幕镜像、音频、视频投放、HLS。

本仓库只改了应用外壳（入口、悬浮球、CarPlay 入口），没有改协议核心。

## 怎么用

1. 车机与 iPhone 连同一个 Wi-Fi（推荐 5GHz 外接路由）；
2. 打开 iCarPlay → 点“AirPlay 接收”，或点悬浮球 → AirPlay；
3. iPhone 控制中心 → 屏幕镜像 → 选择 `iCarPlay`；
4. 首次可能弹 PIN（可在上游设置里关闭/修改）；
5. 镜像画面会全屏渲染，音频走车机音频输出。

## 能做什么

- iPhone / iPad / Mac 屏幕镜像；
- AirPlay 音频（AAC-ELD / AAC-LC / ALAC）；
- AirPlay 视频投放（HLS / 远程 URL）；
- 播放/暂停、音量等基础控制；
- Android TV / 车机横屏。

## 不能做什么

- **没有触控回传**：车机屏幕不能反向控制 iPhone；
- **DRM 内容黑屏**：Netflix、Apple TV+ 等受保护内容不支持；
- **不是 CarPlay**：没有 CarPlay 认证 UI、方控、Siri、电话簿；
- 1920×720（8:3）上 16:9 画面会留黑边；
- 长时间稳定性、发热、音频通道仍需实车验证。

## 常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| iPhone 看不到 `iCarPlay` | 不在同一子网 / AP 隔离 / mDNS 被挡 | 换路由，关闭 AP 隔离，确认同一网段 |
| 有画面没声音 | 音频通道不匹配 | 在认证设置里试“通道 9” |
| 画面卡顿 | 2.4G 或信号差 | 用 5GHz，靠近路由 |
| 黑屏但有连接 | DRM 内容 | 换非 DRM 内容测试 |
| 连接后车机重启循环 | Wi-Fi Direct / 自启 | 关闭自启，不要开 P2P |
