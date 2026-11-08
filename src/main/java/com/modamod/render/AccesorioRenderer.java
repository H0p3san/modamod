package com.modamod.render;

import com.modamod.item.BandaItem;
import com.modamod.item.CalzadoItem;
import com.modamod.item.SombreroItem;
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
        return com.modamod.prenda.Prendas.es(stack, com.modamod.prenda.CategoriaDePrenda.SOMBRERO) || com.modamod.prenda.Prendas.es(stack, com.modamod.prenda.CategoriaDePrenda.BANDA)
                || com.modamod.prenda.Prendas.es(stack, com.modamod.prenda.CategoriaDePrenda.CALZADO) || com.modamod.prenda.Prendas.es(stack, com.modamod.prenda.CategoriaDePrenda.MALETIN)
                || com.modamod.prenda.Prendas.es(stack, com.modamod.prenda.CategoriaDePrenda.CARA);
    }

    /** {@code dil}: lo que sale del cuerpo la ropa de abajo (solo lo usa el cinto). */
    public static void dibujar(ItemStack stack, BipedEntityModel<?> biped, MatrixStack matrices,
                               VertexConsumerProvider vertexConsumers, int luz, float dil) {
        if (com.modamod.prenda.Prendas.es(stack, com.modamod.prenda.CategoriaDePrenda.SOMBRERO)) SombreroRenderer.dibujar(stack, biped, matrices, vertexConsumers, luz);
        else if (com.modamod.prenda.Prendas.es(stack, com.modamod.prenda.CategoriaDePrenda.BANDA)) BandaRenderer.dibujar(stack, biped, matrices, vertexConsumers, luz, dil);
        else if (com.modamod.prenda.Prendas.es(stack, com.modamod.prenda.CategoriaDePrenda.CALZADO)) BorcegosRenderer.dibujar(stack, biped, matrices, vertexConsumers, luz);
        else if (com.modamod.prenda.Prendas.es(stack, com.modamod.prenda.CategoriaDePrenda.MALETIN)) MaletinRenderer.dibujar(stack, biped, matrices, vertexConsumers, luz);
        else if (com.modamod.prenda.Prendas.es(stack, com.modamod.prenda.CategoriaDePrenda.CARA)) CaraRenderer.dibujar(stack, biped, matrices, vertexConsumers, luz);
    }

    /** Las cajas del accesorio en el marco de {@code parte} (solo los borcegos tienen una forma por pie). */
    public static List<SombreroRenderer.Caja> cajas(ItemStack stack, com.modamod.prenda.Parte parte) {
        if (com.modamod.prenda.Prendas.es(stack, com.modamod.prenda.CategoriaDePrenda.CALZADO)) return BorcegosRenderer.cajas(stack, parte == com.modamod.prenda.Parte.PIERNA_IZQ);
        return cajas(stack);
    }

    public static List<SombreroRenderer.Caja> cajas(ItemStack stack) {
        if (com.modamod.prenda.Prendas.es(stack, com.modamod.prenda.CategoriaDePrenda.BANDA)) return BandaRenderer.cajas(stack);
        if (com.modamod.prenda.Prendas.es(stack, com.modamod.prenda.CategoriaDePrenda.CALZADO)) return BorcegosRenderer.cajas(stack);
        if (com.modamod.prenda.Prendas.es(stack, com.modamod.prenda.CategoriaDePrenda.MALETIN)) return MaletinRenderer.cajas();
        if (com.modamod.prenda.Prendas.es(stack, com.modamod.prenda.CategoriaDePrenda.CARA)) return CaraRenderer.cajas(stack);
        return SombreroRenderer.cajas(stack);
    }
}
