package com.modamod.util;

import com.modamod.item.ClothingPatternItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.DyeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Shulkers cargados para la pestaña creativa (2026-10-01, los comandos
 * {@code /modamod debug patrones|moldes|insumos|kit} → "agregalos al
 * creativo con nombres claros"): todos los patrones, todos los moldes (en
 * las cajas que hagan falta) e insumos (64 de cada tinte y de papel). La
 * lista sale de los registros, así que un patrón o molde nuevo entra solo.
 */
public final class KitsCreativos {

    private KitsCreativos() {}

    private static final int POR_CAJA = 27;

    public static List<ItemStack> todos() {
        List<ItemStack> kits = new ArrayList<>();

        List<ItemStack> patrones = new ArrayList<>();
        for (ClothingPatternItem p : ClothingPatternItem.todos()) patrones.add(new ItemStack(p));
        kits.addAll(enCajas(patrones, "modamod.kit.patrones"));

        // Los moldes no tienen un registro común: se reconocen por el nombre de la clase.
        List<ItemStack> moldes = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            if (item.getClass().getSimpleName().contains("Molde")) moldes.add(new ItemStack(item));
        }
        kits.addAll(enCajas(moldes, "modamod.kit.moldes"));

        List<ItemStack> insumos = new ArrayList<>();
        for (DyeColor color : DyeColor.values()) insumos.add(new ItemStack(DyeItem.byColor(color), 64));
        insumos.add(new ItemStack(Items.PAPER, 64));
        insumos.add(new ItemStack(Items.GLASS_BOTTLE, 64));
        kits.addAll(enCajas(insumos, "modamod.kit.insumos"));
        return kits;
    }

    /** Reparte en shulkers de 27; si son varias, el nombre lleva "(1/2)", "(2/2)". */
    private static List<ItemStack> enCajas(List<ItemStack> items, String claveNombre) {
        List<ItemStack> cajas = new ArrayList<>();
        int total = (items.size() + POR_CAJA - 1) / POR_CAJA;
        for (int i = 0, n = 1; i < items.size(); i += POR_CAJA, n++) {
            ItemStack caja = new ItemStack(Items.SHULKER_BOX);
            caja.set(DataComponentTypes.CONTAINER,
                    ContainerComponent.fromStacks(items.subList(i, Math.min(i + POR_CAJA, items.size()))));
            Text nombre = Text.translatable(claveNombre);
            if (total > 1) nombre = Text.translatable("modamod.kit.parte", nombre, n, total);
            caja.set(DataComponentTypes.CUSTOM_NAME, nombre);
            cajas.add(caja);
        }
        return cajas;
    }
}
