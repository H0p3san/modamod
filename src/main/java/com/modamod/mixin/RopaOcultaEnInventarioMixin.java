package com.modamod.mixin;

import com.modamod.ropa.RopaScreenHandler;
import com.modamod.ropa.SlotRopaOculto;
import dev.emi.trinkets.SurvivalTrinketSlot;
import dev.emi.trinkets.api.SlotGroup;
import dev.emi.trinkets.api.SlotType;
import dev.emi.trinkets.api.TrinketInventory;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Los slots de ropa del mod ya no se ven en el inventario de siempre (2026-10-05, "limpiar la gui de tanto ruido...
 * deja los que no son de ropa del mod"): Trinkets los agrega al {@link PlayerScreenHandler}; acá se cambian por una
 * versión apagada ({@link SlotRopaOculto}). Los de otros mods no se tocan. Se manejan desde la pantalla de Ropa.
 */
@Mixin(ScreenHandler.class)
public abstract class RopaOcultaEnInventarioMixin {

    @ModifyVariable(method = "addSlot", at = @At("HEAD"), argsOnly = true)
    private Slot modamod$ocultarRopa(Slot slot) {
        if (!((Object) this instanceof PlayerScreenHandler) || !(slot instanceof SurvivalTrinketSlot s)
                || s instanceof SlotRopaOculto) {
            return slot;
        }
        SlotType tipo = s.getType();
        if (!RopaScreenHandler.esDelMod(tipo.getGroup(), tipo.getName())) return slot;
        TrinketInventory inv = (TrinketInventory) s.inventory;
        if (!(inv.getComponent().getEntity() instanceof net.minecraft.entity.player.PlayerEntity jugador)) return slot;
        SlotGroup grupo = TrinketsApi.getPlayerSlots(jugador).get(tipo.getGroup());
        if (grupo == null) return slot;
        return new SlotRopaOculto(inv, s.getIndex(), s.x, s.y, grupo, tipo);
    }
}
