# SKRoot Pro Compose

Kotlin + Jetpack Compose UI 重构版的 SKRoot Pro 权限管理器源码。

本仓库基于 [SKRoot-linuxKernelRoot](https://github.com/abcz316/SKRoot-linuxKernelRoot) 中的 Pro `PermissionManager` 工程整理，重点重做管理器的 UI、状态流和导航层，采用 Material 3 / Compose 实现 KSU 风格的管理界面。

> 本项目保留原 SKRoot Pro 的 Native/JNI、AIDL、Magica 服务、JSON 协议、应用 ID、权限和 ARM64 Native 构建结构。源码中的上游实现与资源归属原项目作者。

## 当前版本

- 管理器版本：`4.5.3`
- Compose UI 发布版本：`v4.5.3-compose.2`
- 默认 Application ID：`com.linux.permissionmanager`
- `minSdk`：26
- `targetSdk`：31
- ABI：`arm64-v8a`

## 主要内容

- Material 3 Compose 单 Activity 架构
- 自适应手机底部导航与平板 NavigationRail
- 主页环境状态、Root 命令终端、系统状态卡片
- SU 授权管理与应用选择器
- 已安装模块、模块市场、更新和下载进度
- 设置、诊断、日志查看与导出
- Root Key、Boot / 热启动配置和重启选项
- `StateFlow` UI 状态、`SharedFlow` 一次性事件和 Repository 数据层
- 浅色/深色主题、动态强调色和纯白浅色背景

## 构建环境

- JDK 17
- Gradle 8.9（使用仓库中的 Gradle Wrapper）
- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21
- compileSdk 35 / Build Tools 35.0.0
- NDK `26.3.11579264`
- CMake 3.18.1

## 构建命令

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

### 品牌 Release 构建

构建参数可以覆盖 Application ID 和桌面应用名称。Android Application ID 不接受 `*` 字符，因此请求中的 `com.linux.**compose` 使用规范化后的 `com.linux.compose`。

```bash
# SKRoot Bro
./gradlew clean :app:assembleRelease \
  -PPUBLISH_APPLICATION_ID=com.linux.compose \
  -PPUBLISH_APP_NAME="SKRoot Bro"

# 顺丰速运
./gradlew clean :app:assembleRelease \
  -PPUBLISH_APPLICATION_ID=com.sf.activity \
  -PPUBLISH_APP_NAME="顺丰速运"
```

正式分发时通过以下 Gradle 参数注入签名密钥；密钥文件和密码不应提交到仓库：

```text
-PRELEASE_STORE_FILE=... \
-PRELEASE_STORE_PASSWORD=... \
-PRELEASE_KEY_ALIAS=... \
-PRELEASE_KEY_PASSWORD=...
```

Debug APK 输出路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

项目只配置 `arm64-v8a` Native ABI。安装和运行前，请确认设备、Root Key 与 SKRoot 环境配置符合上游项目要求。

本次公开 Release 提供两个签名 APK：

- `com.linux.compose` / `SKRoot Bro`
- `com.sf.activity` / `顺丰速运`

## 上游项目与致谢

- 上游项目：[abcz316/SKRoot-linuxKernelRoot](https://github.com/abcz316/SKRoot-linuxKernelRoot)
- UI 视觉参考：[tiann/KernelSU](https://github.com/tiann/KernelSU)

本仓库不复制 KernelSU 的页面源码或资源，仅参考其 Material 管理器视觉理念并进行独立 Compose 实现。

## 变更范围

本版本主要包含 UI、状态管理、导航、主题和构建迁移。Native C/C++ 函数体、JNI 声明、AIDL 接口、Magica 服务和现有业务协议保持原语义。

## GitHub Actions 自助品牌构建

仓库内置 `.github/workflows/custom-build.yml`，可在 GitHub 网页上按需构建带自定义品牌的签名 Release APK。Workflow 不修改业务代码，只在构建时注入 Application ID、应用名称和启动图标。

### 首次配置签名 Secrets

进入仓库 **Settings → Secrets and variables → Actions → New repository secret**，添加以下四项：

| Secret | 内容 |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | PKCS12/JKS 签名文件经过 Base64 编码后的单行文本 |
| `RELEASE_STORE_PASSWORD` | keystore 密码 |
| `RELEASE_KEY_ALIAS` | 签名 alias |
| `RELEASE_KEY_PASSWORD` | alias 密码 |

签名文件和密码不会写入源码。可以使用以下命令生成 Base64 内容（Windows PowerShell 可使用 `[Convert]::ToBase64String([IO.File]::ReadAllBytes('release.p12'))`）：

```bash
base64 -w 0 release.p12 > release.p12.base64
```

### 运行构建

1. 打开仓库的 **Actions → Custom branded Release build → Run workflow**。
2. 填写 `application_id` 和 `app_name`。
3. 可选填写 `icon_url`（必须是 HTTPS 地址，图片大小不超过 2 MiB，建议使用 PNG）以及 `icon_background`（六位十六进制颜色）。
4. 需要发布到 Releases 时勾选 `create_release`，并可填写 `release_tag`；留空会使用 `custom-运行编号`。
5. 工作流完成后，在运行详情的 **Artifacts** 下载 APK；勾选发布时也可以在仓库 **Releases** 页面下载。

`application_id` 必须符合 Android 规则：每段以小写字母开头，只包含小写字母、数字和下划线，并以点分隔，例如 `com.example.custom`。`com.linux.**compose` 中的星号不是合法字符，请在表单中填写规范化后的 `com.linux.compose`。应用名称支持 1–80 个可打印字符。

工作流会执行资源图标生成、Release 签名、`aapt` 包名/名称校验、APK v2 签名校验，并在摘要中显示 APK 路径和 SHA-256。图标未填写时沿用仓库默认图标。
