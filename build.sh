#!/bin/bash
# CarConnect APK 构建脚本
# 需要预先安装 Android SDK 和 Gradle

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
echo "=============================="
echo "  CarConnect APK 构建脚本"
echo "=============================="
echo "项目目录: $PROJECT_DIR"

# 检查 Android SDK
if [ -z "$ANDROID_HOME" ]; then
    echo "⚠️  ANDROID_HOME 未设置"
    echo "请先安装 Android SDK 并设置 ANDROID_HOME 环境变量"
    echo "下载地址: https://developer.android.google.cn/studio"
    echo ""
    echo "设置示例:"
    echo "  export ANDROID_HOME=/Users/\$USER/Library/Android/sdk"
    echo "  export PATH=\$PATH:\$ANDROID_HOME/tools:\$ANDROID_HOME/platform-tools"
    exit 1
fi

echo "✅ Android SDK: $ANDROID_HOME"

# 检查 local.properties
if [ ! -f "$PROJECT_DIR/local.properties" ]; then
    echo "sdk.dir=$ANDROID_HOME" > "$PROJECT_DIR/local.properties"
    echo "✅ 已生成 local.properties"
fi

# 构建
echo ""
echo "开始构建 Debug APK..."
cd "$PROJECT_DIR"

if [ -f "./gradlew" ]; then
    chmod +x ./gradlew
    ./gradlew assembleDebug
else
    echo "❌ 未找到 gradlew，尝试生成..."
    gradle wrapper --gradle-version 7.3.3
    ./gradlew assembleDebug
fi

APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
    echo ""
    echo "=============================="
    echo "✅ 构建成功!"
    echo "APK 路径: $APK_PATH"
    echo "=============================="
    ls -lh "$APK_PATH"
else
    echo "❌ 构建失败，未找到 APK 文件"
    exit 1
fi
