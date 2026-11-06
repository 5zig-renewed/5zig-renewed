#!/usr/bin/env bash
# Runs ONE Minecraft client for a given project directory, headless, and captures evidence.
# The parent agent runs this serially — never in parallel — because each client is ~1.5 GB.
#
#   scripts/verify-client.sh <project-dir> <log-file> [timeout-seconds]
#
# Exit codes: 0 = reached a world, 2 = client never joined, 3 = client crashed.
set -uo pipefail

PROJECT="${1:?usage: verify-client.sh <project-dir> <log-file> [timeout]}"
LOG="${2:?usage: verify-client.sh <project-dir> <log-file> [timeout]}"
TIMEOUT="${3:-180}"
DISPLAY_NUM="${DISPLAY_NUM:-:99}"
SHOT="${SHOT:-${LOG%.log}.png}"

say() { printf '== %s\n' "$*"; }

# --- display -----------------------------------------------------------------
if ! [ -S "<captures>/.X11-unix/X${DISPLAY_NUM#:}" ]; then
    sudo -n rm -f "<captures>/.X${DISPLAY_NUM#:}-lock" 2>/dev/null
    nohup Xvfb "$DISPLAY_NUM" -screen 0 1280x800x24 +extension GLX +render -noreset \
        > "<captures>/xvfb${DISPLAY_NUM#:}.log" 2>&1 &
    sleep 3
fi

# --- make sure only one client exists ----------------------------------------
if pgrep -f '[n]et.minecraftforge|[K]notClient' >/dev/null; then
    say "killing a previous client"
    pkill -f '[n]et.minecraftforge' 2>/dev/null
    pkill -f '[K]notClient' 2>/dev/null
    sleep 5
fi

# a SIGKILLed client leaves session.lock behind and the world becomes unopenable
rm -f "$PROJECT"/run/saves/*/session.lock 2>/dev/null

# --- run ---------------------------------------------------------------------
say "starting client in $PROJECT"
GRADLE_BIN="${GRADLE_BIN:-$HOME/work/tools/gradle-8.10.2/bin/gradle}"
JAVA_HOME_OVERRIDE="${JAVA_HOME_OVERRIDE:-/usr/lib/jvm/java-17-openjdk}"
( cd "$PROJECT" && DISPLAY="$DISPLAY_NUM" LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5 \
    JAVA_HOME="$JAVA_HOME_OVERRIDE" \
    "$GRADLE_BIN" runClient --no-daemon > "$LOG" 2>&1 ) &
RUNNER=$!

deadline=$((SECONDS + TIMEOUT))
joined=0
while [ $SECONDS -lt $deadline ]; do
    if grep -aq 'joined the game' "$LOG" 2>/dev/null; then joined=1; break; fi
    if grep -aqE 'Game crashed|BUILD FAILED' "$LOG" 2>/dev/null; then break; fi
    sleep 5
done

sleep 12   # let a few frames render with the HUD
DISPLAY="$DISPLAY_NUM" import -window root "$SHOT" 2>/dev/null

if grep -aqE 'Game crashed|BUILD FAILED' "$LOG" 2>/dev/null; then
    say "CRASHED — see $LOG"; kill $RUNNER 2>/dev/null; exit 3
fi
if [ "$joined" -ne 1 ]; then
    say "client did not reach a world within ${TIMEOUT}s — see $LOG"; kill $RUNNER 2>/dev/null; exit 2
fi

say "in-world; screenshot at $SHOT"
say "mod log lines:"
grep -aE '\[5zig|5zig Renewed|HudFrame|Tick handler failed|HUD rendering failed' "$LOG" | tail -8 | cut -c1-160
kill $RUNNER 2>/dev/null
pkill -f '[K]notClient' 2>/dev/null
pkill -f '[n]et.minecraftforge' 2>/dev/null
exit 0
