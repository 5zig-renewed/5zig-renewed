#!/usr/bin/env python3
"""Static check: every @Inject/@Redirect/@ModifyVariable target in platform mixins must exist in 1.20."""
import os, re, subprocess, sys

ROOT = ''
MCJAR = ('versions/1.20-fabric/.gradle/loom-cache/minecraftMaven/net/minecraft/'
         'minecraft-merged-a55b08f8f9/1.20-loom.mappings.1_20.layered+hash.2198-v2/'
         'minecraft-merged-a55b08f8f9-1.20-loom.mappings.1_20.layered+hash.2198-v2.jar')

MIXIN_DIRS = [os.path.join(ROOT, 'platform/1.20/eu/the5zig/mod/mixin')]
EXTRA = [os.path.join(ROOT, 'common/eu/the5zig/mod/mixin')]

def collect(dirpath):
    return [os.path.join(dirpath, f) for f in os.listdir(dirpath) if f.endswith('.java')]

def imports(src):
    return dict((m.group(1).rsplit('.', 1)[-1], m.group(1).replace('.', '/')) for m in re.finditer(r'import\s+([\w.]+);', src))

cache = {}
def methods_of(classpath):
    if classpath in cache:
        return cache[classpath]
    out = subprocess.run(['javap', '-p', '-s', '-cp', MCJAR, classpath.replace('/', '.')],
                         capture_output=True, text=True).stdout
    names = []
    lines = out.splitlines()
    for i, line in enumerate(lines):
        m = re.match(r'^\s+(?:[\w<>\[\].?$]+ )*([\w$<>]+)\(', line)
        if m:
            desc = ''
            for j in range(i + 1, min(i + 4, len(lines))):
                d = re.match(r'\s+descriptor: (\S+)', lines[j])
                if d:
                    desc = d.group(1)
                    break
            names.append((m.group(1), desc))
    cache[classpath] = names
    return names

missing = []
checked = 0
for d in MIXIN_DIRS + EXTRA:
    if not os.path.isdir(d):
        continue
    for path in collect(d):
        src = open(path).read()
        tm = re.search(r'@Mixin\(\s*([\w.]+)\.class', src)
        if not tm:
            continue
        imps = imports(src)
        target = imps.get(tm.group(1))
        if not target:
            continue
        for m in re.finditer(r'method\s*=\s*(\{[^}]*\}|"[^"]*")', src):
            body = m.group(1)
            for spec in re.findall(r'"([^"]+)"', body):
                mm = re.match(r'([\w$<>]+)(\(.*\)\S+)?$', spec)
                if not mm:
                    continue
                name, desc = mm.group(1), mm.group(2)
                checked += 1
                have = methods_of(target)
                ok = any(n == name and (desc is None or dsc == desc) for n, dsc in have)
                if not ok:
                    missing.append((os.path.basename(path), target, spec))

print(f"checked {checked} mixin method targets across {len(cache)} classes")
if missing:
    print("MISSING TARGETS:")
    for f, t, s in missing:
        print(f"  {f}: {t}.{s}")
else:
    print("all targets resolve")
