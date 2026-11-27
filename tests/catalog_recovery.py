"""Verify first offline launch followed by automatic recovery on the next eligible launch."""
import json
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = "io.github.u0axuan.androidchinasetup"


def shell(*args, check=True):
    return subprocess.run(["adb", "shell", *args], capture_output=True, text=True, check=check).stdout


def prefs():
    raw = shell("run-as", PACKAGE, "cat", "shared_prefs/catalog-update.xml", check=False)
    return {n.get("name"): n.get("value", n.text) for n in ET.fromstring(raw)} if raw.strip().startswith("<?xml") else {}


def launch():
    shell("am", "start", "-n", PACKAGE + "/.MainActivity")


def until(condition, timeout):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if condition():
            return
        time.sleep(1)
    raise AssertionError("Catalog condition timed out")


shell("pm", "clear", PACKAGE)
try:
    shell("svc", "wifi", "disable")
    shell("svc", "data", "disable")
    launch()
    until(lambda: "last_attempt" in prefs(), 20)
    first = prefs()["last_attempt"]
    time.sleep(3)
    assert "last_success_day" not in prefs(), "Offline request marked successful"
    shell("am", "force-stop", PACKAGE)
finally:
    shell("svc", "wifi", "enable")
    shell("svc", "data", "enable")
# Use the device clock because preferences use device wall time.
until(lambda: int(shell("date", "+%s")) * 1000 - int(first) >= 60000, 70)
launch()
until(lambda: "last_success_day" in prefs(), 60)
assert prefs()["last_attempt"] != first
catalog = json.loads(shell("run-as", PACKAGE, "cat", "files/catalog-cache.json"))
assert catalog and all(a.get("name") for a in catalog)
print("PASS: Offline launch recovers automatically and persists a validated catalog")
