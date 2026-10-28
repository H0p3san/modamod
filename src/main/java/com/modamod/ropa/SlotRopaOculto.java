package com.modamod.ropa;

import dev.emi.trinkets.SurvivalTrinketSlot;
import dev.emi.trinkets.api.SlotGroup;
import dev.emi.trinkets.api.SlotType;
import dev.emi.trinkets.api.TrinketInventory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/** Slot de ropa del mod en el inventario de siempre: existe (los índices no cambian) pero no se ve ni acepta nada. */
public class SlotRopaOculto extends SurvivalTrinketSlot {

    public SlotRopaOculto(TrinketInventory inventory, int index, int x, int y, SlotGroup group, SlotType type) {
        super(inventory, index, x, y, group, type, index, false);
    }

    @Override
    public boolean isEnabled() { return false; }

    @Override
    public boolean canInsert(ItemStack stack) { return false; }

    @Override
    public boolean canTakeItems(PlayerEntity player) { return false; }
}
