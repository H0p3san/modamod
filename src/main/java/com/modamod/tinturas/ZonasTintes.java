package com.modamod.tinturas;

import com.modamod.modelado.ZonasModelado.Enfoque;
import com.modamod.tinturas.TinturasBlockEntity.Categoria;

/**
 * Zonas de la Estación de Tintes (2026-10-12, plan de GUIs: mismo esquema "visor enfocado + tira de zonas" que la
 * Modeladora): cada categoría agrupa sus cuadraditos (índices de {@link TinturasBlockEntity#regionDe}) en 1 a 4 zonas
 * de a 3 como mucho; la pantalla muestra solo los de la zona activa y el visor se acerca a su {@link Enfoque}.
 */
public final class ZonasTintes {
    private ZonasTintes() {}

    /** Clave de traducción (sin prefijo), enfoque del visor y los cuadraditos, de arriba abajo. */
    public record Zona(String clave, Enfoque enfoque, int... casillas) {}

    private static Zona z(String clave, Enfoque e, int... casillas) { return new Zona(clave, e, casillas); }

    private static final Zona[][] ZONAS = {
            // Remera / Top: 0-1 prenda entera, 2 pecho, 3 cuello, 4-5 mangas, 6 borde de abajo, 7 solapas
            {z("cabeza_cuello", Enfoque.CABEZA, 3, 7), z("torso", Enfoque.TORSO, 2, 6),
                    z("brazos", Enfoque.BRAZOS, 4, 5), z("entero", Enfoque.ENTERO, 0, 1)},
            // Pantalón: 0 cintura, 1-3 prenda entera, 5-6 botamangas
            {z("cintura", Enfoque.CINTURA, 0), z("piernas", Enfoque.PIERNAS, 5, 6), z("entero", Enfoque.ENTERO, 1, 2, 3)},
            // Medias: 0-3 cortes sup./inf. de cada pierna, 5-10 prenda entera
            {z("arriba", Enfoque.PIERNAS, 0, 1), z("abajo", Enfoque.PIES, 2, 3),
                    z("entero_a", Enfoque.ENTERO, 5, 6, 7), z("entero_b", Enfoque.ENTERO, 8, 9, 10)},
            // Calientabrazos: igual que las medias, pero en los brazos
            {z("arriba", Enfoque.BRAZOS, 0, 1), z("abajo", Enfoque.BRAZOS, 2, 3),
                    z("entero_a", Enfoque.ENTERO, 5, 6, 7), z("entero_b", Enfoque.ENTERO, 8, 9, 10)},
            // Pollera: 3 capas de prenda entera
            {z("capas", Enfoque.CINTURA, 0, 1, 2)},
            // Capa: exterior, forro, detalles
            {z("capa", Enfoque.ENTERO, 0, 1, 2)},
            // Retazo de aplique: 3 zonas de color
            {z("retazo", Enfoque.ENTERO, 0, 1, 2)},
            // Delantal: 0 prenda entera, 1 peto, 2 falda
            {z("peto", Enfoque.TORSO, 1), z("falda", Enfoque.CINTURA, 2), z("entero", Enfoque.ENTERO, 0)},
            // Sombrero: 0 ala, 1 copa, 2 cinta
            {z("sombrero", Enfoque.CABEZA, 0, 1, 2)},
            // Calzado: el pie derecho (0-2) y el izquierdo (3-5); con la simetría solo se ve el primero
            {z("pie_der", Enfoque.PIES, 0, 1, 2), z("pie_izq", Enfoque.PIES, 3, 4, 5)},
            // Banda: banda, borde y herraje
            {z("banda", Enfoque.CINTURA, 0, 1, 2)},
            // Maletín (2026-10-23): cuero, costuras y manija, herrajes; cuelga de la mano, a la altura de la cintura
            {z("maletin", Enfoque.CINTURA, 0, 1, 2)},
            // Banner (2026-10-24): 3 capas de banner entero; el visor muestra el banner solo
            {z("banner", Enfoque.ENTERO, 0, 1, 2)},
            // Máscara y barbijo (2026-10-24): 3 capas de prenda entera, el visor en la cabeza
            {z("mascara", Enfoque.CABEZA, 0, 1, 2)},
            {z("barbijo", Enfoque.CABEZA, 0, 1, 2)},
    };

    public static Zona[] de(Categoria cat) { return ZONAS[cat.ordinal()]; }

    public static int cantidad(Categoria cat) { return ZONAS[cat.ordinal()].length; }

    /** Cuadradito de la fila {@code fila} de la zona, o -1. */
    public static int casillaDe(Categoria cat, int zona, int fila) {
        Zona[] zs = ZONAS[cat.ordinal()];
        if (zona < 0 || zona >= zs.length || fila < 0 || fila >= zs[zona].casillas().length) return -1;
        return zs[zona].casillas()[fila];
    }

    /** Zona a la que pertenece el cuadradito, o -1. */
    public static int zonaDe(Categoria cat, int casilla) {
        Zona[] zs = ZONAS[cat.ordinal()];
        for (int zi = 0; zi < zs.length; zi++) for (int c : zs[zi].casillas()) if (c == casilla) return zi;
        return -1;
    }

    public static int filaDe(Categoria cat, int casilla) {
        int zi = zonaDe(cat, casilla);
        if (zi < 0) return -1;
        int[] cs = ZONAS[cat.ordinal()][zi].casillas();
        for (int f = 0; f < cs.length; f++) if (cs[f] == casilla) return f;
        return -1;
    }
}
