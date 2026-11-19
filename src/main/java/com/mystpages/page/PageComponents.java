package com.mystpages.page;

import com.mojang.serialization.Codec;
import com.mystpages.MystPages;
import java.util.List;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Data components that store MystPages pages on a Descriptive Book — the same pattern
 * Mystcraft Reborn uses for its {@code biome_symbols} component. Each component holds a
 * list of target registry ids (ore block id / fluid id / structure id).
 */
public final class PageComponents {
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<ResourceLocation>>> ORE_PAGES =
            MystPages.COMPONENTS.registerComponentType("ore_pages", builder -> builder
                    .persistent(Codec.list(ResourceLocation.CODEC))
                    .networkSynchronized(ByteBufCodecs.fromCodec(Codec.list(ResourceLocation.CODEC))));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<ResourceLocation>>> FLUID_PAGES =
            MystPages.COMPONENTS.registerComponentType("fluid_pages", builder -> builder
                    .persistent(Codec.list(ResourceLocation.CODEC))
                    .networkSynchronized(ByteBufCodecs.fromCodec(Codec.list(ResourceLocation.CODEC))));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<ResourceLocation>>> STRUCTURE_PAGES =
            MystPages.COMPONENTS.registerComponentType("structure_pages", builder -> builder
                    .persistent(Codec.list(ResourceLocation.CODEC))
                    .networkSynchronized(ByteBufCodecs.fromCodec(Codec.list(ResourceLocation.CODEC))));

    private PageComponents() {}

    /** Called from the mod constructor so the static holders are registered. */
    public static void init() {
        // Touching the class is enough; static initializers run on first access.
    }

    public static DeferredHolder<DataComponentType<?>, DataComponentType<List<ResourceLocation>>> forType(PageType type) {
        return switch (type) {
            case ORE -> ORE_PAGES;
            case FLUID -> FLUID_PAGES;
            case STRUCTURE -> STRUCTURE_PAGES;
        };
    }
}
