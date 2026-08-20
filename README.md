# SKRoot Pro Compose

Kotlin + Jetpack Compose UI 重构版的 SKRoot Pro 权限管理器源码。

本仓库基于 [SKRoot-linuxKernelRoot](https://github.com/abcz316/SKRoot-linuxKernelRoot) 中的 Pro `PermissionManager` 工程整理，重点重做管理器的 UI、状态流和导航层，采用 Material 3 / Compose 实现 KSU 风格的管理界面。

> 本项目保留原 SKRoot Pro 的 Native/JNI、AIDL、Magica 服务、JSON 协议、应用 ID 和权限。4.6.0 使用上游 APK 中经过哈希校验的 ARM64 Native 文件；相关实现与资源归属原项目作者。

## 当前版本

- 上游核心版本：`4.6.0`
- 当前应用版本：`4.6.0.1`
- UI 修订号：`1`
- 最新公开版本：`v4.6.0.1`
- 默认 Application ID：`com.linux.permissionmanager`
- `minSdk`：26
- `targetSdk`：31
- ABI：`arm64-v8a`

## 主要内容

- Material 3 Compose 单 Activity 架构
- 默认启用可调透明度的液态玻璃手机底部导航，并可切换回 Material 3 原版导航栏
- 主页环境状态、Root 命令终端、系统状态卡片
- SU 授权管理与应用选择器
- 已安装模块、模块市场、更新和下载进度；支持将模块 WebUI 固定到桌面
- 设置、诊断、日志查看与导出
- Root Key、Boot / 热启动配置和重启选项
- 设备内本地定制管理器：自定义包名、名称和图标后直接导出或安装 APK
- `StateFlow` UI 状态、`SharedFlow` 一次性事件和 Repository 数据层
- 五套浅色配色、自定义背景，以及可折叠的背景图片、控件与栏位透明度设置
- 可选的管理器更新检测，默认关闭；更新来源为本仓库 GitHub Releases

## 版本与发布规则

应用版本使用 `<上游核心版本>.<UI 修订号>`，当前为 `4.6.0.1`。上游核心更新时同步前三段并将 UI 修订号重置为 `1`；本项目每次发布时递增最后一段。

新 Release 统一使用 Tag `v<应用版本>`，官方 APK 统一命名为：

```text
v<应用版本>-UI重构版-SKRoot Pro.apk
```

完整同步、更新渠道、签名与发布检查规则见 [`AGENTS.md`](AGENTS.md)。

## 构建环境

- JDK 17
- Gradle 8.9（使用仓库中的 Gradle Wrapper）
- Android Gradle Plugin 8.7.3
- Kotlin 2.1.0
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

正式版构建会直接生成：

```text
app/build/outputs/apk/release/v4.6.0.1-UI重构版-SKRoot Pro.apk
```

项目只配置 `arm64-v8a` Native ABI。安装和运行前，请确认设备、Root Key 与 SKRoot 环境配置符合上游项目要求。

### 模块 WebUI 桌面快捷方式

对带有 WebUI 的已安装模块，打开模块卡片右侧的 **更多 → 创建 WebUI 桌面快捷方式**，可在确认前自定义快捷方式名称与图标。界面会明确提示桌面入口和启动器记录可能降低管理器的隐藏性。

桌面 Intent 只保存随机不透明 ID，模块 ID 映射保存在管理器私有配置中。Root Key 正常时由不进入最近任务的轻量路由 Activity 直接启动 WebUI；Key 缺失或校验失败时才进入完整管理器，保存后继续原请求。旧版本已创建的明文映射快捷方式需删除后重新创建。

### 设备内本地定制

在应用的 **设置 → 管理器 → 本地定制管理器** 中，可直接以当前安装的单 APK 为模板，在设备内修改包名、管理器名称与五档密度启动图标。构建过程不连接 GitHub Actions 或其他服务；生成包会删除模板签名，使用设备内首次创建并持久复用的本地身份进行 APK v2/v3 签名，再校验 Manifest、应用名称、签名、图标和四项 ARM64 Native 库。

同一安装保留应用数据时，本地身份会持续复用，因此相同定制包名可直接更新。若设备上已有同包名但签名不同的应用，界面会提示签名冲突；仍可仅导出 APK，安装前需更换包名或卸载冲突应用。

最新公开 Release [v4.6.0.1](https://github.com/dreamcolor123/SKRoot-Pro-Compose/releases/tag/v4.6.0.1) 提供签名正式版 APK：

- [`v4.6.0.1-UI重构版-SKRoot Pro.apk` / `com.linux.compose`](https://github.com/dreamcolor123/SKRoot-Pro-Compose/releases/download/v4.6.0.1/v4.6.0.1-UI.-SKRoot.Pro.apk)

SHA-256：

```text
24a3d5df486549cd26d5d0a57d75ff33ba8b77f4c573d5965976b21212570752  v4.6.0.1-UI重构版-SKRoot Pro.apk
```

## 上游项目与致谢

- 上游项目：[abcz316/SKRoot-linuxKernelRoot](https://github.com/abcz316/SKRoot-linuxKernelRoot)
- UI 视觉参考：[tiann/KernelSU](https://github.com/tiann/KernelSU)

本仓库不复制 KernelSU 的页面源码或资源，仅参考其 Material 管理器视觉理念并进行独立 Compose 实现。

## 变更范围

本版本主要包含 UI、状态管理、导航、主题和构建迁移。Native、JNI、AIDL、Magica 服务和现有业务协议跟随上游语义；4.6.0 的四项 ARM64 Native 文件直接同步自上游 APK并进行 SHA-256 构建校验。

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

工作流会执行资源图标生成、Release 签名、`aapt` 包名/名称/版本校验、APK v2 签名校验，并在摘要中显示 APK 路径和 SHA-256。图标未填写时沿用仓库默认图标。构建文件统一命名为 `v<应用版本>-UI重构版-<应用名称>.apk`，不再用自定义 Release Tag 充当 APK 版本号。

### v4.6.0.1 上游核心适配

- 同步上游 `master` 的 SKRoot Pro 4.6.0 官方 APK 中更新后的 `libpermissionmanager.so`。
- 同步 4.6.0 对应的 `kernel_module_kit_static.a`，供仓库内测试模块编译使用。
- 保持三参数环境安装 JNI 协议以及现有 Boot / HotLoad 与漏洞策略传参不变。
- 保留 CVE-2026-43499 即时环境安装完成后的延迟状态刷新，避免读取尚未稳定的系统状态。
- 上游 4.6.0 更新说明：修复 CVE-2026-43499 漏洞痕迹被侦测问题、部分天玑设备应用闪退问题及细节 Bug。

### v4.5.9.1 上游核心适配

- 同步上游 SKRoot Pro 4.5.9 官方 APK 中更新后的 `libpermissionmanager.so`。
- 同步 4.5.9 对应的 `kernel_module_kit_static.a`，供仓库内测试模块编译使用。
- 保持三参数环境安装 JNI 协议以及现有 Boot / HotLoad 与漏洞策略传参不变。
- 同步 CVE-2026-43499 即时环境安装后的延迟状态刷新，避免读取尚未稳定的系统状态。
- 日志导出继续在写入前统一检查存储访问权限，覆盖上游本轮管理器修复。
- 上游更新说明：修复 CVE-2026-43499 漏洞痕迹被侦测问题，以及天玑设备打开部分应用闪退问题。

### v4.5.8.1 上游核心适配

- 同步上游 SKRoot Pro 4.5.8 Native SDK 与更新后的 `libpermissionmanager.so`。
- 安装环境 JNI 适配新的三参数协议：`Boot/HotLoad` 模式与 `MAGICA/CVE-2026-43499` 漏洞策略分离传递。
- 保留 CVE 热启动引导、可选软重启、脚本生命周期修复和模块市场更新回退逻辑。
- 四项上游 Native 文件继续执行固定 SHA-256 构建校验。
- 上游 4.5.8 更新说明：延续 CVE-2026-43499 支持并修复细节问题。

### v4.5.6.1 上游核心适配

- 同步上游 SKRoot Pro 4.5.6 Native SDK，新增 CVE-2026-43499 Ghostlock ARM64 组件。
- 安装环境 JNI 适配上游的 `Boot`、`HotLoad` 模式与独立漏洞策略参数。
- 支持从 `1.h` 识别 `METHOD=CVE-2026-43499`，执行上游 Ghostlock 引导后继续原热启动脚本。
- CVE 热启动环境即时安装完成后提供可选软重启提示，并兼容上游新的热启动模式设置键。
- 未配置独立 `update_json` 的已安装模块，可按模块 ID 使用模块市场数据检查更新。
- 四项上游 Native 文件加入固定 SHA-256 构建校验，本地定制器与 GitHub Actions 同步验证新组件。
- 已与上游 `master` 的 4.5.6 管理器源码、JNI 包装和静态 SDK 交叉核对；APK 继续打包官方发布包中的原始 Native 文件。
- 上游更新说明：新增 CVE-2026-43499 对部分高通、天玑设备及 Linux 6.6/6.12 内核的支持，并修复细节问题。

### v4.5.4.13 环境安装兼容修复

- 恢复与上游原版一致的环境安装、卸载 JNI 调用线程，修复部分设备写入 SKP 环境失败的问题。
- 安装操作固定使用确认时的 Root Key 与 Boot/热启动模式快照，避免状态同步时序导致参数过期。
- 安装确认弹窗显示当前模式，终端保留模式、Key 长度及完整 Native 返回值，便于异常排查。
- Root Key 为空或 Native 返回密钥错误时自动打开配置窗口。

### v4.5.4.12 WebUI 快捷方式兼容修复

- 修复从桌面快捷方式打开隐蔽终端等 WebUI 模块时显示“异常”的问题。
- 避免管理器与 Native Loader 重复打开同一 WebUI 地址；浏览器跳转继续由上游 Native Loader 负责。
- 快捷方式轻量路由在 WebUI 使用期间维持 Loader 父进程，且仍不会出现在最近任务中。
- WebUI 本地端口停止后自动清理隐藏路由任务，普通模块页面的 WebUI 按钮同步使用相同的单次启动流程。

### v4.5.4.11 快捷方式隐私与定制输入修复

- WebUI 快捷方式支持自定义名称与图标，创建前显示隐藏性提示。
- 桌面 Intent 仅保存随机不透明 ID，模块 ID 映射留在管理器私有配置中。
- Root Key 正常时由不进入最近任务的路由 Activity 直接打开 WebUI；Key 异常时才进入完整管理器。
- 修复定制管理器包名粘贴的样式、组合文本和换行符显示问题，默认包名调整为 `com.example.pro`。
- 首页安装、更新、重新安装与卸载环境均增加二次确认。

### v4.5.4.10 模块 WebUI 快捷方式

带 WebUI 的已安装模块现可从“更多”菜单创建桌面快捷方式；点击后会冷启动或复用管理器并打开对应模块 WebUI。未配置 Root Key 时会先打开配置窗口，保存后继续原请求。同时修复了自定义背景图的多层表面叠加与全局白色遮罩，100% 可见度时背景不再被额外漂白。

### v4.5.4-compose.9 液态玻璃导航栏

手机布局新增默认启用的液态玻璃浮动导航栏，支持在设置中切换 Material 3 原版导航栏，并可独立调节玻璃透明度。玻璃背景使用动态高斯模糊与全分辨率采样，高透明度下会轻微降低模糊强度；滚动细节不再叠加噪点纹理。原版导航栏继续保持不透明，平板 NavigationRail 行为不变。

### v4.5.4-compose.4 Magica 生命周期修复

新日志确认 OPlus 输出与脚本输出的拼接顺序造成了错误判断；热启动现已恢复上游的“先执行 Magica 脚本、再配置 OPlus 拦截”顺序。Magica 隔离进程跳过 Compose Application 数据层初始化，修复脚本退出后后台子进程继承输出管道导致的无限等待，补充空绑定、绑定死亡、执行阶段和部分输出诊断，并将慢速设备的执行上限调整为 180 秒。`resetprop` 会校验复制结果并设置可执行权限，临时脚本使用独立文件名。
