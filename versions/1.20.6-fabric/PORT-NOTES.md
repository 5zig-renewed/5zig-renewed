# 1.20.6 Fabric port notes

Built with `gradle-8.10.2` / JDK 21, `--no-daemon`, target Java 21 (class file 65).

| Piece | Version |
|---|---|
| Minecraft | 1.20.6 (Mojang official mappings) |
| Fabric Loom | 1.7.4 |
| Fabric Loader | 0.16.10 |
| Fabric API | 0.100.8+1.20.6 |
| Mixin compatibility level | `JAVA_21` |

Build: `cd versions/1.20.6-fabric && JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  ~/work/tools/gradle-8.10.2/bin/gradle --no-daemon build`
Jar: `build/libs/5zig-renewed-1.20.6-fabric-4.0.0-alpha.1.jar`

## Decompiled reference

`mc-src/1.20.6/out` (vineflower 1.12.0, `--only=net/minecraft`, `-dgs=1`), decompiled from
`versions/1.20.6-fabric/.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-7036bae68b/…`.

## The 1.20.5 item-components rewrite

1.20.5 replaced NBT tags on `ItemStack` with typed `DataComponents`. Everything the port touches:

- **Food** — `Item#getFoodProperties()` is gone; food lives in `DataComponents.FOOD` and is a
  `FoodProperties` **record**. `getHealAmount()` reads `food.nutrition()`.
  `getSaturationModifier()` is the one non-obvious conversion: 1.20.4 stored a *modifier*, 1.20.5+
  stores the *absolute* saturation (`nutrition * modifier * 2`). The core still computes
  `heal * modifier * 2` for its saturation preview, so the platform divides back out:
  `food.saturation() / (2f * food.nutrition())` (guarding `nutrition <= 0`).
- **Tooltips** — `getTooltipLines` now takes an `Item.TooltipContext`; the platform passes
  `Item.TooltipContext.of(minecraft.level)`, the same context `Screen#getTooltipFromItem` uses
  (`EMPTY` when there is no level).
- **Attribute modifiers** — `ItemStack#getAttributeModifiers(EquipmentSlot)` and the
  `Multimap<Attribute, AttributeModifier>` are gone. Modifiers live in the `attribute_modifiers`
  component, reachable only through `ItemStack#forEachModifier(EquipmentSlot, BiConsumer<Holder<Attribute>,
  AttributeModifier>)`. `Attributes.ATTACK_DAMAGE`/`ARMOR` are now `Holder<Attribute>` (`.value()`), and
  `AttributeModifier` is a record (`amount()`, `id()`). Vanilla no longer smuggles the enchantment damage
  bonus into the base attack-damage modifier, so the overlay re-adds it the way
  `ItemStack#addModifierTooltip` does: `EnchantmentHelper.getDamageBonus(stack, null)`, folded into the
  entry whose id is `Item.BASE_ATTACK_DAMAGE_UUID`.
- **Durability / names** — `getMaxDamage()` / `getDamageValue()` / `getHoverName()` still exist and now read
  `MAX_DAMAGE` / `DAMAGE` / `CUSTOM_NAME`; the core's `ItemStackItem` HUD needs no change.
- **Armor** — the platform never touches `ArmorItem`; armor slots go through
  `Inventory#getArmor(int)` (`0` = boots … `3` = helmet) and the durability getters above, so the
  component rewrite needed no change there.
- **Mob effects** — `MobEffectInstance#getEffect()` returns `Holder<MobEffect>`, and
  `MobEffectTextureManager#get` takes the holder; the description id/colour/beneficial flag are read from
  `holder.value()`.

## Other 1.20.5 / 1.20.6 API changes

- `Gui#renderHotbar(float, GuiGraphics)` → **`renderHotbarAndDecorations(GuiGraphics, float)`** (private,
  arguments swapped) — the hotbar-number mixin targets the new name.
- `ChatComponent#render(GuiGraphics, int, int, int)` gained a trailing `boolean focused`.
- `Screen.BACKGROUND_LOCATION` → **`Screen.MENU_BACKGROUND`** (`textures/gui/menu_background.png`, a 32×32
  dirt tile); `Screen#renderDirtBackground` → `renderMenuBackground`. `GuiCompat.blitRepeating` still tiles
  at 32×32, so the list/file-selector backgrounds are unchanged.
- `ClientboundTabListPacket` is a record: `header()` / `footer()` instead of `getHeader()` / `getFooter()`.
- `SharedConstants.filterText/isAllowedChatCharacter` → `StringUtil.filterText/isAllowedChatCharacter`.
- `ServerStatusPinger#pingServer(ServerData, Runnable success, Runnable failure)` gained the failure callback.
- `ConnectScreen.startConnecting(…, boolean)` gained a trailing `@Nullable TransferState`.
- Clientbound `ClientboundPlayerPositionPacket` still exposes `getX()/getY()/getZ()`, so the teleport hook is
  unchanged.

### Fabric networking (1.20.5 removed the raw channel API)

Fabric API 0.100.8 only offers the typed API: `PayloadTypeRegistry.playC2S()/playS2C().register(Type, StreamCodec)`
and `ClientPlayNetworking.registerGlobalReceiver(Type<T>, …)` / `send(T)`. The old
`registerGlobalReceiver(ResourceLocation, …)` and `send(ResourceLocation, FriendlyByteBuf)` overloads are gone.

Vanilla's fallback codec makes arbitrary channels impossible, not merely inconvenient: an unknown incoming
channel decodes to a body-less `DiscardedPayload` **and the fallback encoder writes no body at all**. So
`platform/1.20.6/…/PluginMessages` now declares `the5zigmod:5zig`, `…:5zig_reg` and `…:5zig_set` as payload
types in both directions (a `RawPayload` record carrying the untouched bytes) and hands them back to
`EventListener#handlePluginMessage`. `RawCustomPayload` was deleted; `Variables#sendCustomPayload` logs once
and drops payloads on any other channel instead of pretending to send them.

## Known gaps

- No client launch was performed (per task rules), so HUD/chat pixels are verified only by API
  compatibility + mixin target resolution, not on screen.
- Custom payloads on channels other than the three 5zig API channels are impossible from 1.20.5 on.
- Plugin-channel delivery depends on the server announcing the 5zig channels; servers that never register
  them still see the client's registration packet but no traffic.

## Verification performed

- `gradle --no-daemon build` succeeds; the jar contains remapped classes, the mixin refmap and the eight
  nested runtime jars (DiscordIPC, jacob, json, json-simple, junixsocket-common, junixsocket-native-common,
  sqlite-jdbc, netty-codec-http).
- `tools/check_mixins.py` resolves every `@Inject` target (`name + descriptor`) against the 1.20.6 named
  jar: **36/36 present**.
- The refmap maps all 14 mixin classes through intermediary names (e.g. `renderHotbarAndDecorations` →
  `method_55805`), which also proves the 1.20.5 rename landed on a real method.
- `fabric.mod.json` entrypoint (`FiveZigFabric1206`), mixin list (14 classes), `~1.20.6` / `java >=21`
  dependencies and the `5zig-Version` / `Minecraft-Version` / `5zig-Build` manifest attributes verified
  inside the built jar.
