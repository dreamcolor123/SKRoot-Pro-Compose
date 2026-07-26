APP_ABI        := arm64-v8a
APP_PLATFORM   := android-23

APP_LDFLAGS    := -Wl,-exclude-libs,ALL -Wl,--gc-sections -Wl,--strip-all -Wl,--icf=all -flto
APP_CFLAGS     := -Wall -Wextra -Werror -fvisibility=hidden -fvisibility-inlines-hidden -flto
APP_CONLYFLAGS := -std=c20
APP_CPPFLAGS   := -std=c++20

APP_STL        := c++_shared