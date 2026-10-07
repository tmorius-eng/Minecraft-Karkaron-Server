#!/usr/bin/env python3
"""Хасар's animation clips (docs/BOSS_VISUAL_SPEC.md §5.3, docs/bosses/KHASAR.md §6), authored as curves and
sampled into the keyframe format of docs/MODEL_RENDERER.md §3 (``[tick, …values]``, linear between keys).

Channels are deltas on the rest pose: ``rot`` Euler degrees added to the bone's rest, ``pos`` blocks in the
parent frame, ``scl`` uniform scale. Scale is only ever put on leaf bones (mane, shards, jaw-less parts), so a
parent's clip scale never leaks into children.

Names and events follow the boss code (suld-plugin/.../dungeon/brain/KhasarBrain.java): ``bite`` → ``bite_hit``,
``pounce`` → ``pounce_land``, ``roar`` → ``roar_wave``, ``howl`` → ``howl``, ``phase_change`` on every phase
change. The base layer is chosen by ModelInstance from the host speed: ``idle`` / ``walk`` / ``run``.

Sign guide (rest frames): rot x > 0 swings a hanging leg's foot backwards, pitches a forward part (neck, head)
down and opens the jaw; for the tail (pointing back) rot x > 0 lifts it. rot y > 0 turns the head to the
creature's left; rot z > 0 rolls the body so its left side goes up.
"""
from __future__ import annotations

import math

LEGS_F = ("leg_fl", "leg_fr")
LEGS_H = ("leg_hl", "leg_hr")


# --------------------------------------------------------------------------------------------- curve helpers

def ease(points):
    """Piecewise cosine-eased curve through (tick, value) points; constant outside."""
    pts = sorted(points)

    def f(t):
        if t <= pts[0][0]:
            return pts[0][1]
        for (t0, v0), (t1, v1) in zip(pts, pts[1:]):
            if t <= t1:
                u = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
                u = 0.5 - 0.5 * math.cos(math.pi * u)
                return v0 + (v1 - v0) * u
        return pts[-1][1]
    return f


def const(v):
    return lambda t: v


def wave(period, amp, phase=0.0, offset=0.0):
    return lambda t: offset + amp * math.sin(2 * math.pi * (t / period + phase))


def add(*fs):
    return lambda t: sum(f(t) for f in fs)


def mul(f, k):
    return lambda t: f(t) * k


def V(x=None, y=None, z=None):
    """A 3-channel function from scalar curves (None = 0)."""
    fx, fy, fz = (c if callable(c) else const(c or 0.0) for c in (x, y, z))
    return lambda t: (fx(t), fy(t), fz(t))


def sample(fn, length, step, digits):
    ts = list(range(0, length + 1, step))
    if ts[-1] != length:
        ts.append(length)
    out = []
    for t in ts:
        v = fn(t)
        v = v if isinstance(v, (tuple, list)) else (v,)
        out.append([t] + [round(x, digits) + 0.0 for x in v])
    # drop interior keys that a straight line through their neighbours already gives
    keep = [out[0]]
    for a, b, c in zip(out, out[1:], out[2:]):
        f = (b[0] - a[0]) / (c[0] - a[0])
        if any(abs(a[k] + (c[k] - a[k]) * f - b[k]) > (0.05 if digits <= 2 else 0.0015) for k in range(1, len(b))):
            keep.append(b)
    if len(out) > 1:
        keep.append(out[-1])
    if all(all(abs(x - (1.0 if len(k) == 2 else 0.0)) < 1e-9 for x in k[1:]) for k in keep):
        return None    # an all-rest channel is left out
    if len(keep) >= 2 and all(k[1:] == keep[0][1:] for k in keep):
        keep = [keep[0]]
    return keep


class Clip:
    def __init__(self, length, loop, step=2):
        self.length, self.loop, self.step = length, loop, step
        self.ch = {}
        self.events = []

    def rot(self, bone, fn):
        self.ch.setdefault(bone, {})["rot"] = fn
        return self

    def pos(self, bone, fn):
        self.ch.setdefault(bone, {})["pos"] = fn
        return self

    def scl(self, bone, fn):
        self.ch.setdefault(bone, {})["scl"] = fn
        return self

    def event(self, tick, name):
        self.events.append([tick, name])
        return self

    def build(self, bones):
        out = {}
        for b, chans in self.ch.items():
            if b not in bones:
                raise SystemExit(f"clip refers to unknown bone {b}")
            e = {}
            for k, fn in chans.items():
                keys = sample(fn, self.length, self.step, 3 if k in ("pos", "scl") else 2)
                if keys:
                    e[k] = keys
            if e:
                out[b] = e
        return {"length": self.length, "loop": self.loop, "bones": out, "events": sorted(self.events)}


# --------------------------------------------------------------------------------------------- gaits

def gait_leg(phase, stance=0.6):
    """(swing angle −1…1, lift 0…1) for a leg at gait phase 0…1: stance sweeps the foot back, swing returns it."""
    p = phase % 1.0
    if p < stance:
        return -1 + 2 * (p / stance), 0.0
    u = (p - stance) / (1 - stance)
    return 1 - 2 * (0.5 - 0.5 * math.cos(math.pi * u)), math.sin(math.pi * u)


def legs(c, length, phases, amp_f, amp_h, lift_f, lift_h, stance=0.6):
    for leg, ph in phases.items():
        front = leg in LEGS_F
        amp, lift = (amp_f, lift_f) if front else (amp_h, lift_h)

        def swing(t, ph=ph, amp=amp):
            return amp * gait_leg(t / length + ph, stance)[0]

        def lf(t, ph=ph):
            return gait_leg(t / length + ph, stance)[1]

        # x rotations down a leg add up, so the paw counter-rotates the whole chain: flat on the ground in the
        # stance, toes curled under in the swing (fore 0.8 × lift, hind 0.5 × lift)
        curl = 0.8 if front else 0.5
        c.rot(leg + "_upper", V(lambda t, s=swing: s(t)))
        c.rot(leg + "_lower", V(lambda t, l=lf, k=lift: k * l(t)))   # wrist folds / hock flexes: the foot lifts
        c.rot(leg + "_paw", V(lambda t, l=lf, s=swing, k=lift, cu=curl: (cu - 1) * k * l(t) - s(t)))


def walk():
    L = 20
    c = Clip(L, True, step=2)
    # trot: diagonal pairs (left fore + right hind, right fore + left hind)
    legs(c, L, {"leg_fl": 0.0, "leg_hr": 0.0, "leg_fr": 0.5, "leg_hl": 0.5}, 17, 15, 30, 26)
    c.pos("root", V(y=wave(L / 2, 0.02, 0.25, 0.022)))
    c.rot("body_front", V(x=wave(L / 2, 1.2, 0.0), z=wave(L, 2.0, 0.0)))
    c.rot("body_rear", V(z=wave(L, -2.5, 0.0)))
    c.rot("neck", V(x=wave(L / 2, -2.0, 0.1), y=wave(L, 2.0, 0.0)))
    c.rot("head", V(x=wave(L / 2, 2.5, 0.2)))
    c.rot("jaw", V(x=wave(L / 2, 1.5, 0.0, 2.0)))
    c.rot("ear_l", V(x=wave(L / 2, 3.0, 0.3)))
    c.rot("ear_r", V(x=wave(L / 2, 3.0, 0.35)))
    c.rot("tail_1", V(x=const(4), y=wave(L, 9.0, 0.0)))
    c.rot("tail_2", V(y=wave(L, 9.0, -0.12)))
    c.rot("tail_3", V(y=wave(L, 11.0, -0.24)))
    c.rot("mane_1", V(x=wave(L / 2, 1.5, 0.3)))
    return c


def run():
    L = 12
    c = Clip(L, True, step=1)
    # bounding gallop: the fore pair lands nearly together, then the hind pair half a cycle later
    legs(c, L, {"leg_fl": 0.0, "leg_fr": 0.08, "leg_hl": 0.5, "leg_hr": 0.58}, 34, 32, 45, 40, stance=0.45)
    c.pos("root", V(y=wave(L, 0.06, 0.0, 0.08)))
    c.rot("body_front", V(x=wave(L, 6.0, 0.25)))
    c.rot("body_rear", V(x=wave(L, -9.0, 0.25)))           # the spine flexes and extends
    c.rot("neck", V(x=add(const(-6), wave(L, -5.0, 0.3))))
    c.rot("head", V(x=add(const(4), wave(L, 4.0, 0.35))))
    c.rot("jaw", V(x=add(const(9), wave(L, 4.0, 0.4))))
    c.rot("ear_l", V(x=const(-22)))
    c.rot("ear_r", V(x=const(-22)))
    c.rot("mane_1", V(x=wave(L, -3.0, 0.45)))             # the hackles flow behind the motion
    c.rot("mane_2", V(x=wave(L, -3.0, 0.5)))
    c.rot("tail_1", V(x=add(const(22), wave(L, 7.0, 0.4))))
    c.rot("tail_2", V(x=wave(L, 8.0, 0.25)))
    c.rot("tail_3", V(x=wave(L, 10.0, 0.1)))
    return c


def idle():
    L = 80
    c = Clip(L, True, step=4)
    c.pos("body_front", V(y=wave(40, 0.012, 0.0)))        # slow, deep breathing
    c.rot("body_front", V(x=wave(40, -0.8, 0.0)))
    c.scl("mane_1", add(const(1.0), wave(40, 0.015, 0.0)))
    c.rot("neck", V(x=wave(80, 2.0, 0.0), y=wave(80, 4.0, 0.25)))
    c.rot("head", V(y=wave(80, 5.0, 0.3), z=wave(80, 1.5, 0.1)))
    c.rot("jaw", V(x=add(const(3), wave(20, 3.0, -0.25))))   # a slow pant, four times per loop
    # the torn ear flicks at tick 50, the other one a beat later
    c.rot("ear_l", V(x=ease([(0, 0), (48, 0), (50, -18), (52, 4), (55, 0), (80, 0)]),
                     z=ease([(0, 0), (48, 0), (50, -12), (53, 0), (80, 0)])))
    c.rot("ear_r", V(x=ease([(0, 0), (52, 0), (54, -10), (57, 0), (80, 0)])))
    c.rot("tail_1", V(y=wave(40, 6.0, 0.0)))
    c.rot("tail_2", V(y=wave(40, 6.0, -0.1)))
    c.rot("tail_3", V(y=wave(40, 8.0, -0.2)))
    for leg in LEGS_F:                                        # weight shifts from paw to paw
        c.rot(leg + "_upper", V(z=wave(80, 1.2 if leg.endswith("l") else -1.2, 0.0)))
    return c


def frenzy_idle():
    """Phase 3 (Галзуурсан): low, open-mouthed, fast breathing, hackles up, ears flat. Frost breath is VFX."""
    L = 40
    c = Clip(L, True, step=2)
    c.pos("root", V(y=const(-0.07)))
    c.pos("body_front", V(y=wave(10, 0.018, 0.0)))
    c.rot("body_front", V(x=add(const(4), wave(10, -1.2, 0.0))))
    c.rot("body_rear", V(x=const(-4)))
    c.rot("neck", V(x=add(const(10), wave(40, 2.0, 0.0)), y=wave(20, 6.0, 0.0)))
    c.rot("head", V(x=const(-8), z=wave(4, 1.2, 0.0)))     # a fine tremble
    c.rot("jaw", V(x=add(const(16), wave(10, 5.0, 0.0))))
    c.rot("ear_l", V(x=const(-28)))
    c.rot("ear_r", V(x=const(-28)))
    c.scl("mane_1", add(const(1.25), wave(10, 0.02, 0.0)))
    c.scl("mane_2", const(1.2))
    c.rot("tail_1", V(x=const(14), y=wave(8, 3.0, 0.0)))
    for leg in LEGS_F:
        c.rot(leg + "_upper", V(x=const(-6)))
        c.rot(leg + "_lower", V(x=const(8)))
    for leg in LEGS_H:
        c.rot(leg + "_upper", V(x=const(-8)))
        c.rot(leg + "_lower", V(x=const(10)))
        c.rot(leg + "_paw", V(x=const(-2)))
    return c


# --------------------------------------------------------------------------------------------- abilities

def bite():
    """Wind-up 5 ticks, snap 2, recover 5 (BOSS_VISUAL_SPEC §5.3); the hit lands on the snap."""
    L = 12
    c = Clip(L, False, step=1)
    c.pos("body_front", V(z=ease([(0, 0), (5, -0.1), (7, 0.24), (12, 0)]), y=ease([(0, 0), (5, -0.04), (7, 0), (12, 0)])))
    c.rot("body_front", V(x=ease([(0, 0), (5, -3), (7, 5), (12, 0)])))
    c.rot("neck", V(x=ease([(0, 0), (5, -14), (7, 16), (12, 0)]), y=ease([(0, 0), (5, 6), (7, -3), (12, 0)])))
    c.rot("head", V(x=ease([(0, 0), (5, -14), (7, 8), (12, 0)]), z=ease([(0, 0), (5, 6), (7, -4), (12, 0)])))
    c.rot("jaw", V(x=ease([(0, 0), (4, 34), (6, 30), (7, 0), (9, 4), (12, 0)])))
    c.rot("ear_l", V(x=ease([(0, 0), (4, -24), (10, -24), (12, 0)])))
    c.rot("ear_r", V(x=ease([(0, 0), (4, -24), (10, -24), (12, 0)])))
    for leg in LEGS_F:                                    # the forelegs brace, then drive
        c.rot(leg + "_upper", V(x=ease([(0, 0), (5, 10), (7, -12), (12, 0)])))
        c.rot(leg + "_paw", V(x=ease([(0, 0), (5, -8), (7, 10), (12, 0)])))
    for leg in LEGS_H:
        c.rot(leg + "_upper", V(x=ease([(0, 0), (5, -6), (7, 8), (12, 0)])))
    c.rot("tail_1", V(x=ease([(0, 0), (5, 10), (8, -6), (12, 0)])))
    c.event(6, "bite_hit")
    return c


def pounce():
    """Crouch (telegraph) 0–6; the host leaps at tick 6 (KhasarBrain) and lands ≈ tick 24 (pounce_land)."""
    L = 34
    c = Clip(L, False, step=1)
    crouch, launch, apex, reach, land, end = 0, 6, 14, 21, 24, 34
    c.pos("root", V(y=ease([(crouch, 0), (5, -0.17), (launch, -0.16), (8, 0), (land, 0), (25, -0.14), (29, -0.03), (end, 0)]),
                    z=ease([(crouch, 0), (5, -0.12), (launch, -0.12), (8, 0), (end, 0)])))
    c.rot("root", V(x=ease([(crouch, 0), (5, 4), (8, -16), (apex, -4), (reach, 8), (land, 6), (28, -2), (end, 0)])))
    c.rot("body_rear", V(x=ease([(crouch, 0), (5, 8), (8, -10), (apex, 0), (land, 4), (end, 0)])))
    c.rot("neck", V(x=ease([(crouch, 0), (5, 12), (8, -10), (apex, -6), (reach, 4), (land, 10), (30, 0), (end, 0)])))
    c.rot("head", V(x=ease([(crouch, 0), (5, -12), (apex, -6), (reach, -10), (land, 6), (end, 0)])))
    c.rot("jaw", V(x=ease([(crouch, 0), (5, 10), (apex, 14), (reach, 36), (land, 30), (26, 0), (end, 0)])))
    c.rot("ear_l", V(x=ease([(crouch, 0), (5, -30), (land, -30), (30, 0), (end, 0)])))
    c.rot("ear_r", V(x=ease([(crouch, 0), (5, -30), (land, -30), (30, 0), (end, 0)])))
    c.scl("mane_1", ease([(crouch, 1.0), (5, 1.22), (land, 1.22), (end, 1.0)]))
    c.scl("mane_2", ease([(crouch, 1.0), (5, 1.22), (land, 1.22), (end, 1.0)]))
    for leg in LEGS_F:
        side = 1 if leg.endswith("l") else -1
        c.rot(leg + "_upper", V(x=ease([(crouch, 0), (5, 34), (7, 36), (10, -30), (reach, -48), (land, -6), (26, 22), (30, 4), (end, 0)]),
                                z=ease([(crouch, 0), (reach, 4 * side), (land, 6 * side), (end, 0)])))
        c.rot(leg + "_lower", V(x=ease([(crouch, 0), (5, -50), (7, -40), (9, 30), (12, 10), (reach, -6), (land, 6), (26, -30), (30, -6), (end, 0)])))
        c.rot(leg + "_paw", V(x=ease([(crouch, 0), (5, 16), (7, 4), (9, 30), (reach, -20), (land, -2), (26, 8), (end, 0)])))
    for leg in LEGS_H:
        c.rot(leg + "_upper", V(x=ease([(crouch, 0), (5, -30), (7, 30), (10, 40), (reach, 10), (land, -14), (28, -16), (end, 0)])))
        c.rot(leg + "_lower", V(x=ease([(crouch, 0), (5, 42), (7, -20), (10, -20), (reach, 10), (land, 28), (28, 16), (end, 0)])))
        c.rot(leg + "_paw", V(x=ease([(crouch, 0), (5, -12), (7, 30), (reach, 0), (land, -14), (28, 0), (end, 0)])))
    c.rot("tail_1", V(x=ease([(crouch, 0), (5, -10), (8, 34), (reach, 30), (land, 10), (end, 0)])))
    c.rot("tail_2", V(x=ease([(crouch, 0), (8, 12), (reach, -8), (land, 12), (end, 0)])))
    c.rot("tail_3", V(x=ease([(crouch, 0), (8, 10), (reach, -10), (land, 14), (end, 0)])))
    c.event(land, "pounce_land")
    return c


def roar():
    """Head up, jaw wide; the shock wave leaves at tick 14 (roar_wave)."""
    L = 40
    c = Clip(L, False, step=2)
    up, peak, hold, back = 0, 12, 30, 40
    c.pos("root", V(y=ease([(up, 0), (peak, 0.06), (hold, 0.06), (back, 0)])))
    c.rot("body_front", V(x=ease([(up, 0), (peak, -9), (hold, -9), (back, 0)])))
    c.rot("body_rear", V(x=ease([(up, 0), (peak, 7), (hold, 7), (back, 0)])))
    c.rot("neck", V(x=ease([(up, 0), (6, 6), (peak, -32), (hold, -30), (back, 0)])))
    c.rot("head", V(x=ease([(up, 0), (peak, -18), (hold, -16), (back, 0)]),
                    z=lambda t: 3.0 * math.sin(t * 1.6) if peak <= t <= hold else 0.0))
    c.rot("jaw", lambda t: (ease([(up, 0), (6, 6), (peak, 38), (hold, 38), (back, 0)])(t)
                            + (2.5 * math.sin(t * 2.2) if peak < t < hold else 0.0), 0.0, 0.0))
    c.rot("ear_l", V(x=ease([(up, 0), (peak, -34), (hold, -34), (back, 0)])))
    c.rot("ear_r", V(x=ease([(up, 0), (peak, -34), (hold, -34), (back, 0)])))
    c.scl("mane_1", ease([(up, 1.0), (peak, 1.2), (hold, 1.2), (back, 1.0)]))
    c.scl("mane_2", ease([(up, 1.0), (peak, 1.2), (hold, 1.2), (back, 1.0)]))
    for leg in LEGS_F:                                    # front paws stay planted while the chest rises
        c.rot(leg + "_upper", V(x=ease([(up, 0), (peak, 9), (hold, 9), (back, 0)])))
    for leg in LEGS_H:
        c.rot(leg + "_upper", V(x=ease([(up, 0), (peak, -7), (hold, -7), (back, 0)])))
        c.rot(leg + "_paw", V(x=ease([(up, 0), (peak, 7), (hold, 7), (back, 0)])))
    c.rot("tail_1", V(x=ease([(up, 0), (peak, 18), (hold, 18), (back, 0)])))
    c.event(14, "roar_wave")
    return c


def howl():
    """Muzzle to the cave roof, a long rounded howl; the pack answers at tick 16 (howl)."""
    L = 44
    c = Clip(L, False, step=2)
    c.pos("root", V(y=ease([(0, 0), (12, -0.08), (34, -0.08), (L, 0)])))
    c.rot("body_front", V(x=ease([(0, 0), (12, -10), (34, -10), (L, 0)])))
    c.rot("body_rear", V(x=ease([(0, 0), (12, 12), (34, 12), (L, 0)])))   # the haunches drop
    c.rot("neck", V(x=ease([(0, 0), (12, -46), (34, -46), (L, 0)])))
    c.rot("head", V(x=ease([(0, 0), (12, -38), (34, -40), (L, 0)])))
    c.rot("jaw", lambda t: (ease([(0, 0), (10, 4), (14, 20), (32, 18), (36, 6), (L, 0)])(t)
                            + (2.0 * math.sin(t * 1.3) if 14 < t < 32 else 0.0), 0.0, 0.0))
    c.rot("ear_l", V(x=ease([(0, 0), (12, -16), (34, -16), (L, 0)])))
    c.rot("ear_r", V(x=ease([(0, 0), (12, -16), (34, -16), (L, 0)])))
    for leg in LEGS_F:
        c.rot(leg + "_upper", V(x=ease([(0, 0), (12, 10), (34, 10), (L, 0)])))
    for leg in LEGS_H:
        c.rot(leg + "_upper", V(x=ease([(0, 0), (12, -26), (34, -26), (L, 0)])))
        c.rot(leg + "_lower", V(x=ease([(0, 0), (12, 22), (34, 22), (L, 0)])))
        c.rot(leg + "_paw", V(x=ease([(0, 0), (12, 4), (34, 4), (L, 0)])))
    c.rot("tail_1", V(x=ease([(0, 0), (12, -14), (34, -14), (L, 0)])))
    c.event(16, "howl")
    return c


def hurt():
    L = 4
    c = Clip(L, False, step=1)
    c.rot("body_front", V(x=ease([(0, 0), (1, -3), (L, 0)]), z=ease([(0, 0), (1, 3), (L, 0)])))
    c.rot("neck", V(x=ease([(0, 0), (1, -8), (L, 0)])))
    c.rot("head", V(y=ease([(0, 0), (1, 8), (L, 0)]), z=ease([(0, 0), (1, -5), (L, 0)])))
    c.rot("jaw", V(x=ease([(0, 0), (1, 12), (L, 0)])))
    c.rot("ear_l", V(x=ease([(0, 0), (1, -26), (L, 0)])))
    c.rot("ear_r", V(x=ease([(0, 0), (1, -26), (L, 0)])))
    c.rot("tail_1", V(x=ease([(0, 0), (1, -8), (L, 0)])))
    return c


def phase_change():
    """Shakes (dust and stone flakes off), then roars as the hackles rise (×1.35); the wave at tick 14."""
    L = 30
    c = Clip(L, False, step=1)
    shake = lambda amp: (lambda t: amp * math.sin(t * math.pi / 1.5) * (1 - t / 10) if t <= 10 else 0.0)  # noqa: E731
    c.rot("body_front", V(x=ease([(0, 0), (10, 0), (16, -8), (24, -8), (L, 0)]), z=shake(7)))
    c.rot("body_rear", V(z=shake(-6)))
    c.rot("neck", V(x=ease([(0, 0), (10, 4), (16, -28), (24, -26), (L, 0)]), z=shake(-6)))
    c.rot("head", V(x=ease([(0, 0), (16, -14), (24, -14), (L, 0)]), z=shake(12)))
    c.rot("jaw", V(x=ease([(0, 0), (10, 4), (14, 36), (24, 36), (L, 0)])))
    c.rot("ear_l", V(x=ease([(0, 0), (6, -10), (14, -32), (24, -32), (L, -20)])))
    c.rot("ear_r", V(x=ease([(0, 0), (6, -10), (14, -32), (24, -32), (L, -20)])))
    c.scl("mane_1", ease([(0, 1.0), (10, 1.1), (16, 1.3), (L, 1.3)]))
    c.scl("mane_2", ease([(0, 1.0), (10, 1.1), (16, 1.25), (L, 1.25)]))
    c.pos("shards_flank", V(y=lambda t: 0.02 * math.sin(t * 2.1) if t <= 10 else 0.0))
    for leg in LEGS_F:
        c.rot(leg + "_upper", V(x=ease([(0, 0), (10, 0), (16, 8), (24, 8), (L, 0)])))
    c.rot("tail_1", V(x=ease([(0, 0), (16, 16), (24, 16), (L, 0)]), y=shake(14)))
    c.event(14, "phase_wave")
    return c


def enrage():
    """The 180 s enrage: a raw howl-roar and a shudder; the red pulse starts at tick 12 (enrage_pulse)."""
    L = 30
    c = Clip(L, False, step=1)
    c.rot("body_front", V(x=ease([(0, 0), (10, -8), (22, -8), (L, 0)]),
                          z=lambda t: 3.0 * math.sin(t * 2.4) if 10 <= t <= 22 else 0.0))
    c.rot("neck", V(x=ease([(0, 0), (10, -40), (22, -40), (L, 0)])))
    c.rot("head", V(x=ease([(0, 0), (10, -28), (22, -28), (L, 0)])))
    c.rot("jaw", V(x=ease([(0, 0), (8, 30), (22, 30), (L, 0)])))
    c.rot("ear_l", V(x=ease([(0, 0), (8, -34), (L, -34)])))
    c.rot("ear_r", V(x=ease([(0, 0), (8, -34), (L, -34)])))
    c.scl("mane_1", ease([(0, 1.0), (10, 1.33), (L, 1.3)]))
    c.scl("mane_2", ease([(0, 1.0), (10, 1.28), (L, 1.25)]))
    for leg in LEGS_H:
        c.rot(leg + "_upper", V(x=ease([(0, 0), (10, -12), (22, -12), (L, 0)])))
        c.rot(leg + "_lower", V(x=ease([(0, 0), (10, 12), (22, 12), (L, 0)])))
    c.rot("tail_1", V(x=ease([(0, 0), (10, 20), (22, 20), (L, 0)])))
    c.event(12, "enrage_pulse")
    return c


# --------------------------------------------------------------------------------------------- arrival / rest / death

def spawn():
    """Climbs out of a fissure: rises from 1.5 blocks below, forepaws clawing, then shakes itself."""
    L = 40
    c = Clip(L, False, step=2)
    c.pos("root", V(y=ease([(0, -1.5), (8, -1.1), (18, -0.55), (26, -0.1), (30, 0), (L, 0)]),
                    z=ease([(0, -0.3), (26, 0), (L, 0)])))
    c.rot("root", V(x=ease([(0, -24), (18, -16), (28, 0), (L, 0)])))
    c.rot("neck", V(x=ease([(0, -10), (18, 6), (28, 0), (L, 0)])))
    c.rot("head", V(y=ease([(0, 0), (30, 0), (33, 10), (36, -10), (L, 0)])))
    c.rot("jaw", V(x=ease([(0, 0), (10, 18), (20, 6), (28, 24), (32, 0), (L, 0)])))
    for leg, ph in (("leg_fl", 0.0), ("leg_fr", 0.5)):     # alternating claws reaching over the rim
        c.rot(leg + "_upper", V(x=lambda t, ph=ph: (-46 * (0.5 + 0.5 * math.sin(2 * math.pi * (t / 10 + ph)))) * (1 - min(1, t / 28))))
        c.rot(leg + "_lower", V(x=lambda t, ph=ph: (24 * (0.5 + 0.5 * math.cos(2 * math.pi * (t / 10 + ph)))) * (1 - min(1, t / 28))))
    for leg in LEGS_H:
        c.rot(leg + "_upper", V(x=ease([(0, 30), (20, 20), (28, 0), (L, 0)])))
    c.rot("body_front", V(z=lambda t: 5.0 * math.sin((t - 32) * 2.0) if 32 <= t <= 38 else 0.0))
    c.rot("tail_1", V(x=ease([(0, -20), (28, -10), (34, 10), (L, 0)])))
    c.scl("mane_1", ease([(0, 1.2), (30, 1.2), (L, 1.0)]))
    c.event(2, "spawn_burst")
    return c


def sleep():
    """Lying in the den before the fight: sphinx pose, head on the forepaws, tail curled round; slow breath."""
    L = 80
    c = Clip(L, True, step=4)
    c.pos("root", V(y=const(-0.72)))
    c.pos("body_front", V(y=wave(40, 0.012, 0.0)))
    c.rot("body_front", V(x=add(const(2), wave(40, -0.6, 0.0))))
    c.rot("body_rear", V(x=const(-3), z=const(4)))
    c.rot("neck", V(x=const(18), y=const(8)))
    c.rot("head", V(x=const(14), y=const(6), z=const(-4)))
    c.rot("jaw", V(x=const(0)))
    c.rot("ear_l", V(x=add(const(-24), wave(80, 2.0, 0.0)), z=ease([(0, 0), (60, 0), (62, -10), (65, 0), (80, 0)])))
    c.rot("ear_r", V(x=const(-24)))
    for leg in LEGS_F:
        c.rot(leg + "_upper", V(x=const(-78)))
        c.rot(leg + "_lower", V(x=const(6)))
        c.rot(leg + "_paw", V(x=const(70)))
    for leg in LEGS_H:
        side = 1 if leg.endswith("l") else -1
        c.rot(leg + "_upper", V(x=const(-62), z=const(8 * side)))
        c.rot(leg + "_lower", V(x=const(48)))
        c.rot(leg + "_paw", V(x=const(14)))
    c.rot("tail_1", V(x=const(30), y=const(40)))
    c.rot("tail_2", V(y=add(const(34), wave(80, 3.0, 0.0))))
    c.rot("tail_3", V(y=const(30)))
    return c


def death():
    """Staggers, the forelegs buckle, it collapses onto its right side; the cave stone falls out of its hide,
    then it sinks 0.3 block (the rig is removed by the renderer after the clip)."""
    L = 50
    c = Clip(L, False, step=2)
    c.pos("root", V(x=ease([(0, 0), (10, 0.05), (24, 0.75), (L, 0.75)]),
                    y=ease([(0, 0), (8, -0.06), (14, -0.12), (24, 0.5), (34, 0.5), (L, 0.2)])))
    c.rot("root", V(z=ease([(0, 0), (6, -4), (12, 6), (16, 14), (24, 82), (27, 76), (30, 80), (L, 80)]),
                    x=ease([(0, 0), (14, 8), (24, 0), (L, 0)])))
    c.rot("body_front", V(z=ease([(0, 0), (4, 6), (8, -5), (12, 0), (L, 0)])))
    c.rot("neck", V(x=ease([(0, 0), (6, -14), (12, 10), (24, -6), (30, 8), (L, 10)]), y=ease([(0, 0), (24, -12), (L, -14)])))
    c.rot("head", V(x=ease([(0, 0), (6, -10), (16, 6), (L, 8)]), z=ease([(0, 0), (4, 10), (8, -10), (12, 0), (L, 0)])))
    c.rot("jaw", V(x=ease([(0, 0), (6, 26), (14, 10), (24, 18), (L, 14)])))
    c.rot("ear_l", V(x=ease([(0, 0), (6, -20), (L, -30)])))
    c.rot("ear_r", V(x=ease([(0, 0), (6, -20), (L, -30)])))
    c.scl("mane_1", ease([(0, 1.0), (24, 1.0), (L, 0.92)]))
    # the cave stone breaks out of the old wounds and drops
    c.pos("shards_flank", V(y=ease([(0, 0), (26, 0), (32, -0.25), (L, -0.35)]),
                            x=ease([(0, 0), (26, 0), (32, 0.25), (L, 0.3)])))
    c.scl("shards_flank", ease([(0, 1.0), (32, 1.0), (38, 0.0), (L, 0.0)]))
    for leg in LEGS_F:
        side = 1 if leg.endswith("l") else -1
        c.rot(leg + "_upper", V(x=ease([(0, 0), (8, 10), (14, -18), (24, -30), (L, -34)]),
                                z=ease([(0, 0), (6, 6 * side), (14, -6 * side), (L, 0)])))
        c.rot(leg + "_lower", V(x=ease([(0, 0), (14, 40), (24, 20), (L, 14)])))
        c.rot(leg + "_paw", V(x=ease([(0, 0), (24, 20), (L, 24)])))
    for leg in LEGS_H:
        c.rot(leg + "_upper", V(x=ease([(0, 0), (12, -14), (24, 20), (L, 26)])))
        c.rot(leg + "_lower", V(x=ease([(0, 0), (12, 30), (24, -10), (L, -12)])))
        c.rot(leg + "_paw", V(x=ease([(0, 0), (24, 16), (L, 20)])))
    c.rot("tail_1", V(x=ease([(0, 0), (12, -10), (24, 12), (L, 20)]), y=ease([(0, 0), (24, 18), (L, 20)])))
    c.rot("tail_2", V(y=ease([(0, 0), (24, 10), (L, 12)])))
    c.event(24, "death_fall")
    c.event(32, "stone_fall")
    return c


CLIPS = {
    "idle": idle, "walk": walk, "run": run, "frenzy_idle": frenzy_idle, "sleep": sleep,
    "bite": bite, "pounce": pounce, "roar": roar, "howl": howl,
    "hurt": hurt, "phase_change": phase_change, "enrage": enrage, "spawn": spawn, "death": death,
}


def build(rig):
    bones = {b["id"] for b in rig["bones"]}
    return {name: fn().build(bones) for name, fn in CLIPS.items()}
