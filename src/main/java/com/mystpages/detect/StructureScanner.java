package com.mystpages.detect;

import com.mystpages.AddonConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;

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
        // The structure registry is only present in server-side (SERVER_DATA_LOAD) registry
        // accesses; on the client during the configuration phase it is missing entirely.
        // In 1.21.1 lookup() returns Optional<RegistryLookup<T>> (not a full Registry), so
        // iterate the holders' keys instead — an absent registry is simply "no pages".
        var lookup = registryAccess.lookup(Registries.STRUCTURE);
        if (lookup.isEmpty()) {
            return List.of();
        }
        List<ResourceLocation> out = new ArrayList<>();
        lookup.get().listElements().forEach(holder -> {
            ResourceLocation id = holder.key().location();
            if (id.getNamespace().equals("minecraft")) return; // vanilla structures already generate
            if (AddonConfig.STRUCTURE_BLACKLIST.get().contains(id.toString())) return;
            out.add(id);
        });
        out.sort(Comparator.comparing(ResourceLocation::toString));
        return out;
    }
}
