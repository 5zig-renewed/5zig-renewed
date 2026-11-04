# 1.16 Fabric port notes

Branched from `platform/1.16.1` and reconciled against the decompiled 1.16 client.

**1.16 and 1.16.1 are the same game code.** Mojang shipped 1.16.1 the same day as 1.16 as a hotfix
release, and the only decompiled source file that differs between the two jars is
`net/minecraft/DetectedVersion.java` (the version constants). Measured over both trees: 3,449 `.java`
files each, exactly **1** differing, 0 added, 0 removed. So this port is a version-string port, not a
code port — `platform/1.16` is `platform/1.16.1` with the version numbers in comments and log strings
changed.

| Piece | Version |
|---|---|
| Minecraft | 1.16 (Mojang official mappings, `world_version` 2566, protocol 735) |
| Fabric Loom | 1.7.4 |
| Gradle | 8.10.2, JDK 17 (compiling with `--release 8`) |
| Fabric Loader | 0.14.24 |
| Fabric API | 0.34.7+1.16 |
| Mixin compatibility level | `JAVA_8` |
| Output class files | major version **52** (Java 8) |

```
cd versions/1.16-fabric
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ~/work/tools/gradle-8.10.2/bin/gradle --no-daemon build
```

Jar: `build/libs/5zig-renewed-1.16-fabric-4.0.0-alpha.1.jar` — 18,791,326 bytes, 8 nested runtime jars.

## Decompiled reference

`mc-src/1.16/out` — 3,449 files, vineflower 1.12.0, `-dgs=1 --only=net/minecraft`.
The merged jar is a different artifact from 1.16.1 (different size and hash), and its `version.json`
reports `"id": "1.16"`, `world_version 2566`, `protocol_version 735`; the decompiled code is identical
apart from `DetectedVersion`.

Everything in `versions/1.16.1-fabric/PORT-NOTES.md` about the 1.16.2 → 1.16.1 delta applies unchanged
here: no `FormattedCharSequence` (text is `FormattedText`), no `Screen#renderComponentTooltip`
(`renderTooltip`), no `ClientLevel#registryAccess()` (`Registry.BIOME`), instance
`GuiComponent#drawString`.

## Verification performed

- `gradle --no-daemon build` green (Loom 1.7.4, Gradle 8.10.2, JDK 17 → `--release 8`).
- Class files: major version **52**; manifest `Minecraft-Version: 1.16`.
- `fabric.mod.json` in the jar: `depends.minecraft = ~1.16`, `depends.fabric = *`, 8 nested jars.
- Refmap `5zig-renewed-1.16-fabric-refmap.json` lists all 15 mixin classes; access widener remapped to
  intermediary.
- Mixin targets: **37/37 resolve** against the 1.16 named jar.
- Link check (ASM): **1,505 classes, 70,795 references, 0 unresolved** against the 1.16 named jar +
  Fabric API 0.34.7 + libraries.

## Known gaps / not verified

- **No client run** (task rule), same as the other 1.16.x ports.
- 1.16.1 is a hotfix release and did not touch the client GUI/render code this platform uses: the two
  decompiled trees differ in exactly one file (`DetectedVersion`), so there is no client behaviour to
  reconcile beyond the version string. A mod that compares protocol numbers (735 vs 736) would care;
  this one does not, and `SharedConstants#getProtocolVersion()` does not exist in this era anyway.
