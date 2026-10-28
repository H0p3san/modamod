package com.modamod.render;

/**
 * Motivos de estampado repetido para la Estación de Tintes (2026-09-28,
 * propuesta de patrones, Fase 1: "corazones, estrellas..."). A diferencia
 * de las rayas ({@link PatronGenerador.Forma}), un motivo es un dibujo
 * chico en pixel art que {@link PatronGenerador} repite según la
 * {@link Repeticion} del cuadradito. Los sprites se escriben a mano acá:
 * 'X' = pinta, cualquier otra cosa = hueco.
 *
 * <p>{@link #VICHY} no tiene sprite: es procedural (franjas cruzadas con
 * cobertura parcial, ver {@link PatronGenerador#coberturaCuadros}), y la
 * repetición no le cambia nada.
 */
public enum Motivo {
    CORAZONES(new String[]{
            ".XX...XX.",
            "XXXX.XXXX",
            "XXXXXXXXX",
            "XXXXXXXXX",
            ".XXXXXXX.",
            "..XXXXX..",
            "...XXX...",
            "....X....",
    }),
    ESTRELLAS(new String[]{
            "....X....",
            "....X....",
            "...XXX...",
            "XXXXXXXXX",
            ".XXXXXXX.",
            "..XXXXX..",
            "..XXXXX..",
            ".XX...XX.",
            ".X.....X.",
    }),
    LUNARES(new String[]{
            "..XXX..",
            ".XXXXX.",
            "XXXXXXX",
            "XXXXXXX",
            "XXXXXXX",
            ".XXXXX.",
            "..XXX..",
    }),
    VICHY(null),
    // Animal print (2026-10-19, "tramas de animal print", "si dale sin pestaña"): procedurales como el vichy, siempre al
    // final del enum (el orden viaja en los diseños guardados). Ver PatronGenerador#coberturaAnimal.
    LEOPARDO(null), CEBRA(null), TIGRE(null), VACA(null), JIRAFA(null), SERPIENTE(null), DALMATA(null),
    // Cuadros (2026-10-22, "agregar dos patrones mas, escocesa onda kilt y leñador" + pata de gallo): cuadrícula como el
    // vichy, ver PatronGenerador#coberturaCuadros. Al final del enum por los diseños guardados.
    TARTAN(null), LENADOR(null), PATA_DE_GALLO(null),
    // Argyle (cuadros, rombos con líneas cruzadas) y camuflaje (ruido, como el animal print) (2026-10-22, "sigamos").
    ARGYLE(null), CAMUFLAJE(null),
    // Lote 2 (2026-10-22, "se puede un camo pixelado?" + "segui con todo"): camuflaje digital (el ruido del camuflaje
    // en cuadraditos), rayas diplomáticas, ventana y zigzag (cuadros).
    CAMUFLAJE_DIGITAL(null), DIPLOMATICAS(null), VENTANA(null), ZIGZAG(null);

    /** Filas del sprite, o null si el motivo es procedural. */
    private final String[] sprite;
    public final int ancho, alto;

    Motivo(String[] sprite) {
        this.sprite = sprite;
        this.ancho = sprite == null ? 0 : sprite[0].length();
        this.alto = sprite == null ? 0 : sprite.length;
    }

    public boolean esProcedural() { return sprite == null; }

    /** Los animal print: procedurales con ruido, no la cuadrícula del vichy. */
    public boolean esAnimal() { return this == CAMUFLAJE || this == CAMUFLAJE_DIGITAL || (ordinal() >= LEOPARDO.ordinal() && ordinal() <= DALMATA.ordinal()); }

    /** ¿Pinta el pixel (sx, sy) del sprite? Fuera del sprite, no. */
    public boolean pinta(int sx, int sy) {
        if (sprite == null || sx < 0 || sy < 0 || sy >= alto || sx >= ancho) return false;
        return sprite[sy].charAt(sx) == 'X';
    }
}
