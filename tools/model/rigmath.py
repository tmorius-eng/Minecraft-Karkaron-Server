#!/usr/bin/env python3
"""The model-renderer maths of docs/MODEL_RENDERER.md §2, in Python (a line-for-line twin of
suld-api/.../model/Quat.java, Xf.java, Clip.sample and Sampler.pose/display). Used by export_rig.py (rest pose for
painting) and preview_rig.py (posing the exported assets).

* Euler degrees [x, y, z] → q = qz · qy · qx (X turns first, then Y, then Z, about the parent's axes).
* Bone local  L = T(pivot − parentPivot + clipPos) · R(rest ⊕ clipRot) · S(clipScale);  M = M_parent · L.
* Display: translation = M·0, rotation = rot(M), scale = scale(M) × boneScale; the rig is turned by −yaw about +Y
  and scaled by the rig scale.
"""
from __future__ import annotations

import math


def axis_angle(ax, ay, az, deg):
    h = math.radians(deg) / 2
    s = math.sin(h)
    return (ax * s, ay * s, az * s, math.cos(h))


def qmul(a, b):
    """Hamilton product a·b (apply b first, then a)."""
    ax, ay, az, aw = a
    bx, by, bz, bw = b
    return (aw * bx + ax * bw + ay * bz - az * by,
            aw * by - ax * bz + ay * bw + az * bx,
            aw * bz + ax * by - ay * bx + az * bw,
            aw * bw - ax * bx - ay * by - az * bz)


def euler(x, y, z):
    return qmul(axis_angle(0, 0, 1, z), qmul(axis_angle(0, 1, 0, y), axis_angle(1, 0, 0, x)))


def qnorm(q):
    n = math.sqrt(sum(c * c for c in q))
    return (0.0, 0.0, 0.0, 1.0) if n == 0 else tuple(c / n for c in q)


def qrot(q, v):
    x, y, z, w = q
    vx, vy, vz = v
    tx, ty, tz = 2 * (y * vz - z * vy), 2 * (z * vx - x * vz), 2 * (x * vy - y * vx)
    return (vx + w * tx + (y * tz - z * ty), vy + w * ty + (z * tx - x * tz), vz + w * tz + (x * ty - y * tx))


class Xf:
    """p ↦ t + q·(s·p)."""
    __slots__ = ("t", "q", "s")

    def __init__(self, t=(0.0, 0.0, 0.0), q=(0.0, 0.0, 0.0, 1.0), s=1.0):
        self.t, self.q, self.s = tuple(t), tuple(q), float(s)

    def then(self, c: "Xf") -> "Xf":
        r = qrot(self.q, tuple(v * self.s for v in c.t))
        return Xf(tuple(a + b for a, b in zip(self.t, r)), qnorm(qmul(self.q, c.q)), self.s * c.s)

    def apply(self, p):
        r = qrot(self.q, tuple(v * self.s for v in p))
        return tuple(a + b for a, b in zip(self.t, r))


def sample(keys, t, width, dflt):
    """Clip.sample: linear between [tick, v…] keys, clamped at both ends."""
    if not keys:
        return list(dflt)
    if t <= keys[0][0]:
        return list(keys[0][1:1 + width])
    if t >= keys[-1][0]:
        return list(keys[-1][1:1 + width])
    for a, b in zip(keys, keys[1:]):
        if t <= b[0]:
            f = 1.0 if b[0] == a[0] else (t - a[0]) / (b[0] - a[0])
            return [a[k + 1] + (b[k + 1] - a[k + 1]) * f for k in range(width)]
    return list(keys[-1][1:1 + width])


def clip_local(clip, t):
    L = max(1, int(clip.get("length", 1)))
    if clip.get("loop"):
        m = t % L
        return m + L if m < 0 else m
    return max(0, min(L, t))


def pose(bones, clip=None, t=0.0):
    """Sampler.pose with a single layer: model-space Xf per bone (bones: rig.json bone dicts, parents first)."""
    out = {}
    tl = clip_local(clip, t) if clip else 0
    for b in bones:
        ch = (clip or {}).get("bones", {}).get(b["id"], {}) if clip else {}
        rot = sample(ch.get("rot", []), tl, 3, (0, 0, 0))
        pos = sample(ch.get("pos", []), tl, 3, (0, 0, 0))
        scl = sample(ch.get("scl", []), tl, 1, (1,))
        pp = out[b["parent"]][1] if b.get("parent") else (0.0, 0.0, 0.0)
        piv = b.get("pivot", [0, 0, 0])
        rest = b.get("rest", [0, 0, 0])
        local = Xf((piv[0] - pp[0] + pos[0], piv[1] - pp[1] + pos[1], piv[2] - pp[2] + pos[2]),
                   euler(rest[0] + rot[0], rest[1] + rot[1], rest[2] + rot[2]), scl[0])
        m = out[b["parent"]][0].then(local) if b.get("parent") else local
        out[b["id"]] = (m, tuple(piv))
    return {k: v[0] for k, v in out.items()}


def display(bones, model, yaw=0.0, rig_scale=1.0):
    """Sampler.display: the per-bone display Xf (translation, leftRotation, uniform scale)."""
    world = Xf((0, 0, 0), axis_angle(0, 1, 0, -yaw), rig_scale)
    out = {}
    for b in bones:
        m = world.then(model[b["id"]])
        out[b["id"]] = Xf(m.t, m.q, m.s * float(b.get("scale", 1.0)))
    return out
