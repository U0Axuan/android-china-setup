#!/usr/bin/env python3
"""Android China Setup V0.5 emulator UI smoke checks (API 30 and 35).

Offline catalog & settings are exercised end-to-end; external APK download
is optional since public GitHub network can be throttled on cloud runners.
"""
import json
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

OUT = Path("test-results/emulator")
OUT.mkdir(parents=True, exist_ok=True)
PACKAGE = "io.github.u0axuan.androidchinasetup"
APK = "app/build/outputs/apk/debug/app-debug.apk"
checks = []

def adb(*args, timeout=50, check=True):
    return subprocess.run(["adb", *args], text=True, capture_output=True,
                          timeout=timeout, check=check)

def assert_check(name, condition, info=""):
    checks.append({"name": name, "passed": bool(condition), "note": str(info)[:400]})
    print(("PASS" if condition else "FAIL") + ": " + name + " " + str(info)[:140], flush=True)
    if not condition:
        raise AssertionError(name + " " + str(info))

def shot(name):
    try:
        data = subprocess.run(["adb","exec-out","screencap","-p"],
                              capture_output=True, timeout=35, check=True).stdout
        (OUT / (name+".png")).write_bytes(data)
    except Exception as e:
        print("No screenshot:", e, flush=True)

def dump(label="window"):
    for i in range(7):
        try:
            adb("shell","uiautomator","dump","/sdcard/window.xml",timeout=50)
            xml = adb("shell","cat","/sdcard/window.xml").stdout
            root = ET.fromstring(xml)
            texts = [n.attrib.get("text","") for n in root.iter("node")]
            if any("Pixel Launcher isn't responding" in t for t in texts):
                button = next((n for n in root.iter("node")
                    if n.attrib.get("text") == "Wait"), None)
                if button is not None:
                    tap(button)
                    print("RECOVER: Pixel Launcher CI ANR dialog",flush=True)
                    continue
            (OUT/(label+".xml")).write_text(xml,encoding="utf-8")
            return root
        except Exception:
            time.sleep(1)
    raise RuntimeError("Cannot read UI hierarchy")

def matches(root, text, exact=False):
    return [n for n in root.iter("node")
            if any((v == text if exact else text in v) for v in
            (n.attrib.get("text",""),n.attrib.get("content-desc","")))]

def bounds(node):
    return [int(x) for x in re.findall(r"\d+", node.attrib.get("bounds",""))]

def tap(node):
    b=bounds(node)
    if len(b) == 4:
        adb("shell","input","tap",str((b[0]+b[2])//2),str((b[1]+b[3])//2))
        time.sleep(1.0)

def swipe(down=True):
    adb("shell","input","swipe","520",str(1670 if down else 650),
        "520",str(650 if down else 1670),"230")
    time.sleep(0.4)

def find(label, exact=False, attempts=10):
    for direction in (True,False):
        for i in range(attempts):
            root=dump("find_"+str(i))
            found=matches(root,label,exact)
            if found:
                return found[0]
            swipe(direction)
    raise AssertionError("Unable to find UI element: "+label)

def click(label,exact=False):
    tap(find(label,exact))

def pref():
    return adb("shell","run-as",PACKAGE,"cat",
               "shared_prefs/preferences.xml",check=False).stdout

try:
    api=int(adb("shell","getprop","ro.build.version.sdk").stdout.strip())
    assert_check("Expected emulator SDK",api==int(os.environ.get("EXPECTED_API",api)),api)
    assert_check("Android 11 minimum",api>=30)
    installed=adb("install","-r",APK,timeout=110)
    assert_check("APK installed","Success" in installed.stdout,installed.stdout)
    adb("logcat","-c")
    adb("shell","am","start","-n",PACKAGE+"/.MainActivity")
    root=None
    for i in range(7):
        time.sleep(2)
        root=dump("start_"+str(i))
        if matches(root,"Android China Setup"):
            break
        adb("shell","am","start","-n",PACKAGE+"/.MainActivity")
    assert_check("App launched",bool(matches(root,"Android China Setup")))
    assert_check("Category index visible",bool(matches(root,"精选 Android 开源应用")))
    assert_check("Catalog item count visible",bool(matches(root,"15 个项目")))
    assert_check("Settings entry in top bar",bool(matches(root,"设置",True)))
    assert_check("No bottom navigation",not matches(root,"测速",True) and
        not matches(root,"关于",True))
    assert_check("No app checkbox",not any(n.attrib.get("class")==
        "android.widget.CheckBox" for n in root.iter("node")))
    shot("01_category_home")

    # Browse first detail: compact readme snapshot and canonical project links.
    click("曦码输入法")
    root=dump("02_readme_sheet")
    assert_check("README snapshot displayed",bool(matches(root,"README 快照")))
    assert_check("README snapshot is clearly offline",bool(matches(root,"内置摘要")))
    assert_check("Original README link",bool(matches(root,"阅读原项目 README 全文")))
    assert_check("Original project link",bool(matches(root,"访问原项目",True)))
    shot("02_readme_snapshot")
    adb("shell","input","keyevent","4")
    time.sleep(2)

    # Single LazyColumn with grouped sections and all 15 entries.
    for category in ["日常与效率","游戏与影音","网络与连接",
                     "应用与文件","系统与调试","高级与 Root"]:
        find(category)
        assert_check("Category "+category,True)
    for name in ["osu! (lazer)","DroidDeck","DirtyFrag","KSuRoot","LocalSend"]:
        find(name)
        assert_check("Catalog "+name,True)
    shot("03_catalog_bottom")
    click("KSuRoot")
    root=dump("03_high_risk_sheet")
    assert_check("High risk root entry is informational",
        bool(matches(root,"高风险系统工具仅展示原项目")))
    assert_check("No exploit download action in risk sheet",
        not matches(root,"下载官方 APK"))
    shot("04_high_risk_readme")
    adb("shell","input","keyevent","4")
    time.sleep(2)

    # Capture frame performance as evidence, but emulator software GPU is noisy.
    adb("shell","dumpsys","gfxinfo",PACKAGE,"reset",check=False)
    for i in range(6): swipe(False)
    for i in range(6): swipe(True)
    frames=adb("shell","dumpsys","gfxinfo",PACKAGE,check=False).stdout
    (OUT/"frame_stats.txt").write_text(frames[-160000:],encoding="utf-8")
    assert_check("Frame stats captured",bool(frames.strip()))
    shot("05_scroll_performance")

    # The only settings entry is in the top-right bar.
    click("设置",True)
    root=dump("06_settings")
    assert_check("Settings opened",bool(matches(root,"下载加速与测速")))
    assert_check("Speed test integrated",bool(matches(root,"开始测速")))
    assert_check("Auto mirror selector",bool(matches(root,"智能优选")))
    shot("06_settings_speed")
    for name in ["GHFast","GH-Proxy","LLKK","GHProxy.net"]:
        find(name)
        assert_check("Mirror "+name,True)
    click("GHFast")
    assert_check("Mirror choice saved","ghfast" in pref())

    click("源名称")
    adb("shell","input","text","MyMirror")
    adb("shell","input","keyevent","4")
    click("HTTPS 镜像前缀")
    adb("shell","input","text","https://mirror.example.com")
    adb("shell","input","keyevent","4")
    click("添加加速源")
    assert_check("Custom mirror persisted","mirror.example.com" in pref())
    shot("07_custom_source")

    # The speed action must be visible and respond; external connectivity
    # is not treated as proof that all community mirrors are available.
    click("开始测速")
    root=dump("08_speed_started")
    assert_check("Speed action responds",bool(matches(root,"测速")))
    time.sleep(10)
    shot("08_speed_results")

    # Proxy and About are sections of the same Settings page.
    field=find("自定义代理（默认关闭）")
    row_y=(bounds(field)[1]+bounds(field)[3])//2
    root=dump("09_proxy_toggle")
    toggles=[n for n in root.iter("node")
        if n.attrib.get("checkable")=="true" and
           n.attrib.get("clickable")=="true"]
    assert_check("Proxy toggle exists",bool(toggles))
    toggle=min(toggles,key=lambda n:abs((bounds(n)[1]+bounds(n)[3])//2-row_y))
    tap(toggle)
    assert_check("Proxy details shown",bool(matches(dump("09_proxy_on"),"代理主机/IP")))
    click("SOCKS5")
    assert_check("SOCKS5 persisted","SOCKS5" in pref())
    shot("09_proxy_settings")

    find("关于与自更新")
    assert_check("About and update integrated",True)
    find("检查更新")
    assert_check("Manual update control",True)
    find("访问原项目（GitHub）")
    assert_check("Source link in settings",True)
    shot("10_settings_about")

    click("返回应用列表")
    assert_check("Back returns to categories",bool(matches(dump("11_back"),"精选 Android 开源应用")))
    adb("shell","am","force-stop",PACKAGE)
    adb("shell","am","start","-n",PACKAGE+"/.MainActivity")
    time.sleep(3)
    assert_check("Relaunch after preferences",bool(matches(dump("12_restart"),"Android China Setup")))
    assert_check("Custom source survives restart","mirror.example.com" in pref())
    shot("12_relaunch")

except Exception as exc:
    checks.append({"name":"blocking_error","passed":False,"note":repr(exc)})
    print("FAIL: blocking_error",repr(exc),flush=True)
finally:
    try:
        logs=adb("logcat","-d","-v","time",timeout=40,check=False).stdout
        (OUT/"logcat.txt").write_text(logs[-500000:],encoding="utf-8",errors="replace")
    except Exception: pass
    (OUT/"results.json").write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding="utf-8")
    print("Summary:",sum(1 for x in checks if x["passed"]),
          "passed,",sum(1 for x in checks if not x["passed"]),"failed",flush=True)

if not all(x["passed"] for x in checks):
    raise SystemExit(1)
