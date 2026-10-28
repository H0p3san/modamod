package com.modamod.garment;

import com.modamod.region.Cara;
import com.modamod.region.Lado;
import com.modamod.region.Operacion;
import com.modamod.region.Region;
import net.minecraft.item.ItemStack;

import java.util.Set;

/**
 * Lo que una prenda sabe de si misma y que NO depende del cliente.
 *
 * Vive de este lado porque las mesas de tinturas y sastreria corren en el
 * servidor y necesitan preguntar "que regiones acepta esta operacion" para
 * armar su selector. El aspecto —que textura, que geometria— es otra cosa y
 * vive en el cliente, igual que ya pasa con ModItems.esEstampable (servidor)
 * contra EstampaTextures.prendaDe (cliente).
 */
public interface Garment {

    /** Los tres lados. Lo normal para cualquier prenda bilateral. */
    Set<Region> BILATERAL = Set.of(Lado.IZQUIERDA, Lado.DERECHA, Lado.AMBAS);

    /** Una sola pieza: no hay nada que elegir. */
    Set<Region> ENTERA = Set.of(Lado.AMBAS);

    /** Las dos caras del torso, sin AMBAS: estampar cuesta tinta POR cara. */
    Set<Region> CARAS = Set.of(Cara.FRENTE, Cara.ESPALDA);

    /**
     * Que regiones tienen sentido para esta operacion.
     *
     * Se pregunta por operacion y no de una vez porque no coinciden: una
     * remera se tine entera (una remera, un color base) pero se estampa por
     * cara. El selector de la estacion muestra exactamente estos botones, asi
     * que una lista de mas es un boton que no hace nada.
     *
     * Vacio = la operacion no aplica a esta prenda.
     */
    Set<Region> regionesDe(Operacion op);

    /**
     * Si tiene sentido ponersela al reves.
     *
     * Una remera es casi simetrica frente/espalda, asi que girarla es
     * remapear la superficie. Una prenda asimetrica DE FORMA —un buzo con
     * capucha, un vestido con cola— no puede: habria que rotar la malla.
     */
    default boolean puedeGirarse() {
        return true;
    }

    /**
     * Que partes del cuerpo ocupa, para saber donde hace falta el cuerpo base.
     *
     * Depende del stack porque el corte manda: una musculosa no ocupa los
     * brazos aunque una remera de manga larga si, y son el mismo item.
     */
    Set<Parte> partes(ItemStack stack);
}
