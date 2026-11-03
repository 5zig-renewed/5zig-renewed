#!/usr/bin/env python3
"""Verify that every mixin method target in platform/26.2 exists in the 26.2 jar."""
import os, re, subprocess, sys

JAR = os.path.expanduser("~/.gradle/caches/fabric-loom/26.2/minecraft-merged.jar")
MIXIN_DIR = "/platform/26.2/eu/the5zig/mod/mixin"

# cache of javap descriptor sets per class
_cache = {}

def descriptors(class_name):
    if class_name in _cache:
        return _cache[class_name]
    out = subprocess.run(["javap", "-p", "-s", "-classpath", JAR, class_name],
                         capture_output=True, text=True).stdout
    descs = set()
    current = None
    for line in out.splitlines():
        line = line.strip()
        m = re.match(r"(?:public|protected|private|static|final|abstract|default|synchronized|native|strictfp|transient|volatile|\s)*.*?([\w$<>]+)\(([^)]*)\)", line)
        if line.startswith("descriptor:"):
            descs.add(line.split("descriptor:", 1)[1].strip())
    _cache[class_name] = descs
    return descs

problems = []
for name in sorted(os.listdir(MIXIN_DIR)):
    if not name.endswith(".java"):
        continue
    path = os.path.join(MIXIN_DIR, name)
    src = open(path).read()
    m = re.search(r"@Mixin\(([\w.$]+)\.class\)", src)
    if not m:
        problems.append(f"{name}: no @Mixin target parsed")
        continue
    target = m.group(1)
    if not target.startswith("net.minecraft."):
        im = re.search(r"import (net\.minecraft\.[\w.]+)\." + re.escape(target) + ";", src)
        if not im:
            problems.append(f"{name}: cannot resolve import for {target}")
            continue
        target = im.group(1) + "." + target
    descs = descriptors(target)
    if not descs:
        problems.append(f"{name}: javap could not read {target}")
        continue
    for mm in re.finditer(r'method\s*=\s*"([^"]+)"', src):
        spec = mm.group(1)
        parts = re.findall(r'L?([\w/$]+;|[A-Za-z0-9_$]+|[\[\]])', spec)
        # descriptors in mixin strings are 'name(desc)ret' (no leading L on the class, it's a member)
        dm = re.match(r"([\w$<>]+)(\([^)]*\).*)$", spec)
        if not dm:
            problems.append(f"{name}: unparsable method spec {spec}")
            continue
        meth, desc = dm.group(1), dm.group(2)
        if desc not in descs:
            problems.append(f"{name}: {target}#{meth}{desc} NOT FOUND")
    for mm in re.finditer(r'@(Inject|Redirect|ModifyVariable|ModifyArg|ModifyConstant)\b', src):
        pass

print("\n".join(problems) if problems else "all mixin targets resolve")
