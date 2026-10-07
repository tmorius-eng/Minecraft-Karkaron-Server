#!/usr/bin/env python3
"""Animation clips of the fauna rigs (tools/model/fauna.py), one set per body plan, original keyframes.

Every rig gets ``idle``, ``walk``, ``run`` (not the wraiths: they glide), ``attack`` (with an ``attack_hit`` event at
the strike), ``hurt`` and ``death``. The renderer plays idle/walk/run by speed and death on death; the plugin plays
``attack`` when the mob lands a hit. Channels on bones a rig does not have are dropped (a wraith has no legs).
"""
from __future__ import annotations

from khasar_clips import Clip, V, add, const, ease, wave


class FClip(Clip):
    def build(self, bones):
        self.ch = {b: c for b, c in self.ch.items() if b in bones}
        return super().build(bones)


# ============================================================================================ quadrupeds

def quad_walk(L, amp, bob, tail=True):
    c = FClip(L, True, step=2)
    for leg, ph in (("leg_fl", 0.0), ("leg_hr", 0.0), ("leg_fr", 0.5), ("leg_hl", 0.5)):
        c.rot(leg, V(x=wave(L, amp, ph)))
    c.pos("body", V(y=wave(L / 2, bob, 0.25)))
    c.rot("body", V(z=wave(L, 1.5, 0.0)))
    c.rot("head", V(x=wave(L / 2, 3.0, 0.2), y=wave(L, 3.0, 0.0)))
    if tail:
        c.rot("tail", V(y=wave(L, 12.0, 0.0)))
    return c


def quad_idle():
    L = 60
    c = FClip(L, True, step=4)
    c.pos("body", V(y=wave(30, 0.012, 0.0)))
    c.rot("head", V(x=wave(60, 3.0, 0.0), y=wave(60, 10.0, 0.25)))
    c.rot("jaw", V(x=add(const(4), wave(15, 4.0, 0.0))))
    c.rot("tail", V(y=wave(30, 8.0, 0.0)))
    return c


def canine_attack():
    L = 12
    c = FClip(L, False, step=1)
    c.pos("body", V(z=ease([(0, 0), (4, -0.08), (6, 0.22), (L, 0)])))
    c.rot("body", V(x=ease([(0, 0), (4, -6), (6, 6), (L, 0)])))
    c.rot("head", V(x=ease([(0, 0), (4, -14), (6, 12), (L, 0)])))
    c.rot("jaw", V(x=ease([(0, 0), (4, 30), (6, 2), (8, 10), (L, 0)])))
    c.rot("leg_fl", V(x=ease([(0, 0), (4, -20), (6, 18), (L, 0)])))
    c.rot("leg_fr", V(x=ease([(0, 0), (4, -16), (6, 14), (L, 0)])))
    c.event(6, "attack_hit")
    return c


def bear_attack():
    """A rearing swipe: the bear lifts its forequarters, the left forepaw rakes down across."""
    L = 16
    c = FClip(L, False, step=1)
    c.rot("body", V(x=ease([(0, 0), (6, -22), (9, -6), (L, 0)])))
    c.pos("body", V(y=ease([(0, 0), (6, 0.18), (9, 0.04), (L, 0)])))
    c.rot("leg_fl", V(x=ease([(0, 0), (6, -95), (9, 25), (12, 10), (L, 0)]), z=ease([(0, 0), (6, 20), (9, -10), (L, 0)])))
    c.rot("leg_fr", V(x=ease([(0, 0), (6, -40), (10, 10), (L, 0)])))
    c.rot("head", V(x=ease([(0, 0), (6, 10), (9, -10), (L, 0)])))
    c.rot("jaw", V(x=ease([(0, 0), (5, 28), (9, 12), (L, 0)])))
    c.event(9, "attack_hit")
    return c


def quad_hurt():
    L = 8
    c = FClip(L, False, step=1)
    c.pos("body", V(z=ease([(0, 0), (2, -0.12), (L, 0)])))
    c.rot("head", V(x=ease([(0, 0), (2, -12), (L, 0)]), z=ease([(0, 0), (2, 8), (L, 0)])))
    return c


def quad_death():
    L = 30
    c = FClip(L, False, step=2)
    c.rot("root", V(z=ease([(0, 0), (6, -6), (14, 84), (L, 86)])))
    c.pos("root", V(y=ease([(0, 0), (14, 0.25), (L, 0.15)])))
    c.rot("head", V(x=ease([(0, 0), (8, -15), (L, 12)])))
    c.rot("jaw", V(x=ease([(0, 0), (8, 25), (L, 18)])))
    for leg in ("leg_fl", "leg_fr", "leg_hl", "leg_hr"):
        c.rot(leg, V(x=ease([(0, 0), (14, 20), (L, 28)])))
    c.rot("tail", V(y=ease([(0, 0), (14, 20), (L, 24)])))
    c.event(14, "death_fall")
    return c


# ============================================================================================ humanoids

def hum_idle(wraith):
    L = 60
    c = FClip(L, True, step=4)
    c.rot("torso", V(x=wave(30, 1.2, 0.0)))
    c.rot("head", V(y=wave(60, 12.0, 0.25), x=wave(60, 3.0, 0.0)))
    c.rot("arm_l", V(x=wave(30, 2.5, 0.0), z=add(const(-4), wave(30, 1.5, 0.1))))
    c.rot("arm_r", V(x=wave(30, -2.5, 0.0), z=add(const(4), wave(30, -1.5, 0.1))))
    if wraith:
        c.pos("hips", V(y=wave(40, 0.08, 0.0, 0.06)))
        c.rot("hips", V(x=wave(40, 3.0, 0.2), z=wave(60, 2.0, 0.0)))
    return c


def hum_walk(L, amp, wraith):
    c = FClip(L, True, step=2)
    if wraith:
        c.pos("hips", V(y=wave(L, 0.06, 0.0, 0.08)))
        c.rot("hips", V(x=add(const(10), wave(L, 4.0, 0.25))))       # leans into the glide, the robe trailing
        c.rot("arm_l", V(x=add(const(-25), wave(L, 6.0, 0.0))))
        c.rot("arm_r", V(x=add(const(-25), wave(L, 6.0, 0.5))))
        return c
    c.rot("leg_l", V(x=wave(L, amp, 0.0)))
    c.rot("leg_r", V(x=wave(L, amp, 0.5)))
    c.rot("arm_l", V(x=wave(L, amp * 0.7, 0.5)))
    c.rot("arm_r", V(x=wave(L, amp * 0.5, 0.0)))
    c.pos("hips", V(y=wave(L / 2, 0.03, 0.25)))
    c.rot("torso", V(y=wave(L, 4.0, 0.0), x=const(4)))
    c.rot("head", V(y=wave(L, -3.0, 0.0)))
    return c


def hum_attack(wraith):
    """An overhead cut with the right arm (a wraith claws with both): wind up 6 ticks, strike 2, recover."""
    L = 14
    c = FClip(L, False, step=1)
    c.rot("torso", V(y=ease([(0, 0), (6, 24), (8, -18), (L, 0)]), x=ease([(0, 0), (6, -4), (8, 10), (L, 0)])))
    c.rot("arm_r", V(x=ease([(0, 0), (6, -150), (8, -30), (10, -20), (L, 0)]), z=ease([(0, 0), (6, 10), (8, -10), (L, 0)])))
    if wraith:
        c.rot("arm_l", V(x=ease([(0, 0), (6, -140), (8, -25), (L, 0)])))
        c.pos("hips", V(z=ease([(0, 0), (6, -0.1), (8, 0.3), (L, 0)])))
    else:
        c.rot("arm_l", V(x=ease([(0, 0), (6, -20), (8, 20), (L, 0)])))
        c.rot("leg_l", V(x=ease([(0, 0), (6, -12), (8, -24), (L, 0)])))
        c.rot("leg_r", V(x=ease([(0, 0), (6, 8), (8, 16), (L, 0)])))
    c.event(8, "attack_hit")
    return c


def hum_hurt():
    L = 8
    c = FClip(L, False, step=1)
    c.rot("torso", V(x=ease([(0, 0), (2, -10), (L, 0)])))
    c.rot("head", V(x=ease([(0, 0), (2, -14), (L, 0)])))
    return c


def hum_death(wraith):
    L = 34
    c = FClip(L, False, step=2)
    if wraith:
        # a spirit does not fall: it rises a little, shrinks and is gone
        c.pos("root", V(y=ease([(0, 0), (L, 0.8)])))
        c.scl("hips", ease([(0, 1.0), (20, 0.9), (L, 0.05)]))
        c.rot("root", V(y=ease([(0, 0), (L, 180)])))
    else:
        c.rot("root", V(x=ease([(0, 0), (6, 8), (18, -86), (L, -88)])))
        c.pos("root", V(y=ease([(0, 0), (18, 0.12), (L, 0.1)])))
        c.rot("arm_l", V(z=ease([(0, 0), (18, -60), (L, -70)])))
        c.rot("arm_r", V(z=ease([(0, 0), (18, 60), (L, 70)])))
        c.rot("head", V(x=ease([(0, 0), (10, 18), (L, -10)])))
    c.event(18, "death_fall")
    return c


# ============================================================================================ arachnid

def ara_idle():
    L = 40
    c = FClip(L, True, step=2)
    c.rot("claw_l", V(y=wave(40, 6.0, 0.0)))
    c.rot("claw_r", V(y=wave(40, -6.0, 0.1)))
    c.rot("tail_1", V(x=wave(40, 4.0, 0.0)))
    c.rot("tail_3", V(x=wave(20, 6.0, 0.2)))
    c.pos("body", V(y=wave(40, 0.01, 0.0)))
    return c


def ara_walk(L, amp):
    c = FClip(L, True, step=1)
    c.rot("legs_l", V(y=wave(L, amp, 0.0), z=wave(L / 2, 4.0, 0.0)))
    c.rot("legs_r", V(y=wave(L, amp, 0.5), z=wave(L / 2, -4.0, 0.0)))
    c.pos("body", V(y=wave(L / 2, 0.015, 0.25)))
    c.rot("tail_1", V(y=wave(L, 5.0, 0.0)))
    return c


def ara_attack():
    """The sting: the tail draws back, then lashes forward over the body."""
    L = 12
    c = FClip(L, False, step=1)
    c.rot("tail_1", V(x=ease([(0, 0), (5, -15), (7, 30), (L, 0)])))
    c.rot("tail_2", V(x=ease([(0, 0), (5, -10), (7, 25), (L, 0)])))
    c.rot("tail_3", V(x=ease([(0, 0), (5, -20), (7, 35), (L, 0)])))
    c.rot("claw_l", V(y=ease([(0, 0), (5, 20), (7, -10), (L, 0)])))
    c.rot("claw_r", V(y=ease([(0, 0), (5, -20), (7, 10), (L, 0)])))
    c.event(7, "attack_hit")
    return c


def ara_death():
    L = 26
    c = FClip(L, False, step=2)
    c.rot("root", V(z=ease([(0, 0), (12, 175), (L, 180)])))
    c.pos("root", V(y=ease([(0, 0), (8, 0.3), (14, 0.2), (L, 0.18)])))
    c.rot("legs_l", V(z=ease([(0, 0), (L, -30)])))
    c.rot("legs_r", V(z=ease([(0, 0), (L, 30)])))
    c.event(12, "death_fall")
    return c


def clips_for(rig):
    plan = rig.get("plan")
    bones = {b["id"] for b in rig["bones"]}
    if plan in ("canine", "ursine"):
        attack = canine_attack if plan == "canine" else bear_attack
        slow = 1.25 if plan == "ursine" else 1.0
        out = {"idle": quad_idle(), "walk": quad_walk(int(20 * slow), 24, 0.025, plan == "canine"),
               "run": quad_walk(int(12 * slow), 38, 0.05, plan == "canine"), "attack": attack(),
               "hurt": quad_hurt(), "death": quad_death()}
    elif plan == "humanoid":
        wraith = "leg_l" not in bones
        out = {"idle": hum_idle(wraith), "walk": hum_walk(22, 26, wraith), "attack": hum_attack(wraith),
               "hurt": hum_hurt(), "death": hum_death(wraith)}
        if not wraith:
            out["run"] = hum_walk(14, 40, False)
    elif plan == "arachnid":
        out = {"idle": ara_idle(), "walk": ara_walk(10, 14), "run": ara_walk(6, 18), "attack": ara_attack(),
               "hurt": quad_hurt(), "death": ara_death()}
    else:
        raise SystemExit(f"rig {rig['id']}: unknown body plan {plan!r}")
    return {k: v.build(bones) for k, v in out.items()}
