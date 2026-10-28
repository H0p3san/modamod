package com.modamod.telar;

import com.modamod.item.ModamodItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.DyeColor;

import java.util.function.Supplier;

/**
 * Las prendas básicas que teje el Telar automático (2026-10-07, "produzca prendas basicas a base de lana e hilo";
 * charlado: remera, pantalón, medias, calientabrazos, pollera y capa, sin hoodie). Nuevos valores siempre al FINAL
 * (viajan por la red por ordinal en la pantalla).
 */
public enum TelarPrenda {
    REMERA(() -> com.modamod.sublimadora.ModItems.REMERA, 7, 0, 0),
    PANTALON(() -> ModamodItems.PANTALON, 7, 0, 0),
    MEDIAS(() -> ModamodItems.SOCKS_SOLID, 2, 1, 0),
    CALIENTABRAZOS(() -> ModamodItems.CALIENTABRAZOS, 2, 1, 0),
    POLLERA(() -> ModamodItems.POLLERA, 6, 3, 0),
    CAPA(() -> ModamodItems.CAPA, 7, 2, 0),
    // 2026-10-12, "agregarle las nuevas prendas cinto delantal calzado sombrero etc con lo cual habria que agregarle
    // carga de cuero": los de cuero no gastan lana (salen con sus colores de fábrica); sombrero, vincha y cofia
    // salen del color de la lana.
    CINTO(() -> ModamodItems.BANDA, 0, 0, 2),
    COLLAR(() -> ModamodItems.BANDA, 0, 1, 1),
    DELANTAL(() -> ModamodItems.DELANTAL, 5, 0, 0),
    BORCEGOS(() -> ModamodItems.BORCEGOS, 0, 2, 4),
    SOMBRERO(() -> ModamodItems.SOMBRERO_BRUJA, 2, 0, 3),
    VINCHA(() -> ModamodItems.SOMBRERO_BRUJA, 1, 1, 0),
    COFIA(() -> ModamodItems.SOMBRERO_BRUJA, 2, 1, 0);

    private final Supplier<? extends Item> item;
    /**
     * Los costos son los de la receta de crafteo (2026-10-14, "el telar automatico a veces requiere menos recursos que
     * la receta normal"; las recetas de a 3 se redondean para arriba por prenda). El Telar solo teje las que tienen
     * receta propia: collar, vincha y cofia quedan afuera ("saques el exceso": queda un cinto y un sombrero).
     */
    public boolean enTelar() {
        return this != COLLAR && this != VINCHA && this != COFIA;   // shortcut: el cinto lleva además 1 pepita de hierro en su receta, que el Telar no tiene como insumo
    }

    /** Cuántas lanas (bloques), hilos y cueros gasta cada prenda. */
    public final int lana, hilo, cuero;

    TelarPrenda(Supplier<? extends Item> item, int lana, int hilo, int cuero) {
        this.item = item;
        this.lana = lana;
        this.hilo = hilo;
        this.cuero = cuero;
    }

    /** La prenda tejida: lisa y del color de la lana, o el accesorio con su corte de fábrica. */
    public ItemStack crear(int rgb) {
        ItemStack s = new ItemStack(item());
        switch (this) {
            case CINTO -> com.modamod.item.BandaItem.setZona(s, com.modamod.item.BandaZona.CINTURA);
            case COLLAR -> {
                com.modamod.item.BandaItem.setZona(s, com.modamod.item.BandaZona.CUELLO);
                com.modamod.item.BandaItem.setAncho(s, com.modamod.item.BandaAncho.FINO);
            }
            case BORCEGOS -> { }
            case SOMBRERO -> {
                // La base es una gorra (2026-10-14, "que no sea el sombrero de bruja puede ser el de copa o la gorra").
                com.modamod.item.SombreroBrujaItem.setAla(s, com.modamod.item.SombreroAla.VISERA_CURVA);
                com.modamod.item.SombreroBrujaItem.setPunta(s, com.modamod.item.SombreroPunta.CUPULA);
                com.modamod.item.SombreroBrujaItem.conColores(s, rgb, rgb, rgb);
            }
            case VINCHA -> {
                com.modamod.item.SombreroBrujaItem.setAla(s, com.modamod.item.SombreroAla.VINCHA);
                com.modamod.item.SombreroBrujaItem.conColores(s, rgb, rgb, rgb);
            }
            case COFIA -> {
                com.modamod.item.SombreroBrujaItem.setAla(s, com.modamod.item.SombreroAla.COFIA);
                com.modamod.item.SombreroBrujaItem.conColores(s, rgb, rgb, rgb);
            }
            default -> s.set(net.minecraft.component.DataComponentTypes.DYED_COLOR,
                    new net.minecraft.component.type.DyedColorComponent(rgb, true));
        }
        return s;
    }

    public Item item() { return item.get(); }

    public String clave() { return name().toLowerCase(); }

    /** Las 16 lanas en el orden de {@link DyeColor}. */
    private static final Item[] LANAS = {
            Items.WHITE_WOOL, Items.ORANGE_WOOL, Items.MAGENTA_WOOL, Items.LIGHT_BLUE_WOOL, Items.YELLOW_WOOL,
            Items.LIME_WOOL, Items.PINK_WOOL, Items.GRAY_WOOL, Items.LIGHT_GRAY_WOOL, Items.CYAN_WOOL,
            Items.PURPLE_WOOL, Items.BLUE_WOOL, Items.BROWN_WOOL, Items.GREEN_WOOL, Items.RED_WOOL, Items.BLACK_WOOL};

    public static boolean esLana(Item item) {
        for (Item l : LANAS) if (l == item) return true;
        return false;
    }

    /** El color de la prenda que sale de esa lana (la blanca queda blanca de verdad); -1 si no es lana. */
    public static int colorDeLana(Item item) {
        for (int i = 0; i < LANAS.length; i++) {
            if (LANAS[i] == item) return i == 0 ? 0xFFFFFF : DyeColor.byId(i).getFireworkColor();
        }
        return -1;
    }
}
