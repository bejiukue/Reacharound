package com.reacharound;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

/**
 * Edge-snapping ("reach-around") block placement.
 * <p>
 * When a block is placed against a face of block B and the click lands very close to an edge of that face, the
 * placement is redirected as if the player had clicked the neighbouring side of B that the edge leads to. Example:
 * aiming at the far edge of the top face of B places the new block next to B (in the air beyond the edge) instead of
 * on top of it.
 * <p>
 * The decision only uses the click data (position, face, hit point), the player's look direction and the world, so
 * the client (prediction) and the server reach the same result.
 */
public final class EdgePlacement {

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final double EPS = 1.0E-4D;
    /** Edges whose neighbour lies towards the player have a negative dot product with the look direction. */
    private static final double NEAR_EDGE_DOT = -0.05D;

    private EdgePlacement() {}

    /**
     * @return true if the placement was redirected and succeeded. false means "do nothing special": the caller
     *         continues with the original, unmodified placement.
     */
    public static boolean tryRedirect(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (ACTIVE.get() || !Config.edgeSnap) return false;
        if (stack == null || player == null || world == null || player instanceof FakePlayer) return false;
        if (!(stack.getItem() instanceof ItemBlock)) return false;
        if (side < 0 || side > 5) return false;
        if (!Config.affectSideFaces && side > 1) return false;
        if (Config.requireSneaking && !player.isSneaking()) return false;

        Block block = world.getBlock(x, y, z);
        if (block.isAir(world, x, y, z) || block.isReplaceable(world, x, y, z)) return false;

        block.setBlockBoundsBasedOnState(world, x, y, z);
        double[] lo = { block.getBlockBoundsMinX(), block.getBlockBoundsMinY(), block.getBlockBoundsMinZ() };
        double[] hi = { block.getBlockBoundsMaxX(), block.getBlockBoundsMaxY(), block.getBlockBoundsMaxZ() };
        double[] hit = { hitX, hitY, hitZ };

        int normalAxis = axisOfSide(side);

        // The hit point must really lie on the clicked face; callers that pass made-up hit values are ignored.
        double faceCoord = (side & 1) == 1 ? hi[normalAxis] : lo[normalAxis];
        if (Math.abs(hit[normalAxis] - faceCoord) > 0.01D) return false;

        // Find the closest edge of the face that borders a neighbouring cell (lies on the 0 or 1 boundary).
        double bestDist = Double.MAX_VALUE;
        int edgeAxis = -1;
        int edgeSign = 0;
        for (int t = 0; t < 3; t++) {
            if (t == normalAxis) continue;
            if (lo[t] <= EPS) {
                double d = hit[t] - lo[t];
                if (d >= -EPS && d < bestDist) {
                    bestDist = d;
                    edgeAxis = t;
                    edgeSign = -1;
                }
            }
            if (hi[t] >= 1.0D - EPS) {
                double d = hi[t] - hit[t];
                if (d >= -EPS && d < bestDist) {
                    bestDist = d;
                    edgeAxis = t;
                    edgeSign = 1;
                }
            }
        }
        if (edgeAxis < 0 || bestDist > Config.edgeMargin) return false;

        int dx = edgeAxis == 0 ? edgeSign : 0;
        int dy = edgeAxis == 1 ? edgeSign : 0;
        int dz = edgeAxis == 2 ? edgeSign : 0;

        if (!Config.allowNearEdges) {
            Vec3 look = player.getLookVec();
            double dot = look.xCoord * dx + look.yCoord * dy + look.zCoord * dz;
            if (dot < NEAR_EDGE_DOT) return false;
        }

        int tx = x + dx;
        int ty = y + dy;
        int tz = z + dz;
        if (ty < 0 || ty >= world.getHeight()) return false;
        if (!world.getBlock(tx, ty, tz)
            .isReplaceable(world, tx, ty, tz)) return false;

        int newSide = sideFor(edgeAxis, edgeSign);
        float[] newHit = { hitX, hitY, hitZ };
        newHit[edgeAxis] = edgeSign > 0 ? 1.0F : 0.0F;
        if (normalAxis == 1 && newSide > 1) {
            // The new click is on a vertical face: choose which half of the block we "click", so that slabs and
            // similar blocks continue the line of the block we aimed at.
            newHit[1] = chooseVerticalHit(side, lo[1], hi[1]);
        }

        ACTIVE.set(Boolean.TRUE);
        try {
            return stack.tryPlaceItemIntoWorld(player, world, x, y, z, newSide, newHit[0], newHit[1], newHit[2]);
        } finally {
            ACTIVE.set(Boolean.FALSE);
        }
    }

    /** Hit height used when the click is moved onto a vertical face (above 0.5 selects the upper half). */
    private static float chooseVerticalHit(int originalSide, double minY, double maxY) {
        if (maxY - minY < 1.0D - EPS) {
            // Slab-like block: continue in the same half it occupies.
            return minY >= 0.5D - EPS ? 0.75F : 0.25F;
        }
        // Full-height block: behave like vanilla does for top (lower half) and bottom (upper half) clicks.
        return originalSide == 1 ? 0.25F : 0.75F;
    }

    /** 0 = X, 1 = Y, 2 = Z */
    private static int axisOfSide(int side) {
        switch (side >> 1) {
            case 0:
                return 1;
            case 1:
                return 2;
            default:
                return 0;
        }
    }

    private static int sideFor(int axis, int sign) {
        int base = axis == 1 ? 0 : (axis == 2 ? 2 : 4);
        return base + (sign > 0 ? 1 : 0);
    }
}
