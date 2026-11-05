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
# The game dir is not always run/: NeoGradle projects on 1.20.x use runs/client.
if [ -d "$PROJ/run" ]; then GDIR="$PROJ/run"
elif [ -d "$PROJ/runs/client" ]; then GDIR="$PROJ/runs/client"
else GDIR="$PROJ/run"; fi
DISPLAY="$DISP" xdotool getdisplaygeometry >/dev/null 2>&1 || { echo "display $DISP is down" >&2; exit 2; }

pkill -f 'Knot[C]lient' 2>/dev/null
pkill -f 'net.minecraftforge.*launc[h]' 2>/dev/null
# Wait for the old client's window to actually disappear. A fixed `sleep 3` is not enough on the
# slow 1.18.x startups: the window is still mapped while the JVM exits, so `xdotool search` returns
# the DYING window and every later click/keypress is delivered to a dead client - which looks exactly
# like "the mod's hotkeys do nothing" (measured as a 0.0% frame diff on 1.18.2 three times).
for _w in $(seq 1 20); do
    [ -z "$(DISPLAY="$DISP" xdotool search --class minecraft 2>/dev/null)" ] && break
    sleep 1
done
rm -f "$GDIR"/saves/*/session.lock 2>/dev/null
rm -f "$GDIR"/crash-reports/*.txt 2>/dev/null

say "launching $MC on $DISP"
( cd "$PROJ" && DISPLAY="$DISP" ALSOFT_DRIVERS=null LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5 \
    JAVA_HOME="$JAVA" "$GRADLE" runClient --no-daemon > "$LOG" 2>&1 ) &
RUNNER=$!

# wait for a world
joined=0
failover=0
_i=0
for _i in $(seq 1 $((TIMEOUT / 5))); do
    if grep -aq 'joined the game' "$LOG" 2>/dev/null; then joined=1; break; fi
    # Versions before 1.20 do not implement --quickPlaySingleplayer (they log
    # "Completely ignored arguments"), so the client parks on the title screen. Walk it in
    # by keyboard once, after it has had time to finish loading: Enter opens Singleplayer,
    # Tab moves focus from the search box to the world list, Enter plays the selected world.
    # Time-based trigger, not log-based: 1.20+ honours --quickPlaySingleplayer and joins on its own,
    # while pre-1.20 versions either log "Completely ignored arguments" (1.19.x) or say nothing at all
    # (1.18.2 simply parks on the title screen). After ~100 s with no join, assume the menu is up and drive it.
    if [ "$failover" -eq 0 ] && [ "$_i" -ge 20 ]; then
        failover=1
        say "quickplay unsupported on this version - driving the title screen"
        # `Completely ignored arguments` is logged while parsing argv, long before the title screen
        # renders, so a single attempt is a race. Retry: the click is a no-op until the menu exists.
        # Nothing is focused on the title screen, so Enter alone does nothing - click the first menu
        # button just above centre, then Tab off the world list's search box and Enter to play.
        for _try in 1 2 3 4; do
            sleep 20
            grep -aq 'joined the game' "$LOG" 2>/dev/null && { joined=1; break; }
            GEO=$(DISPLAY="$DISP" xdotool getwindowgeometry "$(DISPLAY="$DISP" xdotool search --class minecraft | head -1)" 2>/dev/null)
            WX=$(echo "$GEO" | sed -n 's/.*Position: \([0-9]*\),\([0-9]*\).*/\1/p')
            WY=$(echo "$GEO" | sed -n 's/.*Position: \([0-9]*\),\([0-9]*\).*/\2/p')
            WW=$(echo "$GEO" | sed -n 's/.*Geometry: \([0-9]*\)x.*/\1/p')
            WH=$(echo "$GEO" | sed -n 's/.*Geometry: [0-9]*x\([0-9]*\).*/\1/p')
            : "${WX:=213}"; : "${WY:=160}"; : "${WW:=854}"; : "${WH:=480}"
            CX=$((WX + WW / 2)); CY=$((WY + WH * 49 / 100))
            DISPLAY="$DISP" xdotool mousemove "$CX" "$CY" click 1; sleep 7
            # The world list does not auto-select an entry on every version: with nothing selected
            # "Play Selected World" is greyed out and clicking it does precisely nothing (1.18.2).
            # Click the list, press Down to select the first world, then click Play (~31% across,
            # ~82% down). Skip the Play click if it is still disabled - a disabled button is silent.
            DISPLAY="$DISP" xdotool mousemove $((WX + WW / 2)) $((WY + WH * 50 / 100)) click 1; sleep 2
            DISPLAY="$DISP" xdotool key --clearmodifiers Down; sleep 2
            DISPLAY="$DISP" xdotool mousemove $((WX + WW * 31 / 100)) $((WY + WH * 82 / 100)) click 1
            sleep 3
            DISPLAY="$DISP" xdotool key --clearmodifiers Tab; sleep 1
            DISPLAY="$DISP" xdotool key --clearmodifiers Return
            say "drive attempt $_try sent"
        done
    fi
    sleep 5
done
if [ "$joined" -ne 1 ]; then
    say "FAIL boot: no world within ${TIMEOUT}s (see $LOG)"
    kill "$RUNNER" 2>/dev/null; pkill -f 'Knot[C]lient' 2>/dev/null
    exit 3
fi
# Wait for the world to finish loading, not just for the join line: the client prints
# "joined the game" while the terrain screen is still up, and a hotkey pressed then is dropped
# (1.18.x reproduced this twice; the same key works once the world is fully in).
sleep 45   # let the world load and the first frames settle

# T2 settings GUI — assert the screen really opened, not just that a key was sent
DISPLAY="$DISP" import -window root "${SHOT}-before.png" 2>/dev/null
DISPLAY="$DISP" xdotool keydown Shift_R; sleep 0.4; DISPLAY="$DISP" xdotool keyup Shift_R
# Software-rendered 1.17/1.18 need well over the old 5 s: the GUI can appear after the screenshot,
# which reads as "the screen did not open".
sleep 12
DISPLAY="$DISP" import -window root "${SHOT}-menu.png" 2>/dev/null
say "captured ${SHOT}-menu.png"
# The very first key press after joining is sometimes swallowed while chunks stream in (1.18.x:
# Shift_R lost, F12 straight after it worked). Retry the press once before declaring the screen dead.
if ! python3 "$(dirname "$0")/shot-diff.py" "${SHOT}-before.png" "${SHOT}-menu.png" 0.10 "settings GUI opened"; then
    say "retrying the settings hotkey"
    DISPLAY="$DISP" xdotool keydown Shift_R; sleep 0.6; DISPLAY="$DISP" xdotool keyup Shift_R
    sleep 6
    DISPLAY="$DISP" import -window root "${SHOT}-menu.png" 2>/dev/null
    python3 "$(dirname "$0")/shot-diff.py" "${SHOT}-before.png" "${SHOT}-menu.png" 0.10 "settings GUI opened" || fail=1
fi

# T3 ESC back to game
DISPLAY="$DISP" xdotool key --clearmodifiers Escape; sleep 3
DISPLAY="$DISP" import -window root "${SHOT}-back.png" 2>/dev/null

# T4 coordinates clipboard + SPACE
# On 1.18.x the single Escape above closes the 5zig screen *and* leaves the vanilla pause menu open,
# so F12 hits the menu instead of the game. Detect that case by a failed diff and retry once: one
# more Escape closes the pause menu, then F12 has a game to open on.
DISPLAY="$DISP" xdotool key --clearmodifiers F12; sleep 10
DISPLAY="$DISP" xdotool key --clearmodifiers space; sleep 4
DISPLAY="$DISP" import -window root "${SHOT}-coords.png" 2>/dev/null
say "captured ${SHOT}-coords.png"
# the clipboard dialog covers less of the frame than the settings menu
if ! python3 "$(dirname "$0")/shot-diff.py" "${SHOT}-back.png" "${SHOT}-coords.png" 0.06 "coordinates GUI opened"; then
    say "retrying: closing a leftover pause menu first"
    DISPLAY="$DISP" xdotool key --clearmodifiers Escape; sleep 3
    DISPLAY="$DISP" import -window root "${SHOT}-back.png" 2>/dev/null
    DISPLAY="$DISP" xdotool key --clearmodifiers F12; sleep 4
    DISPLAY="$DISP" xdotool key --clearmodifiers space; sleep 3
    DISPLAY="$DISP" import -window root "${SHOT}-coords.png" 2>/dev/null
    python3 "$(dirname "$0")/shot-diff.py" "${SHOT}-back.png" "${SHOT}-coords.png" 0.06 "coordinates GUI opened" || fail=1
fi

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
