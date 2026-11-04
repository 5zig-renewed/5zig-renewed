# 1.16.3 Fabric port notes

Oldest Minecraft target in the repository. Ported from the finished **1.16.4** platform (which is a
byte-identical copy of the client-verified 1.16.5 platform), then reconciled against 1.16.3's own
decompiled sources. **Java 8**, `PoseStack`, no `GuiGraphics`, immediate-mode GUI drawing.

| Piece | Version |
|---|---|
| Minecraft | 1.16.3 (Mojang official mappings) |
| Fabric Loom | 1.7.4 |
| Gradle | 8.10.2, JDK 17 (compiling with `--release 8`) |
| Fabric Loader | 0.14.24 |
| Fabric API | 0.42.0+1.16 |
| Mixin compatibility level | `JAVA_8` |
| Output class files | major version **52** (Java 8) |

Build:
```
cd versions/1.16.3-fabric
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ~/work/tools/gradle-8.10.2/bin/gradle --no-daemon build
```
Jar: `build/libs/5zig-renewed-1.16.3-fabric-4.0.0-alpha.1.jar` (18.8 MB, self-contained).

## Delta vs 1.16.4 / 1.16.5

Decompiled reference: `mc-src/1.16.3/out` — 3,393 Java files (1.16.4: 3,400;
1.16.5: 3,400). Against 1.16.5: 927 files differ, 56 of them `.java`; 8 files exist only in 1.16.5
(the whole Social Interactions feature plus its texture, `TextFilter`/`TextFilterClient`, and
`CryptException`).

1.16.4's additions over 1.16.3 that the mod does **not** use (verified: the 1.16.4 platform source
compiles unchanged against 1.16.3's classpath, and `LinkCheck` resolves every reference):

| 1.16.4-only API | 1.16.3 reality |
|---|---|
| `RenderSystem.enableScissor/disableScissor` | **absent** — see the fix below |
| `GlStateManager._enableScissorTest/_scissorBox` | absent (1.16.3 has no scissor helpers at all; vanilla 1.16.3 never scissor-clips a GUI) |
| `EffectRenderingInventoryScreen.doRenderEffects` | exists and is `public` (it is `protected` only in 1.16.5) |
| `AbstractSelectionList.setRenderBackground/setRenderTopAndBottom` | absent (the list always paints its background) |
| `StringDecomposer.getPlainText`, `Gui.guessChatUUID`, `Minecraft.getPlayerSocialManager/isBlocked`, `ClientPacketListener.getOnlinePlayerIds` | absent (social-interactions plumbing) |
| `Options.hideMatchedNames`, `Options.keySocialInteractions`, `BooleanOption(String, Component, …)` tooltip ctor | absent |
| `Connection.setEncryptionKey(Cipher, Cipher)` | `setEncryptionKey(SecretKey)` (2 Ciphers vs 1 key) |
| `Minecraft` ctor builds `YggdrasilAuthenticationService` with a client UUID + `SocialInteractionsService` | plain `YggdrasilAuthenticationService(proxy, UUID.randomUUID().toString())` |

`AbstractWidget#setMessage` also compares the `Component` instead of its `getString()` — irrelevant
to our widgets, which pass fresh components.

### The one source change: `GuiContext.enableScissor/disableScissor`

1.16.3 has no `RenderSystem.enableScissor`, and its `GlStateManager` exposes no scissor helpers
either — so `GuiContext` now issues the GL call directly:

```java
GL11.glEnable(GL11.GL_SCISSOR_TEST);
GL11.glScissor(x, windowHeight - bottom * scale, (right-left) * scale, (bottom-top) * scale);
...
GL11.glDisable(GL11.GL_SCISSOR_TEST);
```

This mirrors what `RenderSystem.enableScissor` does internally from 1.16.4 onwards
(`GlStateManager._enableScissorTest()` + `_scissorBox(x, y, w, h)`), including the bottom-left window
coordinate space, so `platform/1.16.4` and `platform/1.16.3` behave identically here. It is the
**only** file that differs between `platform/1.16.4` and `platform/1.16.3`.

### Everything else carried over unchanged

`platform/1.16.3` is otherwise identical to `platform/1.16.4` (55 files): rendering backend, all 15
mixins, `Variables` (162 `@Override`s on `IVariables`, 387 across the platform), keybinds, widgets,
server pinger, textures, battery. The access widener entry for `doRenderEffects` is redundant on
1.16.3 (the field is already `public`) but is kept so `loader/fabric-1.16.x` stays uniform; Loom
validates it and remaps it to `class_485 field_2900 Z`.

### Fabric API compatibility

`fabric-api:0.42.0+1.16` declares `minecraft: ~1.16.2-alpha.20.28.a`, which admits 1.16.3. The mod
dependency is on the **`fabric`** mod id (`"fabric": "*"`), not `fabric-api` — on 1.16.x the API is
published under `fabric`, and declaring `fabric-api` makes the client refuse to load the mod.

## Verification performed

- `gradle --no-daemon build` green after the one scissor fix (Loom 1.7.4, Gradle 8.10.2, JDK 17 →
  `--release 8`); all other platform sources compiled untouched.
- Jar: 18.79 MB, class major version **52**, manifest `5zig-Version` / `Minecraft-Version: 1.16.3` /
  `5zig-Build: alpha`, 8 nested runtime jars (DiscordIPC, jacob, json, json-simple,
  junixsocket-common, junixsocket-native-common, sqlite-jdbc, netty-codec-http).
- `fabric.mod.json` inside the jar: id `the5zigmod`, `depends.minecraft ~1.16.3`, `depends.fabric *`,
  8 `jars` entries.
- Access widener remapped to intermediary (`class_485 field_2900 Z`).
- `tools/check_mixins.py platform/1.16.3/eu/the5zig/mod/mixin <1.16.3 named jar>`:
  **37/37 injection targets present**; refmap lists 15 classes / 34 entries.
- `tools/LinkCheck.java` (ASM): **1505 classes, 70,795 references, 0 unresolved** against the
  1.16.3 named jar + Fabric API + libraries (the extra reference vs 1.16.4 is the `GL11` scissor
  pair).
- No 1.17+ symbols (`GuiGraphics`, `setShader`, `BuiltInRegistries`) in the remapped jar.

## Known gaps

- **No client run** (task rule), so nothing is pixel-verified. HUD, second chat, widget themes,
  capes and the mod menu are verified only by compile + link + mixin resolution.
- Same behavioural approximations as 1.16.5 (see `versions/1.16.5-fabric/PORT-NOTES.md`): FPS parsed
  from `fpsString`, reflective `setSession`, Windows battery returns `null`, item icons ignore the
  `PoseStack`, overlay drawn under the vanilla hotbar.
- Scissor clipping is raw `GL11` here; if 1.16.3 ever gains a `RenderSystem` wrapper this should be
  re-pointed at it, but the GL state calls are the same ones 1.16.4+ uses.
