#!/usr/bin/env python3
"""Generate the mod's language bundles (lang/<LANGVERSION>/language_<locale>.properties).

I18n loads them as a Java ResourceBundle from the classpath:
    ResourceBundle.getBundle("lang/44/language", locale)
so the layout below has to match eu.the5zig.mod.I18n + Version.LANGVERSION.

Sources (The 5zig Mod translations repository, Crowdin managed):
  * resources/lang/44/language_<locale>.properties  -- the complete, already
    converted bundles shipped by upstream (UTF-8, `key = value`). Copied through
    verbatim; only new keys are appended, so escape sequences stay byte-identical.
  * resources/assets/the5zigmod/lang/<locale>.lang  -- Minecraft-style KEY=value
    files carrying strings added after the last conversion (key bind labels).
    Their `the5zigmod.` namespace prefix is kept as an alias (KeybindingManager
    registers the prefixed ids) and also emitted without it.

Usage:
    scripts/generate-lang.py [--source <translations git dir or worktree>] [--out <dir>]

The source defaults to $TRANSLATIONS_GIT, then ../5zig-mirrors/translations.git.
The output directory defaults to <repo>/loader/resources, which both the Fabric
and the Forge source sets include as a resource root.
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
LANGVERSION = 44
NAMESPACE = "the5zigmod."


def read_source(source: Path, rel: str) -> str:
    """Read a file from a translations checkout or a bare mirror."""
    if (source / rel).is_file():
        return (source / rel).read_text(encoding="utf-8")
    return subprocess.run(
        ["git", f"--git-dir={source}", "show", f"HEAD:{rel}"],
        check=True, capture_output=True,
    ).stdout.decode("utf-8")


def list_source(source: Path, rel_dir: str, suffix: str) -> list[str]:
    """List file names in a source directory (bare mirror or worktree)."""
    if (source / rel_dir).is_dir():
        return sorted(p.name for p in (source / rel_dir).iterdir() if p.name.endswith(suffix))
    out = subprocess.run(
        ["git", f"--git-dir={source}", "ls-tree", "-r", "--name-only", "HEAD", rel_dir + "/"],
        check=True, capture_output=True,
    ).stdout.decode("utf-8")
    return sorted(
        line.rsplit("/", 1)[1]
        for line in out.splitlines()
        if line.endswith(suffix) and "/" in line
    )


def split_entry(line: str) -> tuple[str, str] | None:
    """Split a `key<sep>value` line, honouring backslash escapes in the key."""
    sep = re.search(r"(?<!\\)[=:]", line)
    if sep is None:
        return None
    key = line[: sep.start()].strip()
    value = line[sep.end():].strip()
    return (key, value) if key else None


def bundle_entries(text: str) -> list[tuple[str, str]]:
    """Entries of a .properties file with their raw (already escaped) value text."""
    entries: list[tuple[str, str]] = []
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line[0] in "#!":
            continue
        entry = split_entry(line)
        if entry is not None:
            entries.append(entry)
    return entries


def escape_value(value: str) -> str:
    """Escape a plain single-line string for a UTF-8 .properties file."""
    return (value.replace("\\", "\\\\").replace("\t", "\\t")
            .replace("\r", "\\r").replace("\n", "\\n"))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", default=os.environ.get("TRANSLATIONS_GIT",
                                                       str(REPO.parent / "5zig-mirrors" / "translations.git")))
    ap.add_argument("--out", default=str(REPO / "loader" / "resources"))
    args = ap.parse_args()

    source = Path(args.source)
    if not source.exists():
        print(f"translations source not found: {source}", file=sys.stderr)
        return 1

    props_dir = f"resources/lang/{LANGVERSION}"
    lang_dir = "resources/assets/the5zigmod/lang"

    locales = sorted(n[len("language_"):-len(".properties")]
                     for n in list_source(source, props_dir, ".properties"))
    if not locales:
        print("no property bundles found in source", file=sys.stderr)
        return 1

    # New strings per locale from the Minecraft-style .lang files.
    lang_files = {n[:-len(".lang")]: n for n in list_source(source, lang_dir, ".lang")}
    extra_by_locale: dict[str, list[tuple[str, str]]] = {}
    for locale in locales:
        name = lang_files.get(locale)
        extra_by_locale[locale] = [
            (key, escape_value(value))
            for key, value in (bundle_entries(read_source(source, f"{lang_dir}/{name}")) if name else [])
        ]

    out_dir = Path(args.out) / "lang" / str(LANGVERSION)
    out_dir.mkdir(parents=True, exist_ok=True)

    for locale in locales:
        text = read_source(source, f"{props_dir}/language_{locale}.properties")
        entries = bundle_entries(text)
        keys = {key for key, _ in entries}
        added = 0
        for key, escaped in extra_by_locale[locale]:
            bare = key[len(NAMESPACE):] if key.startswith(NAMESPACE) else key
            for candidate in (key, bare):
                if candidate and candidate not in keys:
                    entries.append((candidate, escaped))
                    keys.add(candidate)
                    added += 1
        body = "\n".join(f"{key} = {value}" for key, value in entries)
        (out_dir / f"language_{locale}.properties").write_text(body + "\n", encoding="utf-8")
        print(f"language_{locale}.properties: {len(entries)} keys (+{added} from .lang)")

    # ResourceBundle falls back to lang/<v>/language.properties when a locale is
    # missing; keep an English copy so an unknown locale never throws.
    (out_dir / "language.properties").write_text(
        (out_dir / "language_en_US.properties").read_text(encoding="utf-8"), encoding="utf-8")
    print(f"wrote {len(locales) + 1} bundles to {out_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
