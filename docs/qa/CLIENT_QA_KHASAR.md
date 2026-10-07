# Client QA: the SÜLD model renderer and Хасар (Stage D1)

The renderer and Хасар were tested on Paper with bots: spawning, despawning, cleanup, the ability flow and the
benchmarks. **Nothing visual has been seen in a real client.** Every line below is **MANUAL_QA_REQUIRED**. Use
Minecraft Java **1.21.11** with the SÜLD resource pack accepted, and record PASS or FAIL with screenshots or short clips
under `docs/qa/screens/khasar/`.

## Setup (op on a dev server)
* Stand on open flat ground at least 30 blocks from Kharkhorum. Run `/suld spawnmob <you> mob.khasar 1`, or start the
  Хасарын Агуй dungeon with a party.
* For a harmless look, use `/suldperf model 1 60 <you> khasar`. It spawns a wandering rig for 60 s; you are invulnerable
  meanwhile.

## A. It is a creature, not a vanilla mob
1. You see Хасар as a large quadruped cave wolf-beast, about 3.4 blocks long. No Ravager is visible, there is no
   name tag over it, and there is no floating vanilla body.
2. Its silhouette reads from 20 blocks: heavy forequarters, mane, long tail, scarred muzzle, bone collar.
3. The textures are crisp pixel art with no missing-texture purple or black, and no seams or gaps between bones.
4. Lighting is correct: no part is fully bright in a dark cave. Look under the body too.

## B. Movement and animation
5. Idle has breathing, head motion and a tail sway. Walking uses a diagonal leg gait, and running is faster and lower.
6. Turning: the whole body turns smoothly with the host. There is no 1-tick snapping and no bone lagging behind.
7. Interpolation is smooth from 5 to 40 blocks. Beyond 48 blocks it may freeze; that is expected.
8. When it walks up and down blocks, the rig follows with no visible jitter.

## C. Combat
9. Hit flash: each hit tints the whole creature red for about 4 ticks, then it returns to normal.
10. Bite: the head and jaw lunge, and damage lands on the bite frame, not before.
11. Pounce: a red ring telegraphs the landing spot and an action-bar warning appears. It leaps, lands, throws a dust
    ring and deals damage. You can dodge it by leaving the ring.
12. Phase 2: the roar animation, a stone debris cone, and knock-back plus slowness.
13. Phase 3: a howl, two cave wolves arrive, then frenzy (faster, ember eyes, a little frost breath).
14. The boss bar shows name, phase and HP. The HUD target frame shows Хасар when you look at it.
15. The hitbox feels fair: hits on the head and body register. The model is longer than the vanilla box, so note any
    "I hit it but nothing happened" spots.

## D. Death and cleanup
16. On death it plays the death clip (collapse), then the model disappears. Nothing is left floating.
17. Leave the area or `/reload`. No stray displays remain anywhere (check with F3 entity counts too).

## E. Frame rate
18. Note your FPS with 1 Хасар and with 5 (`/suldperf model 5 60 <you>`) at 10–20 blocks. Report the drop.
