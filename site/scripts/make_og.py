"""Generate the Open Graph card (1200x630) for the launch site.

Run with the host's Pillow-enabled interpreter:
    keybridge-server/venv/Scripts/python.exe site/scripts/make_og.py
Outputs site/public/og.png. Re-run only if the brand or tagline changes.
"""
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

W, H = 1200, 630
SS = 3  # supersample the keycap for crisp edges
OUT = os.path.join(os.path.dirname(__file__), "..", "public", "og.png")


def font(name, size):
    for path in (f"C:/Windows/Fonts/{name}",):
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def vgradient(w, h, top, bot):
    col = Image.new("RGB", (1, h))
    for y in range(h):
        t = y / max(1, h - 1)
        col.putpixel((0, y), tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3)))
    return col.resize((w, h))


def keycap(size):
    s = 512 * SS
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    def rr(x, y, w, h, r, fill):
        d.rounded_rectangle([x * SS, y * SS, (x + w) * SS, (y + h) * SS], radius=r * SS, fill=fill)

    # navy keycap base (the "side" of the cap)
    rr(101, 122, 310, 294, 66, (26, 46, 74, 255))

    # gradient top face, masked to a rounded rect
    g = vgradient(310 * SS, 294 * SS, (44, 169, 188), (16, 145, 166)).convert("RGBA")
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [101 * SS, 104 * SS, (101 + 310) * SS, (104 + 294) * SS], radius=66 * SS, fill=255
    )
    face = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    face.paste(g, (101 * SS, 104 * SS))
    img = Image.alpha_composite(img, Image.composite(face, Image.new("RGBA", (s, s), (0, 0, 0, 0)), mask))

    d = ImageDraw.Draw(img)
    # top highlight
    d.rounded_rectangle(
        [131 * SS, 130 * SS, (131 + 250) * SS, (130 + 118) * SS], radius=40 * SS, fill=(255, 255, 255, 26)
    )

    # white K — three round-capped strokes
    w = 38 * SS
    segs = [((205, 178), (205, 330)), ((205, 256), (302, 178)), ((205, 256), (302, 330))]
    for (x1, y1), (x2, y2) in segs:
        d.line([x1 * SS, y1 * SS, x2 * SS, y2 * SS], fill=(255, 255, 255, 255), width=w)
    for x, y in [(205, 178), (205, 330), (205, 256), (302, 178), (302, 330)]:
        r = w / 2
        d.ellipse([x * SS - r, y * SS - r, x * SS + r, y * SS + r], fill=(255, 255, 255, 255))

    return img.resize((size, size), Image.LANCZOS)


# background gradient
bg = vgradient(W, H, (26, 31, 42), (15, 17, 23)).convert("RGBA")

# teal glow, top-right
glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
ImageDraw.Draw(glow).ellipse([W - 620, -300, W + 220, 420], fill=(44, 169, 188, 95))
bg = Image.alpha_composite(bg, glow.filter(ImageFilter.GaussianBlur(170)))

draw = ImageDraw.Draw(bg)

# keycap
cap = keycap(248)
cap_x, cap_y = 96, (H - 248) // 2
bg.alpha_composite(cap, (cap_x, cap_y))

# text block
tx = cap_x + 248 + 60
draw.text((tx, 196), "KeyBridge", font=font("segoeuib.ttf", 96), fill=(238, 241, 246))
draw.text((tx, 318), "Your phone is now your PC keyboard.", font=font("segoeui.ttf", 40), fill=(196, 203, 218))
# teal accent rule
draw.rounded_rectangle([tx, 300, tx + 96, 305], radius=3, fill=(70, 198, 218))
draw.text((tx, 392), "dog-broad.github.io/KeyBridge", font=font("seguisb.ttf", 28), fill=(70, 198, 218))

bg.convert("RGB").save(os.path.normpath(OUT), "PNG")
print("wrote", os.path.normpath(OUT))
