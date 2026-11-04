# 1.16.1 Fabric port notes

Branched from `platform/1.16.2` and reconciled against the decompiled 1.16.1 client. This is the first
version in the 1.16 line where the **text model changes shape**, and that is the whole story of this
port: 1.16.2 introduced `FormattedCharSequence`, and 1.16.1 has no such type anywhere.

| Piece | Version |
|---|---|
| Minecraft | 1.16.1 (Mojang official mappings, `world_version` 2567, protocol 736) |
| Fabric Loom | 1.7.4 |
| Gradle | 8.10.2, JDK 17 (compiling with `--release 8`) |
| Fabric Loader | 0.14.24 |
| Fabric API | 0.34.7+1.16 (the 0.34 line, contemporary with 1.16/1.16.1) |
| Mixin compatibility level | `JAVA_8` |
| Output class files | major version **52** (Java 8) |

```
cd versions/1.16.1-fabric
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ~/work/tools/gradle-8.10.2/bin/gradle --no-daemon build
```

Jar: `build/libs/5zig-renewed-1.16.1-fabric-4.0.0-alpha.1.jar` — 18,791,336 bytes, 8 nested runtime jars.

## Decompiled reference

`mc-src/1.16.1/out` — 3,449 files (56 more than 1.16.2), vineflower 1.12.0,
`-dgs=1 --only=net/minecraft`.

## Size of the 1.16.2 → 1.16.1 delta

The two decompiled trees differ in **853 `.java` files**, and 1.16.2 contains **96 classes that do not
exist in 1.16.1** (the piglin/bastion/nether additions, `FormattedCharSequence`, the 1.16.2 worldgen
refactor). On the 102 `net.minecraft.*` types this platform imports, **42 have a different member
list**. That sounds worse than it is: most of it is private-member and method-body churn (Biome's
climate/spawn-settings rewrite, Entity's portal refactor, `ClientLevel`/`ClientPacketListener` tag
handling). Four of those changes are real call sites here.

### 1. Text: `FormattedCharSequence` does not exist

1.16.2 introduced the "reordering processor" wrapper and rewrote every text entry point around it;
1.16.1 renders `FormattedText` directly.

| 1.16.2 | 1.16.1 |
|---|---|
| `Font#draw`/`drawShadow(PoseStack, FormattedCharSequence, …)` | same methods take `FormattedText` |
| `Font#split(FormattedText, int)` → `List<FormattedCharSequence>` | → `List<FormattedText>` |
| `Component#getVisualOrderText()` | absent |
| `ComponentRenderUtils#wrapComponents` → `List<FormattedCharSequence>` | → `List<FormattedText>` |
| `EditBox#setFormatter(BiFunction<…, FormattedCharSequence>)` | formatter is a `String` |
| `ChatComponent` keeps `GuiMessage<FormattedCharSequence>` | `GuiMessage` is raw |

Platform changes (7 of the 55 files in `platform/1.16.2` differ by more than the version string:
`GuiContext`, `LegacyText`, `GuiChatLine`, `Gui2ndChat`, `Variables`, `MixinChatComponent`,
`MixinClientPacketListener`):

- `GuiChatLine.visual` is a `FormattedText` (the component itself for unwrapped lines);
  `getPlainText()` is `visual.getString()`.
- `Gui2ndChat` wraps into `List<FormattedText>` and `getStyleAt` walks runs with
  `FormattedText#visit`, consuming each run code point by code point so the width test lands on the
  same character as 1.16.2's `FormattedCharSequence#accept` would.
- `LegacyText#toLegacy` takes a `FormattedText` and serialises run by run (a run has a single style,
  so no per-codepoint splitting is needed).
- `GuiContext#drawString(Font, FormattedText, …)`, `drawCenteredString` uses the component directly,
  `drawWordWrap`/`wrap` iterate `List<FormattedText>`.
- `Variables#splitStringToWidth` builds `List<FormattedText>`.
- `MixinChatComponent` / `MixinClientPacketListener` drop `.getVisualOrderText()`.

### 2. `Screen#renderComponentTooltip` does not exist

1.16.1 only has `renderTooltip(PoseStack, List<? extends FormattedText>, int, int)` (plus the
`ItemStack` and single-`FormattedText` overloads); `renderComponentTooltip` is a 1.16.2 alias.
`GuiContext#renderComponentTooltip` calls `renderTooltip`.

### 3. `ClientLevel#registryAccess()` does not exist

1.16.1's `RegistryAccess` is a stub that only carries dimension types — it cannot hand out the biome
registry. `Variables#getBiome` uses the static `Registry.BIOME.getKey(biome)`, which is how 1.16.1
resolved biome ids (dynamic registries that reach the client arrive with 1.16.2).

### 4. `GuiComponent#drawString`/`drawCenteredString` are instance methods

They are `static` in 1.16.2 and instance methods in 1.16.1. The platform happens to call neither
directly (it draws through `Font`), so this compiles unchanged — recorded here because it is a trap
for anything that copies vanilla drawing code across the boundary.

Other public 1.16.2-only API this platform does not use, listed so the next feature does not walk into
it: `Options#getCameraType`/`setCameraType` (1.16.1 exposes the plain `thirdPersonView` int and has no
`fovEffectScale`/`screenEffectScale`), `Item#useOn` taking `net.minecraft.world.item.context.UseOnContext`
(1.16.1: `net.minecraft.world.item.UseOnContext`), `DisconnectedScreen(Screen, Component, Component)`
(1.16.1: `(Screen, String, Component)`), `AbstractContainerScreen#shouldCloseOnEsc` (1.16.1 only),
`ResourceLocation#validPathChar` (private in 1.16.1), `ClientLevel#getSharedSpawnAngle`,
`Entity#getRopeHoldPosition`, `LivingEntity#getBoundingBoxForCulling`.

## Fabric API 0.34.7+1.16

The 0.42.0+1.16 line is the 1.16.5-era bundle; 1.16/1.16.1 use 0.34.7+1.16, the last of the 0.34 line.
Both aggregate jars declare the same legacy Minecraft range (`~1.16.2-alpha.20.28.a`, Fabric's
snapshot-era version string that its predicate treats as the 1.16 train), so the swap is about the
module set, not a hard version gate — the platform only uses `fabric-key-binding-api-v1` and the
client lifecycle/tick events, and all of them exist in 0.34.7.

## Verification performed

- `gradle --no-daemon build` green (Loom 1.7.4, Gradle 8.10.2, JDK 17 → `--release 8`).
- Class files: major version **52**; manifest `Minecraft-Version: 1.16.1`.
- `fabric.mod.json` in the jar: `depends.minecraft = ~1.16.1`, `depends.fabric = *`, 8 nested jars.
- Refmap `5zig-renewed-1.16.1-fabric-refmap.json` lists all 15 mixin classes and is referenced by the
  shipped mixin config; access widener remapped to intermediary.
- Mixin targets: **37/37 resolve** against the 1.16.1 named jar.
- Link check (ASM): **1,505 classes, 70,795 references, 0 unresolved** against the 1.16.1 named jar +
  Fabric API 0.34.7 + libraries.

## Known gaps / not verified

- **No client run** (task rule). In particular the hand-written `FormattedText` style walk in
  `Gui2ndChat#getStyleAt` and the run-based `LegacyText#toLegacy` are exercised only by compilation,
  not by hovering over chat text in a live client.
- `Registry.BIOME.getKey(biome)` resolves vanilla biomes exactly; a biome registered by another mod
  through the (1.16.1) static registry also resolves, but one injected into a per-level dynamic
  registry would not — 1.16.1 has no such mechanism for biomes, so this is the platform limit rather
  than a regression.
