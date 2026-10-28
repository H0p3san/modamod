package com.modamod.render;

import com.modamod.item.BandaItem;
import com.modamod.item.BorcegosItem;
import com.modamod.item.SombreroBrujaItem;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * Los accesorios de cajas con 3 zonas (2026-10-05): el sombrero de bruja, la banda, los borcegos y el maletín (2026-10-23). Un solo punto de entrada para
 * dibujarlos y para apuntarlos con el mouse en la Mesa de estilado.
 */
public final class AccesorioRenderer {

    private AccesorioRenderer() {}

    public static boolean es(ItemStack stack) {
        return stack.getItem() instanceof SombreroBrujaItem || stack.getItem() instanceof BandaItem
                || stack.getItem() instanceof BorcegosItem || stack.getItem() instanceof com.modamod.guardarropas.MaletinItem;
    }

    /** {@code dil}: lo que sale del cuerpo la ropa de abajo (solo lo usa el cinto). */
    public static void dibujar(ItemStack stack, BipedEntityModel<?> biped, MatrixStack matrices,
                               VertexConsumerProvider vertexConsumers, int luz, float dil) {
        if (stack.getItem() instanceof SombreroBrujaItem) SombreroRenderer.dibujar(stack, biped, matrices, vertexConsumers, luz);
        else if (stack.getItem() instanceof BandaItem) BandaRenderer.dibujar(stack, biped, matrices, vertexConsumers, luz, dil);
        else if (stack.getItem() instanceof BorcegosItem) BorcegosRenderer.dibujar(stack, biped, matrices, vertexConsumers, luz);
        else if (stack.getItem() instanceof com.modamod.guardarropas.MaletinItem) MaletinRenderer.dibujar(stack, biped, matrices, vertexConsumers, luz);
    }

    /** Las cajas del accesorio en el marco de {@code parte} (solo los borcegos tienen una forma por pie). */
    public static List<SombreroRenderer.Caja> cajas(ItemStack stack, com.modamod.garment.Parte parte) {
        if (stack.getItem() instanceof BorcegosItem) return BorcegosRenderer.cajas(stack, parte == com.modamod.garment.Parte.PIERNA_IZQ);
        return cajas(stack);
    }

    public static List<SombreroRenderer.Caja> cajas(ItemStack stack) {
        if (stack.getItem() instanceof BandaItem) return BandaRenderer.cajas(stack);
        if (stack.getItem() instanceof BorcegosItem) return BorcegosRenderer.cajas(stack);
        if (stack.getItem() instanceof com.modamod.guardarropas.MaletinItem) return MaletinRenderer.cajas();
        return SombreroRenderer.cajas(stack);
    }
}
