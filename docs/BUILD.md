# 构建说明

## 推荐：GitHub Actions

仓库自带 `.github/workflows/apk.yml`：

```bash
gh workflow run apk.yml -R RockJesus/icarplay --ref main
gh run list -R RockJesus/icarplay --limit 3
```

构建产物：

```
app/build/outputs/apk/release/app-release-unsigned.apk
```

CI 只产出**未签名** release APK，签名在本地完成。

## 本地构建

### 依赖

- JDK 21；
- Android SDK Platform 37；
- NDK `27.0.12077973`；
- CMake（AGP 会自动使用 SDK 里的版本）；
- Git 子模块：UxPlay、libplist、openssl-cmake、FFmpeg。

### 初始化

```bash
git submodule update --init --recursive
```

### 编译

```bash
./gradlew assembleRelease
```

原生栈首次编译较慢（FFmpeg / OpenSSL / UxPlay），建议至少 8 GB 内存。

## 签名

```bash
BT=$ANDROID_HOME/build-tools/33.0.2
$BT/zipalign -f 4 app-release-unsigned.apk icarplay-aligned.apk
$BT/apksigner sign \
  --ks keystore/icarplay.jks \
  --ks-pass pass:******** \
  --key-pass pass:******** \
  --ks-key-alias icarplay \
  --out icarplay-v0.1.0.apk icarplay-aligned.apk
$BT/apksigner verify --print-certs icarplay-v0.1.0.apk
```

## 本机环境注意

当前开发机（Linux VM）的 Gradle 依赖解析存在 PKIX 证书问题，
因此本项目优先走 GitHub Actions 构建；本地只做源码修改与静态检查。

## 版本号

- `app/build.gradle.kts` 的 `versionCode` / `versionName`；
- Release tag 与 `versionName` 对齐，例如 `v0.1.0`；
- 下一版按十进制递增：`0.1.0 → 0.2.0`（段内不超过 9）。
