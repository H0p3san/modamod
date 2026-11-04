package com.modamod.ropa;

import com.modamod.prenda.CategoriaDePrenda;
import com.modamod.prenda.Prendas;
import dev.emi.trinkets.api.TrinketInventory;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * La ropa puesta (rediseño, tanda 3): una sola lista de {@link #LUGARES} lugares, el slot {@code ropa/lugar} de Trinkets.
 * El orden es el de dibujo al revés: el lugar 0 (arriba a la izquierda en la pantalla de Ropa) es lo de más afuera y el
 * último, lo de más adentro. Cualquier prenda o accesorio entra en cualquier lugar.
 */
public final class ListaDeRopa {

    private ListaDeRopa() {}

    public static final String GRUPO = "ropa", SLOT = "lugar";
    public static final int LUGARES = 16, COLUMNAS = 4;

    /** Los lugares de ropa de la entidad, o null si no tiene (no es un jugador, o Trinkets no está listo). */
    @Nullable
    public static TrinketInventory de(LivingEntity entidad) {
        var comp = TrinketsApi.getTrinketComponent(entidad);
        if (comp.isEmpty()) return null;
        var grupo = comp.get().getInventory().get(GRUPO);
        return grupo == null ? null : grupo.get(SLOT);
    }

    /**
     * ¿El Top del lugar {@code indice} se lleva como prenda de afuera (chaqueta)? Sí cuando más adentro en la lista hay otro
     * Top: el de afuera se dibuja en la capa exterior.
     */
    public static boolean esTopDeAfuera(TrinketInventory lugares, int indice) {
        if (!Prendas.es(lugares.getStack(indice), CategoriaDePrenda.REMERA)) return false;
        for (int i = indice + 1; i < lugares.size(); i++) {
            if (Prendas.es(lugares.getStack(i), CategoriaDePrenda.REMERA)) return true;
        }
        return false;
    }

    /** La fila de la pantalla de Ropa en la que se ve el lugar {@code indice}, con la fila de la armadura en {@code filaArmadura}. */
    public static int filaEnPantalla(int indice, int filaArmadura) {
        int fila = indice / COLUMNAS;
        return fila >= filaArmadura ? fila + 1 : fila;
    }

    /**
     * Cuántas prendas de cuerpo van por encima de la armadura: las de los lugares de las filas de arriba de la fila de la
     * armadura ({@link Cosmeticos#fila}). Son las últimas de la lista de dibujo, que va de adentro hacia afuera.
     */
    public static int prendasSobreLaArmadura(LivingEntity entidad) {
        TrinketInventory l = de(entidad);
        if (l == null || !(entidad instanceof net.minecraft.entity.player.PlayerEntity jugador)) return 0;
        int hasta = Math.min(l.size(), Cosmeticos.de(jugador).fila() * COLUMNAS), n = 0;
        for (int i = 0; i < hasta; i++) if (Prendas.esRopaDeCuerpo(l.getStack(i))) n++;
        return n;
    }

    /** Lo que tiene puesto en ese lugar, o vacío. */
    public static ItemStack en(LivingEntity entidad, int indice) {
        TrinketInventory l = de(entidad);
        return l == null || indice < 0 || indice >= l.size() ? ItemStack.EMPTY : l.getStack(indice);
    }
}
