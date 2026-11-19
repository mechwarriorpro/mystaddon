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

    /** Full scan; called during common setup. Safe to call again (idempotent). */
    public static synchronized void scanAll() {
        if (scanned) return;
        CATALOG.put(PageType.ORE, new java.util.ArrayList<>(OreScanner.findOreBlocksByName(AddonConfig.INCLUDE_VANILLA_ORES.get())));
        CATALOG.put(PageType.FLUID, FluidScanner.findFluidIds());
        CATALOG.put(PageType.STRUCTURE, StructureScanner.findStructureIds());
        scanned = true;
        MystPages.LOGGER.info("MystPages catalog: {} ore page(s), {} fluid page(s), {} structure page(s)",
                CATALOG.get(PageType.ORE).size(), CATALOG.get(PageType.FLUID).size(), CATALOG.get(PageType.STRUCTURE).size());
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
