package com.modamod.prenda;

import com.modamod.item.*;
import com.modamod.region.Orientacion;
import com.modamod.sublimadora.Variante;

import java.util.Map;

/**
 * Todos los ejes de corte del mod, agrupados por prenda. Es la lista de qué forma puede tener cada prenda; se guardan
 * todos juntos en {@link Corte}. Los nombres son los que se escriben en el guardado: no se cambian.
 */
public final class Ejes {
    private Ejes() {}

    // --- Comunes a varias prendas ---
    public static final EjeDeCorte<Calce> CALCE = EjeDeCorte.enumerado("calce", Calce::values);
    public static final EjeDeCorte<PatronRed> RED = EjeDeCorte.enumerado("red", PatronRed::values);
    public static final EjeDeCorte<Map<ZonaRuedo, Ruedo>> RUEDOS = EjeDeCorte.de("ruedos", Ruedos.CODEC);
    /** Cómo se lleva puesta (al derecho o dada vuelta). */
    public static final EjeDeCorte<Orientacion> ORIENTACION = EjeDeCorte.de("orientacion", Orientacion.CODEC);

    // --- Remera y top ---
    public static final EjeDeCorte<Variante> VARIANTE = EjeDeCorte.de("variante", Variante.CODEC);
    public static final EjeDeCorte<Variante.Manga> MANGA_DERECHA = EjeDeCorte.enumerado("manga_derecha", Variante.Manga::values);
    public static final EjeDeCorte<ChaquetaFrente> FRENTE = EjeDeCorte.enumerado("frente", ChaquetaFrente::values);
    /** El frente abierto o cerrado en este momento (se cambia puesta). */
    public static final EjeDeCorte<Boolean> FRENTE_AHORA = EjeDeCorte.bandera("frente_ahora");
    public static final EjeDeCorte<ChaquetaSolapa> SOLAPA = EjeDeCorte.enumerado("solapa", ChaquetaSolapa::values);
    public static final EjeDeCorte<Boolean> CON_CAPUCHA = EjeDeCorte.bandera("con_capucha");
    /** La capucha puesta en la cabeza en este momento (se cambia puesta). */
    public static final EjeDeCorte<Boolean> CAPUCHA_ARRIBA = EjeDeCorte.bandera("capucha_arriba");
    public static final EjeDeCorte<SombreroEstilo> CAPUCHA_ESTILO = EjeDeCorte.enumerado("capucha_estilo", SombreroEstilo::values);
    public static final EjeDeCorte<Integer> FALDON = EjeDeCorte.nivel("faldon");

    // --- Pantalón ---
    public static final EjeDeCorte<Botamanga> PANTALON_SUPERIOR = EjeDeCorte.enumerado("pantalon_superior", Botamanga::values);
    public static final EjeDeCorte<Botamanga> PANTALON_SUPERIOR_DER = EjeDeCorte.enumerado("pantalon_superior_der", Botamanga::values);
    public static final EjeDeCorte<Botamanga> PANTALON_INFERIOR = EjeDeCorte.enumerado("pantalon_inferior", Botamanga::values);
    public static final EjeDeCorte<Botamanga> PANTALON_INFERIOR_DER = EjeDeCorte.enumerado("pantalon_inferior_der", Botamanga::values);
    public static final EjeDeCorte<PantalonTiro> TIRO = EjeDeCorte.enumerado("tiro", PantalonTiro::values);

    // --- Medias ---
    public static final EjeDeCorte<Botamanga> MEDIAS_SUPERIOR = EjeDeCorte.enumerado("medias_superior", Botamanga::values);
    public static final EjeDeCorte<Botamanga> MEDIAS_SUPERIOR_DER = EjeDeCorte.enumerado("medias_superior_der", Botamanga::values);
    public static final EjeDeCorte<Botamanga> MEDIAS_INFERIOR = EjeDeCorte.enumerado("medias_inferior", Botamanga::values);
    public static final EjeDeCorte<Botamanga> MEDIAS_INFERIOR_DER = EjeDeCorte.enumerado("medias_inferior_der", Botamanga::values);

    // --- Calientabrazos ---
    public static final EjeDeCorte<Variante.Manga> CALIENTABRAZOS_SUPERIOR = EjeDeCorte.enumerado("calientabrazos_superior", Variante.Manga::values);
    public static final EjeDeCorte<Variante.Manga> CALIENTABRAZOS_SUPERIOR_DER = EjeDeCorte.enumerado("calientabrazos_superior_der", Variante.Manga::values);
    public static final EjeDeCorte<Variante.Manga> CALIENTABRAZOS_INFERIOR = EjeDeCorte.enumerado("calientabrazos_inferior", Variante.Manga::values);
    public static final EjeDeCorte<Variante.Manga> CALIENTABRAZOS_INFERIOR_DER = EjeDeCorte.enumerado("calientabrazos_inferior_der", Variante.Manga::values);

    // --- Pollera ---
    public static final EjeDeCorte<PolleraLargo> POLLERA_LARGO = EjeDeCorte.enumerado("pollera_largo", PolleraLargo::values);
    public static final EjeDeCorte<Integer> POLLERA_CINTURA = EjeDeCorte.nivel("pollera_cintura");
    public static final EjeDeCorte<PolleraForma> POLLERA_FORMA = EjeDeCorte.enumerado("pollera_forma", PolleraForma::values);
    public static final EjeDeCorte<PolleraVolado> POLLERA_VOLADO_RUEDO = EjeDeCorte.enumerado("pollera_volado_ruedo", PolleraVolado::values);
    public static final EjeDeCorte<PolleraVolado> POLLERA_VOLADO_TODO = EjeDeCorte.enumerado("pollera_volado_todo", PolleraVolado::values);

    // --- Capa ---
    public static final EjeDeCorte<CapaLargo> CAPA_LARGO = EjeDeCorte.enumerado("capa_largo", CapaLargo::values);
    public static final EjeDeCorte<CapaRuedo> CAPA_RUEDO = EjeDeCorte.enumerado("capa_ruedo", CapaRuedo::values);
    public static final EjeDeCorte<Boolean> CAPA_CAPUCHA = EjeDeCorte.bandera("capa_capucha");
    public static final EjeDeCorte<Boolean> CAPA_CUELLO = EjeDeCorte.bandera("capa_cuello");
    public static final EjeDeCorte<Integer> CAPA_EMBOZO = EjeDeCorte.nivel("capa_embozo");
    public static final EjeDeCorte<Boolean> CAPA_BORDE_EMBOZO = EjeDeCorte.bandera("capa_borde_embozo");

    // --- Delantal ---
    public static final EjeDeCorte<Integer> DELANTAL_INICIO = EjeDeCorte.nivel("delantal_inicio");
    public static final EjeDeCorte<Integer> DELANTAL_LARGO = EjeDeCorte.nivel("delantal_largo");
    public static final EjeDeCorte<DelantalForma> DELANTAL_FORMA = EjeDeCorte.enumerado("delantal_forma", DelantalForma::values);
    public static final EjeDeCorte<CapaRuedo> DELANTAL_ESQUINAS = EjeDeCorte.enumerado("delantal_esquinas", CapaRuedo::values);
    public static final EjeDeCorte<DelantalCobertura> DELANTAL_COBERTURA = EjeDeCorte.enumerado("delantal_cobertura", DelantalCobertura::values);
    public static final EjeDeCorte<DelantalTirantes> DELANTAL_TIRANTES = EjeDeCorte.enumerado("delantal_tirantes", DelantalTirantes::values);
    public static final EjeDeCorte<PolleraVolado> DELANTAL_VOLADO = EjeDeCorte.enumerado("delantal_volado", PolleraVolado::values);
    public static final EjeDeCorte<PolleraVolado> DELANTAL_VOLADO_LATERAL = EjeDeCorte.enumerado("delantal_volado_lateral", PolleraVolado::values);
    public static final EjeDeCorte<PolleraVolado> DELANTAL_VOLADO_SUPERIOR = EjeDeCorte.enumerado("delantal_volado_superior", PolleraVolado::values);

    // --- Sombrero ---
    public static final EjeDeCorte<SombreroAla> SOMBRERO_ALA = EjeDeCorte.enumerado("sombrero_ala", SombreroAla::values);
    public static final EjeDeCorte<SombreroPunta> SOMBRERO_PUNTA = EjeDeCorte.enumerado("sombrero_punta", SombreroPunta::values);
    public static final EjeDeCorte<SombreroPlanta> SOMBRERO_PLANTA = EjeDeCorte.enumerado("sombrero_planta", SombreroPlanta::values);
    public static final EjeDeCorte<SombreroEstilo> SOMBRERO_ESTILO = EjeDeCorte.enumerado("sombrero_estilo", SombreroEstilo::values);
    public static final EjeDeCorte<SombreroGrosor> SOMBRERO_GROSOR = EjeDeCorte.enumerado("sombrero_grosor", SombreroGrosor::values);
    public static final EjeDeCorte<Boolean> SOMBRERO_BANDA = EjeDeCorte.bandera("sombrero_banda");

    // --- Banda (choker, cinto) ---
    public static final EjeDeCorte<BandaZona> BANDA_ZONA = EjeDeCorte.enumerado("banda_zona", BandaZona::values);
    public static final EjeDeCorte<BandaAncho> BANDA_ANCHO = EjeDeCorte.enumerado("banda_ancho", BandaAncho::values);
    public static final EjeDeCorte<BandaHerraje> BANDA_HERRAJE = EjeDeCorte.enumerado("banda_herraje", BandaHerraje::values);

    // --- Borcegos (cada eje del pie izquierdo hereda el del derecho si no está) ---
    public static final EjeDeCorte<SombreroEstilo> BORCEGO_ESTILO = EjeDeCorte.enumerado("borcego_estilo", SombreroEstilo::values);
    public static final EjeDeCorte<BorcegoCana> BORCEGO_CANA = EjeDeCorte.enumerado("borcego_cana", BorcegoCana::values);
    public static final EjeDeCorte<BorcegoCana> BORCEGO_CANA_IZQ = EjeDeCorte.enumerado("borcego_cana_izq", BorcegoCana::values);
    public static final EjeDeCorte<BorcegoSuela> BORCEGO_SUELA = EjeDeCorte.enumerado("borcego_suela", BorcegoSuela::values);
    public static final EjeDeCorte<BorcegoSuela> BORCEGO_SUELA_IZQ = EjeDeCorte.enumerado("borcego_suela_izq", BorcegoSuela::values);
    public static final EjeDeCorte<BorcegoBotamanga> BORCEGO_BOTAMANGA = EjeDeCorte.enumerado("borcego_botamanga", BorcegoBotamanga::values);
    public static final EjeDeCorte<BorcegoBotamanga> BORCEGO_BOTAMANGA_IZQ = EjeDeCorte.enumerado("borcego_botamanga_izq", BorcegoBotamanga::values);
    public static final EjeDeCorte<CalzadoPunta> BORCEGO_PUNTA = EjeDeCorte.enumerado("borcego_punta", CalzadoPunta::values);
    public static final EjeDeCorte<CalzadoPunta> BORCEGO_PUNTA_IZQ = EjeDeCorte.enumerado("borcego_punta_izq", CalzadoPunta::values);
    public static final EjeDeCorte<CalzadoCierre> BORCEGO_CIERRE = EjeDeCorte.enumerado("borcego_cierre", CalzadoCierre::values);
    public static final EjeDeCorte<CalzadoCierre> BORCEGO_CIERRE_IZQ = EjeDeCorte.enumerado("borcego_cierre_izq", CalzadoCierre::values);
    public static final EjeDeCorte<CalzadoDetalle> BORCEGO_DETALLE = EjeDeCorte.enumerado("borcego_detalle", CalzadoDetalle::values);
    public static final EjeDeCorte<CalzadoDetalle> BORCEGO_DETALLE_IZQ = EjeDeCorte.enumerado("borcego_detalle_izq", CalzadoDetalle::values);

    /** Fuerza la carga de todos los ejes (los registra por nombre antes de leer cualquier guardado). */
    public static void init() {}
}
