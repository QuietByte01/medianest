#!/bin/bash
APP_ID="com.medianest.app"
echo "Filtering logs for $APP_ID..."
adb logcat --pid="$(adb shell pidof -s $APP_ID)"
