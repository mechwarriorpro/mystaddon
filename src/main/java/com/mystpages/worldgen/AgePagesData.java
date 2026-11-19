package com.mystpages.worldgen;

import com.mystpages.page.PageType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-server record of which MystPages pages each Mystcraft Reborn Age dimension was
 * created with, plus which chunks already had ore veins placed (so world reloads do not
 * double-place). Stored in the overworld's data storage under {@code mystpages_age_pages}.
 */
public final class AgePagesData extends SavedData {
    public static final String FILE_NAME = "mystpages_age_pages";

    /** dimension id -> page lists per type */
    private final Map<ResourceLocation, EnumMap<PageType, List<ResourceLocation>>> pages = new HashMap<>();
    /** dimension id -> packed chunk positions that already had ore veins placed */
    private final Map<ResourceLocation, Set<Long>> placedChunks = new HashMap<>();

    public static AgePagesData get(ServerLevel level) {
        return level.dataStorage.computeIfAbsent(
                new SavedData.Factory<>(AgePagesData::new, AgePagesData::load, null), FILE_NAME);
    }

    public boolean hasDimension(ResourceLocation dimensionId) {
        return pages.containsKey(dimensionId);
    }

    public void putPages(ResourceLocation dimensionId, PageType type, List<ResourceLocation> ids) {
        pages.computeIfAbsent(dimensionId, k -> new EnumMap<>(PageType.class)).put(type, List.copyOf(ids));
        setDirty();
    }

    public List<ResourceLocation> getPages(ResourceLocation dimensionId, PageType type) {
        EnumMap<PageType, List<ResourceLocation>> map = pages.get(dimensionId);
        if (map == null) return List.of();
        return map.getOrDefault(type, List.of());
    }

    public boolean markChunkPlaced(ResourceLocation dimensionId, long chunkPos) {
        boolean added = placedChunks.computeIfAbsent(dimensionId, k -> new HashSet<>()).add(chunkPos);
        if (added) setDirty();
        return added;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag dims = new ListTag();
        for (Map.Entry<ResourceLocation, EnumMap<PageType, List<ResourceLocation>>> entry : pages.entrySet()) {
            CompoundTag dimTag = new CompoundTag();
            dimTag.putString("dimension", entry.getKey().toString());
            for (Map.Entry<PageType, List<ResourceLocation>> e : entry.getValue().entrySet()) {
                ListTag list = new ListTag();
                for (ResourceLocation id : e.getValue()) list.add(StringTag.valueOf(id.toString()));
                dimTag.put(e.getKey().commandName() + "s", list);
            }
            Set<Long> placed = placedChunks.get(entry.getKey());
            if (placed != null) {
                ListTag placedList = new ListTag();
                for (long pos : placed) placedList.add(StringTag.valueOf(Long.toString(pos)));
                dimTag.put("placed_chunks", placedList);
            }
            dims.add(dimTag);
        }
        tag.put("ages", dims);
        return tag;
    }

    public static AgePagesData load(CompoundTag tag, HolderLookup.Provider provider) {
        AgePagesData data = new AgePagesData();
        ListTag dims = tag.getList("ages", Tag.TAG_COMPOUND);
        for (int i = 0; i < dims.size(); i++) {
            CompoundTag dimTag = dims.getCompound(i);
            ResourceLocation dimId = ResourceLocation.tryParse(dimTag.getString("dimension"));
            if (dimId == null) continue;
            for (PageType type : PageType.values()) {
                ListTag list = dimTag.getList(type.commandName() + "s", Tag.TAG_STRING);
                List<ResourceLocation> ids = new ArrayList<>();
                for (int j = 0; j < list.size(); j++) {
                    ResourceLocation id = ResourceLocation.tryParse(list.getString(j));
                    if (id != null) ids.add(id);
                }
                if (!ids.isEmpty()) data.pages.computeIfAbsent(dimId, k -> new EnumMap<>(PageType.class)).put(type, ids);
            }
            ListTag placedList = dimTag.getList("placed_chunks", Tag.TAG_STRING);
            Set<Long> placed = new HashSet<>();
            for (int j = 0; j < placedList.size(); j++) {
                try {
                    placed.add(Long.parseLong(placedList.getString(j)));
                } catch (NumberFormatException ignored) {}
            }
            if (!placed.isEmpty()) data.placedChunks.put(dimId, placed);
        }
        return data;
    }
}
