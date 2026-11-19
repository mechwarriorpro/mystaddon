package com.mystpages.page;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * The page types MystPages can create pages for.
 *
 * <p>Registry-detected types ({@link #ORE}, {@link #FLUID}, {@link #STRUCTURE}) get their
 * page ids from scanning registries (see {@link PageCatalog}). Value-based types
 * ({@link #GRAVITY}, {@link #LOOT}, {@link #TICKSPEED}, {@link #WEATHER}, {@link #PEACEFUL})
 * have a fixed set of {@link #defaultPages()} with ids of the form
 * {@code mystpages:<type>_<value>}.</p>
 */
public enum PageType {
    ORE("ore", null),
    FLUID("fluid", null),
    STRUCTURE("structure", null),
    GRAVITY("gravity", List.of("down", "up", "north", "south", "east", "west", "random")),
    LOOT("loot", List.of("0", "10", "25", "50", "75", "100", "150", "200", "500")),
    TICKSPEED("tickspeed", List.of("50", "75", "100", "150", "200", "250", "300")),
    WEATHER("weather", List.of("clear", "rain", "thunder")),
    PEACEFUL("peaceful", List.of("on"));

    private final String commandName;
    private final List<String> values;

    PageType(String commandName, List<String> values) {
        this.commandName = commandName;
        this.values = values;
    }

    public String commandName() {
        return commandName;
    }

    /** True for fixed value-based page types (gravity, loot, tickspeed, weather, peaceful). */
    public boolean isValueBased() {
        return values != null;
    }

    /** The fixed page ids for value-based types, or null for registry-scanned types. */
    public List<ResourceLocation> defaultPages() {
        if (values == null) return null;
        return values.stream()
                .map(v -> ResourceLocation.fromNamespaceAndPath("mystpages", commandName + "_" + v))
                .toList();
    }

    /** Extracts the value part of a value-based page id, e.g. "gravity_up" -> "up". */
    public String valueOf(ResourceLocation pageId) {
        String prefix = commandName + "_";
        String path = pageId.getPath();
        return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
    }

    public static PageType fromCommandName(String name) {
        for (PageType type : values()) {
            if (type.commandName.equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }
}
