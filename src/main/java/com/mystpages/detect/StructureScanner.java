package com.mystpages.detect;

import com.mystpages.AddonConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Finds modded worldgen structures registered in the structure registry. */
public final class StructureScanner {
    private StructureScanner() {}

    public static List<ResourceLocation> findStructureIds() {
        List<ResourceLocation> out = new ArrayList<>();
        for (Structure structure : BuiltInRegistries.STRUCTURE) {
            ResourceLocation id = BuiltInRegistries.STRUCTURE.getKey(structure);
            if (id == null) continue;
            if (id.getNamespace().equals("minecraft")) continue; // vanilla structures already generate
            if (AddonConfig.STRUCTURE_BLACKLIST.get().contains(id.toString())) continue;
            out.add(id);
        }
        out.sort(Comparator.comparing(ResourceLocation::toString));
        return out;
    }
}
