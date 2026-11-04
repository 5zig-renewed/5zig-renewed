#!/usr/bin/env bash
# Fail a verification run on the *silent* defect classes we have actually hit.
#
# A run can reach a world, initialise the core and render the HUD while the mod is
# still broken: a missing runtime library kills a netty thread, a payload registration
# that only runs on connection aborts the join, a mixin that only applies to a class
# loaded post-join throws there. Those never appear in the HUD and never fail the build.
#
# Usage: check-client-log.sh <client.log>   -> exit 0 clean, exit 1 with a report
set -uo pipefail
LOG="${1:?usage: check-client-log.sh <client.log>}"
[ -r "$LOG" ] || { echo "check-client-log: cannot read $LOG" >&2; exit 2; }

fail=0
report() { printf '  FAIL  %-28s %s\n' "$1" "$2"; fail=1; }

# 1. Runtime classpath holes — a nested jar is missing from the built artifact.
if grep -aq 'NoClassDefFoundError\|ClassNotFoundException' "$LOG"; then
    report "missing class" "$(grep -ao 'NoClassDefFoundError: [^ ]*\|ClassNotFoundException: [^ ]*' "$LOG" | sort -u | head -3 | tr '\n' ' ')"
fi

# 2. Mixin that compiled and "resolved" statically but cannot bind at runtime.
if grep -aq 'InvalidInjectionException\|MixinApplyError\|MixinTransformerError' "$LOG"; then
    report "mixin failed" "$(grep -ao 'InvalidInjectionException: [^\\n]\{0,90\}\|MixinApplyError: [^\\n]\{0,60\}' "$LOG" | head -2 | tr '\n' ' ')"
fi

# 3. Per-loader networking APIs (NeoForge payload registrars, in particular).
if grep -aq 'missing client-side handlers\|RegistrationFailedException\|network namespace' "$LOG"; then
    report "payload registration" "$(grep -ao 'Some clientbound payloads[^"]*\|Try registering payload[^"]*' "$LOG" | head -2 | tr '\n' ' ')"
fi

# 4. Core services failing after init — the log line lives on a worker thread.
if grep -aq 'because "sql" is null\|(5zig Netty .*ERROR' "$LOG"; then
    report "core service thread" "$(grep -ao '(5zig Netty[^)]*)[A-Z]*[^\\n]\{0,60\}' "$LOG" | head -2 | tr '\n' ' ')"
fi

# 5. Mod refused by the loader (version ranges, missing deps).
if grep -aq 'Incompatible mods found\|requires any version\|mods that were not found' "$LOG"; then
    report "loader rejected mod" "$(grep -ao 'Mod .[^'"'"']*.[^\\n]\{0,70\}' "$LOG" | head -2 | tr '\n' ' ')"
fi

# 6. Native/audio failures that abort a headless client before the mod is exercised.
#    libflite is the narrator TTS voice: absent on a headless box by design, and the
#    mod is unaffected — never report it.
if grep -aq 'Failed to open OpenAL' "$LOG"; then
    report "headless environment" "Failed to open OpenAL device (set ALSOFT_DRIVERS=null)"
fi
if grep -a 'UnsatisfiedLinkError' "$LOG" | grep -avq 'flite'; then
    report "native library" "$(grep -a 'UnsatisfiedLinkError' "$LOG" | grep -av 'flite' | head -1 | cut -c1-90)"
fi

if [ "$fail" -eq 0 ]; then
    echo "  clean  no known silent-failure signature in $LOG"
    exit 0
fi
echo "  -> a world + HUD is NOT proof of a working mod; see $LOG"
exit 1
