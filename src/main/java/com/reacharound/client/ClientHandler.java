package com.reacharound.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import org.lwjgl.opengl.GL11;

import com.reacharound.Config;
import com.reacharound.ReachAroundFinder;
import com.reacharound.Reacharound;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Client side of the Quark-style reach-around. The server needs nothing: the placement is sent as a normal block
 * placement click, aimed at the empty target cell (which is replaceable, so the block simply goes into it).
 */
public class ClientHandler {

    private static final ResourceLocation HORIZONTAL = new ResourceLocation(
        Reacharound.MODID,
        "textures/gui/reacharound_overlay_horizontal.png");
    private static final ResourceLocation VERTICAL = new ResourceLocation(
        Reacharound.MODID,
        "textures/gui/reacharound_overlay_vertical.png");

    private ReachAroundFinder.Target currentTarget;
    private long lastPlacementTick = Long.MIN_VALUE;

    private static ReachAroundFinder.Target computeTarget(Minecraft mc) {
        if (!Config.reachAroundEnabled || mc.thePlayer == null
            || mc.theWorld == null
            || mc.playerController == null
            || mc.currentScreen != null) {
            return null;
        }
        EntityPlayer player = mc.thePlayer;
        ItemStack stack = player.getHeldItem();
        if (stack == null || !(stack.getItem() instanceof ItemBlock)) return null;

        // On the client the player's posY already includes the eye height; this mirrors how vanilla builds the ray.
        Vec3 eye = Vec3.createVectorHelper(
            player.posX,
            player.posY + (player.getEyeHeight() - player.getDefaultEyeHeight()),
            player.posZ);
        Vec3 look = player.getLook(1.0F);

        ReachAroundFinder.Target target = ReachAroundFinder.find(
            player,
            mc.theWorld,
            stack,
            mc.objectMouseOver,
            eye,
            look,
            mc.playerController.getBlockReachDistance(),
            Config.leniency);
        if (target == null) return null;

        // Only report targets where the block can really be placed (not blocked by the player or other entities).
        ItemBlock item = (ItemBlock) stack.getItem();
        if (!item.func_150936_a(mc.theWorld, target.x, target.y, target.z, target.side, player, stack)) return null;
        return target;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        currentTarget = computeTarget(Minecraft.getMinecraft());
    }

    /**
     * Fired (client side) when the right mouse button is used while the crosshair points at nothing. Both vanilla and
     * Backhand post this event before using the item in the air, so we can place the block here and cancel the use.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_AIR) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || event.entityPlayer != mc.thePlayer) return;

        long now = mc.theWorld.getTotalWorldTime();
        if (now == lastPlacementTick) {
            // Already placed this tick (e.g. Backhand is now trying the other hand): do not use a second item.
            event.setCanceled(true);
            return;
        }

        ReachAroundFinder.Target target = computeTarget(mc);
        if (target == null) return;

        EntityPlayer player = mc.thePlayer;
        ItemStack stack = player.getHeldItem();
        Vec3 hit = Vec3.createVectorHelper(target.x + target.hitX, target.y + target.hitY, target.z + target.hitZ);

        if (mc.playerController
            .onPlayerRightClick(player, mc.theWorld, stack, target.x, target.y, target.z, target.side, hit)) {
            player.swingItem();
            if (stack.stackSize <= 0) {
                player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
            }
            lastPlacementTick = now;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onCrosshairRendered(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS) return;

        ReachAroundFinder.Target target = currentTarget;
        if (target == null || !Config.showIndicator) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.thirdPersonView != 0 || mc.gameSettings.hideGUI) return;

        int x = event.resolution.getScaledWidth() / 2 - 7;
        int y = event.resolution.getScaledHeight() / 2 - 7;

        mc.getTextureManager()
            .bindTexture(target.vertical ? VERTICAL : HORIZONTAL);
        GL11.glEnable(GL11.GL_BLEND);
        // Same blend mode as the vanilla crosshair, so the icon inverts the colours behind it.
        OpenGlHelper.glBlendFunc(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE_MINUS_SRC_COLOR, 1, 0);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + 16, 0.0D, 0.0D, 1.0D);
        tessellator.addVertexWithUV(x + 16, y + 16, 0.0D, 1.0D, 1.0D);
        tessellator.addVertexWithUV(x + 16, y, 0.0D, 1.0D, 0.0D);
        tessellator.addVertexWithUV(x, y, 0.0D, 0.0D, 0.0D);
        tessellator.draw();

        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glDisable(GL11.GL_BLEND);
        mc.getTextureManager()
            .bindTexture(Gui.icons);
    }
}
