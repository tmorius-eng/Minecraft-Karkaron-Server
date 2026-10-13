#!/usr/bin/env python3
"""HD Warden for the training grounds (docs/TRAINING_GROUNDS.md): the vanilla Warden textures at 4x with a «voxel bevel»,
every texel becomes a 4x4 tile with a light top-left and a dark bottom-right edge and a little fixed grain, so the
Warden reads like the owner's HD reference (each pixel a small bevelled cube). The glowing layers (bioluminescence,
heart, pulsating spots) get a brighter bevel. UVs are unchanged (Minecraft scales entity textures), so the vanilla model
uses them as they are; every Warden on the server looks this way.

    python3 tools/pack/hd_warden.py <minecraft client 1.21.11 .jar> [out dir]

Deterministic: the same jar always gives the same files (the grain is seeded by each file's name).
"""
import io
import os
import sys
import zipfile
import zlib

import numpy as np
from PIL import Image

LAYERS = ('warden', 'warden_bioluminescent_layer', 'warden_heart', 'warden_pulsating_spots_1', 'warden_pulsating_spots_2')
OUT = 'resourcepack/assets/minecraft/textures/entity/warden'


def bevel(img, scale=4, emissive=False, seed=11):
    a = np.asarray(img.convert('RGBA'), dtype=np.float32)
    h, w = a.shape[:2]
    big = np.repeat(np.repeat(a, scale, axis=0), scale, axis=1)
    rgb, al = big[..., :3], big[..., 3:]
    yy, xx = np.mgrid[0:h * scale, 0:w * scale]
    sy, sx = yy % scale, xx % scale
    rng = np.random.default_rng(seed)
    grain = 1.0 + rng.uniform(-0.035, 0.035, size=(h * scale, w * scale, 1)).astype(np.float32)
    f = np.ones((h * scale, w * scale, 1), dtype=np.float32)
    hi, lo = (1.22, 0.86) if emissive else (1.16, 0.74)
    f[(sy == 0) | (sx == 0)] = hi
    f[(sy == scale - 1) | (sx == scale - 1)] = lo
    f[(sy == 0) & (sx == scale - 1)] = 1.0
    f[(sy == scale - 1) & (sx == 0)] = 1.0
    lum = rgb.mean(axis=2, keepdims=True)
    lift = np.where(f > 1, 10.0 * (lum < 40), 0.0)  # near-black texels still show a highlight
    out = np.clip(rgb * f * grain + lift, 0, 255)
    return Image.fromarray(np.concatenate([out, al], axis=2).astype(np.uint8), 'RGBA')


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    jar = zipfile.ZipFile(sys.argv[1])
    out = sys.argv[2] if len(sys.argv) > 2 else OUT
    os.makedirs(out, exist_ok=True)
    for name in LAYERS:
        src = Image.open(io.BytesIO(jar.read('assets/minecraft/textures/entity/warden/%s.png' % name))).convert('RGBA')
        hd = bevel(src, 4, emissive=name != 'warden', seed=zlib.crc32(name.encode()) % 1000)
        path = os.path.join(out, name + '.png')
        hd.save(path, optimize=True)
        print(path, hd.size, os.path.getsize(path), 'bytes')


if __name__ == '__main__':
    main()
