package com.mystpages.detect;

import com.mystpages.AddonConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Finds modded worldgen structures registered in the structure registry.
 *
 * <p>The structure registry is a <em>dynamic</em> (datapack) registry in 1.21.1 — it is
 * NOT part of {@code BuiltInRegistries}, so it can only be reached through a
 * {@link RegistryAccess} (e.g. from {@code TagsUpdatedEvent#getRegistryAccess()}).</p>
 */
public final class StructureScanner {
    private StructureScanner() {}

    public static List<ResourceLocation> findStructureIds(RegistryAccess registryAccess) {
        Registry<Structure> registry = registryAccess.lookupOrThrow(Registries.STRUCTURE);
        List<ResourceLocation> out = new ArrayList<>();
        for (Structure structure : registry) {
            ResourceLocation id = registry.getKey(structure);
            if (id == null) continue;
            if (id.getNamespace().equals("minecraft")) continue; // vanilla structures already generate
            if (AddonConfig.STRUCTURE_BLACKLIST.get().contains(id.toString())) continue;
            out.add(id);
        }
        out.sort(Comparator.comparing(ResourceLocation::toString));
        return out;
    }
}
