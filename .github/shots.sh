#!/bin/bash
# گرفتن اسکرین‌شات واقعی از برنامه روی شبیه‌ساز (برای کافه‌بازار)
# نسخه ۱.۰ — آشپزی هوشمند
set -x
PKG=ir.aminim.smartcooking
APK=$(find apk -name "*debug*.apk" | head -1)
adb install -r "$APK"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb shell settings put global window_animation_scale 0.5
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1241
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
mkdir -p shots
adb logcat -c
shot() { adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1; sleep 1; adb exec-out screencap -p > "shots/$1.png"; }
open() { adb shell am force-stop $PKG; sleep 1; adb shell am start -n $PKG/.MainActivity --es demo "$1"; sleep "${2:-10}"; }
up() { adb shell input swipe 540 1900 540 ${1:-600} 600; sleep 3; }

open none 25; open none 12; shot 01_home
up 500; shot 02_home_today
up 400; shot 03_home_tools
open drawer; shot 04_drawer
open pantry; shot 05_pantry
up 400; shot 06_pantry_quick
up 400; shot 07_pantry_list
open suggest; shot 08_suggest
up 600; shot 09_suggest_cards
up 600; shot 10_suggest_more
open recipe; shot 11_recipe
up 500; shot 12_recipe_ings
up 600; shot 13_recipe_steps
open recipe2; shot 14_recipe_mirza
open cook; shot 15_cook
adb shell input tap 700 2200; sleep 3; shot 16_cook_step
adb shell input tap 700 2200; sleep 3; shot 17_cook_step2
open chat; shot 18_chat
open library; shot 19_library
open wheel; shot 20_wheel
open planner; shot 21_planner
open shopping; shot 22_shopping
open favorites; shot 23_favorites
open tools; shot 24_tools
open guides; shot 25_guides
open settings; shot 26_settings
open home_light; shot 27_home_light
open suggest_light; shot 28_suggest_light
open about; shot 29_about
open onboard; shot 30_onboard
adb logcat -d -s AndroidRuntime:E > shots/crash_log.txt || true
ls -la shots
