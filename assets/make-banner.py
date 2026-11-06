from PIL import Image, ImageDraw, ImageFont

W, H = 1280, 640
MARGIN = 72
img = Image.new("RGB", (W, H))
top, bot = (21, 26, 34), (9, 11, 16)
for y in range(H):
    t = y / (H - 1)
    img.paste(tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3)), (0, y, W, y + 1))

glow = Image.new("L", (W, H), 0)
ImageDraw.Draw(glow).ellipse([-320, H - 460, 620, H + 260], fill=46)
warm = Image.new("RGB", (W, H), (255, 158, 27))
img = Image.composite(warm, img, glow.point(lambda v: int(v * 0.32)))

d = ImageDraw.Draw(img)
FONT = "/usr/share/fonts/Adwaita/AdwaitaSans-Regular.ttf"


def font(size, weight="Bold"):
    f = ImageFont.truetype(FONT, size)
    try:
        f.set_variation_by_name(weight)
    except Exception:
        pass
    return f


def text(xy, s, size, weight, color):
    f = font(size, weight)
    d.text(xy, s, font=f, fill=color)
    b = d.textbbox(xy, s, font=f)
    if b[2] > W - MARGIN:
        raise SystemExit(f"OVERFLOW: '{s}' ends at {b[2]} > {W - MARGIN}")
    return b


logo = Image.open("assets/logo-512.png").convert("RGBA")
logo = logo.resize((344, 344), Image.Resampling.LANCZOS)
img.paste(logo, (96, 148), logo)

x = 508
text((x, 170), "5zig Renewed", 96, "Bold", (255, 246, 232))
text((x, 290), "The 5zig Mod for modern Minecraft", 38, "Medium", (255, 161, 38))
text((x, 362), "Fabric  ·  NeoForge  ·  Forge", 31, "Medium", (176, 185, 197))
text((x, 406), "Minecraft 1.16 – 26.3", 31, "Medium", (176, 185, 197))

d.line([(x, 468), (x + 660, 468)], fill=(255, 255, 255, 26), width=2)
text((x, 492), "GPL-3.0  ·  continuation of 5zig Reborn", 26, "Medium", (148, 157, 170))

img.save("assets/banner-1280x640.png")
print("saved, all text within margins")
