#!/bin/bash
# 校园助手-FAFUer专用 一键打包脚本
# 产物：R8 混淆 + 资源收缩 + arm64 拆分的 release 包（未配 keystore.properties 时为 debug 密钥签名，可直接安装）
#
# 工具链定位顺序（先命中先用）：
#   1) 环境变量 TOOLS 指向的目录（旧构建机布局：jdk / sdk / gradle-home / gradle-9.8.0）
#   2) 仓库同级的 .toolchain 目录（本机便携布局，见 .toolchain/env.sh）
#   3) 系统已配置的 JAVA_HOME / ANDROID_HOME + PATH 中的 gradle
set -e

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GRADLE_TASK="${GRADLE_TASK:-:app:assembleRelease}"

if [ -n "$TOOLS" ] && [ -x "$TOOLS/gradle-9.8.0/bin/gradle" ]; then
  # --- 1) 旧构建机布局 ---
  export JAVA_HOME="$TOOLS/jdk"
  export ANDROID_HOME="$TOOLS/sdk"
  export GRADLE_USER_HOME="$TOOLS/gradle-home"
  GRADLE_BIN="$TOOLS/gradle-9.8.0/bin/gradle"
elif [ -x "$REPO_DIR/../.toolchain/gradle/gradle-9.8.0/bin/gradle" ]; then
  # --- 2) 本机便携布局 ---
  TOOLCHAIN="$(cd "$REPO_DIR/../.toolchain" && pwd)"
  export JAVA_HOME="$TOOLCHAIN/jdk/jdk-21.0.12.1+1"
  export ANDROID_HOME="$TOOLCHAIN/sdk"
  export GRADLE_USER_HOME="$TOOLCHAIN/gradle-home"
  GRADLE_BIN="$TOOLCHAIN/gradle/gradle-9.8.0/bin/gradle"
else
  # --- 3) 系统环境 ---
  GRADLE_BIN="gradle"
fi

cd "$REPO_DIR"
"$GRADLE_BIN" "$GRADLE_TASK" --console=plain "$@"

echo "== APK =="
ls -lh app/build/outputs/apk/release/*.apk 2>/dev/null || ls -lh app/build/outputs/apk/*/*.apk
