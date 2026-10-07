package mn.suld.sim;

import mn.suld.api.clazz.PlayerClass;

/**
 * Development aid: the median player's power by level under the proposed rules (dps, health, armour, regen per
 * class), used to derive the mob health/damage formulas in {@link ProposedRules} (docs/DIFFICULTY_CURVE.md).
 */
public final class Calibrate {

    public static void main(String[] args) {
        int runs = args.length > 0 ? Integer.parseInt(args[0]) : 3;
        Rules r = new ProposedRules();
        double[][] sum = new double[61][4];
        int[] n = new int[61];
        for (PlayerClass c : PlayerClass.values()) {
            for (int i = 0; i < runs; i++) {
                Engine.Result res = Engine.run(r, Profile.hardcore(), c, 1000 + i * 7 + c.ordinal(), 30);
                res.player().powerAtLevel.forEach((lv, v) -> {
                    for (int k = 0; k < 4; k++) sum[lv][k] += v[k];
                    n[lv]++;
                });
            }
        }
        System.out.println("level dps hp armor regen");
        for (int lv = 2; lv <= 60; lv++) {
            if (n[lv] == 0) continue;
            System.out.printf("%d %.1f %.1f %.1f %.2f%n", lv, sum[lv][0] / n[lv], sum[lv][1] / n[lv], sum[lv][2] / n[lv], sum[lv][3] / n[lv]);
        }
    }
}
