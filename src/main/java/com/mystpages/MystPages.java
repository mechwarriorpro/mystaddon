package com.mystpages;

import com.mojang.logging.LogUtils;
import com.mystpages.compat.sable.SableBridge;
import com.mystpages.compat.sable.SablePortalCompat;
import com.mystpages.page.PageCatalog;
import com.mystpages.page.PageComponents;
import com.mystpages.worldgen.AgeGenerationBridge;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * MystPages — addon for Mystcraft Reborn (mod id {@code mystcraft_reborn}, MC 1.21.1).
 *
 * <p>Detects modded ores, fluids and structures and turns them into Mystcraft-style
 * "pages" that can be attached to a Descriptive Book. Pages are stored on the book in
 * the addon's own data components (mirroring how Mystcraft Reborn stores its biome
 * symbols in {@code biome_symbols}) and are applied when the book's Age dimension
 * generates chunks.</p>
 */
@Mod(MystPages.MOD_ID)
public final class MystPages {
    public static final String MOD_ID = "mystpages";
    /** Mod id of the host mod (StudioRewired/mystcraft-reborn). */
    public static final String HOST_MOD_ID = "mystcraft_reborn";
    /** Registry id of the host's Descriptive Book item. */
    public static final String DESCRIPTIVE_BOOK_ID = HOST_MOD_ID + ":descriptive_book";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MOD_ID);

    public MystPages(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, AddonConfig.SPEC);
        COMPONENTS.register(modEventBus);
        PageComponents.init();

        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::onRegisterCommands);

        NeoForge.EVENT_BUS.addListener(AgeGenerationBridge::onTagsUpdated);
        NeoForge.EVENT_BUS.addListener(AgeGenerationBridge::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(AgeGenerationBridge::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(AgeGenerationBridge::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(MystPagesCommands::onItemTooltip);

        // Sable sub-level portal compatibility (no-op unless the sable mod is loaded).
        if (ModList.get().isLoaded("sable") && SableBridge.isSableLoaded()) {
            NeoForge.EVENT_BUS.addListener(SablePortalCompat::onEntityTick);
            LOGGER.info("MystPages: Sable detected - sub-level portal compatibility enabled.");
        }
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(PageCatalog::scanAll);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        MystPagesCommands.register(event.getDispatcher(), event.getBuildContext());
    }
}
