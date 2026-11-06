#!/usr/bin/env python3
"""Static check that every mixin target method descriptor exists in the 1.20.2 jar."""
import re, subprocess, sys, os, glob, json

MIXIN_DIR = 'platform/1.20.2/eu/the5zig/mod/mixin'
SRC_ROOT = 'mc-src/1.20.2/out'
JAR = 'versions/1.20.2-fabric/.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-57017e2b25/1.20.2-loom.mappings.1_20_2.layered+hash.2198-v2/minecraft-merged-57017e2b25-1.20.2-loom.mappings.1_20_2.layered+hash.2198-v2.jar'
JAVAP = '/usr/lib/jvm/java-17-openjdk/bin/javap'

def target_of(path):
    src = open(path).read()
    m = re.search(r'@Mixin\(([\w.]+)\.class\)', src)
    if not m:
        return None, src
    simple = m.group(1)
    imp = re.search(r'import\s+([\w.]+\.%s);' % re.escape(simple), src)
    return (imp.group(1) if imp else simple), src

def methods(cls):
    out = subprocess.run([JAVAP, '-p', '-s', '-classpath', JAR, cls], capture_output=True, text=True)
    if out.returncode != 0:
        return None
    res = {}
    lines = out.stdout.splitlines()
    for i, line in enumerate(lines):
        mm = re.match(r'\s{2}.*?\b([\w$<>]+)\(.*\);\s*$', line)
        if mm and i + 1 < len(lines) and 'descriptor:' in lines[i+1]:
            desc = lines[i+1].split('descriptor:')[1].strip()
            res.setdefault(mm.group(1), set()).add(desc)
    return res

ok = bad = 0
for path in sorted(glob.glob(os.path.join(MIXIN_DIR, '*.java'))):
    cls, src = target_of(path)
    ms = methods(cls)
    name = os.path.basename(path)
    if ms is None:
        print(f'{name}: TARGET CLASS NOT FOUND {cls}')
        bad += 1
        continue
    for m in re.finditer(r'(?:method|value)\s*=\s*"([^"]+)"', src):
        spec = m.group(1)
        nm, _, desc = spec.partition('(')
        desc = '(' + desc
        if desc in ms.get(nm, set()):
            ok += 1
            print(f'OK   {name}: {spec}')
        else:
            bad += 1
            print(f'FAIL {name}: {spec}   (known: {sorted(ms.get(nm, []))})')
print(f'\n{ok} ok, {bad} failing')
sys.exit(1 if bad else 0)
