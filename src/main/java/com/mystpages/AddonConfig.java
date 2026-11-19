package com.mystpages;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config for MystPages: detection blacklists plus generation tuning for the
 * pages that get applied inside Mystcraft Reborn Age dimensions.
 */
public final class AddonConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue INCLUDE_VANILLA_ORES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ORE_BLACKLIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FLUID_BLACKLIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STRUCTURE_BLACKLIST;
    public static final ModConfigSpec.IntValue ORE_VEINS_PER_CHUNK;
    public static final ModConfigSpec.IntValue ORE_VEIN_RADIUS;
    public static final ModConfigSpec.IntValue ORE_MAX_Y;
    public static final ModConfigSpec.BooleanValue REPLACE_SEA_FLUID;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("MystPages - modded ore/fluid/structure pages for Mystcraft Reborn")
                .push("detection");

        INCLUDE_VANILLA_ORES = builder
                .comment("Also offer pages for vanilla Minecraft ores.")
                .define("includeVanillaOres", false);

        ORE_BLACKLIST = builder
                .comment("Block ids (e.g. 'somemod:some_ore') excluded from ore pages.")
                .defineList("oreBlacklist", List.of(), o -> o instanceof String);

        FLUID_BLACKLIST = builder
                .comment("Fluid ids (e.g. 'somemod:molten_iron') excluded from fluid pages.",
                        "Water and lava are always excluded (vanilla already covers them).")
                .defineList("fluidBlacklist", List.of(), o -> o instanceof String);

        STRUCTURE_BLACKLIST = builder
                .comment("Structure ids (e.g. 'somemod:cool_ruin') excluded from structure pages.")
                .defineList("structureBlacklist", List.of(), o -> o instanceof String);

        builder.pop().push("generation");

        ORE_VEINS_PER_CHUNK = builder
                .comment("Ore veins placed per chunk in an Age for each ore page on the book.")
                .defineInRange("oreVeinsPerChunk", 2, 0, 32);

        ORE_VEIN_RADIUS = builder
                .comment("Radius (in blocks) of each placed ore vein.")
                .defineInRange("oreVeinRadius", 2, 1, 8);

        ORE_MAX_Y = builder
                .comment("Highest Y level at which ore veins are placed in an Age.")
                .defineInRange("oreMaxY", 60, -60, 320);

        REPLACE_SEA_FLUID = builder
                .comment("If true, a fluid page replaces the Age's ocean/lake water with that fluid.")
                .define("replaceSeaFluid", true);

        builder.pop();

        SPEC = builder.build();
    }

    private AddonConfig() {}
}
