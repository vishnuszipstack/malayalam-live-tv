"""Renders the launcher icon and the Fire TV banner as PNGs."""
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

FONTS, RES, PREVIEW = sys.argv[1], sys.argv[2], sys.argv[3]
MAL = f"{FONTS}/NotoSansMalayalam-Bold.ttf"
INTER_BOLD = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"
INTER_XBOLD = "/usr/share/fonts/opentype/inter/Inter-ExtraBold.otf"

GREEN_TOP = (16, 120, 76)
GREEN_BOTTOM = (6, 46, 40)
GOLD = (251, 191, 36)
RED = (225, 29, 72)
WHITE = (255, 255, 255)


def gradient(size, top, bottom, diagonal=True):
    w, h = size
    img = Image.new("RGB", size)
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = ((x / w) * 0.35 + (y / h) * 0.65) if diagonal else y / h
            px[x, y] = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
    return img


def glow(img, center, radius, color, alpha):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    cx, cy = center
    d.ellipse([cx - radius, cy - radius, cx + radius, cy + radius], fill=color + (alpha,))
    layer = layer.filter(ImageFilter.GaussianBlur(radius / 2))
    img.alpha_composite(layer)


def tv_mark(size):
    """The mark: a TV screen holding the Malayalam letter 'മ', with a red live dot."""
    s = size
    mark = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(mark)
    # screen
    left, top, right, bottom = s * 0.12, s * 0.20, s * 0.88, s * 0.76
    d.rounded_rectangle([left, top, right, bottom], radius=s * 0.09, outline=WHITE, width=int(s * 0.055))
    # stand
    d.rounded_rectangle([s * 0.36, s * 0.80, s * 0.64, s * 0.85], radius=s * 0.025, fill=WHITE)
    # letter
    font = ImageFont.truetype(MAL, int(s * 0.46))
    text = "മ"
    bbox = d.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    cx, cy = (left + right) / 2, (top + bottom) / 2
    d.text((cx - tw / 2 - bbox[0], cy - th / 2 - bbox[1]), text, font=font, fill=GOLD)
    # live dot
    r = s * 0.075
    dx, dy = right - s * 0.02, top + s * 0.02
    d.ellipse([dx - r - s * 0.02, dy - r - s * 0.02, dx + r + s * 0.02, dy + r + s * 0.02], fill=GREEN_BOTTOM + (255,))
    d.ellipse([dx - r, dy - r, dx + r, dy + r], fill=RED)
    return mark


def icon(size=1024):
    base = gradient((size, size), GREEN_TOP, GREEN_BOTTOM).convert("RGBA")
    glow(base, (size * 0.25, size * 0.15), size * 0.45, (60, 200, 130), 90)
    base.alpha_composite(tv_mark(size))
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=size * 0.22, fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(base, (0, 0), mask)
    return out


def banner(w=1280, h=720):
    img = gradient((w, h), GREEN_TOP, GREEN_BOTTOM).convert("RGBA")
    glow(img, (w * 0.18, h * 0.2), h * 0.6, (60, 200, 130), 80)
    glow(img, (w * 0.9, h * 1.0), h * 0.5, (251, 191, 36), 40)
    mark = tv_mark(int(h * 0.70))
    img.alpha_composite(mark, (int(w * 0.05), int(h * 0.15)))
    d = ImageDraw.Draw(img)
    x = int(w * 0.50)
    title = ImageFont.truetype(INTER_XBOLD, int(h * 0.15))
    d.text((x, int(h * 0.27)), "Malayalam", font=title, fill=WHITE)
    sub = ImageFont.truetype(INTER_BOLD, int(h * 0.12))
    # "LIVE TV" in gold with a red live pill
    pill_h = int(h * 0.13)
    py = int(h * 0.53)
    d.rounded_rectangle([x, py, x + int(h * 0.30), py + pill_h], radius=pill_h // 4, fill=RED)
    live = ImageFont.truetype(INTER_BOLD, int(pill_h * 0.62))
    lb = d.textbbox((0, 0), "LIVE", font=live)
    d.text((x + (h * 0.30 - (lb[2] - lb[0])) / 2 - lb[0], py + (pill_h - (lb[3] - lb[1])) / 2 - lb[1]), "LIVE", font=live, fill=WHITE)
    sb = d.textbbox((0, 0), "TV", font=sub)
    d.text((x + int(h * 0.34), py + (pill_h - (sb[3] - sb[1])) / 2 - sb[1]), "TV", font=sub, fill=GOLD)
    return img


big = icon()
for folder, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
    path = f"{RES}/mipmap-{folder}"
    import os
    os.makedirs(path, exist_ok=True)
    big.resize((px, px), Image.LANCZOS).save(f"{path}/ic_launcher.png", optimize=True)

os.makedirs(f"{RES}/drawable-xhdpi", exist_ok=True)
banner().resize((640, 360), Image.LANCZOS).save(f"{RES}/drawable-xhdpi/banner.png", optimize=True)

# preview sheet
sheet = Image.new("RGBA", (1100, 420), (11, 15, 20, 255))
sheet.alpha_composite(big.resize((300, 300), Image.LANCZOS), (40, 60))
sheet.alpha_composite(banner().resize((640, 360), Image.LANCZOS), (400, 30))
sheet.convert("RGB").save(PREVIEW)
