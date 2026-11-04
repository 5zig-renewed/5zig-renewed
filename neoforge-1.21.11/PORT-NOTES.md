# 1.21.11 NeoForge port notes

| Piece | Version |
|---|---|
| Gradle | 8.14.3 (`~/work/tools/gradle-8.14.3`, `--no-daemon`, `-Dorg.gradle.jvmargs=-Xmx1200m`) |
| JDK | 21 (`/usr/lib/jvm/java-21-openjdk`), target class file 65 |
| Plugin | ModDevGradle `net.neoforged.moddev` **2.0.148** |
| NeoForge | **21.11.48** (FancyModLoader 10.0.36, Mixin 0.8.7) |
| Minecraft | 1.21.11, Mojang official mappings |

Build:

```bash
cd neoforge-1.21.11
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  ~/work/tools/gradle-8.14.3/bin/gradle --no-daemon -Dorg.gradle.jvmargs=-Xmx1200m build
```

Jar: `neoforge-1.21.11/build/libs/5zig-renewed-1.21.11-neoforge-4.0.0-alpha.1.jar`
(20.2 MB, self-contained: twelve libraries nested under `META-INF/jarjar/`).

## What is different from the verified 1.21.4 project

| Item | 1.21.4 | 1.21.11 |
|---|---|---|
| NeoForge | 21.4.161 (FML 6) | 21.11.48 (FML 10) |
| `loaderVersion` | `[4,)` | `[10,)` |
| neoForge dependency range | `[21.4,22)` | `[21.11,22)` |
| Minecraft dependency range | `[1.21.4,1.21.5)` | `[1.21.11,1.21.12)` |
| Platform sources | `platform/1.21.4/eu` | `platform/1.21.11/eu` |
| Loader glue | `loader/neoforge-1.21.4/java` | `loader/neoforge-1.21.11/java` |
| Manifest `Minecraft-Version` | 1.21.4 | 1.21.11 |

### Code changes forced by the version bump

* **`ResourceLocation` → `Identifier`.** 1.21.9 renamed `net.minecraft.resources.ResourceLocation`
  to `net.minecraft.resources.Identifier` (and made it final; `platform/1.21.11` now wraps it by
  composition). `PluginMessages` uses `Identifier.parse(channel)` and
  `Identifier` in the `NetworkRegistry.hasChannel` gate — that overload exists with `Identifier` on
  21.11.
* **`PacketDistributor.sendToServer` is gone.** The client sender moved to
  `net.neoforged.neoforge.client.network.ClientPacketDistributor`, same as on 1.21.8.
* **No `additionalRuntimeClasspath`.** FML 10's mod-locator rework makes the mod's own class loader
  see the game classpath; MDG rejects the configuration outright
  ("there is no additional classpath anymore for Minecraft 1.21.11"). `implementation` alone is
  correct here — do not copy the double declaration from the 1.21.8 build.
* **12-file core overlay.** 1.21.11 ships authlib 7 and commons-lang3 3.19, so
  `versions/1.21.11-fabric/compat-src` supplies `AccountManager`, `NetworkManager`,
  `PacketEncryption`, `GuiHypixelStats`, `GuiNameHistory`, `ChatFilterManager`, `DataManager`,
  `ServerPing`, `OCCListener`, `ChatHighlighting`, `ServerTabImpl`, `ImageManager`; those paths are
  excluded from the copied core. Same set as the verified 1.21.11 Fabric build; no Fabric imports.
* **Apache HttpClient is nested** (1.21.5+ dropped it): `httpclient 4.5.14`, `httpcore 4.4.16`,
  `commons-codec 1.17.1`, `commons-logging 1.3.4` — twelve nested jars total.

### Mixin targets

No loader copy of any mixin was needed — 33/33 `@Inject`/`@ModifyVariable`/`@Shadow` targets named by
`platform/1.21.11` resolve with the exact JVM descriptor against
`build/moddev/artifacts/neoforge-21.11.48.jar`. Notes:

* `Gui#render(GuiGraphics, DeltaTracker)` and `Gui#renderItemHotbar(GuiGraphics, DeltaTracker)` are
  still present and `renderItemHotbar` is still invoked from `renderHotbar` (checked in bytecode).
* The platform's `@Mixin(Gui)` targets `tick()V` on 1.21.11. The patched jar has both the public
  `tick(boolean)` and the private no-arg `tick()V`; the no-arg one is invoked from `tick(boolean)`,
  so the TAIL injection is live.
* `ChatComponent#render` changed shape between the versions (1.21.8: `(GuiGraphics,IIIZ)V`;
  1.21.11: `(GuiGraphics, Font, IIIZZ)V`) — the platform already carries both, and the
  1.21.11 copy is the one that resolves here.
* `ChatScreen#keyPressed`/`mouseClicked` take the 1.21.9+ `KeyEvent`/`MouseButtonEvent` signatures
  in the platform, and those are what the 21.11 jar exposes.

## Verification performed

No client was launched (task rule). Static checks:

* `gradle build` → `BUILD SUCCESSFUL` against NeoForge 21.11.48's patched jar.
* 33/33 mixin targets resolve by name + descriptor against the 21.11.48 jar; the reverse check
  (1.21.8 platform vs this jar) reports the three known 1.21.9+ signature changes, so the checker
  is discriminating.
* `META-INF/neoforge.mods.toml` TOML-parses, `${version}` expanded, `[[mixins]]` names
  `mixins.the5zigmod.json`, ranges `[21.11,22)` / `[1.21.11,1.21.12)`.
* `mixins.the5zigmod.json`: `JAVA_21`, 13 classes, every class file present in the jar.
* 12 nested jars, each a valid zip; `metadata.json` lists exactly those 12; no duplicate entries;
  class file version 65 throughout; zero `net/fabricmc` references.

## Not verified without a client

* HUD bracket/action-bar mixins actually firing (only presence and call sites were checked).
* Keybinding registration and `Options` freeze timing.
* Payload registration/handshake with the 21.11 `PayloadRegistrar`, and `ClientPacketDistributor`
  sending under a real connection.
* Discord RPC (DiscordIPC + junixsocket) and sqlite initialisation from the nested jars.
* Whether FML 10 needs anything else for the mod's own class loader in a *packaged* install (the
  26.1.2 build, same FML generation, needs nothing).
