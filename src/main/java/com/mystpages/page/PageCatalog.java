package com.mystpages.page;

import com.mystpages.AddonConfig;
import com.mystpages.MystPages;
import com.mystpages.detect.FluidScanner;
import com.mystpages.detect.OreScanner;
import com.mystpages.detect.StructureScanner;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * The catalog of detected modded pages, one list per {@link PageType}.
 *
 * <p>Built lazily once the built-in registries are frozen (common setup), plus a
 * tag-driven refresh of the ore list once block tags are bound. A page id is simply the
 * target registry id: the ore block id, the fluid id, or the structure id.</p>
 */
public final class PageCatalog {
    private static final Map<PageType, List<ResourceLocation>> CATALOG = new EnumMap<>(PageType.class);
    private static volatile boolean scanned;

    private PageCatalog() {}

    /**
     * Scan of the built-in registries (ores, fluids); called during common setup.
     * Structure pages are scanned separately via {@link #scanStructures} once a
     * {@link net.minecraft.core.RegistryAccess} is available (structures live in a
     * dynamic registry, not in the built-in registries). Safe to call again (idempotent).
     */
    public static synchronized void scanAll() {
        if (scanned) return;
        CATALOG.put(PageType.ORE, new java.util.ArrayList<>(OreScanner.findOreBlocksByName(AddonConfig.INCLUDE_VANILLA_ORES.get())));
        CATALOG.put(PageType.FLUID, FluidScanner.findFluidIds());
        CATALOG.putIfAbsent(PageType.STRUCTURE, List.of());
        scanned = true;
        MystPages.LOGGER.info("MystPages catalog: {} ore page(s), {} fluid page(s)",
                CATALOG.get(PageType.ORE).size(), CATALOG.get(PageType.FLUID).size());
    }

    /**
     * Scans the dynamic structure registry; called from TagsUpdatedEvent, which carries
     * a RegistryAccess. Idempotent per registry access instance.
     */
    public static synchronized void scanStructures(net.minecraft.core.RegistryAccess registryAccess) {
        scanAll();
        CATALOG.put(PageType.STRUCTURE, StructureScanner.findStructureIds(registryAccess));
        MystPages.LOGGER.info("MystPages catalog: {} structure page(s)",
                CATALOG.get(PageType.STRUCTURE).size());
    }

    /** Adds tag-detected ores after TagsUpdatedEvent; dedupes against name-detected ones. */
    public static synchronized void refreshTaggedOres() {
        if (!scanned) {
            scanAll();
            return;
        }
        Set<ResourceLocation> merged = new LinkedHashSet<>(CATALOG.get(PageType.ORE));
        int added = 0;
        for (ResourceLocation id : OreScanner.findTaggedOreBlocks(AddonConfig.INCLUDE_VANILLA_ORES.get())) {
            if (merged.add(id)) added++;
        }
        if (added > 0) {
            CATALOG.put(PageType.ORE, List.copyOf(merged));
            MystPages.LOGGER.info("MystPages catalog: +{} ore page(s) from the c:ores tag ({} total)",
                    added, merged.size());
        }
    }

    /** All detected page target ids for the given type. */
    public static List<ResourceLocation> of(PageType type) {
        scanAll();
        return CATALOG.getOrDefault(type, List.of());
    }

    /** True if the id is a known page target of the given type. */
    public static boolean isKnown(PageType type, ResourceLocation id) {
        return of(type).contains(id);
    }
}
