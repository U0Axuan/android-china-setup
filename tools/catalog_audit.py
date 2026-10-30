#!/usr/bin/env python3
"""Audit catalog admission rules against public GitHub repository metadata.

Pure stdlib; no Android build. Unknown evidence is never represented as a pass.
"""
import argparse
import datetime as dt
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "catalog/apps.json"
API = "https://api.github.com"
TODAY = dt.datetime.now(dt.timezone.utc)
CUTOFF = TODAY - dt.timedelta(days=183)
OPEN_SOURCE_LICENSES = {"NOASSERTION", "OTHER", "NONE", ""}

def fetch(path):
    req = urllib.request.Request(API + path, headers={
        "Accept": "application/vnd.github+json",
        "User-Agent": "android-china-setup-catalog-audit",
        **({"Authorization": "Bearer " + os.environ["GITHUB_TOKEN"]} if os.getenv("GITHUB_TOKEN") else {}),
    })
    try:
        with urllib.request.urlopen(req, timeout=12) as resp:
            return json.load(resp), None
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, ValueError) as exc:
        return None, str(exc)

def github_repo(app):
    repo = app.get("repository")
    if repo:
        return repo
    for candidate in (app.get("readmeUrl", ""), app.get("homepage", "")):
        parts = urllib.parse.urlsplit(candidate)
        if parts.hostname and parts.hostname.lower() == "github.com":
            segments = [s for s in parts.path.split("/") if s]
            if len(segments) >= 2:
                return "/".join(segments[:2])
    return None

def audit(app):
    name = app["name"]
    messages = []
    repo = github_repo(app)
    if not repo:
        return [(name, "REVIEW", "未提供 GitHub 仓库，需人工核验源码、许可证、发布页、维护及安全扫描记录")]
    data, err = fetch("/repos/" + repo)
    if err:
        return [(name, "REVIEW", f"无法核验公开仓库 {repo}：{err}")]
    if data.get("private") or data.get("archived") or data.get("disabled"):
        messages.append((name, "FAIL", "仓库私有、已归档或不可用"))
    lic = (data.get("license") or {}).get("spdx_id", "")
    if lic in OPEN_SOURCE_LICENSES:
        messages.append((name, "REVIEW", "未识别到明确的开源许可证，需核验 LICENSE 文件"))
    last = data.get("pushed_at")
    if last:
        try:
            age = dt.datetime.fromisoformat(last.replace("Z", "+00:00"))
            if age < CUTOFF:
                messages.append((name, "REVIEW", f"最后推送于 {last[:10]}，需核验近六个月的实质性维护活动"))
        except ValueError:
            messages.append((name, "REVIEW", "无法解析最近维护日期"))
    else:
        messages.append((name, "REVIEW", "缺少最近维护日期"))
    releases, release_err = fetch("/repos/" + repo + "/releases?per_page=1")
    if release_err:
        messages.append((name, "REVIEW", "无法检查开源平台 Release：" + release_err))
    elif not releases:
        tags, tag_err = fetch("/repos/" + repo + "/tags?per_page=1")
        if tag_err or not tags:
            messages.append((name, "REVIEW", "未发现 GitHub Releases / Tags，请确认其他开源平台的版本发布地址"))
        else:
            messages.append((name, "REVIEW", "存在 GitHub Tag，但需确认对应正式版本及 APK 发布位置"))
    elif not releases[0].get("assets"):
        messages.append((name, "REVIEW", "GitHub Release 未包含附件；需确认安装包在开源平台的发布位置"))
    # Public code scanning can be disabled/absent; lack of records is not proof of malware.
    scans, scan_err = fetch("/repos/" + repo + "/code-scanning/analyses?per_page=1")
    if scan_err or not isinstance(scans, list) or not scans:
        messages.append((name, "REVIEW", "未取得可核验的公开安全扫描结果，需提供报告或 CI 扫描链接"))
    else:
        messages.append((name, "REVIEW", "找到 Code Scanning 分析记录；仍需核验扫描结论及高危风险处置情况"))
    return messages

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--file", type=Path, default=CATALOG)
    p.add_argument("--strict", action="store_true", help="Fail CI on confirmed violations, not missing evidence")
    args = p.parse_args()
    apps = json.loads(args.file.read_text(encoding="utf-8"))
    findings = []
    for app in apps:
        findings.extend(audit(app))
    summary = ["# 应用入选要求自动审核", "", f"检查 {len(apps)} 个应用（UTC {TODAY:%Y-%m-%d}）", "",
               "状态说明：FAIL=明确不符合；REVIEW=证据不足或需要人工核验。自动核验不等于安全认证。", "",
               "| 应用 | 状态 | 说明 |", "|---|---|---|"]
    for name, level, message in findings:
        safe = message.replace("|", "/").replace("\n", " ")
        summary.append(f"| {name} | {level} | {safe} |")
        print(f"::{'error' if level == 'FAIL' else 'warning'} title={name} [{level}]::{message}")
    summary.append("")
    summary.append(f"共 {sum(x[1] == 'FAIL' for x in findings)} 项明确不符合，{sum(x[1] == 'REVIEW' for x in findings)} 项待核验。")
    output = "\n".join(summary) + "\n"
    if os.getenv("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as f:
            f.write(output)
    else:
        print(output)
    return int(args.strict and any(x[1] == "FAIL" for x in findings))

if __name__ == "__main__":
    sys.exit(main())
