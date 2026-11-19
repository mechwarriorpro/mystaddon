package com.mystpages.detect;

import com.mystpages.AddonConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;

/** Finds modded ore blocks: registry-name based ("ore" in the path) and c:ores tag based. */
public final class OreScanner {
    private OreScanner() {}

    public static List<ResourceLocation> findOreBlocksByName(boolean includeVanilla) {
        List<ResourceLocation> out = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (id == null) continue;
            if (!includeVanilla && id.getNamespace().equals("minecraft")) continue;
            if (!id.getPath().contains("ore")) continue;
            if (AddonConfig.ORE_BLACKLIST.get().contains(id.toString())) continue;
            out.add(id);
        }
        out.sort(Comparator.comparing(ResourceLocation::toString));
        return out;
    }

    public static List<ResourceLocation> findTaggedOreBlocks(boolean includeVanilla) {
        List<ResourceLocation> out = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (id == null) continue;
            if (!includeVanilla && id.getNamespace().equals("minecraft")) continue;
            boolean tagged;
            try {
                tagged = block.builtInRegistryHolder().is(Tags.Blocks.ORES);
            } catch (Throwable t) {
                tagged = false; // tags not bound yet; the name-based pass already ran
            }
            if (!tagged) continue;
            if (AddonConfig.ORE_BLACKLIST.get().contains(id.toString())) continue;
            out.add(id);
        }
        out.sort(Comparator.comparing(ResourceLocation::toString));
        return out;
    }
}
