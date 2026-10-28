package com.modamod.render;

import com.modamod.garment.Parte;

import java.util.List;

/**
 * Que hay que borrarle a la SKIN del jugador para que la ropa del mod se vea.
 *
 * Antes esta clase hacia dos cosas: borrar la segunda capa donde va una
 * prenda, y REPINTAR con tono de piel lo que la prenda dejaba a la vista.
 * Lo segundo se fue entero: de eso se ocupa el cuerpo base, que es una
 * geometria propia dibujada abajo de la ropa. Con eso desaparecieron la
 * tabla de rectangulos escritos a mano, el muestreo de paleta y la
 * reconstruccion por corte — el UV del cuerpo base es conocido, no hay nada
 * que adivinar.
 *
 * Lo que queda no es opcional. La skin trae una SEGUNDA CAPA que vanilla
 * dibuja inflada 0.25 y que 3D Skin Layers convierte en geometria 3D real:
 * si la skin tiene una remera pintada ahi, esa remera pasa a ser volumen
 * ALREDEDOR del cuerpo y la prenda del mod queda tapada adentro. Borrarla
 * apaga las dos cosas de una, porque 3DSL solo extruye pixeles solidos.
 *
 * Es {@code f(partes cubiertas)} y ya no {@code f(prenda, corte)}: no
 * importa hasta donde llega la tela, importa que parte del cuerpo gobierna
 * el mod. Un croptop y un remeron borran lo mismo.
 */
public final class SkinRegions {

    private SkinRegions() {}

    /**
     * Los rectangulos de la capa externa a borrar, para esas partes.
     *
     * Se borra la parte ENTERA y no solo lo que la prenda tapa. Recortar al
     * borde de la tela dejaba la capa vieja asomando por el escote, por las
     * sisas y por la costura de abajo cuando la prenda se mueve. Debajo de la
     * tela no se ve nada, asi que borrar de mas no cuesta y borrar de menos
     * se nota.
     */
    public static List<CajaSkin.Rect> aBorrar(List<Parte> partes, boolean slim) {
        return partes.stream()
                .map(parte -> LayoutSkin.overlay(parte, slim).todo())
                .toList();
    }

    /**
     * Con que identificar el conjunto, para cachear la skin compuesta.
     *
     * Alcanza con las partes: dos cortes distintos de la misma prenda borran
     * exactamente lo mismo, asi que comparten skin compuesta en vez de
     * generar una por corte como pasaba antes.
     */
    public static String clave(List<Parte> partes, boolean slim) {
        StringBuilder sb = new StringBuilder(slim ? "slim" : "wide");
        for (Parte p : Parte.values()) {
            if (partes.contains(p)) sb.append(';').append(p.clave());
        }
        return sb.toString();
    }
}
