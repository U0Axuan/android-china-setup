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

try:
    sdk=int(adb("shell","getprop","ro.build.version.sdk").stdout.strip())
    check("API matches emulator",sdk==int(os.environ.get("EXPECTED_API",sdk)),str(sdk))
    res=adb("install","-r",APK,timeout=150)
    check("Signed APK installed","Success" in res.stdout,res.stdout.strip())
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
    check("15 catalog projects shown",bool(elements(root,"15 个项目")))
    check("Settings accessible from top right",bool(elements(root,"设置")))
    check("No old bottom navigation",not elements(root,"测速") and
          not elements(root,"关于"))
    screenshot("01_release_home")
    # Software-rendered CI emulators occasionally lose taps to a transient
    # system overlay. Retry only while the catalog is still visible; never
    # report settings as passed without seeing real settings controls.
    for attempt in range(6):
        root=dump("settings-attempt-"+str(attempt))
        if elements(root,"开始测速") and elements(root,"智能优选"):
            break
        setting=elements(root,"设置")
        if not setting:
            time.sleep(2)
            continue
        tap(setting[-1])
        time.sleep(2)
    root=dump("02_release_settings")
    check("Settings includes benchmark",bool(elements(root,"开始测速")))
    check("Settings includes mirrors",bool(elements(root,"智能优选")))
    screenshot("02_release_settings")
    adb("shell","input","keyevent","4")
    time.sleep(2)
    root=dump("03_back_home")
    check("Back returns to catalog",bool(elements(root,"精选 Android 开源应用")))
    pid=adb("shell","pidof",PACKAGE,check=False).stdout.strip()
    check("App process remains alive",bool(pid))
    screenshot("03_release_home_back")
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
