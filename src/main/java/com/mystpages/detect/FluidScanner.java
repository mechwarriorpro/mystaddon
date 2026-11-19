package com.mystpages.detect;

import com.mystpages.AddonConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Finds modded fluids that have a real block form and positive density (gases and
 * vacuum-like fluids are skipped). Still/flowing variants are deduplicated by block.
 */
public final class FluidScanner {
    private FluidScanner() {}

    public static List<ResourceLocation> findFluidIds() {
        Map<ResourceLocation, ResourceLocation> byBlock = new LinkedHashMap<>(); // blockId -> fluidId
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid);
            if (fluidId == null) continue;
            if (fluid == Fluids.EMPTY || fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER
                    || fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA) continue;
            if (AddonConfig.FLUID_BLACKLIST.get().contains(fluidId.toString())) continue;
            try {
                if (fluid.getFluidType().getDensity() <= 0) continue;
            } catch (Throwable t) {
                continue;
            }
            Block block = fluid.defaultFluidState().createLegacyBlock().getBlock();
            if (block == null || block == Blocks.AIR) continue;
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
            if (blockId == null) continue;
            byBlock.putIfAbsent(blockId, fluidId);
        }
        List<ResourceLocation> out = new ArrayList<>(byBlock.values());
        out.sort(Comparator.comparing(ResourceLocation::toString));
        return out;
    }
}
