#!/usr/bin/env python3
"""GitHub Actions Android emulator smoke/e2e checks (no desktop or root on the phone)."""
import json
import hashlib
import urllib.request
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

def adb(*args, timeout=40, check=True):
    return subprocess.run(["adb", *args], capture_output=True, text=True,
                          timeout=timeout, check=check)

def record(name, ok, note=""):
    results.append({"name": name, "passed": bool(ok), "note": str(note)})
    print(("PASS" if ok else "FAIL") + ": " + name + (" — " + str(note) if note else ""), flush=True)

def screenshot(name):
    try:
        raw = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=35, check=True)
        (OUT / (name + ".png")).write_bytes(raw.stdout)
    except Exception as exc:
        print("Screenshot unavailable:", exc, flush=True)

def dump(name):
    for _ in range(3):
        try:
            adb("shell", "uiautomator", "dump", "/sdcard/window.xml", timeout=45)
            data = adb("shell", "cat", "/sdcard/window.xml").stdout
            (OUT / (name + ".xml")).write_text(data, encoding="utf-8")
            return ET.fromstring(data)
        except Exception:
            time.sleep(2)
    raise RuntimeError("Cannot obtain UiAutomator accessibility hierarchy")

def matches(root, text):
    return [node for node in root.iter("node") if text in (node.attrib.get("text") or "")
            or text in (node.attrib.get("content-desc") or "")]

def bounds(node):
    nums = list(map(int, re.findall(r"\d+", node.attrib["bounds"])))
    return (nums[0], nums[1], nums[2], nums[3])

def tap(node, x_override=None):
    x0, y0, x1, y1 = bounds(node)
    x = (x0+x1)//2 if x_override is None else x_override
    adb("shell", "input", "tap", str(x), str((y0+y1)//2))
    time.sleep(1.2)

def visible(text, label="current"):
    return matches(dump(label), text)

def tap_text(text, label):
    found = visible(text, label)
    if not found:
        raise AssertionError("UI item not visible: " + text)
    tap(found[0])

def tap_source_radio(label):
    target, tree = scroll_to(label)
    center_y = (bounds(target)[1] + bounds(target)[3]) // 2
    radios = [node for node in tree.iter("node")
              if node.attrib.get("class") == "android.widget.RadioButton"]
    assert radios, "No radio buttons in source panel"
    radio = min(radios, key=lambda n: abs((bounds(n)[1] + bounds(n)[3]) // 2 - center_y))
    tap(radio)
    time.sleep(1)
    return pref()

def scroll_to(text):
    # Search toward the bottom, then upward again; both mirror editor and
    # the catalog are in one vertically scrolling LazyColumn.
    for direction in ("down", "up"):
        for i in range(12):
            root = dump("scroll_%s_%02d" % (direction, i))
            found = matches(root, text)
            if found:
                return found[0], root
            y1, y2 = (1790, 820) if direction == "down" else (820, 1790)
            adb("shell", "input", "swipe", "500", str(y1), "500", str(y2), "250")
            time.sleep(0.5)
    raise AssertionError("Could not scroll to: " + text)

def pref():
    return adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/preferences.xml", check=False).stdout

def check(condition, name, note=""):
    record(name, condition, note)
    if not condition:
        raise AssertionError(name + ": " + note)

try:
    sdk_api = int(adb("shell", "getprop", "ro.build.version.sdk").stdout.strip())
    check(sdk_api == int(os.environ.get("EXPECTED_API", sdk_api)),
          "emulator_expected_android_api", str(sdk_api))
    check(sdk_api >= 30, "android_11_minimum_supported")
    print("Installing APK:", APK, flush=True)
    install = adb("install", "-r", APK, timeout=120)
    check("Success" in install.stdout, "install_debug_apk", install.stdout.strip())
    adb("logcat", "-c")
    start = adb("shell", "am", "start", "-n", PACKAGE + "/.MainActivity")
    time.sleep(7)
    screenshot("01_initial")
    root = dump("01_initial")
    check(bool(matches(root, "Android China Setup")), "launch_and_show_main_screen")
    check(bool(matches(root, "自定义代理")), "proxy_toggle_visible")
    check(bool(matches(root, "设备：")), "device_information_visible")
    check(bool(matches(root, "配置下载加速镜像")), "download_source_configuration_visible")
    check("proxyEnabled" not in pref() or "proxyEnabled\" value=\"false" in pref(),
          "proxy_disabled_by_default", pref()[:600])
    check(adb("shell", "pidof", PACKAGE, check=False).returncode == 0, "no_crash_on_start")

    # Mirror selector: auto, official, presets, and persistent custom sources.
    tap_text("配置下载加速镜像", "02_before_mirror")
    screenshot("03_mirror_open")
    check(bool(matches(dump("03_mirror_open"), "智能优选")), "auto_source_option_visible")
    check("sourceId" not in pref() or "auto" in pref(), "auto_is_default")
    for source in ("GHFast", "GH-Proxy", "LLKK", "GHProxy.net"):
        scroll_to(source)
        record("preset_source_" + source, True)

    # A user can explicitly choose a preset source.
    saved = tap_source_radio("GHFast")
    check('ghfast' in saved, "preset_source_selection_persisted", saved[:800])
    screenshot("04_preset_selected")

    # The reference catalogue is discoverable directly inside the app.
    scroll_to("打开 hiwiki 加速源参考列表")
    record("hiwiki_reference_link_visible", True)

    # Add a custom HTTPS URL-prefix mirror without ADB or desktop tooling
    # in the Android app itself; ADB here is only test automation.
    name, _ = scroll_to("自定义源名称")
    tap(name)
    adb("shell", "input", "text", "MyMirror")
    adb("shell", "input", "keyevent", "4")
    prefix, _ = scroll_to("自定义 HTTPS 镜像前缀")
    tap(prefix)
    adb("shell", "input", "text", "https://mirror.example.com")
    adb("shell", "input", "keyevent", "4")
    button, _ = scroll_to("添加加速源")
    tap(button)
    check("mirror.example.com" in pref(), "custom_mirror_persisted", pref()[:800])
    check("custom-" in pref(), "custom_mirror_selected")
    screenshot("05_custom_added")

    # Select official source before the live download; test of mirror network
    # transit/fallback requires a controlled mirror and is not implied here.
    saved = tap_source_radio("GitHub 官方直连")
    check('official' in saved, "official_source_selection_persisted", saved[:800])
    close, _ = scroll_to("收起下载源设置")
    tap(close)

    # Proxy settings: default off, enabled config, SOCKS5, persistence, then off.
    proxy_label, _ = scroll_to("自定义代理")
    screen_width = int(re.findall(r"\d+", adb("shell", "wm", "size").stdout)[0])
    tap(proxy_label, x_override=screen_width-65)
    proxy_root = dump("06_proxy_on")
    check(bool(matches(proxy_root, "代理主机/IP")), "proxy_host_setting_visible")
    check(bool(matches(proxy_root, "端口")), "proxy_port_setting_visible")
    check(bool(matches(proxy_root, "SOCKS5")), "socks5_option_visible")
    tap_text("SOCKS5", "07_before_socks")
    check("SOCKS5" in pref(), "socks5_option_persisted")
    screenshot("07_proxy_socks5")
    # Switch off before download.
    proxy_label, _ = scroll_to("自定义代理")
    tap(proxy_label, x_override=screen_width-65)
    check("proxyEnabled\" value=\"false" in pref(), "proxy_can_be_disabled", pref()[:550])

    # Catalog and navigation.
    for key in ("曦码输入法", "Via 浏览器", "BiliPai", "FlClash",
                "Shizuku", "GhostLock", "知乎++", "Obtainium",
                "F-Droid", "Material Files", "LocalSend"):
        scroll_to(key)
        record("catalog_" + key, True)
    screenshot("08_catalog_bottom")
    # UI item state persists after process restart.
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-n", PACKAGE + "/.MainActivity")
    time.sleep(3)
    check(bool(matches(dump("09_relaunch"), "Android China Setup")),
          "relaunch_after_saved_settings")

    # Download official Material Files APK and trigger Android Package Installer.
    # This is a real network integration test and can fail due to connectivity.
    material, root = scroll_to("Material Files")
    my = (bounds(material)[1] + bounds(material)[3]) // 2
    buttons = [node for node in matches(root, "下载 APK")
               if bounds(node)[1] >= my and bounds(node)[1] <= my + 280]
    check(bool(buttons), "material_files_download_button_found")
    tap(buttons[0])
    screenshot("10_download_started")
    print("Waiting for Material Files download (max 180s)", flush=True)
    complete = False
    status = ""
    for i in range(45):
        time.sleep(4)
        root = dump("download_poll")
        text_content = " ".join(x.attrib.get("text", "") for x in root.iter("node"))
        if "下载完成" in text_content:
            complete = True
            status = "Downloaded APK available"
            break
        if "下载失败" in text_content:
            status = text_content[-650:]
            break
    screenshot("11_download_result")
    check(complete, "official_apk_download", status or "Timed out")
    # Independent SHA-256 verification of the downloaded APK bytes against
    # current GitHub Release asset metadata.
    try:
        req = urllib.request.Request(
            "https://api.github.com/repos/zhanghai/MaterialFiles/releases/latest",
            headers={"User-Agent": "Android-China-Setup-CI", "Accept": "application/vnd.github+json"})
        with urllib.request.urlopen(req, timeout=35) as response:
            metadata = json.load(response)
        matching_assets = [a for a in metadata["assets"]
                           if a["name"] == "app-release-universal.apk"]
        check(len(matching_assets) == 1 and
              matching_assets[0]["digest"].startswith("sha256:"),
              "official_release_digest_available")
        expected = matching_assets[0]["digest"].split(":", 1)[1]
        apk_bytes = subprocess.run(
            ["adb", "exec-out", "run-as", PACKAGE, "cat", "cache/apk_downloads/pending.apk"],
            check=True, capture_output=True, timeout=40).stdout
        actual = hashlib.sha256(apk_bytes).hexdigest()
        check(actual == expected, "download_sha256_matches_official_release",
              "sha256=" + actual)
    except Exception as exc:
        record("independent_digest_check", False, repr(exc))
        raise

    # Request unknown-app permission, then open package installer.
    adb("shell", "appops", "set", PACKAGE, "REQUEST_INSTALL_PACKAGES", "allow")
    tap_text("安装已下载 APK", "12_before_install")
    time.sleep(3)
    screenshot("13_package_installer")
    root = dump("13_package_installer")
    all_text = " ".join((n.attrib.get("text") or "") for n in root.iter("node"))
    # Some platform versions require a separate unknown-sources settings screen.
    check(any(token in all_text for token in ("Install", "安装", "Allow from this source", "允许来自此来源")),
          "android_package_installer_launched", all_text[-450:])
    if matches(root, "Install"):
        tap(matches(root, "Install")[-1])
    elif matches(root, "安装"):
        tap(matches(root, "安装")[-1])
    else:
        raise AssertionError("Installer confirmation action not visible")
    time.sleep(10)
    screenshot("14_after_install")
    installed = adb("shell", "pm", "path", "me.zhanghai.android.files", check=False)
    check("package:" in installed.stdout, "material_files_actually_installed",
          installed.stdout.strip() or installed.stderr.strip())
    # Verify returning to Android China Setup does not crash after installation.
    adb("shell", "am", "start", "-n", PACKAGE + "/.MainActivity")
    time.sleep(2)
    screenshot("15_return_to_app")
    check(bool(matches(dump("15_return_to_app"), "Android China Setup")),
          "return_after_install")
except Exception as exc:
    record("blocking_exception", False, repr(exc))
finally:
    try:
        out = adb("logcat", "-d", "-v", "time", timeout=45, check=False).stdout
        (OUT / "logcat.txt").write_text(out[-500000:], encoding="utf-8", errors="replace")
    except Exception as exc:
        print("Logcat unavailable", exc)
    (OUT / "results.json").write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print("Summary:", len([r for r in results if r["passed"]]), "passed",
          len([r for r in results if not r["passed"]]), "failed", flush=True)

if not all(x["passed"] for x in results):
    raise SystemExit(1)
