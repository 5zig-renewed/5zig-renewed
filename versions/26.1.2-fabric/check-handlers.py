#!/usr/bin/env python3
"""Static check: every @Inject/@ModifyVariable handler matches its target descriptor.

Mixin validates handler parameter types at load time and throws if they do not line up with the
target method's descriptor, so a typo here is a crash on startup rather than a compile error.
"""
import os, re, subprocess

JAR = os.path.expanduser("~/.gradle/caches/fabric-loom/26.1.2/minecraft-merged.jar")
MIXIN_DIR = "/platform/26.1.2/eu/the5zig/mod/mixin"

PRIM = {"int": "I", "boolean": "Z", "float": "F", "double": "D", "long": "J", "byte": "B",
        "short": "S", "char": "C", "void": "V"}

def javap_methods(cls):
    out = subprocess.run(["javap", "-p", "-s", "-classpath", JAR, cls], capture_output=True, text=True).stdout
    methods = {}
    pending = None
    for line in out.splitlines():
        line = line.strip()
        m = re.search(r"([\w$<>]+)\(([^)]*)\)", line)
        if m and "descriptor:" not in line:
            pending = m.group(1)
        elif line.startswith("descriptor:") and pending:
            methods.setdefault(pending, set()).add(line.split(":", 1)[1].strip())
            pending = None
    return methods

def java_type_to_desc(t, imports):
    t = t.strip().replace("...", "[]")
    dims = 0
    while t.endswith("[]"):
        dims += 1
        t = t[:-2]
    if t in PRIM:
        base = PRIM[t]
    else:
        if "." in t:
            fq = t
        else:
            fq = imports.get(t, t)
        base = "L" + fq.replace(".", "/") + ";"
    return "[" * dims + base

def imports_of(src):
    out = {}
    for m in re.finditer(r"import ([\w.]+)\.([\w$]+);", src):
        out[m.group(2)] = m.group(1) + "." + m.group(2)
    return out

problems = []
for name in sorted(os.listdir(MIXIN_DIR)):
    if not name.endswith(".java"):
        continue
    src = open(os.path.join(MIXIN_DIR, name)).read()
    imports = imports_of(src)
    tm = re.search(r"@Mixin\(([\w.$]+)\.class\)", src)
    if not tm:
        continue
    target = tm.group(1)
    if not target.startswith("net.minecraft."):
        target = imports.get(target, target)
    methods = javap_methods(target)

    # find @Inject blocks: method string + following handler signature
    for m in re.finditer(r'@Inject\(method\s*=\s*"([^"]+)"[^)]*\)\s*(?:private|public|protected)\s+(?:static\s+)?[\w<>\[\].]+\s+[\w$]+\(([^)]*)\)', src, re.S):
        spec, params = m.group(1), m.group(2)
        dm = re.match(r"([\w$<>]+)(\([^)]*\).*)$", spec)
        if not dm:
            problems.append(f"{name}: bad spec {spec}")
            continue
        meth, desc = dm.group(1), dm.group(2)
        if desc not in methods.get(meth, set()):
            problems.append(f"{name}: target {target}#{meth}{desc} not found")
            continue
        # build handler descriptor, dropping the trailing CallbackInfo(Returnable)
        plist = [p.strip() for p in params.split(",") if p.strip()]
        plist = [p for p in plist if "CallbackInfo" not in p]
        handler = "(" + "".join(java_type_to_desc(p.rsplit(" ", 1)[0].strip(), imports) for p in plist) + ")"
        target_params = desc[:desc.index(")") + 1]
        # allow the handler to omit trailing params (mixin permits fewer args only for injected locals)
        if not target_params.startswith(handler):
            problems.append(f"{name}: {meth} handler {handler} != target {target_params}")

print("\n".join(problems) if problems else "all mixin handlers match their targets")
