package com.modamod.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** La esquina de la ventana (para poner el botón de Ropa en el inventario). */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {

    @Accessor("x")
    int modamod$getX();

    @Accessor("y")
    int modamod$getY();
}
