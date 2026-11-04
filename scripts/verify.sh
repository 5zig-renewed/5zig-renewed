#!/usr/bin/env bash
# Verify a 5zig build in a real client, headlessly.
#
#   scripts/verify.sh <project-dir> <log> <seconds> [gradle] [java-home]
#
# Handles the things that kept biting us:
#  - stale KnotClient/Forge JVMs holding session.lock and covering the X display
#  - a dead Xvfb :99 (looks like "GLFW 0x1000E: Failed to open display")
#  - copied options.txt from another MC version (KeyMapping.setAll crash on 26.x)
#  - capturing the wrong window
set -uo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)

proj=${1:?project dir}
log=${2:?log path}
secs=${3:-240}
gradle=${4:-$HOME/work/tools/gradle-8.10.2/bin/gradle}
jhome=${5:-/usr/lib/jvm/java-17-openjdk}
shot=${SHOT:-/tmp/verify-$(basename "$proj").png}
# Each verification gets its own X display so a stray client from another run cannot
# sit on top of the window we screenshot.
VDISPLAY=${VDISPLAY:-:99}

# 1. no stale clients
pkill -f 'Knot[C]lient' 2>/dev/null
pkill -f 'net.minecraftforge.*launc[h]' 2>/dev/null
sleep 3

# 2. Xvfb must actually answer
num=${VDISPLAY#:}
if ! DISPLAY=$VDISPLAY xdotool getdisplaygeometry >/dev/null 2>&1; then
    rm -f "/tmp/.X${num}-lock" "/tmp/.X11-unix/X${num}"
    setsid Xvfb "$VDISPLAY" -screen 0 1280x800x24 +extension GLX </dev/null >"/tmp/xvfb${num}.log" 2>&1 &
    for _ in $(seq 1 20); do
        DISPLAY=$VDISPLAY xdotool getdisplaygeometry >/dev/null 2>&1 && break
        sleep 1
    done
fi

# 3. minimal options: never copy another version's keybinds
mkdir -p "$proj/run/saves"
printf 'onboardAccessibility:false\npauseOnLostFocus:false\nguiScale:2\n' > "$proj/run/options.txt"
rm -f "$proj"/run/saves/*/session.lock 2>/dev/null

# 3b. quick-play needs a world. Seeding from an OLDER version is fine (MC upgrades forward
#     silently); seeding from a newer one triggers the downgrade dialog, which blocks quick-play.
if [ -z "$(ls -A "$proj/run/saves" 2>/dev/null)" ]; then
    src=${WORLD_SRC:-}
    if [ -z "$src" ]; then
        for cand in $(ls -d "$ROOT"/versions/*/run/saves/'New World' 2>/dev/null | sort -V); do
            d=$(basename "$(dirname "$(dirname "$(dirname "$cand")")")")
            if [ "$(printf '%s\n%s\n' "$d" "$(basename "$proj")" | sort -V | head -1)" = "$d" ]; then src=$cand; fi
        done
    fi
    [ -n "$src" ] && cp -a "$src" "$proj/run/saves/" && echo "seeded world from $src"
fi

# 4. run
( cd "$proj" && DISPLAY=$VDISPLAY LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5 \
    JAVA_HOME="$jhome" "$gradle" runClient --no-daemon >"$log" 2>&1 ) &
pid=$!

joined=0
for _ in $(seq 1 "$secs"); do
    sleep 1
    if grep -qa "joined the game" "$log" 2>/dev/null; then joined=1; break; fi
    kill -0 $pid 2>/dev/null || break
done

sleep 12
DISPLAY=$VDISPLAY import -window root "$shot" 2>/dev/null

core=$(grep -ac 'Core initialised' "$log" 2>/dev/null || echo 0)
hud=$(grep -ac 'HUD rendering failed' "$log" 2>/dev/null || echo 0)
echo "core_init=$core joined=$joined hud_errors=$hud shot=$shot"

# 5. tear down
pkill -f 'Knot[C]lient' 2>/dev/null
pkill -f 'net.minecraftforge.*launc[h]' 2>/dev/null
sleep 2
