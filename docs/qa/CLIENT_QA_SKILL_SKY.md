# Client QA: full-screen skill tree (Тэнгэрийн мод)

Use a 1.21.11 client with the SÜLD pack, on flat ground, out of combat, with a class picked and a few skill points.

1. Run `/skills`. The view becomes a night sky with the yurt and the campfire; no world blocks are visible.
2. The tree fans upward from a centre node, and the universal nodes sit below it.
   * Icons are readable, not mirrored and not back-to-front.
   * Frames are dark with light borders.
3. Point the crosshair at a white-framed node: a glow ring appears, and a purple tooltip shows its name and effects.
4. Left click it: the frame turns gold, its icon gets a glint, the edge from the root turns gold, and the header points
   go down by its cost.
5. Right click a learned node: it is refunded, or the reason it can't be is shown.
6. Hold W / S / A / D: the view pans smoothly. Scroll the wheel: it zooms in and out. The hotbar selection doesn't move.
7. The header (top) and footer (bottom) stay fixed on screen while panning.
8. Press Shift: you are back exactly where you stood. Fall distance is 0, so there's no damage.
9. Try `/skills` after hitting a mob (within 8 s): it is refused, and the chest map opens.
10. Note your FPS while the tree is open. Note anything that looks wrong: z-fighting, flicker, a wrong colour, text that
    is too big or too small.
