<div align="center">

# CarPlay 接入

接入层已完成，协议引擎未接入：**没有 Apple MFi 认证身份时，iPhone 会在认证阶段直接拒绝。**

`状态：骨架 · 需 MFi 材料`　·　`不含任何 Apple 私钥或他人配件身份`　·　[← 返回 README](../README.md)

</div>

---

## 已经完成的部分

- 认证后端 4 选 1（未配置 / 本地证书 / MFi 芯片 / 远程 MFI）；
- Apple USB 设备枚举（vendor id `0x05AC`）；
- 有线 / 无线启动入口与状态返回；
- 认证与连接设置页；
- 与 AirPlay、悬浮按钮共用的单 APK 外壳。

**没有完成的是协议引擎**：iAP2 识别、MFi 认证签名、CarPlay 的 AirPlay 隧道、H.264 解码、HID 触控 / 方控、USB NCM、无线蓝牙引导 + Wi‑Fi 切换。

## 代码位置

| 文件 | 作用 |
|---|---|
| `app/src/main/java/com/rockjesus/icarplay/CarPlayCore.java` | 认证后端、USB 枚举、启动状态 |
| `CarPlayActivity.java` | 有线 / 无线入口、认证检查、状态展示 |
| `AuthSettingsActivity.java` | 认证后端 / 音频通道 / 网络模式 |
| `FloatService.java` | 悬浮按钮里的 CarPlay 快捷入口 |

## 三种认证后端

### 1. 本地证书（`.p7b` + `.pk8`）

- 证书：DER 编码 PKCS#7，文件名 `certificate.p7b`；
- 私钥：未加密 DER PKCS#8，文件名 `identity.pk8`；
- 放置位置（二选一）：
  - `app/src/main/assets/carplay/`（需重新构建 APK）；
  - `/sdcard/Android/data/com.rockjesus.icarplay/files/carplay/`；
- 当前代码只做“文件存在性检查”，没有加载证书与签名。

### 2. MFi 芯片

- 板载 I2C：`/dev/i2c-N` 上的 Apple 认证协处理器；
- 或 CH341 USB 转 I2C 桥；
- 需要在 native 层实现 I2C 读写与 MFi 签名协议；
- 当前只有设置项，没有驱动。

### 3. 远程 MFI 服务

| 接口 | 作用 |
|---|---|
| `GET /mfi/certificate` | 返回配件证书 |
| `POST /mfi/sign` | 对 challenge 签名 |
| `POST /mfi/reset` | 重置会话 |

当前只有设置项，没有 HTTP 客户端。

## 还缺哪些协议模块

1. **iAP2**：USB / 蓝牙链路上的配件识别、会话建立；
2. **MFi**：证书链、challenge 签名、配件身份校验；
3. **CarPlay AirPlay 隧道**：RTSP / HTTP 信令、加密流、H.264 / H.265；
4. **显示**：MediaCodec → SurfaceView，1920×720 letterbox；
5. **音频**：通道 9 / 默认通道，AudioTrack 路由；
6. **输入**：HID 触控回传、方控按键映射；
7. **USB**：NCM / 有线 CarPlay 网络接口；
8. **无线**：蓝牙引导 + Wi‑Fi 凭据切换 + mDNS 广播。

## 推荐接入路线

| 路线 | 说明 | 前置条件 |
|---|---|---|
| **DiPlay-Legacy** | 与目标车机同源硬件已有实车记录，GPLv3 | 认证材料 + 把 `:mobile` 模块并入本仓库 |
| **PlayPort** | 同为 GPLv3，JVM 侧 CarPlay 实现，架构清晰 | 认证材料 + 移植到 Android / WebCodecs 替换 |
| **xcertplay** | 协议实现参考，Android 9+ | 认证材料 + 处理 API 27 兼容 |

三条路线都绕不开同一件事：**MFi 认证身份**。

## 拿到材料后的接入顺序

1. 在 `AuthSettingsActivity` 选“本地证书”，把 `.p7b` / `.pk8` 放到 assets；
2. 增加 `carplay/` native 模块，接 DiPlay-Legacy 的 iAP2 + MFi + AirPlay 隧道；
3. 把解码输出接到 `SurfaceView`，音频走通道 9；
4. 先做**有线 USB**，跑通后再做无线蓝牙引导；
5. 用 `CarPlayActivity` 做状态机：未配置 → 等待设备 → 认证中 → 已连接 → 断开重连。

## 明确不做的事

- 不生成、不伪造 Apple 认证材料；
- 不把他人提取的配件身份打进仓库；
- 不承诺“没有认证也能连”；
- 不在车机上注册开机自启，避免重启循环。

## 相关文档

[`AIRPLAY.md`](AIRPLAY.md) 车机侧 AirPlay 通道 · [`SAFETY.md`](SAFETY.md) 不开机自启的约定

## 连接方式：先检测，再连接

参考 CarUp 的思路，CarPlay 页的有线/无线入口现在都会先进入「连接方式检测」页：

| 栏位 | 检测项 |
|---|---|
| **有线**（9 项） | USB Host 能力、USB 设备枚举、Apple 设备（VID 0x05AC）、USB 访问权限、MFi 认证后端、本地证书材料、CarPlay 协议引擎、数据线要求、iPhone 端状态 |
| **无线**（9 项） | Wi‑Fi 开关、Wi‑Fi Direct、蓝牙开关、BLE 支持、当前 Wi‑Fi SSID、频段、MFi 认证后端、CarPlay 协议引擎、iPhone 端状态 |

每项右侧直接给出状态（就绪 / 待满足 / 不可用），栏头显示 `n/9 就绪`。底部三个动作：

- **刷新检测**：重新枚举 USB 设备与网络状态
- **请求 USB 权限**：对已识别的 Apple 设备真正调起系统授权弹框，授权结果回写列表（不再只是提示文字）
- **复制报告**：把两组检测结果整理成文本，粘贴出来即可定位卡在哪一步

### 关于协议引擎

检测页里「CarPlay 协议引擎」一项固定显示**未接入**，这是实话：真正的 CarPlay 会话需要 MFi 认证材料 + 协议引擎，本应用目前只有链路、枚举、认证后端与检测，CarUp 的引擎是闭源的。这一项就是后续要补的核心。

## 与 DiPlay 的关系（引擎补齐路径）

DiPlay（`com.shihab.diplay`，GPLv3，开源）是独立的 Android 车机 CarPlay 接收端，自带 `assets/offline-mfi/certificate.p7b` + `identity.pk8`——也就是本应用一直缺的离线 MFi 身份，还带 `libxcertplay_i2c.so` 等原生库。

本应用**不复制**它的资产或代码，只做入口桥接（`DiPlayBridge`）：

| 能力 | 行为 |
|---|---|
| 检测安装 | 读 `com.shihab.diplay` 的版本号，CarPlay 页显示 `DiPlay v0.2.22` 或「未安装」 |
| 启动 | 「启动 CarPlay」调 `getLaunchIntentForPackage` 拉起 DiPlay |
| 获取 | 「获取 DiPlay」打开官方 Releases 页 |
| 检测页联动 | 有线/无线两栏的「CarPlay 协议引擎」一项改为显示 DiPlay 状态，装上即变绿 |

兼容性判断（对 2020 款锐际 SYNC+，Android 8.1 / API 27）：DiPlay 要求 **Android 7.0+（API 24）**，`arm64-v8a` 支持，两者都满足；它在 Android 7.0–8.1 上**有线 CarPlay 已被一台车验证可用**，无线在该区间尚未在实车确认。

