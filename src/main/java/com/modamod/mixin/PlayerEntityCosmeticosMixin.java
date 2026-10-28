package com.modamod.mixin;

import com.modamod.ropa.Cosmeticos;
import com.modamod.ropa.CosmeticosRender;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Armadura cosmética (2026-10-05): mientras se DIBUJA a este jugador, lo que ven los renderizadores de armadura,
 * élitros y cabezas ({@code getEquippedStack}) es la pieza cosmética o nada, según los interruptores. Fuera del
 * render no cambia nada (los stats y la lógica siguen leyendo la armadura real).
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityCosmeticosMixin {

    @Inject(method = "getEquippedStack", at = @At("HEAD"), cancellable = true)
    private void modamod$cosmetico(EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
        if (CosmeticosRender.entidad != (Object) this) return;
        ItemStack visual = Cosmeticos.visual((PlayerEntity) (Object) this, slot);
        if (visual != null) cir.setReturnValue(visual);
    }
}
