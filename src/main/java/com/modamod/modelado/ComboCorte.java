package com.modamod.modelado;

import com.modamod.item.Botamanga;
import com.modamod.item.Calce;
import com.modamod.item.PantalonTiro;
import com.modamod.item.PatronRed;
import com.modamod.region.Lado;
import com.modamod.sublimadora.Variante;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;

import java.util.Optional;

/**
 * Varios ejes de forma juntos (una fijada o un diseño de corte de la Mesa de
 * Modelado), uno por familia de prenda. Cada campo es opcional —
 * un molde de corte no tiene por qué tocar todos a la vez, y aplicar sobre
 * una prenda solo escribe los campos presentes Y relevantes a esa prenda
 * (ver {@link PrendaModelado#aplicar}).
 *
 * <h2>Cobertura de torso vs. extremidad</h2>
 * {@code remeraLargo}/{@code remeraManga}/{@code remeraCuello} y
 * {@code pantalonTiro} son ejes de TORSO — un solo valor cada uno, sin
 * anclaje doble ni lado. Los tres ejes de EXTREMIDAD (pantalón-pierna,
 * medias, calientabrazos) en cambio son dos anclajes que se intersecan
 * (superior/inferior — ver {@code PantalonItem#filasVisibles} y análogos) Y
 * pueden ser asimétricos — de ahí {@link #lado}, que dice a qué pierna/brazo
 * aplica el anclaje que esta entrada puebla. Una entrada puebla UN anclajes
 * de UN eje; fijar superior y después inferior son dos entradas separadas
 * que se acumulan sobre la misma prenda (ver {@link PrendaModelado}).
 *
 * <p><b>2026-09-15</b>: {@code remeraLargo/manga/cuello} eran ANTES un solo
 * campo {@code remeraVariante} (el {@link Variante} entero de una) — bug
 * real encontrado jugando: como cada fijada de remera reemplazaba el
 * Variante COMPLETO, fijar el largo DESPUÉS de fijar la manga pisaba la
 * manga de vuelta a un default congelado (y viceversa) — dos fijadas de
 * remera competían en vez de acumularse, a diferencia de cómo ya
 * funcionaba pantalón/medias. Separarlos en 3 campos independientes,
 * aplicados por {@link PrendaModelado#aplicar} sobre el Variante ACTUAL de
 * la prenda (no un borrador congelado), arregla esto con el mismo patrón
 * que ya usan los demás ejes.
 *
 * <p><b>2026-09-16</b>: {@link #iconoOrigen} — a pedido, la GUI de la Mesa
 * (ver {@code ModeladoScreen}) quiere mostrar el ícono del molde que
 * produjo cada fijada en vez de un código de texto. El VALOR guardado
 * (ej. {@code Botamanga.RODILLA}) no alcanza para eso: el Molde de
 * Rango tiene menos escalones que {@link Botamanga}, así que la
 * traducción valor→ítem no es 1 a 1 ni reversible. Se guarda el
 * {@link Identifier} del ítem tal como estaba en el slot Activo al fijar
 * (ver {@code ModeladoBlockEntity#fijar}) — no participa de
 * {@link #estaVacio()} (es metadata de UI, no un eje real).
 *
 * <p><b>2026-09-20</b>: {@link #red} — molde de red/fishnet, transversal a
 * las 4 categorías igual que {@link #calce}, sin anclaje ni lado. Ver
 * {@link PatronRed}.
 *
 * <p><b>2026-09-24</b>: {@link #capaPatron} — pines "Materiales" del
 * esquema visual de remera (3 pines = 3 índices de capa, ver
 * {@code ModeladoBlockEntity#pinearMaterial}). Reusa el mismo sistema de
 * capas apiladas que ya escribe Tinturas ({@code RegionResolver.CapaPatron}/
 * {@code ponerPatron}, tope real {@code TinturasBlockEntity#CAPAS_MAXIMO}=3)
 * — acá solo se guarda el {@code patronId} del molde soltado en ese índice;
 * color/tamaño/ángulo/posición salen con sus defaults al aplicar (ver
 * {@link PrendaModelado#aplicar}), no hay edición fina desde la Modeladora
 * todavía (eso sigue siendo cosa de Tinturas).
 */
public record ComboCorte(
        Optional<Variante.Largo> remeraLargo,
        Optional<Variante.Manga> remeraManga,
        Optional<Variante.Cuello> remeraCuello,
        Optional<PantalonTiro> pantalonTiro,
        Optional<Botamanga> pantalonLargoSuperior,
        Optional<Botamanga> pantalonLargoInferior,
        Optional<Botamanga> mediasLargoSuperior,
        Optional<Botamanga> mediasLargoInferior,
        Optional<Variante.Manga> calientabrazosCoberturaSuperior,
        Optional<Variante.Manga> calientabrazosCoberturaInferior,
        Optional<Calce> calce,
        Optional<PatronRed> red,
        Optional<CapaIndexada> capaPatron,
        Lado lado,
        Optional<Identifier> iconoOrigen,
        Optional<PolleraCorte> pollera
) {

    /**
     * Ejes de la pollera (2026-09-29, "quiero poder hacerle distintos
     * largos" + forma campana/tableada): van juntos en un solo campo porque
     * el codec de registros admite hasta 16.
     */
    public record PolleraCorte(Optional<com.modamod.item.PolleraLargo> largo,
                               Optional<com.modamod.item.PolleraForma> forma,
                               // La capa (2026-09-29) viaja en el mismo campo: el
                               // codec de ComboCorte ya está en su tope de 16.
                               Optional<com.modamod.item.CapaLargo> capaLargo,
                               Optional<com.modamod.item.CapaRuedo> capaRuedo,
                               Optional<Boolean> capaCapucha,
                               Optional<Boolean> capaCuello,
                               // El sombrero de bruja (2026-10-05) también: el codec ya está en su tope de 16.
                               Optional<com.modamod.item.SombreroAla> sombreroAla,
                               Optional<com.modamod.item.SombreroPunta> sombreroPunta,
                               // La banda (cinto y choker, 2026-10-05) también.
                               Optional<com.modamod.item.BandaZona> bandaZona,
                               Optional<com.modamod.item.BandaAncho> bandaAncho,
                               Optional<com.modamod.item.BandaHerraje> bandaHerraje,
                               // Los volados de la pollera (2026-10-05): del borde de abajo y de toda la pollera.
                               Optional<com.modamod.item.PolleraVolado> voladoRuedo,
                               Optional<com.modamod.item.PolleraVolado> voladoTodo,
                               // El ruedo de un borde libre (2026-10-07, fusiona el borde de la pollera y el remate de la chaqueta).
                               Optional<RuedoCorte> ruedo,
                               // La chaqueta (2026-10-07): frente, capucha y solapa en un solo campo (tope de 16 campos).
                               Optional<ChaquetaCorte> chaqueta,
                               // La cintura de la pollera (2026-10-08): nivel de rango 0..6 — el 16.º y último campo.
                               Optional<Integer> cintura) {

        /** Los 15 campos de antes de la cintura. */
        public PolleraCorte(Optional<com.modamod.item.PolleraLargo> largo,
                            Optional<com.modamod.item.PolleraForma> forma,
                            Optional<com.modamod.item.CapaLargo> capaLargo,
                            Optional<com.modamod.item.CapaRuedo> capaRuedo,
                            Optional<Boolean> capaCapucha,
                            Optional<Boolean> capaCuello,
                            Optional<com.modamod.item.SombreroAla> sombreroAla,
                            Optional<com.modamod.item.SombreroPunta> sombreroPunta,
                            Optional<com.modamod.item.BandaZona> bandaZona,
                            Optional<com.modamod.item.BandaAncho> bandaAncho,
                            Optional<com.modamod.item.BandaHerraje> bandaHerraje,
                            Optional<com.modamod.item.PolleraVolado> voladoRuedo,
                            Optional<com.modamod.item.PolleraVolado> voladoTodo,
                            Optional<RuedoCorte> ruedo,
                            Optional<ChaquetaCorte> chaqueta) {
            this(largo, forma, capaLargo, capaRuedo, capaCapucha, capaCuello, sombreroAla, sombreroPunta, bandaZona,
                    bandaAncho, bandaHerraje, voladoRuedo, voladoTodo, ruedo, chaqueta, Optional.empty());
        }

        /** Los 14 campos de antes de la chaqueta. */
        public PolleraCorte(Optional<com.modamod.item.PolleraLargo> largo,
                            Optional<com.modamod.item.PolleraForma> forma,
                            Optional<com.modamod.item.CapaLargo> capaLargo,
                            Optional<com.modamod.item.CapaRuedo> capaRuedo,
                            Optional<Boolean> capaCapucha,
                            Optional<Boolean> capaCuello,
                            Optional<com.modamod.item.SombreroAla> sombreroAla,
                            Optional<com.modamod.item.SombreroPunta> sombreroPunta,
                            Optional<com.modamod.item.BandaZona> bandaZona,
                            Optional<com.modamod.item.BandaAncho> bandaAncho,
                            Optional<com.modamod.item.BandaHerraje> bandaHerraje,
                            Optional<com.modamod.item.PolleraVolado> voladoRuedo,
                            Optional<com.modamod.item.PolleraVolado> voladoTodo,
                            Optional<RuedoCorte> ruedo) {
            this(largo, forma, capaLargo, capaRuedo, capaCapucha, capaCuello, sombreroAla, sombreroPunta, bandaZona,
                    bandaAncho, bandaHerraje, voladoRuedo, voladoTodo, ruedo, Optional.empty());
        }

        /** Los 13 campos de antes del ruedo. */
        public PolleraCorte(Optional<com.modamod.item.PolleraLargo> largo,
                            Optional<com.modamod.item.PolleraForma> forma,
                            Optional<com.modamod.item.CapaLargo> capaLargo,
                            Optional<com.modamod.item.CapaRuedo> capaRuedo,
                            Optional<Boolean> capaCapucha,
                            Optional<Boolean> capaCuello,
                            Optional<com.modamod.item.SombreroAla> sombreroAla,
                            Optional<com.modamod.item.SombreroPunta> sombreroPunta,
                            Optional<com.modamod.item.BandaZona> bandaZona,
                            Optional<com.modamod.item.BandaAncho> bandaAncho,
                            Optional<com.modamod.item.BandaHerraje> bandaHerraje,
                            Optional<com.modamod.item.PolleraVolado> voladoRuedo,
                            Optional<com.modamod.item.PolleraVolado> voladoTodo) {
            this(largo, forma, capaLargo, capaRuedo, capaCapucha, capaCuello, sombreroAla, sombreroPunta, bandaZona,
                    bandaAncho, bandaHerraje, voladoRuedo, voladoTodo, Optional.empty(), Optional.empty());
        }
        public PolleraCorte(Optional<com.modamod.item.PolleraLargo> largo,
                            Optional<com.modamod.item.PolleraForma> forma) {
            this(largo, forma, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty());
        }

        public PolleraCorte(Optional<com.modamod.item.PolleraLargo> largo,
                            Optional<com.modamod.item.PolleraForma> forma,
                            Optional<com.modamod.item.CapaLargo> capaLargo,
                            Optional<com.modamod.item.CapaRuedo> capaRuedo,
                            Optional<Boolean> capaCapucha,
                            Optional<Boolean> capaCuello) {
            this(largo, forma, capaLargo, capaRuedo, capaCapucha, capaCuello, Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }

        public PolleraCorte(Optional<com.modamod.item.PolleraLargo> largo,
                            Optional<com.modamod.item.PolleraForma> forma,
                            Optional<com.modamod.item.CapaLargo> capaLargo,
                            Optional<com.modamod.item.CapaRuedo> capaRuedo,
                            Optional<Boolean> capaCapucha,
                            Optional<Boolean> capaCuello,
                            Optional<com.modamod.item.SombreroAla> sombreroAla,
                            Optional<com.modamod.item.SombreroPunta> sombreroPunta) {
            this(largo, forma, capaLargo, capaRuedo, capaCapucha, capaCuello, sombreroAla, sombreroPunta,
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }

        /** Los 11 campos de antes de los volados (la banda, 2026-10-05). */
        public PolleraCorte(Optional<com.modamod.item.PolleraLargo> largo,
                            Optional<com.modamod.item.PolleraForma> forma,
                            Optional<com.modamod.item.CapaLargo> capaLargo,
                            Optional<com.modamod.item.CapaRuedo> capaRuedo,
                            Optional<Boolean> capaCapucha,
                            Optional<Boolean> capaCuello,
                            Optional<com.modamod.item.SombreroAla> sombreroAla,
                            Optional<com.modamod.item.SombreroPunta> sombreroPunta,
                            Optional<com.modamod.item.BandaZona> bandaZona,
                            Optional<com.modamod.item.BandaAncho> bandaAncho,
                            Optional<com.modamod.item.BandaHerraje> bandaHerraje) {
            this(largo, forma, capaLargo, capaRuedo, capaCapucha, capaCuello, sombreroAla, sombreroPunta,
                    bandaZona, bandaAncho, bandaHerraje, Optional.empty(), Optional.empty());
        }

        boolean vacio() {
            return largo.isEmpty() && forma.isEmpty() && capaLargo.isEmpty() && capaRuedo.isEmpty()
                    && capaCapucha.isEmpty() && capaCuello.isEmpty()
                    && sombreroAla.isEmpty() && sombreroPunta.isEmpty()
                    && bandaZona.isEmpty() && bandaAncho.isEmpty() && bandaHerraje.isEmpty()
                    && voladoRuedo.isEmpty() && voladoTodo.isEmpty() && ruedo.isEmpty() && chaqueta.isEmpty() && cintura.isEmpty();
        }

        static final Codec<PolleraCorte> CODEC = RecordCodecBuilder.create(i -> i.group(
                StringIdentifiable.createCodec(com.modamod.item.PolleraLargo::values).optionalFieldOf("largo")
                        .forGetter(PolleraCorte::largo),
                StringIdentifiable.createCodec(com.modamod.item.PolleraForma::values).optionalFieldOf("forma")
                        .forGetter(PolleraCorte::forma),
                StringIdentifiable.createCodec(com.modamod.item.CapaLargo::values).optionalFieldOf("capa_largo")
                        .forGetter(PolleraCorte::capaLargo),
                StringIdentifiable.createCodec(com.modamod.item.CapaRuedo::values).optionalFieldOf("capa_ruedo")
                        .forGetter(PolleraCorte::capaRuedo),
                Codec.BOOL.optionalFieldOf("capa_capucha").forGetter(PolleraCorte::capaCapucha),
                Codec.BOOL.optionalFieldOf("capa_cuello").forGetter(PolleraCorte::capaCuello),
                StringIdentifiable.createCodec(com.modamod.item.SombreroAla::values).optionalFieldOf("sombrero_ala")
                        .forGetter(PolleraCorte::sombreroAla),
                StringIdentifiable.createCodec(com.modamod.item.SombreroPunta::values).optionalFieldOf("sombrero_punta")
                        .forGetter(PolleraCorte::sombreroPunta),
                StringIdentifiable.createCodec(com.modamod.item.BandaZona::values).optionalFieldOf("banda_zona")
                        .forGetter(PolleraCorte::bandaZona),
                StringIdentifiable.createCodec(com.modamod.item.BandaAncho::values).optionalFieldOf("banda_ancho")
                        .forGetter(PolleraCorte::bandaAncho),
                StringIdentifiable.createCodec(com.modamod.item.BandaHerraje::values).optionalFieldOf("banda_herraje")
                        .forGetter(PolleraCorte::bandaHerraje),
                StringIdentifiable.createCodec(com.modamod.item.PolleraVolado::values).optionalFieldOf("volado_ruedo")
                        .forGetter(PolleraCorte::voladoRuedo),
                StringIdentifiable.createCodec(com.modamod.item.PolleraVolado::values).optionalFieldOf("volado_todo")
                        .forGetter(PolleraCorte::voladoTodo),
                RuedoCorte.CODEC.optionalFieldOf("ruedo").forGetter(PolleraCorte::ruedo),
                ChaquetaCorte.CODEC.optionalFieldOf("chaqueta").forGetter(PolleraCorte::chaqueta),
                Codec.intRange(0, 7).optionalFieldOf("cintura").forGetter(PolleraCorte::cintura)
        ).apply(i, PolleraCorte::new));
    }

    /**
     * Los ejes propios de la chaqueta (2026-10-07): frente, capucha (true = con) y solapa. Desde 2026-10-08 también
     * lleva el corte de los borcegos ({@link BorcegoCorte}): el codec de {@link PolleraCorte} ya tenía sus 16 campos y
     * este sub-registro tenía lugar.
     */
    public record ChaquetaCorte(Optional<com.modamod.item.ChaquetaFrente> frente, Optional<Boolean> capucha,
                                Optional<com.modamod.item.ChaquetaSolapa> solapa, Optional<BorcegoCorte> borcego,
                                Optional<Integer> faldon, Optional<DelantalCorte> delantal,
                                Optional<EmbozoCorte> embozo, Optional<SombreroExtra> sombrero,
                                // Estilo de la capucha (2026-10-23): true = cajas, false = malla.
                                Optional<Boolean> capuchaCajas) {
        /** Sin estilo de capucha (los cortes de antes del 2026-10-23). */
        public ChaquetaCorte(Optional<com.modamod.item.ChaquetaFrente> frente, Optional<Boolean> capucha,
                             Optional<com.modamod.item.ChaquetaSolapa> solapa, Optional<BorcegoCorte> borcego,
                             Optional<Integer> faldon, Optional<DelantalCorte> delantal, Optional<EmbozoCorte> embozo,
                             Optional<SombreroExtra> sombrero) {
            this(frente, capucha, solapa, borcego, faldon, delantal, embozo, sombrero, Optional.empty());
        }

        /** Sin planta (los cortes de antes de las mallas del sombrero, 2026-10-14). */
        public ChaquetaCorte(Optional<com.modamod.item.ChaquetaFrente> frente, Optional<Boolean> capucha,
                             Optional<com.modamod.item.ChaquetaSolapa> solapa, Optional<BorcegoCorte> borcego,
                             Optional<Integer> faldon, Optional<DelantalCorte> delantal, Optional<EmbozoCorte> embozo) {
            this(frente, capucha, solapa, borcego, faldon, delantal, embozo, Optional.empty());
        }

        public ChaquetaCorte(Optional<com.modamod.item.ChaquetaFrente> frente, Optional<Boolean> capucha,
                             Optional<com.modamod.item.ChaquetaSolapa> solapa, Optional<BorcegoCorte> borcego,
                             Optional<Integer> faldon, Optional<DelantalCorte> delantal) {
            this(frente, capucha, solapa, borcego, faldon, delantal, Optional.empty());
        }

        public ChaquetaCorte(Optional<com.modamod.item.ChaquetaFrente> frente, Optional<Boolean> capucha,
                             Optional<com.modamod.item.ChaquetaSolapa> solapa, Optional<BorcegoCorte> borcego,
                             Optional<Integer> faldon) {
            this(frente, capucha, solapa, borcego, faldon, Optional.empty());
        }

        public ChaquetaCorte(Optional<com.modamod.item.ChaquetaFrente> frente, Optional<Boolean> capucha,
                             Optional<com.modamod.item.ChaquetaSolapa> solapa, Optional<BorcegoCorte> borcego) {
            this(frente, capucha, solapa, borcego, Optional.empty(), Optional.empty());
        }

        public ChaquetaCorte(Optional<com.modamod.item.ChaquetaFrente> frente, Optional<Boolean> capucha,
                             Optional<com.modamod.item.ChaquetaSolapa> solapa) {
            this(frente, capucha, solapa, Optional.empty(), Optional.empty(), Optional.empty());
        }

        static final Codec<ChaquetaCorte> CODEC = RecordCodecBuilder.create(i -> i.group(
                StringIdentifiable.createCodec(com.modamod.item.ChaquetaFrente::values).optionalFieldOf("frente")
                        .forGetter(ChaquetaCorte::frente),
                Codec.BOOL.optionalFieldOf("capucha").forGetter(ChaquetaCorte::capucha),
                StringIdentifiable.createCodec(com.modamod.item.ChaquetaSolapa::values).optionalFieldOf("solapa")
                        .forGetter(ChaquetaCorte::solapa),
                BorcegoCorte.CODEC.optionalFieldOf("borcego").forGetter(ChaquetaCorte::borcego),
                Codec.intRange(0, 7).optionalFieldOf("faldon").forGetter(ChaquetaCorte::faldon),
                DelantalCorte.CODEC.optionalFieldOf("delantal").forGetter(ChaquetaCorte::delantal),
                EmbozoCorte.CODEC.optionalFieldOf("embozo").forGetter(ChaquetaCorte::embozo),
                SombreroExtra.CODEC.optionalFieldOf("sombrero").forGetter(ChaquetaCorte::sombrero),
                Codec.BOOL.optionalFieldOf("capucha_cajas").forGetter(ChaquetaCorte::capuchaCajas)
        ).apply(i, ChaquetaCorte::new));
    }

    /**
     * Los ejes del sombrero que no caben en el codec de {@link PolleraCorte} (2026-10-14): planta cuadrada, estilo de
     * cuboides, ala gruesa y banda (true = con, false = sin). Viaja dentro de {@link ChaquetaCorte}.
     */
    public record SombreroExtra(Optional<Boolean> plantaCuadrada, Optional<Boolean> estiloCajas,
                                Optional<Boolean> grosorGrueso, Optional<Boolean> banda) {
        static final Codec<SombreroExtra> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("planta_cuadrada").forGetter(SombreroExtra::plantaCuadrada),
                Codec.BOOL.optionalFieldOf("estilo_cajas").forGetter(SombreroExtra::estiloCajas),
                Codec.BOOL.optionalFieldOf("grosor_grueso").forGetter(SombreroExtra::grosorGrueso),
                Codec.BOOL.optionalFieldOf("banda").forGetter(SombreroExtra::banda)
        ).apply(i, SombreroExtra::new));
    }

    /**
     * El embozo de la capa (2026-10-11): nivel de rango 0..6 (−1 = quitarlo) y borde. Viaja dentro de
     * {@link ChaquetaCorte}: el codec de {@link PolleraCorte} ya tiene sus 16 campos.
     */
    public record EmbozoCorte(Optional<Integer> nivel, Optional<Boolean> borde) {
        static final Codec<EmbozoCorte> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(-1, 6).optionalFieldOf("nivel").forGetter(EmbozoCorte::nivel),
                Codec.BOOL.optionalFieldOf("borde").forGetter(EmbozoCorte::borde)
        ).apply(i, EmbozoCorte::new));
    }

    /**
     * Los ejes del delantal (2026-10-11): de dónde arranca y hasta dónde llega (niveles de rango 0..6), la silueta, las
     * esquinas de abajo (los moldes de ruedo de la capa) y el volado del ruedo (los de la pollera). Viaja dentro de
     * {@link ChaquetaCorte}: el codec de {@link PolleraCorte} ya tiene sus 16 campos.
     */
    public record DelantalCorte(Optional<Integer> inicio, Optional<Integer> largo,
                                Optional<com.modamod.item.DelantalForma> forma,
                                Optional<com.modamod.item.CapaRuedo> esquinas,
                                Optional<com.modamod.item.PolleraVolado> volado,
                                Optional<com.modamod.item.PolleraVolado> lateral,
                                Optional<com.modamod.item.PolleraVolado> superior,
                                Optional<com.modamod.item.DelantalTirantes> tirantes,
                                Optional<com.modamod.item.DelantalCobertura> cobertura) {
        public DelantalCorte(Optional<Integer> inicio, Optional<Integer> largo,
                             Optional<com.modamod.item.DelantalForma> forma,
                             Optional<com.modamod.item.CapaRuedo> esquinas,
                             Optional<com.modamod.item.PolleraVolado> volado,
                             Optional<com.modamod.item.PolleraVolado> lateral,
                             Optional<com.modamod.item.PolleraVolado> superior,
                             Optional<com.modamod.item.DelantalTirantes> tirantes) {
            this(inicio, largo, forma, esquinas, volado, lateral, superior, tirantes, Optional.empty());
        }

        public DelantalCorte(Optional<Integer> inicio, Optional<Integer> largo,
                             Optional<com.modamod.item.DelantalForma> forma,
                             Optional<com.modamod.item.CapaRuedo> esquinas,
                             Optional<com.modamod.item.PolleraVolado> volado) {
            this(inicio, largo, forma, esquinas, volado, Optional.empty(), Optional.empty(), Optional.empty());
        }

        public DelantalCorte(Optional<Integer> inicio, Optional<Integer> largo,
                             Optional<com.modamod.item.DelantalForma> forma,
                             Optional<com.modamod.item.CapaRuedo> esquinas,
                             Optional<com.modamod.item.PolleraVolado> volado,
                             Optional<com.modamod.item.PolleraVolado> lateral,
                             Optional<com.modamod.item.PolleraVolado> superior) {
            this(inicio, largo, forma, esquinas, volado, lateral, superior, Optional.empty());
        }

        static final Codec<DelantalCorte> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 6).optionalFieldOf("inicio").forGetter(DelantalCorte::inicio),
                Codec.intRange(0, 6).optionalFieldOf("largo").forGetter(DelantalCorte::largo),
                StringIdentifiable.createCodec(com.modamod.item.DelantalForma::values).optionalFieldOf("forma")
                        .forGetter(DelantalCorte::forma),
                StringIdentifiable.createCodec(com.modamod.item.CapaRuedo::values).optionalFieldOf("esquinas")
                        .forGetter(DelantalCorte::esquinas),
                StringIdentifiable.createCodec(com.modamod.item.PolleraVolado::values).optionalFieldOf("volado")
                        .forGetter(DelantalCorte::volado),
                StringIdentifiable.createCodec(com.modamod.item.PolleraVolado::values).optionalFieldOf("lateral")
                        .forGetter(DelantalCorte::lateral),
                StringIdentifiable.createCodec(com.modamod.item.PolleraVolado::values).optionalFieldOf("superior")
                        .forGetter(DelantalCorte::superior),
                StringIdentifiable.createCodec(com.modamod.item.DelantalTirantes::values).optionalFieldOf("tirantes")
                        .forGetter(DelantalCorte::tirantes),
                StringIdentifiable.createCodec(com.modamod.item.DelantalCobertura::values).optionalFieldOf("cobertura")
                        .forGetter(DelantalCorte::cobertura)
        ).apply(i, DelantalCorte::new));
    }

    /** Los ejes de los borcegos (2026-10-08): altura de la caña, suela y cómo se lleva la botamanga. */
    public record BorcegoCorte(Optional<com.modamod.item.BorcegoCana> cana, Optional<com.modamod.item.BorcegoSuela> suela,
                               Optional<com.modamod.item.BorcegoBotamanga> botamanga,
                               Optional<com.modamod.item.CalzadoPunta> punta, Optional<com.modamod.item.CalzadoCierre> cierre,
                               Optional<com.modamod.item.CalzadoDetalle> detalle, Optional<Boolean> estiloCajas) {
        /** Sin estilo (los cortes de antes del molde de cuboides del calzado, 2026-10-14). */
        public BorcegoCorte(Optional<com.modamod.item.BorcegoCana> cana, Optional<com.modamod.item.BorcegoSuela> suela,
                            Optional<com.modamod.item.BorcegoBotamanga> botamanga,
                            Optional<com.modamod.item.CalzadoPunta> punta, Optional<com.modamod.item.CalzadoCierre> cierre,
                            Optional<com.modamod.item.CalzadoDetalle> detalle) {
            this(cana, suela, botamanga, punta, cierre, detalle, Optional.empty());
        }

        /** Solo caña, suela y botamanga (los cortes de antes del calzado del zip, 2026-10-14). */
        public BorcegoCorte(Optional<com.modamod.item.BorcegoCana> cana, Optional<com.modamod.item.BorcegoSuela> suela,
                            Optional<com.modamod.item.BorcegoBotamanga> botamanga) {
            this(cana, suela, botamanga, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }

        static final Codec<BorcegoCorte> CODEC = RecordCodecBuilder.create(i -> i.group(
                StringIdentifiable.createCodec(com.modamod.item.BorcegoCana::values).optionalFieldOf("cana")
                        .forGetter(BorcegoCorte::cana),
                StringIdentifiable.createCodec(com.modamod.item.BorcegoSuela::values).optionalFieldOf("suela")
                        .forGetter(BorcegoCorte::suela),
                StringIdentifiable.createCodec(com.modamod.item.BorcegoBotamanga::values).optionalFieldOf("botamanga")
                        .forGetter(BorcegoCorte::botamanga),
                StringIdentifiable.createCodec(com.modamod.item.CalzadoPunta::values).optionalFieldOf("punta")
                        .forGetter(BorcegoCorte::punta),
                StringIdentifiable.createCodec(com.modamod.item.CalzadoCierre::values).optionalFieldOf("cierre")
                        .forGetter(BorcegoCorte::cierre),
                StringIdentifiable.createCodec(com.modamod.item.CalzadoDetalle::values).optionalFieldOf("detalle")
                        .forGetter(BorcegoCorte::detalle),
                Codec.BOOL.optionalFieldOf("estilo_cajas").forGetter(BorcegoCorte::estiloCajas)
        ).apply(i, BorcegoCorte::new));
    }

    /** El ruedo de UN borde libre (2026-10-07): qué zona y qué remate. Cada pin de ruedo carga uno. */
    public record RuedoCorte(com.modamod.item.ZonaRuedo zona, com.modamod.item.Ruedo ruedo, boolean espejo) {
        static final Codec<RuedoCorte> CODEC = RecordCodecBuilder.create(i -> i.group(
                StringIdentifiable.createCodec(com.modamod.item.ZonaRuedo::values).fieldOf("zona").forGetter(RuedoCorte::zona),
                StringIdentifiable.createCodec(com.modamod.item.Ruedo::values).fieldOf("ruedo").forGetter(RuedoCorte::ruedo),
                Codec.BOOL.optionalFieldOf("espejo", false).forGetter(RuedoCorte::espejo)
        ).apply(i, RuedoCorte::new));
    }

    public static ComboCorte chaquetaFrente(com.modamod.item.ChaquetaFrente v) {
        return conChaqueta(new ChaquetaCorte(Optional.of(v), Optional.empty(), Optional.empty()));
    }

    /** Capucha puesta con su estilo (2026-10-23, los moldes Capucha de malla / de cajas): para el top. */
    public static ComboCorte chaquetaCapuchaEstilo(boolean cajas) {
        return conChaqueta(new ChaquetaCorte(Optional.empty(), Optional.of(true), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(cajas)));
    }

    /** Lo mismo para la capa: su capucha y el estilo (que viaja en el corte de chaqueta, el único con lugar). */
    public static ComboCorte capaCapuchaEstilo(boolean cajas) {
        return VACIO.conPollera(new PolleraCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(true), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(new ChaquetaCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(cajas)))));
    }

    public static ComboCorte chaquetaCapucha(boolean v) {
        return conChaqueta(new ChaquetaCorte(Optional.empty(), Optional.of(v), Optional.empty()));
    }

    public static ComboCorte chaquetaSolapa(com.modamod.item.ChaquetaSolapa v) {
        return conChaqueta(new ChaquetaCorte(Optional.empty(), Optional.empty(), Optional.of(v)));
    }

    /** Los borcegos llevan el lado del pie al que van (IZQUIERDA, DERECHA o AMBAS): usan el {@code lado} del combo. */
    public static ComboCorte borcegoCana(com.modamod.item.BorcegoCana v, Lado lado) {
        return conChaqueta(new ChaquetaCorte(Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(new BorcegoCorte(Optional.of(v), Optional.empty(), Optional.empty())))).conLado(lado);
    }

    public static ComboCorte borcegoSuela(com.modamod.item.BorcegoSuela v, Lado lado) {
        return conChaqueta(new ChaquetaCorte(Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(new BorcegoCorte(Optional.empty(), Optional.of(v), Optional.empty())))).conLado(lado);
    }

    private static ComboCorte conBorcego(BorcegoCorte b, Lado lado) {
        return conChaqueta(new ChaquetaCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(b))).conLado(lado);
    }

    /** El estilo del calzado (2026-10-14): true = cuboides, false = malla; los dos pies juntos. */
    public static ComboCorte borcegoEstilo(boolean cajas) {
        return conBorcego(new BorcegoCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of(cajas)), Lado.AMBAS);
    }

    public static ComboCorte borcegoPunta(com.modamod.item.CalzadoPunta v, Lado lado) {
        return conBorcego(new BorcegoCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(v), Optional.empty(), Optional.empty()), lado);
    }

    public static ComboCorte borcegoCierre(com.modamod.item.CalzadoCierre v, Lado lado) {
        return conBorcego(new BorcegoCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(v), Optional.empty()), lado);
    }

    public static ComboCorte borcegoDetalle(com.modamod.item.CalzadoDetalle v, Lado lado) {
        return conBorcego(new BorcegoCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(v)), lado);
    }

    public static ComboCorte borcegoBotamanga(com.modamod.item.BorcegoBotamanga v, Lado lado) {
        return conChaqueta(new ChaquetaCorte(Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(new BorcegoCorte(Optional.empty(), Optional.empty(), Optional.of(v))))).conLado(lado);
    }

    /** El faldón de malla de un Top (2026-10-11): nivel de rango 0..6. */
    public static ComboCorte faldon(int nivel) {
        return conChaqueta(new ChaquetaCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(nivel)));
    }

    private static ComboCorte conDelantal(DelantalCorte d) {
        return conChaqueta(new ChaquetaCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(d)));
    }

    public static ComboCorte delantalTirantes(com.modamod.item.DelantalTirantes v) {
        return conDelantal(new DelantalCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(v)));
    }

    public static ComboCorte capaEmbozo(int nivel) {
        return conChaqueta(new ChaquetaCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of(new EmbozoCorte(Optional.of(nivel), Optional.empty()))));
    }

    public static ComboCorte capaBordeEmbozo(boolean v) {
        return conChaqueta(new ChaquetaCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of(new EmbozoCorte(Optional.empty(), Optional.of(v)))));
    }

    public static ComboCorte delantalCobertura(com.modamod.item.DelantalCobertura v) {
        return conDelantal(new DelantalCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(v)));
    }

    public static ComboCorte delantalInicio(int nivel) {
        return conDelantal(new DelantalCorte(Optional.of(nivel), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
    }

    public static ComboCorte delantalLargo(int nivel) {
        return conDelantal(new DelantalCorte(Optional.empty(), Optional.of(nivel), Optional.empty(), Optional.empty(), Optional.empty()));
    }

    public static ComboCorte delantalForma(com.modamod.item.DelantalForma v) {
        return conDelantal(new DelantalCorte(Optional.empty(), Optional.empty(), Optional.of(v), Optional.empty(), Optional.empty()));
    }

    public static ComboCorte delantalEsquinas(com.modamod.item.CapaRuedo v) {
        return conDelantal(new DelantalCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(v), Optional.empty()));
    }

    public static ComboCorte delantalLateral(com.modamod.item.PolleraVolado v) {
        return conDelantal(new DelantalCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(v), Optional.empty()));
    }

    public static ComboCorte delantalSuperior(com.modamod.item.PolleraVolado v) {
        return conDelantal(new DelantalCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of(v)));
    }

    public static ComboCorte delantalVolado(com.modamod.item.PolleraVolado v) {
        return conDelantal(new DelantalCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(v)));
    }

    public ComboCorte conLado(Lado nuevo) {
        return new ComboCorte(remeraLargo, remeraManga, remeraCuello, pantalonTiro,
                pantalonLargoSuperior, pantalonLargoInferior, mediasLargoSuperior, mediasLargoInferior,
                calientabrazosCoberturaSuperior, calientabrazosCoberturaInferior, calce, red,
                capaPatron, nuevo, iconoOrigen, pollera);
    }

    private static ComboCorte conChaqueta(ChaquetaCorte c) {
        return VACIO.conPollera(new PolleraCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(c)));
    }

    public static ComboCorte polleraLargo(com.modamod.item.PolleraLargo v) {
        return VACIO.conPollera(new PolleraCorte(Optional.of(v), Optional.empty()));
    }

    public static ComboCorte polleraCintura(int nivel) {
        return VACIO.conPollera(new PolleraCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(nivel)));
    }

    public static ComboCorte polleraForma(com.modamod.item.PolleraForma v) {
        return VACIO.conPollera(new PolleraCorte(Optional.empty(), Optional.of(v)));
    }

    public static ComboCorte capaLargo(com.modamod.item.CapaLargo v) {
        return VACIO.conPollera(new PolleraCorte(Optional.empty(), Optional.empty(), Optional.of(v),
                Optional.empty(), Optional.empty(), Optional.empty()));
    }

    public static ComboCorte capaRuedo(com.modamod.item.CapaRuedo v) {
        return VACIO.conPollera(new PolleraCorte(Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(v), Optional.empty(), Optional.empty()));
    }

    public static ComboCorte capaCapucha(boolean v) {
        return VACIO.conPollera(new PolleraCorte(Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(v), Optional.empty()));
    }

    public static ComboCorte capaCuello(boolean v) {
        return VACIO.conPollera(new PolleraCorte(Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of(v)));
    }

    public static ComboCorte sombreroAla(com.modamod.item.SombreroAla v) {
        return VACIO.conPollera(new PolleraCorte(Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(v), Optional.empty()));
    }

    private static ComboCorte conSombrero(SombreroExtra e) {
        return conChaqueta(new ChaquetaCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(e)));
    }

    /** La planta del ala plana del sombrero (2026-10-14): true = cuadrada, false = redonda. */
    public static ComboCorte sombreroPlanta(boolean cuadrada) {
        return conSombrero(new SombreroExtra(Optional.of(cuadrada), Optional.empty(), Optional.empty(), Optional.empty()));
    }

    /** El estilo del sombrero (2026-10-14): true = cuboides, false = malla. */
    public static ComboCorte sombreroEstilo(boolean cajas) {
        return conSombrero(new SombreroExtra(Optional.empty(), Optional.of(cajas), Optional.empty(), Optional.empty()));
    }

    /** El grosor de las alas del sombrero (2026-10-14): true = gruesas, false = finas. */
    public static ComboCorte sombreroGrosor(boolean gruesa) {
        return conSombrero(new SombreroExtra(Optional.empty(), Optional.empty(), Optional.of(gruesa), Optional.empty()));
    }

    /** La banda del sombrero (2026-10-14): true = con, false = sin. */
    public static ComboCorte sombreroBanda(boolean con) {
        return conSombrero(new SombreroExtra(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(con)));
    }

    public static ComboCorte sombreroPunta(com.modamod.item.SombreroPunta v) {
        return VACIO.conPollera(new PolleraCorte(Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(v)));
    }

    /** Un molde de ruedo puesto en el pin de {@code zona}. */
    public static ComboCorte ruedo(com.modamod.item.ZonaRuedo zona, com.modamod.item.Ruedo v, boolean espejo) {
        return VACIO.conPollera(new PolleraCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(new RuedoCorte(zona, v, espejo))));
    }

    public static ComboCorte voladoRuedo(com.modamod.item.PolleraVolado v) {
        return VACIO.conPollera(new PolleraCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of(v), Optional.empty()));
    }

    public static ComboCorte voladoTodo(com.modamod.item.PolleraVolado v) {
        return VACIO.conPollera(new PolleraCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(v)));
    }

    public static ComboCorte bandaZona(com.modamod.item.BandaZona v) {
        return VACIO.conPollera(new PolleraCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(v),
                Optional.empty(), Optional.empty()));
    }

    public static ComboCorte bandaAncho(com.modamod.item.BandaAncho v) {
        return VACIO.conPollera(new PolleraCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(v), Optional.empty()));
    }

    public static ComboCorte bandaHerraje(com.modamod.item.BandaHerraje v) {
        return VACIO.conPollera(new PolleraCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(v)));
    }

    public ComboCorte conPollera(PolleraCorte p) {
        return new ComboCorte(remeraLargo, remeraManga, remeraCuello, pantalonTiro,
                pantalonLargoSuperior, pantalonLargoInferior, mediasLargoSuperior, mediasLargoInferior,
                calientabrazosCoberturaSuperior, calientabrazosCoberturaInferior, calce, red,
                capaPatron, lado, iconoOrigen, Optional.of(p));
    }

    /** Un molde de patrón en un índice de capa (0..2) — ver {@link #capaPatron}. */
    public record CapaIndexada(int indice, Identifier patronId) {}

    public static final ComboCorte VACIO = new ComboCorte(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(),
            Lado.AMBAS, Optional.empty(), Optional.empty());

    // ── factories: un campo poblado por llamada, evita el positional de 15 args ──

    public static ComboCorte remeraLargo(Variante.Largo v) {
        return new ComboCorte(Optional.of(v), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(),
                Lado.AMBAS, Optional.empty(), Optional.empty());
    }

    public static ComboCorte remeraManga(Variante.Manga v) {
        return remeraManga(v, Lado.AMBAS);
    }

    /** Manga por lado (2026-09-24, "vamos con mangas distintas") — ver {@code RemeraItem#setManga}. */
    public static ComboCorte remeraManga(Variante.Manga v, Lado lado) {
        return new ComboCorte(Optional.empty(), Optional.of(v), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(),
                lado, Optional.empty(), Optional.empty());
    }

    public static ComboCorte remeraCuello(Variante.Cuello v) {
        return new ComboCorte(Optional.empty(), Optional.empty(), Optional.of(v), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(),
                Lado.AMBAS, Optional.empty(), Optional.empty());
    }

    public static ComboCorte tiro(PantalonTiro t) {
        return new ComboCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(t),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(),
                Lado.AMBAS, Optional.empty(), Optional.empty());
    }

    public static ComboCorte pantalonSuperior(Botamanga v, Lado lado) {
        return new ComboCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(v), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(),
                lado, Optional.empty(), Optional.empty());
    }

    public static ComboCorte pantalonInferior(Botamanga v, Lado lado) {
        return new ComboCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(v), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(),
                lado, Optional.empty(), Optional.empty());
    }

    public static ComboCorte mediasSuperior(Botamanga v, Lado lado) {
        return new ComboCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of(v), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(),
                lado, Optional.empty(), Optional.empty());
    }

    public static ComboCorte mediasInferior(Botamanga v, Lado lado) {
        return new ComboCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(v),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(),
                lado, Optional.empty(), Optional.empty());
    }

    public static ComboCorte calientabrazosSuperior(Variante.Manga v, Lado lado) {
        return new ComboCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(v), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(),
                lado, Optional.empty(), Optional.empty());
    }

    public static ComboCorte calientabrazosInferior(Variante.Manga v, Lado lado) {
        return new ComboCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(v), Optional.empty(), Optional.empty(),
                Optional.empty(),
                lado, Optional.empty(), Optional.empty());
    }

    /** Calce — a pedido, transversal a las 4 categorías, sin anclaje ni lado. */
    public static ComboCorte calce(Calce v) {
        return new ComboCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of(v), Optional.empty(),
                Optional.empty(),
                Lado.AMBAS, Optional.empty(), Optional.empty());
    }

    /** Red — a pedido (2026-09-20), transversal a las 4 categorías, sin anclaje ni lado. */
    public static ComboCorte red(PatronRed v) {
        return new ComboCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(v),
                Optional.empty(),
                Lado.AMBAS, Optional.empty(), Optional.empty());
    }

    /** Material en un índice de capa (2026-09-24) — transversal, sin lado (remera es AMBAS-únicamente para patrón). */
    public static ComboCorte capaPatron(int indice, Identifier patronId) {
        return capaPatron(indice, patronId, Lado.AMBAS);
    }

    /** Personalización por lado (medias/calientabrazos, 2026-09-26): la capa va solo a la pierna/brazo de {@code lado}. */
    public static ComboCorte capaPatron(int indice, Identifier patronId, Lado lado) {
        return new ComboCorte(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(new CapaIndexada(indice, patronId)),
                lado, Optional.empty(), Optional.empty());
    }

    /**
     * Presets de prueba "cobertura de torso/extremidad" (ítems genéricos,
     * un nivel abstracto aplicado a todas las prendas de esa categoría a la
     * vez) — reusan el anclaje HISTÓRICO de cada prenda: torso siempre
     * entero (remera+tiro); extremidad usa el anclaje que ya tenía cada una
     * antes de este eje (pantalón=superior, medias=inferior,
     * calientabrazos=superior), la anterior queda en su default (abierta).
     */
    public static ComboCorte coberturaTorso(Variante.Largo remeraLargo, PantalonTiro tiro) {
        return new ComboCorte(
                Optional.of(remeraLargo), Optional.empty(), Optional.empty(),
                Optional.of(tiro),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(),
                Lado.AMBAS, Optional.empty(), Optional.empty());
    }

    public static ComboCorte coberturaExtremidad(Botamanga pantalon, Botamanga medias, Variante.Manga calientabrazos) {
        return new ComboCorte(
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(),
                Optional.of(pantalon), Optional.empty(),
                Optional.empty(), Optional.of(medias),
                Optional.of(calientabrazos), Optional.empty(),
                Optional.empty(), Optional.empty(),
                Optional.empty(),
                Lado.AMBAS, Optional.empty(), Optional.empty());
    }

    /** Copia con {@link #iconoOrigen} puesto — ver {@code ModeladoBlockEntity#fijar}. */
    public ComboCorte conIcono(Identifier id) {
        return new ComboCorte(remeraLargo, remeraManga, remeraCuello, pantalonTiro,
                pantalonLargoSuperior, pantalonLargoInferior, mediasLargoSuperior, mediasLargoInferior,
                calientabrazosCoberturaSuperior, calientabrazosCoberturaInferior, calce, red,
                capaPatron, lado,
                Optional.of(id), pollera);
    }

    public boolean estaVacio() {
        return remeraLargo.isEmpty() && remeraManga.isEmpty() && remeraCuello.isEmpty()
                && pantalonTiro.isEmpty()
                && pantalonLargoSuperior.isEmpty() && pantalonLargoInferior.isEmpty()
                && mediasLargoSuperior.isEmpty() && mediasLargoInferior.isEmpty()
                && calientabrazosCoberturaSuperior.isEmpty() && calientabrazosCoberturaInferior.isEmpty()
                && calce.isEmpty() && red.isEmpty() && capaPatron.isEmpty()
                && (pollera.isEmpty() || pollera.get().vacio());
    }

    private static final Codec<Lado> LADO_CODEC = Codec.STRING.xmap(Lado::valueOf, Enum::name);

    private static final Codec<CapaIndexada> CAPA_INDEXADA_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("indice").forGetter(CapaIndexada::indice),
            Identifier.CODEC.fieldOf("patron_id").forGetter(CapaIndexada::patronId)
    ).apply(i, CapaIndexada::new));

    public static final Codec<ComboCorte> CODEC = RecordCodecBuilder.create(i -> i.group(
            StringIdentifiable.createCodec(Variante.Largo::values).optionalFieldOf("remera_largo")
                    .forGetter(ComboCorte::remeraLargo),
            StringIdentifiable.createCodec(Variante.Manga::values).optionalFieldOf("remera_manga")
                    .forGetter(ComboCorte::remeraManga),
            StringIdentifiable.createCodec(Variante.Cuello::values).optionalFieldOf("remera_cuello")
                    .forGetter(ComboCorte::remeraCuello),
            StringIdentifiable.createCodec(PantalonTiro::values).optionalFieldOf("pantalon_tiro")
                    .forGetter(ComboCorte::pantalonTiro),
            StringIdentifiable.createCodec(Botamanga::values).optionalFieldOf("pantalon_largo_superior")
                    .forGetter(ComboCorte::pantalonLargoSuperior),
            StringIdentifiable.createCodec(Botamanga::values).optionalFieldOf("pantalon_largo_inferior")
                    .forGetter(ComboCorte::pantalonLargoInferior),
            StringIdentifiable.createCodec(Botamanga::values).optionalFieldOf("medias_largo_superior")
                    .forGetter(ComboCorte::mediasLargoSuperior),
            StringIdentifiable.createCodec(Botamanga::values).optionalFieldOf("medias_largo_inferior")
                    .forGetter(ComboCorte::mediasLargoInferior),
            StringIdentifiable.createCodec(Variante.Manga::values).optionalFieldOf("calientabrazos_cobertura_superior")
                    .forGetter(ComboCorte::calientabrazosCoberturaSuperior),
            StringIdentifiable.createCodec(Variante.Manga::values).optionalFieldOf("calientabrazos_cobertura_inferior")
                    .forGetter(ComboCorte::calientabrazosCoberturaInferior),
            StringIdentifiable.createCodec(Calce::values).optionalFieldOf("calce")
                    .forGetter(ComboCorte::calce),
            StringIdentifiable.createCodec(PatronRed::values).optionalFieldOf("red")
                    .forGetter(ComboCorte::red),
            CAPA_INDEXADA_CODEC.optionalFieldOf("capa_patron")
                    .forGetter(ComboCorte::capaPatron),
            LADO_CODEC.optionalFieldOf("lado", Lado.AMBAS).forGetter(ComboCorte::lado),
            Identifier.CODEC.optionalFieldOf("icono_origen").forGetter(ComboCorte::iconoOrigen),
            PolleraCorte.CODEC.optionalFieldOf("pollera").forGetter(ComboCorte::pollera)
    ).apply(i, ComboCorte::new));

    public static final PacketCodec<ByteBuf, ComboCorte> PACKET_CODEC = PacketCodec.of(
            (combo, buf) -> {
                writeOptEnum(buf, combo.remeraLargo);
                writeOptEnum(buf, combo.remeraManga);
                writeOptEnum(buf, combo.remeraCuello);
                writeOptEnum(buf, combo.pantalonTiro);
                writeOptEnum(buf, combo.pantalonLargoSuperior);
                writeOptEnum(buf, combo.pantalonLargoInferior);
                writeOptEnum(buf, combo.mediasLargoSuperior);
                writeOptEnum(buf, combo.mediasLargoInferior);
                writeOptEnum(buf, combo.calientabrazosCoberturaSuperior);
                writeOptEnum(buf, combo.calientabrazosCoberturaInferior);
                writeOptEnum(buf, combo.calce);
                writeOptEnum(buf, combo.red);
                buf.writeBoolean(combo.capaPatron.isPresent());
                combo.capaPatron.ifPresent(c -> {
                    buf.writeInt(c.indice());
                    Identifier.PACKET_CODEC.encode(buf, c.patronId());
                });
                buf.writeByte(combo.lado.ordinal());
                buf.writeBoolean(combo.iconoOrigen.isPresent());
                combo.iconoOrigen.ifPresent(id -> Identifier.PACKET_CODEC.encode(buf, id));
                buf.writeBoolean(combo.pollera.isPresent());
                combo.pollera.ifPresent(p -> {
                    writeOptEnum(buf, p.largo());
                    writeOptEnum(buf, p.forma());
                    writeOptEnum(buf, p.capaLargo());
                    writeOptEnum(buf, p.capaRuedo());
                    writeOptBool(buf, p.capaCapucha());
                    writeOptBool(buf, p.capaCuello());
                    writeOptEnum(buf, p.sombreroAla());
                    writeOptEnum(buf, p.sombreroPunta());
                    writeOptEnum(buf, p.bandaZona());
                    writeOptEnum(buf, p.bandaAncho());
                    writeOptEnum(buf, p.bandaHerraje());
                    writeOptEnum(buf, p.voladoRuedo());
                    writeOptEnum(buf, p.voladoTodo());
                    buf.writeBoolean(p.ruedo().isPresent());
                    p.ruedo().ifPresent(r -> {
                        buf.writeByte(r.zona().ordinal());
                        buf.writeByte(r.ruedo().ordinal());
                        buf.writeBoolean(r.espejo());
                    });
                    buf.writeBoolean(p.chaqueta().isPresent());
                    p.chaqueta().ifPresent(c -> {
                        writeOptEnum(buf, c.frente());
                        writeOptBool(buf, c.capucha());
                        writeOptEnum(buf, c.solapa());
                        buf.writeBoolean(c.borcego().isPresent());
                        c.borcego().ifPresent(bo -> {
                            writeOptEnum(buf, bo.cana());
                            writeOptEnum(buf, bo.suela());
                            writeOptEnum(buf, bo.botamanga());
                            writeOptEnum(buf, bo.punta());
                            writeOptEnum(buf, bo.cierre());
                            writeOptEnum(buf, bo.detalle());
                            writeOptBool(buf, bo.estiloCajas());
                        });
                        buf.writeByte(c.faldon().isPresent() ? c.faldon().get() : -1);
                        buf.writeBoolean(c.delantal().isPresent());
                        c.delantal().ifPresent(d -> {
                            buf.writeByte(d.inicio().isPresent() ? d.inicio().get() : -1);
                            buf.writeByte(d.largo().isPresent() ? d.largo().get() : -1);
                            writeOptEnum(buf, d.forma());
                            writeOptEnum(buf, d.esquinas());
                            writeOptEnum(buf, d.volado());
                            writeOptEnum(buf, d.lateral());
                            writeOptEnum(buf, d.superior());
                            writeOptEnum(buf, d.tirantes());
                            writeOptEnum(buf, d.cobertura());
                        });
                        buf.writeBoolean(c.embozo().isPresent());
                        c.embozo().ifPresent(em -> {
                            buf.writeByte(em.nivel().isPresent() ? em.nivel().get() : -2);
                            writeOptBool(buf, em.borde());
                        });
                        buf.writeBoolean(c.sombrero().isPresent());
                        c.sombrero().ifPresent(so -> {
                            writeOptBool(buf, so.plantaCuadrada());
                            writeOptBool(buf, so.estiloCajas());
                            writeOptBool(buf, so.grosorGrueso());
                            writeOptBool(buf, so.banda());
                        });
                    });
                    buf.writeByte(p.cintura().isPresent() ? p.cintura().get() : -1);
                });
            },
            buf -> new ComboCorte(
                    readOptEnum(buf, Variante.Largo.values()),
                    readOptEnum(buf, Variante.Manga.values()),
                    readOptEnum(buf, Variante.Cuello.values()),
                    readOptEnum(buf, PantalonTiro.values()),
                    readOptEnum(buf, Botamanga.values()),
                    readOptEnum(buf, Botamanga.values()),
                    readOptEnum(buf, Botamanga.values()),
                    readOptEnum(buf, Botamanga.values()),
                    readOptEnum(buf, Variante.Manga.values()),
                    readOptEnum(buf, Variante.Manga.values()),
                    readOptEnum(buf, Calce.values()),
                    readOptEnum(buf, PatronRed.values()),
                    buf.readBoolean()
                            ? Optional.of(new CapaIndexada(buf.readInt(), Identifier.PACKET_CODEC.decode(buf)))
                            : Optional.empty(),
                    Lado.values()[buf.readByte()],
                    buf.readBoolean() ? Optional.of(Identifier.PACKET_CODEC.decode(buf)) : Optional.empty(),
                    buf.readBoolean() ? Optional.of(new PolleraCorte(
                            readOptEnum(buf, com.modamod.item.PolleraLargo.values()),
                            readOptEnum(buf, com.modamod.item.PolleraForma.values()),
                            readOptEnum(buf, com.modamod.item.CapaLargo.values()),
                            readOptEnum(buf, com.modamod.item.CapaRuedo.values()),
                            readOptBool(buf), readOptBool(buf),
                            readOptEnum(buf, com.modamod.item.SombreroAla.values()),
                            readOptEnum(buf, com.modamod.item.SombreroPunta.values()),
                            readOptEnum(buf, com.modamod.item.BandaZona.values()),
                            readOptEnum(buf, com.modamod.item.BandaAncho.values()),
                            readOptEnum(buf, com.modamod.item.BandaHerraje.values()),
                            readOptEnum(buf, com.modamod.item.PolleraVolado.values()),
                            readOptEnum(buf, com.modamod.item.PolleraVolado.values()),
                            buf.readBoolean() ? Optional.of(new RuedoCorte(
                                    com.modamod.item.ZonaRuedo.values()[buf.readByte()],
                                    com.modamod.item.Ruedo.values()[buf.readByte()],
                                    buf.readBoolean())) : Optional.empty(),
                            buf.readBoolean() ? Optional.of(new ChaquetaCorte(
                                    readOptEnum(buf, com.modamod.item.ChaquetaFrente.values()),
                                    readOptBool(buf),
                                    readOptEnum(buf, com.modamod.item.ChaquetaSolapa.values()),
                                    buf.readBoolean() ? Optional.of(new BorcegoCorte(
                                            readOptEnum(buf, com.modamod.item.BorcegoCana.values()),
                                            readOptEnum(buf, com.modamod.item.BorcegoSuela.values()),
                                            readOptEnum(buf, com.modamod.item.BorcegoBotamanga.values()),
                                            readOptEnum(buf, com.modamod.item.CalzadoPunta.values()),
                                            readOptEnum(buf, com.modamod.item.CalzadoCierre.values()),
                                            readOptEnum(buf, com.modamod.item.CalzadoDetalle.values()),
                                            readOptBool(buf)))
                                            : Optional.empty(),
                                    readCintura(buf),
                                    buf.readBoolean() ? Optional.of(new DelantalCorte(
                                            readCintura(buf), readCintura(buf),
                                            readOptEnum(buf, com.modamod.item.DelantalForma.values()),
                                            readOptEnum(buf, com.modamod.item.CapaRuedo.values()),
                                            readOptEnum(buf, com.modamod.item.PolleraVolado.values()),
                                            readOptEnum(buf, com.modamod.item.PolleraVolado.values()),
                                            readOptEnum(buf, com.modamod.item.PolleraVolado.values()),
                                            readOptEnum(buf, com.modamod.item.DelantalTirantes.values()),
                                            readOptEnum(buf, com.modamod.item.DelantalCobertura.values())))
                                            : Optional.empty(),
                                    buf.readBoolean() ? Optional.of(new EmbozoCorte(readNivelEmbozo(buf), readOptBool(buf)))
                                            : Optional.empty(),
                                    buf.readBoolean() ? Optional.of(new SombreroExtra(readOptBool(buf), readOptBool(buf),
                                            readOptBool(buf), readOptBool(buf))) : Optional.empty())) : Optional.empty(),
                            readCintura(buf)))
                            : Optional.empty()));

    private static Optional<Integer> readNivelEmbozo(ByteBuf buf) {
        byte b = buf.readByte();
        return b < -1 ? Optional.empty() : Optional.of((int) b);
    }

    private static Optional<Integer> readCintura(ByteBuf buf) {
        byte b = buf.readByte();
        return b < 0 ? Optional.empty() : Optional.of((int) b);
    }

    private static void writeOptBool(ByteBuf buf, Optional<Boolean> value) {
        buf.writeByte(value.isEmpty() ? 0 : value.get() ? 2 : 1);
    }

    private static Optional<Boolean> readOptBool(ByteBuf buf) {
        byte b = buf.readByte();
        return b == 0 ? Optional.empty() : Optional.of(b == 2);
    }

    private static <E extends Enum<E>> void writeOptEnum(ByteBuf buf, Optional<E> value) {
        buf.writeBoolean(value.isPresent());
        if (value.isPresent()) buf.writeByte(value.get().ordinal());
    }

    private static <E extends Enum<E>> Optional<E> readOptEnum(ByteBuf buf, E[] values) {
        return buf.readBoolean() ? Optional.of(values[buf.readByte()]) : Optional.empty();
    }
}
