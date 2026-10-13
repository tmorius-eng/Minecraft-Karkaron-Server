package mn.suld.api.dungeon.heroic;

/**
 * «Тэнгэрийн Гэсгээлт Агуй»: the weather and spirits a heroic key brings into a hall (docs/HEROIC_DUNGEONS.md). Each
 * one is a rule the party must play around, not a bigger number: the numbers are {@link HeroicLadder}'s. The names
 * are the steppe's own hardships (VERIFIED words: зуд is the killing winter, ган the drought); what each does in a hall
 * is the game's (INSPIRED).
 */
public enum SteppeAffix {

    /**
     * «Цагаан Зуд»: every {@link #ZUD_PERIOD_SECONDS} s a blizzard sweeps the hall. Whoever carries no fire (a torch,
     * a lantern or a campfire in either hand) and stands within {@link #ZUD_HEAT_RADIUS} blocks of no open fire (a
     * campfire, a torch, fire, lava; the halls' own lanterns do not warm) is slowed by {@link #ZUD_SLOW} and takes
     * frostbite.
     */
    ZUD("Цагаан Зуд", "30 секунд тутам цасан шуурга: гартаа бамбар, дэнлүү барь эсвэл галын дэргэд бай, эс бөгөөс 40 % удааширч хөлдөнө."),
    /**
     * «Галт Ган»: a hall mob bursts into embers when it dies (fire around its corpse for a moment), and the party's
     * class resource (mana, stamina) comes back at half the rate.
     */
    GAN("Галт Ган", "Үхсэн мангас галт цогоор тэсрэнэ; ид, тэнхээ хоёр дахин удаан нөхөгдөнө."),
    /**
     * «Чонын Омог»: a hall mob within {@link #PACK_RADIUS} blocks of others hits {@link #PACK_BONUS} harder for each
     * of them (at most {@link #PACK_MAX} of them count): pull them apart, never in a heap.
     */
    CHONYN_OMOG("Чонын Омог", "8 блок дотор бөөгнөрсөн мангас бүр хамт олныхоо тоогоор 15 %-иар хүчтэй цохино."),
    /**
     * «Тэнгэрийн Ниргэлт»: during the boss fight, marks appear under the party; {@link #STRIKE_TELEGRAPH_MS} ms later
     * lightning strikes each of them. Step out of the mark.
     */
    TENGERIIN_NIRGELT("Тэнгэрийн Ниргэлт", "Эзэнтэй тулахад газарт тэмдэг гарч, 1.5 секундын дараа аянга буух тул тэмдгээс гар.");

    public static final int ZUD_PERIOD_SECONDS = 30;
    public static final double ZUD_HEAT_RADIUS = 4.0;
    /** Movement slowed by this share while frozen. */
    public static final double ZUD_SLOW = 0.40;
    public static final int ZUD_SLOW_SECONDS = 8;
    /** Frostbite: this share of the player's max health. */
    public static final double ZUD_FROSTBITE = 0.08;

    public static final double EMBER_RADIUS = 3.0;
    /** An ember burst: this share of the dying mob's attack, to everyone in the radius. */
    public static final double EMBER_DAMAGE = 0.5;
    public static final double GAN_REGEN = 0.5;

    public static final double PACK_RADIUS = 8.0;
    public static final double PACK_BONUS = 0.15;
    public static final int PACK_MAX = 4;

    public static final int STRIKE_PERIOD_SECONDS = 9;
    public static final long STRIKE_TELEGRAPH_MS = 1500;
    public static final double STRIKE_RADIUS = 2.0;
    /** A strike: this share of the player's max health. */
    public static final double STRIKE_DAMAGE = 0.25;

    private final String displayName;
    private final String description;

    SteppeAffix(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    /** «Чонын Омог»: the damage multiplier of a mob that has {@code neighbours} other hall mobs within the radius. */
    public static double packMultiplier(int neighbours) {
        return 1.0 + PACK_BONUS * Math.max(0, Math.min(PACK_MAX, neighbours));
    }
}
