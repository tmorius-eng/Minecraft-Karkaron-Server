#!/usr/bin/env python3
"""Renders the pack's corner-minimap shader (rendertype_text.vsh/.fsh) on the GPU and checks it finds the minimap
(docs/WORLD_MAP.md) both in a map's own 128 x 128 texture (vanilla) and inside a 2048 map atlas of 128-texel cells
between eight other maps (ImmediatelyFast's map_atlas_generation, on by default in Fabulously Optimized: the shader
used to check 128 x 128 textures only, and the four minimap frames hung in front of the player as floating maps).
Also: the neighbouring maps stay where they hang, the marker row is hidden, and glyphs are never taken for the map.

Needs moderngl and an OpenGL 3.3 context (Mesa's software renderer is enough: CI runs it under Xvfb). Without them
it prints SKIP and exits with SKIPPED (75), which run_all.py reports as skipped, never as passed, and counts as a
failure when SULD_REQUIRE_GPU=1 (CI).
  py -m pip install moderngl numpy
  py tools/validation/check_minimap_shader.py [shader dir]
"""
from __future__ import annotations

import os
import re
import struct
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SKIPPED = 75  # run_all.py: not run here (no moderngl or no OpenGL 3.3), neither passed nor failed
DEFAULT = os.path.join(HERE, "..", "..", "resourcepack", "assets", "minecraft", "shaders", "core")

try:
    import numpy as np
    import moderngl
except ImportError:
    print("SKIP check_minimap_shader: moderngl/numpy not installed (py -m pip install moderngl numpy)")
    sys.exit(SKIPPED)

d = sys.argv[1] if len(sys.argv) > 1 else DEFAULT
STUB_V = """
uniform mat4 ModelViewMat; uniform mat4 ProjMat; uniform vec2 ScreenSize; uniform vec4 ColorModulator;
float fog_spherical_distance(vec3 p) { return length(p); }
float fog_cylindrical_distance(vec3 p) { return length(p.xz); }
"""
STUB_F = """
uniform vec4 ColorModulator; uniform float FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd; uniform vec4 FogColor;
vec4 apply_fog(vec4 c, float a, float b, float s0, float e0, float s1, float e1, vec4 fc) { return c; }
"""
def src(name, stub):
    with open(os.path.join(d, name), encoding="utf-8") as f:
        s = f.read()
    s = re.sub(r"#moj_import <[^>]+>\n", "", s)
    return s.replace("#version 330\n", "#version 330\n" + stub, 1)

try:
    ctx = moderngl.create_standalone_context(require=330)
except Exception as e:  # no GPU / no GL 3.3 (CI)
    print(f"SKIP check_minimap_shader: no OpenGL 3.3 context ({e})")
    sys.exit(SKIPPED)
prog = ctx.program(vertex_shader=src("rendertype_text.vsh", STUB_V), fragment_shader=src("rendertype_text.fsh", STUB_F))
W, H = 1920, 1080
prog["ScreenSize"].value = (W, H)
if "ColorModulator" in prog: prog["ColorModulator"].value = (1, 1, 1, 1)
ident = tuple(np.eye(4, dtype="f4").flatten())
prog["ModelViewMat"].value = ident
prog["ProjMat"].value = ident
light = np.full((16, 16, 4), 255, np.uint8)
light_tex = ctx.texture((16, 16), 4, light.tobytes()); light_tex.filter = (moderngl.NEAREST, moderngl.NEAREST)
fbo = ctx.simple_framebuffer((W, H)); fbo.use()

MARK = [(178, 76, 216), (48, 115, 112), (176, 168, 54), (81, 27, 27)]
MAP_RGB, OTHER_RGB, CLEAR = (10, 200, 30), (200, 10, 10), (0, 0, 0)

def minimap():
    img = np.zeros((128, 128, 4), np.uint8); img[:, :] = (*MAP_RGB, 255)
    for i, c in enumerate(MARK): img[0, i] = (*c, 255)
    return img

def draw(tex_img, quads):
    """quads: list of (clip x0, y0, x1, y1, u0, v0, u1, v1), each drawn in vanilla's order BL, BR, TR, TL."""
    h, w = tex_img.shape[:2]
    tex = ctx.texture((w, h), 4, np.ascontiguousarray(tex_img).tobytes()); tex.filter = (moderngl.NEAREST, moderngl.NEAREST)
    vb, ib = bytearray(), []
    for n, (x0, y0, x1, y1, u0, v0, u1, v1) in enumerate(quads):
        # texture row 0 is the image's top; v grows downward like Minecraft's
        for (x, y, u, v) in ((x0, y0, u0, v1), (x1, y0, u1, v1), (x1, y1, u1, v0), (x0, y1, u0, v0)):
            vb += struct.pack("3f4f2f2i", x, y, 0, 1, 1, 1, 1, u, v, 240, 240)
        b = 4 * n; ib += [b, b + 1, b + 2, b + 2, b + 3, b]
    vbo = ctx.buffer(bytes(vb)); ibo = ctx.buffer(np.array(ib, "i4").tobytes())
    vao = ctx.vertex_array(prog, [(vbo, "3f 4f 2f 2i", "Position", "Color", "UV0", "UV2")], ibo)
    tex.use(0); light_tex.use(2); prog["Sampler0"].value = 0; prog["Sampler2"].value = 2
    fbo.clear(*[c / 255 for c in CLEAR], 1.0)
    vao.render(moderngl.TRIANGLES)
    return np.frombuffer(fbo.read(components=3), np.uint8).reshape(H, W, 3)[::-1]  # row 0 = top of screen

def px(img, x, y): return tuple(int(c) for c in img[y, x])
fails = []
def check(name, cond):
    print(("PASS " if cond else "FAIL ") + name); cond or fails.append(name)

# the minimap quad sits far right in the world (clip x 0.5..0.9) so its in-world spot is distinct from the corner
WORLD = (0.5, -0.3, 0.9, 0.3)
scale = max(1, H // 450); margin = 3 * scale

# 1. vanilla: the map's own 128 x 128 texture
out = draw(minimap(), [(*WORLD, 0, 0, 1, 1)])
check("vanilla: corner shows the map", px(out, margin + 60, margin + 60) == MAP_RGB)
check("vanilla: nothing left in the world", px(out, int(W * 0.85), H // 2) == CLEAR)
check("vanilla: marker row hidden", px(out, margin + 1, margin - scale // 2 - 1) == CLEAR)

# 2. ImmediatelyFast atlas: 2048, minimap in cell (3, 1), other maps in all eight neighbouring cells
A, C = 2048, 128
atlas = np.zeros((A, A, 4), np.uint8)
mx, my = 3, 1
for cy in range(0, 3):
    for cx in range(2, 5):
        atlas[cy * C:(cy + 1) * C, cx * C:(cx + 1) * C] = (*OTHER_RGB, 255)
atlas[my * C:(my + 1) * C, mx * C:(mx + 1) * C] = minimap()
def cell(cx, cy): return (cx * C / A, cy * C / A, (cx + 1) * C / A, (cy + 1) * C / A)
quads = []
for cy in range(0, 3):
    for cx in range(2, 5):
        if (cx, cy) == (mx, my): continue
        x0 = -0.9 + (cx - 2) * 0.2; y0 = -0.9 + (2 - cy) * 0.2
        quads.append((x0, y0, x0 + 0.18, y0 + 0.18, *cell(cx, cy)))
quads.insert(4, (*WORLD, *cell(mx, my)))  # drawn in the middle of the batch, like a real frame between others
out = draw(atlas, quads)
check("atlas: corner shows the map", px(out, margin + 60, margin + 60) == MAP_RGB)
check("atlas: nothing left in the world", px(out, int(W * 0.85), H // 2) == CLEAR)
check("atlas: marker row hidden", px(out, margin + 1, margin - scale // 2 - 1) == CLEAR)
for i, q in enumerate(q for q in quads if q[:4] != WORLD):
    cxp = int((q[0] + q[2]) / 2 * W / 2 + W / 2); cyp = int(H / 2 - (q[1] + q[3]) / 2 * H / 2)
    check(f"atlas: neighbour map {i} stays in place", px(out, cxp, cyp) == OTHER_RGB)
# no stretched triangle of a neighbour reaches the corner region outside the minimap
check("atlas: no neighbour pixels in the corner", not np.any(np.all(out[:300, :300] == OTHER_RGB, axis=2)))

# 3. glyphs on a 256 font page are never taken for the minimap. A wrongly taken glyph may move only one vertex (a
# stretched triangle), so the whole corner must stay empty and the glyph's own four corners drawn where it is.
GLYPH = (-0.2, -0.1, 0.2, 0.1)

def glyph(name, page, uv):
    out = draw(page, [(*GLYPH, *uv)])
    xs = (int((GLYPH[0] + 1) / 2 * W) + 4, int((GLYPH[2] + 1) / 2 * W) - 4)
    ys = (int(H / 2 - GLYPH[3] * H / 2) + 4, int(H / 2 - GLYPH[1] * H / 2) - 4)
    check(f"glyph ({name}): drawn whole where it is", all(px(out, x, y) != CLEAR for x in xs for y in ys))
    check(f"glyph ({name}): nothing in the corner", not np.any(np.any(out[:300, :300] != CLEAR, axis=2)))

font = np.zeros((256, 256, 4), np.uint8); font[:, :] = (255, 255, 255, 255)
for i, c in enumerate(MARK): font[0, i] = (*c, 255)  # the first glyph of a page, its first texels the marker colours
glyph("at a 256 font page's corner, marker texels", font, (0, 0, 8 / 256, 8 / 256))  # font pages are never atlases
big = np.zeros((2048, 2048, 4), np.uint8); big[:, :] = (255, 255, 255, 255)
glyph("a whole 128-texel cell of a big texture, no marker", big, (0, 0, 128 / 2048, 128 / 2048))  # the marker rule

print("ALL PASS" if not fails else f"{len(fails)} FAILED")
sys.exit(1 if fails else 0)
