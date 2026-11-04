# 1.19.1 Fabric port notes

**Chat-signing** release of the 1.19 line, ported from the client-verified `platform/1.19.2` and
reconciled against `mc-src/1.19.1/out` (vineflower 1.12.0, `-dgs=1
--only=net/minecraft`, 4284 sources). Built with Gradle 8.10.2 / JDK 17, `--no-daemon`, Java 17
(class file 61).

| Piece | Version |
|---|---|
| Minecraft | 1.19.1 (Mojang official mappings) |
| Fabric Loom | 1.7.4 |
| Fabric Loader | 0.15.11 |
| Fabric API | 0.58.5+1.19.1 |
| Mixin compatibility level | `JAVA_17` |

Build: `cd versions/1.19.1-fabric && JAVA_HOME=/usr/lib/jvm/java-17-openjdk \
  ~/work/tools/gradle-8.10.2/bin/gradle --no-daemon build`
Jar: `build/libs/5zig-renewed-1.19.1-fabric-4.0.0-alpha.1.jar` (18.8 MB, self-contained)

Decompiled reference: `mc-src/1.19.1/out`, decompiled from
`versions/1.19.1-fabric/.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-e0e16841ac/1.19.1-loom.mappings.1_19_1.layered+hash.2198-v2/…jar`.

## Delta: 1.19.2 → 1.19.1 is source-compatible for this platform

1.19.1 and 1.19.2 are the same development cycle (1.19.1 added chat signing and reporting; 1.19.2
fixed the profile-key grace period). Decompiling both and diffing the trees gives **4284 classes on
both sides, 47 changed, 0 added, 0 removed** — and none of the changes touch an entry point this
platform uses. `platform/1.19.1` is therefore `platform/1.19.2` byte-for-byte, with the prose
references retargeted from 1.19.2 to 1.19.1.

The 47 changed classes, and why each is a no-op here:

| Change | Why it does not matter |
|---|---|
| `ProfilePublicKey.createValidated(..., EXPIRY_GRACE_PERIOD)`; `PlayerInfo` now catches `Exception` instead of `CryptException \| InsecurePublicKeyException` | Profile-key validation happens inside the client; the platform never validates keys. |
| `Minecraft#doWorldLoad` calls `profileKeyPairManager.preparePublicKey()` and reuses the future in `ServerboundHelloPacket` | World-load internals; the platform does not build the login packet. |
| `DamageSource(String)` and its `bypass*`/`setIsFire` mutators widened `protected` → `public` | Only widens access; `DamageSource.GENERIC` (the one member used, in the armour-enchantment path) is unchanged. |
| `ItemStack implements FabricItemStack` | Marker interface; every method the platform calls (`getItem`, `getCount`, `getTag`, `getHoverName`, …) is unchanged. |
| `SharedConstants.filterText(String, boolean)` added | The platform calls the existing one-argument `filterText(String)` (`Textfield`). |
| `Minecraft#getChatListener/getNarrator/getReportingContext/multiplayerBan/…` added; `User#getProfileId` added | Not used — `setSession` reflects over the declared `User`-typed field instead. |
| `Connection#send(Packet, PacketSendListener)` replaces the `GenericFutureListener` overload | The platform only calls `send(Packet)`. |
| `ServerData#enforcesSecureChat` added | Not read (1.19.1 has no `players` counters either, so `getPopulationInfo()` is unchanged). |
| `Font#wordWrapHeight`, `Util#getFilenameFormattedDateTime`, `TextColor`, `LivingEntity`, `Block`, `Material`, … | None of these members are referenced by the platform. |

Result: **no source change, no stub, no behaviour change** relative to the 1.19.2 platform. The chat
hook keeps 1.19.2's placement (`LocalPlayer#chatSigned/commandSigned`), which is correct for 1.19.1 —
that is exactly the release where those methods were introduced.

## Stubs / degraded behaviour

Identical to 1.19.2:

| Method | 1.19.1 behaviour |
|---|---|
| `getFPS()` | Parses the leading field of `Minecraft#fpsString` (no `getFps()` before 1.20). |
| `setSession(name, uuid, token, type)` | Replaces the private `Minecraft#user` field by reflected type; `User` carries the extra `Type` argument, already passed. |
| `getDummyPotionEffects()` | Synthetic HUD preview, same as every other port. |
| `resetServer()` / `updateScaledResolution()` | No cached state to reset — intentional no-ops. |
| `ServerData#getPopulationInfo()` | Sampled player-list size only (1.19.x has no online/max counters). |
| Item icons | Pose-less `ItemRenderer#renderAndDecorateItem(ItemStack,int,int)`: correct for absolute-coordinate HUD callers, ignores pose transforms. |

## Verification performed

- `gradle --no-daemon build` green from a clean `rm -rf build`.
- Jar contains remapped classes (major version 61), refmap (15 mixin classes / 35 mappings),
  8 nested runtime jars (DiscordIPC, jacob, json, json-simple, junixsocket-common,
  junixsocket-native-common, sqlite-jdbc, netty-codec-http) and the `5zig-Version` /
  `Minecraft-Version: 1.19.1` / `5zig-Build` manifest attributes; `fabric.mod.json` depends on
  `~1.19.1` and lists all 8 nested jars.
- `tools/check_mixins.py` resolves every `@Inject`/`@ModifyVariable` name+descriptor against the
  1.19.1 named jar: **38/38 present**.
- `tools/LinkCheck.java` (ASM) resolves every class/method/field reference in the compiled mod
  against the 1.19.1 named jar + Fabric API + libraries: **1495 classes, 65 303 references,
  0 unresolved**.

## Errors encountered

None. `platform/1.19.1` is the 1.19.2 tree verbatim, so the first `gradle build` was already green
(only the pre-existing `MixinCommonMinecraft`/`AccessController` warnings). No stub had to be written.

## Known gaps / not verified

- **No client run** (task rule): nothing here is pixel-verified. HUD, second chat, widget themes,
  capes and the mod menu are verified only by API/link compatibility.
- `MixinCommonMinecraft` from `common/` is compiled in but not listed in `mixins.the5zigmod.json`
  (annotation-processor warning only), as in every other port.
- Plugin channels use the 1.19.x `ResourceLocation`-keyed `ServerboundCustomPayloadPacket`.
