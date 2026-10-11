#!/usr/bin/env python3
"""Make the 64x64 server-list icon from the owner's logo art.

    python3 tools/pack/make_server_icon.py <logo image> [--out suld-plugin/src/main/resources/server-icon.png]

The logo is trimmed to its visible pixels (a transparent margin, or a flat background colour taken from the corners), centred on a square, scaled down in
two steps (high quality at 64 px), lightly sharpened, and saved as an RGBA PNG. The owner's source art is kept as
assets/art/source/server_icon_emblem.<ext> (--keep-source); logo_suld.png is the in-game wordmark (gen_ui.py). The plugin serves the icon unless the server folder has its own server-icon.png
(docs/OPERATIONS.md).
"""
import argparse
from pathlib import Path

from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parents[2]


def background(img: Image.Image):
    """The flat background colour, when the four corners agree (an opaque export on white, black or a dark field)."""
    rgb = img.convert("RGB")
    w, h = rgb.size
    corners = [rgb.getpixel(p) for p in [(1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)]]
    mean = tuple(sum(c[i] for c in corners) / 4 for i in range(3))
    if all(sum(abs(c[i] - mean[i]) for i in range(3)) < 45 for c in corners):
        return mean
    return None


def cut_background(img: Image.Image, bg, tol: float = 38) -> Image.Image:
    """Pixels near the background colour become transparent; the edge band fades, so there is no hard fringe."""
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            d = abs(r - bg[0]) + abs(g - bg[1]) + abs(b - bg[2])
            if d < tol:
                px[x, y] = (r, g, b, 0)
            elif d < tol * 2:
                px[x, y] = (r, g, b, int(a * (d - tol) / tol))
    return img


def make_icon(src: Path, size: int = 64) -> Image.Image:
    img = Image.open(src).convert("RGBA")
    if img.getchannel("A").getextrema()[0] >= 250:
        bg = background(img)
        if bg is not None:
            img = cut_background(img, bg)
    box = img.getchannel("A").point(lambda a: 255 if a > 24 else 0).getbbox() or (0, 0, img.width, img.height)
    img = img.crop(box)
    side = max(img.width, img.height)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(img, ((side - img.width) // 2, (side - img.height) // 2), img)
    mid = square.resize((size * 4, size * 4), Image.LANCZOS)
    icon = mid.resize((size, size), Image.LANCZOS)
    return icon.filter(ImageFilter.UnsharpMask(radius=0.6, percent=60, threshold=1))


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("logo", type=Path)
    ap.add_argument("--out", type=Path, default=ROOT / "suld-plugin/src/main/resources/server-icon.png")
    ap.add_argument("--keep-source", action="store_true", help="copy the source art to assets/art/source/server_icon_emblem.<ext>")
    a = ap.parse_args()
    icon = make_icon(a.logo)
    a.out.parent.mkdir(parents=True, exist_ok=True)
    icon.save(a.out, "PNG", optimize=True)
    if a.keep_source:
        dst = ROOT / "assets/art/source" / ("server_icon_emblem" + a.logo.suffix.lower())
        dst.write_bytes(a.logo.read_bytes())
    print(f"{a.out}: {icon.size[0]}x{icon.size[1]} RGBA")


if __name__ == "__main__":
    main()
