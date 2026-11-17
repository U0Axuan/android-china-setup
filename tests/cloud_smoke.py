#!/usr/bin/env python3
"""Android China Setup V0.7 emulator UI smoke checks (API 30 and 35).

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
    w, h = map(int, re.findall(r"\d+", adb("shell", "wm", "size").stdout)[-2:])
    adb("shell", "input", "swipe", str(w//2), str(int(h*(0.70 if down else 0.28))),
        str(w//2), str(int(h*(0.28 if down else 0.70))), "230")
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

def hide_keyboard():
    state = adb("shell", "dumpsys", "input_method").stdout
    if re.search(r"(?:mInputShown|isInputViewShown)=true", state):
        adb("shell", "input", "keyevent", "4")
        time.sleep(1)

def fill_field(label, value):
    # A label can be visible underneath the IME. Find the actual EditText,
    # then verify focus before sending text; never type into a stale target.
    hide_keyboard()
    find(label)
    for attempt in range(8):
        root = dump("field_" + label + "_" + str(attempt))
        fields = [n for n in root.iter("node")
                  if n.attrib.get("class") == "android.widget.EditText"
                  and matches(n, label, True)]
        if fields:
            tap(fields[0])
            focused = dump("focus_" + label)
            candidates = [n for n in focused.iter("node")
                          if n.attrib.get("class") == "android.widget.EditText"
                          and n.attrib.get("focused") == "true"
                          and matches(n, label, True)]
            if candidates:
                adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
                adb("shell", "input", "keyevent", "--longpress", "KEYCODE_DEL")
                # Tests fill fresh/cleared fields, except invalid-address retry.
                for _ in range(len(candidates[0].attrib.get("text", ""))):
                    adb("shell", "input", "keyevent", "KEYCODE_DEL")
                adb("shell", "input", "text", value)
                root = dump("typed_" + label)
                assert_check("Input reaches " + label,
                    any(n.attrib.get("text") == value for n in root.iter("node")
                        if n.attrib.get("class") == "android.widget.EditText"))
                hide_keyboard()
                return
        hide_keyboard()
        swipe(True)
    raise AssertionError("Unable to focus input: " + label)

def pref():
    return adb("shell","run-as",PACKAGE,"cat",
               "shared_prefs/preferences.xml",check=False).stdout

def custom_prefixes():
    root = ET.fromstring(pref())
    value = next((n.text for n in root if n.attrib.get("name") == "customSources"), "[]")
    return [item["prefix"] for item in json.loads(value)]

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
    assert_check("Catalog item count visible",bool(matches(root,"23 个项目")))
    assert_check("Settings entry in top bar",bool(matches(root,"设置",True)))
    assert_check("No bottom navigation",not matches(root,"测速",True) and
        not matches(root,"关于",True))
    assert_check("No app checkbox",not any(n.attrib.get("class")==
        "android.widget.CheckBox" for n in root.iter("node")))
    shot("01_category_home")

    # Browse first detail: compact readme snapshot and canonical project links.
    click("曦码输入法")
    root=dump("02_readme_sheet")
    for attempt in range(5):
        if matches(root,"阅读原项目 README 全文"): break
        adb("shell","input","keyevent","4")
        time.sleep(1)
        click("曦码输入法")
        root=dump("02_readme_retry_"+str(attempt))
    assert_check("Upstream README area displayed",bool(matches(root,"README")))
    assert_check("README is cached or offline",bool(matches(root,"离线摘要")) or bool(matches(root,"原文已排版")))
    assert_check("Original README link",bool(matches(root,"阅读原项目 README 全文")))
    assert_check("Original project link",bool(matches(root,"访问原项目",True)))
    shot("02_readme_snapshot")
    adb("shell","input","keyevent","4")
    time.sleep(2)

    # Scan one stable LazyColumn in a single pass instead of performing
    # dozens of separate forward/backward searches (faster and less flaky).
    categories=["日常与效率","游戏与影音","网络与连接",
                "应用与文件","系统与调试","高级与 Root"]
    projects=["osu! (lazer)","DroidDeck","TagMacro","Canta",
              "DirtyFrag","KSuRoot","LocalSend"]
    seen_categories=set()
    seen_projects=set()
    for direction in (True,False):
        for step in range(15):
            screen=dump("scan_"+str(direction)+"_"+str(step))
            seen_categories.update(c for c in categories if matches(screen,c))
            seen_projects.update(p for p in projects if matches(screen,p))
            if len(seen_categories)==len(categories) and len(seen_projects)==len(projects):
                break
            swipe(direction)
        if len(seen_categories)==len(categories) and len(seen_projects)==len(projects):
            break
    for category in categories:
        assert_check("Category "+category,category in seen_categories)
    for name in projects:
        assert_check("Catalog "+name,name in seen_projects)
    shot("03_catalog_bottom")
    click("KSuRoot")
    assert_check("High-risk warning visible",
        find("高风险 · 仅原项目入口",attempts=7) is not None)
    assert_check("High risk README is accessible",
        find("README",attempts=7) is not None)
    assert_check("High risk root entry is informational",
        find("高风险系统工具仅展示原项目",attempts=7) is not None)
    root=dump("03_high_risk_sheet_bottom")
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
    assert_check("Settings opened",bool(matches(root,"进入下载与测速")))
    assert_check("Online catalog controls shown",bool(matches(root,"刷新应用目录")))
    assert_check("Online catalog count shown",bool(matches(root,"已收录 23 个开源项目")))
    assert_check("Recommendation issue entry",bool(matches(root,"提交应用推荐")))
    click("进入下载与测速")
    assert_check("Speed test in secondary menu",bool(matches(dump("06_download_menu"),"开始测速")))
    assert_check("Auto mirror selector",bool(matches(dump("06_mirrors"),"智能优选")))
    shot("06_settings_speed")
    for name in ["GHFast","GH-Proxy","LLKK","GHProxy.net"]:
        find(name)
        assert_check("Mirror "+name,True)
    click("GHFast")
    assert_check("Mirror choice saved","ghfast" in pref())

    fill_field("源名称", "MyMirror")
    fill_field("HTTPS 镜像前缀", "http://invalid.example.com")
    click("添加加速源")
    assert_check("HTTP mirror rejected", bool(matches(dump("07_invalid_source"), "请输入合法 HTTPS")))
    assert_check("Invalid mirror not persisted", "http://invalid.example.com" not in custom_prefixes())
    fill_field("HTTPS 镜像前缀", "https://mirror.example.com")
    click("添加加速源")
    assert_check("Custom mirror persisted", "https://mirror.example.com" in custom_prefixes())
    assert_check("Custom mirror success feedback", bool(matches(dump("07_added"), "已添加并选中 MyMirror")))
    shot("07_custom_source")

    # The speed action must be visible and respond; external connectivity
    # is not treated as proof that all community mirrors are available.
    click("开始测速")
    root=dump("08_speed_started")
    assert_check("Speed action responds",bool(matches(root,"测速")))
    time.sleep(10)
    shot("08_speed_results")

    # Proxy and About are sections of the same Settings page.
    # The proxy row can be only partly visible at the bottom (Android 15
    # reported y=2317..2400 on a 2400px screen). A tap there can be
    # intercepted by the system gesture/navigation area. Scroll until the
    # *entire* switch is in the safe centre of the view before tapping it.
    height=int(re.findall(r"\d+",adb("shell","wm","size").stdout)[-1])
    def visible_proxy_switch(label):
        ui=dump(label)
        labels=matches(ui,"自定义代理（默认关闭）")
        if not labels:
            return None
        row=(bounds(labels[0])[1]+bounds(labels[0])[3])//2
        switches=[n for n in ui.iter("node")
                  if n.attrib.get("checkable")=="true"
                  and n.attrib.get("class")=="android.view.View"
                  and n.attrib.get("clickable")=="true"
                  and len(bounds(n))==4
                  and abs((bounds(n)[1]+bounds(n)[3])//2-row)<170]
        if not switches:
            return None
        switch=min(switches,key=lambda n:
            abs((bounds(n)[1]+bounds(n)[3])//2-row))
        box=bounds(switch)
        if box[1] < height*0.18 or box[3] > height*0.78:
            return None
        return switch
    toggle=None
    for attempt in range(12):
        toggle=visible_proxy_switch("09_proxy_visible_"+str(attempt))
        if toggle is not None:
            break
        swipe(False if attempt < 6 else True)
    assert_check("Proxy toggle fully visible in safe touch area",toggle is not None)
    # Verify actual state, not that the tap command merely returned.
    if toggle.attrib.get("checked")!="true":
        tap(toggle)
    for attempt in range(4):
        switch=visible_proxy_switch("09_proxy_on_"+str(attempt))
        if switch is not None and switch.attrib.get("checked")=="true":
            break
        if switch is not None:
            tap(switch)
        else:
            swipe(True)
        time.sleep(0.7)
    assert_check("Proxy switch enabled",
        switch is not None and switch.attrib.get("checked")=="true")
    host_field=find("代理主机/IP",attempts=8)
    assert_check("Proxy details shown",host_field is not None)
    click("SOCKS5")
    assert_check("SOCKS5 persisted","SOCKS5" in pref())
    shot("09_proxy_settings")

    # Restore a working network configuration before update/network checks.
    toggle = visible_proxy_switch("09_disable_proxy")
    if toggle is not None and toggle.attrib.get("checked") == "true": tap(toggle)
    assert_check("Proxy disabled again", 'name="proxyEnabled" value="false"' in pref())
    click("返回设置")
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
    assert_check("Custom source survives restart","https://mirror.example.com" in custom_prefixes())
    shot("12_relaunch")
    # Delete the selected custom mirror and verify the automatic fallback.
    click("设置", True)
    click("进入下载与测速")
    click("删除", True)
    assert_check("Custom mirror deleted", "https://mirror.example.com" not in custom_prefixes())
    assert_check("Deleting selected mirror restores auto", '>auto<' in pref())
    click("返回设置")
    click("返回应用列表")

    # Offline cold start must retain the catalog and a readable detail.
    adb("shell", "svc", "wifi", "disable")
    adb("shell", "svc", "data", "disable")
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-n", PACKAGE + "/.MainActivity")
    time.sleep(3)
    assert_check("Offline cold start shows catalog", bool(matches(dump("14_offline"), "23 个项目")))
    click("曦码输入法")
    assert_check("Offline README fallback readable", bool(matches(dump("14_offline_detail"), "README")))
    shot("14_offline_detail")
    adb("shell", "input", "keyevent", "4")
    adb("shell", "svc", "wifi", "enable")
    adb("shell", "svc", "data", "enable")

    # Exercise navigation under font/theme/size changes, capture actual UI.
    for name, font, dark, size in [
        ("15_dark_large", "1.4", "yes", None),
        ("16_font_200", "2.0", "no", None),
        ("17_narrow", "1.0", "no", "840x1800"),
        ("18_landscape", "1.0", "yes", "2400x1080"),
        ("19_tablet", "1.0", "no", "2560x1600"),
    ]:
        adb("shell", "settings", "put", "system", "font_scale", font)
        adb("shell", "cmd", "uimode", "night", dark)
        if size: adb("shell", "wm", "size", size)
        time.sleep(3)
        assert_check(name + " catalog", bool(matches(dump(name), "Android China Setup")))
        shot(name + "_home")
        click("设置", True)
        find("进入下载与测速")
        shot(name + "_settings")
        click("进入下载与测速")
        find("开始测速")
        assert_check(name + " submenu usable", True)
        shot(name + "_download")
        click("返回设置")
        click("返回应用列表")
    adb("shell", "wm", "size", "reset")
    adb("shell", "settings", "put", "system", "font_scale", "1.0")
    adb("shell", "cmd", "uimode", "night", "no")
    assert_check("No app fatal exception", not re.search(
        r"FATAL EXCEPTION[^\n]*\n[^\n]*Process: " + re.escape(PACKAGE),
        adb("logcat", "-d", "-v", "brief").stdout))

except Exception as exc:
    checks.append({"name":"blocking_error","passed":False,"note":repr(exc)})
    print("FAIL: blocking_error",repr(exc),flush=True)
finally:
    shot("final_state")
    try:
        dump("final_state")
        (OUT/"preferences.xml").write_text(pref(), encoding="utf-8")
    except Exception: pass
    try:
        logs=adb("logcat","-d","-v","time",timeout=40,check=False).stdout
        (OUT/"logcat.txt").write_text(logs[-500000:],encoding="utf-8",errors="replace")
    except Exception: pass
    (OUT/"results.json").write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding="utf-8")
    print("Summary:",sum(1 for x in checks if x["passed"]),
          "passed,",sum(1 for x in checks if not x["passed"]),"failed",flush=True)

if not all(x["passed"] for x in checks):
    raise SystemExit(1)
