#!/usr/bin/env python3
"""
Render the NOVA 2048 launcher icon (a neon rounded tile with "2048").
Produces a single PNG; committed as app/res/mipmap/ic_launcher.png so the
build does not need Pillow. Regenerate with: python3 tools/make_icon.py <out.png>
"""
import sys
from PIL import Image, ImageDraw, ImageFont

FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def rounded(draw, box, radius, fill):
    draw.rounded_rectangle(box, radius=radius, fill=fill)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "ic_launcher.png"
    S = 432                      # high-res, Android downscales as needed
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # background rounded square with a subtle vertical gradient
    top, bot = (16, 20, 46), (8, 11, 26)
    grad = Image.new("RGB", (1, S))
    for y in range(S):
        t = y / (S - 1)
        grad.putpixel((0, y), tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3)))
    grad = grad.resize((S, S))
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.22), fill=255)
    img.paste(grad, (0, 0), mask)
    d = ImageDraw.Draw(img)

    # inner neon tile
    pad = int(S * 0.16)
    tile = [pad, pad, S - pad, S - pad]
    # glow layers
    for i, col in enumerate([(25, 227, 194, 40), (25, 227, 194, 70)]):
        gp = pad - (2 - i) * 10
        glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        ImageDraw.Draw(glow).rounded_rectangle([gp, gp, S - gp, S - gp],
                                               radius=int(S * 0.14), fill=col)
        img = Image.alpha_composite(img, glow)
    d = ImageDraw.Draw(img)
    rounded(d, tile, int(S * 0.14), (25, 227, 194, 255))

    # "2048" text in dark ink on the neon tile
    try:
        font = ImageFont.truetype(FONT, int(S * 0.26))
    except Exception:
        font = ImageFont.load_default()
    txt = "2048"
    bb = d.textbbox((0, 0), txt, font=font)
    tw, th = bb[2] - bb[0], bb[3] - bb[1]
    d.text(((S - tw) / 2 - bb[0], (S - th) / 2 - bb[1]), txt,
           font=font, fill=(10, 14, 28, 255))

    img.save(out)
    print("wrote", out, img.size)


if __name__ == "__main__":
    main()
