#!/bin/bash
# 校园助手-FAFUer专用 一键打包脚本（JDK21 + Android SDK37 + Gradle 9.8 已内置）
# 产物：R8 混淆 + 资源收缩 + arm64 拆分的 release 包（debug 密钥签名，可直接安装）
set -e
TOOLS=/home/work/.openclaw/workspace/.openclaw/tmp/tools
export JAVA_HOME=$TOOLS/jdk
export ANDROID_HOME=$TOOLS/sdk
export GRADLE_USER_HOME=$TOOLS/gradle-home
cd /home/work/.openclaw/workspace/CampusGlass
$TOOLS/gradle-9.8.0/bin/gradle :app:assembleRelease --console=plain "$@"
echo "== APK =="
ls -lh app/build/outputs/apk/release/*.apk 2>/dev/null || ls -lh app/build/outputs/apk/*/*.apk
