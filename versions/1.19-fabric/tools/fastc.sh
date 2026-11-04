#!/usr/bin/env bash
# Fast javac loop against the Loom compile classpath (no mixin AP, no remap).
# Usage: ./fastc.sh [extra javac args]
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
OUT="$HOME/.port/cache/scratch/out-1.19"
CP="$(cat "$ROOT/versions/1.19-fabric/compile-classpath.txt")"
rm -rf "$OUT"; mkdir -p "$OUT"
files=$(find "$ROOT/core" "$ROOT/common" "$ROOT/api" "$ROOT/platform/1.19/eu" "$ROOT/loader/fabric/java" "$ROOT/loader/fabric-1.19/java" -name '*.java' 2>/dev/null | grep -v 'eu/the5zig/mod/installer/')
echo "sources: $(echo "$files" | wc -l)"
JAVA_HOME=/usr/lib/jvm/java-17-openjdk /usr/lib/jvm/java-17-openjdk/bin/javac \
  -nowarn -Xmaxerrs 400 -d "$OUT" -cp "$CP" -encoding UTF-8 --release 17 $files "$@"
