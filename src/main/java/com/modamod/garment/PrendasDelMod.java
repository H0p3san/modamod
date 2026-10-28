package com.modamod.garment;

import com.modamod.item.ModamodItems;
import com.modamod.region.Operacion;
import com.modamod.region.Region;
import com.modamod.sublimadora.ModItems;
import net.minecraft.item.ItemStack;

import java.util.EnumSet;
import java.util.Set;

/**
 * Que es cada prenda del mod, del lado que el servidor tambien necesita.
 *
 * Es un solo archivo y no un metodo en cada Item a proposito: asi la tabla de
 * "quien acepta que operacion en que region" se lee entera de un vistazo, que
 * es lo que hace falta para no volver a tener el mismo concepto escrito
 * distinto en cada estacion.
 */
public final class PrendasDelMod {

    private PrendasDelMod() {}

    public static void init() {
        Garments.registrar(ModamodItems.SOCKS_SOLID, MEDIAS);
        Garments.registrar(ModamodItems.PANTALON, PANTALON);
        Garments.registrar(ModItems.REMERA, REMERA);
        Garments.registrar(ModamodItems.CALIENTABRAZOS, CALIENTABRAZOS);
        Garments.registrar(ModamodItems.POLLERA, POLLERA);
        Garments.registrar(ModamodItems.CAPA, CAPA);
        Garments.registrar(ModamodItems.DELANTAL, DELANTAL);
    }

    /**
     * Medias: bilaterales de punta a punta.
     *
     * Son la unica prenda que ya tenia el per-lado antes del RegionResolver
     * (los componentes RIGHT_*), y por eso son la referencia de como se
     * comporta el eje Lado.
     */
    private static final Garment MEDIAS = new Garment() {
        @Override
        public Set<Region> regionesDe(Operacion op) {
            return switch (op) {
                case TENIR, PATRON, ESTAMPAR -> BILATERAL;
                // Las medias todavia no tienen ejes de corte. Cuando los
                // tengan (largo tobillo/media/rodilla/muslo, puño) esto pasa
                // a BILATERAL tambien: nada impide una media hasta la rodilla
                // y la otra al tobillo.
                case CORTE -> Set.of();
            };
        }

        @Override
        public Set<Parte> partes(ItemStack stack) {
            return EnumSet.of(Parte.PIERNA_IZQ, Parte.PIERNA_DER);
        }
    };

    /**
     * Pantalón: la prenda larga de pierna, y la primera prueba real del
     * sistema de capas (era "shorts" — ver MODAMOD.md).
     *
     * Va en {@code piernas/exterior}, un slot DISTINTO del de las medias
     * ({@code socks/pair}) — pueden estar puestas las dos a la vez. Quien
     * decide que se dibuja arriba es {@code Capa.PIERNA_EXTERIOR} (20)
     * contra {@code Capa.MEDIA} (10), no el order de los slots de Trinkets.
     *
     * `CORTE` es `ENTERA` (como remera): el largo es UN valor para todo el
     * pantalón, no por pierna — nadie tiene una pierna en bermudas y la otra
     * en tanga. `TENIR` sigue siendo `BILATERAL`, sin cambios respecto de
     * cuando era shorts: cada pierna puede tener su propio color.
     *
     * Sin patrón todavía: a propósito, mismo motivo que shorts no lo tenía
     * — se suma después con el mismo mecanismo genérico de medias/remera.
     */
    private static final Garment PANTALON = new Garment() {
        @Override
        public Set<Region> regionesDe(Operacion op) {
            return switch (op) {
                // PATRON habilitado a pedido (2026-09-18): "todas las
                // prendas compatibles con los patrones" — antes vacío
                // "por diseño" (ver el tooltip viejo de ClothingPatternItem,
                // que hay que actualizar).
                case TENIR, PATRON -> BILATERAL;
                case CORTE -> ENTERA;
                case ESTAMPAR -> Set.of();
            };
        }

        @Override
        public Set<Parte> partes(ItemStack stack) {
            return EnumSet.of(Parte.PIERNA_IZQ, Parte.PIERNA_DER);
        }
    };

    /**
     * Calientabrazos: la prenda base de brazo, análoga a medias en la pierna.
     *
     * `CORTE` es `ENTERA`: cobertura y tiro son un valor para toda la prenda,
     * no por brazo (nadie tiene un brazo con muñequera y el otro con manga
     * larga). `TENIR` sigue siendo `BILATERAL`, cada brazo su color, igual
     * que pantalón cada pierna.
     *
     * `partes()` es siempre los dos brazos, nunca TORSO — igual que pantalón
     * NO declara TORSO pese a pintar ahí una pieza por el tiro: una parte
     * declarada se cubre con cuerpo base, y eso taparía el torso entero sin
     * remera puesta a cambio de una banda de un par de filas.
     */
    private static final Garment CALIENTABRAZOS = new Garment() {
        @Override
        public Set<Region> regionesDe(Operacion op) {
            return switch (op) {
                // PATRON habilitado a pedido (2026-09-18), ver PANTALON.
                case TENIR, PATRON -> BILATERAL;
                case CORTE -> ENTERA;
                case ESTAMPAR -> Set.of();
            };
        }

        @Override
        public Set<Parte> partes(ItemStack stack) {
            return EnumSet.of(Parte.BRAZO_IZQ, Parte.BRAZO_DER);
        }
    };

    /**
     * Pollera — segunda pasada (2026-09-16): NO declara partes. A
     * diferencia del pantalón (que cubre la pierna entera y por eso apaga
     * la skin/CUERPO ahí), la pollera es un tubo-campana que abraza y
     * puede colisionar con la pierna pero no la tapa por completo en toda
     * su superficie — apagar {@code PIERNA_IZQ}/{@code PIERNA_DER} dejaría
     * huecos de skin faltante donde la geometría de la pollera no llega.
     * Se dibuja puramente ENCIMA (ver {@code GarmentFeatureRenderer
     * #dibujarPollera}), sin gobernar ninguna parte del cuerpo. Comparte
     * slot con pantalón igual (alternativas — ver {@code
     * piernas/exterior.json}). Sin eje de corte todavía, `CORTE` vacío.
     */
    private static final Garment POLLERA = new Garment() {
        @Override
        public Set<Region> regionesDe(Operacion op) {
            return switch (op) {
                // PATRON habilitado a pedido (2026-09-18), ver PANTALON.
                case TENIR, PATRON -> BILATERAL;
                // Largo/forma y estampa desde 2026-09-29 (pollera rehecha).
                case CORTE -> ENTERA;
                case ESTAMPAR -> CARAS;
            };
        }

        @Override
        public Set<Parte> partes(ItemStack stack) {
            return Set.of();
        }
    };

    /**
     * Delantal (2026-10-11): se dibuja aparte, como la pollera (malla al frente del cuerpo), sin gobernar ninguna parte.
     * Frente de la tela = el delantal; no tiene espalda para estampar.
     */
    private static final Garment DELANTAL = new Garment() {
        @Override
        public Set<Region> regionesDe(Operacion op) {
            return switch (op) {
                case TENIR, PATRON, CORTE -> ENTERA;
                case ESTAMPAR -> CARAS;
            };
        }

        @Override
        public Set<Parte> partes(ItemStack stack) {
            return Set.of();
        }
    };

    /**
     * Capa (2026-09-29): se dibuja aparte, colgando de los hombros con la
     * dinámica de la capa vanilla (ver {@code GarmentFeatureRenderer#dibujarCapa}),
     * sin gobernar ninguna parte del cuerpo. Frente = exterior, Espalda = forro.
     */
    private static final Garment CAPA = new Garment() {
        @Override
        public Set<Region> regionesDe(Operacion op) {
            return switch (op) {
                case TENIR, PATRON, CORTE -> ENTERA;
                case ESTAMPAR -> CARAS;
            };
        }

        @Override
        public Set<Parte> partes(ItemStack stack) {
            return Set.of();
        }
    };

    /**
     * Remera: un color base para toda la prenda, pero dos caras para estampar.
     *
     * Es el ejemplo de por que las regiones se declaran POR OPERACION. Tenir
     * medio torso no tiene sentido —es una remera, tiene un color—, pero
     * estampar frente y espalda por separado es justamente lo que hace la
     * sublimadora. Un solo set para las dos operaciones dejaria botones que
     * no hacen nada en una de las dos estaciones.
     */
    private static final Garment REMERA = new Garment() {
        @Override
        public Set<Region> regionesDe(Operacion op) {
            return switch (op) {
                case TENIR, PATRON, CORTE -> ENTERA;
                case ESTAMPAR -> CARAS;
            };
        }

        /**
         * El torso siempre; los brazos SIEMPRE tambien, tenga mangas o no.
         *
         * Que una musculosa declare los brazos parece de mas y es justamente
         * lo contrario: no dibuja tela ahi, pero tiene que taparle las mangas
         * pintadas de la skin. Sin eso, una musculosa muestra la manga de la
         * remera que el jugador tiene dibujada, no el brazo.
         */
        @Override
        public Set<Parte> partes(ItemStack stack) {
            // El remeron va a sumar PIERNA_IZQ/DER cuando el ruedo se dibuje
            // de verdad (punto 4 del roadmap). NO se declaran todavia: una
            // parte declarada se cubre con cuerpo base y se le borra la capa
            // externa de la skin, asi que declararla sin tela ahi le borraria
            // el pantalon pintado al jugador a cambio de nada.
            return EnumSet.of(Parte.TORSO, Parte.BRAZO_IZQ, Parte.BRAZO_DER);
        }
    };
}
