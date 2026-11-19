package com.mystpages.loot;

import com.mojang.serialization.MapCodec;
import com.mystpages.MystPages;
import com.mystpages.page.PageType;
import com.mystpages.worldgen.AgeEffects;
import com.mystpages.worldgen.AgePagesData;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;

/**
 * Global loot modifier backing the {@code loot} page: scales every loot-table-generated
 * stack (structure chests, ore/block drops, mob drops, …) by the Age's loot percentage.
 *
 * <p>Registered as {@code mystpages:loot_abundance} and enabled through
 * {@code data/neoforge/loot_modifiers/global_loot_modifiers.json}. Because it runs with a
 * {@link LootContext}, it can check the dimension the loot is generated in and only apply
 * inside Mystcraft Reborn Age dimensions that carry a loot page.</p>
 */
public final class LootAbundanceModifier implements IGlobalLootModifier {
    public static final MapCodec<LootAbundanceModifier> CODEC = MapCodec.unit(new LootAbundanceModifier());

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }

    @Override
    public ObjectArrayList<ItemStack> apply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        ServerLevel level = context.getLevel();
        ResourceLocation dim = level.dimension().location();
        if (!dim.getNamespace().equals(MystPages.HOST_MOD_ID)) return generatedLoot;

        List<ResourceLocation> pages = AgePagesData.get(level).getPages(dim, PageType.LOOT);
        if (pages.isEmpty()) return generatedLoot;

        int percent = AgeEffects.parsePercent(PageType.LOOT.valueOf(pages.get(0)), 100);
        if (percent == 100) return generatedLoot;
        if (percent <= 0) {
            generatedLoot.clear();
            return generatedLoot;
        }

        double factor = percent / 100.0;
        RandomSource random = context.getRandom();
        ObjectArrayList<ItemStack> out = new ObjectArrayList<>(generatedLoot.size());
        for (ItemStack stack : generatedLoot) {
            // Fractional counts are kept probabilistically so e.g. 10% does not always
            // round a single item down to zero.
            double scaled = stack.getCount() * factor;
            int whole = (int) scaled;
            double frac = scaled - whole;
            int count = whole + (random.nextDouble() < frac ? 1 : 0);
            if (count <= 0) continue;
            stack.setCount(count);
            out.add(stack);
        }
        return out;
    }
}
