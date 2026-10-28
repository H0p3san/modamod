package com.modamod.mixin;

import dev.emi.trinkets.TrinketSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Una sola prenda por slot de Trinkets (2026-10-08, "una prenda por slot de trinket"): las prendas y accesorios del mod
 * apilan hasta 16 en el inventario, pero un slot de ropa recibe una sola aunque arrastres la pila entera.
 */
@Mixin(Slot.class)
public abstract class SlotUnaPrendaMixin {

    @Inject(method = "getMaxItemCount(Lnet/minecraft/item/ItemStack;)I", at = @At("HEAD"), cancellable = true)
    private void modamod$unaPorSlot(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof TrinketSlot && !stack.isEmpty()
                && Registries.ITEM.getId(stack.getItem()).getNamespace().equals("modamod")) {
            cir.setReturnValue(1);
        }
    }
}
