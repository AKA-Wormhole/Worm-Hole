#!/usr/bin/env python3
"""Rename WormHole / com.wormhole.browser to Holocore / holocore.browser.app."""
from __future__ import annotations

import os
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SKIP_DIRS = {".git", ".gradle", "build", ".idea", "node_modules"}
TEXT_EXTS = {
    ".kt", ".kts", ".java", ".xml", ".gradle", ".properties", ".pro",
    ".md", ".txt", ".json", ".yml", ".yaml", ".toml", ".cfg", ".ini",
    ".html", ".css", ".js", ".svg", ".gitignore", ".proguard",
}

def is_text(path: Path) -> bool:
    if path.suffix.lower() in TEXT_EXTS:
        return True
    if path.name in {"gradlew", "LICENSE", "README"}:
        return True
    return False

def rewrite_text(s: str) -> str:
    s = s.replace("com.wormhole.browser", "holocore.browser.app")
    s = s.replace("WormHole", "HoloCore")
    s = s.replace("Wormhole", "Holocore")
    s = s.replace("WORMHOLE", "HOLOCORE")
    s = s.replace("wormhole", "holocore")
    return s

def walk_files(root: Path):
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for name in filenames:
            yield Path(dirpath) / name

changed = 0
for path in walk_files(ROOT):
    if not is_text(path):
        continue
    raw = path.read_bytes()
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError:
        continue
    new = rewrite_text(text)
    if new != text:
        path.write_text(new, encoding="utf-8")
        changed += 1
        print("edit", path.relative_to(ROOT))

# Rename files that still have old names after content rewrite
renamed_files = 0
for path in list(walk_files(ROOT)):
    new_name = rewrite_text(path.name)
    if new_name != path.name:
        dest = path.with_name(new_name)
        path.rename(dest)
        renamed_files += 1
        print("rename-file", path.relative_to(ROOT), "->", dest.name)

def move_tree(src: Path, dest: Path):
    if not src.exists():
        return False
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists():
        # merge
        for item in src.iterdir():
            target = dest / item.name
            if item.is_dir():
                move_tree(item, target)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.move(str(item), str(target))
        shutil.rmtree(src, ignore_errors=True)
    else:
        shutil.move(str(src), str(dest))
    return True

moved = 0
for rel in (
    "app/src/main/java/com/wormhole/browser",
    "app/src/test/java/com/wormhole/browser",
    "app/src/androidTest/java/com/wormhole/browser",
):
    src = ROOT / rel
    dest = ROOT / rel.replace("com/wormhole/browser", "holocore/browser/app")
    if move_tree(src, dest):
        moved += 1
        print("move", rel, "->", dest.relative_to(ROOT))

# Clean leftover empty package dirs
for leftover in (
    ROOT / "app/src/main/java/com/wormhole",
    ROOT / "app/src/main/java/com",
    ROOT / "app/src/test/java/com/wormhole",
    ROOT / "app/src/test/java/com",
    ROOT / "app/src/androidTest/java/com/wormhole",
    ROOT / "app/src/androidTest/java/com",
):
    if leftover.exists() and leftover.is_dir() and not any(leftover.rglob("*")):
        leftover.rmdir()
        print("rmdir", leftover.relative_to(ROOT))
    elif leftover.exists() and leftover.is_dir():
        # remove if only empty children
        for dirpath, dirnames, filenames in os.walk(leftover, topdown=False):
            if not dirnames and not filenames:
                Path(dirpath).rmdir()
                print("rmdir", Path(dirpath).relative_to(ROOT))

print("---")
print("files_edited", changed)
print("files_renamed", renamed_files)
print("trees_moved", moved)
