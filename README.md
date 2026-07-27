# SKRoot Pro Compose

Kotlin + Jetpack Compose UI 重构版的 SKRoot Pro 权限管理器源码。

本仓库基于 [SKRoot-linuxKernelRoot](https://github.com/abcz316/SKRoot-linuxKernelRoot) 中的 Pro `PermissionManager` 工程整理，重点重做管理器的 UI、状态流和导航层，采用 Material 3 / Compose 实现 KSU 风格的管理界面。

> 本项目保留原 SKRoot Pro 的 Native/JNI、AIDL、Magica 服务、JSON 协议、应用 ID、权限和 ARM64 Native 构建结构。源码中的上游实现与资源归属原项目作者。

## 当前版本

- 管理器版本：`4.5.4`
- Compose UI 发布版本：`v4.5.4-compose.8`
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
- 设备内本地定制管理器：自定义包名、名称和图标后直接导出或安装 APK
- `StateFlow` UI 状态、`SharedFlow` 一次性事件和 Repository 数据层
- 五套浅色配色、自定义背景，以及可折叠的背景图片、控件与栏位透明度设置
- 可选的管理器更新检测，默认关闭

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
# SKRoot Pro
./gradlew clean :app:assembleRelease \
  -PPUBLISH_APPLICATION_ID=com.linux.compose \
  -PPUBLISH_APP_NAME="SKRoot Pro"

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

### 设备内本地定制

在应用的 **设置 → 管理器 → 本地定制管理器** 中，可直接以当前安装的单 APK 为模板，在设备内修改包名、管理器名称与五档密度启动图标。构建过程不连接 GitHub Actions 或其他服务；生成包会删除模板签名，使用设备内首次创建并持久复用的本地身份进行 APK v2/v3 签名，再校验 Manifest、应用名称、签名、图标和三项 ARM64 Native 库。

同一安装保留应用数据时，本地身份会持续复用，因此相同定制包名可直接更新。若设备上已有同包名但签名不同的应用，界面会提示签名冲突；仍可仅导出 APK，安装前需更换包名或卸载冲突应用。

最新公开 Release [v4.5.4-compose.8](https://github.com/dreamcolor123/SKRoot-Pro-Compose/releases/tag/v4.5.4-compose.8) 提供两个 APK：

- [`SKRoot Pro` / `com.linux.compose` Release](https://github.com/dreamcolor123/SKRoot-Pro-Compose/releases/download/v4.5.4-compose.8/SKRoot-Pro-com.linux.compose-v4.5.4-release.apk)
- [`SKRoot(Pro)` / `com.linux.permissionmanager` Debug](https://github.com/dreamcolor123/SKRoot-Pro-Compose/releases/download/v4.5.4-compose.8/SKRoot-Pro-com.linux.permissionmanager-v4.5.4-debug.apk)

SHA-256：

```text
a5ece669c5a1eaba9a811bd9dcd535d27e7d32e3fb631e0c8d6a1617f6f8689e  SKRoot-Pro-com.linux.compose-v4.5.4-release.apk
65e7cddd1dda874514653bea570413a646d80447647453aff9a7e1aba35ee1ba  SKRoot-Pro-com.linux.permissionmanager-v4.5.4-debug.apk
```

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

### v4.5.4-compose.4 Magica 生命周期修复

新日志确认 OPlus 输出与脚本输出的拼接顺序造成了错误判断；热启动现已恢复上游的“先执行 Magica 脚本、再配置 OPlus 拦截”顺序。Magica 隔离进程跳过 Compose Application 数据层初始化，修复脚本退出后后台子进程继承输出管道导致的无限等待，补充空绑定、绑定死亡、执行阶段和部分输出诊断，并将慢速设备的执行上限调整为 180 秒。`resetprop` 会校验复制结果并设置可执行权限，临时脚本使用独立文件名。
