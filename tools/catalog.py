#!/usr/bin/env python3
"""Android China Setup catalog editor. Standard library only.

Future app additions change ONLY catalog/apps.json; the app refreshes the
remote catalog at launch (periodically) or through Settings, without an APK
rebuild. APK source changes remain subject to full Android CI.
"""
import argparse
import json
import re
import sys
import tempfile
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parent.parent
DEFAULT = ROOT / "catalog" / "apps.json"
CATEGORIES = (
    "日常与效率", "游戏与影音", "网络与连接",
    "应用与文件", "系统与调试", "高级与 Root",
)
REPOSITORY = re.compile(r"^[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}$")
HIGH_RISK = {"GhostLock", "DirtyFrag", "KSuRoot"}
ALLOWED = {
    "name", "category", "summary", "readmeSnapshot", "homepage",
    "readmeUrl", "repository", "notice", "risk", "downloadMode",
    "preferredApkRegex"
}
LIMIT_BYTES = 256 * 1024

def valid_repo(repo):
    if not REPOSITORY.fullmatch(repo):
        return False
    parts = repo.split("/")
    return all(part not in (".", "..") and not part.startswith("-")
               for part in parts)

def is_https(value):
    if not isinstance(value, str) or len(value) > 1000:
        return False
    try:
        uri = urlsplit(value)
        return (uri.scheme == "https" and uri.hostname is not None
                and "." in uri.hostname
                and uri.hostname.lower() != "localhost"
                and uri.username is None and uri.password is None
                and uri.port is None
                and not re.match(r"^\d+(?:\.\d+){3}$", uri.hostname))
    except (ValueError, TypeError):
        return False

def validate(data):
    if not isinstance(data, list) or not 1 <= len(data) <= 300:
        raise ValueError("目录项目数必须在 1–300 之间")
    names = set()
    for i, app in enumerate(data):
        if not isinstance(app, dict):
            raise ValueError(f"第 {i+1} 项必须为对象")
        unknown = app.keys() - ALLOWED
        if unknown:
            raise ValueError(f"第 {i+1} 项包含未知字段: {sorted(unknown)}")
        name = app.get("name")
        if not isinstance(name, str) or not name.strip() or len(name) > 70:
            raise ValueError(f"第 {i+1} 项缺少有效名称")
        if name in names:
            raise ValueError(f"发现重复应用名称: {name}")
        names.add(name)
        for field, limit in (("category",40),("summary",260),("readmeSnapshot",900)):
            value = app.get(field)
            if not isinstance(value, str) or not value.strip() or len(value) > limit:
                raise ValueError(f"{name} 的 {field} 为空或过长")
        for field in ("homepage", "readmeUrl"):
            if not is_https(app.get(field)):
                raise ValueError(f"{name} 的 {field} 不是合法 HTTPS URL")
        repo = app.get("repository", "")
        if not isinstance(repo, str) or (repo and not valid_repo(repo)):
            raise ValueError(f"{name} 的 GitHub 仓库名无效")
        mode = app.get("downloadMode", "auto")
        risk = app.get("risk", "")
        if mode not in ("auto", "external") or risk not in ("", "caution", "high"):
            raise ValueError(f"{name} 的下载模式或风险级别无效")
        if not repo and mode != "external":
            raise ValueError(f"{name} 没有 GitHub 仓库时只能使用外部入口")
        if (risk == "high" and mode != "external") or \
           (name in HIGH_RISK and risk != "high"):
            raise ValueError(f"{name} 为高风险项目，必须仅提供外部入口")
        if len(app.get("notice", "")) > 200:
            raise ValueError(f"{name} 的风险提醒过长")
        regex = app.get("preferredApkRegex", "")
        if not isinstance(regex, str) or len(regex) > 160:
            raise ValueError(f"{name} 的 APK 匹配规则无效")
        if regex:
            try:
                re.compile(regex)
            except re.error as exc:
                raise ValueError(f"{name} 的 APK 正则表达式无效: {exc}") from exc
    size = len(json.dumps(data, ensure_ascii=False, indent=2).encode("utf-8"))
    if size > LIMIT_BYTES:
        raise ValueError("清单超过 256 KiB")
    return len(data)

def read(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))

def write(path, data):
    validate(data)
    Path(path).write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n",
                          encoding="utf-8")

def add(data, args):
    if any(item["name"] == args.name for item in data):
        raise ValueError(f"项目已经存在: {args.name}")
    repo = args.repo or ""
    if repo and not valid_repo(repo):
        raise ValueError("请使用 owner/repo 格式的 GitHub 仓库名")
    homepage = args.homepage or (f"https://github.com/{repo}" if repo else "")
    readme = args.readme_url or (homepage + "#readme" if repo else homepage)
    mode = args.mode or ("auto" if repo else "external")
    result = dict(name=args.name, category=args.category,
                  summary=args.summary, readmeSnapshot=args.snapshot,
                  homepage=homepage, readmeUrl=readme)
    if repo:
        result["repository"] = repo
    if mode != "auto":
        result["downloadMode"] = mode
    if args.risk:
        result["risk"] = args.risk
    if args.notice:
        result["notice"] = args.notice
    if args.apk_regex:
        result["preferredApkRegex"] = args.apk_regex
    # Keep categories together without reordering existing entries.
    last = next((i for i in reversed(range(len(data)))
                 if data[i]["category"] == args.category), None)
    if last is None:
        # New categories are appended; the UI groups and orders independently.
        # Avoid shuffling unrelated metadata lines on each catalog change.
        last = len(data) - 1
    data.insert(last + 1, result)
    validate(data)
    return data

def selftest():
    sample = [{
        "name": "Example", "category": "网络与连接",
        "summary": "Example summary", "readmeSnapshot": "Example README snapshot",
        "homepage": "https://github.com/owner/example",
        "readmeUrl": "https://github.com/owner/example#readme",
        "repository": "owner/example",
    }]
    args = argparse.Namespace(name="TestProject", category="网络与连接",
        summary="测试简介", snapshot="README 快照",
        repo="owner/test", homepage=None, readme_url=None,
        mode="auto", risk="", notice="", apk_regex="")
    add(sample, args)
    assert validate(sample) == 2
    assert [p["name"] for p in sample] == ["Example", "TestProject"]
    for invalid in [
        lambda x: x[1].update(homepage="http://example.org"),
        lambda x: x[1].update(name="Example"),
        lambda x: x[1].update(risk="high"),
        lambda x: x[1].update(repository="../invalid"),
    ]:
        data = json.loads(json.dumps(sample, ensure_ascii=False))
        invalid(data)
        try:
            validate(data)
        except ValueError:
            pass
        else:
            raise AssertionError("Invalid metadata unexpectedly accepted")
    with tempfile.TemporaryDirectory() as temporary:
        path = Path(temporary) / "apps.json"
        write(path, sample)
        assert read(path) == sample
    print("PASS: CLI add/validate/selftest and invalid metadata checks")

def main():
    parser = argparse.ArgumentParser(description="Edit the online Android application catalog")
    parser.add_argument("--file", type=Path, default=DEFAULT, help="Target catalog JSON")
    command = parser.add_subparsers(dest="command", required=True)
    command.add_parser("validate", help="Validate catalog; no Android build required")
    command.add_parser("selftest", help="Run safe offline catalog editor tests")
    cmd = command.add_parser("add", help="Add one app; changes only catalog/apps.json")
    cmd.add_argument("--name", required=True)
    cmd.add_argument("--category", required=True)
    cmd.add_argument("--summary", required=True)
    cmd.add_argument("--snapshot", required=True, help="Short editorial README snapshot")
    cmd.add_argument("--repo", help="Official GitHub owner/repo")
    cmd.add_argument("--homepage", help="Official HTTPS home page")
    cmd.add_argument("--readme-url", help="Official HTTPS README URL")
    cmd.add_argument("--mode", choices=["auto","external"])
    cmd.add_argument("--risk", choices=["","caution","high"], default="")
    cmd.add_argument("--notice", default="")
    cmd.add_argument("--apk-regex", default="", help="Exact official APK asset filename regex")
    args = parser.parse_args()

    if args.command == "selftest":
        selftest()
        return
    apps = read(args.file)
    if args.command == "add":
        apps = add(apps, args)
        write(args.file, apps)
        print(f"已添加 {args.name}。只需提交 {args.file}，无需重新编译 APK。")
    else:
        count = validate(apps)
        print(f"PASS: {count} 个应用；{len(set(a['category'] for a in apps))} 个分类")
if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, AssertionError, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        sys.exit(1)
