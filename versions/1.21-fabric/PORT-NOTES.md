# 1.21 Fabric port notes

Era E7 (1.21–1.21.1). Built with `gradle-8.10.2` / JDK 21, `--no-daemon`, target Java 21.

| Piece | Version |
|---|---|
| Minecraft | 1.21 (Mojang official mappings) |
| Fabric Loom | 1.7.4 |
| Fabric Loader | 0.16.10 |
| Fabric API | 0.102.0+1.21 |
| Mixin compatibility level | `JAVA_21` |
| authlib (from the 1.21 version json) | 6.0.54 |

Build: `cd versions/1.21-fabric && JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  ~/work/tools/gradle-8.10.2/bin/gradle --no-daemon build`
Jar: `build/libs/5zig-renewed-1.21-fabric-4.0.0-alpha.1.jar` (self-contained: the five runtime
libraries are nested under `META-INF/jars/`, sqlite-jdbc's natives included).

Entrypoint: `eu.the5zig.mod.fabric.FiveZigFabric121` (registers the 5zig payload types before
delegating to the shared `FiveZigFabric`). 1.21 already decodes custom payloads into registered
types before any packet listener runs, so the bootstrap is required exactly as on 1.21.1.

## Decompiled reference

`mc-src/1.21/out` (vineflower 1.12.0, `--only=net/minecraft`, `-dgs=1`), from
`~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21-loom.mappings.1_21.layered+hash.2198-v2/`.

## Base

`platform/1.21` is a verbatim copy of `platform/1.21.1`; only comments/strings that named 1.21.1
were retargeted to 1.21, and the mangled `forceOpaque` javadoc/`@Override` block in `Variables` was
repaired. `loader/fabric-1.21` is `loader/fabric-1.21.1` with the entrypoint renamed and the
version strings bumped. No source-level port was needed.

## API breaks 1.21.1 → 1.21: none

Verified rather than assumed:

- `javap -p` member dumps of all **108 `net.minecraft.*` classes imported by the platform** are
  identical between the 1.21 and 1.21.1 merged named jars (0 added, 0 removed members). Spot checks
  of `GuiGraphics` (89 members), `ItemStack` (160) and `Minecraft` (362) are also identical, so the
  1.21.1 platform compiles unchanged against 1.21 — confirmed by a clean `gradle build`.
- All **36 mixin-targeted members** (35 `@Inject`/`@ModifyVariable` method targets + the
  `DisconnectedScreen#parent` `@Shadow` field) resolve with their exact descriptors in the 1.21
  named jar, from the same source that resolves 36/36 on 1.21.1.
- `Options#keyMappings` (read reflectively by `Keybinds`) still exists as `public final KeyMapping[]`.
- Every GUI sprite the platform blits exists in the 1.21 client jar: `widget/button`,
  `widget/button_disabled`, `widget/button_highlighted`, `widget/slider`, `widget/slider_handle`,
  `widget/slider_handle_highlighted`, `toast/system`, `hud/*`.
- authlib is still 6.0.54, so the `platform/1.21` copies of `AccountManager` and
  `PacketEncryption` (and the matching `sourceSets` excludes) stay required.

## Colour handling

The legacy-`0xRRGGBB` trap does **not** bite on this version: 1.21's `Font#adjustColor`
(`(colour & 0xFC000000) == 0 ? colour | 0xFF000000 : colour`) still restores the missing alpha for
every `drawString`/`drawCenteredString` call, unlike 1.21.9+ where it was removed. The
`forceOpaque` guard in `Variables#drawString` is kept (harmless, keeps the call site correct for a
future render path); `GuiHandle#drawRect` needs no guard because the core's `Gui.drawRect` colours
already carry an explicit alpha byte.

## Known gaps (inherited from the 1.21.1 fork, unchanged)

- `textures/gui/icons.png` (the 1.8-era sheet the core blits hunger/saturation icons from) does not
  exist in 1.21 either; the food/saturation overlays therefore show the missing-texture checker.
  The 1.21.4 platform remaps those six icons onto `hud/food_*` sprites — not backported here, to
  keep this port a pure era copy.
- Legacy 5zig plugin channels: only the three registered payload types can be sent/received.
- 5zig capes are resolved and uploaded but cannot be spliced into the immutable `PlayerSkin`.
- Enchantment damage bonus / damage protection terms are omitted from tooltips and the HUD.

## Verification performed

- `gradle --no-daemon clean build` succeeds; the jar contains remapped classes (intermediary
  references, no named `net/minecraft/client/...` leakage), `fabric.mod.json` (`minecraft: ~1.21`),
  the empty access widener, `mixins.the5zigmod.json`, the five nested jars and
  `5zig-renewed-1.21-fabric-refmap.json` with all 13 mixin classes.
- Manifest carries `5zig-Version: 4.0.0-alpha.1`, `Minecraft-Version: 1.21`, `5zig-Build: alpha`.
- Jar entry set is identical to the 1.21.1 jar apart from the entrypoint class and refmap filename.
- No client was launched (per task rules), so this is compile + static-target verification only.
