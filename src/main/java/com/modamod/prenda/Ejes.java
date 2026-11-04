package com.modamod.prenda;

import com.modamod.item.*;
import com.modamod.region.Orientacion;
import com.modamod.sublimadora.Variante;

import java.util.Map;

import static com.modamod.prenda.CategoriaDePrenda.*;

/**
 * Todos los ejes de corte del mod, agrupados por prenda. Es la lista de qué forma puede tener cada prenda; se guardan
 * todos juntos en {@link Corte}. Cada uno dice además su valor de fábrica, en qué prendas vale y su otro lado, si tiene:
 * con eso un molde de la Modeladora se aplica sin código propio. Los nombres son los que se escriben en el guardado.
 */
public final class Ejes {
    private Ejes() {}

    // --- Comunes a varias prendas ---
    public static final EjeDeCorte<Calce> CALCE = EjeDeCorte.enumerado("calce", Calce::values)
            .fabrica(Calce.NORMAL).en(REMERA, PANTALON, MEDIAS, CALIENTABRAZOS, POLLERA, DELANTAL);
    /** La trama del lado izquierdo (2026-11-04, "left custom y right custom no cambian asimetricamente"); sin valor, vale {@link #RED}. */
    public static final EjeDeCorte<PatronRed> RED_IZQ = EjeDeCorte.enumerado("red_izq", PatronRed::values);
    public static final EjeDeCorte<PatronRed> RED = EjeDeCorte.enumerado("red", PatronRed::values)
            .fabrica(PatronRed.LISA).en(REMERA, PANTALON, MEDIAS, CALIENTABRAZOS, POLLERA, DELANTAL).conOtroLado(RED_IZQ, true);
    /** Un ruedo por borde libre; un molde trae solo los suyos y se suman (ver {@link Ruedos#aplicar}). */
    public static final EjeDeCorte<Map<ZonaRuedo, Ruedo>> RUEDOS = EjeDeCorte.de("ruedos", Ruedos.CODEC);
    /** Cómo se lleva puesta (al derecho o dada vuelta). */
    public static final EjeDeCorte<Orientacion> ORIENTACION = EjeDeCorte.de("orientacion", Orientacion.CODEC);

    // --- Remera y top ---
    public static final EjeDeCorte<Variante.Largo> REMERA_LARGO = EjeDeCorte.enumerado("remera_largo", Variante.Largo::values)
            .fabrica(Variante.BASE.largo()).en(REMERA);
    public static final EjeDeCorte<Variante.Manga> MANGA_DERECHA = EjeDeCorte.enumerado("manga_derecha", Variante.Manga::values);
    /** La manga de los dos brazos, o la izquierda cuando la derecha tiene la suya ({@link #MANGA_DERECHA}). */
    public static final EjeDeCorte<Variante.Manga> REMERA_MANGA = EjeDeCorte.enumerado("remera_manga", Variante.Manga::values)
            .fabrica(Variante.BASE.manga()).en(REMERA).conOtroLado(MANGA_DERECHA, false);
    public static final EjeDeCorte<Variante.Cuello> REMERA_CUELLO = EjeDeCorte.enumerado("remera_cuello", Variante.Cuello::values)
            .fabrica(Variante.BASE.cuello()).en(REMERA);
    /** El frente abierto o cerrado en este momento (se cambia puesta). */
    public static final EjeDeCorte<Boolean> FRENTE_AHORA = EjeDeCorte.bandera("frente_ahora");
    public static final EjeDeCorte<ChaquetaFrente> FRENTE = EjeDeCorte.enumerado("frente", ChaquetaFrente::values)
            .fabrica(ChaquetaFrente.CERRADA).en(REMERA).reinicia(FRENTE_AHORA);
    public static final EjeDeCorte<ChaquetaSolapa> SOLAPA = EjeDeCorte.enumerado("solapa", ChaquetaSolapa::values)
            .fabrica(ChaquetaSolapa.NINGUNA).en(REMERA);
    public static final EjeDeCorte<Boolean> CON_CAPUCHA = EjeDeCorte.bandera("con_capucha").fabrica(false).en(REMERA);
    /** La capucha puesta en la cabeza en este momento (se cambia puesta). */
    public static final EjeDeCorte<Boolean> CAPUCHA_ARRIBA = EjeDeCorte.bandera("capucha_arriba");
    public static final EjeDeCorte<SombreroEstilo> CAPUCHA_ESTILO = EjeDeCorte.enumerado("capucha_estilo", SombreroEstilo::values)
            .fabrica(SombreroEstilo.MALLA).en(REMERA, CAPA);
    public static final EjeDeCorte<Integer> FALDON = EjeDeCorte.nivel("faldon").fabrica(EjeDeCorte.SIN_NIVEL).en(REMERA);

    // --- Pantalón (la pierna izquierda vale para las dos hasta que la derecha tenga la suya) ---
    public static final EjeDeCorte<Botamanga> PANTALON_SUPERIOR_DER = EjeDeCorte.enumerado("pantalon_superior_der", Botamanga::values);
    public static final EjeDeCorte<Botamanga> PANTALON_SUPERIOR = EjeDeCorte.enumerado("pantalon_superior", Botamanga::values)
            .fabrica(Botamanga.PIE).en(PANTALON).conOtroLado(PANTALON_SUPERIOR_DER, false);
    public static final EjeDeCorte<Botamanga> PANTALON_INFERIOR_DER = EjeDeCorte.enumerado("pantalon_inferior_der", Botamanga::values);
    public static final EjeDeCorte<Botamanga> PANTALON_INFERIOR = EjeDeCorte.enumerado("pantalon_inferior", Botamanga::values)
            .fabrica(Botamanga.PIE).en(PANTALON).conOtroLado(PANTALON_INFERIOR_DER, false);
    public static final EjeDeCorte<PantalonTiro> TIRO = EjeDeCorte.enumerado("tiro", PantalonTiro::values)
            .fabrica(PantalonTiro.MEDIO).en(PANTALON);

    // --- Medias ---
    public static final EjeDeCorte<Botamanga> MEDIAS_SUPERIOR_DER = EjeDeCorte.enumerado("medias_superior_der", Botamanga::values);
    public static final EjeDeCorte<Botamanga> MEDIAS_SUPERIOR = EjeDeCorte.enumerado("medias_superior", Botamanga::values)
            .fabrica(Botamanga.PIE).en(MEDIAS).conOtroLado(MEDIAS_SUPERIOR_DER, false);
    public static final EjeDeCorte<Botamanga> MEDIAS_INFERIOR_DER = EjeDeCorte.enumerado("medias_inferior_der", Botamanga::values);
    public static final EjeDeCorte<Botamanga> MEDIAS_INFERIOR = EjeDeCorte.enumerado("medias_inferior", Botamanga::values)
            .fabrica(Botamanga.PIE).en(MEDIAS).conOtroLado(MEDIAS_INFERIOR_DER, false);

    // --- Calientabrazos ---
    public static final EjeDeCorte<Variante.Manga> CALIENTABRAZOS_SUPERIOR_DER = EjeDeCorte.enumerado("calientabrazos_superior_der", Variante.Manga::values);
    public static final EjeDeCorte<Variante.Manga> CALIENTABRAZOS_SUPERIOR = EjeDeCorte.enumerado("calientabrazos_superior", Variante.Manga::values)
            .fabrica(Variante.Manga.LARGA).en(CALIENTABRAZOS).conOtroLado(CALIENTABRAZOS_SUPERIOR_DER, false);
    public static final EjeDeCorte<Variante.Manga> CALIENTABRAZOS_INFERIOR_DER = EjeDeCorte.enumerado("calientabrazos_inferior_der", Variante.Manga::values);
    public static final EjeDeCorte<Variante.Manga> CALIENTABRAZOS_INFERIOR = EjeDeCorte.enumerado("calientabrazos_inferior", Variante.Manga::values)
            .fabrica(Variante.Manga.LARGA).en(CALIENTABRAZOS).conOtroLado(CALIENTABRAZOS_INFERIOR_DER, false);

    // --- Pollera ---
    public static final EjeDeCorte<PolleraLargo> POLLERA_LARGO = EjeDeCorte.enumerado("pollera_largo", PolleraLargo::values)
            .fabrica(PolleraLargo.MEDIO).en(POLLERA);
    public static final EjeDeCorte<Integer> POLLERA_CINTURA = EjeDeCorte.nivel("pollera_cintura").fabrica(EjeDeCorte.SIN_NIVEL).en(POLLERA);
    public static final EjeDeCorte<PolleraForma> POLLERA_FORMA = EjeDeCorte.enumerado("pollera_forma", PolleraForma::values)
            .fabrica(PolleraForma.CAMPANA).en(POLLERA);
    public static final EjeDeCorte<PolleraVolado> POLLERA_VOLADO_RUEDO = EjeDeCorte.enumerado("pollera_volado_ruedo", PolleraVolado::values)
            .fabrica(PolleraVolado.SIN).en(POLLERA);
    public static final EjeDeCorte<PolleraVolado> POLLERA_VOLADO_TODO = EjeDeCorte.enumerado("pollera_volado_todo", PolleraVolado::values)
            .fabrica(PolleraVolado.SIN).en(POLLERA);

    // --- Capa ---
    public static final EjeDeCorte<CapaLargo> CAPA_LARGO = EjeDeCorte.enumerado("capa_largo", CapaLargo::values)
            .fabrica(CapaLargo.MEDIO).en(CAPA);
    public static final EjeDeCorte<CapaRuedo> CAPA_RUEDO = EjeDeCorte.enumerado("capa_ruedo", CapaRuedo::values)
            .fabrica(CapaRuedo.RECTO).en(CAPA);
    public static final EjeDeCorte<Boolean> CAPA_CAPUCHA = EjeDeCorte.bandera("capa_capucha").fabrica(false).en(CAPA);
    public static final EjeDeCorte<Boolean> CAPA_CUELLO = EjeDeCorte.bandera("capa_cuello").fabrica(false).en(CAPA);
    /** Sin embozo de fábrica ({@link EjeDeCorte#SIN_NIVEL}); cambiarlo vuelve a cerrar el frente. */
    public static final EjeDeCorte<Integer> CAPA_EMBOZO = EjeDeCorte.nivel("capa_embozo").fabrica(EjeDeCorte.SIN_NIVEL).en(CAPA)
            .reinicia(FRENTE_AHORA);
    public static final EjeDeCorte<Boolean> CAPA_BORDE_EMBOZO = EjeDeCorte.bandera("capa_borde_embozo").fabrica(false).en(CAPA);

    // --- Delantal ---
    public static final EjeDeCorte<Integer> DELANTAL_INICIO = EjeDeCorte.nivel("delantal_inicio")
            .fabrica(DelantalItem.INICIO_DE_FABRICA).en(DELANTAL);
    public static final EjeDeCorte<Integer> DELANTAL_LARGO = EjeDeCorte.nivel("delantal_largo")
            .fabrica(DelantalItem.LARGO_DE_FABRICA).en(DELANTAL);
    public static final EjeDeCorte<DelantalForma> DELANTAL_FORMA = EjeDeCorte.enumerado("delantal_forma", DelantalForma::values)
            .fabrica(DelantalForma.RECTO).en(DELANTAL);
    public static final EjeDeCorte<CapaRuedo> DELANTAL_ESQUINAS = EjeDeCorte.enumerado("delantal_esquinas", CapaRuedo::values)
            .fabrica(CapaRuedo.RECTO).en(DELANTAL);
    public static final EjeDeCorte<DelantalCobertura> DELANTAL_COBERTURA = EjeDeCorte.enumerado("delantal_cobertura", DelantalCobertura::values)
            .fabrica(DelantalCobertura.ENVOLVENTE).en(DELANTAL);
    public static final EjeDeCorte<DelantalTirantes> DELANTAL_TIRANTES = EjeDeCorte.enumerado("delantal_tirantes", DelantalTirantes::values)
            .fabrica(DelantalTirantes.SIN).en(DELANTAL);
    public static final EjeDeCorte<PolleraVolado> DELANTAL_VOLADO = EjeDeCorte.enumerado("delantal_volado", PolleraVolado::values)
            .fabrica(PolleraVolado.SIN).en(DELANTAL);
    public static final EjeDeCorte<PolleraVolado> DELANTAL_VOLADO_LATERAL = EjeDeCorte.enumerado("delantal_volado_lateral", PolleraVolado::values)
            .fabrica(PolleraVolado.SIN).en(DELANTAL);
    public static final EjeDeCorte<PolleraVolado> DELANTAL_VOLADO_SUPERIOR = EjeDeCorte.enumerado("delantal_volado_superior", PolleraVolado::values)
            .fabrica(PolleraVolado.SIN).en(DELANTAL);

    // --- Sombrero ---
    public static final EjeDeCorte<SombreroAla> SOMBRERO_ALA = EjeDeCorte.enumerado("sombrero_ala", SombreroAla::values)
            .fabrica(SombreroAla.ANCHA).en(SOMBRERO);
    public static final EjeDeCorte<SombreroPunta> SOMBRERO_PUNTA = EjeDeCorte.enumerado("sombrero_punta", SombreroPunta::values)
            .fabrica(SombreroPunta.RECTA).en(SOMBRERO);
    public static final EjeDeCorte<SombreroPlanta> SOMBRERO_PLANTA = EjeDeCorte.enumerado("sombrero_planta", SombreroPlanta::values)
            .fabrica(SombreroPlanta.REDONDA).en(SOMBRERO);
    public static final EjeDeCorte<SombreroEstilo> SOMBRERO_ESTILO = EjeDeCorte.enumerado("sombrero_estilo", SombreroEstilo::values)
            .fabrica(SombreroEstilo.MALLA).en(SOMBRERO);
    public static final EjeDeCorte<SombreroGrosor> SOMBRERO_GROSOR = EjeDeCorte.enumerado("sombrero_grosor", SombreroGrosor::values)
            .fabrica(SombreroGrosor.FINA).en(SOMBRERO);
    public static final EjeDeCorte<Boolean> SOMBRERO_BANDA = EjeDeCorte.bandera("sombrero_banda").fabrica(true).en(SOMBRERO);

    // --- Banda (choker, cinto) ---
    public static final EjeDeCorte<BandaZona> BANDA_ZONA = EjeDeCorte.enumerado("banda_zona", BandaZona::values)
            .fabrica(BandaZona.CINTURA).en(BANDA);
    public static final EjeDeCorte<BandaAncho> BANDA_ANCHO = EjeDeCorte.enumerado("banda_ancho", BandaAncho::values)
            .fabrica(BandaAncho.MEDIO).en(BANDA);
    public static final EjeDeCorte<BandaHerraje> BANDA_HERRAJE = EjeDeCorte.enumerado("banda_herraje", BandaHerraje::values)
            .fabrica(BandaHerraje.NINGUNO).en(BANDA);

    // --- Calzado (cada eje del pie izquierdo hereda el del derecho si no está) ---
    public static final EjeDeCorte<SombreroEstilo> BORCEGO_ESTILO = EjeDeCorte.enumerado("borcego_estilo", SombreroEstilo::values)
            .fabrica(SombreroEstilo.MALLA).en(CALZADO);
    public static final EjeDeCorte<BorcegoCana> BORCEGO_CANA_IZQ = EjeDeCorte.enumerado("borcego_cana_izq", BorcegoCana::values);
    public static final EjeDeCorte<BorcegoCana> BORCEGO_CANA = EjeDeCorte.enumerado("borcego_cana", BorcegoCana::values)
            .fabrica(BorcegoCana.MEDIA).en(CALZADO).conOtroLado(BORCEGO_CANA_IZQ, true);
    public static final EjeDeCorte<BorcegoSuela> BORCEGO_SUELA_IZQ = EjeDeCorte.enumerado("borcego_suela_izq", BorcegoSuela::values);
    public static final EjeDeCorte<BorcegoSuela> BORCEGO_SUELA = EjeDeCorte.enumerado("borcego_suela", BorcegoSuela::values)
            .fabrica(BorcegoSuela.CHATA).en(CALZADO).conOtroLado(BORCEGO_SUELA_IZQ, true);
    public static final EjeDeCorte<BorcegoBotamanga> BORCEGO_BOTAMANGA_IZQ = EjeDeCorte.enumerado("borcego_botamanga_izq", BorcegoBotamanga::values);
    public static final EjeDeCorte<BorcegoBotamanga> BORCEGO_BOTAMANGA = EjeDeCorte.enumerado("borcego_botamanga", BorcegoBotamanga::values)
            .fabrica(BorcegoBotamanga.ADENTRO).en(CALZADO).conOtroLado(BORCEGO_BOTAMANGA_IZQ, true);
    public static final EjeDeCorte<CalzadoPunta> BORCEGO_PUNTA_IZQ = EjeDeCorte.enumerado("borcego_punta_izq", CalzadoPunta::values);
    public static final EjeDeCorte<CalzadoPunta> BORCEGO_PUNTA = EjeDeCorte.enumerado("borcego_punta", CalzadoPunta::values)
            .fabrica(CalzadoPunta.CUERO).en(CALZADO).conOtroLado(BORCEGO_PUNTA_IZQ, true);
    public static final EjeDeCorte<CalzadoCierre> BORCEGO_CIERRE_IZQ = EjeDeCorte.enumerado("borcego_cierre_izq", CalzadoCierre::values);
    public static final EjeDeCorte<CalzadoCierre> BORCEGO_CIERRE = EjeDeCorte.enumerado("borcego_cierre", CalzadoCierre::values)
            .fabrica(CalzadoCierre.CORDONES).en(CALZADO).conOtroLado(BORCEGO_CIERRE_IZQ, true);
    public static final EjeDeCorte<CalzadoDetalle> BORCEGO_DETALLE_IZQ = EjeDeCorte.enumerado("borcego_detalle_izq", CalzadoDetalle::values);
    public static final EjeDeCorte<CalzadoDetalle> BORCEGO_DETALLE = EjeDeCorte.enumerado("borcego_detalle", CalzadoDetalle::values)
            .fabrica(CalzadoDetalle.NINGUNO).en(CALZADO).conOtroLado(BORCEGO_DETALLE_IZQ, true);

    /** Fuerza la carga de todos los ejes (los registra por nombre antes de leer cualquier guardado). */
    public static void init() {}
}
