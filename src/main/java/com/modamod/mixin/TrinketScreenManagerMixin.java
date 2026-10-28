package com.modamod.mixin;

import dev.emi.trinkets.TrinketScreenManager;
import dev.emi.trinkets.TrinketsClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pasar el mouse por el lugar donde estaban los grupos de ropa del mod (ahora apagados) no abre ningún panel de
 * Trinkets (2026-10-05). Los grupos que son solo del mod; "head" se comparte con otros mods y no se toca.
 */
@Mixin(value = TrinketScreenManager.class, remap = false)
public abstract class TrinketScreenManagerMixin {

    @Inject(method = "update", at = @At("TAIL"))
    private static void modamod$sinGruposDelMod(float mouseX, float mouseY, CallbackInfo ci) {
        if (TrinketsClient.activeGroup != null && soloDelMod(TrinketsClient.activeGroup.getName())) {
            TrinketsClient.activeGroup = null;
            TrinketScreenManager.group = null;
        }
        if (TrinketsClient.quickMoveGroup != null && soloDelMod(TrinketsClient.quickMoveGroup.getName())) {
            TrinketsClient.quickMoveGroup = null;
        }
    }

    private static boolean soloDelMod(String grupo) {
        return grupo.equals("torso") || grupo.equals("piernas") || grupo.equals("arms") || grupo.equals("socks")
                || grupo.equals("espalda");
    }
}
