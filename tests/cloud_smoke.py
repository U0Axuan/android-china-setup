#!/usr/bin/env python3
"""Android 11/15 real emulator UI smoke checks for the redesigned v0.4 app.

The live download/install test is opt-in (RUN_NETWORK_E2E=1), because real
GitHub traffic can be blocked or throttled on shared CI emulator hosts.
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
results = []

def adb(*args, check=True, timeout=50):
    return subprocess.run(["adb", *args], text=True, capture_output=True,
                          timeout=timeout, check=check)

def record(name, ok=True, note=""):
    results.append({"name": name, "passed": bool(ok), "note": str(note)})
    print(("PASS: " if ok else "FAIL: ") + name + " " + str(note)[:180], flush=True)
    if not ok:
        raise AssertionError(name + " / " + str(note))

def screenshot(name):
    try:
        output = subprocess.run(["adb", "exec-out", "screencap", "-p"],
                                capture_output=True, check=True, timeout=35)
        (OUT / (name + ".png")).write_bytes(output.stdout)
    except Exception as e:
        print("screenshot:", e, flush=True)

def dump(name="ui"):
    for attempt in range(3):
        try:
            adb("shell", "uiautomator", "dump", "/sdcard/window.xml", timeout=50)
            xml = adb("shell", "cat", "/sdcard/window.xml").stdout
            (OUT / (name + ".xml")).write_text(xml, encoding="utf-8")
            return ET.fromstring(xml)
        except Exception:
            time.sleep(1.5)
    raise RuntimeError("Unable to dump UI")

def nodes(root, pattern, exact=False):
    found = []
    for node in root.iter("node"):
        vals = (node.attrib.get("text", ""), node.attrib.get("content-desc", ""))
        if any(v == pattern if exact else pattern in v for v in vals):
            found.append(node)
    return found

def bounds(n):
    return [int(x) for x in re.findall(r"\d+", n.attrib.get("bounds", ""))]

def tap(n):
    x1,y1,x2,y2 = bounds(n)
    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(1.2)

def swipe(down=True):
    y1,y2 = (1750,850) if down else (850,1750)
    adb("shell", "input", "swipe", "520", str(y1), "520", str(y2), "250")
    time.sleep(0.5)

def find(text, exact=False, attempts=10):
    for direction in (True,False):
        for i in range(attempts):
            root = dump("find_" + str(i))
            matched = nodes(root,text,exact)
            if matched:
                return matched[0]
            swipe(direction)
    raise AssertionError("Not found on screen: " + text)

def pref():
    return adb("shell", "run-as", PACKAGE, "cat",
               "shared_prefs/preferences.xml", check=False).stdout

def bottom(label):
    root=dump("before_nav_"+label)
    candidates=nodes(root,label,True)
    if not candidates:
        raise AssertionError("Missing navigation tab " + label)
    tap(max(candidates,key=lambda n: bounds(n)[3]))
    return dump("after_nav_"+label)

def click(text, exact=False):
    tap(find(text,exact))

def text_all(root):
    return " ".join(n.attrib.get("text","") for n in root.iter("node"))

try:
    api = int(adb("shell", "getprop", "ro.build.version.sdk").stdout.strip())
    record("Android SDK matches requested emulator", api == int(os.environ.get("EXPECTED_API",api)), api)
    record("Android 11 minimum supported", api >= 30)
    install = adb("install", "-r", APK, timeout=120)
    record("APK installation", "Success" in install.stdout, install.stdout)
    adb("logcat", "-c")
    adb("shell", "am", "start", "-n", PACKAGE + "/.MainActivity")
    root = None
    for i in range(8):
        time.sleep(2)
        root = dump("boot"+str(i))
        if nodes(root,"Android China Setup"): break
        if nodes(root,"Wait"): tap(nodes(root,"Wait")[0])
        adb("shell", "am", "start", "-n", PACKAGE + "/.MainActivity")
    record("App launches without blocking", bool(nodes(root,"Android China Setup")))
    screenshot("01_apps_home")
    record("Catalog hero shown", bool(nodes(root,"为 Android 11+")))
    record("Download source benchmark shortcut exists", bool(nodes(root,"下载源测速")))
    record("Open original project button exists", bool(nodes(root,"访问原项目")))
    record("Download button exists", bool(nodes(root,"下载 APK")))
    record("App process alive", adb("shell","pidof",PACKAGE,check=False).returncode==0)

    # Check lazy list scrolling: one isolated list instead of the old giant
    # nested settings item in a single LazyColumn.
    adb("shell","dumpsys","gfxinfo",PACKAGE,"reset",check=False)
    for i in range(5): swipe(True)
    record("Scrollable catalog remains responsive",
           bool(nodes(dump("scroll_end"),"LocalSend")))
    screenshot("02_catalog_scrolled")
    gfx=adb("shell","dumpsys","gfxinfo",PACKAGE,check=False).stdout
    (OUT/"frame_stats.txt").write_text(gfx[-120000:],encoding="utf-8")
    record("Frame metrics collected", bool(gfx.strip()))

    # Explicit, visible benchmark screen and preset selection.
    root=bottom("测速")
    record("Speed test UI",bool(nodes(root,"开始测速")))
    record("Source selection shown",bool(nodes(root,"智能优选")))
    for name in ("GHFast","GH-Proxy","LLKK","GHProxy.net"):
        find(name)
        record("Visible mirror source "+name)
    screenshot("03_speed_sources")
    tap(find("GHFast"))
    record("Selecting source persists", "ghfast" in pref(),pref()[:250])
    # Custom mirror editing and persistence.
    click("源名称",exact=False)
    adb("shell","input","text","MyMirror")
    adb("shell","input","keyevent","4")
    click("HTTPS 镜像前缀")
    adb("shell","input","text","https://mirror.example.com")
    adb("shell","input","keyevent","4")
    click("添加加速源")
    record("Custom HTTPS mirror persisted", "mirror.example.com" in pref())
    screenshot("04_custom_mirror")
    # A real probe is started; success depends on current external network.
    click("开始测速")
    record("Speed test action available",bool(nodes(dump("04_speed_running"),"测速")))
    time.sleep(13)
    screenshot("05_speed_results_or_error")

    # Settings and optional proxy.
    root=bottom("设置")
    record("Proxy control visible",bool(nodes(root,"自定义代理")))
    record("Proxy disabled by default","proxyEnabled" not in pref() or
           'proxyEnabled" value="false' in pref())
    proxy_label=find("自定义代理")
    row_y=(bounds(proxy_label)[1]+bounds(proxy_label)[3])//2
    root=dump("settings_before_switch")
    toggles=[n for n in root.iter("node") if n.attrib.get("checkable")=="true"
             and n.attrib.get("clickable")=="true"]
    record("Compose proxy toggle found",bool(toggles))
    control=min(toggles,key=lambda n:abs((bounds(n)[1]+bounds(n)[3])//2-row_y))
    tap(control)
    record("Proxy host editable",bool(nodes(dump("settings_proxy"),"代理主机/IP")))
    click("SOCKS5")
    record("SOCKS5 selection saved","SOCKS5" in pref())
    screenshot("06_settings_proxy")

    # About and GitHub self-update controls.
    root=bottom("关于")
    record("About has source link",bool(nodes(root,"访问原项目（GitHub 源码）")))
    record("About has update control",bool(nodes(root,"检查更新")))
    record("About identifies Android 11",bool(nodes(root,"Android 11+")))
    screenshot("07_about_update")
    click("检查更新")
    time.sleep(2)
    record("Update action responds",bool(nodes(dump("after_check"),"应用自更新")))
    # After relaunch, settings should be preserved.
    adb("shell","am","force-stop",PACKAGE)
    adb("shell","am","start","-n",PACKAGE+"/.MainActivity")
    time.sleep(4)
    record("App relaunches",bool(nodes(dump("after_restart"),"Android China Setup")))
    record("Source is persisted across restart","mirror.example.com" in pref())
    record("Proxy is persisted across restart","SOCKS5" in pref())

    if os.environ.get("RUN_NETWORK_E2E")=="1":
        # Optional real external-network test; separate from deterministic UI CI.
        click("下载 APK")
        deadline=time.time()+190
        success=False
        while time.time()<deadline:
            text=text_all(dump("download_poll"))
            if "APK 已下载" in text:
                success=True
                break
            if "下载失败" in text: break
            time.sleep(4)
        screenshot("08_network_download")
        record("Real third-party APK download",success)
    else:
        print("SKIP network E2E download (requires RUN_NETWORK_E2E=1)",flush=True)
except Exception as e:
    results.append({"name":"blocking_error","passed":False,"note":repr(e)})
    print("FAIL: blocking_error",repr(e),flush=True)
finally:
    try:
        log=adb("logcat","-d","-v","time",check=False,timeout=40).stdout
        (OUT/"logcat.txt").write_text(log[-500000:],encoding="utf-8",errors="replace")
    except Exception: pass
    (OUT/"results.json").write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding="utf-8")
    print("Total:",len([r for r in results if r["passed"]]),"pass",
          len([r for r in results if not r["passed"]]),"fail",flush=True)

if not all(x["passed"] for x in results):
    raise SystemExit(1)
