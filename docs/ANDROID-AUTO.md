<div align="center">

# Android Auto 接收

USB AOA 有线为主路径，手机 Headunit Server 手动无线为实验路径。

`状态：协议已接通 · 待实车验证`　·　`AGPL-3.0 协议层`　·　[← 返回 README](../README.md)

</div>

---

协议层来自 [`harryjph/android-auto-headunit`](https://github.com/harryjph/android-auto-headunit)（AGPL-3.0），去掉了它原有的旧 UI 与开机自启，保留：

- USB AOA（Android Open Accessory）切换；
- AAP 消息编解码（protobuf-lite，离线生成，不依赖 Gradle protoc 插件）；
- TLSv1.2 握手（使用开源项目内置的 Google Automotive Link 测试身份）；
- H.264 MediaCodec 解码 / 音频输出 / 触控与按键回传 / 定位发送。

## 入口

首页的 **Android Auto** 卡与悬浮按钮面板都有入口。页面提供四件事：

1. **连接 USB 手机（推荐）**：列 USB 设备 → 请求权限 → 发送 AOA 切换 → 手机重新枚举为 `0x18D1:0x2D00/0x2D01` → 启动 `AapService`；
2. **无线连接（手机 Headunit Server）**：手机 Android Auto 开发者选项里 `Start headunit server`，车机与手机同一 Wi‑Fi，输入手机 IP，端口 `5277`；
3. **打开投影画面**：进入 `AapProjectionActivity`，显示手机画面并回传触控；
4. **停止 Android Auto**：停止 `AapService`。

手机切到配件模式后，`AndroidAutoUsbReceiver` 也会直接启动服务，插线后不必每次手动点“连接 USB”。

## 必须满足

| 条件 | 说明 |
|---|---|
| USB Host + 数据 | 车机 USB 口必须支持 USB Host 与数据，只供电的口不行 |
| 手机端 Android Auto | 必须安装；部分国行 / 定制系统会裁剪或限制 |
| USB 权限 | 首次连接要在手机上确认 USB 权限 |
| 无线前提 | 依赖手机端 `Headunit Server`，不是原生无线 AA 的蓝牙 + Wi‑Fi Direct 自动握手 |

## 已知边界

- 尚未在 2020 款 SYNC+ 真车上验证；
- 车机没有 GMS 不影响“接收端”，但手机端 Android Auto 被厂商裁剪时仍然连不上；
- 无线只做手动 IP 的 TCP 5277；不做蓝牙引导 + Wi‑Fi Direct——后者在 SYNC+ 上曾出现 Wi‑Fi Direct 重启循环，且 Android Auto 17.4+ 对传统无线触发有限制；
- 1920×720 是 8:3，Android Auto 画面按 16:9 输出时会左右留黑边；
- 音频 / 麦克风需要 `RECORD_AUDIO`，定位需要 `ACCESS_FINE_LOCATION`；拒绝授权只影响对应通道，不影响视频与触控；
- 与 CarLife+ 有线共用 USB AOA，同一时刻只能有一个接收端占用 USB，切换前先在当前页点「停止」。

## 构建说明

7 个 protobuf-lite Java 类已离线生成并提交到：

```
app/src/main/java/info/anodsplace/headunit/aap/protocol/proto/
```

所以 CI 不需要 `com.google.protobuf` Gradle 插件与 `protoc`，只依赖运行期 `com.google.protobuf:protobuf-lite:3.0.1`。如需重新生成：

```bash
protoc -I app/src/main/proto \
  --plugin=protoc-gen-javalite=/path/to/protoc-gen-javalite \
  --javalite_out=app/src/main/java \
  app/src/main/proto/*.proto
```

## 许可

Android Auto 模块为 AGPL-3.0，与 iCarPlay 其余部分组合后按 AGPL-3.0 分发。完整文本见仓库根目录 `LICENSE-AGPL-3.0.txt`，来源与版权见 `NOTICE`。

## 相关文档

[`CARLIFE-PLUS.md`](CARLIFE-PLUS.md) USB AOA 冲突与切换 · [`INSTALL.md`](INSTALL.md) 首次验证清单
