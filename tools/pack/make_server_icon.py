#!/usr/bin/env python3
"""Make the 64x64 server-list icon from the owner's logo art.

    python3 tools/pack/make_server_icon.py <logo image> [--out suld-plugin/src/main/resources/server-icon.png]

The logo is trimmed to its visible pixels (transparent or near-white margins), centred on a square, scaled down in
two steps (high quality at 64 px), lightly sharpened, and saved as an RGBA PNG. The source is kept as
assets/art/source/logo_suld.png. The plugin serves the icon unless the server folder has its own server-icon.png
(docs/OPERATIONS.md).
"""
import argparse
from pathlib import Path

from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parents[2]


def visible_box(img: Image.Image):
    """Bounding box of the logo: alpha where there is alpha, else everything that is not near-white background."""
    rgba = img.convert("RGBA")
    alpha = rgba.getchannel("A")
    if alpha.getextrema()[0] < 250:
        return alpha.point(lambda a: 255 if a > 16 else 0).getbbox()
    # an opaque export on white: drop the near-white margin
    grey = rgba.convert("L")
    return grey.point(lambda v: 255 if v < 245 else 0).getbbox()


def make_icon(src: Path, size: int = 64) -> Image.Image:
    img = Image.open(src).convert("RGBA")
    box = visible_box(img) or (0, 0, img.width, img.height)
    img = img.crop(box)
    if img.getchannel("A").getextrema()[0] >= 250:
        # opaque white margins become transparent so the icon sits on the launcher's dark list
        px = img.load()
        for y in range(img.height):
            for x in range(img.width):
                r, g, b, a = px[x, y]
                if r > 245 and g > 245 and b > 245:
                    px[x, y] = (r, g, b, 0)
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
    ap.add_argument("--keep-source", action="store_true", help="copy the logo to assets/art/source/logo_suld.png")
    a = ap.parse_args()
    icon = make_icon(a.logo)
    a.out.parent.mkdir(parents=True, exist_ok=True)
    icon.save(a.out, "PNG", optimize=True)
    if a.keep_source:
        dst = ROOT / "assets/art/source/logo_suld.png"
        Image.open(a.logo).convert("RGBA").save(dst, "PNG", optimize=True)
    print(f"{a.out}: {icon.size[0]}x{icon.size[1]} RGBA")


if __name__ == "__main__":
    main()
