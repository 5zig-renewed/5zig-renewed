#!/usr/bin/env python3
"""Rewrite non-semver versions inside nested (jar-in-jar) mod metadata.

Fabric Loader 0.17+ (Minecraft 26.3) parses every nested mod's `version` with the
extended semver scheme and *refuses to load the whole jar* when it is not semver:

    Mod com_github_5zig-reborn_discordipc uses the version master-SNAPSHOT
    which isn't compatible with Loader's extended version parsing scheme

Loom generates that metadata for a `include`d mod dependency, taking the Maven version
verbatim, so a JitPack `master-SNAPSHOT` dependency poisons the artifact. Older loaders
accepted it, which is why dev runs and every pre-26.3 build looked fine.

Usage: fix_nested_versions.py <mod.jar> [replacement-version]
"""
import json
import os
import shutil
import sys
import tempfile
import zipfile

SEMVER_FIX = {"master-SNAPSHOT": "1.0.0"}


def is_semver(v: str) -> bool:
    v = v.split("+")[0]
    core = v.split("-")[0]
    parts = core.split(".")
    if len(parts) != 3:
        return False
    return all(p.isdigit() for p in parts)


def fix_jar(path: str, fallback: str | None = None) -> int:
    changed = 0
    with zipfile.ZipFile(path) as zin:
        names = zin.namelist()
        nested = [n for n in names if n.startswith("META-INF/jars/") and n.endswith(".jar")]
        if not nested:
            return 0
        out = {}
        for name in nested:
            data = zin.read(name)
            with zipfile.ZipFile(__import__("io").BytesIO(data)) as nj:
                entries = [(i, nj.read(i.filename)) for i in nj.infolist()]
                meta_name = "fabric.mod.json"
                if not any(e[0].filename == meta_name for e in entries):
                    continue
                raw = next(d for i, d in entries if i.filename == meta_name)
                try:
                    meta = json.loads(raw)
                except Exception:
                    continue
                ver = str(meta.get("version", ""))
                if is_semver(ver):
                    continue
                new_ver = fallback or SEMVER_FIX.get(ver) or "1.0.0"
                meta["version"] = new_ver
                patched = json.dumps(meta, indent=2).encode()
                buf = __import__("io").BytesIO()
                with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as nout:
                    for info, d in entries:
                        nout.writestr(info, patched if info.filename == meta_name else d)
                out[name] = buf.getvalue()
                changed += 1
                print(f"  {name}: {ver} -> {new_ver}")
        if not changed:
            return 0
        fd, tmp = tempfile.mkstemp(suffix=".jar")
        os.close(fd)
        with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
            for info in zin.infolist():
                zout.writestr(info, out.get(info.filename, zin.read(info.filename)))
    shutil.move(tmp, path)
    return changed


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("usage: fix_nested_versions.py <mod.jar> [replacement-version]")
    n = fix_jar(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
    print(f"nested mod metadata patched: {n}")
