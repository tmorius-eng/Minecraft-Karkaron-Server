package mn.suld.api.worldbuild;

/** Build passes, applied strictly in this order. Detail never goes in before the macro form is right. */
public enum Pass {
    TERRAIN(1), WALLS_GATES_ROADS(2), DISTRICT_FOOTPRINTS(3), LANDMARKS(4), SHELLS(5), ROOFS_DETAIL(6),
    DECORATION(7), LANDSCAPING(8), INTERIORS(9), LIGHTING(10), GAMEPLAY(11), SECRETS(12);

    public final int number;

    Pass(int number) {
        this.number = number;
    }
}
