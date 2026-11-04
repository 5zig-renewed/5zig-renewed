# 1.18.2 Fabric port notes

First **1.18** target of the repo, built on the 1.19.2 platform (`platform/1.19.2` was copied and
reconciled file by file). 1.18.2 is transitional: the 1.17 rendering rewrite is already in place
(no `Tessellator`/immediate-mode GUI, no `GuiGraphics`), but none of the 1.19 additions exist yet —
no chat signing, no `OptionInstance`, no `Component` factories.

Built with `gradle-8.10.2` / JDK 17, `--no-daemon`, Java 17 (class file 61).

| Piece | Version |
|---|---|
| Minecraft | 1.18.2 (Mojang official mappings) |
| Fabric Loom | 1.7.4 |
| Fabric Loader | 0.15.11 |
| Fabric API | 0.77.0+1.18.2 |
| Mixin compatibility level | `JAVA_17` |

Build: `cd versions/1.18.2-fabric && JAVA_HOME=/usr/lib/jvm/java-17-openjdk \
  ~/work/tools/gradle-8.10.2/bin/gradle --no-daemon build`
Jar: `build/libs/5zig-renewed-1.18.2-fabric-4.0.0-alpha.1.jar` (18.8 MB, self-contained,
sha256 `057672857c04d5459303ac479e84293f7738376bfe21a225f9648d6908cf762a`).

## Decompiled reference

`mc-src/1.18.2/out` (vineflower 1.12.0, `--only=net/minecraft`, `-dgs=1`),
decompiled from
`~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.18.2-loom.mappings.1_18_2.layered+hash.2198-v2/…`.

## Scope of the delta against `platform/1.19.2`

Ten files carry code changes (141 insertions / 129 deletions over 40 files; the other 30 files are
a comment-only sweep replacing "1.19.2" with "1.18.2" in the port documentation). Everything that is
not listed below is identical to the 1.19.2 platform. The `GuiContext` design carries over untouched:
1.18.2 has no `GuiGraphics` either, and the 1.17+ `PoseStack`-per-frame model is the same one 1.19.2
uses, so `fill`/`blit`/`drawString` still map onto `GuiComponent` + `Font` + `RenderSystem`.

| Area | 1.19.2 | 1.18.2 | Resolution |
|---|---|---|---|
| Text components | `Component.literal/empty/translatable` | `Component` is still a bare interface (only `nullToEmpty`); factories live on `TextComponent` / `TranslatableComponent` | 16 call sites in 7 files rewritten to `new TextComponent(…)`, `new TranslatableComponent(…)` |
| Options | `OptionInstance` accessors `fov()`, `graphicsMode()` | plain public fields `Options#fov` (double) and `Options#graphicsMode` | direct field access in `setFOV`/`getFOV`/`isFancyGraphicsEnabled` |
| Scissor | `GuiComponent.enableScissor/disableScissor` (GUI-scale aware) | absent; only `RenderSystem.enableScissor` in framebuffer pixels | `GuiContext` converts GUI rect → pixels (× scale, flipped Y, clamped) |
| Immediate buffers | `BufferBuilder#end()` returns the builder; `BufferUploader.drawWithShader` | `end()` is void; `BufferUploader.end(BufferBuilder)` | `buffer.end(); BufferUploader.end(buffer);` |
| Interaction | `useItemOn(player, hand, hit)` / `useItem(player, hand)` | both take the `ClientLevel` as the second argument | pass `minecraft.level` |
| Outgoing chat | `LocalPlayer#chatSigned` + `commandSigned` | single `LocalPlayer#chat(String)` (adds the `/` for commands itself) | `MixinLocalPlayer` reduced to one injection on `chat(Ljava/lang/String;)V` |
| Incoming chat | `ChatComponent#addMessage(Component, MessageSignature, GuiMessageTag)` | `ChatComponent#addMessage(Component)` only; player chat first goes through `enqueueMessage` (chat-delay queue) and always lands in `addMessage` | signature dropped, `@ModifyVariable` index dropped |
| Chat signing | signed, with message signatures | none at all | hooks for signatures/tags deleted |

### Mixin changes (1.18.2 vs 1.19.2)

- `MixinLocalPlayer` — rewritten. `chatSigned`/`commandSigned` do not exist; `chat(String)` is the
  only send path for both chat and commands, so it is a single `@Inject(HEAD, cancellable)`.
- `MixinChatComponent` — `addMessage(Component)` replaces the three-argument form; the time-prefix
  `@ModifyVariable` no longer needs `index = 1`.
- All other 13 mixin classes are unchanged and their targets resolve against 1.18.2.

## IVariables coverage

`Variables` implements **all 162 methods** of `eu.the5zig.mod.util.IVariables` (162 `@Override`s,
enforced by `javac`). Nothing throws `UnsupportedOperationException` on a hot path; the only throw is
the defensive fallback at the end of `setSession` (unreachable while `Minecraft#user` exists).

Fully real (read live from Minecraft state): text/width/split, widget factories, chat (2nd chat,
chat box text, chat field), sounds, server data/pinger, FOV/smooth camera, screen switching, joining
servers, client accessors (user, profile, session, proxy, FPS source, window, scaled resolution),
player position/rotation/chunk, target block/entity ray trace, biome/light/entity count,
food/health/armor/air/resistance, potion effects, items in hand/armor/hotbar + counts, HUD helpers,
plugin payloads, resource/texture handling (dynamic images, capes), scoreboard, battery (OSHI 5.8.5,
shipped by 1.18.2), key dispatch, reflective session replacement, chat components with click/hover.

Degraded / approximated (documented, not stubbed):

| Method | 1.18.2 behaviour |
|---|---|
| `getFPS()` | Parses the leading field of `Minecraft#fpsString` (`"120 fps T: …"`, verified in the 1.18.2 source); correct value, only updated once per second. |
| `setFOV(float)` / `getFOV()` | Writes/reads `Options#fov` (a `double`) directly instead of an `OptionInstance`. No 30..110 clamp on the programmatic path — the vanilla slider still clamps when the player uses it. |
| `setSession(name, uuid, token, type)` | Replaces the private **final** `Minecraft#user` field by reflection (`setAccessible` + `Field#set`; verified on JDK 17 for non-static final fields). The only reflection-dependent path. |
| `getPopulationInfo()` (`ServerData`) | 1.18.2 has no online/max counters on `ServerData` — returns the sampled player-list size, or `""`. |
| `getDummyPotionEffects()` | Intentionally synthetic (HUD preview), as in every other port. |
| `resetServer()` / `updateScaledResolution()` | No cached state exists in 1.18.2 (values are read live from `ClientPacketListener`/`Window`), so they are no-ops by design. |
| Item icon drawing (`WrappedItemStack#render`) | The 1.18.2 item renderer (`ItemRenderer#renderAndDecorateItem`) draws in absolute GUI coordinates through its own model view and ignores the `PoseStack`; correct for the current absolute-coordinate callers, wrong inside a scaled/translated pose. |
| `GuiContext#enableScissor/disableScissor` | Not provided by `GuiComponent` in 1.18.2; the facade multiplies by `Window#getGuiScale()`, flips Y and rounds, so a non-integral scale can be off by one pixel at the edges. |

## Verification performed

- `gradle --no-daemon build` green (JDK 17, Loom 1.7.4, `--release 17`).
- `tools/check_mixins.py` resolves every `@Inject`/`@ModifyVariable` name+descriptor against the
  1.18.2 named jar: **37/37 present** across 15 mixin classes.
- `tools/LinkCheck.java` (ASM) resolves every class/method/field reference in the compiled mod against
  the 1.18.2 named jar + Fabric API + libraries: **1495 classes, 65 311 references, 0 unresolved**
  (would catch `NoSuchMethodError`/`NoSuchFieldError`/`NoClassDefFoundError`).
- Refmap check on the remapped jar: `MixinChatComponent.addMessage` → `class_338;method_1812`,
  `MixinLocalPlayer.chat` → `class_746;method_3142` (both verified present in the intermediary jar).
- Jar contains the remapped classes, refmap (15 classes), 8 nested runtime jars (DiscordIPC, jacob,
  json, json-simple, junixsocket-common, junixsocket-native-common, sqlite-jdbc, netty-codec-http)
  and the `5zig-Version` / `Minecraft-Version` / `5zig-Build` manifest attributes; `fabric.mod.json`
  declares `minecraft: ~1.18.2`.
- The remapped jar contains no reference to 1.19+/1.20-only symbols (`GuiGraphics`,
  `BuiltInRegistries`, `org.joml`, `MessageSignature`, `GuiMessageTag`, `chatSigned`,
  `commandSigned`, `OptionInstance`); the only named `net/minecraft/...` strings left are the
  mixin method descriptors that the refmap translates at load time.

## Known gaps / not verified

- **No client run** (task rule), so nothing here is pixel-verified. HUD, second chat, widget themes,
  capes and the mod menu are verified only by API/link compatibility.
- Item icons ignore `PoseStack` transforms (see table); fine for today's callers.
- `MixinCommonMinecraft` ("clickMouse") from `common/` is compiled in but **not** listed in
  `mixins.the5zigmod.json`; `MixinMinecraft#startAttack` covers CPS. It produces the only
  annotation-processor warning in the build.
- `setFOV` does not clamp to the vanilla slider range.
- 1.18.2 has no chat signing, so the "secure chat" related UI/hooks of later ports are simply absent
  rather than disabled.
