package mn.suld.api.worldbuild.kharkhorum;

import mn.suld.api.worldbuild.ModuleLibrary;

/** The Kharkhorum module library (docs/world/MODULE_LIBRARY.md). */
public final class Kharkhorum {

    private Kharkhorum() {
    }

    public static ModuleLibrary library() {
        return new ModuleLibrary()
                .register(Infra.ROAD)
                .register(Infra.CANAL)
                .register(Infra.BRIDGE)
                .register(Infra.GRAND_STAIR)
                .register(Infra.RETAINING)
                .register(Walls.GATE)
                .register(Walls.WALL)
                .register(Walls.WATCHTOWER)
                .register(Walls.SIDE_GATE)
                .register(Plaza.PLAZA)
                .register(Plaza.MONUMENT)
                .register(Plaza.CLASS_STONES)
                .register(Plaza.RELAY_POST)
                .register(Palace.GATE)
                .register(Palace.HALL)
                .register(Palace.SILVER_TREE)
                .register(Market.STALL)
                .register(Market.SHOP)
                .register(Market.STREET)
                .register(Residential.YURT)
                .register(Residential.YARD)
                .register(Residential.CAMP)
                .register(Craft.FORGE)
                .register(Spiritual.SHRINE)
                .register(Spiritual.CAIRN)
                .register(Landscape.GROVE);
    }
}
