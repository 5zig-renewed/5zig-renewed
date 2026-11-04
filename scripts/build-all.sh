#!/usr/bin/env bash
# Builds every project in the repo serially with a small heap, then prints a matrix of jars.
# Serial on purpose: each Loom/NeoForm build is 1-2 GB and this box has 16 GB total.
#
#   scripts/build-all.sh [--only <substr>]
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ONLY=""
[ "${1:-}" = "--only" ] && ONLY="${2:-}"

G810="$HOME/work/tools/gradle-8.10.2/bin/gradle"
G814="$HOME/work/tools/gradle-8.14.3/bin/gradle"
G98="$HOME/.tools/gradle/gradle-9.8.2/bin/gradle"

# project:gradle:jdk  — the combination each era needs (see docs/porting-playbook.md)
run_project() {
    local dir="$1" gradle="$2" jdk="$3" label="$4"
    [ -f "$dir/build.gradle" ] || return 0
    if [ -n "$ONLY" ] && [[ "$label" != *"$ONLY"* ]]; then return 0; fi
    printf '\n===== %s (%s, %s)\n' "$label" "$(basename "$gradle")" "$(basename "$jdk")"
    if ( cd "$dir" && JAVA_HOME="$jdk" "$gradle" build --no-daemon -q \
            -Dorg.gradle.jvmargs=-Xmx1200m > "/tmp/build-${label}.log" 2>&1 ); then
        echo "  OK"
    else
        echo "  FAILED — tail of /tmp/build-${label}.log:"
        grep -E "error:|FAILURE|Could not" "/tmp/build-${label}.log" | head -6 | cut -c1-160
    fi
}

matrices=()
collect() {
    local dir="$1" label="$2"
    for jar in "$dir"/build/libs/*.jar; do
        [ -f "$jar" ] || continue
        case "$jar" in *-sources.jar|*-dev.jar|*-slim.jar) continue;; esac
        matrices+=("$(printf '%-28s %10s  %s' "$label" "$(stat -c%s "$jar")" "${jar#"$ROOT"/}")")
    done
}

JDK17=/usr/lib/jvm/java-17-openjdk
JDK21=/usr/lib/jvm/java-21-openjdk
JDK25=/usr/lib/jvm/java-25-openjdk
JDK27=/usr/lib/jvm/java-27-openjdk

run_project "$ROOT"         "$G810" "$JDK17" "1.20.1-fabric"
run_project "$ROOT/forge"   "$G810" "$JDK17" "1.20.1-forge"
run_project "$ROOT/neoforge" "$G810" "$JDK17" "1.20.1-neoforge"

for proj in "$ROOT"/versions/*/; do
    [ -d "$proj" ] || continue
    name="$(basename "$proj")"
    case "$name" in
        # 26.x: Loom 1.19.3 + Gradle 9.8 + JDK 25, unobfuscated game jars
        26.*)      run_project "$proj" "$G98"  "$JDK25" "$name" ;;
        # E9 second half (1.21.5-1.21.11): Loom 1.13.4 needs Gradle 8.14
        1.21.5*|1.21.6*|1.21.7*|1.21.8*|1.21.9*|1.21.10*|1.21.11*)
                   run_project "$proj" "$G814" "$JDK21" "$name" ;;
        # E5 tail (1.20.2-1.20.4): Java 17 era
        1.20.2*|1.20.4*)
                   run_project "$proj" "$G810" "$JDK17" "$name" ;;
        *)         run_project "$proj" "$G810" "$JDK21" "$name" ;;
    esac
done

# standalone NeoForge projects (MDG): 1.21.x -> Gradle 8.14/JDK 21, 26.x -> 9.8/JDK 25
for proj in "$ROOT"/neoforge-*/; do
    [ -d "$proj" ] || continue
    name="$(basename "$proj")"
    case "$name" in
        26.*)  run_project "$proj" "$G98"  "$JDK25" "$name" ;;
        *)     run_project "$proj" "$G814" "$JDK21" "$name" ;;
    esac
done

echo
echo "================ jars ================"
collect "$ROOT" "1.20.1-fabric"
collect "$ROOT/forge" "1.20.1-forge"
collect "$ROOT/neoforge" "1.20.1-neoforge"
for proj in "$ROOT"/versions/*/ "$ROOT"/neoforge-*/; do
    [ -d "$proj" ] || continue
    collect "$proj" "$(basename "$proj")"
done
printf '%s\n' "${matrices[@]:-<none>}"
