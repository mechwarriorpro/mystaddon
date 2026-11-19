package com.mystpages.worldgen;

import com.mystpages.AddonConfig;
import com.mystpages.MystPages;
import com.mystpages.page.BookPages;
import com.mystpages.page.PageCatalog;
import com.mystpages.page.PageType;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Applies MystPages pages inside Mystcraft Reborn Age dimensions.
 *
 * <p>Flow: when a player right-clicks with a Descriptive Book carrying pages, the pages
 * are captured (the host mod creates the Age dimension synchronously inside that same
 * use call). When the new {@code mystcraft_reborn:age_*} level loads, the captured pages
 * are bound to that dimension in {@link AgePagesData}. Afterwards, each generated chunk
 * in such a dimension gets its ore veins placed and its sea fluid swapped according to
 * the bound pages.</p>
 */
public final class AgeGenerationBridge {
    /** Pages captured at book-use time, waiting for the new Age level to load. */
    private static final Map<UUID, EnumMap<PageType, List<ResourceLocation>>> PENDING = new ConcurrentHashMap<>();
    /** Dimensions for which we already logged the structure-page note. */
    private static final Set<ResourceLocation> STRUCTURE_LOGGED = ConcurrentHashMap.newKeySet();

    private AgeGenerationBridge() {}

    public static void onTagsUpdated(TagsUpdatedEvent event) {
        PageCatalog.refreshTaggedOres();
    }

    /** Capture the book's pages right before Mystcraft Reborn consumes the book to create an Age. */
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) return;
        if (!BookPages.isDescriptiveBook(event.getItemStack())) return;
        EnumMap<PageType, List<ResourceLocation>> captured = new EnumMap<>(PageType.class);
        for (PageType type : PageType.values()) {
            List<ResourceLocation> pages = BookPages.get(event.getItemStack(), type);
            if (!pages.isEmpty()) captured.put(type, pages);
        }
        if (!captured.isEmpty()) {
            PENDING.put(event.getEntity().getUUID(), captured);
        }
    }

    /** Bind captured pages to the newly created Age dimension. */
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        ResourceLocation dimensionId = serverLevel.dimension().location();
        if (!dimensionId.getNamespace().equals(MystPages.HOST_MOD_ID)) return;

        AgePagesData data = AgePagesData.get(serverLevel);
        if (data.hasDimension(dimensionId)) return;

        EnumMap<PageType, List<ResourceLocation>> captured = PENDING.values().stream().findFirst().orElse(null);
        if (captured == null) return;
        PENDING.clear();
        for (Map.Entry<PageType, List<ResourceLocation>> entry : captured.entrySet()) {
            data.putPages(dimensionId, entry.getKey(), entry.getValue());
        }
        MystPages.LOGGER.info("MystPages bound pages to Age {}: ores={}, fluids={}, structures={}",
                dimensionId,
                data.getPages(dimensionId, PageType.ORE).size(),
                data.getPages(dimensionId, PageType.FLUID).size(),
                data.getPages(dimensionId, PageType.STRUCTURE).size());
    }

    /** Apply ore/fluid pages when a chunk in an Age dimension finishes loading. */
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        ResourceLocation dimensionId = serverLevel.dimension().location();
        if (!dimensionId.getNamespace().equals(MystPages.HOST_MOD_ID)) return;

        AgePagesData data = AgePagesData.get(serverLevel);
        List<ResourceLocation> ores = data.getPages(dimensionId, PageType.ORE);
        List<ResourceLocation> fluids = data.getPages(dimensionId, PageType.FLUID);
        List<ResourceLocation> structures = data.getPages(dimensionId, PageType.STRUCTURE);
        if (ores.isEmpty() && fluids.isEmpty() && structures.isEmpty()) return;

        ChunkAccess chunk = event.getChunk();
        ChunkPos chunkPos = chunk.getPos();
        long packed = chunkPos.toLong();
        if (!data.markChunkPlaced(dimensionId, packed)) return; // already processed (e.g. world reload)

        if (!ores.isEmpty()) {
            placeOreVeins(serverLevel, chunk, ores);
        }
        if (!fluids.isEmpty() && AddonConfig.REPLACE_SEA_FLUID.get()) {
            replaceSeaFluid(serverLevel, chunk, fluids.get(0));
        }
        if (!structures.isEmpty() && STRUCTURE_LOGGED.add(dimensionId)) {
            MystPages.LOGGER.info(
                    "MystPages: Age {} has {} structure page(s) {}. Structure placement inside Ages requires " +
                    "the host mod to expose structure-set injection; pages are recorded on the Age for now.",
                    dimensionId, structures.size(), structures);
        }
    }

    /** Places ore veins (blobs of the ore block replacing stone/deepslate) in the chunk. */
    private static void placeOreVeins(ServerLevel level, ChunkAccess chunk, List<ResourceLocation> orePages) {
        RandomSource random = level.getRandom();
        int veins = AddonConfig.ORE_VEINS_PER_CHUNK.get();
        int radius = AddonConfig.ORE_VEIN_RADIUS.get();
        int maxY = Math.max(AddonConfig.ORE_MAX_Y.get(), chunk.getMinBuildHeight() + 5);
        int minY = chunk.getMinBuildHeight() + 4;
        int baseX = chunkPosBase(chunk, true);
        int baseZ = chunkPosBase(chunk, false);

        for (ResourceLocation oreId : orePages) {
            Block oreBlock = BuiltInRegistries.BLOCK.get(oreId);
            if (oreBlock == null || oreBlock == Blocks.AIR) continue;
            BlockState oreState = oreBlock.defaultBlockState();
            for (int v = 0; v < veins; v++) {
                int cx = baseX + random.nextInt(16);
                int cz = baseZ + random.nextInt(16);
                int cy = minY + random.nextInt(Math.max(1, maxY - minY));
                int r2 = radius * radius;
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dy = -radius; dy <= radius; dy++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            if (dx * dx + dy * dy + dz * dz > r2) continue;
                            BlockPos pos = new BlockPos(cx + dx, cy + dy, cz + dz);
                            BlockState current = chunk.getBlockState(pos);
                            if (current.is(Blocks.STONE) || current.is(Blocks.DEEPSLATE)) {
                                chunk.setBlockState(pos, oreState, false);
                            }
                        }
                    }
                }
            }
        }
    }

    /** Replaces ocean/lake water below sea level in the chunk with the page's fluid. */
    private static void replaceSeaFluid(ServerLevel level, ChunkAccess chunk, ResourceLocation fluidId) {
        Fluid fluid = BuiltInRegistries.FLUID.get(fluidId);
        if (fluid == null) return;
        BlockState fluidState = fluid.defaultFluidState().createLegacyBlock();
        if (fluidState.isAir()) return;
        int seaLevel = level.getSeaLevel();
        int baseX = chunkPosBase(chunk, true);
        int baseZ = chunkPosBase(chunk, false);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = chunk.getMinBuildHeight(); y < seaLevel; y++) {
                    BlockPos pos = new BlockPos(baseX + x, y, baseZ + z);
                    if (chunk.getBlockState(pos).is(Blocks.WATER)) {
                        chunk.setBlockState(pos, fluidState, false);
                    }
                }
            }
        }
    }

    private static int chunkPosBase(ChunkAccess chunk, boolean x) {
        return x ? chunk.getPos().getMinBlockX() : chunk.getPos().getMinBlockZ();
    }
}
