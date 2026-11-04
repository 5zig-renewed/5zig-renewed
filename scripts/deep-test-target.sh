#!/usr/bin/env bash
# deep-test-target.sh <project-dir> <display> <gradle> <java-home> <mc-version> [timeout]
#
# Runs the T0-T5 battery against one target and leaves screenshots in /tmp/dt-<mc>-*.png:
#   boot -> settings GUI (RSHIFT) -> ESC -> Coordinates Clipboard (F12) -> SPACE -> DELETE
# Exits non-zero if a step failed. The caller owns the build; this only launches and drives.
set -uo pipefail
PROJ="${1:?usage: deep-test-target.sh <project-dir> <display> <gradle> <java-home> <mc-version> [timeout]}"
DISP="${2:?display}"
GRADLE="${3:?gradle}"
JAVA="${4:?java-home}"
MC="${5:?mc version}"
TIMEOUT="${6:-420}"
LOG="/tmp/dt-${MC}.log"
SHOT="/tmp/dt-${MC}"

say() { printf '  %s\n' "$*"; }
fail=0

[ -d "$PROJ" ] || { echo "no project dir $PROJ" >&2; exit 2; }
DISPLAY="$DISP" xdotool getdisplaygeometry >/dev/null 2>&1 || { echo "display $DISP is down" >&2; exit 2; }

pkill -f 'Knot[C]lient' 2>/dev/null
pkill -f 'net.minecraftforge.*launc[h]' 2>/dev/null
sleep 3
rm -f "$PROJ"/run/saves/*/session.lock 2>/dev/null
rm -f "$PROJ"/run/crash-reports/*.txt 2>/dev/null

say "launching $MC on $DISP"
( cd "$PROJ" && DISPLAY="$DISP" ALSOFT_DRIVERS=null LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5 \
    JAVA_HOME="$JAVA" "$GRADLE" runClient --no-daemon > "$LOG" 2>&1 ) &
RUNNER=$!

# wait for a world
joined=0
for _ in $(seq 1 $((TIMEOUT / 5))); do
    if grep -aq 'joined the game' "$LOG" 2>/dev/null; then joined=1; break; fi
    sleep 5
done
if [ "$joined" -ne 1 ]; then
    say "FAIL boot: no world within ${TIMEOUT}s (see $LOG)"
    kill "$RUNNER" 2>/dev/null; pkill -f 'Knot[C]lient' 2>/dev/null
    exit 3
fi
sleep 12   # let the first frames settle

# T2 settings GUI — assert the screen really opened, not just that a key was sent
DISPLAY="$DISP" import -window root "${SHOT}-before.png" 2>/dev/null
DISPLAY="$DISP" xdotool keydown Shift_R; sleep 0.4; DISPLAY="$DISP" xdotool keyup Shift_R
sleep 5
DISPLAY="$DISP" import -window root "${SHOT}-menu.png" 2>/dev/null
say "captured ${SHOT}-menu.png"
python3 "$(dirname "$0")/shot-diff.py" "${SHOT}-before.png" "${SHOT}-menu.png" 0.10 "settings GUI opened" || fail=1

# T3 ESC back to game
DISPLAY="$DISP" xdotool key --clearmodifiers Escape; sleep 3
DISPLAY="$DISP" import -window root "${SHOT}-back.png" 2>/dev/null

# T4 coordinates clipboard + SPACE
DISPLAY="$DISP" xdotool key --clearmodifiers F12; sleep 4
DISPLAY="$DISP" xdotool key --clearmodifiers space; sleep 3
DISPLAY="$DISP" import -window root "${SHOT}-coords.png" 2>/dev/null
say "captured ${SHOT}-coords.png"
python3 "$(dirname "$0")/shot-diff.py" "${SHOT}-back.png" "${SHOT}-coords.png" 0.10 "coordinates GUI opened" || fail=1

# T5 DELETE
DISPLAY="$DISP" xdotool key --clearmodifiers Delete; sleep 2
DISPLAY="$DISP" import -window root "${SHOT}-deleted.png" 2>/dev/null
say "captured ${SHOT}-deleted.png"
DISPLAY="$DISP" xdotool key --clearmodifiers Escape; sleep 1

# log gate
if [ -x "$(dirname "$0")/check-client-log.sh" ]; then
    "$(dirname "$0")/check-client-log.sh" "$LOG" || fail=1
else
    say "WARN check-client-log.sh not found, skipped"
fi

pkill -f 'Knot[C]lient' 2>/dev/null
say "screenshots: ${SHOT}-{menu,coords,deleted}.png · log: $LOG"
exit $fail
