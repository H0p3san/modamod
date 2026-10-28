package com.modamod.client;

import com.modamod.item.ModamodComponents;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * La prenda madre de un diseño, igual en todas las máquinas (2026-10-14, "del icono del diseño lo normalicemos en todas
 * las guis"): lo que se dibuja a la izquierda de cada fila de las listas de diseños.
 */
public final class IconoDiseno {
    private IconoDiseno() {}

    public static ItemStack madre(ItemStack diseno) {
        var estilo = diseno.get(ModamodComponents.DISENO_ESTILO);
        if (estilo != null) return porId(estilo.getString("Prenda"));
        var subli = diseno.get(ModamodComponents.DISENO_SUBLIMADO);
        if (subli != null) return porId(subli.getString("Item"));
        var tinte = diseno.get(ModamodComponents.DISENO_TINTE);
        if (tinte != null) return porCategoria(tinte.getString("Categoria"));
        var corte = diseno.get(ModamodComponents.DISENO_CORTE);
        if (corte != null) return porCategoria(corte.categoria());
        return ItemStack.EMPTY;
    }

    /** Dibuja la prenda madre achicada a {@code lado} px (las filas de Tintes y Modeladora miden 12). */
    public static void dibujar(net.minecraft.client.gui.DrawContext c, ItemStack diseno, int x, int y, int lado) {
        ItemStack m = madre(diseno);
        if (m.isEmpty()) return;
        float k = lado / 16f;
        c.getMatrices().push();
        c.getMatrices().translate(x, y, 0);
        c.getMatrices().scale(k, k, 1f);
        c.drawItem(m, 0, 0);
        c.getMatrices().pop();
    }

    private static ItemStack porId(String id) {
        Identifier i = Identifier.tryParse(id);
        var item = i == null ? Items.AIR : Registries.ITEM.get(i);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static ItemStack porCategoria(String cat) {
        if ("BANNER".equals(cat)) return new ItemStack(net.minecraft.item.Items.WHITE_BANNER);   // 2026-10-24
        String n = switch (cat) {
            case "REMERA" -> "remera";
            case "PANTALON" -> "pantalon";
            case "MEDIAS" -> "socks_solid";
            case "CALIENTABRAZOS" -> "calientabrazos";
            case "POLLERA" -> "pollera";
            case "CAPA" -> "capa";
            case "SOMBRERO" -> "sombrero_bruja";
            case "BANDA" -> "banda";
            case "CALZADO" -> "borcegos";
            case "DELANTAL" -> "delantal";
            case "MALETIN" -> "maletin";
            case "MASCARA" -> "antifaz";
            case "BARBIJO" -> "barbijo";
            default -> "";
        };
        return n.isEmpty() ? ItemStack.EMPTY : porId("modamod:" + n);
    }

    /** Fila de una lista de diseños, igual en todas las máquinas: fondo arena, prenda madre, nombre y etiqueta opcional. */
    public static void fila(net.minecraft.client.gui.DrawContext c, net.minecraft.client.font.TextRenderer tr, int x, int y, int w, int h,
                            boolean hover, ItemStack diseno, String etiqueta) {
        c.fill(x, y, x + w, y + h, hover ? 0xFFC9A877 : 0xFFD9BF8E);
        c.drawItem(madre(diseno), x + 1, y + (h - 16) / 2);
        int libre = w - 24 - (etiqueta == null ? 0 : tr.getWidth(etiqueta) + 6);
        c.drawText(tr, tr.trimToWidth(diseno.getName().getString(), libre), x + 20, y + (h - 8) / 2, EstiloPergamino.TEXTO, false);
        if (etiqueta != null) c.drawText(tr, etiqueta, x + w - 3 - tr.getWidth(etiqueta), y + (h - 8) / 2, 0xFF7A5A36, false);
    }

    /**
     * El casillero del diseño en blanco de exportar (2026-10-25), con el marco de los slots de las máquinas y, vacío, el
     * diseño en blanco en fantasma para que se entienda qué va ahí. {@code x, y}: la esquina del ítem de 16×16.
     */
    public static void marcoBlanco(net.minecraft.client.gui.DrawContext c, int x, int y, boolean lleno) {
        c.fill(x - 1, y - 1, x + 17, y + 17, 0xFF6B5136);
        c.fill(x, y, x + 16, y + 16, 0xFFC9A877);
        if (lleno) return;
        c.drawItem(new ItemStack(com.modamod.modelado.ModeladoMod.DISENO_EN_BLANCO), x, y);
        c.getMatrices().push();
        c.getMatrices().translate(0, 0, 250);   // el velo arriba del ítem (los ítems se dibujan a z 150)
        c.fill(x, y, x + 16, y + 16, 0xA0C9A877);   // velo: es un fantasma, no un ítem de verdad
        c.getMatrices().pop();
    }

    /** La tarjeta "★ Último" que abre toda lista de diseños, con una barrita del color de destaque de la máquina. */
    public static void tarjetaUltimo(net.minecraft.client.gui.DrawContext c, net.minecraft.client.font.TextRenderer tr, int x, int y, int w, int h,
                                     boolean hover) {
        c.fill(x, y, x + w, y + h, hover ? 0xFFC9A877 : 0xFFD2B47F);
        c.fill(x, y, x + 2, y + h, EstiloPergamino.tema().claro);
        c.drawText(tr, tr.trimToWidth(net.minecraft.text.Text.translatable("modamod.estilista.disenos.ultimo").getString(), w - 8),
                x + 5, y + (h - 8) / 2, 0xFF4B2E83, false);
    }
}
