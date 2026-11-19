package com.mystpages.compat.sable;

import com.mystpages.MystPages;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import org.joml.Vector3d;

/**
 * Reflection facade over Sable's runtime API (dev.ryanhcode.sable). Everything is looked
 * up lazily and defensively so this class loads fine when Sable is absent; all methods
 * no-op / return empty when Sable is not installed.
 *
 * <p>Used API surface (from ryanhcode/sable):
 * <ul>
 *   <li>{@code Sable.HELPER} (ActiveSableCompanion) — {@code getAllIntersecting(Level, BoundingBox3dc)},
 *       {@code getTrackingSubLevel(Entity)}, {@code getTrackingOrVehicleSubLevel(Entity)}</li>
 *   <li>{@code SubLevel.logicalPose()} → Pose3d — {@code transformPositionInverse(Vector3dc)}</li>
 *   <li>{@code SubLevelAccess} — {@code getBlockState(BlockPos)}, {@code getBlockEntity(BlockPos)} in local coords</li>
 * </ul>
 */
public final class SableBridge {
    private static Boolean sableLoaded;
    private static Object helper;
    private static Method mGetAllIntersecting;
    private static Method mGetTrackingSubLevel;
    private static Method mGetTrackingOrVehicleSubLevel;
    private static Method mLogicalPose;
    private static Method mTransformPositionInverse;
    private static Class<?> boundingBox3dClass;

    private SableBridge() {}

    public static boolean isSableLoaded() {
        if (sableLoaded == null) {
            sableLoaded = ModList.get().isLoaded("sable");
        }
        return sableLoaded;
    }

    /** @return Sable.HELPER (ActiveSableCompanion), or null when unavailable. */
    public static Object helper() {
        if (!isSableLoaded()) return null;
        if (helper == null) {
            try {
                Class<?> sableClass = Class.forName("dev.ryanhcode.sable.Sable");
                helper = sableClass.getField("HELPER").get(null);
            } catch (Throwable t) {
                MystPages.LOGGER.warn("MystPages: could not access Sable.HELPER", t);
                return null;
            }
        }
        return helper;
    }

    /** All sub-levels intersecting the given world-space box. */
    public static List<Object> subLevelsIntersecting(Level level, AABB box) {
        Object h = helper();
        if (h == null) return List.of();
        try {
            if (boundingBox3dClass == null) {
                boundingBox3dClass = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3d");
            }
            Object bb = boundingBox3dClass
                    .getConstructor(double.class, double.class, double.class, double.class, double.class, double.class)
                    .newInstance(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            if (mGetAllIntersecting == null) {
                for (Method m : h.getClass().getMethods()) {
                    if (m.getName().equals("getAllIntersecting") && m.getParameterCount() == 2
                            && m.getParameterTypes()[1].isInstance(bb)) {
                        mGetAllIntersecting = m;
                        break;
                    }
                }
            }
            if (mGetAllIntersecting == null) return List.of();
            Object result = mGetAllIntersecting.invoke(h, level, bb);
            List<Object> out = new ArrayList<>();
            if (result instanceof Iterable<?> it) {
                for (Object o : it) out.add(o);
            }
            return out;
        } catch (Throwable t) {
            MystPages.LOGGER.debug("MystPages: subLevelsIntersecting failed", t);
            return List.of();
        }
    }

    /** The sub-level the entity is currently tracked on (standing on / riding), if any. */
    public static Object trackingSubLevel(Entity entity) {
        Object h = helper();
        if (h == null) return null;
        try {
            if (mGetTrackingOrVehicleSubLevel == null) {
                for (Method m : h.getClass().getMethods()) {
                    if (m.getName().equals("getTrackingOrVehicleSubLevel") && m.getParameterCount() == 1) {
                        mGetTrackingOrVehicleSubLevel = m;
                        break;
                    }
                }
            }
            if (mGetTrackingOrVehicleSubLevel == null) return null;
            return mGetTrackingOrVehicleSubLevel.invoke(h, entity);
        } catch (Throwable t) {
            return null;
        }
    }

    /** The sub-level's logical pose (world ↔ local transform). */
    public static Object logicalPose(Object subLevel) {
        try {
            if (mLogicalPose == null) {
                mLogicalPose = subLevel.getClass().getMethod("logicalPose");
            }
            return mLogicalPose.invoke(subLevel);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Transforms a world-space position into the sub-level's local (grid) coordinates. */
    public static Vector3d worldToLocal(Object subLevel, double x, double y, double z) {
        Object pose = logicalPose(subLevel);
        if (pose == null) return new Vector3d(x, y, z);
        try {
            if (mTransformPositionInverse == null) {
                for (Method m : pose.getClass().getMethods()) {
                    if (m.getName().equals("transformPositionInverse") && m.getParameterCount() == 1) {
                        mTransformPositionInverse = m;
                        break;
                    }
                }
            }
            if (mTransformPositionInverse == null) return new Vector3d(x, y, z);
            Object local = mTransformPositionInverse.invoke(pose, new Vector3d(x, y, z));
            if (local instanceof Vector3d v) return v;
            return new Vector3d(x, y, z);
        } catch (Throwable t) {
            return new Vector3d(x, y, z);
        }
    }

    /** Block state at a local position inside the sub-level's grid. */
    public static BlockState blockStateAt(Object subLevel, BlockPos localPos) {
        try {
            Method m = findMethod(subLevel.getClass(), "getBlockState", BlockPos.class);
            if (m == null) return null;
            return (BlockState) m.invoke(subLevel, localPos);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Block entity at a local position inside the sub-level's grid. */
    public static BlockEntity blockEntityAt(Object subLevel, BlockPos localPos) {
        try {
            Method m = findMethod(subLevel.getClass(), "getBlockEntity", BlockPos.class);
            if (m == null) return null;
            return (BlockEntity) m.invoke(subLevel, localPos);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method findMethod(Class<?> cls, String name, Class<?>... params) {
        Class<?> c = cls;
        while (c != null) {
            try {
                return c.getMethod(name, params);
            } catch (NoSuchMethodException ignored) {
                for (Class<?> iface : c.getInterfaces()) {
                    try {
                        return iface.getMethod(name, params);
                    } catch (NoSuchMethodException ignored2) {}
                }
                c = c.getSuperclass();
            }
        }
        return null;
    }
}
