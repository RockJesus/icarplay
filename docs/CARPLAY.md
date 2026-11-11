# CarPlay 接入说明（当前为骨架）

## 结论先说

**当前 v0.4.4 的 CarPlay 仍不能真正连接 iPhone。**

仓库里已经完成的是“接入层”：

- 认证后端 4 选 1（未配置 / 本地证书 / MFi 芯片 / 远程 MFI）；
- Apple USB 设备枚举（vendor id `0x05AC`）；
- 有线 / 无线启动入口与状态返回；
- 认证与连接设置页；
- 与 AirPlay、悬浮球共用的单 APK 外壳。

**没有完成的是协议引擎**：iAP2 识别、MFi 认证签名、CarPlay 的 AirPlay 隧道、
H.264 解码、HID 触控/方控、USB NCM、无线蓝牙引导 + Wi-Fi 切换。

没有 Apple MFi 认证身份，iPhone 会在认证阶段直接拒绝，协议引擎写了也连不上。

## 代码位置

| 文件 | 作用 |
|---|---|
| `app/src/main/java/com/rockjesus/icarplay/CarPlayCore.java` | 认证后端、USB 枚举、启动状态 |
| `CarPlayActivity.java` | 有线/无线入口、认证检查、状态展示 |
| `AuthSettingsActivity.java` | 认证后端 / 音频通道 / 网络模式 |
| `FloatService.java` | 悬浮球里的 CarPlay 快捷入口 |

## 三种认证后端

### 1. 本地证书（`.p7b` + `.pk8`）

- 证书：DER 编码 PKCS#7，文件名 `certificate.p7b`；
- 私钥：未加密 DER PKCS#8，文件名 `identity.pk8`；
- 放置位置（二选一）：
  - `app/src/main/assets/carplay/`（需重新构建 APK）；
  - `/sdcard/Android/data/com.rockjesus.icarplay/files/carplay/`；
- 当前代码只做“文件存在性检查”，没有加载证书和签名。

### 2. MFi 芯片

- 板载 I2C：`/dev/i2c-N` 上的 Apple 认证协处理器；
- 或 CH341 USB 转 I2C 桥；
- 需要在 native 层实现 I2C 读写与 MFi 签名协议；
- 当前只有设置项，没有驱动。

### 3. 远程 MFI 服务

需要自建三个接口：

| 接口 | 作用 |
|---|---|
| `GET /mfi/certificate` | 返回配件证书 |
| `POST /mfi/sign` | 对 challenge 签名 |
| `POST /mfi/reset` | 重置会话 |

当前只有设置项，没有 HTTP 客户端。

## 还缺哪些协议模块

1. **iAP2**：USB / 蓝牙链路上的配件识别、会话建立；
2. **MFi**：证书链、challenge 签名、配件身份校验；
3. **CarPlay AirPlay 隧道**：RTSP/HTTP 信令、加密流、H.264/H.265；
4. **显示**：MediaCodec → SurfaceView，1920×720 letterbox；
5. **音频**：通道 9 / 默认通道，AudioTrack 路由；
6. **输入**：HID 触控回传、方控按键映射；
7. **USB**：NCM / 有线 CarPlay 网络接口；
8. **无线**：蓝牙引导 + Wi-Fi 凭据切换 + mDNS 广播。

## 推荐接入路线

| 路线 | 说明 | 前置条件 |
|---|---|---|
| **DiPlay-Legacy** | 与锐际同源硬件已有实车记录，GPLv3 | 认证材料 + 把 `:mobile` 模块并入本仓库 |
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
