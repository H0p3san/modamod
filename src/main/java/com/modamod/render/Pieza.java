package com.modamod.render;

import com.modamod.garment.Parte;
import net.minecraft.util.Identifier;

/**
 * Un pedazo dibujable de una prenda: que parte del cuerpo, en que capa, con
 * que textura, y con que dilatacion (Calce — a pedido, 2026-09-15).
 *
 * Que una prenda sea una LISTA de estas y no una sola cosa es lo que deja que
 * una remera dibuje torso y brazos con dos texturas distintas, que un remeron
 * baje al muslo, y que una polera sea torso mas una banda en la cabeza. El
 * slot sigue existiendo para el inventario; lo que se dibuja son piezas.
 *
 * La textura va en LAYOUT DE SKIN a {@link CuerpoGeometria#ESCALA}x, y tiene
 * que ser TRANSPARENTE donde la prenda no tiene tela. Ese es el contrato del
 * sistema de capas: rellenar el hueco con piel es lo que hacia que dos
 * prendas en la misma pierna se taparan entre si.
 *
 * {@code dilatacion} reemplaza la dilatacion FIJA de
 * {@link CuerpoGeometria.Superficie#TELA} — el constructor de 3 args
 * (compat) usa esa misma fija, asi que llamar {@code new Pieza(parte, capa,
 * textura)} como siempre da el calce "Normal" de toda la vida.
 *
 * {@code filaDesde}/{@code filaHasta} (de las 12 filas del cuboide, [0,12)
 * = pieza entera) son que filas tiene tela de VERDAD — a pedido
 * (2026-09-16): cuando el calce de esta pieza es mas chico que el cuerpo
 * (Ajustado), {@link GarmentFeatureRenderer} necesita saber EXACTAMENTE
 * donde para achicar la piel de abajo solo ahi, no en toda la parte (ver
 * {@code CuerpoGeometria#cuerpoSegmentado}). El constructor de 4 args
 * (compat) asume pieza entera.
 */
public record Pieza(Parte parte, int capa, Identifier textura, float dilatacion, int filaDesde, int filaHasta,
                    boolean volumenPierna, com.modamod.item.Ruedo ruedo, com.modamod.item.Ruedo ruedoSup) {

    /** Sin ruedo propio en el borde de arriba. */
    public Pieza(Parte parte, int capa, Identifier textura, float dilatacion, int filaDesde, int filaHasta,
                 boolean volumenPierna, com.modamod.item.Ruedo ruedo) {
        this(parte, capa, textura, dilatacion, filaDesde, filaHasta, volumenPierna, ruedo, com.modamod.item.Ruedo.RECTO);
    }

    /** El borde de arriba (la primera fila con tela) es elástico: aprieta (2026-10-08, borde superior de medias y calientabrazos). */
    public boolean elasticoSup() { return ruedoSup == com.modamod.item.Ruedo.AJUSTADO; }

    /** Compat: {@code elastico} = ruedo AJUSTADO, si no RECTO. */
    public Pieza(Parte parte, int capa, Identifier textura, float dilatacion, int filaDesde, int filaHasta,
                 boolean volumenPierna, boolean elastico) {
        this(parte, capa, textura, dilatacion, filaDesde, filaHasta, volumenPierna,
                elastico ? com.modamod.item.Ruedo.AJUSTADO : com.modamod.item.Ruedo.RECTO);
    }

    /** El borde libre de esta pieza es elástico (puños y ruedo del hoodie): aprieta y no cuelga. */
    public boolean elastico() { return ruedo == com.modamod.item.Ruedo.AJUSTADO; }

    /** El borde libre se abre en campana. */
    public boolean campana() { return ruedo == com.modamod.item.Ruedo.CAMPANA; }

    /** La tela se frunce (acordeón) cerca del borde de abajo / de arriba. */
    public boolean fruncido() { return ruedo == com.modamod.item.Ruedo.FRUNCIDO; }
    public boolean fruncidoSup() { return ruedoSup == com.modamod.item.Ruedo.FRUNCIDO; }

    /**
     * Sin elástico. {@code elastico} (2026-09-30, hoodie: "puños y ruedo
     * elásticos"): la última fila con tela aprieta hacia el cuerpo y la
     * prenda no cuelga — la caída se ve como un "globo" arriba de la banda
     * (ver {@code GarmentFeatureRenderer#dibujarPiezas}).
     */
    public Pieza(Parte parte, int capa, Identifier textura, float dilatacion, int filaDesde, int filaHasta,
                 boolean volumenPierna) {
        this(parte, capa, textura, dilatacion, filaDesde, filaHasta, volumenPierna, false);
    }

    /**
     * Sin volumen extra (todo lo que había antes). {@code volumenPierna}
     * (2026-09-26, "hacer los muslos para las medias" + "pantorrilla
     * también"): la pieza pide muslo y pantorrilla abultados — ver
     * {@link CuerpoGeometria#telaConVolumenDePierna}.
     */
    public Pieza(Parte parte, int capa, Identifier textura, float dilatacion, int filaDesde, int filaHasta) {
        this(parte, capa, textura, dilatacion, filaDesde, filaHasta, false, false);
    }

    public Pieza(Parte parte, int capa, Identifier textura, float dilatacion) {
        this(parte, capa, textura, dilatacion, 0, 12, false, false);
    }

    public Pieza(Parte parte, int capa, Identifier textura) {
        this(parte, capa, textura, CuerpoGeometria.Superficie.TELA.dilatacion, 0, 12, false, false);
    }
}
