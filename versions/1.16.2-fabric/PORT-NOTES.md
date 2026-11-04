# 1.16.2 Fabric port notes

Branched from `platform/1.16.5` (the client-verified base of this repository) and reconciled against
the decompiled 1.16.2 client. Same Java 8 target, same immediate-mode rendering backend; the delta is
small but not zero, and one of the deltas is invisible to `javac` unless you already know it is there.

| Piece | Version |
|---|---|
| Minecraft | 1.16.2 (Mojang official mappings, `world_version` 2578, protocol 751) |
| Fabric Loom | 1.7.4 |
| Gradle | 8.10.2, JDK 17 (compiling with `--release 8`) |
| Fabric Loader | 0.14.24 |
| Fabric API | 0.42.0+1.16 |
| Mixin compatibility level | `JAVA_8` |
| Output class files | major version **52** (Java 8) |

```
cd versions/1.16.2-fabric
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ~/work/tools/gradle-8.10.2/bin/gradle --no-daemon build
```

Jar: `build/libs/5zig-renewed-1.16.2-fabric-4.0.0-alpha.1.jar` — 18,791,733 bytes, 1,700 entries,
8 nested runtime jars (DiscordIPC, jacob, json, json-simple, junixsocket-common,
junixsocket-native-common, sqlite-jdbc, netty-codec-http).

## Decompiled reference

`mc-src/1.16.2/out` — 3,393 files, produced with

```
java -jar ~/.tools/decomp/vineflower-1.12.0.jar -dgs=1 --only=net/minecraft -Xmx2G \
  <versions/1.16.2-fabric/.gradle/loom-cache/minecraftMaven/.../minecraft-merged-*.jar> out
```

## What actually differs from 1.16.5

Measured, not guessed: the platform imports 103 `net.minecraft.*` types; their `javap -p` member sets
were diffed between the 1.16.5 and 1.16.2 named jars. **Seven classes differ, and exactly one of them
is used by this mod.**

| Class | 1.16.5 → 1.16.2 | Used here? |
|---|---|---|
| `EffectRenderingInventoryScreen` | `doRenderEffects` protected → **public** | yes — the HUD reads it, and it now compiles without the access widener |
| `SharedConstants` | `getProtocolVersion()` removed | no |
| `Minecraft` | social-interactions API added (`getPlayerSocialManager`, `SocialInteractionsService`) | no |
| `Options` | `keySocialInteractions`, `hideMatchedNames`, `joinedFirstServer` added | no |
| `Gui` | `guessChatUUID(Component)` added | no |
| `ClientPacketListener` | `getOnlinePlayerIds()` added | no |
| `Connection` | `setEncryptionKey(Cipher, Cipher)` → `setEncryptionKey(SecretKey)` | no |

So the *only* mandatory source change for 1.16.2 is rendering state that `javac` cannot see through
`net.minecraft.*` imports:

### Scissor: `RenderSystem.enableScissor` is 1.16.4+

`RenderSystem.enableScissor(int,int,int,int)` / `disableScissor()` — and the `GlStateManager`
companions `_enableScissorTest`, `_disableScissorTest`, `_scissorBox` and the `ScissorState` shadow
field they keep in sync — do not exist in 1.16.2. They were added in 1.16.4. Everything else in
`com.mojang.blaze3d` is identical between 1.16.2 and 1.16.5 (verified by `javap` on the merged jars;
the only other differences are lambda renumbering).

`GuiContext#enableScissor`/`disableScissor` therefore drive `GL11.glEnable(GL_SCISSOR_TEST)` +
`GL11.glScissor(...)` directly, with the same GUI-scale and bottom-left-origin conversion as before.
That is safe *because* 1.16.2 keeps no cached scissor state to invalidate — the reason the helper
moved into `RenderSystem` in 1.16.4 is precisely that `GlStateManager` started shadowing it.

### Access widener is a no-op here

`doRenderEffects` is public in 1.16.2 (and in 1.16/1.16.1/1.16.3/1.16.4; 1.16.5 demoted it to
`protected`). The `accessible field … doRenderEffects Z` entry in `loader/fabric-1.16.2/resources/5zig.accesswidener`
is therefore harmless but redundant. It is kept so the access-widener file, the `fabric.mod.json`
declaration and the `loom { accessWidenerPath }` wiring stay textually identical across the 1.16.x
line — and so a future 1.16.5-shaped feature can move back without touching build plumbing.

## Fabric API's mod id

`loader/fabric-1.16.2/resources/fabric.mod.json` declares `"fabric": "*"` in `depends`. On 1.16.x the
Fabric API mod id is `fabric`, not `fabric-api`; declaring the latter makes the loader refuse to start
the mod with a missing-dependency error even though the API is installed.

## Verification performed

- `gradle --no-daemon build` green (Loom 1.7.4, Gradle 8.10.2, JDK 17 → `--release 8`).
- Class files: major version **52**.
- Manifest carries `5zig-Version`, `Minecraft-Version: 1.16.2`, `5zig-Build`.
- `fabric.mod.json` in the jar: `depends.minecraft = ~1.16.2`, `depends.fabric = *`, 8 `jars` entries.
- Refmap present and complete (`5zig-renewed-1.16.2-fabric-refmap.json`, 15 mixin classes), and the
  shipped mixin config points at it. Access widener remapped to intermediary
  (`accessible field net/minecraft/class_485 field_2900 Z`).
- Mixin targets: 15 mixin classes, **37/37 injection targets resolve** against the 1.16.2 named jar
  (same 37 as the 1.16.5 baseline, i.e. no target silently disappeared).
- Link check (ASM): every class/method/field reference in the compiled platform resolved against the
  1.16.2 named jar + Fabric API 0.42 + libraries — **1,505 classes, 70,795 references, 0 unresolved**.

## Known gaps / not verified

- **No client run** (task rule), so nothing is pixel-verified; the port is verified by compile, link,
  mixin-target resolution and jar inspection only.
- The 1.16.2 `Font`/`Screen`/`ChatComponent` APIs are identical to 1.16.5, so the text and chat paths
  carry over without changes — this stops being true one version down (see `1.16.1`).
