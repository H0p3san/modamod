package com.modamod.render;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Como se ve cada prenda. La mitad CLIENTE de {@code Garments}.
 *
 * Esta partido en dos porque las mesas de tinturas y sastreria corren en el
 * servidor y necesitan {@code regionesDe}, pero componer una textura carga
 * NativeImage y MinecraftClient. Es la misma division que ya existe entre
 * ModItems.esEstampable y EstampaTextures.prendaDe, y esta vez esta puesta a
 * proposito y no por accidente.
 */
public final class PiezasDePrenda {

    /**
     * Recibe la entidad porque el aspecto puede depender de ella: los brazos
     * de una skin slim son mas finos, y una prenda podria querer el perfil de
     * cuerpo de quien la lleva.
     *
     * Devolver una lista vacia es valido y significa "no se dibuja nada":
     * una musculosa no tiene pieza de brazo, y dibujar dos cajas transparentes
     * por frame y por jugador es trabajo tirado.
     */
    @FunctionalInterface
    public interface Proveedor {
        List<Pieza> piezas(ItemStack stack, LivingEntity entidad);
    }

    private static final Map<Item, Proveedor> REGISTRO = new HashMap<>();

    private PiezasDePrenda() {}

    public static void registrar(Item item, Proveedor proveedor) {
        REGISTRO.put(item, proveedor);
    }

    public static List<Pieza> de(ItemStack stack, LivingEntity entidad) {
        if (stack.isEmpty()) return List.of();
        Proveedor p = REGISTRO.get(stack.getItem());
        return p == null ? List.of() : p.piezas(stack, entidad);
    }
}
