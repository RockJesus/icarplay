<div align="center">

# 构建与签名

CI 出未签名 release 包，本机做签名与发布前安全检查，最终只发布一个 `icarplay.apk`。

`当前版本 0.9.3 / versionCode 49`　·　`compileSdk 37 · minSdk 23 · NDK 27.0.12077973`　·　[← 返回 README](../README.md)

</div>

---

## 推荐：GitHub Actions

仓库自带 `.github/workflows/apk.yml`，构建前会跑 `scripts/preflight-safety.sh`：

```bash
gh workflow run apk.yml -R RockJesus/icarplay --ref main
gh run list -R RockJesus/icarplay --limit 3
```

产物：

```
app/build/outputs/apk/release/app-release-unsigned.apk
```

CI 只产出**未签名** release APK，签名在本地完成。

## 本地构建

| 依赖 | 版本 |
|---|---|
| JDK | 17（CI 使用 21） |
| Android SDK Platform | 37 |
| NDK | `27.0.12077973` |
| CMake | AGP 自动使用 SDK 内置版本 |
| Git 子模块 | UxPlay、libplist、openssl-cmake、FFmpeg |

```bash
git submodule update --init --recursive
./gradlew :app:assembleRelease --console=plain -x lint
```

原生栈首次编译较慢（FFmpeg / OpenSSL / UxPlay），建议至少 8 GB 内存。

## 签名

```bash
BT=$ANDROID_HOME/build-tools/37.0.0
$BT/zipalign -p -f 4 app-release-unsigned.apk dist/aligned.apk
$BT/apksigner sign \
  --ks keystore/icarplay.jks \
  --ks-key-alias icarplay \
  --ks-pass pass:******** \
  --key-pass pass:******** \
  --out dist/icarplay.apk dist/aligned.apk
$BT/apksigner verify --print-certs dist/icarplay.apk
```

签名后跑一次安全检查，再核对体积与 SHA-256：

```bash
bash scripts/preflight-safety.sh
sha256sum dist/icarplay.apk
```

## 预览图

README 里的界面预览图由脚本生成（HTML 复刻 → Chromium 截图，1600×900）：

```bash
python3 scripts/render-preview.py     # 输出到 docs/preview/
```

脚本读取 `PageUi` 的色板、`res/drawable-nodpi` 的品牌位图与 `res/drawable` 的矢量图标，所以改 UI 后重跑即可同步预览图。

## 版本号

- `app/build.gradle.kts` 的 `versionCode` / `versionName`；
- 首页右上角胶囊与 `DlnaRenderer` 的 `modelNumber` 跟 `versionName` 对齐；
- Release tag 与 `versionName` 对齐，例如 `v0.9.3`；
- 递增按十进制逐位，段内不超过 9：`0.9.0 → 0.9.1 … 0.9.9 → 1.0.0`。

## 发布流程

1. 改版本号 → 构建 → 签名 → `preflight-safety.sh` 两项 OK；
2. 提交并推送 `main`，打 tag（与 `versionName` 一致）；
3. `gh release create <tag> dist/icarplay.apk --title "iCarPlay <tag>"`；
4. `gh workflow run apk.yml --ref main`，确认 CI 结论为 `success`。

## 相关文档

[`INSTALL.md`](INSTALL.md) 装车步骤 · [`SAFETY.md`](SAFETY.md) 预检规则清单
