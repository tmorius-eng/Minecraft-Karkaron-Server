#!/usr/bin/env python3
"""Make the 64x64 server-list icon from the owner's logo art.

    python3 tools/pack/make_server_icon.py <logo image> [--out suld-plugin/src/main/resources/server-icon.png]

The logo is trimmed to its visible pixels (a transparent margin, or a flat background colour taken from the corners), centred on a square, scaled down in
two steps, colour and contrast raised, sharpened, given a 1 px dark rim (64 px is the protocol's fixed icon size: the
art has to be bold to read at it), and saved as an RGBA PNG. The owner's source art is kept as
assets/art/source/server_icon_emblem.<ext> (--keep-source); logo_suld.png is the in-game wordmark (gen_ui.py). The plugin serves the icon unless the server folder has its own server-icon.png
(docs/OPERATIONS.md).
"""
import argparse
from pathlib import Path

from PIL import Image, ImageChops, ImageEnhance, ImageFilter

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


def punch(im: Image.Image, saturation=1.45, contrast=1.35, brightness=1.18) -> Image.Image:
    """Richer colour and contrast: fine, dark art turns to mud at 64 px otherwise."""
    rgb, alpha = im.convert("RGB"), im.getchannel("A")
    rgb = ImageEnhance.Color(rgb).enhance(saturation)
    rgb = ImageEnhance.Contrast(rgb).enhance(contrast)
    rgb = ImageEnhance.Brightness(rgb).enhance(brightness)
    out = rgb.convert("RGBA")
    out.putalpha(alpha)
    return out


def outline(icon: Image.Image, color=(10, 8, 6, 255)) -> Image.Image:
    """A 1 px dark rim around the silhouette, so the icon stands off the launcher's list like the big servers' do."""
    solid = icon.getchannel("A").point(lambda v: 255 if v > 90 else 0)
    ring = ImageChops.subtract(solid.filter(ImageFilter.MaxFilter(3)), solid)
    out = Image.new("RGBA", icon.size, (0, 0, 0, 0))
    out.paste(Image.new("RGBA", icon.size, color), (0, 0), ring)
    out.alpha_composite(icon)
    return out


def make_icon(src: Path, size: int = 64, crop: float = 0.88, vivid: bool = True) -> Image.Image:
    img = Image.open(src).convert("RGBA")
    if img.getchannel("A").getextrema()[0] >= 250:
        bg = background(img)
        if bg is not None:
            img = cut_background(img, bg)
    box = img.getchannel("A").point(lambda a: 255 if a > 24 else 0).getbbox() or (0, 0, img.width, img.height)
    img = img.crop(box)
    if crop < 1:
        # the centre carries the emblem: a little of the outer edge is given up for a bigger, readable core
        w, h = img.size
        cw, ch = int(w * crop), int(h * crop)
        top = max(0, (h - ch) // 2 - int(h * 0.02))
        img = img.crop(((w - cw) // 2, top, (w + cw) // 2, top + ch))
    side = max(img.width, img.height)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(img, ((side - img.width) // 2, (side - img.height) // 2), img)
    if vivid:
        square = punch(square)
    # two steps: sharpen at 4x, then average down (BOX keeps the lines), then a final light sharpen
    mid = square.resize((size * 4, size * 4), Image.LANCZOS).filter(ImageFilter.UnsharpMask(radius=1.4, percent=90, threshold=2))
    icon = mid.resize((size, size), Image.BOX).filter(ImageFilter.UnsharpMask(radius=0.8, percent=120, threshold=1))
    return outline(icon) if vivid else icon


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("logo", type=Path)
    ap.add_argument("--out", type=Path, default=ROOT / "suld-plugin/src/main/resources/server-icon.png")
    ap.add_argument("--crop", type=float, default=0.88, help="keep this centre fraction of the trimmed art (1 = all)")
    ap.add_argument("--plain", action="store_true", help="no colour boost and no outline")
    ap.add_argument("--keep-source", action="store_true", help="copy the source art to assets/art/source/server_icon_emblem.<ext>")
    a = ap.parse_args()
    icon = make_icon(a.logo, crop=a.crop, vivid=not a.plain)
    a.out.parent.mkdir(parents=True, exist_ok=True)
    icon.save(a.out, "PNG", optimize=True)
    if a.keep_source:
        dst = ROOT / "assets/art/source" / ("server_icon_emblem" + a.logo.suffix.lower())
        dst.write_bytes(a.logo.read_bytes())
    print(f"{a.out}: {icon.size[0]}x{icon.size[1]} RGBA")


if __name__ == "__main__":
    main()
