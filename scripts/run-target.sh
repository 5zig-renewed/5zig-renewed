#!/usr/bin/env bash
# Run the deep-test battery for one target and append a machine-generated record.
#
#   run-target.sh <project-dir> <label> <display> <gradle-bin> <java-home> [world-timeout]
#
# Appends one JSON object per target to docs/deep-test-log.json, so the scorecard is
# generated from what actually happened rather than typed by hand.
set -u
PROJ="$1"; LABEL="$2"; DISP="$3"; GRADLE="$4"; JAVA="$5"; TMO="${6:-420}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG="/tmp/dt-${LABEL}.log"
OUT="/tmp/dt-${LABEL}-battery.txt"

printf 'onboardAccessibility:false\nguiScale:2\n' > "$PROJ/run/options.txt" 2>/dev/null
rm -f "$PROJ"/run/crash-reports/*.txt 2>/dev/null

( cd "$PROJ" && JAVA_HOME="$JAVA" "$GRADLE" build --no-daemon -x test ) > "/tmp/b-${LABEL}.log" 2>&1
BUILD=$?

"$ROOT/scripts/deep-test-target.sh" "$PROJ" "$DISP" "$GRADLE" "$JAVA" "$LABEL" "$TMO" > "$OUT" 2>&1
BATTRY=$?

python3 - "$ROOT" "$PROJ" "$LABEL" "$BUILD" "$BATTRY" "$LOG" "$OUT" <<'PY'
import json, os, sys
root, proj, label, build, battry, log, out = sys.argv[1:8]
shot = f"/tmp/dt-{label}"
def diff(a, b, thresh):
    pa, pb = f"{shot}-{a}.png", f"{shot}-{b}.png"
    if not (os.path.exists(pa) and os.path.exists(pb)):
        return None, "missing screenshot"
    from PIL import Image, ImageChops
    A = Image.open(pa).convert("L"); B = Image.open(pb).convert("L")
    if A.size != B.size:
        return None, "size mismatch"
    h = ImageChops.difference(A, B).histogram()
    frac = sum(h[12:]) / (A.size[0] * A.size[1])
    return round(frac, 4), ("ok" if frac >= thresh else "FAIL")
menu, menu_s = diff("before", "menu", 0.10)
coords, coords_s = diff("back", "coords", 0.06)
crashes = len([f for f in os.listdir(f"{proj}/run/crash-reports") if f.endswith(".txt")]) \
    if os.path.isdir(f"{proj}/run/crash-reports") else 0
txt = open(log, errors="ignore").read() if os.path.exists(log) else ""
rec = {
    "target": label, "project": os.path.basename(proj),
    "build": int(build), "battery": int(battry),
    "joined": txt.count("joined the game"), "crashes": crashes,
    "menu_diff": menu, "menu": menu_s,
    "coords_diff": coords, "coords": coords_s,
    "battery_said": [l.strip() for l in open(out, errors="ignore") if "FAIL" in l],
}
rec["verdict"] = "PASS" if (rec["build"] == 0 and rec["joined"] > 0 and crashes == 0
                            and menu_s == "ok" and coords_s == "ok") else "CHECK"
path = f"{root}/docs/deep-test-log.json"
rows = json.load(open(path)) if os.path.exists(path) and os.path.getsize(path) else []
rows = [r for r in rows if r.get("target") != label] + [rec]
json.dump(rows, open(path, "w"), indent=1)
print(f"{rec['verdict']:5} {label}: build={rec['build']} joined={rec['joined']} crashes={crashes} "
      f"menu={menu_s}({menu}) coords={coords_s}({coords})")
for l in rec["battery_said"]:
    print("   ", l)
PY
