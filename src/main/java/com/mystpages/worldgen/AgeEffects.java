package com.mystpages.worldgen;

import com.mystpages.MystPages;
import com.mystpages.page.PageType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Runtime effects of value-based MystPages pages inside Mystcraft Reborn Age dimensions:
 *
 * <ul>
 *   <li>{@code gravity} — per-entity directional gravity, re-applied every tick. The
 *       {@code random} value re-rolls a direction every 30 s – 5 min per Age.</li>
 *   <li>{@code weather} — forces clear/rain/thunder when the Age level loads.</li>
 *   <li>{@code tickspeed} — scales the Age's random tick speed game rule (base 3).</li>
 *   <li>{@code peaceful} — cancels hostile mob spawns in the Age.</li>
 * </ul>
 *
 * <p>Loot abundance is handled separately by a global loot modifier
 * ({@link com.mystpages.loot.LootAbundanceModifier}).</p>
 */
public final class AgeEffects {
    /** Vanilla gravity acceleration per tick (blocks/tick^2). */
    private static final double GRAVITY = 0.08;
    /** Random-gravity re-roll interval: 30 s .. 5 min, in ticks. */
    private static final int RANDOM_MIN_TICKS = 600;
    private static final int RANDOM_SPAN_TICKS = 5400;

    private static final Vec3 DOWN = new Vec3(0, -1, 0);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 NORTH = new Vec3(0, 0, -1);
    private static final Vec3 SOUTH = new Vec3(0, 0, 1);
    private static final Vec3 WEST = new Vec3(-1, 0, 0);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final Vec3[] DIRECTIONS = {DOWN, UP, NORTH, SOUTH, WEST, EAST};

    /** Per-Age state for the "random" gravity page: current direction + next re-roll tick. */
    private static final Map<ResourceLocation, RandomGravity> RANDOM_GRAVITY = new ConcurrentHashMap<>();

    private AgeEffects() {}

    private static final class RandomGravity {
        Vec3 direction = DOWN;
        long nextChangeTick;
    }

    private static boolean isAge(ServerLevel level) {
        return level.dimension().location().getNamespace().equals(MystPages.HOST_MOD_ID);
    }

    /** Gravity pages: apply custom directional gravity to entities in the Age. */
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || !isAge(level)) return;
        List<ResourceLocation> pages = AgePagesData.get(level).getPages(level.dimension().location(), PageType.GRAVITY);
        if (pages.isEmpty()) return;
        if (entity.isSpectator() || entity.isNoGravity()) return;

        Vec3 direction = resolveDirection(level, PageType.GRAVITY.valueOf(pages.get(0)));
        if (direction == null || direction == DOWN) return; // "down" is vanilla gravity

        // Add (custom - default) gravity so the net acceleration matches the page direction.
        Vec3 correction = direction.scale(GRAVITY).add(DOWN.scale(-GRAVITY));
        entity.setDeltaMovement(entity.getDeltaMovement().add(correction));
        entity.hasImpulse = true;
    }

    private static Vec3 resolveDirection(ServerLevel level, String value) {
        return switch (value) {
            case "up" -> UP;
            case "north" -> NORTH;
            case "south" -> SOUTH;
            case "west" -> WEST;
            case "east" -> EAST;
            case "down" -> DOWN;
            case "random" -> resolveRandom(level);
            default -> null;
        };
    }

    private static Vec3 resolveRandom(ServerLevel level) {
        ResourceLocation dim = level.dimension().location();
        RandomGravity state = RANDOM_GRAVITY.computeIfAbsent(dim, k -> new RandomGravity());
        long now = level.getGameTime();
        RandomSource random = level.getRandom();
        if (now >= state.nextChangeTick) {
            state.direction = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
            state.nextChangeTick = now + RANDOM_MIN_TICKS + random.nextInt(RANDOM_SPAN_TICKS);
        }
        return state.direction;
    }

    /** Weather + tick speed pages: applied when the Age level loads. */
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !isAge(level)) return;
        ResourceLocation dim = level.dimension().location();
        AgePagesData data = AgePagesData.get(level);

        List<ResourceLocation> weather = data.getPages(dim, PageType.WEATHER);
        if (!weather.isEmpty()) {
            switch (PageType.WEATHER.valueOf(weather.get(0))) {
                case "clear" -> level.setWeatherParameters(1_000_000, 0, false, false);
                case "rain" -> level.setWeatherParameters(0, 1_000_000, true, false);
                case "thunder" -> level.setWeatherParameters(0, 1_000_000, true, true);
                default -> {}
            }
        }

        List<ResourceLocation> tickSpeed = data.getPages(dim, PageType.TICKSPEED);
        if (!tickSpeed.isEmpty()) {
            int percent = parsePercent(PageType.TICKSPEED.valueOf(tickSpeed.get(0)), 100);
            int scaled = Math.max(0, Math.round(3 * percent / 100.0f)); // vanilla default random tick speed is 3
            level.getGameRules().set(GameRules.RULE_RANDOMTICKPEED, scaled, level.getServer());
            MystPages.LOGGER.info("MystPages: Age {} random tick speed set to {} ({}%)", dim, scaled, percent);
        }
    }

    /** Peaceful page: cancel hostile mob spawns in the Age. */
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !isAge(level)) return;
        if (!(event.getEntity() instanceof Monster)) return;
        List<ResourceLocation> pages = AgePagesData.get(level).getPages(level.dimension().location(), PageType.PEACEFUL);
        if (!pages.isEmpty()) {
            event.setCanceled(true);
        }
    }

    static int parsePercent(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
