#!/usr/bin/env python3
"""Verify a built 5zig NeoForge jar: manifest, TOML, mixin config, nested libs, class files,
and that every mixin injection target resolves in the NeoForge-patched Minecraft jar.

Usage: verify-neoforge-26x.py <mod.jar> <minecraft-patched.jar>
"""
import json, os, re, struct, subprocess, sys, zipfile

JAR, PATCHED = sys.argv[1], sys.argv[2]
MIXIN_CFG = 'mixins.the5zigmod.json'


def javap(args):
    return subprocess.run(['javap'] + args, capture_output=True, text=True)


def methods_of(jar, cls):
    """name+descriptor set for a class, e.g. {'tick(Z)V', 'setOverlayMessage(...)V'}."""
    out = javap(['-p', '-s', '-cp', jar, cls])
    if out.returncode != 0:
        return None
    lines = out.stdout.splitlines()
    res = set()
    for i, line in enumerate(lines):
        s = line.strip()
        m = re.match(r'^[\w$.\[\]<> ]*?([\w$<>]+)\([^)]*\);$', s)
        if m and i + 1 < len(lines) and lines[i + 1].strip().startswith('descriptor:'):
            desc = lines[i + 1].split(':', 1)[1].strip()
            res.add(m.group(1) + desc)
    return res


def super_of(jar, cls):
    out = javap(['-p', '-cp', jar, cls])
    if out.returncode != 0:
        return None
    m = re.search(r'^[\w.]*class [\w.$]+ extends ([\w.$]+)', out.stdout, re.M)
    return m.group(1) if m else None


def all_methods(jar, cls, depth=6):
    """Methods of a class plus its superclasses: mixin targets may be inherited."""
    res, cur = set(), cls
    for _ in range(depth):
        ms = methods_of(jar, cur)
        if ms is None:
            break
        res |= ms
        nxt = super_of(jar, cur)
        if not nxt or nxt == cur:
            break
        cur = nxt
    return res


def mixin_meta(jar, cls):
    """(target internal name, set of exact method specs) from javap -v of a mixin class."""
    out = javap(['-v', '-p', '-cp', jar, cls])
    text = out.stdout
    target = None
    m = re.search(r'org\.spongepowered\.asm\.mixin\.Mixin\(\s*value=\[class L([^;]+);', text)
    if not m:
        m = re.search(r'value=\[class L([^;]+);', text)
    if m:
        target = m.group(1)
    specs = set()
    for mm in re.finditer(r'method=\[?"([^"]+)"?\]?', text):
        specs.add(mm.group(1))
    return target, specs


def main():
    z = zipfile.ZipFile(JAR)
    names = set(z.namelist())
    problems = []
    print('== %s (%d entries, %.1f MB)' % (JAR, len(names), os.path.getsize(JAR) / 1e6))

    manifest = z.read('META-INF/MANIFEST.MF').decode()
    print('-- manifest')
    for key in ('5zig-Version', 'Minecraft-Version', '5zig-Build', 'Specification-Title'):
        m = re.search(r'^%s: (.*)$' % re.escape(key), manifest, re.M)
        print('   %-22s %s' % (key, m.group(1).strip() if m else '*** MISSING ***'))
        if not m:
            problems.append('manifest missing ' + key)
    if 'MixinConfigs' in manifest:
        problems.append('legacy MixinConfigs manifest attribute present')

    print('-- neoforge.mods.toml')
    toml = z.read('META-INF/neoforge.mods.toml').decode()
    for line in toml.splitlines():
        if re.match(r'\s*(modLoader|loaderVersion|modId|version|config)\s*=', line) or 'versionRange' in line:
            print('   ' + line.strip())
    if '${version}' in toml:
        problems.append('TOML has unexpanded ${version}')
    if '[[mixins]]' not in toml:
        problems.append('TOML has no [[mixins]] section')

    print('-- mixin config')
    cfg = json.loads(z.read(MIXIN_CFG))
    print('   compatibilityLevel=%s client=%d' % (cfg['compatibilityLevel'], len(cfg['client'])))
    if cfg['compatibilityLevel'] != 'JAVA_25':
        problems.append('mixin compatibilityLevel != JAVA_25')
    for cls in cfg['client']:
        if 'eu/the5zig/mod/mixin/%s.class' % cls not in names:
            problems.append('mixin class missing from jar: ' + cls)

    print('-- mixin targets vs %s' % os.path.basename(PATCHED))
    total = 0
    for cls in cfg['client']:
        target, specs = mixin_meta(JAR, 'eu.the5zig.mod.mixin.' + cls)
        if not target:
            problems.append('%s: no @Mixin target in class file' % cls)
            continue
        avail = all_methods(PATCHED, target.replace('/', '.'))
        if not avail:
            problems.append('%s: @Mixin target %s not in patched jar' % (cls, target))
            continue
        bad = [s for s in sorted(specs) if s not in avail]
        total += len(specs)
        for s in bad:
            problems.append('%s: unresolved target %s' % (cls, s))
        print('   %-26s -> %-22s %2d target(s) %s' % (
            cls, target.split('/')[-1], len(specs), 'FAIL' if bad else 'OK'))

    print('-- nested jars')
    jarjar = sorted(n for n in names if n.startswith('META-INF/jarjar/') and n.endswith('.jar'))
    meta = json.loads(z.read('META-INF/jarjar/metadata.json'))
    ids = [e['identifier']['artifact'] for e in meta['jars']]
    print('   %d nested, metadata lists %d: %s' % (len(jarjar), len(ids), ', '.join(ids)))
    if len(jarjar) != len(ids):
        problems.append('nested jar count %d != metadata %d' % (len(jarjar), len(ids)))
    for n in jarjar:
        try:
            with zipfile.ZipFile(z.open(n)) as inner:
                if inner.testzip() is not None:
                    problems.append('corrupt nested jar ' + n)
        except Exception as e:
            problems.append('nested jar %s unreadable: %s' % (n, e))

    print('-- class files')
    classes = [n for n in names if n.endswith('.class')]
    majors, fabric = set(), []
    for n in classes:
        d = z.read(n)
        majors.add(struct.unpack('>H', d[6:8])[0])
        if b'net/fabricmc' in d:
            fabric.append(n)
    print('   %d classes, class-file major %s' % (len(classes), sorted(majors)))
    if majors != {69}:
        problems.append('class file major versions: %s' % sorted(majors))
    if fabric:
        problems.append('fabric references in: %s' % fabric[:5])

    if 'eu/the5zig/mod/platform/PluginMessages.class' in names:
        problems.append('PluginMessages should be absent on 26.x')

    print()
    if problems:
        print('PROBLEMS (%d):' % len(problems))
        for p in problems:
            print('  ! ' + p)
        return 1
    print('ALL CHECKS PASSED (%d mixin targets)' % total)
    return 0


sys.exit(main())
