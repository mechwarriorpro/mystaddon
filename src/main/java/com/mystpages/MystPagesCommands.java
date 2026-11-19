package com.mystpages;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mystpages.page.BookPages;
import com.mystpages.page.PageCatalog;
import com.mystpages.page.PageType;
import java.util.List;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * {@code /mystpages} — manage pages on the held Descriptive Book and inspect the catalog:
 *
 * <pre>
 * /mystpages add &lt;ore|fluid|structure&gt; &lt;id&gt;     add a page to the held book
 * /mystpages remove &lt;ore|fluid|structure&gt; &lt;id&gt;  remove a page from the held book
 * /mystpages list &lt;ore|fluid|structure&gt;           pages currently on the held book
 * /mystpages catalog &lt;ore|fluid|structure&gt;        all detected pages of that type
 * </pre>
 */
public final class MystPagesCommands {
    private MystPagesCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("mystpages")
                .then(Commands.literal("add")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(ctx -> add(ctx, StringArgumentType.getString(ctx, "type"),
                                                StringArgumentType.getString(ctx, "id"))))))
                .then(Commands.literal("remove")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(ctx -> remove(ctx, StringArgumentType.getString(ctx, "type"),
                                                StringArgumentType.getString(ctx, "id"))))))
                .then(Commands.literal("list")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .executes(ctx -> list(ctx, StringArgumentType.getString(ctx, "type")))))
                .then(Commands.literal("catalog")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .executes(ctx -> catalog(ctx, StringArgumentType.getString(ctx, "type"))))));
    }

    private static PageType parseType(CommandContext<CommandSourceStack> ctx, String raw) {
        PageType type = PageType.fromCommandName(raw);
        if (type == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown page type '" + raw + "' (expected ore, fluid or structure)."));
        }
        return type;
    }

    private static ItemStack heldBook(CommandSourceStack src) {
        if (!src.isPlayer()) return ItemStack.EMPTY;
        ItemStack stack = src.getPlayer().getItemInHand(InteractionHand.MAIN_HAND);
        return BookPages.isDescriptiveBook(stack) ? stack : ItemStack.EMPTY;
    }

    private static int add(CommandContext<CommandSourceStack> ctx, String typeRaw, String idRaw) {
        PageType type = parseType(ctx, typeRaw);
        if (type == null) return 0;
        ResourceLocation id = ResourceLocation.tryParse(idRaw);
        if (id == null) {
            ctx.getSource().sendFailure(Component.literal("Invalid id: " + idRaw));
            return 0;
        }
        ItemStack book = heldBook(ctx.getSource());
        if (book.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Hold a Mystcraft Reborn Descriptive Book in your main hand."));
            return 0;
        }
        if (!PageCatalog.isKnown(type, id)) {
            ctx.getSource().sendFailure(Component.literal(id + " is not a detected " + type.commandName() + " page. See /mystpages catalog " + type.commandName()));
            return 0;
        }
        if (BookPages.add(book, type, id)) {
            ctx.getSource().sendSuccess(() -> Component.literal("Added " + type.commandName() + " page " + id + " to the book."), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("That page is already on the book."));
        return 0;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx, String typeRaw, String idRaw) {
        PageType type = parseType(ctx, typeRaw);
        if (type == null) return 0;
        ResourceLocation id = ResourceLocation.tryParse(idRaw);
        if (id == null) {
            ctx.getSource().sendFailure(Component.literal("Invalid id: " + idRaw));
            return 0;
        }
        ItemStack book = heldBook(ctx.getSource());
        if (book.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Hold a Mystcraft Reborn Descriptive Book in your main hand."));
            return 0;
        }
        if (BookPages.remove(book, type, id)) {
            ctx.getSource().sendSuccess(() -> Component.literal("Removed " + type.commandName() + " page " + id + " from the book."), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("That page is not on the book."));
        return 0;
    }

    private static int list(CommandContext<CommandSourceStack> ctx, String typeRaw) {
        PageType type = parseType(ctx, typeRaw);
        if (type == null) return 0;
        ItemStack book = heldBook(ctx.getSource());
        if (book.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Hold a Mystcraft Reborn Descriptive Book in your main hand."));
            return 0;
        }
        List<ResourceLocation> pages = BookPages.get(book, type);
        if (pages.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No " + type.commandName() + " pages on this book."), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal(type.commandName() + " pages (" + pages.size() + "): " + pages), false);
        }
        return pages.size();
    }

    private static int catalog(CommandContext<CommandSourceStack> ctx, String typeRaw) {
        PageType type = parseType(ctx, typeRaw);
        if (type == null) return 0;
        List<ResourceLocation> pages = PageCatalog.of(type);
        ctx.getSource().sendSuccess(() -> Component.literal("Detected " + type.commandName() + " pages (" + pages.size() + "): " + pages), false);
        return pages.size();
    }

    /** Shows the book's pages in the item tooltip. */
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!BookPages.isDescriptiveBook(stack)) return;
        for (PageType type : PageType.values()) {
            List<ResourceLocation> pages = BookPages.get(stack, type);
            if (pages.isEmpty()) continue;
            event.getToolTip().add(Component.literal(type.commandName() + " pages: " + pages.size())
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            for (ResourceLocation id : pages) {
                event.getToolTip().add(Component.literal("  • " + id)
                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
        }
    }
}
