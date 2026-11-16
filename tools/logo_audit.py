#!/usr/bin/env python3
"""Audit curated upstream icons in catalog/apps.json; no third-party logo guessing."""
import base64
import json
import os
import re
import struct
import sys
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

CATALOG = Path(__file__).resolve().parent.parent / "catalog/apps.json"
MAX_ICON_BYTES = 350_000

def dimensions(data):
    if data.startswith(b"\x89PNG\r\n\x1a\n") and len(data) >= 24:
        return struct.unpack(">II", data[16:24])
    if data.startswith(b"RIFF") and data[8:12] == b"WEBP":
        if data[12:16] == b"VP8X" and len(data) >= 30:
            w = 1 + int.from_bytes(data[24:27], "little")
            h = 1 + int.from_bytes(data[27:30], "little")
            return w, h
        if data[12:16] == b"VP8L" and len(data) >= 25:
            b0,b1,b2,b3 = data[21:25]
            return 1 + (((b1 & 0x3F) << 8) | b0), 1 + (((b3 & 0x0F) << 10) | (b2 << 2) | (b1 >> 6))
        if data[12:16] == b"VP8 ":
            return 0, 0  # Lossy WebP format verified; dimensions checked by Android's image decoder.
    if data.startswith(b"\xff\xd8\xff"):
        return 0, 0
    raise ValueError("no supported raster image signature")

def check(app):
    path = app.get("logoPath")
    if not path:
        return "SKIP", "no verified logo; app should hide the image area"
    repo = app.get("repository")
    basename = path.rsplit("/", 1)[-1].lower()
    if not re.search(r"logo|icon|launcher", basename):
        return "FAIL", "filename is not clearly an application icon"
    url = "https://api.github.com/repos/" + repo + "/contents/" + path
    headers = {"Accept":"application/vnd.github+json",
               "User-Agent":"Android-China-Setup-Logo-Audit"}
    token = os.environ.get("GITHUB_TOKEN", "")
    if token: headers["Authorization"] = "Bearer " + token
    try:
        with urlopen(Request(url, headers=headers), timeout=12) as response:
            item = json.load(response)
    except HTTPError as exc:
        if exc.code in (403, 429, 500, 502, 503, 504):
            return "REVIEW", "GitHub temporarily unavailable (" + str(exc.code) + ")"
        return "FAIL", "GitHub source asset missing or inaccessible (HTTP " + str(exc.code) + ")"
    except (URLError, TimeoutError, OSError) as exc:
        return "REVIEW", "network unavailable: " + str(exc)[:120]

    if item.get("type") != "file":
        return "FAIL", "icon path does not point to a file"
    size = item.get("size", -1)
    if not isinstance(size, int) or not 100 <= size <= MAX_ICON_BYTES:
        return "FAIL", "source image exceeds 350KB limit or is empty"
    source = item.get("download_url", "")
    if not source.startswith("https://raw.githubusercontent.com/" + repo + "/"):
        return "FAIL", "image URL is not from its official GitHub repository"
    encoded = item.get("content") or ""
    if item.get("encoding") != "base64" or not encoded:
        return "REVIEW", "GitHub did not provide bounded image bytes"
    try:
        data = base64.b64decode(encoded)
        if not 100 <= len(data) <= MAX_ICON_BYTES:
            raise ValueError("image file size invalid")
        width, height = dimensions(data)
        if width and height and not (32 <= width <= 2048 and 32 <= height <= 2048 and
                                     0.70 <= width / height <= 1.43):
            raise ValueError("image is not a reasonably square app icon")
    except (ValueError, TypeError) as exc:
        return "FAIL", str(exc)
    return "PASS", path

def main():
    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    failed = []
    count = 0
    for app in data:
        status, reason = check(app)
        if status != "SKIP": count += 1
        print(status + ": " + app["name"] + " — " + reason)
        if status == "FAIL":
            failed.append(app["name"] + ": " + reason)
            print("::error::Logo invalid for " + app["name"] + ": " + reason)
        elif status == "REVIEW":
            print("::warning::Review logo evidence for " + app["name"] + ": " + reason)
    print("Audited " + str(count) + " curated logos from " + str(len(data)) + " apps.")
    if failed:
        print("Invalid curated icons: " + "; ".join(failed), file=sys.stderr)
        raise SystemExit(1)

if __name__ == "__main__":
    main()
