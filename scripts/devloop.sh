#!/bin/bash
# MioLauncher 一键构建 + 安装 + 启动 + 截图
set -e
export ANDROID_HOME=$HOME/android-sdk
export PATH=/opt/gradle-8.7/bin:$PATH
cd ~/MioLauncher

DEV="192.168.10.70:35271"
APK=app/build/outputs/apk/debug/app-debug.apk
PKG=com.miolauncher.app

echo "==> [1/4] 构建 APK"
/opt/gradle-8.7/bin/gradle :app:assembleDebug 2>&1 | tail -2

echo "==> [2/4] 安装到手机"
adb -s $DEV push $APK /data/local/tmp/mio.apk >/dev/null 2>&1
adb -s $DEV shell pm install -r -d /data/local/tmp/mio.apk

echo "==> [3/4] 启动应用"
adb -s $DEV shell am force-stop $PKG
adb -s $DEV shell am start -n $PKG/.MainActivity

echo "==> [4/4] 截图"
sleep 3
adb -s $DEV exec-out screencap -p > /tmp/opencode/mio_screen.png
echo "完成！截图已保存到 /tmp/opencode/mio_screen.png"
