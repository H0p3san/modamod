package com.modamod.garment;

/**
 * El ordinal que decide quien va arriba cuando dos prendas comparten una
 * parte del cuerpo.
 *
 * Es la pieza que faltaba para desbloquear todo el track de prendas. Antes
 * cada renderer dibujaba la parte del cuerpo con una textura opaca que era
 * "tela + piel reconstruida en todo lo demas", asi que el ultimo que dibujaba
 * tapaba al anterior POR COMPLETO — no se veia feo, desaparecia una de las
 * dos prendas. Con el ordinal cada prenda dibuja SOLO su tela y donde no
 * tiene se ve lo de abajo.
 *
 * Los numeros se comparan solo DENTRO de una misma {@link Parte}: que el
 * torso exterior sea 25 y el short 20 no significa nada, nunca comparten
 * pixel. Van espaciados de a 5 y 10 para poder meter algo en el medio sin
 * renumerar.
 */
public final class Capa {

    private Capa() {}

    /** El cuerpo base. Siempre lo mas abajo, y lo dibuja el sustrato, no una prenda. */
    public static final int CUERPO = 0;

    /** Ropa interior: binder, corpiño, slip. Lo primero que va sobre el cuerpo. */
    public static final int INTERIOR = 5;

    /** Medias, fishnet, leggings. */
    public static final int MEDIA = 10;

    /**
     * Calientabrazos y cualquier otra prenda "interior" de brazo: va bajo la
     * manga de la remera ({@code TORSO_EXTERIOR}=25 en Parte.BRAZO_DER/IZQ),
     * igual que {@code MEDIA} va bajo {@code PIERNA_EXTERIOR} en la pierna.
     */
    public static final int MANGA_INTERIOR = 15;

    /** Shorts, pantalon, minifalda tubo: lo que va sobre la media. */
    public static final int PIERNA_EXTERIOR = 20;

    /** Remera, top: lo que va sobre el binder. */
    public static final int TORSO_EXTERIOR = 25;

    /** Pollera y todo lo acampanado. */
    public static final int POLLERA = 30;

    /**
     * Chaquetas: hoodie, campera, saco (2026-09-30, "haceme un hoodie
     * oversize... una nueva categoria de prendas llamadas chaquetas"). Va
     * por encima de la remera y de la pollera (un buzo largo cae sobre la
     * cintura de la pollera).
     */
    public static final int CHAQUETA = 35;

    /**
     * El ruedo de un remeron sobre el muslo.
     *
     * Va arriba del short a proposito: una remera larga cae POR ENCIMA del
     * pantalon, no adentro. Es el caso que confirma que una prenda dibuja
     * piezas fuera de su slot.
     */
    public static final int RUEDO = 40;

    /** Calzado. Lo ultimo de la pierna. */
    public static final int CALZADO = 50;
}
