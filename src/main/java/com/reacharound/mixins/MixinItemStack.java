package com.reacharound.mixins;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.reacharound.EdgePlacement;

/**
 * Runs on both logical sides (client prediction and the server), so both reach the same decision from the same
 * click data.
 */
@Mixin(ItemStack.class)
public abstract class MixinItemStack {

    @Inject(method = "tryPlaceItemIntoWorld", at = @At("HEAD"), cancellable = true)
    private void reacharound$redirectToEdge(EntityPlayer player, World world, int x, int y, int z, int side, float hitX,
        float hitY, float hitZ, CallbackInfoReturnable<Boolean> cir) {
        if (EdgePlacement.tryRedirect((ItemStack) (Object) this, player, world, x, y, z, side, hitX, hitY, hitZ)) {
            cir.setReturnValue(true);
        }
    }
}
