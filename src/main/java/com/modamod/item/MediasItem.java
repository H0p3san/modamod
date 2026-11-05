package com.modamod.item;

import com.modamod.prenda.PrendaItem;
import com.modamod.region.Lado;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/** Las medias: el nombre sale de dónde empieza y termina la tela (2026-10-23): soquetes, bucaneras, calzas... */
public class MediasItem extends PrendaItem {

    public MediasItem(Settings settings) {
        super(settings);
    }

    @Override
    public Text getName(ItemStack stack) {
        int[] der = MediasLargo.filasVisibles(stack, Lado.DERECHA);
        int[] izq = MediasLargo.filasVisibles(stack, Lado.IZQUIERDA);
        return Text.translatable("item.modamod.medias." + (java.util.Arrays.equals(der, izq) ? NombresDePrenda.medias(der) : "medias"));
    }
}
