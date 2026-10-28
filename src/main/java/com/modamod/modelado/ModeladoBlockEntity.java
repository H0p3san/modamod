package com.modamod.modelado;

import com.modamod.item.Botamanga;
import com.modamod.item.ClothingPatternItem;
import com.modamod.item.PantalonTiro;
import com.modamod.region.Lado;
import com.modamod.sublimadora.MoldeCuelloItem;
import com.modamod.sublimadora.MoldeItem;
import com.modamod.sublimadora.Variante;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Mesa de Modelado — "máquina industrial" (docs/archivo/PRODUCCION_TEXTIL.md):
 * apagada = configurable (almacén, borrador, lista de fijadas); encendida =
 * config bloqueada, procesa sola cualquier prenda que reciba.
 *
 * Storage en dos niveles (a pedido, "storage compartido y storage por
 * prenda"): {@link #ALMACEN_INICIO}..{@link #ALMACEN_FIN} (27, compartido
 * entre las 4 categorías) y {@link #PORPRENDA_INICIO}..{@link #PORPRENDA_FIN}
 * (48 = 12 por {@link Categoria}, aunque en pantalla solo se ve el banco de
 * la categoría actual — el botón de categoría cicla cuál). Un molde ACTIVO
 * por categoría ({@link #ACTIVO_INICIO}, 4 slots, uno por
 * {@code Categoria.ordinal()}), también con el mismo truco: solo el de la
 * categoría actual se ve/usa. {@link #PRENDA} (1, física) y {@link #SALIDA}
 * (1, resultado) son genéricos, no por categoría.
 */
public class ModeladoBlockEntity extends BlockEntity implements SidedInventory, com.modamod.util.ConSalida,
        net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<BlockPos>, GeoBlockEntity,
        com.modamod.util.MaquinaCreativa.Cargable , com.modamod.util.LimiteEntrada {

    public static final int ALMACEN_INICIO = 0;
    public static final int ALMACEN_TAMANO = 27; // 3 filas x 9
    public static final int ALMACEN_FIN = ALMACEN_INICIO + ALMACEN_TAMANO; // exclusivo

    public static final int ACTIVO_INICIO = ALMACEN_FIN;
    public static final int ACTIVO_TAMANO = 4; // uno por Categoria
    public static final int ACTIVO_FIN = ACTIVO_INICIO + ACTIVO_TAMANO;

    public static final int PORPRENDA_INICIO = ACTIVO_INICIO + ACTIVO_TAMANO;
    public static final int PORPRENDA_POR_CATEGORIA = 12;
    public static final int PORPRENDA_TAMANO = PORPRENDA_POR_CATEGORIA * 4; // 48
    public static final int PORPRENDA_FIN = PORPRENDA_INICIO + PORPRENDA_TAMANO;

    public static final int PRENDA = PORPRENDA_FIN;
    public static final int SALIDA = PRENDA + 1;

    // Pines del esquema de remera (2026-09-24, "lo que pones ahi desaparece
    // en vez de quedar para pinear y q se vea que tiene"): slots REALES del
    // inventario, al final de la lista (así los índices de las partidas
    // viejas no se corren) — el molde se queda en el pin y se ve.
    public static final int PINES_INICIO = SALIDA + 1;
    /**
     * 14 pines por categoría (2026-10-07, "porque tenemos el limite de 12?" → subir a 14 para el ruedo/puño/bordes;
     * reset de Modeladoras con el renombre a ModaMod, sin migración). Las filas de {@link #ROLES} y de
     * {@code PIN_POS}/{@code PIN_BTN} se rellenan solas hasta este largo con pines NINGUNO.
     * La lista NBT {@code pollera_items} sigue bajo el tope de 255 (5 categorías x 51 = 255 slots como mucho).
     */
    public static final int PINES_POR_CATEGORIA = 14;
    public static final int PINES_TAMANO = PINES_POR_CATEGORIA * 4;
    public static final int PINES_FIN = PINES_INICIO + PINES_TAMANO;
    /**
     * Más lugares en "Moldes de <prenda>" (2026-09-29, "agregaria mas
     * slots a moldes de pantalones moldes de remera"): 24 más por
     * categoría, al FINAL del inventario igual que los pines, así los
     * índices de las partidas viejas no se corren. En pantalla se ven los
     * 36 juntos (9x4) — ver {@link #porPrendaSlot}.
     */
    public static final int PORPRENDA_EXTRA_POR_CATEGORIA = 24;
    public static final int PORPRENDA_TOTAL = PORPRENDA_POR_CATEGORIA + PORPRENDA_EXTRA_POR_CATEGORIA;
    public static final int PORPRENDA_EXTRA_INICIO = PINES_FIN;
    public static final int PORPRENDA_EXTRA_FIN = PORPRENDA_EXTRA_INICIO + PORPRENDA_EXTRA_POR_CATEGORIA * 4;
    /**
     * Lo que ya existía antes de la Pollera: se guarda con
     * {@code Inventories.writeNbt}, que anota el número de slot en un BYTE
     * (0..255) — por eso lo nuevo no puede ir en la misma lista.
     */
    public static final int TAMANO_VIEJO = PORPRENDA_EXTRA_FIN;
    /**
     * Categoría Pollera (2026-09-29, "Categoría Pollera en la Modeladora"):
     * su Activo, sus 12 pines y sus 36 "Moldes de Pollera", al FINAL del
     * inventario (no se corre ningún índice viejo) y guardados en una lista
     * NBT aparte ({@code "pollera_items"}).
     */
    public static final int POLLERA_ACTIVO = TAMANO_VIEJO;
    public static final int POLLERA_PINES_INICIO = POLLERA_ACTIVO + 1;
    public static final int POLLERA_PORPRENDA_INICIO = POLLERA_PINES_INICIO + PINES_POR_CATEGORIA;
    /**
     * Categoría Capa (2026-09-29, "seria una nueva categoria de ropa"): mismo
     * bloque que la Pollera (Activo + 12 pines + 36 moldes), a continuación,
     * en la misma lista NBT aparte.
     */
    public static final int CAPA_ACTIVO = POLLERA_PORPRENDA_INICIO + PORPRENDA_TOTAL;
    public static final int CAPA_PINES_INICIO = CAPA_ACTIVO + 1;
    public static final int CAPA_PORPRENDA_INICIO = CAPA_PINES_INICIO + PINES_POR_CATEGORIA;
    /**
     * Categoría Sombrero (2026-10-05, "segunda tanda del sombrero"): mismo
     * bloque que la Capa, a continuación, en la misma lista NBT aparte.
     */
    public static final int SOMBRERO_ACTIVO = CAPA_PORPRENDA_INICIO + PORPRENDA_TOTAL;
    public static final int SOMBRERO_PINES_INICIO = SOMBRERO_ACTIVO + 1;
    public static final int SOMBRERO_PORPRENDA_INICIO = SOMBRERO_PINES_INICIO + PINES_POR_CATEGORIA;
    /** Categoría Banda (2026-10-05, "correas y cintos"): mismo bloque, a continuación del Sombrero. */
    public static final int BANDA_ACTIVO = SOMBRERO_PORPRENDA_INICIO + PORPRENDA_TOTAL;
    public static final int BANDA_PINES_INICIO = BANDA_ACTIVO + 1;
    public static final int BANDA_PORPRENDA_INICIO = BANDA_PINES_INICIO + PINES_POR_CATEGORIA;
    /** Categoría Calzado (2026-10-08, slot de calzado): mismo bloque, a continuación de la Banda (la lista NBT llega justo a 255 slots). */
    public static final int CALZADO_ACTIVO = BANDA_PORPRENDA_INICIO + PORPRENDA_TOTAL;
    public static final int CALZADO_PINES_INICIO = CALZADO_ACTIVO + 1;
    public static final int CALZADO_PORPRENDA_INICIO = CALZADO_PINES_INICIO + PINES_POR_CATEGORIA;
    /**
     * Categoría Delantal (2026-10-11): mismo bloque, a continuación del Calzado. Con ella la lista {@code pollera_items}
     * (las 5 categorías de antes, 255 slots) quedó llena: el Delantal va en una SEGUNDA lista NBT, {@code delantal_items}
     * (ver {@link #INICIO_LISTA_2}).
     */
    public static final int DELANTAL_ACTIVO = CALZADO_PORPRENDA_INICIO + PORPRENDA_TOTAL;
    public static final int DELANTAL_PINES_INICIO = DELANTAL_ACTIVO + 1;
    public static final int DELANTAL_PORPRENDA_INICIO = DELANTAL_PINES_INICIO + PINES_POR_CATEGORIA;
    public static final int TAMANO = DELANTAL_PORPRENDA_INICIO + PORPRENDA_TOTAL;
    /** Primer slot que ya no entra en {@code pollera_items} (el índice de {@code Inventories} es un byte). */
    public static final int INICIO_LISTA_2 = TAMANO_VIEJO + 255;
    /** Pines "lógicos": 12 por categoría, p = categoría*12 + i (ver {@link #pinSlot}). */
    public static final int PINES_LOGICOS = PINES_POR_CATEGORIA * 10;

    /** Primer slot de pin de una categoría de las del final (Pollera, Capa); -1 para las 4 de siempre. */
    private static int pinesInicioExtra(Categoria cat) {
        return switch (cat) {
            case POLLERA -> POLLERA_PINES_INICIO;
            case CAPA -> CAPA_PINES_INICIO;
            case SOMBRERO -> SOMBRERO_PINES_INICIO;
            case BANDA -> BANDA_PINES_INICIO;
            case CALZADO -> CALZADO_PINES_INICIO;
            case DELANTAL -> DELANTAL_PINES_INICIO;
            default -> -1;
        };
    }

    /** Slot real del pin lógico {@code p}. */
    public static int pinSlot(int p) {
        if (p < PINES_TAMANO) return PINES_INICIO + p;
        return pinesInicioExtra(Categoria.values()[p / PINES_POR_CATEGORIA]) + p % PINES_POR_CATEGORIA;
    }

    /** Pin lógico de un slot real, o -1 si no es un pin. */
    public static int pinDeSlot(int slot) {
        if (slot >= PINES_INICIO && slot < PINES_FIN) return slot - PINES_INICIO;
        for (Categoria cat : new Categoria[]{Categoria.POLLERA, Categoria.CAPA, Categoria.SOMBRERO, Categoria.BANDA, Categoria.CALZADO, Categoria.DELANTAL}) {
            int inicio = pinesInicioExtra(cat);
            if (slot >= inicio && slot < inicio + PINES_POR_CATEGORIA) {
                return cat.ordinal() * PINES_POR_CATEGORIA + (slot - inicio);
            }
        }
        return -1;
    }

    /** Slot real del Activo de {@code cat}. */
    public static int activoSlot(Categoria cat) {
        return switch (cat) {
            case POLLERA -> POLLERA_ACTIVO;
            case CAPA -> CAPA_ACTIVO;
            case SOMBRERO -> SOMBRERO_ACTIVO;
            case BANDA -> BANDA_ACTIVO;
            case CALZADO -> CALZADO_ACTIVO;
            case DELANTAL -> DELANTAL_ACTIVO;
            default -> ACTIVO_INICIO + cat.ordinal();
        };
    }

    /** Qué prenda está configurando ahora el jugador — cicla con {@link #BTN_CATEGORIA}. */
    /** POLLERA al final (2026-09-29): sus slots van al final del inventario, ver {@link #POLLERA_ACTIVO}. */
    public enum Categoria { REMERA, PANTALON, MEDIAS, CALIENTABRAZOS, POLLERA, CAPA, SOMBRERO, BANDA, CALZADO, DELANTAL }

    /** Qué hace cada pin del esquema (2026-09-26, pines para las 4 prendas). NINGUNO = pin sin usar en esa categoría. */
    public enum Rol { CUELLO, MAT1, MAT2, MAT3, MANGA_IZQ, MANGA_DER, CALCE, TORSO, TIRO, BOTA_IZQ, BOTA_DER,
        SUP_IZQ, SUP_DER, INF_IZQ, INF_DER,
        PERS_IZQ1, PERS_IZQ2, PERS_IZQ3, PERS_DER1, PERS_DER2, PERS_DER3, NINGUNO,
        /** Pollera (2026-09-29): molde de forma y molde de rango para el largo. */
        FORMA_POLLERA, LARGO_POLLERA,
        /** Volados de la pollera (2026-10-05): del borde de abajo y de toda la pollera (MoldeVoladoItem). */
        VOLADO_INFERIOR, VOLADO_TOTAL,
        /** Cintura de la pollera (2026-10-08): cajón del largo, acepta moldes de rango. */
        CINTURA_POLLERA,
        /** Ruedo de cada borde libre (2026-10-07, MoldeRuedoItem): torso, puño de cada manga y ruedo de la pollera. */
        RUEDO_TORSO, RUEDO_PUNO_IZQ, RUEDO_PUNO_DER, RUEDO_POLLERA,
        /** Capa (2026-09-29): largo (molde de rango), ruedo, capucha y cuello alto (MoldeCapaItem). */
        LARGO_CAPA, RUEDO_CAPA, CAPUCHA_CAPA, CUELLO_CAPA,
        /** Sombrero (2026-10-05): ala y punta (MoldeSombreroItem). */
        ALA_SOMBRERO, PUNTA_SOMBRERO,
        /** Banda (2026-10-05): zona, ancho y herraje (MoldeBandaItem). */
        ZONA_BANDA, ANCHO_BANDA, HERRAJE_BANDA,
        /** Chaqueta (2026-10-07): frente, capucha y solapa (MoldeChaquetaItem). */
        FRENTE_CHAQUETA, CAPUCHA_CHAQUETA, SOLAPA_CHAQUETA,
        /** Ruedo de la botamanga, del borde de arriba y del de abajo de medias y calientabrazos (2026-10-08, tanda 2). */
        RUEDO_BOTA_IZQ, RUEDO_BOTA_DER, RUEDO_SUP_IZQ, RUEDO_SUP_DER, RUEDO_INF_IZQ, RUEDO_INF_DER,
        /** Borcegos (2026-10-08): altura de la caña, suela y botamanga (MoldeBorcegoItem). */
        CANA_BORCEGO_IZQ, CANA_BORCEGO_DER, SUELA_BORCEGO_IZQ, SUELA_BORCEGO_DER,
        BOTAMANGA_BORCEGO_IZQ, BOTAMANGA_BORCEGO_DER,
        /** Faldón de malla del Top (2026-10-11, "las chaquetas de malla"): largo por molde de rango. */
        FALDON_TOP,
        /** Delantal (2026-10-11): forma, largo, inicio (cajón del largo), esquinas, volado y ruedo (cajón del volado). */
        FORMA_DELANTAL, LARGO_DELANTAL, INICIO_DELANTAL, ESQUINAS_DELANTAL, VOLADO_DELANTAL, RUEDO_DELANTAL,
        /** Volados de los costados y del borde de arriba del delantal (2026-10-11). */
        VOLADO_LATERAL_DELANTAL, VOLADO_SUPERIOR_DELANTAL,
        /** Cobertura del delantal y embozo (largo por rango) y borde de la capa (2026-10-11). */
        COBERTURA_DELANTAL, EMBOZO_CAPA, BORDE_EMBOZO_CAPA,
        /** Tirantes del delantal (2026-10-11). */
        TIRANTES_DELANTAL,
        /** Planta del ala plana del sombrero (2026-10-14, "un molde mas para cuadrada o malla"): cuadrada o redonda. */
        PLANTA_SOMBRERO,
        /** Estilo del sombrero (2026-10-14): malla o cuboides. */
        ESTILO_SOMBRERO,
        /** Grosor del ala y banda del sombrero (2026-10-14). */
        GROSOR_SOMBRERO, BANDA_SOMBRERO,
        /** Punta, cierre y detalle del calzado, por pie (2026-10-14). */
        PUNTA_BORCEGO_IZQ, PUNTA_BORCEGO_DER, CIERRE_BORCEGO_IZQ, CIERRE_BORCEGO_DER,
        DETALLE_BORCEGO_IZQ, DETALLE_BORCEGO_DER,
        /** Cuboides o malla del calzado, los dos pies (2026-10-14, "el mismo molde cuboide"). */
        ESTILO_CALZADO }

    /** Rol de cada uno de los 8 pines por categoría — MISMO orden que {@code ModeladoScreenHandler#PIN_POS}. */
    public static final Rol[][] ROLES = rellenarRoles(new Rol[][]{
            {Rol.CUELLO, Rol.MAT1, Rol.NINGUNO, Rol.MANGA_IZQ, Rol.FALDON_TOP, Rol.MANGA_DER, Rol.CALCE, Rol.TORSO,
                    Rol.RUEDO_TORSO, Rol.RUEDO_PUNO_IZQ, Rol.RUEDO_PUNO_DER,
                    // El Top (2026-10-07, "un solo top"): frente, capucha y solapa también en la remera.
                    Rol.FRENTE_CHAQUETA, Rol.CAPUCHA_CHAQUETA, Rol.SOLAPA_CHAQUETA},
            {Rol.TIRO, Rol.MAT1, Rol.NINGUNO, Rol.NINGUNO, Rol.CALCE, Rol.BOTA_IZQ, Rol.BOTA_DER, Rol.RUEDO_BOTA_IZQ,
                    Rol.RUEDO_BOTA_DER, Rol.NINGUNO, Rol.NINGUNO, Rol.NINGUNO},
            {Rol.SUP_IZQ, Rol.SUP_DER, Rol.INF_IZQ, Rol.INF_DER, Rol.CALCE, Rol.PERS_IZQ1, Rol.RUEDO_SUP_IZQ,
                    Rol.RUEDO_INF_IZQ, Rol.PERS_DER1, Rol.RUEDO_SUP_DER, Rol.RUEDO_INF_DER, Rol.NINGUNO},
            {Rol.SUP_IZQ, Rol.SUP_DER, Rol.INF_IZQ, Rol.INF_DER, Rol.CALCE, Rol.PERS_IZQ1, Rol.RUEDO_SUP_IZQ,
                    Rol.RUEDO_INF_IZQ, Rol.PERS_DER1, Rol.RUEDO_SUP_DER, Rol.RUEDO_INF_DER, Rol.NINGUNO},
            // Pollera (2026-09-29): Forma, Largo, Calce y los 3 materiales.
            {Rol.FORMA_POLLERA, Rol.LARGO_POLLERA, Rol.CALCE, Rol.MAT1, Rol.CINTURA_POLLERA, Rol.NINGUNO,
                    Rol.VOLADO_INFERIOR, Rol.VOLADO_TOTAL, Rol.RUEDO_POLLERA},
            // Capa (2026-09-29, "podemos agregar todo eso como patrones de corte?"):
            // Largo, Ruedo, Capucha, Cuello y los 3 materiales.
            {Rol.LARGO_CAPA, Rol.RUEDO_CAPA, Rol.CAPUCHA_CAPA, Rol.CUELLO_CAPA, Rol.MAT1, Rol.EMBOZO_CAPA, Rol.BORDE_EMBOZO_CAPA,
                    Rol.NINGUNO, Rol.NINGUNO, Rol.NINGUNO, Rol.NINGUNO, Rol.NINGUNO},
            // Sombrero (2026-10-05): Ala y Punta.
            {Rol.ALA_SOMBRERO, Rol.PUNTA_SOMBRERO, Rol.PLANTA_SOMBRERO, Rol.ESTILO_SOMBRERO, Rol.GROSOR_SOMBRERO, Rol.BANDA_SOMBRERO,
                    Rol.NINGUNO, Rol.NINGUNO, Rol.NINGUNO, Rol.NINGUNO, Rol.NINGUNO, Rol.NINGUNO},
            // Banda (2026-10-05): Zona, Ancho y Herraje.
            {Rol.ZONA_BANDA, Rol.ANCHO_BANDA, Rol.HERRAJE_BANDA, Rol.NINGUNO, Rol.NINGUNO, Rol.NINGUNO,
                    Rol.NINGUNO, Rol.NINGUNO, Rol.NINGUNO, Rol.NINGUNO, Rol.NINGUNO, Rol.NINGUNO},
            // Borcegos (2026-10-08): Caña, Suela y Botamanga.
            // (2026-10-11, "independizar derecho e izquierdo": un pin por pie; con la simetría activa mandan los dos.)
            {Rol.CANA_BORCEGO_IZQ, Rol.CANA_BORCEGO_DER, Rol.SUELA_BORCEGO_IZQ, Rol.SUELA_BORCEGO_DER,
                    Rol.BOTAMANGA_BORCEGO_IZQ, Rol.BOTAMANGA_BORCEGO_DER, Rol.PUNTA_BORCEGO_IZQ, Rol.PUNTA_BORCEGO_DER,
                    Rol.CIERRE_BORCEGO_IZQ, Rol.CIERRE_BORCEGO_DER, Rol.DETALLE_BORCEGO_IZQ, Rol.DETALLE_BORCEGO_DER, Rol.ESTILO_CALZADO},
            // Delantal (2026-10-11): Forma, Largo (con el Inicio en su cajón), Calce, Trama, Volado (con el Ruedo en su
            // cajón) y Esquinas — sobre esquema_delantal.png (provisoriamente el de la pollera).
            {Rol.FORMA_DELANTAL, Rol.LARGO_DELANTAL, Rol.CALCE, Rol.MAT1, Rol.INICIO_DELANTAL, Rol.TIRANTES_DELANTAL,
                    Rol.VOLADO_DELANTAL, Rol.ESQUINAS_DELANTAL, Rol.RUEDO_DELANTAL,
                    Rol.VOLADO_LATERAL_DELANTAL, Rol.VOLADO_SUPERIOR_DELANTAL, Rol.COBERTURA_DELANTAL}
    });

    private static Rol[][] rellenarRoles(Rol[][] filas) {
        Rol[][] r = new Rol[filas.length][PINES_POR_CATEGORIA];
        for (int c = 0; c < filas.length; c++) {
            for (int i = 0; i < PINES_POR_CATEGORIA; i++) r[c][i] = i < filas[c].length ? filas[c][i] : Rol.NINGUNO;
        }
        return r;
    }

    /** 15s a 20 ticks — a pedido (2026-09-21, "que cada maquina tome su tiempo... 15 la modeladora"). */
    public static final int TICKS_PROCESO = 300;

    /**
     * La tijera (2026-10-05, "a la modeladora le vamos a poner un insumo... una tijera... una barra verde su durabilidad"):
     * un casillero INVISIBLE después del último del inventario. Son las tijeras de vanilla; cada Modelar gasta 1 uso y
     * sin tijera no arranca. Se carga a mano sobre el bloque, con shift-click en la GUI o por tolva/cinta; la
     * Modeladora creativa ni la necesita ni la gasta.
     */
    public static final int SLOT_TIJERA = TAMANO;
    private ItemStack tijera = ItemStack.EMPTY;

    public ItemStack tijera() { return tijera; }

    /** ¿Hay con qué cortar? La creativa siempre. */
    public boolean tijeraOk() {
        return com.modamod.util.MaquinaCreativa.es(this) || (!tijera.isEmpty() && tijera.isOf(net.minecraft.item.Items.SHEARS));
    }

    /** Lo que le queda a la tijera, de 0 a 1 (para la barra verde de la pantallita). */
    public float durabilidadTijera() {
        if (tijera.isEmpty() || tijera.getMaxDamage() <= 0) return tijera.isEmpty() ? 0f : 1f;
        return 1f - tijera.getDamage() / (float) tijera.getMaxDamage();
    }

    /** Pone una tijera (de a una) si el casillero está libre; le saca una al {@code pila}. */
    public boolean cargarTijera(ItemStack pila) {
        if (!tijera.isEmpty() || !pila.isOf(net.minecraft.item.Items.SHEARS)) return false;
        tijera = pila.copyWithCount(1);
        pila.decrement(1);
        sincronizar();
        return true;
    }

    /** La tijera sale del casillero (para devolverla al jugador). */
    public ItemStack sacarTijera() {
        ItemStack r = tijera;
        tijera = ItemStack.EMPTY;
        if (!r.isEmpty()) sincronizar();
        return r;
    }

    private void avisarSinTijera() {
        if (world == null || world.isClient) return;
        for (var jugador : world.getPlayers()) {
            if (jugador.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 100.0) {
                jugador.sendMessage(net.minecraft.text.Text.translatable("modamod.modelado.sin_tijera"), true);
            }
        }
    }

    public enum Estado { REPOSO, PROCESANDO, LISTO }

    /**
     * A qué anclaje apunta el borrador de un eje de EXTREMIDAD (pantalón-
     * pierna, medias, calientabrazos) al fijar — ver
     * docs sesión "cobertura de extremidad: dos anclajes que se
     * intersecan". No aplica a remera (torso, sigue entero) ni a tiro
     * (torso, un solo valor).
     */
    public enum Anclaje { SUPERIOR, INFERIOR }

    private static final Logger LOG = LoggerFactory.getLogger("modamod-modelado");

    // Modelo "garment_shaper" v2 (bloques de moda 2.0, ver HUESOS.md): dos
    // controladores independientes, no un solo reposo/trabajo — "trabajo"
    // mueve guillotine/needle/cargo (solo mientras PROCESANDO), "en_marcha"
    // hace girar el ventilador (mientras la máquina está encendida, prenda o
    // no prenda adentro — es la señal de "tiene corriente").
    private static final RawAnimation TRABAJO_ANIM = RawAnimation.begin().thenLoop("animation.garment_shaper.trabajo");
    private static final RawAnimation EN_MARCHA_ANIM = RawAnimation.begin().thenLoop("animation.garment_shaper.en_marcha");

    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(TAMANO, ItemStack.EMPTY);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private boolean encendida = false;
    /** Cuántos jugadores tienen la GUI abierta ahora (solo servidor, no se guarda). */
    private int guiAbiertas = 0;
    private Estado estado = Estado.REPOSO;
    private int progreso = 0;

    /**
     * Fijadas por categoría — a pedido (2026-09-16): "quiero que cada
     * prenda tenga sus 8 slots porque hay moldes que sirven para varias
     * prendas". Antes era UNA sola lista compartida entre las 4
     * categorías (tope 8 total): armar una configuración completa que
     * cubriera remera+pantalón+medias+calientabrazos a la vez —el caso de
     * uso real, "una vez seteada la máquina sabe qué hacer con lo que le
     * entre"— se quedaba sin lugar (remera sola ya puede pedir 3 fijadas
     * — largo/manga/cuello — y pantalón otras 2 —superior/inferior—).
     * Ahora cada {@link Categoria} tiene su propia lista de hasta 8: 32
     * fijadas en total, ninguna categoría le saca lugar a otra.
     * {@link #procesar} las aplica TODAS (las 4 listas), filtradas por
     * tipo de ítem como siempre — así que da igual qué prenda física
     * entre en el slot {@link #PRENDA}, cada una recibe la config de SU
     * categoría automáticamente sin tocar nada más.
     */
    private final Map<Categoria, List<ComboCorte>> fijadasPorCategoria = new EnumMap<>(Categoria.class);
    {
        for (Categoria c : Categoria.values()) fijadasPorCategoria.put(c, new ArrayList<>());
    }

    /**
     * Diseños guardados por categoría (2026-09-27, "que los slots donde se
     * fijaban sirvan para guardar el diseño completo de todos los pines
     * como 1 2 etc"): cada uno es una copia con nombre de la lista de
     * fijadas de ese momento — reemplaza la fila vieja de "fijadas
     * individuales con botón de quitar" (redundante: el corte del pin ya se
     * ve en el propio esquema, y la chincheta ya lo quita).
     */
    public static final int DISENOS_MAXIMO = 3;
    /** Carga el ultimo diseno usado en la categoria actual (boton "Ultimo", 2026-10-12). */
    public static final int BTN_CARGAR_ULTIMO = 13;
    /** Borrar lo fijado de la categoría actual / de todas (2026-10-14, "un boton de delete... y un delete all"); los diseños con nombre no se tocan. */
    public static final int BTN_BORRAR = 14, BTN_BORRAR_TODAS = 15;
    /**
     * "Ultimo" (2026-10-12, tanda 3, "Ultimo, 3 disenos con nombre y un +"): las fijadas con las que se corto la ultima
     * prenda de cada categoria, guardadas solas al empezar a modelar. Es lo que se recarga con el boton.
     */
    private final Map<Categoria, List<ComboCorte>> ultimoPorCategoria = new EnumMap<>(Categoria.class);
    public boolean hayUltimo() { List<ComboCorte> l = ultimoPorCategoria.get(categoria); return l != null && !l.isEmpty(); }
    public record DisenoGuardado(String nombre, List<ComboCorte> combos) {}
    private final Map<Categoria, List<DisenoGuardado>> disenosPorCategoria = new EnumMap<>(Categoria.class);
    {
        for (Categoria c : Categoria.values()) disenosPorCategoria.put(c, new ArrayList<>());
    }

    // ── borrador: valor actual de cada eje que el molde ACTIVO permite tocar ─
    private Variante.Largo remeraLargo = Variante.Largo.NORMAL;
    private Variante.Manga remeraManga = Variante.Manga.SIN;
    private Variante.Cuello remeraCuello = Variante.Cuello.CERRADO;
    private Variante.Manga calientabrazosCobertura = Variante.Manga.LARGA;
    /** Anclaje y lado del borrador — solo importan para los 3 ejes de extremidad. */
    private Anclaje anclaje = Anclaje.SUPERIOR;
    private Lado ladoBorrador = Lado.AMBAS;
    /** Qué categoría se está configurando ahora — ver {@link #BTN_CATEGORIA}. */
    private Categoria categoria = Categoria.REMERA;
    /**
     * Simetría de manga (2026-09-24, "agreguemos un boton de simetria") —
     * true (default): soltar un molde en CUALQUIERA de los dos pines de
     * manga del esquema pinea las DOS mangas iguales (Lado.AMBAS). false:
     * cada pin pinea solo su lado.
     */
    private boolean remeraSimetria = true;

    public ModeladoBlockEntity(BlockPos pos, net.minecraft.block.BlockState state) {
        super(ModeladoMod.MODELADO_BLOCK_ENTITY, pos, state);
    }

    // ── slots por categoría ─────────────────────────────────────────────

    public Categoria categoria() { return categoria; }

    /** El slot Activo dedicado de esa categoría (de los 4 en {@link #ACTIVO_INICIO}). */
    public ItemStack activoStack(Categoria cat) { return items.get(activoSlot(cat)); }

    private ItemStack activoActual() { return activoStack(categoria); }

    public boolean activoVacio() { return activoActual().isEmpty(); }

    /** Primer slot del banco de storage-por-prenda de esa categoría (12 slots desde ahí). */
    public static int porPrendaInicio(Categoria cat) { return PORPRENDA_INICIO + cat.ordinal() * PORPRENDA_POR_CATEGORIA; }

    /** Índice de inventario del lugar {@code i} (0..{@link #PORPRENDA_TOTAL}-1) del banco de {@code cat}, en el orden de la pantalla. */
    public static int porPrendaSlot(Categoria cat, int i) {
        if (cat == Categoria.POLLERA) return POLLERA_PORPRENDA_INICIO + i;
        if (cat == Categoria.CAPA) return CAPA_PORPRENDA_INICIO + i;
        if (cat == Categoria.SOMBRERO) return SOMBRERO_PORPRENDA_INICIO + i;
        if (cat == Categoria.BANDA) return BANDA_PORPRENDA_INICIO + i;
        if (cat == Categoria.CALZADO) return CALZADO_PORPRENDA_INICIO + i;
        if (cat == Categoria.DELANTAL) return DELANTAL_PORPRENDA_INICIO + i;
        return i < PORPRENDA_POR_CATEGORIA ? porPrendaInicio(cat) + i
                : PORPRENDA_EXTRA_INICIO + cat.ordinal() * PORPRENDA_EXTRA_POR_CATEGORIA + (i - PORPRENDA_POR_CATEGORIA);
    }

    /**
     * Qué moldes acepta el Activo (o el storage-por-prenda) de esta
     * categoría — los moldes por-eje, cada uno atado a UNA categoría salvo el de manga (remera Y
     * calientabrazos comparten el mismo ítem físico — antes eso generaba
     * ambigüedad de a qué eje aplicaba; ahora la resuelve el slot en el que
     * lo pusiste, no el ítem).
     */
    public static boolean esMoldeDeCategoria(ItemStack stack, Categoria cat) {
        Item item = stack.getItem();
        // Molde y trama en blanco (2026-10-12): "quitar este rasgo", valen en cualquier categoría.
        if (item instanceof MoldeEnBlancoItem b && (b.tipo == MoldeEnBlancoItem.Tipo.MOLDE || b.tipo == MoldeEnBlancoItem.Tipo.TRAMA)) return true;
        // Molde de rango unificado (a pedido): sirve para las 4 categorías
        // de extremidad, cada una lo traduce a su propia escala (ver fijar()).
        if (item instanceof MoldeRangoItem) return true;
        // Molde de calce (a pedido): transversal a las 4, sin anclaje ni lado.
        if (item instanceof MoldeCalceItem) return true;
        // Molde de red (a pedido): mismo criterio que calce.
        if (item instanceof MoldeRedItem) return true;
        // Los patrones/tramas (ClothingPatternItem) ya NO sirven acá (2026-10-04, "patrones van en la estacion de
        // tintes no hacen nada en la modeladora"): ocupaban 7 casilleros del almacén general (32 compartidos para
        // 27 lugares) y no tienen nada que hacer en el corte.
        return switch (cat) {
            case REMERA -> item instanceof MoldeCuelloItem
                    || item instanceof MoldeChaquetaItem
                    || (item instanceof MoldeCapaItem k && k.tipo == MoldeCapaItem.Tipo.CON_CAPUCHA)
                    || (item instanceof MoldeItem m && m.eje == MoldeItem.Eje.MANGA)
                    || (item instanceof MoldeRuedoItem r && r.valor.valeEnTela());
            case PANTALON, MEDIAS -> false;
            case CALIENTABRAZOS -> item instanceof MoldeItem m && m.eje == MoldeItem.Eje.MANGA;
            case POLLERA -> item instanceof MoldePolleraItem || item instanceof MoldeVoladoItem
                    || (item instanceof MoldeRuedoItem q && !q.valor.esDeCapa());
            case CAPA -> item instanceof MoldeCapaItem || (item instanceof MoldeRuedoItem q && q.valor.esDeCapa());
            case SOMBRERO -> item instanceof MoldeSombreroItem;
            case BANDA -> item instanceof MoldeBandaItem;
            case CALZADO -> item instanceof MoldeBorcegoItem || (item instanceof MoldeSombreroItem s && s.tipo.esEstilo());
            case DELANTAL -> item instanceof MoldeFormaDelantalItem || item instanceof MoldeTirantesDelantalItem
                    || item instanceof MoldeCoberturaDelantalItem || item instanceof MoldeVoladoItem
                    || (item instanceof MoldeRuedoItem q && q.valor != com.modamod.item.Ruedo.COLA);
        };
    }

    // ── categorización de moldes para storage (2026-09-24, "en los slots
    // de moldes de remera solo entran moldes que sirvan solo para la
    // remera... en el almacen de prendas van los moldes que sirven para
    // mas de una prenda") — separado de esMoldeDeCategoria de arriba, que
    // sigue siendo "¿sirve ACÁ?" (usado por Activo, sin cambios); esto es
    // "¿sirve SOLO acá, o en varias?", para decidir en QUÉ banco guardarlo.

    private static java.util.EnumSet<Categoria> categoriasDe(ItemStack stack) {
        var set = java.util.EnumSet.noneOf(Categoria.class);
        for (Categoria c : Categoria.values()) if (esMoldeDeCategoria(stack, c)) set.add(c);
        return set;
    }

    /** True si este molde sirve para {@code cat} y NINGUNA otra categoría. */
    public static boolean esMoldeExclusivoDe(ItemStack stack, Categoria cat) {
        var cats = categoriasDe(stack);
        return cats.size() == 1 && cats.contains(cat);
    }

    /** True si este molde sirve para 2 o más categorías — va al almacén general, no al banco por-prenda. */
    public static boolean esMoldeCompartido(ItemStack stack) {
        return categoriasDe(stack).size() >= 2 && !seGuardaEnBanco(stack.getItem());
    }

    /**
     * Los moldes de ruedo sirven en varias categorías pero NO van al almacén general (que ya está casi lleno): cada
     * banco "Moldes de <prenda>" guarda su copia (2026-10-07).
     */
    /** Los moldes que sirven en varias categorías pero no van al almacén general (que está lleno): ruedo, volado y los ruedos de la capa. */
    private static boolean seGuardaEnBanco(Item item) {
        return item instanceof MoldeRuedoItem || item instanceof MoldeVoladoItem || item instanceof MoldeCapaItem;
    }

    public static boolean sirveEnBanco(ItemStack stack, Categoria cat) {
        return esMoldeExclusivoDe(stack, cat) || (seGuardaEnBanco(stack.getItem()) && esMoldeDeCategoria(stack, cat));
    }

    /**
     * La prenda TERMINADA de esa categoría, o un molde EXCLUSIVO de esa
     * categoría — lo que puebla el banco "Moldes de {@code <Categoria>}".
     * Null si es una prenda de otra categoría (no vale acá), un molde
     * compartido (va al almacén general) o ninguna de las dos cosas.
     */
    @Nullable
    public static Categoria categoriaExclusivaDe(ItemStack stack) {
        Categoria prenda = categoriaDe(stack);
        if (prenda != null) return prenda;
        var cats = categoriasDe(stack);
        return cats.size() == 1 ? cats.iterator().next() : null;
    }

    // ── traducción de rango abstracto a la escala real de cada prenda ───
    //
    // Los 7 niveles (2026-10-04, "agreguemos un molde y medida mas"; "los 6 niveles marquen solo la altura del
    // brazo o de la pierna de arriba hacia abajo"): el nivel k es una LÍNEA a 2k filas desde arriba (hombro,
    // cintura, cadera). El corte de abajo de una prenda (donde TERMINA la tela) guarda esa altura tal cual
    // (anclaje Superior: 0..filas desde arriba); el corte de arriba (donde EMPIEZA) guarda 12 − 2k (anclaje
    // Inferior, medido desde abajo). Nivel 0 = la línea más alta, nivel 6 = la más baja.

    private static Botamanga botamangaDeFilas(int filas) {
        for (Botamanga b : Botamanga.values()) if (b.filas == filas) return b;
        return Botamanga.PIE;
    }

    private static Variante.Manga mangaDeFilas(int filas) {
        for (Variante.Manga m : Variante.Manga.values()) if (m.filas == filas) return m;
        return Variante.Manga.LARGA;
    }

    /** Pantalón: dónde termina la pierna, desde la cintura (anclaje Superior; 2026-10-04, "siempre superior"). */
    private static Botamanga pantalonDeRango(MoldeRangoItem.Rango r) {
        return botamangaDeFilas(2 * r.nivel());
    }

    /** Medias: dónde TERMINAN (corte de abajo). */
    private static Botamanga mediasDeRango(MoldeRangoItem.Rango r) {
        return botamangaDeFilas(2 * r.nivel());
    }

    /** Medias: dónde EMPIEZAN (corte de arriba), medido desde abajo. */
    private static Botamanga mediasInicioDeRango(MoldeRangoItem.Rango r) {
        return botamangaDeFilas(12 - 2 * r.nivel());
    }

    /** Calientabrazos: dónde TERMINAN. */
    private static Variante.Manga mangaCalientabrazosDeRango(MoldeRangoItem.Rango r) {
        return mangaDeFilas(2 * r.nivel());
    }

    /** Calientabrazos: dónde EMPIEZAN, medido desde abajo. */
    private static Variante.Manga mangaCalientabrazosInicioDeRango(MoldeRangoItem.Rango r) {
        return mangaDeFilas(12 - 2 * r.nivel());
    }

    /**
     * Manga de remera: dónde termina, del hombro hacia abajo. Los 7 niveles son los 7 valores de
     * {@link Variante.Manga} (sin, mínima, corta, media, tres cuartos, siete octavos, larga). Sin
     * {@code private} a propósito: {@link ModeladoScreenHandler} la reusa para el slot del esquema visual.
     */
    static Variante.Manga mangaRemeraDeRango(MoldeRangoItem.Rango r) {
        return mangaDeFilas(2 * r.nivel());
    }

    /** Largo de remera por nivel: 3, 5, 7, 9, 10, 11 y 12 filas desde el hombro (2026-10-04). */
    private static Variante.Largo largoDeNivel(MoldeRangoItem.Rango r) {
        return switch (r.nivel()) {
            case 0 -> Variante.Largo.TOP;
            case 1 -> Variante.Largo.CROP;
            case 2 -> Variante.Largo.CORTO;
            case 3 -> Variante.Largo.NORMAL;
            case 4 -> Variante.Largo.CADERA_ALTA;
            case 5 -> Variante.Largo.CADERA;
            default -> Variante.Largo.LARGO;
        };
    }

    /**
     * Tiro por nivel (2026-10-04, "tiro... extendamos hasta los hombros"): la línea de la cintura está a 2k filas
     * desde el hombro, así que la banda sube 12 − 2k filas: hombros, pecho, bajo el pecho, muy alto, alto (el
     * LARGO de siempre), medio (el MEDIO de siempre) y a la cadera.
     */
    private static PantalonTiro tiroDeNivel(MoldeRangoItem.Rango r) {
        return switch (r.nivel()) {
            case 0 -> PantalonTiro.HOMBROS;
            case 1 -> PantalonTiro.PECHO;
            case 2 -> PantalonTiro.BAJO_PECHO;
            case 3 -> PantalonTiro.MUY_ALTO;
            case 4 -> PantalonTiro.LARGO;
            case 5 -> PantalonTiro.MEDIO;
            default -> PantalonTiro.CADERA;
        };
    }

    // ── getters de estado, para la GUI ──────────────────────────────────

    public boolean encendida() { return encendida; }

    public void guiAbierta() {
        guiAbiertas++;
        // Una Modeladora creativa puesta antes de que existieran los moldes nuevos completa su bolsa al abrirla
        // (2026-10-14, "no llego a cambiar el molde"): sumar los que faltan no repite los que ya tiene.
        if (com.modamod.util.MaquinaCreativa.es(this)) cargarCreativa();
    }
    public void guiCerrada() { guiAbiertas = Math.max(0, guiAbiertas - 1); }

    private boolean hayFijadas() {
        for (List<ComboCorte> l : fijadasPorCategoria.values()) if (!l.isEmpty()) return true;
        return false;
    }

    /** A qué categoría pertenece la prenda cargada AHORA (null si no hay nada) — para el hueso "REMERA" de {@link ModeladoGeoModel}. */
    @Nullable
    /** La prenda que se ve arriba de la máquina: la de la Entrada o, ya terminada, la de la Salida. */
    public ItemStack prendaVisible() {
        return !items.get(PRENDA).isEmpty() ? items.get(PRENDA) : items.get(SALIDA);
    }

    public Categoria categoriaPrenda() {
        // La de la ranura PRENDA y, cuando la máquina la terminó y pasó a
        // SALIDA, la del resultado: la prenda se ve arriba hasta que se
        // retira (2026-09-26) y no solo mientras está sin procesar.
        Categoria cat = categoriaDe(items.get(PRENDA));
        return cat != null ? cat : categoriaDe(items.get(SALIDA));
    }

    /**
     * Sin botón de Encender en la GUI (a pedido): se prende sola al cerrar
     * la pantalla — ver {@code ModeladoScreenHandler#onClosed} — y se apaga
     * sola al terminar de procesar (ver {@link #tick}), así el jugador
     * puede volver a abrirla para retirar el resultado y cargar la
     * siguiente prenda sin un paso manual de más.
     */
    public void encenderAlCerrar() {
        // Sin esto prendía aunque la GUI se cerrara con el slot PRENDA
        // vacío (2026-09-22, "se esta encendiendo... sin que haya prenda
        // puesta eso esta mal") — el ventilador ("en_marcha") arrancaba
        // sin nada para procesar.
        if (encendida || items.get(PRENDA).isEmpty()) return;
        // Tampoco sin nada fijado o con la Salida ocupada (2026-09-29, "sin
        // ningun pin cuando salgo de la gui dice que la apague para
        // configurar y tiene particulas arriba"): tick() nunca pasaba a
        // PROCESANDO y quedaba "encendida" de adorno — ventilador y
        // partículas prendidos, config bloqueada, sin trabajar.
        if (!hayFijadas() || !items.get(SALIDA).isEmpty()) return;
        if (!tijeraOk()) { avisarSinTijera(); return; }     // sin tijera no corta
        encendida = true;
        // Mismo bug que las transiciones de tick() (ver #sincronizar): sin
        // esto, "en_marcha" (el ventilador) nunca se enteraba del lado
        // cliente de que la máquina se prendió sola al cerrar la GUI.
        sincronizar();
    }

    /** ¿Tiene sentido apretar Modelar? Apagada, con prenda en la Entrada, algo fijado y la Salida libre. */
    public boolean puedeModelar() {
        return !encendida && !items.get(PRENDA).isEmpty() && items.get(SALIDA).isEmpty() && hayFijadas() && tijeraOk();
    }

    public Estado estado() { return estado; }

    /** Trabajando de verdad: la GUI se cierra y no se toca (2026-10-08, "las 5 maquinas... cierran y bloquean gui"). */
    public boolean trabajando() { return estado == Estado.PROCESANDO; }

    /** El resultado esperando en la bandeja de salida (lo dibuja el hueso "cargo"). */
    public ItemStack salidaVisible() { return items.get(SALIDA); }
    public int progreso() { return progreso; }
    /** Las fijadas de la categoría ACTUAL — ver {@link #fijadasPorCategoria}. */
    public List<ComboCorte> fijadas() { return fijadasPorCategoria.get(categoria); }
    public Variante.Largo remeraLargo() { return remeraLargo; }
    public Variante.Manga remeraManga() { return remeraManga; }
    public Variante.Cuello remeraCuello() { return remeraCuello; }
    public Variante.Manga calientabrazosCobertura() { return calientabrazosCobertura; }
    public Anclaje anclaje() { return anclaje; }
    public Lado ladoBorrador() { return ladoBorrador; }

    // ── botones ──────────────────────────────────────────────────────

    public static final int BTN_FIJAR = 4;
    public static final int BTN_ENCENDER = 6;
    /** Cicla Superior/Inferior — solo importa para pantalón-largo/medias/calientabrazos. */
    public static final int BTN_ANCLAJE = 7;
    /** Cicla Izquierda/Derecha/Ambas — ídem. */
    public static final int BTN_LADO = 8;
    /** Cicla Remera -> Pantalón -> Medias -> Calientabrazos -> Remera. */
    public static final int BTN_CATEGORIA = 9;
    /** Togglea {@link #remeraSimetria} — solo el esquema de REMERA lo usa. */
    public static final int BTN_SIMETRIA = 10;
    /**
     * Botón Modelar (2026-09-28, "a modeladora le agreguemos el boton
     * modelar"), encima de la flecha como Teñir/Prensar: arranca con la
     * prenda de la Entrada sin tener que cerrar la GUI. Cerrarla sigue
     * arrancando igual (ver {@link #encenderAlCerrar}).
     */
    public static final int BTN_MODELAR = 11;
    /** Categoría anterior (flecha ‹ junto al botón de prenda, 2026-10-12). */
    public static final int BTN_CATEGORIA_ATRAS = 12;
    public static final int BTN_DESFIJAR_BASE = 100; // + índice en fijadas
    /** + índice de pin (0..7): la chincheta de cada slot de corte de remera. */
    public static final int BTN_PIN_BASE = 200;
    /** + índice de diseño (0..{@link #DISENOS_MAXIMO}-1): click directo en el casillero numerado carga ese diseño. */
    /** Abre/cierra el cajón del pin principal {@code i} de la categoría actual (2026-10-08). */
    public static final int BTN_CAJON_BASE = 500;
    public static final int BTN_CARGAR_DISENO_BASE = 400; // (era 300: chocaba con los pines desde la 9.ª categoría, 2026-10-07)
    /** + índice de diseño: click derecho en el casillero lo borra. */
    public static final int BTN_BORRAR_DISENO_BASE = 420;

    public boolean onButtonClick(int id) {
        LOG.info("onButtonClick(id={}) side={}", id, world != null && world.isClient ? "CLIENTE" : "SERVIDOR");
        if (id == BTN_LINEA) {
            linea = !linea;
            markDirty();
            if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
            return true;
        }
        if (encendida && id != BTN_ENCENDER) return false; // config bloqueada mientras produce
        boolean cambio;
        switch (id) {
            case BTN_FIJAR -> cambio = fijar();
            case BTN_ENCENDER -> {
                encendida = !encendida;
                cambio = true;
            }
            case BTN_ANCLAJE -> {
                anclaje = anclaje == Anclaje.SUPERIOR ? Anclaje.INFERIOR : Anclaje.SUPERIOR;
                cambio = true;
            }
            case BTN_LADO -> {
                ladoBorrador = siguiente(Lado.values(), ladoBorrador);
                cambio = true;
            }
            case BTN_CATEGORIA -> {
                categoria = siguiente(Categoria.values(), categoria);
                cambio = true;
            }
            case BTN_CATEGORIA_ATRAS -> {
                Categoria[] cs = Categoria.values();
                categoria = cs[(categoria.ordinal() + cs.length - 1) % cs.length];
                cambio = true;
            }
            case BTN_SIMETRIA -> {
                remeraSimetria = !remeraSimetria;
                reaplicarLados();
                cambio = true;
            }
            case BTN_BORRAR -> cambio = borrarFijadas(categoria);
            case BTN_BORRAR_TODAS -> {
                cambio = false;
                for (Categoria c : Categoria.values()) cambio |= borrarFijadas(c);
            }
            case BTN_MODELAR -> {
                if (!puedeModelar()) return false;
                encenderAlCerrar();
                cambio = true;
            }
            default -> {
                if (id >= BTN_CAJON_BASE && id < BTN_CAJON_BASE + PINES_POR_CATEGORIA) {
                    int pc = categoria.ordinal() * PINES_POR_CATEGORIA + (id - BTN_CAJON_BASE);
                    int sec = cajonDe(categoria, id - BTN_CAJON_BASE);
                    if (sec < 0) return false;
                    int ps = categoria.ordinal() * PINES_POR_CATEGORIA + sec;
                    // No se cierra con algo adentro: el molde quedaría invisible.
                    if (cajonAbierto(pc) && (pinFijado[ps] || !getStack(pinSlot(ps)).isEmpty())) return false;
                    cajones.flip(pc);
                    cambio = true;
                } else if (id >= BTN_PIN_BASE && id < BTN_PIN_BASE + PINES_LOGICOS) {
                    cambio = chinchetaPin(id - BTN_PIN_BASE);
                    if (!cambio) return false;
                } else if (id == BTN_CARGAR_ULTIMO) {
                    cambio = cargarUltimo();
                    if (!cambio) return false;
                } else if (id >= BTN_CARGAR_DISENO_BASE && id < BTN_CARGAR_DISENO_BASE + DISENOS_MAXIMO) {
                    cambio = cargarDiseno(id - BTN_CARGAR_DISENO_BASE);
                    if (!cambio) return false;
                } else if (id >= BTN_BORRAR_DISENO_BASE && id < BTN_BORRAR_DISENO_BASE + DISENOS_MAXIMO) {
                    cambio = borrarDiseno(id - BTN_BORRAR_DISENO_BASE);
                    if (!cambio) return false;
                } else if (id >= BTN_DESFIJAR_BASE && id < BTN_PIN_BASE) {
                    int idx = id - BTN_DESFIJAR_BASE;
                    List<ComboCorte> lista = fijadasPorCategoria.get(categoria);
                    if (idx < 0 || idx >= lista.size()) return false;
                    ComboCorte quitada = lista.remove(idx);
                    soltarPinDeFijada(categoria, quitada);
                    cambio = true;
                } else {
                    return false;
                }
            }
        }
        if (!cambio) return false;
        // markDirty() sola no manda el paquete al cliente (solo marca el
        // chunk para GUARDAR) — sin esto, la lista de fijadas y el resto
        // del estado nunca llegaban en vivo (bug jugando: "no hace pin",
        // "no lo desfija"). Mismo mecanismo que ya usa
        // SublimadoraBlockEntity para sus barras de tinta.
        markDirty();
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        return true;
    }

    /**
     * Al cambiar la simetría se vuelven a armar los cortes ya fijados de la categoría con el lado que les toca ahora
     * (2026-10-12, "si aplico un molde y despues pongo simetria no se actualiza, queda asimetrico"): antes cada corte
     * conservaba el lado con el que se fijó. Con simetría todos pasan a AMBAS; si dos casilleros pares tenían moldes
     * distintos, gana el que está más abajo en la lista de fijadas.
     */
    private void reaplicarLados() {
        List<ComboCorte> lista = fijadasPorCategoria.get(categoria);
        for (int i = 0; i < PINES_POR_CATEGORIA; i++) {
            int idx = categoria.ordinal() * PINES_POR_CATEGORIA + i;
            ComboCorte viejo = pinCombo[idx];
            if (viejo == null || viejo.iconoOrigen().isEmpty()) continue;
            Item item = net.minecraft.registry.Registries.ITEM.get(viejo.iconoOrigen().get());
            ComboCorte nuevo = comboDePin(categoria, i, new ItemStack(item), ladoDePin(categoria, i));
            if (nuevo == null) continue;
            int pos = lista.indexOf(viejo);
            if (pos >= 0) lista.set(pos, nuevo);
            pinCombo[idx] = nuevo;
        }
    }

    private static <T> T siguiente(T[] valores, T actual) {
        int i = 0;
        for (; i < valores.length; i++) if (valores[i] == actual) break;
        return valores[(i + 1) % valores.length];
    }

    /**
     * Junta el borrador de la categoría actual (según qué molde hay en su
     * slot Activo dedicado) en un ComboCorte y lo agrega a la lista fijada.
     * Un solo botón FIJAR para las 4 categorías: cuál molde vale para cuál
     * eje ya no lo decide el TIPO de ítem en ambigüedad (antes MOLDE_MANGA
     * servía a la vez para remera y calientabrazos) sino en QUÉ slot
     * Activo lo pusiste, uno por categoría.
     */
    private boolean fijar() {
        ItemStack activo = activoActual();
        LOG.info("fijar(): categoria={} anclaje={} lado={} activo={}",
                categoria, anclaje, ladoBorrador, activo.isEmpty() ? "VACIO" : activo.getItem());
        if (activo.isEmpty()) return false;
        // A pedido (2026-09-16): la GUI (ModeladoScreen) muestra el ícono
        // del molde que produjo cada fijada en vez de un código de texto
        // — se guarda ACÁ, una sola vez, y se le pega a cualquier combo
        // que salga de este método (ver ComboCorte#iconoOrigen).
        Identifier icono = net.minecraft.registry.Registries.ITEM.getId(activo.getItem());
        // Calce: transversal a las 4 categorías, sin anclaje ni lado — se
        // resuelve ACÁ, antes del switch por categoría, en vez de
        // duplicarlo en las 4 ramas.
        if (activo.getItem() instanceof MoldeCalceItem m) {
            return agregarFijada(ComboCorte.calce(m.valor).conIcono(icono));
        }
        // Red: mismo criterio que calce — transversal, antes del switch por categoría.
        if (activo.getItem() instanceof MoldeRedItem m) {
            return agregarFijada(ComboCorte.red(m.valor).conIcono(icono));
        }
        ComboCorte combo = switch (categoria) {
            case REMERA -> {
                // Cada eje es una fijada INDEPENDIENTE (largo/manga/cuello
                // por separado) — ver PrendaModelado#aplicar: cada una
                // parchea solo su eje sobre el Variante actual, no pisa a
                // las demás.
                if (activo.getItem() instanceof MoldeRangoItem m) yield ComboCorte.remeraManga(mangaRemeraDeRango(m.rango));
                if (activo.getItem() instanceof MoldeCuelloItem m) yield ComboCorte.remeraCuello(m.valor);
                ComboCorte chaqueta = comboDeMoldeChaqueta(activo.getItem());
                yield chaqueta == null ? ComboCorte.VACIO : chaqueta;
            }
            case PANTALON -> {
                // Solo el corte INFERIOR (2026-09-23, "pantalones se fija
                // solo el corte inferior") — el pantalón perdió el anclaje
                // Superior, ya no mira "anclaje" para decidir a cuál de
                // los dos escribir.
                if (activo.getItem() instanceof MoldeRangoItem m) yield ComboCorte.pantalonSuperior(pantalonDeRango(m.rango), ladoBorrador);
                yield ComboCorte.VACIO;
            }
            case MEDIAS -> {
                if (!(activo.getItem() instanceof MoldeRangoItem m)) yield ComboCorte.VACIO;
                yield anclaje == Anclaje.SUPERIOR
                        ? ComboCorte.mediasSuperior(mediasDeRango(m.rango), ladoBorrador)
                        : ComboCorte.mediasInferior(mediasInicioDeRango(m.rango), ladoBorrador);
            }
            case CALIENTABRAZOS -> {
                Variante.Manga fin = activo.getItem() instanceof MoldeRangoItem m ? mangaCalientabrazosDeRango(m.rango) : calientabrazosCobertura;
                Variante.Manga inicio = activo.getItem() instanceof MoldeRangoItem m ? mangaCalientabrazosInicioDeRango(m.rango) : calientabrazosCobertura;
                yield anclaje == Anclaje.SUPERIOR
                        ? ComboCorte.calientabrazosSuperior(fin, ladoBorrador)
                        : ComboCorte.calientabrazosInferior(inicio, ladoBorrador);
            }
            case POLLERA -> {
                if (activo.getItem() instanceof MoldeRangoItem m)
                    yield ComboCorte.polleraLargo(com.modamod.item.PolleraLargo.valueOf(m.rango.name()));
                if (activo.getItem() instanceof MoldePolleraItem m) yield ComboCorte.polleraForma(m.valor);
                if (activo.getItem() instanceof MoldeVoladoItem m) yield ComboCorte.voladoRuedo(m.valor);
                if (activo.getItem() instanceof MoldeRuedoItem m) yield ComboCorte.ruedo(com.modamod.item.ZonaRuedo.POLLERA, m.valor, false);
                yield ComboCorte.VACIO;
            }
            case CAPA -> {
                ComboCorte c = comboDeMoldeCapa(activo.getItem());
                yield c == null ? ComboCorte.VACIO : c;
            }
            case SOMBRERO -> {
                ComboCorte c = comboDeMoldeSombrero(activo.getItem());
                yield c == null ? ComboCorte.VACIO : c;
            }
            case BANDA -> {
                ComboCorte c = comboDeMoldeBanda(activo.getItem());
                yield c == null ? ComboCorte.VACIO : c;
            }
            case CALZADO -> {
                ComboCorte c = comboDeMoldeBorcego(activo.getItem(), Lado.AMBAS);
                yield c == null ? ComboCorte.VACIO : c;
            }
            case DELANTAL -> {
                if (activo.getItem() instanceof MoldeFormaDelantalItem m) yield ComboCorte.delantalForma(m.valor);
                if (activo.getItem() instanceof MoldeVoladoItem m) yield ComboCorte.delantalVolado(m.valor);
                if (activo.getItem() instanceof MoldeRuedoItem m) yield ComboCorte.ruedo(com.modamod.item.ZonaRuedo.DELANTAL, m.valor, false);
                yield ComboCorte.VACIO;
            }
        };
        return agregarFijada(combo.conIcono(icono));
    }

    private boolean agregarFijada(ComboCorte combo) {
        if (combo.estaVacio()) {
            LOG.info("agregarFijada(): RECHAZADA, combo vacio");
            return false;
        }
        List<ComboCorte> lista = fijadasPorCategoria.get(categoria);
        if (lista.size() >= 12) {
            LOG.info("agregarFijada(): RECHAZADA, lista llena (12) categoria={} combo={}", categoria, combo);
            return false;
        }
        lista.add(combo);
        LOG.info("agregarFijada(): agregada categoria={} combo={} (total ahora {})", categoria, combo, lista.size());
        return true;
    }

    // ── pines del esquema visual de remera (2026-09-24) ──────────────────
    // Un pin por eje, cada uno arma su ComboCorte y lo fija DIRECTO al
    // soltar el molde — sin pasar por Activo+Fijar (a diferencia del resto
    // de las categorías, que siguen con ese flujo). Solo tienen efecto con
    // REMERA activa: si el jugador cambió de pestaña sin cerrar la GUI y
    // de algún modo el click llega igual, no hay que fijarlo en la lista
    // de OTRA categoría por error.

    /**
     * El combo que representa {@code molde} puesto en el pin {@code i} de la
     * categoría {@code cat}, o null si ese pin no acepta ese ítem.
     * {@code lado}: el lado que aplica un pin de lado (con simetría activa
     * se pasa AMBAS sin importar en cuál de los dos se soltó).
     */
    private static ComboCorte comboDePin(Categoria cat, int i, ItemStack molde, Lado lado) {
        Rol rol = ROLES[cat.ordinal()][i];
        Item item = molde.getItem();
        // Cada casillero acepta SOLO su tipo de molde (2026-10-12, "ruedo y rango y cuello y solapas estan mezclados, no
        // deberian tener su propia bolsa?"): antes los pares desplegables (manga↔puño, cuello↔solapa, largo↔cintura...)
        // aceptaban cualquiera de los dos tipos y el molde decidía a cuál se aplicaba; ahora que cada uno es su fila,
        // la lista de la bolsa de un casillero muestra solo lo suyo.
        // Ítem en blanco (2026-10-12, "que todos los moldes 'sin' no existan"): quita el rasgo del pin.
        if (item instanceof MoldeEnBlancoItem b) {
            return switch (b.tipo) {
                case MOLDE -> comboEnBlanco(rol, lado, cat);
                case TRAMA -> switch (rol) {
                    case MAT1, MAT2, MAT3, PERS_IZQ1, PERS_IZQ2, PERS_IZQ3, PERS_DER1, PERS_DER2, PERS_DER3 ->
                            ComboCorte.red(com.modamod.item.PatronRed.LISA);
                    default -> null;
                };
                default -> null;
            };
        }
        ComboCorte c = null;
        switch (rol) {
            case CUELLO -> {
                if (item instanceof MoldeCuelloItem m) c = ComboCorte.remeraCuello(m.valor);
            }
            case MAT1, MAT2, MAT3 -> {
                // Medias de red/calado (2026-09-25): un solo eje, cualquiera
                // de los 3 pines la acepta. Los patrones son capas apiladas.
                if (item instanceof MoldeRedItem m) c = ComboCorte.red(m.valor);
            }
            case PERS_IZQ1, PERS_IZQ2, PERS_IZQ3, PERS_DER1, PERS_DER2, PERS_DER3 -> {
                if (item instanceof MoldeRedItem m) c = ComboCorte.red(m.valor);
            }
            case MANGA_IZQ, MANGA_DER -> {
                if (item instanceof MoldeRangoItem m) c = ComboCorte.remeraManga(mangaRemeraDeRango(m.rango), lado);
            }
            case CALCE -> {
                if (item instanceof MoldeCalceItem m) c = ComboCorte.calce(m.valor);
            }
            case TORSO -> {
                if (item instanceof MoldeRangoItem m) c = ComboCorte.remeraLargo(largoDeNivel(m.rango));
            }
            case TIRO -> {
                if (item instanceof MoldeRangoItem m) c = ComboCorte.tiro(tiroDeNivel(m.rango));
            }
            case RUEDO_TORSO -> {
                if (item instanceof MoldeRuedoItem m && m.valor.valeEnTela()) c = ComboCorte.ruedo(com.modamod.item.ZonaRuedo.TORSO, m.valor, false);
            }
            case RUEDO_PUNO_IZQ, RUEDO_PUNO_DER -> {
                // El lado sale de ladoDePin (cruzado y con la simetría ya aplicada): AMBAS = los dos puños.
                com.modamod.item.Ruedo r = ruedoDeMolde(item);
                if (r != null) {
                    c = ComboCorte.ruedo(lado == Lado.DERECHA ? com.modamod.item.ZonaRuedo.PUNO_DER : com.modamod.item.ZonaRuedo.PUNO_IZQ,
                            r, lado == Lado.AMBAS);
                }
            }
            case RUEDO_BOTA_IZQ, RUEDO_BOTA_DER, RUEDO_SUP_IZQ, RUEDO_SUP_DER, RUEDO_INF_IZQ, RUEDO_INF_DER -> {
                // Como el puño: el lado sale de ladoDePin; AMBAS = los dos lados.
                com.modamod.item.Ruedo r = ruedoDeMolde(item);
                if (r != null) {
                    com.modamod.item.ZonaRuedo z = zonaDeRol(rol, lado == Lado.DERECHA);
                    c = ComboCorte.ruedo(z, r, lado == Lado.AMBAS);
                }
            }
            case RUEDO_POLLERA -> {
                if (item instanceof MoldeRuedoItem m && !m.valor.esDeCapa()) c = ComboCorte.ruedo(com.modamod.item.ZonaRuedo.POLLERA, m.valor, false);
            }
            case VOLADO_INFERIOR -> {
                if (item instanceof MoldeVoladoItem m) c = ComboCorte.voladoRuedo(m.valor);
            }
            case VOLADO_TOTAL -> {
                if (item instanceof MoldeVoladoItem m) c = ComboCorte.voladoTodo(m.valor);
            }
            case FORMA_POLLERA -> {
                if (item instanceof MoldePolleraItem m) c = ComboCorte.polleraForma(m.valor);
            }
            case CINTURA_POLLERA -> {
                if (item instanceof MoldeRangoItem m) c = ComboCorte.polleraCintura(m.rango.nivel());
            }
            case FALDON_TOP -> {
                if (item instanceof MoldeRangoItem m) c = ComboCorte.faldon(m.rango.nivel());
            }
            case LARGO_POLLERA -> {
                // Los 6 rangos del pantalón, 1 a 1 (2026-09-29, "Los 6 rangos del pantalón").
                if (item instanceof MoldeRangoItem m) c = ComboCorte.polleraLargo(com.modamod.item.PolleraLargo.valueOf(m.rango.name()));
            }
            case LARGO_CAPA -> {
                // Los 6 rangos, 1 a 1 (2026-09-29, "Los 6 rangos").
                if (item instanceof MoldeRangoItem m) c = ComboCorte.capaLargo(com.modamod.item.CapaLargo.valueOf(m.rango.name()));
            }
            case RUEDO_CAPA -> {
                // Los remates de la capa son moldes de ruedo (2026-10-12): redondeado y cola.
                if (item instanceof MoldeRuedoItem m && m.valor.esDeCapa()) {
                    c = ComboCorte.capaRuedo(m.valor == com.modamod.item.Ruedo.COLA
                            ? com.modamod.item.CapaRuedo.COLA : com.modamod.item.CapaRuedo.REDONDEADO);
                }
            }
            case CAPUCHA_CAPA -> {
                if (item instanceof MoldeCapaItem m && m.tipo.esCapucha()) c = comboDeMoldeCapa(item);
            }
            case CUELLO_CAPA -> {
                if (item instanceof MoldeCapaItem m && m.tipo.esCuello()) c = comboDeMoldeCapa(item);
            }
            case EMBOZO_CAPA -> {
                if (item instanceof MoldeRangoItem m) c = ComboCorte.capaEmbozo(m.rango.nivel());
                else if (item instanceof MoldeCapaItem m && m.tipo.esEmbozo()) c = ComboCorte.capaEmbozo(-1);
            }
            case BORDE_EMBOZO_CAPA -> {
                if (item instanceof MoldeCapaItem m && m.tipo.esBordeEmbozo()) c = ComboCorte.capaBordeEmbozo(m.tipo == MoldeCapaItem.Tipo.BORDE_EMBOZO);
            }
            case COBERTURA_DELANTAL -> {
                if (item instanceof MoldeCoberturaDelantalItem m) c = ComboCorte.delantalCobertura(m.valor);
            }
            case FRENTE_CHAQUETA -> {
                if (item instanceof MoldeChaquetaItem m && m.tipo.esFrente()) c = comboDeMoldeChaqueta(item);
            }
            case CAPUCHA_CHAQUETA -> {
                // Un solo molde de capucha para remera y capa (2026-10-12, "el molde de capucha no es uno solo...").
                if (item instanceof MoldeCapaItem m && m.tipo == MoldeCapaItem.Tipo.CON_CAPUCHA) c = ComboCorte.chaquetaCapucha(true);
                // Capucha de malla o de cajas (2026-10-23): la pone con ese estilo.
                else if (item instanceof MoldeCapaItem m && m.tipo.esEstiloCapucha())
                    c = ComboCorte.chaquetaCapuchaEstilo(m.tipo == MoldeCapaItem.Tipo.CAPUCHA_CAJAS);
            }
            case SOLAPA_CHAQUETA -> {
                if (item instanceof MoldeChaquetaItem m && m.tipo.esSolapa()) c = comboDeMoldeChaqueta(item);
            }
            case ZONA_BANDA -> {
                if (item instanceof MoldeBandaItem m && m.tipo.esZona()) c = comboDeMoldeBanda(item);
            }
            case ANCHO_BANDA -> {
                if (item instanceof MoldeBandaItem m && m.tipo.esAncho()) c = comboDeMoldeBanda(item);
            }
            case HERRAJE_BANDA -> {
                if (item instanceof MoldeBandaItem m && m.tipo.esHerraje()) c = comboDeMoldeBanda(item);
            }
            case CANA_BORCEGO_IZQ, CANA_BORCEGO_DER -> {
                if (item instanceof MoldeBorcegoItem m && m.tipo.esCana()) c = comboDeMoldeBorcego(item, lado);
            }
            case SUELA_BORCEGO_IZQ, SUELA_BORCEGO_DER -> {
                if (item instanceof MoldeBorcegoItem m && m.tipo.esSuela()) c = comboDeMoldeBorcego(item, lado);
            }
            case BOTAMANGA_BORCEGO_IZQ, BOTAMANGA_BORCEGO_DER -> {
                if (item instanceof MoldeBorcegoItem m && m.tipo.esBotamanga()) c = comboDeMoldeBorcego(item, lado);
            }
            case PUNTA_BORCEGO_IZQ, PUNTA_BORCEGO_DER -> {
                if (item instanceof MoldeBorcegoItem m && m.tipo.esPunta()) c = comboDeMoldeBorcego(item, lado);
            }
            case CIERRE_BORCEGO_IZQ, CIERRE_BORCEGO_DER -> {
                if (item instanceof MoldeBorcegoItem m && m.tipo.esCierre()) c = comboDeMoldeBorcego(item, lado);
            }
            case DETALLE_BORCEGO_IZQ, DETALLE_BORCEGO_DER -> {
                if (item instanceof MoldeBorcegoItem m && m.tipo.esDetalle()) c = comboDeMoldeBorcego(item, lado);
            }
            case ESTILO_CALZADO -> {
                // El mismo molde de cuboides que el sombrero.
                if (item instanceof MoldeSombreroItem m && m.tipo.esEstilo()) c = ComboCorte.borcegoEstilo(true);
            }
            case FORMA_DELANTAL -> {
                if (item instanceof MoldeFormaDelantalItem m) c = ComboCorte.delantalForma(m.valor);
            }
            case LARGO_DELANTAL -> {
                if (item instanceof MoldeRangoItem m) c = ComboCorte.delantalLargo(m.rango.nivel());
            }
            case INICIO_DELANTAL -> {
                if (item instanceof MoldeRangoItem m) c = ComboCorte.delantalInicio(m.rango.nivel());
            }
            case ESQUINAS_DELANTAL -> {
                // Las esquinas de abajo del delantal: el molde de ruedo redondeado (el recto es el blanco).
                if (item instanceof MoldeRuedoItem m && m.valor == com.modamod.item.Ruedo.REDONDEADO) {
                    c = ComboCorte.delantalEsquinas(com.modamod.item.CapaRuedo.REDONDEADO);
                }
            }
            case TIRANTES_DELANTAL -> {
                if (item instanceof MoldeTirantesDelantalItem m) c = ComboCorte.delantalTirantes(m.valor);
            }
            case VOLADO_DELANTAL -> {
                if (item instanceof MoldeVoladoItem m) c = ComboCorte.delantalVolado(m.valor);
            }
            case VOLADO_LATERAL_DELANTAL -> {
                if (item instanceof MoldeVoladoItem m) c = ComboCorte.delantalLateral(m.valor);
            }
            case VOLADO_SUPERIOR_DELANTAL -> {
                if (item instanceof MoldeVoladoItem m) c = ComboCorte.delantalSuperior(m.valor);
            }
            case RUEDO_DELANTAL -> {
                if (item instanceof MoldeRuedoItem m && !m.valor.esDeCapa()) c = ComboCorte.ruedo(com.modamod.item.ZonaRuedo.DELANTAL, m.valor, false);
            }
            case ALA_SOMBRERO -> {
                if (item instanceof MoldeSombreroItem m && m.tipo.esAla()) c = comboDeMoldeSombrero(item);
            }
            case PUNTA_SOMBRERO -> {
                if (item instanceof MoldeSombreroItem m && m.tipo.esPunta()) c = comboDeMoldeSombrero(item);
            }
            case PLANTA_SOMBRERO -> {
                if (item instanceof MoldeSombreroItem m && m.tipo.esPlanta()) c = comboDeMoldeSombrero(item);
            }
            case ESTILO_SOMBRERO -> {
                if (item instanceof MoldeSombreroItem m && m.tipo.esEstilo()) c = comboDeMoldeSombrero(item);
            }
            case GROSOR_SOMBRERO -> {
                if (item instanceof MoldeSombreroItem m && m.tipo.esGrosor()) c = comboDeMoldeSombrero(item);
            }
            case BANDA_SOMBRERO -> {
                if (item instanceof MoldeSombreroItem m && m.tipo.esBanda()) c = comboDeMoldeSombrero(item);
            }
            case BOTA_IZQ, BOTA_DER -> {
                if (item instanceof MoldeRangoItem m) c = ComboCorte.pantalonSuperior(pantalonDeRango(m.rango), lado);
            }
            case SUP_IZQ, SUP_DER, INF_IZQ, INF_DER -> {
                // El pin de ARRIBA del esquema dice dónde EMPIEZA la tela (guarda el anclaje INFERIOR: 12 − altura);
                // el de abajo dónde TERMINA (anclaje SUPERIOR: la altura tal cual). Los dos miden la altura de la
                // línea desde arriba (2026-10-04, "el slot de corte de arriba indique donde empieza la tela y el
                // de abajo donde termina").
                boolean sup = rol == Rol.INF_IZQ || rol == Rol.INF_DER;
                if (cat == Categoria.MEDIAS) {
                    if (item instanceof MoldeRangoItem m) {
                        c = sup ? ComboCorte.mediasSuperior(mediasDeRango(m.rango), lado)
                                : ComboCorte.mediasInferior(mediasInicioDeRango(m.rango), lado);
                    }
                } else if (cat == Categoria.CALIENTABRAZOS && item instanceof MoldeRangoItem m) {
                    c = sup ? ComboCorte.calientabrazosSuperior(mangaCalientabrazosDeRango(m.rango), lado)
                            : ComboCorte.calientabrazosInferior(mangaCalientabrazosInicioDeRango(m.rango), lado);
                }
            }
            default -> { }
        }
        return c == null ? null : c.conIcono(net.minecraft.registry.Registries.ITEM.getId(item));
    }

    /** ¿Es el casillero de ruedo de un puño, una botamanga o un borde de media/calentador? (acepta los volados de malla) */
    private static boolean esRolDeExtremidad(Rol r) {
        return switch (r) {
            case RUEDO_PUNO_IZQ, RUEDO_PUNO_DER, RUEDO_BOTA_IZQ, RUEDO_BOTA_DER, RUEDO_SUP_IZQ, RUEDO_SUP_DER,
                 RUEDO_INF_IZQ, RUEDO_INF_DER -> true;
            default -> false;
        };
    }

    /** El ruedo que pone este molde en una extremidad: el de un molde de ruedo de tela, o el volado de malla. */
    @Nullable
    private static com.modamod.item.Ruedo ruedoDeMolde(Item item) {
        // Los bordes decorativos también valen en las extremidades (2026-10-11): una faldilla con ese recorte.
        if (item instanceof MoldeRuedoItem m) return m.valor.esDeCapa() ? null : m.valor;
        if (item instanceof MoldeVoladoItem v) {
            return v.valor == com.modamod.item.PolleraVolado.CIRCULAR
                    ? com.modamod.item.Ruedo.VOLADO_CIRCULAR : com.modamod.item.Ruedo.VOLADO_RECTO;
        }
        return null;
    }

    /** El corte de un {@link MoldeChaquetaItem} (frente, capucha o solapa), o null. */
    @Nullable
    private static ComboCorte comboDeMoldeChaqueta(Item item) {
        if (!(item instanceof MoldeChaquetaItem m)) return null;
        if (m.tipo.esFrente()) return ComboCorte.chaquetaFrente(m.tipo.frente);
        if (m.tipo.esCapucha()) return ComboCorte.chaquetaCapucha(m.tipo.capucha);
        return ComboCorte.chaquetaSolapa(m.tipo.solapa);
    }

    /** El corte de un {@link MoldeBorcegoItem} (caña, suela o botamanga), o null. */
    @Nullable
    private static ComboCorte comboDeMoldeBorcego(Item item, Lado lado) {
        if (!(item instanceof MoldeBorcegoItem m)) return null;
        if (m.tipo.esCana()) return ComboCorte.borcegoCana(m.tipo.cana, lado);
        if (m.tipo.esSuela()) return ComboCorte.borcegoSuela(m.tipo.suela, lado);
        if (m.tipo.esPunta()) return ComboCorte.borcegoPunta(m.tipo.punta, lado);
        if (m.tipo.esCierre()) return ComboCorte.borcegoCierre(m.tipo.cierre, lado);
        if (m.tipo.esDetalle()) return ComboCorte.borcegoDetalle(m.tipo.detalle, lado);
        return ComboCorte.borcegoBotamanga(m.tipo.botamanga, lado);
    }

    /** El corte de un {@link MoldeBandaItem} (zona, ancho o herraje), o null. */
    @Nullable
    private static ComboCorte comboDeMoldeBanda(Item item) {
        if (!(item instanceof MoldeBandaItem m)) return null;
        if (m.tipo.esZona()) return ComboCorte.bandaZona(m.tipo.zona);
        if (m.tipo.esAncho()) return ComboCorte.bandaAncho(m.tipo.ancho);
        return ComboCorte.bandaHerraje(m.tipo.herraje);
    }

    /** El corte de un {@link MoldeSombreroItem} (ala o punta), o null. */
    @Nullable
    private static ComboCorte comboDeMoldeSombrero(Item item) {
        if (!(item instanceof MoldeSombreroItem m)) return null;
        return m.tipo.esAla() ? ComboCorte.sombreroAla(m.tipo.ala) : m.tipo.esPlanta() ? ComboCorte.sombreroPlanta(true)
                : m.tipo.esEstilo() ? ComboCorte.sombreroEstilo(true) : m.tipo.esGrosor() ? ComboCorte.sombreroGrosor(true)
                : m.tipo.esBanda() ? ComboCorte.sombreroBanda(true) : ComboCorte.sombreroPunta(m.tipo.punta);
    }

    /** El corte de un {@link MoldeCapaItem} (o del molde de rango, como largo de capa), o null. */
    @Nullable
    private static ComboCorte comboDeMoldeCapa(Item item) {
        if (item instanceof MoldeRangoItem m) {
            return ComboCorte.capaLargo(com.modamod.item.CapaLargo.valueOf(m.rango.name()));
        }
        if (!(item instanceof MoldeCapaItem m)) return null;
        return switch (m.tipo) {
            case CON_CAPUCHA -> ComboCorte.capaCapucha(true);
            case SIN_CAPUCHA -> ComboCorte.capaCapucha(false);
            case CUELLO_ALTO -> ComboCorte.capaCuello(true);
            case SIN_CUELLO -> ComboCorte.capaCuello(false);
            case EMBOZO_NINGUNO -> ComboCorte.capaEmbozo(-1);
            case BORDE_EMBOZO -> ComboCorte.capaBordeEmbozo(true);
            case BORDE_NINGUNO -> ComboCorte.capaBordeEmbozo(false);
            case CAPUCHA_MALLA -> ComboCorte.capaCapuchaEstilo(false);
            case CAPUCHA_CAJAS -> ComboCorte.capaCapuchaEstilo(true);
        };
    }

    /**
     * Lo que hace un molde en blanco en el pin de {@code rol}: devuelve el rasgo a su valor de fábrica (el que tiene
     * la prenda sin componente). Null donde no hay un "de fábrica" expresable (rangos de extremidad, tiro, largos de
     * pollera y capa, volados, faldón, cintura): ahí el blanco no entra.
     */
    @Nullable
    private static ComboCorte comboEnBlanco(Rol rol, Lado lado, Categoria cat) {
        return switch (rol) {
            case CUELLO -> ComboCorte.remeraCuello(Variante.Cuello.CERRADO);
            case MANGA_IZQ, MANGA_DER -> ComboCorte.remeraManga(Variante.Manga.SIN, lado);
            case CALCE -> ComboCorte.calce(com.modamod.item.Calce.NORMAL);
            case TORSO -> ComboCorte.remeraLargo(Variante.Largo.NORMAL);
            case FRENTE_CHAQUETA -> ComboCorte.chaquetaFrente(com.modamod.item.ChaquetaFrente.CERRADA);
            case CAPUCHA_CHAQUETA -> ComboCorte.chaquetaCapucha(false);
            case SOLAPA_CHAQUETA -> ComboCorte.chaquetaSolapa(com.modamod.item.ChaquetaSolapa.NINGUNA);
            case RUEDO_TORSO -> ComboCorte.ruedo(com.modamod.item.ZonaRuedo.TORSO, com.modamod.item.Ruedo.RECTO, false);
            case RUEDO_PUNO_IZQ, RUEDO_PUNO_DER, RUEDO_BOTA_IZQ, RUEDO_BOTA_DER, RUEDO_SUP_IZQ, RUEDO_SUP_DER,
                 RUEDO_INF_IZQ, RUEDO_INF_DER ->
                    ComboCorte.ruedo(zonaDeRol(rol, lado == Lado.DERECHA), com.modamod.item.Ruedo.RECTO, lado == Lado.AMBAS);
            case RUEDO_POLLERA -> ComboCorte.ruedo(com.modamod.item.ZonaRuedo.POLLERA, com.modamod.item.Ruedo.RECTO, false);
            case RUEDO_DELANTAL -> ComboCorte.ruedo(com.modamod.item.ZonaRuedo.DELANTAL, com.modamod.item.Ruedo.RECTO, false);
            case FORMA_POLLERA -> ComboCorte.polleraForma(com.modamod.item.PolleraForma.CAMPANA);
            case RUEDO_CAPA -> ComboCorte.capaRuedo(com.modamod.item.CapaRuedo.RECTO);
            case CAPUCHA_CAPA -> ComboCorte.capaCapucha(false);
            case CUELLO_CAPA -> ComboCorte.capaCuello(false);
            case EMBOZO_CAPA -> ComboCorte.capaEmbozo(-1);
            case BORDE_EMBOZO_CAPA -> ComboCorte.capaBordeEmbozo(false);
            case ZONA_BANDA -> ComboCorte.bandaZona(com.modamod.item.BandaZona.CINTURA);
            case ANCHO_BANDA -> ComboCorte.bandaAncho(com.modamod.item.BandaAncho.MEDIO);
            case HERRAJE_BANDA -> ComboCorte.bandaHerraje(com.modamod.item.BandaHerraje.NINGUNO);
            case CANA_BORCEGO_IZQ, CANA_BORCEGO_DER -> ComboCorte.borcegoCana(com.modamod.item.BorcegoCana.MEDIA, lado);
            case SUELA_BORCEGO_IZQ, SUELA_BORCEGO_DER -> ComboCorte.borcegoSuela(com.modamod.item.BorcegoSuela.CHATA, lado);
            case BOTAMANGA_BORCEGO_IZQ, BOTAMANGA_BORCEGO_DER ->
                    ComboCorte.borcegoBotamanga(com.modamod.item.BorcegoBotamanga.ADENTRO, lado);
            case PUNTA_BORCEGO_IZQ, PUNTA_BORCEGO_DER -> ComboCorte.borcegoPunta(com.modamod.item.CalzadoPunta.CUERO, lado);
            case CIERRE_BORCEGO_IZQ, CIERRE_BORCEGO_DER -> ComboCorte.borcegoCierre(com.modamod.item.CalzadoCierre.CORDONES, lado);
            case DETALLE_BORCEGO_IZQ, DETALLE_BORCEGO_DER -> ComboCorte.borcegoDetalle(com.modamod.item.CalzadoDetalle.NINGUNO, lado);
            case ESTILO_CALZADO -> ComboCorte.borcegoEstilo(false);
            case FORMA_DELANTAL -> ComboCorte.delantalForma(com.modamod.item.DelantalForma.RECTO);
            case LARGO_DELANTAL -> ComboCorte.delantalLargo(com.modamod.item.DelantalItem.LARGO_DE_FABRICA);
            case INICIO_DELANTAL -> ComboCorte.delantalInicio(com.modamod.item.DelantalItem.INICIO_DE_FABRICA);
            case ESQUINAS_DELANTAL -> ComboCorte.delantalEsquinas(com.modamod.item.CapaRuedo.RECTO);
            case TIRANTES_DELANTAL -> ComboCorte.delantalTirantes(com.modamod.item.DelantalTirantes.SIN);
            case COBERTURA_DELANTAL -> ComboCorte.delantalCobertura(com.modamod.item.DelantalCobertura.ENVOLVENTE);
            case ALA_SOMBRERO -> ComboCorte.sombreroAla(com.modamod.item.SombreroAla.VISERA_CURVA);   // de fábrica: gorra
            case PUNTA_SOMBRERO -> ComboCorte.sombreroPunta(com.modamod.item.SombreroPunta.CUPULA);
            case PLANTA_SOMBRERO -> ComboCorte.sombreroPlanta(false);
            case ESTILO_SOMBRERO -> ComboCorte.sombreroEstilo(false);
            case GROSOR_SOMBRERO -> ComboCorte.sombreroGrosor(false);
            case BANDA_SOMBRERO -> ComboCorte.sombreroBanda(false);   // el molde en blanco la saca
            // Rangos y volados (2026-10-12, "vamos con la uno"): el blanco vuelve al largo/tiro de fábrica o quita el volado.
            case TIRO -> ComboCorte.tiro(com.modamod.item.PantalonTiro.MEDIO);
            case BOTA_IZQ, BOTA_DER -> ComboCorte.pantalonSuperior(com.modamod.item.Botamanga.PIE, lado);
            case SUP_IZQ, SUP_DER, INF_IZQ, INF_DER -> {
                boolean sup = rol == Rol.INF_IZQ || rol == Rol.INF_DER;
                if (cat == Categoria.MEDIAS) yield sup ? ComboCorte.mediasSuperior(com.modamod.item.Botamanga.PIE, lado)
                        : ComboCorte.mediasInferior(com.modamod.item.Botamanga.PIE, lado);
                if (cat == Categoria.CALIENTABRAZOS) yield sup ? ComboCorte.calientabrazosSuperior(Variante.Manga.LARGA, lado)
                        : ComboCorte.calientabrazosInferior(Variante.Manga.LARGA, lado);
                yield null;
            }
            case LARGO_POLLERA -> ComboCorte.polleraLargo(com.modamod.item.PolleraLargo.MEDIO);
            case LARGO_CAPA -> ComboCorte.capaLargo(com.modamod.item.CapaLargo.MEDIO);
            case CINTURA_POLLERA -> ComboCorte.polleraCintura(7);
            case FALDON_TOP -> ComboCorte.faldon(7);
            case VOLADO_INFERIOR -> ComboCorte.voladoRuedo(com.modamod.item.PolleraVolado.SIN);
            case VOLADO_TOTAL -> ComboCorte.voladoTodo(com.modamod.item.PolleraVolado.SIN);
            case VOLADO_DELANTAL -> ComboCorte.delantalVolado(com.modamod.item.PolleraVolado.SIN);
            case VOLADO_LATERAL_DELANTAL -> ComboCorte.delantalLateral(com.modamod.item.PolleraVolado.SIN);
            case VOLADO_SUPERIOR_DELANTAL -> ComboCorte.delantalSuperior(com.modamod.item.PolleraVolado.SIN);
            default -> null;
        };
    }

    /** El rol "gemelo" de un casillero desplegable (manga ↔ puño, cuello ↔ solapa...), o null si no es de un par. */
    static Rol parDeCajon(Rol r) {
        return switch (r) {
            case MANGA_IZQ -> Rol.RUEDO_PUNO_IZQ; case RUEDO_PUNO_IZQ -> Rol.MANGA_IZQ;
            case MANGA_DER -> Rol.RUEDO_PUNO_DER; case RUEDO_PUNO_DER -> Rol.MANGA_DER;
            case BOTA_IZQ -> Rol.RUEDO_BOTA_IZQ; case RUEDO_BOTA_IZQ -> Rol.BOTA_IZQ;
            case BOTA_DER -> Rol.RUEDO_BOTA_DER; case RUEDO_BOTA_DER -> Rol.BOTA_DER;
            case SUP_IZQ -> Rol.RUEDO_SUP_IZQ; case RUEDO_SUP_IZQ -> Rol.SUP_IZQ;
            case SUP_DER -> Rol.RUEDO_SUP_DER; case RUEDO_SUP_DER -> Rol.SUP_DER;
            case INF_IZQ -> Rol.RUEDO_INF_IZQ; case RUEDO_INF_IZQ -> Rol.INF_IZQ;
            case INF_DER -> Rol.RUEDO_INF_DER; case RUEDO_INF_DER -> Rol.INF_DER;
            case CUELLO -> Rol.SOLAPA_CHAQUETA; case SOLAPA_CHAQUETA -> Rol.CUELLO;
            case TORSO -> Rol.RUEDO_TORSO; case RUEDO_TORSO -> Rol.TORSO;
            case VOLADO_INFERIOR -> Rol.RUEDO_POLLERA; case RUEDO_POLLERA -> Rol.VOLADO_INFERIOR;
            case LARGO_POLLERA -> Rol.CINTURA_POLLERA; case CINTURA_POLLERA -> Rol.LARGO_POLLERA;
            case LARGO_DELANTAL -> Rol.INICIO_DELANTAL; case INICIO_DELANTAL -> Rol.LARGO_DELANTAL;
            case VOLADO_DELANTAL -> Rol.RUEDO_DELANTAL; case RUEDO_DELANTAL -> Rol.VOLADO_DELANTAL;
            default -> null;
        };
    }

    /** ¿Es el casillero SECUNDARIO (el que se despliega) de un par? */
    static boolean esRolRuedoDeCajon(Rol r) {
        return switch (r) {
            case RUEDO_PUNO_IZQ, RUEDO_PUNO_DER, RUEDO_BOTA_IZQ, RUEDO_BOTA_DER, RUEDO_SUP_IZQ, RUEDO_SUP_DER,
                 RUEDO_INF_IZQ, RUEDO_INF_DER, SOLAPA_CHAQUETA, RUEDO_TORSO, RUEDO_POLLERA, CINTURA_POLLERA,
                 INICIO_DELANTAL, RUEDO_DELANTAL -> true;
            default -> false;
        };
    }

    /** La zona de ruedo de un rol de ruedo de cajón; {@code derecha} elige el lado cuando el rol no lo trae. */
    static com.modamod.item.ZonaRuedo zonaDeRol(Rol r, boolean derecha) {
        return switch (r) {
            case RUEDO_BOTA_IZQ, RUEDO_BOTA_DER -> derecha ? com.modamod.item.ZonaRuedo.BOTA_DER : com.modamod.item.ZonaRuedo.BOTA_IZQ;
            case RUEDO_SUP_IZQ, RUEDO_SUP_DER -> derecha ? com.modamod.item.ZonaRuedo.SUP_DER : com.modamod.item.ZonaRuedo.SUP_IZQ;
            case RUEDO_INF_IZQ, RUEDO_INF_DER -> derecha ? com.modamod.item.ZonaRuedo.INF_DER : com.modamod.item.ZonaRuedo.INF_IZQ;
            default -> derecha ? com.modamod.item.ZonaRuedo.PUNO_DER : com.modamod.item.ZonaRuedo.PUNO_IZQ;
        };
    }

    /**
     * Pin "principal" del que cuelga un casillero desplegable (2026-10-08): el del puño se abre con la manga de su
     * lado, el de la solapa con el cuello, el del ruedo de la botamanga/borde con su corte. -1 si no es un cajón.
     */
    public static int principalDeCajon(Categoria cat, int i) {
        Rol rol = ROLES[cat.ordinal()][i];
        if (!esRolRuedoDeCajon(rol)) return -1;
        Rol principal = parDeCajon(rol);
        for (int k = 0; k < PINES_POR_CATEGORIA; k++) if (ROLES[cat.ordinal()][k] == principal) return k;
        return -1;
    }

    /** Si el pin {@code i} es un principal con cajón, el índice del pin secundario; si no, -1. */
    public static int cajonDe(Categoria cat, int i) {
        Rol par = parDeCajon(ROLES[cat.ordinal()][i]);
        if (par == null || !esRolRuedoDeCajon(par)) return -1;
        for (int k = 0; k < PINES_POR_CATEGORIA; k++) if (ROLES[cat.ordinal()][k] == par) return k;
        return -1;
    }

    /** Cajones abiertos a mano (un bit por pin lógico), con el botón ▾ del pin principal. */
    private final java.util.BitSet cajones = new java.util.BitSet(PINES_LOGICOS);

    public boolean cajonAbierto(int p) { return cajones.get(p); }

    /** ¿Se ve y se puede usar el pin {@code i}? Un cajón se abre a mano o solo cuando él o su principal tienen molde o corte. */
    public boolean pinVisible(Categoria cat, int i) {
        if (ROLES[cat.ordinal()][i] == Rol.NINGUNO) return false;
        // 2026-10-12 (visor enfocado, "el puño es una fila más de la zona"): ya no hay cajones; todo casillero con rol se ve.
        if (true) return true;
        int principal = principalDeCajon(cat, i);
        if (principal < 0) return true;
        int base = cat.ordinal() * PINES_POR_CATEGORIA;
        return cajonAbierto(base + principal) || pinFijado[base + i] || !getStack(pinSlot(base + i)).isEmpty();
    }

    /** El lado que le toca a un pin de lado; con simetría activa (o en pines sin lado) es AMBAS. */
    private Lado ladoDePin(Categoria cat, int i) {
        if (remeraSimetria) return Lado.AMBAS;
        Lado lado = switch (ROLES[cat.ordinal()][i]) {
            case MANGA_IZQ, BOTA_IZQ, SUP_IZQ, INF_IZQ, PERS_IZQ1, PERS_IZQ2, PERS_IZQ3, RUEDO_PUNO_IZQ,
                 RUEDO_BOTA_IZQ, RUEDO_SUP_IZQ, RUEDO_INF_IZQ, CANA_BORCEGO_IZQ, SUELA_BORCEGO_IZQ,
                 BOTAMANGA_BORCEGO_IZQ, PUNTA_BORCEGO_IZQ, CIERRE_BORCEGO_IZQ, DETALLE_BORCEGO_IZQ -> Lado.IZQUIERDA;
            case MANGA_DER, BOTA_DER, SUP_DER, INF_DER, PERS_DER1, PERS_DER2, PERS_DER3, RUEDO_PUNO_DER,
                 RUEDO_BOTA_DER, RUEDO_SUP_DER, RUEDO_INF_DER, CANA_BORCEGO_DER, SUELA_BORCEGO_DER,
                 BOTAMANGA_BORCEGO_DER, PUNTA_BORCEGO_DER, CIERRE_BORCEGO_DER, DETALLE_BORCEGO_DER -> Lado.DERECHA;
            default -> Lado.AMBAS;
        };
        // Todos los esquemas se leen "de frente" (izquierda del dibujo = izquierda de pantalla), pero la Izq.
        // anatómica del jugador queda a la derecha en el visor: se cruzan siempre (2026-10-04, "el corte de mangas
        // en modeladora, remera esta invertido izquierda derecha, igual en pantalon"; antes solo medias y
        // calientabrazos).
        if (lado == Lado.IZQUIERDA) return Lado.DERECHA;
        if (lado == Lado.DERECHA) return Lado.IZQUIERDA;
        return lado;
    }

    public boolean remeraSimetria() { return remeraSimetria; }

    /** ¿Acepta este pin este ítem? (independiente de la categoría activa/encendida — eso lo chequea {@link #isValid}). */
    public static boolean pinAcepta(Categoria cat, int i, ItemStack molde) {
        return comboDePin(cat, i, molde, Lado.AMBAS) != null;
    }

    // Estado por pin (2026-09-24, "una chincheta en cada slot de corte...
    // fijarlo con la chincheta y el molde vuelve al almacen"): el pin tiene
    // el molde puesto (se aplica de una, para verlo en el visor) y con la
    // CHINCHETA se fija: el corte queda aplicado y el molde vuelve al
    // almacén, así el mismo molde sirve para el otro lado. pinCombo = el
    // corte que aplica ese pin ahora; pinFijado = si ya se chinchó.
    private final ComboCorte[] pinCombo = new ComboCorte[PINES_LOGICOS];
    private final boolean[] pinFijado = new boolean[PINES_LOGICOS];

    public boolean pinFijado(int i) { return pinFijado[i]; }
    public ComboCorte pinCombo(int i) { return pinCombo[i]; }

    /**
     * Un pin cambió de contenido: el corte anterior de ese pin (si había)
     * deja de aplicarse, y el molde nuevo (si hay) se aplica sin fijar.
     * Solo corre del lado SERVIDOR — el cliente se entera por el sync de
     * NBT y por el de slots; correrlo también allá pisaba el estado
     * recién sincronizado (ej. el molde que la chincheta devuelve al
     * almacén hace vaciar el pin del lado cliente).
     */
    private void pinCambio(int idx, ItemStack viejo, ItemStack nuevo) {
        if (world == null || world.isClient) return;
        Categoria cat = Categoria.values()[idx / PINES_POR_CATEGORIA];
        List<ComboCorte> lista = fijadasPorCategoria.get(cat);
        if (pinCombo[idx] != null) {
            lista.remove(pinCombo[idx]);
            pinCombo[idx] = null;
        }
        pinFijado[idx] = false;
        if (!nuevo.isEmpty()) {
            int i = idx % PINES_POR_CATEGORIA;
            ComboCorte c = comboDePin(cat, i, nuevo, ladoDePin(cat, i));
            if (c != null && lista.size() < 12) {
                lista.add(c);
                pinCombo[idx] = c;
            }
        }
        sincronizar();
    }

    /**
     * Botón chincheta del pin {@code idx}: con molde puesto, FIJA el corte
     * y manda el molde de vuelta al almacén; con el corte ya fijado (pin
     * vacío), lo QUITA. Falso si no hay lugar donde devolver el molde
     * (no se pierde nada, queda en el pin).
     */
    private boolean chinchetaPin(int idx) {
        if (idx < 0 || idx >= PINES_LOGICOS || idx / PINES_POR_CATEGORIA != categoria.ordinal()) return false;
        int slot = pinSlot(idx);
        ItemStack molde = items.get(slot);
        List<ComboCorte> lista = fijadasPorCategoria.get(categoria);
        if (!molde.isEmpty()) {
            if (pinCombo[idx] == null) return false;
            // Con la bolsa (2026-10-12) el molde elegido es una referencia: no se gasto, no hay nada que devolver.
            items.set(slot, ItemStack.EMPTY);
            pinFijado[idx] = true;
            return true;
        }
        if (pinFijado[idx]) {
            if (pinCombo[idx] != null) lista.remove(pinCombo[idx]);
            pinCombo[idx] = null;
            pinFijado[idx] = false;
            return true;
        }
        return false;
    }

    public int disenosGuardados() { return disenosPorCategoria.get(categoria).size(); }

    /** Nombre del diseño guardado en ese casillero (para el hover), o null si está vacío. */
    @Nullable
    public String nombreDiseno(int idx) {
        List<DisenoGuardado> lista = disenosPorCategoria.get(categoria);
        return idx >= 0 && idx < lista.size() ? lista.get(idx).nombre() : null;
    }

    /**
     * Recibido desde {@link GuardarDisenoPayload} (el nombre lo escribe el
     * jugador en un {@code TextFieldWidget}, no entra en un
     * {@code clickButton(int)} común) — guarda TODAS las fijadas de la
     * categoría actual (todos los pines juntos) como un diseño con nombre,
     * en el próximo casillero libre.
     */
    public boolean guardarDiseno(String nombreCrudo) {
        List<ComboCorte> actual = fijadasPorCategoria.get(categoria);
        List<DisenoGuardado> lista = disenosPorCategoria.get(categoria);
        if (actual.isEmpty() || lista.size() >= DISENOS_MAXIMO) return false;
        String nombre = nombreCrudo == null || nombreCrudo.isBlank()
                ? "#" + (lista.size() + 1) : nombreCrudo.trim();
        if (nombre.length() > 24) nombre = nombre.substring(0, 24);
        lista.add(new DisenoGuardado(nombre, new ArrayList<>(actual)));
        markDirty();
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        return true;
    }

    /** Carga el diseño {@code idx} de la categoría actual: reemplaza TODAS las fijadas y suelta los pines. */
    private boolean cargarDiseno(int idx) {
        List<DisenoGuardado> lista = disenosPorCategoria.get(categoria);
        if (idx < 0 || idx >= lista.size()) return false;
        return cargarCombos(lista.get(idx).combos());
    }

    private boolean cargarUltimo() {
        List<ComboCorte> l = ultimoPorCategoria.get(categoria);
        return l != null && !l.isEmpty() && cargarCombos(l);
    }

    private boolean cargarCombos(List<ComboCorte> combos) {
        for (int i = categoria.ordinal() * PINES_POR_CATEGORIA; i < (categoria.ordinal() + 1) * PINES_POR_CATEGORIA; i++) {
            pinCombo[i] = null;
            pinFijado[i] = false;
            if (!items.get(pinSlot(i)).isEmpty()) items.set(pinSlot(i), ItemStack.EMPTY);   // era una referencia a la bolsa, no un ítem gastado
        }
        List<ComboCorte> fijadas = fijadasPorCategoria.get(categoria);
        fijadas.clear();
        fijadas.addAll(combos);
        marcarFijados(fijadas);
        return true;
    }

    /**
     * Vuelve a asignar cada corte de la lista a su casillero y lo marca FIJADO (2026-10-12, "quiero q aparezcan fijados
     * asi se pueda analizar el diseno"): se recrea el corte que daria cada casillero con el molde de origen del corte
     * ({@code iconoOrigen}) y, si coincide, ese casillero queda con su corte y su fantasma. Primero con el lado que
     * le toca a cada casillero y, para lo que no coincidio (se guardo con otra simetria), con cualquier lado.
     */
    private void marcarFijados(List<ComboCorte> lista) {
        int base = categoria.ordinal() * PINES_POR_CATEGORIA;
        List<ComboCorte> pendientes = new ArrayList<>();
        for (ComboCorte combo : lista) {
            if (!asignarAPin(combo, base, true)) pendientes.add(combo);
        }
        for (ComboCorte combo : pendientes) asignarAPin(combo, base, false);
    }

    private boolean asignarAPin(ComboCorte combo, int base, boolean ladoDelPin) {
        if (combo.iconoOrigen().isEmpty()) return false;
        Item item = net.minecraft.registry.Registries.ITEM.get(combo.iconoOrigen().get());
        ItemStack molde = new ItemStack(item);
        for (int i = 0; i < PINES_POR_CATEGORIA; i++) {
            if (pinCombo[base + i] != null || ROLES[categoria.ordinal()][i] == Rol.NINGUNO) continue;
            Lado[] lados = ladoDelPin ? new Lado[]{ladoDePin(categoria, i)} : Lado.values();
            for (Lado l : lados) {
                ComboCorte c = comboDePin(categoria, i, molde, l);
                if (c != null && c.equals(combo)) {
                    pinCombo[base + i] = combo;
                    pinFijado[base + i] = true;
                    return true;
                }
            }
        }
        return false;
    }

    private boolean borrarDiseno(int idx) {
        List<DisenoGuardado> lista = disenosPorCategoria.get(categoria);
        if (idx < 0 || idx >= lista.size()) return false;
        lista.remove(idx);
        return true;
    }

    /**
     * Modeladora creativa (2026-10-01, "tengan adentro todos los patrones de
     * cada maquina"): uno de cada molde registrado, cada uno en su lugar
     * (los compartidos en el almacén general, los exclusivos en el banco de
     * su prenda).
     */
    @Override
    public void cargarCreativa() {
        // Uno de cada molde registrado, en la bolsa (2026-10-12: ya no hay almacenes con lugares contados).
        for (Item item : net.minecraft.registry.Registries.ITEM) {
            ItemStack molde = new ItemStack(item);
            if (esMoldeDeModelado(molde)) bolsa.merge(net.minecraft.registry.Registries.ITEM.getId(item), 1, (x, y) -> Math.max(x, y));
        }
        markDirty();
        sincronizar();
    }

    // ── La bolsa de moldes (2026-10-12, "bolsas por tipo de molde... el casillero guarda solo cual molde elegiste,
    // no una copia del item... reemplazan al almacen de 27 y a los Moldes de <prenda>"): un solo deposito de moldes
    // por maquina, sin lugares contados (se guarda como lista propia en el NBT, no con Inventories). La "bolsa por
    // tipo" es la vista: cada casillero muestra solo los moldes de la bolsa que ese casillero acepta (pinAcepta), asi
    // un molde de rango sirve en todos los casilleros de rango. Elegir un molde NO lo gasta.

    /** Moldes guardados: id del item -> cantidad (el orden de la lista lo da el registro, ver la pantalla). */
    private final java.util.LinkedHashMap<net.minecraft.util.Identifier, Integer> bolsa = new java.util.LinkedHashMap<>();
    public static final int BOLSA_TOPE_POR_MOLDE = 9999;

    public java.util.Map<net.minecraft.util.Identifier, Integer> bolsa() { return java.util.Collections.unmodifiableMap(bolsa); }

    /** ¿Es un item que la Modeladora usa como molde (sirve en alguna categoria)? */
    public static boolean esMoldeDeModelado(ItemStack stack) {
        return !stack.isEmpty() && !categoriasDe(stack).isEmpty();
    }

    /** Suma un molde a la bolsa (servidor). Devuelve falso si no es un molde. */
    public boolean absorber(ItemStack stack) {
        if (world != null && world.isClient) return false;
        if (!esMoldeDeModelado(stack)) return false;
        bolsa.merge(net.minecraft.registry.Registries.ITEM.getId(stack.getItem()), stack.getCount(),
                (x, y) -> Math.min(BOLSA_TOPE_POR_MOLDE, x + y));
        sincronizar();
        return true;
    }

    /** Pone en el casillero {@code pin} (de la categoria actual) el molde {@code id} de la bolsa, sin gastarlo; lo fija solo. */
    public boolean elegirDeBolsa(int pin, net.minecraft.util.Identifier id) {
        if (encendida || pin < 0 || pin >= PINES_POR_CATEGORIA || ROLES[categoria.ordinal()][pin] == Rol.NINGUNO) return false;
        if (bolsa.getOrDefault(id, 0) <= 0) return false;
        Item item = net.minecraft.registry.Registries.ITEM.get(id);
        ItemStack molde = new ItemStack(item);
        if (!pinAcepta(categoria, pin, molde)) return false;
        setStack(pinSlot(categoria.ordinal() * PINES_POR_CATEGORIA + pin), molde);
        sincronizar();
        return true;
    }

    // ── Bolsa de diseños (2026-10-12, "8 de papel mas nucleo de carbon... 200... pestañas"): ítems "Diseño de corte" que se
    // sueltan en el depósito, se cargan con click y se sacan con click derecho. Se guardan como ítems enteros (nombre incluido).
    public static final int DISENOS_ITEM_MAXIMO = 200;
    private final List<ItemStack> disenosItem = new ArrayList<>();

    /** El casillero del diseño en blanco para exportar (2026-10-25, "un slot para poner un diseño en blanco y un botoncito de exportar"). */
    public final net.minecraft.inventory.SimpleInventory blancoDiseno = com.modamod.util.DisenosMaquina.casillero(this);

    /** Exporta el diseño {@code idx} de la lista como ítem, gastando un diseño en blanco del casillero. */
    public boolean exportarDiseno(PlayerEntity jugador, int idx) {
        boolean ok = com.modamod.util.DisenosMaquina.exportar(jugador, disenosItem, idx, blancoDiseno,
                com.modamod.util.MaquinaCreativa.es(this) || jugador.isCreative());
        if (ok) sincronizar();
        return ok;
    }

    public List<ItemStack> disenosItem() { return java.util.Collections.unmodifiableList(disenosItem); }

    public static boolean esDiseno(ItemStack stack) {
        return stack.getItem() instanceof DisenoCorteItem && stack.get(com.modamod.item.ModamodComponents.DISENO_CORTE) != null;
    }

    public boolean absorberDiseno(ItemStack stack) {
        if (world != null && world.isClient) return false;
        if (!esDiseno(stack)) return false;
        if (com.modamod.util.DisenosMaquina.yaEsta(disenosItem, stack)) return true;
        if (disenosItem.size() >= DISENOS_ITEM_MAXIMO) return false;
        disenosItem.add(stack.copyWithCount(1));
        sincronizar();
        return true;
    }

    /** Carga el diseño {@code idx} de la bolsa: cambia a su categoría y deja sus cortes fijados. */
    public boolean cargarDisenoItem(int idx) {
        if (encendida || idx < 0 || idx >= disenosItem.size()) return false;
        DisenoCorte d = disenosItem.get(idx).get(com.modamod.item.ModamodComponents.DISENO_CORTE);
        if (d == null) return false;
        try {
            categoria = Categoria.valueOf(d.categoria());
        } catch (IllegalArgumentException e) {
            return false;
        }
        cargarCombos(new ArrayList<>(d.combos()));
        sincronizar();
        return true;
    }

    public boolean sacarDiseno(PlayerEntity jugador, int idx) {
        if (idx < 0 || idx >= disenosItem.size()) return false;
        // Click derecho borra sin dar el ítem (2026-10-25, "cuando le das click secundario al diseño te da el molde sin
        // haber necesitado un diseño en blanco"): el ítem sale solo con Exportar.
        disenosItem.remove(idx);
        sincronizar();
        return true;
    }

    /**
     * Fabrica un ítem "Diseño de corte" con los cortes fijados de la categoría actual (botón "+" con el nombre escrito).
     * Gasta un diseño en blanco del inventario del jugador; la máquina creativa y el modo creativo no gastan.
     */
    public boolean crearDisenoItem(PlayerEntity jugador, String nombre) {
        if (encendida) { com.modamod.util.DisenosMaquina.avisarTrabajando(jugador); return false; }
        List<ComboCorte> actual = fijadasPorCategoria.get(categoria);
        if (actual.isEmpty()) { jugador.sendMessage(Text.translatable("modamod.disenos.sin_fijar_corte"), true); return false; }
        ItemStack item = new ItemStack(ModeladoMod.DISENO_CORTE);
        item.set(com.modamod.item.ModamodComponents.DISENO_CORTE, new DisenoCorte(categoria.name(), new ArrayList<>(actual)));
        String n = nombre == null ? "" : nombre.trim();
        if (n.length() > 24) n = n.substring(0, 24);
        item.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, (n.isEmpty()
                ? Text.translatable("modamod.modelado.diseno.defecto",
                        Text.translatable("modamod.modelado.categoria." + categoria.name().toLowerCase(java.util.Locale.ROOT)))
                : Text.literal(n)).styled(st -> st.withItalic(false)));
        // Directo a la lista de la máquina (2026-10-25, "que en la pestaña de diseños de todas las maquinas quede").
        if (!com.modamod.util.DisenosMaquina.guardar(jugador, disenosItem, DISENOS_ITEM_MAXIMO, item)) return false;
        sincronizar();
        return true;
    }

    /** Saca un molde de la bolsa y se lo da al jugador (click derecho en la bolsa). */
    public boolean sacarDeBolsa(PlayerEntity jugador, net.minecraft.util.Identifier id) {
        int n = bolsa.getOrDefault(id, 0);
        if (n <= 0) return false;
        if (n == 1) bolsa.remove(id); else bolsa.put(id, n - 1);
        jugador.getInventory().offerOrDrop(new ItemStack(net.minecraft.registry.Registries.ITEM.get(id)));
        sincronizar();
        return true;
    }

    /**
     * Lo que quedo de los almacenes viejos (27 + los bancos por prenda) pasa a la bolsa; si habia una prenda terminada
     * se suelta al mundo; y los moldes reales que hubieran quedado puestos en un casillero tambien van a la bolsa.
     */
    private void drenarViejos() {
        boolean cambio = false;
        java.util.List<Integer> lugares = new java.util.ArrayList<>();
        for (int i = ALMACEN_INICIO; i < ALMACEN_FIN; i++) lugares.add(i);
        for (Categoria c : Categoria.values()) for (int i = 0; i < PORPRENDA_TOTAL; i++) lugares.add(porPrendaSlot(c, i));
        for (int slot : lugares) {
            ItemStack en = items.get(slot);
            if (en.isEmpty()) continue;
            items.set(slot, ItemStack.EMPTY);
            if (!absorber(en) && world != null) {
                net.minecraft.util.ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, en);
            }
            cambio = true;
        }
        for (int p = 0; p < PINES_LOGICOS; p++) {
            int slot = pinSlot(p);
            if (items.get(slot).isEmpty()) continue;
            ItemStack en = removeStack(slot);
            absorber(en);
            cambio = true;
        }
        if (cambio) sincronizar();
    }

    /** Al des-fijar a mano una fijada de remera, el pin que la produjo se limpia (y el molde, si todavía estaba puesto, vuelve al mundo). */
    /** Suelta todos los cortes fijados de la categoría (y sus chinchetas). */
    private boolean borrarFijadas(Categoria cat) {
        List<ComboCorte> lista = fijadasPorCategoria.get(cat);
        if (lista == null || lista.isEmpty()) return false;
        for (ComboCorte quitada : new java.util.ArrayList<>(lista)) {
            lista.remove(quitada);
            soltarPinDeFijada(cat, quitada);
        }
        return true;
    }

    private void soltarPinDeFijada(Categoria cat, ComboCorte quitada) {
        for (int i = cat.ordinal() * PINES_POR_CATEGORIA; i < (cat.ordinal() + 1) * PINES_POR_CATEGORIA; i++) {
            if (!quitada.equals(pinCombo[i])) continue;
            pinCombo[i] = null;
            pinFijado[i] = false;
            ItemStack en = items.get(pinSlot(i));
            if (!en.isEmpty()) items.set(pinSlot(i), ItemStack.EMPTY);   // referencia a la bolsa: no hay nada que soltar
            return;
        }
    }

    /**
     * Aplica las fijadas de la categoría de la PRENDA (no la que esté
     * mostrando la GUI en ese momento, ni las otras 3).
     *
     * <p><b>2026-09-20, bug real jugando</b>: "sigue cortandose la remera
     * en crop top y en media red" — antes esto aplicaba las fijadas de
     * las 4 categorías a CUALQUIER prenda, confiando en que
     * {@link PrendaModelado#aplicar} filtrara por tipo de ítem. Eso
     * funciona para los ejes propios de cada categoría (remeraLargo,
     * pantalonTiro, etc. — cada uno ya chequea el tipo adentro), pero NO
     * para los transversales (Calce y {@link com.modamod.item.PatronRed}):
     * esos aplican a CUALQUIER prenda reconocida sin importar bajo qué
     * categoría se guardó la fijada. Una fijada "red fina" guardada
     * mientras la GUI mostraba MEDIAS se colaba en cualquier remera
     * procesada después, aunque el jugador ya hubiera borrado esa fijada
     * de la pestaña que estaba mirando — porque en realidad estaba
     * pisando la remera desde la lista de OTRA categoría. Limitar esto a
     * la categoría real de la prenda que entra es la forma correcta de
     * que "sin fijada de esa categoría" signifique de verdad "sin
     * cambios" (vuelve a whatever el ítem ya traía — largo normal, sin
     * red, tela completa).
     */
    private ItemStack procesar(ItemStack prenda) {
        Categoria cat = categoriaDe(prenda);
        if (cat == null) return prenda;
        ItemStack out = prenda;
        for (ComboCorte combo : fijadasPorCategoria.get(cat)) out = PrendaModelado.aplicar(out, combo);
        return out;
    }

    /** Lo que entra en el slot de prenda: la ropa de siempre y el sombrero de bruja (2026-10-05; no es ropa para el resto de las máquinas). */
    public static boolean esPrendaModelable(ItemStack stack) {
        return com.modamod.item.ModamodDye.isClothing(stack)
                || stack.getItem() instanceof com.modamod.item.SombreroBrujaItem
                || stack.getItem() instanceof com.modamod.item.BandaItem
                || stack.getItem() instanceof com.modamod.item.BorcegosItem;
    }

    /** A qué categoría pertenece esta prenda de verdad — mismo chequeo de tipo que {@link PrendaModelado#aplicar}. */
    @Nullable
    private static Categoria categoriaDe(ItemStack stack) {
        if (stack.getItem() instanceof com.modamod.sublimadora.RemeraItem) return Categoria.REMERA;
        if (stack.getItem() instanceof com.modamod.item.PantalonItem) return Categoria.PANTALON;
        if (stack.getItem() == com.modamod.item.ModamodItems.SOCKS_SOLID) return Categoria.MEDIAS;
        if (stack.getItem() instanceof com.modamod.item.CalientabrazosItem) return Categoria.CALIENTABRAZOS;
        if (stack.getItem() instanceof com.modamod.item.PolleraItem) return Categoria.POLLERA;
        if (stack.getItem() instanceof com.modamod.item.CapaItem) return Categoria.CAPA;
        if (stack.getItem() instanceof com.modamod.item.SombreroBrujaItem) return Categoria.SOMBRERO;
        if (stack.getItem() instanceof com.modamod.item.BandaItem) return Categoria.BANDA;
        if (stack.getItem() instanceof com.modamod.item.BorcegosItem) return Categoria.CALZADO;
        if (stack.getItem() instanceof com.modamod.item.DelantalItem) return Categoria.DELANTAL;
        return null;
    }

    /**
     * La prenda física del slot {@link #PRENDA} con todas las fijadas ya
     * aplicadas, para el visor 3D — {@link #procesar} no muta el slot real
     * (cada {@code PrendaModelado.aplicar} devuelve una copia), así que
     * llamar esto todos los frames del lado del cliente es seguro.
     */
    public ItemStack previsualizar() {
        return procesar(items.get(PRENDA));
    }

    /**
     * La ÚLTIMA vista previa no vacía — a pedido (2026-09-21, "quiero
     * que las 3 muestren la ultima prenda con preview del ultimo
     * seteado"): {@link #previsualizar} solo tiene algo mientras hay una
     * prenda física en el slot; la pantallita del bloque
     * ({@code ModeladoGeoModel}) usa ESTO en cambio, así que sigue
     * mostrando el último corte hecho aunque ya hayas retirado el
     * resultado. No persiste en NBT a propósito (es solo para la
     * pantalla, se resetea si se descarga el chunk — aceptable).
     */
    private ItemStack ultimaVistaPrevia = ItemStack.EMPTY;

    public ItemStack vistaPreviaPersistente() {
        ItemStack actual = previsualizar();
        if (!actual.isEmpty()) ultimaVistaPrevia = actual;
        return ultimaVistaPrevia;
    }

    // ── animación + ticker ──────────────────────────────────────────────

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "trabajo", 0, state -> estado == Estado.PROCESANDO
                ? state.setAndContinue(TRABAJO_ANIM)
                : software.bernie.geckolib.animation.PlayState.STOP));
        controllers.add(new AnimationController<>(this, "en_marcha", 0, state -> encendida
                ? state.setAndContinue(EN_MARCHA_ANIM)
                : software.bernie.geckolib.animation.PlayState.STOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    public static void tick(net.minecraft.world.World world, BlockPos pos, net.minecraft.block.BlockState state, ModeladoBlockEntity be) {
        if (world.isClient) return;
        // Con señal de redstone la máquina se detiene del todo (2026-10-05).
        if (com.modamod.util.Redstone.pausada(world, pos)) return;
        if (world.getTime() % 20 == 0) be.drenarViejos();
        // Luz del LED (2026-09-29, "hace que las luces de las maquinas iluminen").
        com.modamod.util.LuzMaquina.actualizar(world, pos, state,
                be.estado == Estado.PROCESANDO || be.estado == Estado.LISTO);

        // Ráfaga del ventilador (2026-09-22, "unos efectos visuales como
        // de carga de viento") — atada a "encendida", igual que la
        // animación "en_marcha" del hueso "fan" (no solo mientras
        // PROCESANDO: la idea es que se sienta que la máquina está
        // prendida, no solo trabajando).
        if (be.encendida && world instanceof ServerWorld servidor && world.getTime() % 12 == 0) {
            rafaga(servidor, pos);
        }

        switch (be.estado) {
            case REPOSO -> {
                boolean prendaOk = !be.items.get(PRENDA).isEmpty();
                boolean salidaOk = be.items.get(SALIDA).isEmpty();
                boolean fijadaOk = be.fijadasPorCategoria.values().stream().anyMatch(l -> !l.isEmpty());
                if (be.linea && be.guiAbiertas == 0 && !be.encendida
                        && prendaOk && salidaOk && fijadaOk && be.tijeraOk()) {
                    be.encenderAlCerrar();
                }
                if (be.encendida && prendaOk && salidaOk && fijadaOk && be.tijeraOk()) {
                    be.estado = Estado.PROCESANDO;
                    be.progreso = 0;
                    Categoria deLaPrenda = categoriaDe(be.items.get(PRENDA));
                    if (deLaPrenda != null && !be.fijadasPorCategoria.get(deLaPrenda).isEmpty()) {
                        be.ultimoPorCategoria.put(deLaPrenda, new ArrayList<>(be.fijadasPorCategoria.get(deLaPrenda)));
                    }
                    // Cada Modelar gasta un uso de la tijera (2026-10-05); la creativa no gasta.
                    if (!com.modamod.util.MaquinaCreativa.es(be) && world instanceof ServerWorld sw) {
                        be.tijera.damage(1, sw, null, item -> { });
                        if (be.tijera.isEmpty()) {
                            be.tijera = ItemStack.EMPTY;
                            be.sonar(SoundEvents.ENTITY_ITEM_BREAK, 0.8f, 1.0f);
                        }
                    }
                    be.sincronizar();
                } else if (be.encendida && (!prendaOk || !fijadaOk || !be.tijeraOk())) {
                    if (prendaOk && fijadaOk) be.avisarSinTijera();
                    // Prendida sin nada que hacer (mundos guardados con el
                    // bug de arriba, o sacaron la prenda por una tolva):
                    // se apaga sola en vez de quedar trabada.
                    be.encendida = false;
                    be.sincronizar();
                }
            }
            case PROCESANDO -> {
                be.progreso++;
                // Diseño sonoro (2026-09-22, "aplica los mismos [sonidos]
                // a las otras dos" — clon del de SublimadoraBlockEntity —
                // + "sonido de tijeras y maquinas de coser"): pitido en
                // cada flanco del LED rojo, tijera de la guillotina cada
                // tanto, clic-clic rápido de la aguja/máquina de coser.
                if (world.getTime() % 20 == 0) be.sonar(SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), 0.25f, 2.0f);
                if (be.progreso % 32 == 0) {
                    be.sonar(SoundEvents.ENTITY_SHEEP_SHEAR, 0.5f, 0.9f + world.getRandom().nextFloat() * 0.2f);
                }
                if (be.progreso % 4 == 0) {
                    be.sonar(SoundEvents.BLOCK_BAMBOO_WOOD_HIT, 0.3f, 1.6f + (world.getRandom().nextFloat() - 0.5f) * 0.3f);
                }
                // Partículas de las partes móviles de arriba (2026-09-23,
                // "mas particulas arriba de la modeladora en la guillotina
                // y la maquina de coser"): un tijeretazo de polvo cada
                // corte, hilo de color (el de la prenda) en cada puntada.
                if (world instanceof ServerWorld servidor) {
                    if (be.progreso % 32 == 0) tijeretazo(servidor, pos);
                    if (be.progreso % 8 == 0) hilada(servidor, pos, be.items.get(PRENDA));
                }
                // Sync periódica para que la barra de progreso (y el resto
                // de lo que depende de estado()/progreso() del lado
                // cliente) se vea avanzar en vivo, no solo al terminar —
                // mismo intervalo que TinturasBlockEntity#tick.
                if (be.progreso % 20 == 0) be.sincronizar();
                if (be.progreso >= com.modamod.util.MaquinaCreativa.duracion(be, TICKS_PROCESO)) {
                    ItemStack resultado = be.procesar(be.items.get(PRENDA));
                    be.items.set(SALIDA, resultado);
                    be.items.set(PRENDA, ItemStack.EMPTY);
                    be.estado = Estado.LISTO;
                    // Se apaga sola al terminar — sin esto, con el botón de
                    // Encender ya sacado de la GUI, no habría forma de
                    // volver a abrirla para cargar la siguiente prenda.
                    be.encendida = false;
                    // Campanita + carrillón juntos, igual que el LED verde
                    // de Sublimadora — clon exacto de su combo de "listo".
                    be.sonar(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.0f, 1.5f);
                    be.sonar(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
                    be.sincronizar();
                }
            }
            case LISTO -> {
                if (!be.items.get(SALIDA).isEmpty()) {
                    be.empujarSalida(world, pos);
                }
                if (be.items.get(SALIDA).isEmpty()) {
                    be.estado = Estado.REPOSO;
                    be.sincronizar();
                }
            }
        }
    }

    /**
     * Ráfaga de viento cerca del ventilador (2026-09-22, "efectos
     * visuales como de carga de viento") — punto de salida relevado a
     * mano desde el pivote real del hueso "fan" en
     * {@code garment_shaper.geo.json} (X≈-1.5, Y≈5.8, Z≈7.71 en espacio
     * de modelo Blockbench, /16 + centro de bloque). Mismo criterio de
     * spawnParticles que {@code SublimadoraBlockEntity#vapor}.
     */
    private static void rafaga(ServerWorld world, BlockPos pos) {
        // Arriba del bloque (2026-09-22, "las particulas de la modeladora
        // que aparezcan arriba") — no en la posición real del ventilador
        // (que queda pegado al costado, poco visible); centrado en X/Z,
        // justo por encima de la máquina (su modelo llega a Y≈1 real).
        double cx = pos.getX() + 0.5, cy = pos.getY() + 1.15, cz = pos.getZ() + 0.5;
        world.spawnParticles(ParticleTypes.SMALL_GUST, cx, cy, cz, 0, 0, 0.02, 0, 1.0);
    }

    /**
     * Tijeretazo de polvo en la guillotina — punto relevado a mano desde
     * su pivote real ({@code garment_shaper.geo.json}, X≈-6.4, Y≈13.1,
     * Z≈0 en espacio de modelo, /16 + centro de bloque).
     */
    private static void tijeretazo(ServerWorld world, BlockPos pos) {
        double x = pos.getX() + 0.1, y = pos.getY() + 0.82, z = pos.getZ() + 0.5;
        world.spawnParticles(ParticleTypes.CLOUD, x, y, z, 4, 0.15, 0.08, 0.15, 0.02);
    }

    /**
     * Puntada de hilo de color en la aguja — mismo punto relevado que
     * {@link #tijeretazo} pero del lado de "needle" (X≈6.4, Y≈11.6,
     * Z≈-3.4). El color es el de la prenda que se está cosiendo, no uno
     * fijo — mismo criterio que {@code TinturasGeoModel#colorDePrenda}.
     */
    private static void hilada(ServerWorld world, BlockPos pos, ItemStack prenda) {
        int rgb = colorDeHilo(prenda);
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        DustParticleEffect efecto = new DustParticleEffect(new org.joml.Vector3f(r, g, b), 0.8f);
        double x = pos.getX() + 0.9, y = pos.getY() + 0.73, z = pos.getZ() + 0.29;
        world.spawnParticles(efecto, x, y, z, 2, 0.05, 0.05, 0.05, 0.01);
    }

    /** Color plano de la prenda para el hilo — mismos tipos que {@link #categoriaDe}, blanco si no hay ninguna reconocida. */
    private static int colorDeHilo(ItemStack stack) {
        if (stack.isEmpty()) return 0xFFFFFF;
        if (stack.getItem() instanceof com.modamod.sublimadora.RemeraItem) {
            return com.modamod.sublimadora.RemeraItem.color(stack);
        }
        return com.modamod.region.RegionResolver.colorBase(stack, com.modamod.region.Lado.IZQUIERDA);
    }

    private void sonar(SoundEvent evento, float volumen, float tono) {
        if (world != null) world.playSound(null, pos, evento, SoundCategory.BLOCKS, volumen, tono);
    }

    /**
     * {@code markDirty()} sola NO manda el paquete de sync al cliente
     * (solo marca el chunk para guardar) — bug real (2026-09-22, "no
     * prende los leds"/"no anima la guillotina"): las 3 transiciones de
     * {@link #tick} solo llamaban {@code markDirty()}, así que el
     * cliente nunca se enteraba del cambio de {@code estado} fuera de
     * cuando la GUI estaba abierta (el {@code PropertyDelegate} la
     * sincroniza aparte) — ni el LED ({@link ModeladoGeoModel#coloresLed})
     * ni el controlador de animación de guillotina/aguja (que leen
     * {@code estado()} del lado cliente) veían nunca PROCESANDO. Mismo
     * mecanismo que {@code TinturasBlockEntity#sincronizar}.
     */
    private void sincronizar() {
        markDirty();
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    // ── Inventory ────────────────────────────────────────────────────

    @Override
    public int size() { return TAMANO + 1; }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : items) if (!s.isEmpty()) return false;
        return tijera.isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) { return slot == SLOT_TIJERA ? tijera : items.get(slot); }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        if (slot == SLOT_TIJERA) return sacarTijera();
        ItemStack antes = items.get(slot).copy();
        ItemStack r = Inventories.splitStack(items, slot, amount);
        if (!r.isEmpty()) {
            markDirty();
            if (slot == PRENDA || slot == SALIDA) sincronizar();
            if (pinDeSlot(slot) >= 0) pinCambio(pinDeSlot(slot), antes, items.get(slot));
        }
        return r;
    }

    @Override
    public ItemStack removeStack(int slot) {
        if (slot == SLOT_TIJERA) return sacarTijera();
        ItemStack antes = items.get(slot).copy();
        ItemStack r = Inventories.removeStack(items, slot);
        if ((slot == PRENDA || slot == SALIDA) && !antes.isEmpty()) sincronizar();
        if (pinDeSlot(slot) >= 0 && !antes.isEmpty()) pinCambio(pinDeSlot(slot), antes, ItemStack.EMPTY);
        return r;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot == SLOT_TIJERA) {
            tijera = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
            sincronizar();
            return;
        }
        int pin = pinDeSlot(slot);
        ItemStack antesPin = pin >= 0 ? items.get(slot).copy() : ItemStack.EMPTY;
        items.set(slot, stack);
        if (slot == PRENDA && !stack.isEmpty()) {
            // La GUI muestra el tipo de la prenda que entró (2026-10-04, "al poner un x input... se seleccione
            // automaticamente la gui de ese tipo de prenda").
            Categoria entra = categoriaDe(stack);
            if (entra != null) categoria = entra;
        }
        if (pin >= 0) {
            if (stack.getCount() > 1) stack.setCount(1);
            pinCambio(pin, antesPin, stack);
            if (!stack.isEmpty() && pinCombo[pin] != null && chinchetaPin(pin)) sincronizar();
        }
        if (stack.getCount() > getMaxCountPerStack()) stack.setCount(getMaxCountPerStack());
        if (slot == PRENDA && !stack.isEmpty() && (com.modamod.util.InventarioUtil.enCadena && linea)
                && guiAbiertas == 0 && !hayFijadas() && estado == Estado.REPOSO
                && !encendida && items.get(SALIDA).isEmpty()) {
            // Llegó por la cadena y no hay nada que cortar: la saltea
            // (2026-10-04, "me parece perfecto que saltee") y sale a la derecha.
            items.set(SALIDA, stack);
            items.set(PRENDA, ItemStack.EMPTY);
            estado = Estado.LISTO;
            markDirty();
            sincronizar();
            return;
        }
        if (slot == PRENDA && !stack.isEmpty() && guiAbiertas == 0 && hayFijadas()) {
            // Arranca sola al cargar la prenda POR ARRIBA (hopper/bloque) con
            // algo fijado (2026-09-22, "que arranque cuando le ponen prenda").
            // NUNCA con la GUI abierta (2026-09-26): ahí se prendía sola al
            // apoyar la remera en el slot y, ya "encendida", la config quedaba
            // bloqueada (isValid/botones devuelven false) — el "despineo
            // trabado" y el molde de red que no entraba. Con la GUI abierta
            // arranca con el botón Modelar (cerrar la pantalla ya no enciende, 2026-10-15).
            encenderAlCerrar();
        }
        markDirty();
        if (slot == PRENDA || slot == SALIDA) sincronizar();
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        if (slot == SLOT_TIJERA) return tijera.isEmpty() && stack.isOf(net.minecraft.item.Items.SHEARS);
        if (encendida) return false; // apagada para tocar el inventario, salvo la prenda física (ver ModeladoBlock)
        if (slot == SALIDA) return false;
        if (slot == PRENDA) return esPrendaModelable(stack);
        if (pinDeSlot(slot) >= 0) {
            int p = pinDeSlot(slot);
            Categoria cat = Categoria.values()[p / PINES_POR_CATEGORIA];
            boolean ok = categoria == cat && pinAcepta(cat, p % PINES_POR_CATEGORIA, stack);
            if (!ok) LOG.info("isValid(pin) RECHAZADO: item={} clase={} pin={} cat={} categoriaActual={} rol={}",
                    stack.getItem(), stack.getItem().getClass().getSimpleName(), p % PINES_POR_CATEGORIA, cat, categoria,
                    ROLES[cat.ordinal()][p % PINES_POR_CATEGORIA]);
            return ok;
        }
        if (slot == POLLERA_ACTIVO || slot == CAPA_ACTIVO || slot == SOMBRERO_ACTIVO || slot == BANDA_ACTIVO || slot == CALZADO_ACTIVO || slot == DELANTAL_ACTIVO) return false; // sin uso, igual que los otros (ver abajo)
        if (slot >= ACTIVO_INICIO && slot < ACTIVO_FIN) {
            Categoria cat = Categoria.values()[slot - ACTIVO_INICIO];
            // El Activo de REMERA quedó sin uso (2026-09-24, esquema de
            // pines) — se rechaza cualquier cosa ahí para que no quede un
            // molde invisible atrás del diagrama sin ninguna pista visual.
            if (cat != null) return false;
            return esMoldeDeCategoria(stack, cat);
        }
        if (slot >= POLLERA_PORPRENDA_INICIO && slot < POLLERA_PORPRENDA_INICIO + PORPRENDA_TOTAL) {
            return sirveEnBanco(stack, Categoria.POLLERA) || categoriaDe(stack) == Categoria.POLLERA;
        }
        if (slot >= CAPA_PORPRENDA_INICIO && slot < CAPA_PORPRENDA_INICIO + PORPRENDA_TOTAL) {
            return sirveEnBanco(stack, Categoria.CAPA) || categoriaDe(stack) == Categoria.CAPA;
        }
        if (slot >= BANDA_PORPRENDA_INICIO && slot < BANDA_PORPRENDA_INICIO + PORPRENDA_TOTAL) {
            return sirveEnBanco(stack, Categoria.BANDA) || categoriaDe(stack) == Categoria.BANDA;
        }
        if (slot >= DELANTAL_PORPRENDA_INICIO && slot < DELANTAL_PORPRENDA_INICIO + PORPRENDA_TOTAL) {
            return sirveEnBanco(stack, Categoria.DELANTAL) || categoriaDe(stack) == Categoria.DELANTAL;
        }
        if (slot >= CALZADO_PORPRENDA_INICIO && slot < CALZADO_PORPRENDA_INICIO + PORPRENDA_TOTAL) {
            return sirveEnBanco(stack, Categoria.CALZADO) || categoriaDe(stack) == Categoria.CALZADO;
        }
        if (slot >= SOMBRERO_PORPRENDA_INICIO && slot < SOMBRERO_PORPRENDA_INICIO + PORPRENDA_TOTAL) {
            return sirveEnBanco(stack, Categoria.SOMBRERO) || categoriaDe(stack) == Categoria.SOMBRERO;
        }
        if ((slot >= PORPRENDA_INICIO && slot < PORPRENDA_FIN) || (slot >= PORPRENDA_EXTRA_INICIO && slot < PORPRENDA_EXTRA_FIN)) {
            Categoria cat = slot < PORPRENDA_FIN
                    ? Categoria.values()[(slot - PORPRENDA_INICIO) / PORPRENDA_POR_CATEGORIA]
                    : Categoria.values()[(slot - PORPRENDA_EXTRA_INICIO) / PORPRENDA_EXTRA_POR_CATEGORIA];
            // Banco "Moldes de <Categoria>" (2026-09-24, a pedido): solo
            // moldes EXCLUSIVOS de esta categoría + la prenda terminada de
            // esta categoría — los compartidos (Rango/Calce/Red/Torso/
            // Corte/Materiales) van al almacén general, no acá.
            return sirveEnBanco(stack, cat) || categoriaDe(stack) == cat;
        }
        // Almacén general: moldes que sirven para 2+ categorías (2026-09-24,
        // "en el almacen de prendas van los moldes que sirven para mas de una prenda").
        return esMoldeCompartido(stack);
    }

    // ── SidedInventory: la prenda física por ARRIBA (2026-09-21, "que las
    // tres carguen por arriba" — reemplaza el costado izquierdo de
    // 2026-09-20). Nada más se expone: ni almacén ni activo ni salida son
    // alcanzables por hopper todavía (a propósito, fuera de alcance de
    // este pedido).
    private net.minecraft.util.math.Direction ladoIzquierdo() {
        return getCachedState().get(ModeladoBlock.FACING).getOpposite().rotateYCounterclockwise();
    }

    @Override
    public net.minecraft.util.math.Direction ladoSalida() { return ladoDerecho(); }

    /** El lado opuesto al de carga — hacia ahí se empuja el resultado (ver {@link #tick}, caso LISTO). */
    private net.minecraft.util.math.Direction ladoDerecho() {
        return ladoIzquierdo().getOpposite();
    }

    /**
     * Empuja el resultado hacia la derecha — a pedido (2026-09-20, "las 3
     * maquinas hacen como el crafter..."), mismo mecanismo que
     * {@code SublimadoraBlockEntity#empujarSalida}.
     */
    /**
     * Línea de producción (2026-10-08, "un boton activador de la linea de produccion en la gui para que se los pueda
     * usar individuales por default sin que se pasen los items"): apagada de fábrica, la máquina no empuja su salida
     * al vecino ni saltea las prendas que le llegan por la cadena; la prenda queda para sacarla a mano.
     */
    private boolean linea;

    public boolean linea() { return linea; }

    /** Alterna la línea de producción (botón del frente, 2026-10-08): igual que el botón de la pantalla. */
    public void alternarLinea() {
        linea = !linea;
        markDirty();
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    public static final int BTN_LINEA = 900;

    private void empujarSalida(net.minecraft.world.World world, BlockPos pos) {
        ItemStack actual = items.get(SALIDA);
        if (actual.isEmpty() || !linea) return;
        net.minecraft.util.math.Direction derecha = ladoDerecho();
        ItemStack sobrante = com.modamod.util.InventarioUtil.empujarA(
                world, pos.offset(derecha), derecha.getOpposite(), actual);
        if (sobrante.getCount() != actual.getCount()) {
            items.set(SALIDA, sobrante);
            markDirty();
        }
    }

    @Override
    public int[] getAvailableSlots(net.minecraft.util.math.Direction side) {
        // También por la IZQUIERDA (2026-10-04, "poder cargarles prendas por la izquierda"):
        // es la cara donde empalma la salida de la máquina de al lado.
        if (side == net.minecraft.util.math.Direction.UP || side == ladoIzquierdo()) return new int[]{PRENDA};
        // Las tijeras entran por cualquier otro lado menos abajo (2026-10-05).
        return side == net.minecraft.util.math.Direction.DOWN ? new int[0] : new int[]{SLOT_TIJERA};
    }

    @Override
    public int topeEntrada(int slot) { return slot == PRENDA ? 1 : getMaxCountPerStack(); }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.util.math.Direction dir) {
        if (dir != null && com.modamod.util.Redstone.pausada(this)) return false;   // con señal, nada entra por automatización
        if (PRENDA == slot && !getStack(slot).isEmpty()) return false;   // una prenda por vez

        return isValid(slot, stack);
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, net.minecraft.util.math.Direction dir) {
        return false;
    }

    /**
     * Cualquier tipo de molde reconocido, exclusivo o compartido — antes
     * (2026-09-24) le faltaban Rango/Torso/Calce/Red/Cuello/
     * ClothingPatternItem, así que esos 6 tipos no se detectaban como
     * molde en absoluto para el almacén general ni el shift-click (bug de
     * paso, arreglado junto con las reglas nuevas de storage). Ver
     * {@link #esMoldeExclusivoDe}/{@link #esMoldeCompartido} para el
     * detalle de EN QUÉ banco va cada uno.
     */
    public static boolean esMolde(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof MoldeItem
                || (item instanceof MoldeEnBlancoItem b && (b.tipo == MoldeEnBlancoItem.Tipo.MOLDE || b.tipo == MoldeEnBlancoItem.Tipo.TRAMA))
                || item instanceof MoldeRangoItem
                || item instanceof MoldeCalceItem || item instanceof MoldeRedItem
                || item instanceof MoldeCuelloItem
                || item instanceof MoldePolleraItem || item instanceof MoldeVoladoItem || item instanceof MoldeRuedoItem || item instanceof MoldeCapaItem || item instanceof MoldeSombreroItem
                || item instanceof MoldeBandaItem || item instanceof MoldeChaquetaItem || item instanceof MoldeBorcegoItem
                || item instanceof MoldeFormaDelantalItem || item instanceof MoldeTirantesDelantalItem || item instanceof MoldeCoberturaDelantalItem;
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return world != null && world.getBlockEntity(pos) == this
                && player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clear() { items.clear(); tijera = ItemStack.EMPTY; }

    // ── persistencia ─────────────────────────────────────────────────

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        com.modamod.util.DisenosMaquina.leer(nbt, blancoDiseno, lookup);
        items.clear();
        // Los primeros TAMANO_VIEJO van en la lista de siempre; lo de la
        // Pollera, aparte (el índice de slot de Inventories es un byte).
        DefaultedList<ItemStack> viejos = DefaultedList.ofSize(TAMANO_VIEJO, ItemStack.EMPTY);
        Inventories.readNbt(nbt, viejos, lookup);
        for (int i = 0; i < TAMANO_VIEJO; i++) items.set(i, viejos.get(i));
        DefaultedList<ItemStack> nuevos = DefaultedList.ofSize(INICIO_LISTA_2 - TAMANO_VIEJO, ItemStack.EMPTY);
        Inventories.readNbt(nbt.getCompound("pollera_items"), nuevos, lookup);
        for (int i = 0; i < nuevos.size(); i++) items.set(TAMANO_VIEJO + i, nuevos.get(i));
        // Segunda lista (2026-10-11): lo que no entra en el byte de la primera.
        DefaultedList<ItemStack> segunda = DefaultedList.ofSize(TAMANO - INICIO_LISTA_2, ItemStack.EMPTY);
        Inventories.readNbt(nbt.getCompound("delantal_items"), segunda, lookup);
        for (int i = 0; i < segunda.size(); i++) items.set(INICIO_LISTA_2 + i, segunda.get(i));
        bolsa.clear();
        NbtList listaBolsa = nbt.getList("bolsa", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < listaBolsa.size(); i++) {
            NbtCompound e = listaBolsa.getCompound(i);
            net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(e.getString("id"));
            if (id != null && e.getInt("n") > 0) bolsa.put(id, e.getInt("n"));
        }
        tijera = nbt.contains("tijera") ? ItemStack.fromNbtOrEmpty(lookup, nbt.getCompound("tijera")) : ItemStack.EMPTY;
        encendida = nbt.getBoolean("encendida");
        estado = Estado.values()[nbt.getInt("estado")];
        progreso = nbt.getInt("progreso");
        remeraLargo = Variante.Largo.values()[nbt.getInt("remera_largo")];
        remeraManga = Variante.Manga.values()[nbt.getInt("remera_manga")];
        remeraCuello = Variante.Cuello.values()[nbt.getInt("remera_cuello")];
        calientabrazosCobertura = Variante.Manga.values()[nbt.getInt("calientabrazos_cobertura")];
        anclaje = Anclaje.values()[nbt.getInt("anclaje")];
        ladoBorrador = Lado.values()[nbt.getInt("lado_borrador")];
        categoria = Categoria.values()[nbt.getInt("categoria")];
        remeraSimetria = !nbt.contains("remera_simetria") || nbt.getBoolean("remera_simetria");
        linea = nbt.getBoolean("linea");
        cajones.clear();
        if (nbt.contains("cajones_bits")) {
            cajones.or(java.util.BitSet.valueOf(nbt.getLongArray("cajones_bits")));
        }

        for (Categoria cat : Categoria.values()) {
            List<ComboCorte> destino = fijadasPorCategoria.get(cat);
            destino.clear();
            NbtList lista = nbt.getList("fijadas_" + cat.name(), NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < lista.size(); i++) {
                ComboCorte.CODEC.parse(NbtOps.INSTANCE, lista.getCompound(i))
                        .result().ifPresent(destino::add);
            }
        }

        for (Categoria cat : Categoria.values()) {
            List<DisenoGuardado> destino = disenosPorCategoria.get(cat);
            destino.clear();
            NbtList disenos = nbt.getList("disenos_" + cat.name(), NbtElement.COMPOUND_TYPE);
            for (int d = 0; d < disenos.size(); d++) {
                NbtCompound uno = disenos.getCompound(d);
                String nombre = uno.getString("nombre");
                NbtList combosNbt = uno.getList("combos", NbtElement.COMPOUND_TYPE);
                List<ComboCorte> combos = new ArrayList<>();
                for (int i = 0; i < combosNbt.size(); i++) {
                    ComboCorte.CODEC.parse(NbtOps.INSTANCE, combosNbt.getCompound(i)).result().ifPresent(combos::add);
                }
                destino.add(new DisenoGuardado(nombre, combos));
            }
        }

        disenosItem.clear();
        NbtList listaDisenos = nbt.getList("disenos_item", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < listaDisenos.size(); i++) {
            ItemStack d = ItemStack.fromNbtOrEmpty(lookup, listaDisenos.getCompound(i));
            if (!d.isEmpty()) disenosItem.add(d);
        }
        ultimoPorCategoria.clear();
        for (Categoria cat : Categoria.values()) {
            NbtList lista = nbt.getList("ultimo_" + cat.name(), NbtElement.COMPOUND_TYPE);
            List<ComboCorte> combos = new ArrayList<>();
            for (int i = 0; i < lista.size(); i++) {
                ComboCorte.CODEC.parse(NbtOps.INSTANCE, lista.getCompound(i)).result().ifPresent(combos::add);
            }
            if (!combos.isEmpty()) ultimoPorCategoria.put(cat, combos);
        }

        for (int i = 0; i < PINES_LOGICOS; i++) {
            pinCombo[i] = null;
            pinFijado[i] = nbt.getBoolean("pin_fijado_" + i);
            if (nbt.contains("pin_combo_" + i)) {
                int idx = i;
                ComboCorte.CODEC.parse(NbtOps.INSTANCE, nbt.get("pin_combo_" + i))
                        .result().ifPresent(c -> pinCombo[idx] = c);
            }
            // Un pin que en el layout actual no existe (NINGUNO) pudo quedar fijado de un layout viejo:
            // se descarta, si no dibuja un ícono fantasma en (0,0) y deja un corte huérfano.
            if (ROLES[i / PINES_POR_CATEGORIA][i % PINES_POR_CATEGORIA] == Rol.NINGUNO) {
                if (pinCombo[i] != null) fijadasPorCategoria.get(Categoria.values()[i / PINES_POR_CATEGORIA]).remove(pinCombo[i]);
                pinCombo[i] = null;
                pinFijado[i] = false;
            }
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        com.modamod.util.DisenosMaquina.escribir(nbt, blancoDiseno, lookup);
        DefaultedList<ItemStack> viejos = DefaultedList.ofSize(TAMANO_VIEJO, ItemStack.EMPTY);
        for (int i = 0; i < TAMANO_VIEJO; i++) viejos.set(i, items.get(i));
        Inventories.writeNbt(nbt, viejos, lookup);
        DefaultedList<ItemStack> nuevos = DefaultedList.ofSize(INICIO_LISTA_2 - TAMANO_VIEJO, ItemStack.EMPTY);
        for (int i = 0; i < nuevos.size(); i++) nuevos.set(i, items.get(TAMANO_VIEJO + i));
        NbtCompound polleraNbt = new NbtCompound();
        Inventories.writeNbt(polleraNbt, nuevos, lookup);
        nbt.put("pollera_items", polleraNbt);
        DefaultedList<ItemStack> segunda = DefaultedList.ofSize(TAMANO - INICIO_LISTA_2, ItemStack.EMPTY);
        for (int i = 0; i < segunda.size(); i++) segunda.set(i, items.get(INICIO_LISTA_2 + i));
        NbtCompound segundaNbt = new NbtCompound();
        Inventories.writeNbt(segundaNbt, segunda, lookup);
        nbt.put("delantal_items", segundaNbt);
        if (!tijera.isEmpty()) nbt.put("tijera", tijera.encode(lookup));
        NbtList listaBolsa = new NbtList();
        for (var e : bolsa.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putString("id", e.getKey().toString());
            c.putInt("n", e.getValue());
            listaBolsa.add(c);
        }
        nbt.put("bolsa", listaBolsa);
        nbt.putBoolean("encendida", encendida);
        nbt.putInt("estado", estado.ordinal());
        nbt.putInt("progreso", progreso);
        nbt.putInt("remera_largo", remeraLargo.ordinal());
        nbt.putInt("remera_manga", remeraManga.ordinal());
        nbt.putInt("remera_cuello", remeraCuello.ordinal());
        nbt.putInt("calientabrazos_cobertura", calientabrazosCobertura.ordinal());
        nbt.putInt("anclaje", anclaje.ordinal());
        nbt.putInt("lado_borrador", ladoBorrador.ordinal());
        nbt.putInt("categoria", categoria.ordinal());
        nbt.putBoolean("remera_simetria", remeraSimetria);
        nbt.putBoolean("linea", linea);
        nbt.putLongArray("cajones_bits", cajones.toLongArray());

        for (Categoria cat : Categoria.values()) {
            NbtList lista = new NbtList();
            for (ComboCorte combo : fijadasPorCategoria.get(cat)) {
                ComboCorte.CODEC.encodeStart(NbtOps.INSTANCE, combo).result().ifPresent(lista::add);
            }
            nbt.put("fijadas_" + cat.name(), lista);
        }

        for (Categoria cat : Categoria.values()) {
            NbtList disenos = new NbtList();
            for (DisenoGuardado uno : disenosPorCategoria.get(cat)) {
                NbtList combosNbt = new NbtList();
                for (ComboCorte combo : uno.combos()) {
                    ComboCorte.CODEC.encodeStart(NbtOps.INSTANCE, combo).result().ifPresent(combosNbt::add);
                }
                NbtCompound unoNbt = new NbtCompound();
                unoNbt.putString("nombre", uno.nombre());
                unoNbt.put("combos", combosNbt);
                disenos.add(unoNbt);
            }
            nbt.put("disenos_" + cat.name(), disenos);
        }

        NbtList listaDisenos = new NbtList();
        for (ItemStack d : disenosItem) listaDisenos.add(d.encode(lookup));
        nbt.put("disenos_item", listaDisenos);
        for (var e : ultimoPorCategoria.entrySet()) {
            NbtList lista = new NbtList();
            for (ComboCorte combo : e.getValue()) {
                ComboCorte.CODEC.encodeStart(NbtOps.INSTANCE, combo).result().ifPresent(lista::add);
            }
            nbt.put("ultimo_" + e.getKey().name(), lista);
        }

        for (int i = 0; i < PINES_LOGICOS; i++) {
            nbt.putBoolean("pin_fijado_" + i, pinFijado[i]);
            if (pinCombo[i] != null) {
                int idx = i;
                ComboCorte.CODEC.encodeStart(NbtOps.INSTANCE, pinCombo[i]).result()
                        .ifPresent(e -> nbt.put("pin_combo_" + idx, e));
            }
        }
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.modamod.modelado");
    }

    /**
     * Sin esto la lista de fijadas (y el resto del NBT) nunca llega al
     * cliente en vivo — el PropertyDelegate solo cubre los campos escalares
     * del borrador, no una lista de tamaño variable. Mismo mecanismo que ya
     * usa {@code SublimadoraBlockEntity} para sus barras de tinta.
     */
    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    @Override
    public net.minecraft.network.packet.Packet<net.minecraft.network.listener.ClientPlayPacketListener> toUpdatePacket() {
        return net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket.create(this);
    }

    private final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> encendida ? 1 : 0;
                case 1 -> estado.ordinal();
                case 2 -> progreso;
                case 3 -> remeraLargo.ordinal();
                case 4 -> remeraManga.ordinal();
                case 5 -> remeraCuello.ordinal();
                case 6 -> calientabrazosCobertura.ordinal();
                case 7 -> anclaje.ordinal();
                case 8 -> ladoBorrador.ordinal();
                case 9 -> categoria.ordinal();
                case 10 -> remeraSimetria ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> encendida = value != 0;
                case 1 -> estado = Estado.values()[value];
                case 2 -> progreso = value;
                case 3 -> remeraLargo = Variante.Largo.values()[value];
                case 4 -> remeraManga = Variante.Manga.values()[value];
                case 5 -> remeraCuello = Variante.Cuello.values()[value];
                case 6 -> calientabrazosCobertura = Variante.Manga.values()[value];
                case 7 -> anclaje = Anclaje.values()[value];
                case 8 -> ladoBorrador = Lado.values()[value];
                case 9 -> categoria = Categoria.values()[value];
                case 10 -> remeraSimetria = value != 0;
                default -> {}
            }
        }

        @Override
        public int size() { return 11; }
    };

    public PropertyDelegate getPropertyDelegate() { return propertyDelegate; }

    @Override
    public ScreenHandler createMenu(int syncId, net.minecraft.entity.player.PlayerInventory inv, PlayerEntity player) {
        return new ModeladoScreenHandler(syncId, inv, this);
    }

    @Override
    public BlockPos getScreenOpeningData(net.minecraft.server.network.ServerPlayerEntity player) {
        return this.pos;
    }
}
