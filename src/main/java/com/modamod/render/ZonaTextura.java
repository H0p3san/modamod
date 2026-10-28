package com.modamod.render;

import com.modamod.Modamod;
import com.modamod.item.ModamodComponents;
import com.modamod.item.SombreroPatron;
import com.modamod.item.ZonasTenibles;
import com.modamod.region.RegionPintura;
import com.modamod.region.RegionResolver.CapaPatron;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * La textura de una zona del sombrero, la banda o el calzado con las capas de Tintes encima (2026-10-15, "posibilidad de
 * aplicar patrones a sombrero calzado y cintos... para estacion de tintes como las otras prendas"). Cada zona es una
 * textura plana de 64x64 con su color base; sus capas ({@link ModamodComponents#CAPAS_TINTE} con región
 * {@code ZONA_n}) se funden encima con la misma máscara y mezcla que las prendas, sobre la "prenda" plana {@code zona} de
 * {@link PatronGenerador} (256x256, 2 px de máscara por texel; las UV de las mallas son de 64 normalizadas, así que la resolución es libre). Sin capas queda el color liso (o el dibujo simple de antes).
 */
public final class ZonaTextura {

    private static final int TEX = 256, ESC = 2;   // la máscara es de 512: 2 px por texel (a 64 los motivos de ~3 px de skin salían picados)
    private static final Map<String, Identifier> CACHE = new HashMap<>();

    private ZonaTextura() {}

    /** La región de la zona {@code zona} (0..2) del pie {@code izq}: ZONA_1..3 el derecho (o el único), ZONA_4..6 el izquierdo. */
    public static RegionPintura region(int zona, boolean izq) {
        return RegionPintura.values()[RegionPintura.ZONA_1.ordinal() + (izq ? 3 : 0) + zona];
    }

    public static Identifier de(ItemStack stack, int zona, boolean izq) {
        ZonasTenibles zt = (ZonasTenibles) stack.getItem();
        int rgb = zt.coloresDe(stack, izq).get(zona) & 0xFFFFFF;
        SombreroPatron patron = zt.patronesDe(stack, izq).get(zona);
        RegionPintura reg = region(zona, izq);
        List<CapaPatron> capas = new ArrayList<>();
        for (CapaPatron c : stack.getOrDefault(ModamodComponents.CAPAS_TINTE, List.<CapaPatron>of())) {
            if (c.region() == reg) capas.add(c);
        }
        if (capas.isEmpty()) return SombreroRenderer.textura(rgb, patron);
        return componer(rgb, capas, false, false);
    }

    /**
     * La tela de una pieza suelta de una prenda (2026-10-23, la capucha y el cuello polera salían lisos aunque la prenda
     * tuviera patrón): el color base con esas capas encima, sobre la misma "prenda" plana, y la sombra de la textura lisa de
     * {@code CuelloYCapucha} (la mitad derecha más oscura, el lado de adentro; {@code rib}: el acanalado del cuello).
     */
    public static Identifier plana(int rgb, List<CapaPatron> capas, boolean rib) {
        return componer(rgb & 0xFFFFFF, capas, true, rib);
    }

    /** El paño de un banner o escudo (2026-10-24): el color del banner con las capas encima, sobre la textura de 64 de vanilla. */
    public static Identifier banner(int rgb, List<CapaPatron> capas) {
        return componer(rgb & 0xFFFFFF, capas, false, false);
    }

    private static Identifier componer(int rgb, List<CapaPatron> capas, boolean sombra, boolean rib) {
        String key = rgb + "|" + capas.hashCode() + (sombra ? "|s" + rib : "");
        Identifier hecho = CACHE.get(key);
        if (hecho != null) return hecho;

        List<ClothingTextureCache.CapaMascara> mascaras = new ArrayList<>();
        for (CapaPatron c : capas) {
            NativeImage m = PatronGenerador.mascaraDeCapa("zona", c);
            if (!c.lisa() && m == null) continue;
            mascaras.add(ClothingTextureCache.CapaMascara.de(c, m, null));
        }
        int base = 0xFF000000 | ((rgb & 0xFF) << 16) | (rgb & 0xFF00) | ((rgb >> 16) & 0xFF);
        NativeImage img = new NativeImage(TEX, TEX, true);
        for (int y = 0; y < TEX; y++) {
            for (int x = 0; x < TEX; x++) {
                int acc = base;
                for (ClothingTextureCache.CapaMascara cm : mascaras) {
                    int op = cm.opacidadEn(x * ESC + ESC / 2, y * ESC + ESC / 2);
                    if (op > 0) acc = ClothingTextureCache.mezclar(acc, 0xFFFFFFFF, cm.colorEn(x * ESC + ESC / 2, y * ESC + ESC / 2), cm.modo(), op);
                }
                if (sombra) {
                    float f = x >= TEX / 2 ? 0.72F : rib && ((x * 64 / TEX) % 2 == 0) ? 0.82F : 1F;
                    int r = (int) ((acc & 0xFF) * f), g = (int) (((acc >> 8) & 0xFF) * f), b = (int) (((acc >> 16) & 0xFF) * f);
                    acc = (acc & 0xFF000000) | (b << 16) | (g << 8) | r;
                }
                // Recorte binario (la capa de render es de recorte): la transparencia de la tinta a medias se redondea.
                img.setColor(x, y, (acc >>> 24) < 128 ? 0 : acc | 0xFF000000);
            }
        }
        Identifier id = Identifier.of(Modamod.MOD_ID, "dynamic/zona_" + Integer.toHexString(key.hashCode()));
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(img));
        CACHE.put(key, id);
        return id;
    }
}
