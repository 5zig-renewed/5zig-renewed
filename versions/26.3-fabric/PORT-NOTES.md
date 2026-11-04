# 5zig Renewed — Minecraft 26.3 / Fabric

Status: **tree compiles, jar builds** (`build/libs/5zig-renewed-26.3-fabric-4.0.0-alpha.1.jar`,
1 676 entries). This is the 26.2 port re-targeted at 26.3; `platform/26.3` is a copy of
`platform/26.2` with the four deltas listed below, and `compat-src` carries one updated core file.

## Toolchain (verified)

| Piece | Value |
|---|---|
| Minecraft | 26.3 (unobfuscated jar — **no `mappings` line at all**) |
| Fabric Loader | 0.20.1 |
| Fabric API | 0.165.2+26.3 |
| Loom | `net.fabricmc.fabric-loom` **1.19.3** (requires Gradle ≥ 9.8) |
| Gradle | **9.8.2** (`~/.tools/gradle/gradle-9.8.2`) |
| JDK | **25** (`/usr/lib/jvm/java-25-openjdk`); `options.release = 25` |
| LWJGL | 3.4.3 (26.2 was 3.4.1) |
| authlib (game) | **10.0.77** (26.2 was 9.0.75) |
| Mixin | sponge-mixin 0.17.5+mixin.0.8.7 → `compatibilityLevel: JAVA_25` |

Build:

```bash
cd versions/26.3-fabric
JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  ~/.tools/gradle/gradle-9.8.2/bin/gradle --no-daemon build
```

## Why 26.3's compile classpath differs: the window backend moved from GLFW to SDL

`~/.gradle/caches/fabric-loom/<ver>/mojang_minecraft_info.json` is the authority here:

| | 26.2 | 26.3 |
|---|---|---|
| window/input backend | `org.lwjgl:lwjgl-glfw:3.4.1` (+ 6 natives) | **`org.lwjgl:lwjgl-sdl:3.4.3`** (+ natives) |
| `lwjgl-glfw` entries | 7 | **0** |
| core LWJGL | 3.4.1 | 3.4.3 |
| authlib | 9.0.75 | 10.0.77 |

26.3's `com.mojang.blaze3d.platform.Window` now takes `org.lwjgl.sdl.SDL_Event` and
`Window#handle()` returns an **SDL** window handle; the GLFW module is not part of the game any
more, so Loom puts no `org.lwjgl.glfw` on the compile classpath. The platform layer still imports
`org.lwjgl.glfw.GLFW` in four files (`util/Keybinds`, `platform/Textfield`, `platform/Variables`
and the core's `manager/SearchEntry`), which is exactly the reported
`package org.lwjgl.glfw does not exist`.

Fix: one explicit dependency in `versions/26.3-fabric/build.gradle`

```groovy
implementation 'org.lwjgl:lwjgl-glfw:3.4.3'
```

Pinned to 3.4.3 on purpose — 3.4.3 is the LWJGL core version 26.3 ships and LWJGL modules refuse to
mix versions. Verified both ways: removing the line reproduces the four
`package org.lwjgl.glfw does not exist` errors (SearchEntry, Keybinds, Textfield, Variables);
with it, `dependencies --configuration compileClasspath` shows `org.lwjgl:lwjgl-glfw:3.4.3` with
`org.lwjgl:lwjgl:3.4.3` beneath it and no conflict resolution.

## authlib: present, but 10.x deleted the API the core overlay used

authlib was **not** missing from 26.3's classpath — `com.mojang:authlib:10.0.77` is in the 26.3
version manifest (as 9.0.75 was in 26.2's) and resolves onto `compileClasspath` through the
`minecraft` dependency. What disappeared is the API:

| 26.2 (authlib 9) | 26.3 (authlib 10) |
|---|---|
| `com.mojang.authlib.minecraft.MinecraftSessionService` | **removed** → `com.mojang.authlib.minecraft.SessionService` |
| `com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService` | **removed**, whole `yggdrasil` package gone |
| `new YggdrasilAuthenticationService(proxy).createMinecraftSessionService()` | `Minecraft.getInstance().services().sessionService()` |

`compat-src/eu/the5zig/mod/chat/network/packets/PacketEncryption.java` was updated accordingly
(`SessionService`, borrowed from `Minecraft#services()`); `joinServer(profileId, session, hash)` is
unchanged. No authlib dependency was added — it is already supplied by the game, and declaring it
separately would only risk a second, conflicting copy. The reported errors
(`cannot find symbol: MinecraftSessionService`, `package com.mojang.authlib.yggdrasil does not
exist`) were javac resolving imports against a jar that no longer contains those names.

## The other 26.3 API deltas (4 platform files)

| 26.2 | 26.3 | Files |
|---|---|---|
| `InputConstants.Type.KEYSYM` | **`InputConstants.Type.KEYBOARD`** (`MOUSE` unchanged) | `util/Keybinds`, `platform/Keybinding`, `platform/Variables#getKeyDisplayStringShort` |
| `KeyEvent#scancode()` | **`KeyEvent#keycode()`** (`key()`/`modifiers()` unchanged) | `platform/GuiHandle#keyPressed` |
| `LivingEntity#swing(InteractionHand)` | **`swing(InteractionHand, SwingAnimation, boolean)`** | `platform/Variables#onRightClickMouse` (3 call sites) |

`swing` passes `SwingAnimation.DEFAULT` and `true` for the "send to self as well" flag; that flag
only branches inside the `ServerLevel` path, and these calls run against a `LocalPlayer` in a
`ClientLevel`, so it is inert — `true` matches vanilla's own "send to tracking players and self".

Everything else in `platform/26.3` is byte-identical to `platform/26.2`, including the `Gui`/`Hud`
split (`MixinGui` screen hook + `MixinHud` frame bracket, both listed in the mixin config).

## Colour trap (unchanged in 26.3 — still strict ARGB)

26.x colours are strict ARGB: a legacy `0xRRGGBB` constant has alpha 0 and draws **nothing**, with
no error. `forceOpaque(int)` (`(c & 0xFF000000) == 0 ? c | 0xFF000000 : c`) is applied in
`Variables#drawString` / `#drawCenteredString`; every other draw passes an explicit `0xFF……` or an
alpha-composed value. No `forceOpaque` call was dropped in this port.

## Core overlay (unchanged mechanism)

`core/` is still compiled through `coreCompatTree` (core minus 12 authlib-4/commons-lang3 files,
plus `compat-src/` versions). Only `PacketEncryption.java` needed a 26.3 change (authlib 10, above);
the other eleven overlay files compile against authlib 10 unchanged.

## Verification done here

* `gradle build` → `BUILD SUCCESSFUL`; `build/libs/5zig-renewed-26.3-fabric-4.0.0-alpha.1.jar`,
  1 676 entries; every `platform/26.3` source has a matching `.class` in the jar.
* `check-mixins.py` — every `@Inject`/`@ModifyVariable` descriptor exists in the **26.3** jar
  (`all mixin targets resolve`).
* `check-handlers.py` — every injector handler's parameter list matches its target descriptor
  (`all mixin handlers match their targets`).
* Mixin AP emitted `5zig-renewed-26.3-fabric-refmap.json` (identity named→intermediary map);
  `processResources` put the `refmap` key into `mixins.the5zigmod.json`.
* Manifest carries `5zig-Version: 4.0.0-alpha.1`, `Minecraft-Version: 26.3`, `5zig-Build: alpha`.
* Jar-in-Jar list is identical to 26.2's (12 jars): DiscordIPC, jacob, json, json-simple,
  junixsocket-common, junixsocket-native-common, sqlite-jdbc, netty-codec-http, httpclient 4.5.14,
  httpcore 4.4.16, commons-codec, commons-logging.
* `fabric.mod.json`: `minecraft ~26.3`, `java >=25`, `fabricloader >=0.20.1`.
* No Minecraft client was started from this build.

## Remaining gaps

* **No runtime test.** The jar is compile-verified and mixin-verified only.
* **GLFW at runtime.** The `lwjgl-glfw` dependency makes the platform *link*; it does not bundle
  GLFW into the jar (no `include`), and 26.3 never initialises GLFW. Two paths in
  `platform/Variables` still call into it for real, not just for compile-time constants:
  `Keyboard.isKeyDown` / `Mouse.isButtonDown` (`GLFW.glfwGetKey/glfwGetMouseButton(window.handle(), …)`)
  and `getKeyDisplayStringShort` (`GLFW.glfwGetKeyName`). `Window#handle()` is an **SDL** handle in
  26.3, so those calls are both wrong and, in a production install where `lwjgl-glfw` is absent,
  `NoClassDefFoundError`. Follow-up (needs a client run to verify): use
  `InputConstants.isKeyDown(int)` (exists in 26.3, no window handle) for the keyboard, the SDL
  mouse state for the buttons, and `InputConstants.Type.KEYBOARD.getOrCreate(key).getDisplayName()`
  for the name. Everything else (`Keybinds`, `Textfield`) uses only `static final int` GLFW
  constants, which javac inlines — no runtime class reference.
* Network-facing features (custom payloads, Hypixel stats) stay stubbed as in 26.2.
