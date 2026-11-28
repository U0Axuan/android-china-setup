#!/usr/bin/env python3
"""Black-box installation smoke checks for a NON-DEBUGGABLE signed release APK.

Does not use run-as or read private preferences. Intended for x86_64 Android
11 and Android 15 CI emulators. Network access not required for core UI.
"""
import json
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "io.github.u0axuan.androidchinasetup"
APK = "dist/Android-China-Setup.apk"
RESULTS = Path("test-results/signed-release")
RESULTS.mkdir(parents=True, exist_ok=True)
checks = []

def adb(*args, check=True, timeout=50):
    return subprocess.run(["adb", *args], check=check, timeout=timeout,
                          capture_output=True, text=True)

def check(name, result, detail=""):
    checks.append({"name":name, "passed":bool(result), "detail":detail})
    print(("PASS: " if result else "FAIL: ") + name + " " + detail, flush=True)
    if not result:
        raise AssertionError(name + ": " + detail)

def elements(root, label):
    return [n for n in root.iter("node") if
            label in n.attrib.get("text", "") or
            label in n.attrib.get("content-desc", "")]

def bounds(n):
    return list(map(int, re.findall(r"\d+", n.attrib.get("bounds", ""))))

def tap(n):
    b = bounds(n)
    if len(b) == 4:
        adb("shell", "input", "tap", str((b[0]+b[2])//2), str((b[1]+b[3])//2))
        time.sleep(1)

def screenshot(name):
    try:
        data = subprocess.run(["adb","exec-out","screencap","-p"],
                              check=True, capture_output=True, timeout=40).stdout
        (RESULTS/(name+".png")).write_bytes(data)
    except Exception as e:
        print("Screenshot failed: "+str(e), flush=True)

def dump(name):
    for i in range(8):
        adb("shell","uiautomator","dump","/sdcard/ui.xml",check=False)
        xml=adb("shell","cat","/sdcard/ui.xml",check=False).stdout
        try:
            root=ET.fromstring(xml)
        except Exception:
            time.sleep(1)
            continue
        if elements(root, "Pixel Launcher isn't responding"):
            wait=elements(root,"Wait")
            if wait:
                tap(wait[0])
                continue
        (RESULTS/(name+".xml")).write_text(xml,encoding="utf-8")
        return root
    raise RuntimeError("Unable to dump release UI")

def find(label, attempts=16):
    for i in range(attempts):
        root = dump("find_" + label + "_" + str(i))
        found = elements(root, label)
        if found:
            return found[0]
        # Slow, overlapping swipes keep every row on screen for at least one dump.
        adb("shell", "input", "swipe", "520", "1450", "520", "850", "600")
    raise AssertionError("Cannot find " + label)

try:
    sdk=int(adb("shell","getprop","ro.build.version.sdk").stdout.strip())
    check("API matches emulator",sdk==int(os.environ.get("EXPECTED_API",sdk)),str(sdk))
    previous = Path("dist/previous.apk")
    if previous.exists():
        res = adb("install", "-r", str(previous), timeout=150)
        check("Previous release installs", "Success" in res.stdout)
        adb("shell", "am", "start", "-n", PACKAGE+"/.MainActivity")
        time.sleep(3)
        adb("shell", "am", "force-stop", PACKAGE)
    res=adb("install","-r",APK,timeout=150)
    check("Signed APK installed","Success" in res.stdout,res.stdout.strip())
    if previous.exists():
        package = adb("shell", "dumpsys", "package", PACKAGE).stdout
        expected = os.environ.get("EXPECTED_VERSION", "0.7.2")
        check("Upgrade installs " + expected, "versionName=" + expected in package)
    adb("logcat","-c")
    adb("shell","am","start","-n",PACKAGE+"/.MainActivity")
    root=None
    for i in range(10):
        time.sleep(2)
        root=dump("start-"+str(i))
        if elements(root,"精选 Android 开源应用"):
            break
        adb("shell","am","start","-n",PACKAGE+"/.MainActivity",check=False)
    check("Non-debuggable signed app launches",root is not None and
          bool(elements(root,"精选 Android 开源应用")))
    counts = {len(json.loads(Path(p).read_text())) for p in ("catalog/apps.json", "app/src/main/assets/apps.json")}
    check("Catalog count shown", any(elements(root, f"{n} 个项目") for n in counts))
    check("Settings accessible from top right",bool(elements(root,"设置")))
    check("No old bottom navigation",not elements(root,"测速") and
          not elements(root,"关于"))
    screenshot("01_release_home")
    # Software-rendered CI emulators occasionally lose taps to a transient
    # system overlay. Retry only while the catalog is still visible; never
    # report settings as passed without seeing real settings controls.
    for attempt in range(6):
        root=dump("settings-attempt-"+str(attempt))
        if elements(root,"刷新应用目录") and elements(root,"进入下载与测速"):
            break
        setting=elements(root,"设置")
        if not setting:
            time.sleep(2)
            continue
        tap(setting[-1])
        time.sleep(2)
    root=dump("02_release_settings")
    check("Settings shows second-level download controls",bool(elements(root,"进入下载与测速")))
    check("Settings includes online catalog refresh",bool(elements(root,"刷新应用目录")))
    check("GitHub recommendation entry present",bool(elements(root,"提交应用推荐")))
    screenshot("02_release_settings")
    for attempt in range(7):
        root=dump("02_download_submenu_attempt_"+str(attempt))
        if elements(root,"开始测速") and elements(root,"智能优选"):
            break
        menu=elements(root,"进入下载与测速")
        if menu:
            tap(menu[0])
        else:
            time.sleep(1)
    root=dump("02_download_submenu")
    check("Benchmark appears in secondary menu",bool(elements(root,"开始测速")))
    check("Mirrors appear in secondary menu",bool(elements(root,"智能优选")))
    screenshot("02_download_submenu")
    for attempt in range(5):
        root=dump("02_back_to_settings_"+str(attempt))
        if elements(root,"刷新应用目录"): break
        back_menu=elements(root,"返回设置")
        if back_menu: tap(back_menu[0])
        else: adb("shell","input","keyevent","4")

    # Prefer the visible Material 3 back control. Software-rendered Android
    # emulators can lose a single key event during system animations.
    for attempt in range(6):
        root=dump("back-attempt-"+str(attempt))
        if elements(root,"精选 Android 开源应用"):
            break
        back=elements(root,"返回应用列表")
        if back:
            tap(back[-1])
        else:
            adb("shell","input","keyevent","4")
        time.sleep(2)
    root=dump("03_back_home")
    check("Back returns to catalog",bool(elements(root,"精选 Android 开源应用")))
    pid=adb("shell","pidof",PACKAGE,check=False).stdout.strip()
    check("App process remains alive",bool(pid))
    screenshot("03_release_home_back")

    # Exercise the real release downloader, including its APK parsing and
    # official checksum validation. Do not install the third-party application.
    tap(find("设置"))
    tap(find("进入下载与测速"))
    tap(find("GitHub 官方直连"))
    tap(find("返回设置"))
    tap(find("返回应用列表"))
    tap(find("Material Files"))
    # A second tap at the same position must not schedule a second download.
    button = bounds(find("下载官方 APK"))
    x, y = str((button[0]+button[2])//2), str((button[1]+button[3])//2)
    adb("shell", "settings", "put", "system", "accelerometer_rotation", "0")
    adb("shell", "input", "tap", x, y)
    adb("shell", "input", "tap", x, y)
    adb("shell", "settings", "put", "system", "user_rotation", "1")
    time.sleep(2)
    adb("shell", "settings", "put", "system", "user_rotation", "0")
    time.sleep(2)
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    adb("shell", "am", "start", "-n", PACKAGE+"/.MainActivity")

    # The status card is above the scrolled catalog; return to its top.
    for _ in range(12):
        adb("shell", "input", "swipe", "520", "650", "520", "1670", "180")
    ready = False
    for attempt in range(30):
        root = dump("download_" + str(attempt))
        if elements(root, "安装已下载 APK（系统确认）"):
            ready = True
            break
        if elements(root, "下载失败"):
            break
        time.sleep(2)
    check("Official APK downloaded and validated in the signed app", ready,
          "Material Files; no third-party app installed")
    screenshot("04_verified_download")
except Exception as error:
    checks.append({"name":"blocking_error","passed":False,"detail":repr(error)})
    print("FAIL: blocking_error "+repr(error),flush=True)
finally:
    (RESULTS/"results.json").write_text(
        json.dumps(checks,ensure_ascii=False,indent=2),encoding="utf-8")
    try:
        logs=adb("logcat","-d","-v","time",check=False).stdout
        (RESULTS/"logcat.txt").write_text(logs[-450000:],encoding="utf-8")
    except Exception:
        pass
if not all(x["passed"] for x in checks):
    raise SystemExit(1)
