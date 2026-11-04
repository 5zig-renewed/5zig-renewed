#!/usr/bin/env python3
"""Assert that a screen actually opened by diffing two screenshots.

The deep-test battery used to pass a target purely because a world loaded and the log was clean —
which it did for a build where the settings hotkey was dead and the screenshot showed the vanilla
pause menu instead. A GUI opening changes a large, diffuse region of the frame; require that.

Usage: shot-diff.sh <before.png> <after.png> <min-changed-fraction> <label>
Exit 0 when the frame changed enough, 1 otherwise.
"""
import sys
from PIL import Image, ImageChops

before, after, thresh, label = sys.argv[1], sys.argv[2], float(sys.argv[3]), sys.argv[4]
try:
    a = Image.open(before).convert("L")
    b = Image.open(after).convert("L")
except Exception as exc:                      # missing screenshot is a failure, not a pass
    print(f"  FAIL  {label}: cannot read screenshots ({exc})")
    sys.exit(1)

if a.size != b.size:
    print(f"  FAIL  {label}: size changed {a.size} -> {b.size}")
    sys.exit(1)

diff = ImageChops.difference(a, b)
hist = diff.histogram()
total = a.size[0] * a.size[1]
# count pixels that changed by more than a small noise threshold
changed = sum(hist[12:])
fraction = changed / total
if fraction >= thresh:
    print(f"  ok    {label}: {fraction:.1%} of the frame changed")
    sys.exit(0)
print(f"  FAIL  {label}: only {fraction:.1%} changed (needed {thresh:.0%}) - the screen did not open")
sys.exit(1)
