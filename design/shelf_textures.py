"""Paint the listening-console artwork (posters, calendar, rug) used by collection_room.py."""
import math, random
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

out = Path(__file__).resolve().parent / "textures"; out.mkdir(exist_ok=True)
def font(names, size):
    for n in names:
        for d in ("/usr/share/fonts", "/Library/Fonts", "/System/Library/Fonts", str(Path.home()/"Library/Fonts")):
            for p in Path(d).rglob(n) if Path(d).exists() else []:
                return ImageFont.truetype(str(p), size)
    return ImageFont.load_default(size)
serif = lambda s: font(["DejaVuSerif.ttf", "Caladea-Regular.ttf", "Georgia.ttf", "Times New Roman.ttf"], s)
heavy = lambda s: font(["Inter-ExtraBold.otf", "Inter-Black.otf", "DejaVuSans-Bold.ttf", "Arial Black.ttf", "Helvetica.ttc"], s)
small = lambda s: font(["Inter-Medium.otf", "DejaVuSans.ttf", "Helvetica.ttc"], s)

def stacked_lenses():
    """Black poster: four glowing pink lenses with fine meridian lines over a cream title band."""
    W, H = 720, 1000
    im = Image.new("RGB", (W, H), (22, 20, 20)); d = ImageDraw.Draw(im)
    for k in range(4):
        cy, rx, ry = 90 + k * 165, 300, 78
        yy, xx = np.mgrid[0:H, 0:W]
        r = ((xx - W/2) / rx) ** 2 + ((yy - cy) / ry) ** 2
        glow = np.clip(1 - r, 0, 1) ** .55
        layer = np.zeros((H, W, 3)); layer[..., 0] = 236; layer[..., 0] = 230 + 22 * glow; layer[..., 1] = 70 + 120 * glow; layer[..., 2] = 110 + 90 * glow
        a = np.asarray(im).astype(float); m = (r <= 1)[..., None] * (0.35 + .65 * glow[..., None])
        im = Image.fromarray((a * (1 - m) + layer * m).astype("uint8")); d = ImageDraw.Draw(im)
        d.ellipse((W/2 - rx, cy - ry, W/2 + rx, cy + ry), outline=(242, 120, 60), width=3)
        for j in range(1, 24):
            x = rx * math.cos(math.pi * j / 24)
            d.arc((W/2 - abs(x), cy - ry, W/2 + abs(x), cy + ry), 0, 360, fill=(170, 70, 80), width=1)
    d.rectangle((0, 760, W, H), fill=(236, 228, 214))
    t = "UNBOXING"; f = serif(104); w = d.textlength(t, font=f)
    d.text(((W - w) / 2, 790), t, font=f, fill=(26, 24, 24))
    for i in range(6): d.line((40, 920 + i * 11, 40 + random.Random(i).randint(160, 320), 920 + i * 11), fill=(90, 86, 80), width=3)
    im.save(out / "poster_lenses.png")

def bold_flowers():
    """Black poster with outline flowers and heavy cream letters."""
    W, H = 720, 1020
    im = Image.new("RGB", (W, H), (16, 15, 15)); d = ImageDraw.Draw(im)
    for cx, cy, s in [(250, 300, 95), (420, 190, 85)]:
        for k in range(5):
            a = math.tau * k / 5
            px, py = cx + math.cos(a) * s * .62, cy + math.sin(a) * s * .62
            d.ellipse((px - s * .45, py - s * .45, px + s * .45, py + s * .45), outline=(240, 236, 226), width=5)
        d.ellipse((cx - s * .5, cy - s * .5, cx + s * .5, cy + s * .5), fill=(16, 15, 15))
        d.ellipse((cx - 14, cy - 14, cx + 14, cy + 14), outline=(240, 236, 226), width=5)
    d.line([(250, 360), (300, 470), (360, 560)], fill=(240, 236, 226), width=5, joint="curve")
    d.line([(420, 250), (450, 420), (420, 560)], fill=(240, 236, 226), width=5, joint="curve")
    letters = Image.new("L", (1400, 460)); ld = ImageDraw.Draw(letters)
    ld.text((10, -40), "SLOP", font=heavy(380), fill=255)
    letters = letters.crop(letters.getbbox()).resize((620, 400))
    im.paste((236, 226, 200), (50, 570), letters)
    f = small(18)
    for x, t in [(50, "BLINDBOX WORLD"), (330, "SIDE A"), (560, "SERIES 01")]: d.text((x, 40), t, font=f, fill=(220, 214, 200))
    im.save(out / "poster_flowers.png")

def pink_capybara():
    """Small cream print: a pink capybara between two palms."""
    W, H = 480, 600
    im = Image.new("RGB", (W, H), (245, 236, 226)); d = ImageDraw.Draw(im); p = (230, 92, 150)
    for x in (110, 370):
        d.line([(x, 560), (x - 10, 330), (x + 5, 140)], fill=p, width=14, joint="curve")
        for k in range(7):
            a = math.pi * (0.05 + k / 6.5)
            d.line([(x + 5, 140), (x + 5 + math.cos(a) * 95, 140 - math.sin(a) * 55 + 30)], fill=p, width=12)
    d.ellipse((120, 330, 360, 480), fill=p); d.ellipse((300, 300, 400, 390), fill=p)
    for x in (150, 200, 280, 330): d.rectangle((x, 450, x + 22, 545), fill=p)
    d.ellipse((370, 325, 382, 337), fill=(245, 236, 226))
    d.line((40, 580, 440, 580), fill=p, width=3)
    im = im.filter(ImageFilter.GaussianBlur(.8)); im.save(out / "print_capybara.png")

def calendar():
    W, H = 400, 600
    im = Image.new("RGB", (W, H), (246, 242, 236)); d = ImageDraw.Draw(im)
    d.rectangle((0, 0, W, 300), fill=(236, 240, 244))
    for i, c in enumerate([(30, 40, 70), (210, 40, 50), (240, 140, 160), (20, 90, 110)]):
        d.pieslice((40 + i * 70, 70 + (i % 2) * 40, 210 + i * 70, 260), 180 + i * 40, 360 + i * 30, fill=c)
    d.ellipse((290, 30, 360, 100), fill=(40, 70, 140))
    d.rectangle((0, 300, W, H), fill=(226, 70, 110))
    for r in range(5):
        for c in range(7): d.rectangle((24 + c * 52, 340 + r * 50, 64 + c * 52, 378 + r * 50), outline=(250, 210, 220), width=2)
    for x in range(30, 380, 18): d.ellipse((x, 6, x + 8, 14), fill=(120, 120, 120))
    im.save(out / "calendar.png")

def persian_rug():
    """Deep red field, repeated octagonal guls, layered borders and a cream fringe."""
    W, H = 1024, 1024; rng = random.Random(5)
    red, navy, cream, rust, ink = (150, 22, 30), (34, 30, 58), (232, 214, 188), (196, 72, 40), (40, 18, 20)
    im = Image.new("RGB", (W, H), red); d = ImageDraw.Draw(im)
    def octagon(cx, cy, r, **kw):
        d.polygon([(cx + r * math.cos(math.pi / 8 + k * math.pi / 4), cy + r * math.sin(math.pi / 8 + k * math.pi / 4)) for k in range(8)], **kw)
    for gy in range(5):
        for gx in range(4):
            cx, cy = 200 + gx * 208, 190 + gy * 162
            octagon(cx, cy, 70, fill=rust, outline=ink, width=4); octagon(cx, cy, 48, fill=red, outline=cream, width=3)
            d.polygon([(cx, cy - 30), (cx + 30, cy), (cx, cy + 30), (cx - 30, cy)], fill=navy, outline=cream)
            d.rectangle((cx - 6, cy - 6, cx + 6, cy + 6), fill=cream)
            if gx < 3 and gy < 4:
                ox, oy = cx + 104, cy + 81
                d.polygon([(ox, oy - 22), (ox + 22, oy), (ox, oy + 22), (ox - 22, oy)], fill=navy); d.ellipse((ox - 5, oy - 5, ox + 5, oy + 5), fill=cream)
    for inset, w, c in [(0, 34, ink), (34, 56, navy), (90, 8, cream), (98, 6, ink)]:
        d.rectangle((inset, inset, W - 1 - inset, H - 1 - inset), outline=c, width=w)
    for t in range(40, W - 40, 34):
        for (x, y) in [(t, 62), (t, H - 62), (62, t), (W - 62, t)]:
            d.polygon([(x, y - 12), (x + 12, y), (x, y + 12), (x - 12, y)], fill=rust if (t // 34) % 2 else cream)
    a = np.asarray(im).astype(float)
    a *= (0.9 + 0.12 * np.random.default_rng(3).random((H, W, 1)))  # knotted pile
    a *= (0.96 + 0.04 * np.sin(np.arange(W) * 1.7))[None, :, None]
    Image.fromarray(np.clip(a, 0, 255).astype("uint8")).save(out / "rug_persian.png")

for f in (stacked_lenses, bold_flowers, pink_capybara, calendar, persian_rug): f()
print("textures in", out)
