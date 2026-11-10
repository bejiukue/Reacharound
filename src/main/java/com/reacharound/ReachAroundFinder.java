package com.reacharound;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * "Reach-around" placement in the style of Quark: when the crosshair points at nothing, a block can still be placed
 * if a slightly shifted ray would hit a block. Two modes:
 * <ul>
 * <li>horizontal: the ray is shifted backwards by {@code leniency}; if it hits block B, the block goes into the cell
 * in front of B (bridging over an edge);</li>
 * <li>vertical (only when not looking up): the ray is shifted up by {@code leniency}; if it hits block B, the block
 * goes into the cell under B, provided that cell is more than one block below the player's feet.</li>
 * </ul>
 * Pure logic with no client-only classes; the caller supplies the ray (eye position and look vector).
 */
public final class ReachAroundFinder {

    private static final double EPS = 1.0E-4D;
    // Index 0..3 from the player's yaw: south (+Z), west (-X), north (-Z), east (+X)
    private static final int[] DIR_X = { 0, -1, 0, 1 };
    private static final int[] DIR_Z = { 1, 0, -1, 0 };

    private ReachAroundFinder() {}

    /** Where to place the block and how to "click" it. */
    public static final class Target {

        /** Cell that will receive the block (it is empty or replaceable). */
        public final int x, y, z;
        /** Face index (0..5): direction from the supporting block towards the target cell. */
        public final int side;
        /** Click position relative to the target cell. */
        public final float hitX, hitY, hitZ;
        /** true for the "place under a block" mode (used to pick the indicator icon). */
        public final boolean vertical;

        Target(int x, int y, int z, int side, float hitX, float hitY, float hitZ, boolean vertical) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.side = side;
            this.hitX = hitX;
            this.hitY = hitY;
            this.hitZ = hitZ;
            this.vertical = vertical;
        }
    }

    /**
     * @param normalHit what the normal crosshair ray hit (may be null)
     * @param eye       eye position of the player
     * @param look      unit look vector
     * @param range     block reach distance
     * @param leniency  how far (in blocks) the ray may be shifted
     * @return the target, or null if reach-around does not apply
     */
    public static Target find(EntityPlayer player, World world, ItemStack stack, MovingObjectPosition normalHit,
        Vec3 eye, Vec3 look, double range, double leniency) {
        if (stack == null || stack.stackSize <= 0 || !(stack.getItem() instanceof ItemBlock)) return null;

        // Only when the crosshair points at nothing (or at an air cell, which 1.7.10 reports as a block hit).
        if (normalHit != null) {
            if (normalHit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) return null;
            if (normalHit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && !isAir(world, normalHit.blockX, normalHit.blockY, normalHit.blockZ)) {
                return null;
            }
        }

        double feetY = player.boundingBox.minY;
        double rx = look.xCoord * range;
        double ry = look.yCoord * range;
        double rz = look.zCoord * range;

        // Vertical mode: not available while looking up.
        if (player.rotationPitch >= 0.0F) {
            Vec3 start = eye.addVector(0.0D, leniency, 0.0D);
            MovingObjectPosition hit = trace(world, start, start.addVector(rx, ry, rz));
            if (hit != null) {
                int ty = hit.blockY - 1;
                if (feetY - ty > 1.0D && isFree(world, hit.blockX, ty, hit.blockZ)) {
                    return new Target(hit.blockX, ty, hit.blockZ, 0, 0.5F, 1.0F, 0.5F, true);
                }
            }
        }

        // Horizontal mode: shift the ray backwards along the horizontal facing direction.
        int facing = MathHelper.floor_double(player.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        int dx = DIR_X[facing];
        int dz = DIR_Z[facing];
        Vec3 start = eye.addVector(-leniency * dx, 0.0D, -leniency * dz);
        MovingObjectPosition hit = trace(world, start, start.addVector(rx, ry, rz));
        if (hit != null) {
            int tx = hit.blockX + dx;
            int tz = hit.blockZ + dz;
            // Rejects hits that lie behind the player.
            Vec3 behind = Vec3.createVectorHelper(player.posX - look.xCoord, feetY + 2.0D, player.posZ - look.zCoord);
            if (isFree(world, tx, hit.blockY, tz) && behind.distanceTo(hit.hitVec) > 0.9D) {
                float hitX = dx > 0 ? 0.0F : (dx < 0 ? 1.0F : 0.5F);
                float hitZ = dz > 0 ? 0.0F : (dz < 0 ? 1.0F : 0.5F);
                float hitY = horizontalHitY(world, hit.blockX, hit.blockY, hit.blockZ);
                return new Target(tx, hit.blockY, tz, sideOf(dx, dz), hitX, hitY, hitZ, false);
            }
        }

        return null;
    }

    private static MovingObjectPosition trace(World world, Vec3 start, Vec3 end) {
        MovingObjectPosition hit = world.func_147447_a(start, end, false, false, false);
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) return hit;
        return null;
    }

    private static boolean isAir(World world, int x, int y, int z) {
        return world.getBlock(x, y, z)
            .isAir(world, x, y, z);
    }

    private static boolean isFree(World world, int x, int y, int z) {
        if (y < 0 || y >= world.getHeight()) return false;
        Block block = world.getBlock(x, y, z);
        return block.isAir(world, x, y, z) || block.isReplaceable(world, x, y, z);
    }

    /** Half of the cell that a slab placed this way should occupy (above 0.5 selects the upper half). */
    private static float horizontalHitY(World world, int bx, int by, int bz) {
        Block block = world.getBlock(bx, by, bz);
        block.setBlockBoundsBasedOnState(world, bx, by, bz);
        double minY = block.getBlockBoundsMinY();
        double maxY = block.getBlockBoundsMaxY();
        if (maxY - minY < 1.0D - EPS) {
            // Slab-like block: continue in the same half.
            return minY >= 0.5D - EPS ? 0.75F : 0.25F;
        }
        // Full block: the new slab is flush with its top surface.
        return 0.75F;
    }

    private static int sideOf(int dx, int dz) {
        if (dz > 0) return 3;
        if (dz < 0) return 2;
        return dx > 0 ? 5 : 4;
    }
}
