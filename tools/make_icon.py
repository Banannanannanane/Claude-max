#!/usr/bin/env python3
"""Génère l'icône de l'application (manette stylisée sur fond dégradé)."""
import math
import os
import sys

from PIL import Image, ImageDraw

SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

BASE = 512


def make_base():
    img = Image.new("RGBA", (BASE, BASE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # Fond : dégradé diagonal violet -> turquoise, coins arrondis
    grad = Image.new("RGBA", (BASE, BASE))
    gd = ImageDraw.Draw(grad)
    c1 = (124, 92, 255)   # violet
    c2 = (0, 217, 192)    # turquoise
    for y in range(BASE):
        t = y / BASE
        r = int(c1[0] + (c2[0] - c1[0]) * t)
        g = int(c1[1] + (c2[1] - c1[1]) * t)
        b = int(c1[2] + (c2[2] - c1[2]) * t)
        gd.line([(0, y), (BASE, y)], fill=(r, g, b, 255))

    mask = Image.new("L", (BASE, BASE), 0)
    md = ImageDraw.Draw(mask)
    md.rounded_rectangle([8, 8, BASE - 8, BASE - 8], radius=110, fill=255)
    img.paste(grad, (0, 0), mask)

    d = ImageDraw.Draw(img)

    # Corps de la manette
    body = (255, 255, 255, 235)
    cx, cy = BASE // 2, BASE // 2 + 10
    d.rounded_rectangle([cx - 170, cy - 80, cx + 170, cy + 60], radius=70, fill=body)
    d.ellipse([cx - 210, cy - 60, cx - 90, cy + 95], fill=body)
    d.ellipse([cx + 90, cy - 60, cx + 210, cy + 95], fill=body)

    # Croix directionnelle
    pad = (60, 60, 90, 255)
    d.rounded_rectangle([cx - 155, cy - 25, cx - 75, cy + 5], radius=10, fill=pad)
    d.rounded_rectangle([cx - 130, cy - 50, cx - 100, cy + 30], radius=10, fill=pad)

    # Boutons
    for (bx, by, col) in [
        (cx + 115, cy - 40, (255, 93, 108, 255)),
        (cx + 160, cy - 5, (255, 211, 77, 255)),
        (cx + 70, cy - 5, (91, 227, 124, 255)),
        (cx + 115, cy + 30, (77, 171, 255, 255)),
    ]:
        d.ellipse([bx - 20, by - 20, bx + 20, by + 20], fill=col)

    return img


def main(res_dir):
    base = make_base()
    for folder, size in SIZES.items():
        out_dir = os.path.join(res_dir, folder)
        os.makedirs(out_dir, exist_ok=True)
        base.resize((size, size), Image.LANCZOS).save(
            os.path.join(out_dir, "ic_launcher.png"))
        print(f"{folder}/ic_launcher.png ({size}x{size})")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "app/res")
