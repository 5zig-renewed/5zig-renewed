# Port spec — 5zig Renewed, platform layer (1.20.1, Mojang mappings)

Everything in this file is verified against the decompiled 1.20.1 sources at
`mc-src/1.20.1/out` (mojmap names) — read them, do not guess.

## Golden rules

1. **Platform classes live in the DEFAULT package** (`Button`, `Textfield`, `Variables`, ...). The
   core (`eu.the5zig.mod.*`) refers to them unqualified because `MinecraftFactory` does
   `Class.forName("Variables")` and `IconButton extends Button` has no import. Keep it that way.
   Only `eu.the5zig.mod.*` helpers may be packaged.
2. Implement the **core's interface**, not a redesign. `eu.the5zig.mod.util.IVariables` (162 methods)
   is the contract; GUI widgets implement `eu.the5zig.mod.gui.elements.*`.
3. **No `net.minecraft.launchwrapper`, no ASM transformers.** Fabric mixins replace them.
4. Target **Java 17**, `-release 17`. Compile with the classpath file in the project root.
5. 1.20.1 only. Do not write version-portable preprocessor syntax yet.

## Rendering model (the one real design decision)

`IVariables.drawString(...)`/`drawIngameTexturedModalRect(...)` take no `GuiGraphics`, because the
core was written against 1.15's global Tessellator. So:

- `eu.the5zig.mod.util.RenderContext` (package-private static holder, new file) keeps the current
  `GuiGraphics` while a frame is being drawn. It is set by the HUD mixin
  (`MixinGui.render`) and by `GuiHandle.render(...)`, and cleared in a `finally`.
- Every drawing method requires `RenderContext.get()`; if null, skip drawing (never crash).
- Use only `GuiGraphics` APIs: `fill`, `fillGradient`, `blit`, `drawString`, `drawCenteredString`,
  `enableScissor`/`disableScissor`, `pose()` (a `PoseStack`).

## Verified 1.20.1 signatures (from decompiled sources)

```java
Minecraft mc = Minecraft.getInstance();
mc.font                 // net.minecraft.client.gui.Font
mc.options              // net.minecraft.client.Options  (was GameSettings)
mc.player               // net.minecraft.client.player.LocalPlayer (was ClientPlayerEntity)
mc.level                // ClientLevel
mc.screen               // Screen
mc.getWindow()          // com.mojang.blaze3d.platform.Window
mc.getTextureManager()  // TextureManager
mc.getResourceManager() // ResourceManager
mc.gameDirectory        // File
mc.getUser()            // com.mojang.authlib.GameProfile  (UserInfo in 1.20.1: mc.getUser().getProfileId()?)
mc.getConnection()      // ClientPacketListener
mc.getSingleplayerServer()
mc.getCurrentServer()   // ServerData
mc.fpsString            // public String, e.g. "120 fps T: ..."  -> FPS must be parsed
mc.isSingleplayer()
mc.getFrameTime()       // float
mc.getDeltaFrameTime()  // float

Font f = mc.font;
f.width(String) -> int
f.lineHeight -> int                 // int field, not method
f.drawInBatch(...)                 // low level; prefer GuiGraphics.drawString
f.split(String, int) -> List<FormattedCharSequence>

GuiGraphics g;
g.fill(x1,y1,x2,y2,argb)                       // solid rect
g.fillGradient(x1,y1,x2,y2,rgbTop,rgbBottom)
g.blit(ResourceLocation, x, y, u, v, w, h)     // 1.20.1 signature: (rl, x, y, u, v, width, height)
g.blit(ResourceLocation, x, y, u, v, w, h, texW, texH)
g.drawString(font, text, x, y, color)          // color is int, shadow = true
g.drawString(font, text, x, y, color, false)
g.drawCenteredString(font, text, x, y, color)
g.enableScissor(x1,y1,x2,y2)  /  g.disableScissor()
g.pose()  -> PoseStack

Screen:
  protected void init()
  public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick)
  public boolean mouseClicked(double x, double y, int button)
  public boolean keyPressed(int keyCode, int scanCode, int modifiers)
  public boolean charTyped(char c, int modifiers)

KeyMapping(String name, int keyCode, String category)     // category == translation key
KeyMapping#consumeClick(), #isDown(), #getKey().getValue()

Component.literal(String) / Component.translatable(String)
ResourceLocation: new ResourceLocation("the5zigmod", "textures/items.png") is PUBLIC in 1.20.1
```

## Widget/behaviour ports

- `GuiList`/`GuiListChat` scroll + cull themselves; use `g.enableScissor`.
- Text input: `net.minecraft.client.gui.components.EditBox` is the modern text field; but the core
  expects an `ITextfield` with 1.15 semantics (`callGetText`, `callSetText`, cursor, selection).
  Port `Textfield` as a **custom widget** extending `AbstractWidget` so the semantics can be matched
  exactly, and keep `EditBox` only as a fallback.
- Keybindings: `KeyMapping` + `net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper`.

## What "done" means for a file

- It compiles with `javac` against the project classpath.
- No `UnsupportedOperationException` / `TODO` in methods the core calls on the hot path
  (init, tick, render, config load, keybind handling).
- GPL-3.0 header retained.

## Build/verify command

```bash
cd <project>
JAVA_HOME=/usr/lib/jvm/java-21-openjdk /usr/lib/jvm/java-21-openjdk/bin/javac \
  -nowarn -d <out-dir> -cp "$(cat compile-classpath.txt)" $(find core common api platform loader -name '*.java')
```
