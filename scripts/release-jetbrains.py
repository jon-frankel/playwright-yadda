#!/usr/bin/env python3
"""Prepares versioning, changelog, and checks for JetBrains plugin releases."""

import argparse
import html
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request


def check_tag_exists(ver: str) -> bool:
    res = subprocess.run(
        ["git", "rev-parse", "-q", "--verify", f"refs/tags/jetbrains-v{ver}"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    return res.returncode == 0


def check_marketplace_version(xml_id: str, ver: str) -> None:
    lookup_url = f"https://plugins.jetbrains.com/api/plugins?xmlId={xml_id}&family=intellij"
    try:
        req = urllib.request.Request(lookup_url, headers={"Accept": "application/json"})
        with urllib.request.urlopen(req, timeout=15) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            numeric_id = data.get("id")
    except urllib.error.HTTPError as e:
        if e.code == 404:
            print(f"Plugin {xml_id} not yet listed on Marketplace (HTTP 404); skipping remote duplicate check.")
            return
        print(f"::warning::Marketplace plugin lookup returned HTTP {e.code}; continuing.")
        return
    except Exception as e:
        print(f"::warning::Could not reach JetBrains Marketplace API ({e}); continuing.")
        return

    if not numeric_id:
        return

    updates_url = f"https://plugins.jetbrains.com/api/plugins/{numeric_id}/updates?size=50"
    try:
        req = urllib.request.Request(updates_url, headers={"Accept": "application/json"})
        with urllib.request.urlopen(req, timeout=15) as resp:
            updates = json.loads(resp.read().decode("utf-8"))
            if isinstance(updates, list):
                for u in updates:
                    if u.get("version") == ver:
                        print(
                            f"::error::Version {ver} of {xml_id} (ID: {numeric_id}) is already published on JetBrains Marketplace.",
                            file=sys.stderr,
                        )
                        sys.exit(1)
    except Exception as e:
        print(f"::warning::Could not query updates for plugin {numeric_id} ({e}); continuing.")


def main() -> None:
    parser = argparse.ArgumentParser(description="Prepare JetBrains plugin release metadata.")
    parser.add_argument("--bump", choices=["auto", "patch", "minor", "major"], default="auto")
    parser.add_argument("--previous-tag", default="")
    parser.add_argument("--props-path", default="ide/jetbrains/gradle.properties")
    parser.add_argument("--output-dir", default="dist-jetbrains")
    parser.add_argument("--xml-id", default="io.github.jonfrankel.playwright-yadda")
    args = parser.parse_args()

    os.makedirs(args.output_dir, exist_ok=True)

    with open(args.props_path, "r", encoding="utf-8") as f:
        props = f.read()

    m = re.search(r"^pluginVersion=(\d+)\.(\d+)\.(\d+)\s*$", props, re.MULTILINE)
    if not m:
        print(f"::error::Could not parse pluginVersion=X.Y.Z in {args.props_path}", file=sys.stderr)
        sys.exit(1)

    maj, min_v, patch = map(int, m.groups())
    current_version = f"{maj}.{min_v}.{patch}"

    prev_tag = args.previous_tag.strip()
    if not prev_tag:
        prev_tag = (
            subprocess.check_output(
                ["git", "tag", "--list", "jetbrains-v*", "--sort=-v:refname"],
                text=True,
            )
            .splitlines()[0:1]
        )
        prev_tag = prev_tag[0].strip() if prev_tag else ""

    bump_kind = args.bump
    if bump_kind == "auto":
        if check_tag_exists(current_version):
            print(f"Tag jetbrains-v{current_version} already exists; bumping patch version.")
            bump_kind = "patch"
        else:
            bump_kind = "none"

    if bump_kind == "major":
        maj, min_v, patch = maj + 1, 0, 0
    elif bump_kind == "minor":
        min_v, patch = min_v + 1, 0
    elif bump_kind == "patch":
        patch += 1

    new_version = f"{maj}.{min_v}.{patch}"
    bumped = new_version != current_version

    if bumped:
        updated_props = re.sub(
            r"^pluginVersion=\d+\.\d+\.\d+\s*$",
            f"pluginVersion={new_version}",
            props,
            count=1,
            flags=re.MULTILINE,
        )
        with open(args.props_path, "w", encoding="utf-8") as f:
            f.write(updated_props)

    if check_tag_exists(new_version):
        print(f"::error::Git tag jetbrains-v{new_version} already exists.", file=sys.stderr)
        sys.exit(1)

    check_marketplace_version(args.xml_id, new_version)

    log_cmd = ["git", "log", "--no-merges", "--pretty=format:%s (%h)"]
    if prev_tag:
        log_cmd.append(f"{prev_tag}..HEAD")
    else:
        log_cmd.extend(["-n", "30"])
    log_cmd.extend(["--", "ide/jetbrains"])

    raw_lines = subprocess.check_output(log_cmd, text=True).strip().splitlines()
    items = [line.strip() for line in raw_lines if line.strip() and not line.startswith("release(ide):")]
    if not items:
        items = [f"Release Playwright Yadda JetBrains plugin v{new_version}."]

    html_items = "\n".join(f"  <li>{html.escape(item)}</li>" for item in items)
    change_notes_html = f"<ul>\n{html_items}\n</ul>\n"
    with open(os.path.join(args.output_dir, "change-notes.html"), "w", encoding="utf-8") as f:
        f.write(change_notes_html)

    md_items = "\n".join(f"- {item}" for item in items)
    release_notes_md = (
        f"## Playwright Yadda for JetBrains IDEs v{new_version}\n\n"
        f"### Changes\n\n{md_items}\n\n"
        f"### Installation\n\n"
        f"- **JetBrains Marketplace**: In WebStorm or another JetBrains IDE (2026.2+), open **Settings → Plugins → Marketplace** and search for **Playwright Yadda**.\n"
        f"- **From Disk**: Download `playwright-yadda-jetbrains-{new_version}-signed.zip` below and choose **Settings → Plugins → ⚙ → Install Plugin from Disk…**.\n"
    )
    with open(os.path.join(args.output_dir, "release-notes.md"), "w", encoding="utf-8") as f:
        f.write(release_notes_md)

    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with open(github_output, "a", encoding="utf-8") as out:
            out.write(f"version={new_version}\n")
            out.write(f"bumped={'true' if bumped else 'false'}\n")

    print(f"Candidate JetBrains plugin version: {new_version} (bumped={bumped})")


if __name__ == "__main__":
    main()
