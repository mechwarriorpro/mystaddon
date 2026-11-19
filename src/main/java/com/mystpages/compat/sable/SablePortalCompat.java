package com.mystpages.compat.sable;

import com.mystpages.MystPages;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.portal.Portal;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Makes Mystcraft Reborn link portals work with Sable sub-levels ("sub-grids"):
 *
 * <ul>
 *   <li><b>Portals on sub-levels</b> — when an entity's bounding box overlaps a
 *       {@code mystcraft_reborn:link_portal} block that lives inside a Sable sub-level, the
 *       entity is teleported through it. The destination is resolved the same way the host
 *       block does it: portal block entity → receptacle position → linking book →
 *       {@code LinkingBookItem.getDestination(...)} (all via reflection, so no compile-time
 *       dependency on either mod).</li>
 *   <li><b>Players on sub-levels using world portals</b> — Sable can suppress vanilla's
 *       {@code entityInside} portal trigger for entities standing on a sub-level, so for
 *       entities with a tracking sub-level we explicitly re-trigger the vanilla portal flow
 *       ({@code Entity.setAsInsidePortal}) when a world portal block overlaps them.</li>
 * </ul>
 *
 * <p>Only active when the {@code sable} mod is loaded.</p>
 */
public final class SablePortalCompat {
    private static final ResourceLocation LINK_PORTAL_ID =
            ResourceLocation.fromNamespaceAndPath(MystPages.HOST_MOD_ID, "link_portal");
    private static final ResourceLocation LOCATION_COMPONENT_ID =
            ResourceLocation.fromNamespaceAndPath(MystPages.HOST_MOD_ID, "location");
    private static final ResourceLocation ROTATION_COMPONENT_ID =
            ResourceLocation.fromNamespaceAndPath(MystPages.HOST_MOD_ID, "rotation");
    /** Minimum ticks between two sub-level portal teleports of the same entity. */
    private static final long TELEPORT_COOLDOWN_TICKS = 20L;
    private static final Map<UUID, Long> TELEPORT_COOLDOWN = new ConcurrentHashMap<>();

    private SablePortalCompat() {}

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!SableBridge.isSableLoaded()) return;
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (!entity.canUsePortal(true)) return;

        Object tracking = SableBridge.trackingSubLevel(entity);

        // Players/entities riding a sub-level may not get vanilla's entityInside portal
        // trigger for portal blocks in the world — re-trigger it explicitly.
        if (tracking != null) {
            triggerWorldPortal(entity, level);
        }

        // Portals that live on a sub-level: teleport overlapping entities through them.
        triggerSubLevelPortals(entity, level);
    }

    /** Vanilla portal flow for a world portal block overlapping the entity. */
    private static void triggerWorldPortal(Entity entity, ServerLevel level) {
        AABB box = entity.getBoundingBox();
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof Portal portal
                    && LINK_PORTAL_ID.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) {
                entity.setAsInsidePortal(portal, pos);
                return;
            }
        }
    }

    /** Find link-portal blocks inside overlapping sub-levels and teleport the entity. */
    private static void triggerSubLevelPortals(Entity entity, ServerLevel level) {
        AABB box = entity.getBoundingBox();
        for (Object subLevel : SableBridge.subLevelsIntersecting(level, box)) {
            BlockPos localMin = null;
            BlockPos localMax = null;
            for (double x : new double[]{box.minX, box.maxX}) {
                for (double y : new double[]{box.minY, box.maxY}) {
                    for (double z : new double[]{box.minZ, box.maxZ}) {
                        org.joml.Vector3d local = SableBridge.worldToLocal(subLevel, x, y, z);
                        BlockPos p = BlockPos.containing(local.x, local.y, local.z);
                        if (localMin == null) {
                            localMin = p;
                            localMax = p;
                        } else {
                            localMin = new BlockPos(Math.min(localMin.getX(), p.getX()), Math.min(localMin.getY(), p.getY()), Math.min(localMin.getZ(), p.getZ()));
                            localMax = new BlockPos(Math.max(localMax.getX(), p.getX()), Math.max(localMax.getY(), p.getY()), Math.max(localMax.getZ(), p.getZ()));
                        }
                    }
                }
            }
            if (localMin == null) continue;
            for (BlockPos localPos : BlockPos.betweenClosed(localMin, localMax)) {
                BlockState state = SableBridge.blockStateAt(subLevel, localPos);
                if (state != null && LINK_PORTAL_ID.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) {
                    if (teleportThroughSubLevelPortal(entity, level, subLevel, localPos)) {
                        return;
                    }
                }
            }
        }
    }

    /**
     * Resolves the portal's linking book inside the sub-level and teleports the entity to the
     * book's destination, mirroring LinkPortalBlock#getPortalDestination.
     */
    private static boolean teleportThroughSubLevelPortal(Entity entity, ServerLevel level, Object subLevel, BlockPos localPortalPos) {
        long now = level.getGameTime();
        Long last = TELEPORT_COOLDOWN.get(entity.getUUID());
        if (last != null && now - last < TELEPORT_COOLDOWN_TICKS) return false;
        try {
            BlockEntity portalBE = SableBridge.blockEntityAt(subLevel, localPortalPos);
            if (portalBE == null) return false;
            Method getReceptacle = portalBE.getClass().getMethod("getReceptaclePosition");
            BlockPos receptacleLocal = (BlockPos) getReceptacle.invoke(portalBE);
            if (receptacleLocal == null) return false;

            BlockEntity receptacleBE = SableBridge.blockEntityAt(subLevel, receptacleLocal);
            if (receptacleBE == null) return false;
            Object bookObj = receptacleBE.getClass().getMethod("getBook").invoke(receptacleBE);
            if (!(bookObj instanceof ItemStack book) || book.isEmpty()) return false;

            DataComponentType<?> locationType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(LOCATION_COMPONENT_ID);
            DataComponentType<?> rotationType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(ROTATION_COMPONENT_ID);
            if (locationType == null || rotationType == null) return false;
            Object location = book.get(locationType);
            Object rotation = book.get(rotationType);
            if (location == null || rotation == null) return false;

            Method getDestination = book.getItem().getClass().getMethod(
                    "getDestination", Level.class, Entity.class, location.getClass(), rotation.getClass());
            Object transition = getDestination.invoke(book.getItem(), level, entity, location, rotation);
            if (transition instanceof DimensionTransition dimensionTransition) {
                entity.changeDimension(dimensionTransition);
                TELEPORT_COOLDOWN.put(entity.getUUID(), now);
                MystPages.LOGGER.info("MystPages: teleported {} through a sub-level portal in {}",
                        entity.getName().getString(), level.dimension().location());
                return true;
            }
        } catch (Throwable t) {
            MystPages.LOGGER.warn("MystPages: sub-level portal teleport failed for {}", entity.getName().getString(), t);
        }
        return false;
    }
}
