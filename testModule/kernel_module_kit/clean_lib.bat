@echo off
cd /d "%~dp0"

if exist "src\obj" (
    rmdir /S /Q "src\obj"
)

if exist "lib" (
    rmdir /S /Q "lib"
)

if exist "include\skroot_env\parasite_app\web_assets\parasite_web_assets_bundle.generated.h" (
    del "include\skroot_env\parasite_app\web_assets\parasite_web_assets_bundle.generated.h"
)

if exist "src\jni\skroot_env\su\res.h" (
    del "src\jni\skroot_env\su\res.h"
)

if exist "src\jni\skroot_env\su\su_data.generated.h" (
    del "src\jni\skroot_env\su\su_data.generated.h"
)

if exist "src\jni\skroot_env\su\libs" (
    rmdir /S /Q "src\jni\skroot_env\su\libs"
)

if exist "src\jni\skroot_env\su\obj" (
    rmdir /S /Q "src\jni\skroot_env\su\obj"
)


if exist "src\jni\skroot_env\features\weui_loader\res.h" (
    del "src\jni\skroot_env\features\weui_loader\res.h"
)

if exist "src\jni\skroot_env\features\weui_loader\webui_loader_data.generated.h" (
    del "src\jni\skroot_env\features\weui_loader\webui_loader_data.generated.h"
)

if exist "src\jni\skroot_env\features\weui_loader\libs" (
    rmdir /S /Q "src\jni\skroot_env\features\weui_loader\libs"
)

if exist "src\jni\skroot_env\features\weui_loader\obj" (
    rmdir /S /Q "src\jni\skroot_env\features\weui_loader\obj"
)

if exist "src\jni\skroot_env\autorun_bootstrap\res.h" (
    del "src\jni\skroot_env\autorun_bootstrap\res.h"
)

if exist "src\jni\skroot_env\autorun_bootstrap\autorun_bootstrap_file_data.generated.h" (
    del "src\jni\skroot_env\autorun_bootstrap\autorun_bootstrap_file_data.generated.h"
)

if exist "src\jni\skroot_env\autorun_bootstrap\libs" (
    rmdir /S /Q "src\jni\skroot_env\autorun_bootstrap\libs"
)

if exist "src\jni\skroot_env\autorun_bootstrap\obj" (
    rmdir /S /Q "src\jni\skroot_env\autorun_bootstrap\obj"
)

if exist "src\jni\skroot_env\parasite_app\parasite_web_server\parasite_web_server_file_data.generated.h" (
    del "src\jni\skroot_env\parasite_app\parasite_web_server\parasite_web_server_file_data.generated.h"
)

if exist "src\jni\skroot_env\parasite_app\parasite_web_server\libs" (
    rmdir /S /Q "src\jni\skroot_env\parasite_app\parasite_web_server\libs"
)

if exist "src\jni\skroot_env\parasite_app\parasite_web_server\obj" (
    rmdir /S /Q "src\jni\skroot_env\parasite_app\parasite_web_server\obj"
) 

