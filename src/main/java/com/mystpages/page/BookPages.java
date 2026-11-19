package com.mystpages.page;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Read/write helpers for MystPages page lists stored on a Descriptive Book. */
public final class BookPages {
    private BookPages() {}

    /** True if the stack is Mystcraft Reborn's Descriptive Book (checked by registry id). */
    public static boolean isDescriptiveBook(ItemStack stack) {
        return !stack.isEmpty()
                && ResourceLocation.fromNamespaceAndPath(com.mystpages.MystPages.HOST_MOD_ID, "descriptive_book")
                        .equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static List<ResourceLocation> get(ItemStack book, PageType type) {
        if (!isDescriptiveBook(book)) return List.of();
        List<ResourceLocation> list = book.get(PageComponents.forType(type).get());
        return list == null ? List.of() : list;
    }

    /** Adds the page id if not already present. Returns true if the book changed. */
    public static boolean add(ItemStack book, PageType type, ResourceLocation pageId) {
        if (!isDescriptiveBook(book)) return false;
        var component = PageComponents.forType(type);
        List<ResourceLocation> current = new ArrayList<>(get(book, type));
        if (current.contains(pageId)) return false;
        current.add(pageId);
        set(book, component, current);
        return true;
    }

    /** Removes the page id. Returns true if the book changed. */
    public static boolean remove(ItemStack book, PageType type, ResourceLocation pageId) {
        if (!isDescriptiveBook(book)) return false;
        var component = PageComponents.forType(type);
        List<ResourceLocation> current = new ArrayList<>(get(book, type));
        if (!current.remove(pageId)) return false;
        set(book, component, current);
        return true;
    }

    private static void set(ItemStack book,
                            DeferredHolder<DataComponentType<?>, DataComponentType<List<ResourceLocation>>> component,
                            List<ResourceLocation> value) {
        PatchedDataComponentMap patched = new PatchedDataComponentMap(book.getComponents());
        patched.set(component.get(), value);
        book.applyComponentsAndValidate(patched.asPatch());
    }
}
