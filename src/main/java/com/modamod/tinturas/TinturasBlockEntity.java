package com.modamod.tinturas;

import com.modamod.garment.Garment;
import com.modamod.garment.Garments;
import com.modamod.item.ClothingPatternItem;
import com.modamod.item.ModamodDye;
import com.modamod.item.TamanoPatron;
import com.modamod.region.Lado;
import com.modamod.region.Operacion;
import com.modamod.region.RegionResolver;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Estación de Tintes — v2 (a pedido, 2026-09-17). Mezcla CMYK real (no 16
 * DyeColor fijos como planeaba `docs/archivo/MAQUINAS.md` originalmente — "va a
 * usar cmyk", mismo tanque que la Sublimadora) + sistema de fijadas
 * "como el de la Modeladora":
 *
 * <ul>
 *   <li><b>Borrador</b>: los 4 sliders CMYK + el slot Activo (un patrón,
 *   opcional) + el ciclo de Lado — sirven SOLO para construir una fijada
 *   nueva, igual que el Activo/Anclaje/Lado de {@code ModeladoBlockEntity}
 *   no aplican nada por sí solos hasta que se aprieta Fijar.</li>
 *   <li><b>Fijar</b>: copia el borrador actual (mezcla + el patronId del
 *   slot Activo, si hay algo puesto ahí + lado) a una fijada nueva en la
 *   lista (tope 8), y la deja seleccionada.</li>
 *   <li><b>Fijadas</b>: lista de looks guardados. A diferencia de
 *   Modeladora (donde TODAS se aplican juntas, son ejes distintos), acá
 *   son alternativas mutuamente excluyentes — un tinte es UN color, así
 *   que aplicar usa la fijada SELECCIONADA nada más. Click en una la
 *   selecciona; click de nuevo sobre la ya seleccionada la borra.</li>
 *   <li><b>Almacén</b>: una sola grilla (no una por categoría, acá no hay
 *   categorías) para guardar patrones a mano — igual que el almacén
 *   compartido de Modeladora, es conveniencia, no obligatorio: un patrón
 *   nunca se consume, se puede usar directo desde el inventario.</li>
 * </ul>
 *
 * Aplicar sigue funcionando con click derecho directo con la prenda en
 * mano ({@link com.modamod.tinturas.TinturasBlock#onUseWithItem}), pero
 * desde 2026-09-21 ("que las tres tengan slot de entrada y de salida, asi
 * vemos la vista previa") también hay un slot de ENTRADA de verdad
 * ({@link #SLOT_PRENDA_ENTRADA}, visible en pantalla) y uno de SALIDA
 * ({@link #SLOT_SALIDA}) — mismo criterio que Modeladora/Sublimadora.
 *
 * Reusa sin tocar {@link RegionResolver} (color/patrón por lado) y
 * {@link Garment}/{@link Garments} (qué prenda acepta qué operación) —
 * ver la nota de migración en {@code ClothingLoomScreenHandler
 * .reformarRemera}, que ya anticipaba esta estación.
 */
public class TinturasBlockEntity extends BlockEntity
        implements ExtendedScreenHandlerFactory<BlockPos>, GeoBlockEntity, SidedInventory, com.modamod.util.ConSalida,
        com.modamod.util.MaquinaCreativa.Cargable , com.modamod.util.LimiteEntrada {

    // Mismos índices/convención que SublimadoraBlockEntity.
    public static final int C = 0, M = 1, Y = 2, K = 3;
    // Mismas claves de traducción que ya usa la Sublimadora (modamod.sublimadora.tinta.*).
    private static final String[] NOMBRE_CANAL = { "cian", "magenta", "amarillo", "negro" };

    /** Cuántas dosis entran por canal. Mismo número que el tanque CMYK de la Sublimadora. */
    // 64 y no 16 (2026-09-18, "subime la capacidad de tinta") — un stack
    // entero de tinte llena el tanque justo.
    public static final int CARGA_MAXIMA = 64;
    /** Niveles de cada slider: 0%, 25%, 50%, 75%, 100%. */
    // 21 y no 5 (2026-09-28, más control de color): de a 5% en vez de 25%.
    public static final int NIVELES_MEZCLA = 21;
    /**
     * Quinto canal de la mezcla, además de C/M/Y/K: TRANSPARENCIA de la
     * tinta (2026-09-28, "bajarle el alpha y convertirlo en recorte o
     * transparencia"). 0 = tela opaca de siempre, al máximo = recorte
     * (agujero). No gasta tinta. Viaja en el byte alto del color de la capa
     * (ver {@link #colorDeMezcla} y {@code ClothingTextureCache#mezclar}).
     */
    public static final int T = 4;
    /** Canales de una mezcla: C, M, Y, K y T. */
    public static final int CANALES = 5;
    /** Un solo almacén de patrones (a pedido, "y un solo almacén" — acá no hay categorías que lo dividan). */
    public static final int ALMACEN_TAMANO = 9;

    // ── inventario: almacén (9) + un slot de molde por CUADRADITO, POR CATEGORÍA ──
    public static final int ALMACEN_INICIO = 0;
    public static final int ALMACEN_FIN = ALMACEN_INICIO + ALMACEN_TAMANO;
    /**
     * Cuadraditos por categoría (2026-09-27, "poner un color y patron al
     * cuello otro a la manga otro al pecho"): los mismos 12 lugares del
     * esquema de la Modeladora ({@code ModeladoBlockEntity#ROLES}), cada
     * uno una CAPA de color propia — región ({@link #regionDe}), molde
     * opcional (sin molde = liso), mezcla CMYK, modo de mezcla y opacidad.
     * Reemplaza a las 3 capas fijas + la lista de fijadas de antes.
     */
    public static final int CASILLAS = 12;
    /** Pollera no tiene esquema: sus capas son 3 cuadraditos sueltos, todos de prenda entera. */
    public static final int CASILLAS_POLLERA = 3;
    /** Capa (2026-09-29, "forro aparte"): Exterior, Forro y Detalles (capucha y cuello alto). */
    public static final int CASILLAS_CAPA = 3;
    /** Las zonas del retazo de aplique (2026-10-04). */
    public static final int CASILLAS_APLIQUE = 3;
    /** El sombrero (2026-10-14, "teñibles por estacion de tintes"): ala, copa y cinta, como las 3 zonas del retazo. */
    public static final int CASILLAS_SOMBRERO = 3;
    /** Banda (cinto o choker, 2026-10-15): banda, borde y herraje. */
    public static final int CASILLAS_BANDA = 3;
    /** Calzado (2026-10-15): 3 zonas del pie derecho y 3 del izquierdo (con la simetría prendida el izquierdo copia al derecho). */
    public static final int CASILLAS_CALZADO = 6;
    /** Delantal (2026-10-11): prenda entera, peto y falda. */
    public static final int CASILLAS_DELANTAL = 3;
    public static final int CASILLAS_INICIO = ALMACEN_FIN;
    public static final int TAMANO = CASILLAS_INICIO + CASILLAS * Categoria.values().length;
    /** Opacidad en pasos de 10% (10..100). */
    public static final int PASO_OPACIDAD = 10;

    // 2026-09-20, a pedido ("que carguen por hopper atrás [tintas] y que
    // las 3 carguen prenda por hopper del lado izquierdo"): 5 slots más,
    // fuera del DefaultedList de siempre (que sigue siendo solo almacén+
    // activo, sin tocar el layout de la pantalla) — uno para la prenda
    // entrante y 4 "tanque como stack" para la tinta CMYK, mismo truco
    // que SublimadoraBlockEntity.
    public static final int SLOT_PRENDA_ENTRADA = TAMANO;
    public static final int SLOT_TINTA_BASE = TAMANO + 1; // .. +4 (C,M,Y,K)
    /**
     * Salida visible — a pedido (2026-09-21, "que las tres tengan slot de
     * entrada y de salida, asi vemos la vista previa"): antes la prenda
     * teñida se empujaba afuera EN EL MISMO tick que entraba (ver
     * {@code empujarPrenda}, ahora {@link #empujarSalida}), sin pasar por
     * ningún lugar visible — quedaba bien para el hopper pero no daba
     * pie a un preview real como el de Modeladora/Sublimadora. Ahora
     * {@link #SLOT_PRENDA_ENTRADA} se vacía apenas se tiñe con éxito y el
     * resultado pasa por acá antes de empujarse — mismo mecanismo que
     * {@code SublimadoraBlockEntity.salida}.
     */
    public static final int SLOT_SALIDA = SLOT_TINTA_BASE + 4;
    /**
     * Almacén más grande (2026-09-28, "quiero mas espacios de
     * almacenamiento"): los lugares nuevos van en una lista APARTE al
     * final del inventario — el almacén de siempre (0..8) está al
     * principio, y agrandarlo ahí corría los índices guardados de los
     * moldes de los cuadraditos. En pantalla se ven los 30 juntos, en una
     * grilla de 5x6 en la columna izquierda (ver TinturasScreenHandler).
     */
    public static final int ALMACEN_EXTRA = 21;
    public static final int SLOT_ALMACEN_EXTRA = SLOT_SALIDA + 1;
    public static final int ALMACEN_TOTAL = ALMACEN_TAMANO + ALMACEN_EXTRA;

    /** Índice de inventario del lugar {@code i} (0..ALMACEN_TOTAL-1) del almacén, tal como se ve en pantalla. */
    public static int slotAlmacen(int i) {
        return i < ALMACEN_TAMANO ? ALMACEN_INICIO + i : SLOT_ALMACEN_EXTRA + (i - ALMACEN_TAMANO);
    }
    /** Mismo orden que {@code C,M,Y,K} — duplicado de SublimadoraBlock.TINTES (paquete distinto). */
    private static final net.minecraft.item.Item[] TINTES = {
            net.minecraft.item.Items.CYAN_DYE, net.minecraft.item.Items.MAGENTA_DYE,
            net.minecraft.item.Items.YELLOW_DYE, net.minecraft.item.Items.BLACK_DYE };
    /**
     * La prenda esperando/en proceso de teñirse — insertada por hopper,
     * por el slot de la pantalla o por click derecho directo (ver
     * {@link #iniciarTenido}). Si al insertarla no se pudo arrancar el
     * ciclo (sin fijada elegida, sin tinta) se queda CRUDA acá esperando
     * — no hay reintento automático, hay que sacarla y volver a
     * insertarla cuando la condición cambie. Si sí arrancó, sigue
     * ocupando este campo (todavía cruda, visualmente) durante los
     * {@link #TICKS_TENIDO} del ciclo — ver {@link #tick}, que la
     * reemplaza por la versión teñida en {@link #salida} al terminar.
     */
    private ItemStack prendaEntrada = ItemStack.EMPTY;
    /** La prenda ya teñida, esperando salir — ver {@link #SLOT_SALIDA}. */
    private ItemStack salida = ItemStack.EMPTY;

    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(TAMANO, ItemStack.EMPTY);
    /** Los lugares nuevos del almacén — ver {@link #ALMACEN_EXTRA}. */
    private final DefaultedList<ItemStack> almacenExtra = DefaultedList.ofSize(ALMACEN_EXTRA, ItemStack.EMPTY);

    private final int[] cargas = new int[4];
    /**
     * "Vista" de {@code cargas[]} como ItemStack real — mismo bug/fix que
     * {@code SublimadoraBlockEntity.tintaSlotView} (2026-09-20, "el item
     * se vacia del hopper pero el indicador nunca sube"): un hopper vanilla
     * NO vuelve a llamar {@code setStack} cuando el slot ya tiene algo —
     * incrementa en memoria el ItemStack que le dio {@code getStack} y
     * listo llama {@code markDirty()}. Necesita ser la MISMA instancia
     * entre llamadas para que ese incremento no se pierda.
     */
    private final ItemStack[] tintaSlotView = { ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY };

    private void sincronizarVistaTanques() {
        for (int i = 0; i < 4; i++) {
            tintaSlotView[i] = cargas[i] <= 0 ? ItemStack.EMPTY : new ItemStack(TINTES[i], cargas[i]);
        }
    }
    /**
     * Un cuadradito del esquema = una capa de color (2026-09-27, "poner un
     * color y patron al cuello otro a la manga otro al pecho" / "concretizar
     * los colores seleccionados" / "controlar como se mezclan las capas").
     * Todo se edita EN VIVO sobre el cuadradito seleccionado; la chincheta
     * ({@link #chincheta}) lo "concretiza": lo suma al diseño que se aplica
     * (y si tenía un molde puesto, se queda con ese patrón y devuelve el
     * molde al almacén, igual que la Modeladora). Sin molde = color liso en
     * su región.
     */
    public static final class Casilla {
        public final int[] mezcla = new int[CANALES];
        public TamanoPatron tamano = TamanoPatron.GRANDE;
        public float angulo = 0f;
        public float posicion = 0.5f;
        /** null = la forma propia del molde. */
        @Nullable public com.modamod.render.PatronGenerador.Forma forma = null;
        public boolean invertido = false;
        public com.modamod.region.ModoMezcla modo = com.modamod.region.ModoMezcla.NORMAL;
        public int opacidad = 100;
        /** Cómo se reparte el motivo (solo moldes de motivo — las rayas la ignoran). */
        public com.modamod.render.Repeticion repeticion = com.modamod.render.Repeticion.GRILLA;
        /** Semilla de {@link com.modamod.render.Repeticion#DISPERSO} — el botón 🎲 la cambia. */
        public int semilla = 0;
        /** Mezclas CMYK de Color 2 y Color 3 (Fase 2, 2026-09-28) — Color 1 es {@link #mezcla}. */
        public final int[][] mezclasExtra = new int[2][CANALES];
        /** Cuántos colores usa la capa (1..3). */
        public int colores = 1;
        /** Contorno alrededor de cada motivo/raya, con el ÚLTIMO color activo. */
        public boolean contorno = false;
        /**
         * Fondo (2026-10-24, "elijo un patron y puedo cambiar el color del patron pero no del fondo de la prenda"): con
         * patrón, el ÚLTIMO color activo no va en el patrón sino que pinta liso toda la zona debajo de él. Pensado para
         * las categorías de un cuadradito por zona (capa, sombrero, banda, maletín, calzado).
         */
        public boolean fondo = false;
        /** Cómo varía el relleno entre los colores — ver {@link com.modamod.render.Variacion}. */
        public com.modamod.render.Variacion variacion = com.modamod.render.Variacion.FIJO;
        /** Qué color (0..2) editan los sliders ahora. */
        public int editando = 0;
        public boolean fijada = false;
        /**
         * Ojo cerrado en el panel de capas (Fase B, 2026-09-28): sigue
         * fijada y se guarda en los diseños, pero no se aplica al teñir, no
         * gasta tinta y no sale en la vista previa — "oculta también al
         * teñir", como en un editor de imágenes.
         */
        public boolean oculta = false;
        /** El patrón con el que quedó fijada (el molde ya volvió al almacén) — null = liso. */
        @Nullable public Identifier patronFijado = null;
        /**
         * Giro de cada motivo en su lugar y separación horizontal/vertical
         * (2026-09-30, "que los patrones si se puedan girar y que haya un
         * slider vertical y horizontal") — ver {@link com.modamod.render.DistribucionPatron}.
         */
        public float giroMotivo = 0f;
        public float distanciaH = 1f;
        public float distanciaV = 1f;
        /** Espejo del motivo, espejo alternado y simetría del torso (2026-09-30, "vamos con todo"). */
        public com.modamod.render.DistribucionPatron.Espejo espejo = com.modamod.render.DistribucionPatron.Espejo.NINGUNO;
        public com.modamod.render.DistribucionPatron.Alternancia alternancia =
                com.modamod.render.DistribucionPatron.Alternancia.NINGUNA;
        public boolean simetria = false;
        public com.modamod.render.DistribucionPatron.GiroAzar giroAzar =
                com.modamod.render.DistribucionPatron.GiroAzar.NINGUNO;
        /** Corrimiento horizontal de la grilla de motivos, 0..1 (0,5 = sin corrimiento). */
        public float posX = 0.5f;

        public com.modamod.render.DistribucionPatron distribucion() {
            return new com.modamod.render.DistribucionPatron(giroMotivo, distanciaH, distanciaV,
                    espejo, alternancia, simetria, giroAzar, posX);
        }

        public int color() { return colorDeMezcla(mezcla); }

        /** La mezcla del color {@code i} (0 = Color 1). */
        public int[] mezclaDe(int i) { return i == 0 ? mezcla : mezclasExtra[i - 1]; }

        public int colorDe(int i) { return colorDeMezcla(mezclaDe(i)); }

        Casilla copia() {
            Casilla c = new Casilla();
            System.arraycopy(mezcla, 0, c.mezcla, 0, CANALES);
            c.tamano = tamano;
            c.angulo = angulo;
            c.posicion = posicion;
            c.forma = forma;
            c.invertido = invertido;
            c.modo = modo;
            c.opacidad = opacidad;
            c.repeticion = repeticion;
            c.semilla = semilla;
            for (int i = 0; i < 2; i++) System.arraycopy(mezclasExtra[i], 0, c.mezclasExtra[i], 0, CANALES);
            c.colores = colores;
            c.contorno = contorno;
            c.fondo = fondo;
            c.variacion = variacion;
            c.editando = editando;
            c.fijada = fijada;
            c.oculta = oculta;
            c.patronFijado = patronFijado;
            c.giroMotivo = giroMotivo;
            c.distanciaH = distanciaH;
            c.distanciaV = distanciaV;
            c.espejo = espejo;
            c.alternancia = alternancia;
            c.simetria = simetria;
            c.giroAzar = giroAzar;
            c.posX = posX;
            return c;
        }

        NbtCompound aNbt() {
            NbtCompound c = new NbtCompound();
            c.putIntArray("Mezcla", mezcla);
            c.putString("Tamano", tamano.name());
            c.putFloat("Angulo", angulo);
            c.putFloat("Posicion", posicion);
            c.putString("Forma", forma == null ? "AUTO" : forma.name());
            c.putBoolean("Invertido", invertido);
            c.putString("Modo", modo.name());
            c.putInt("Opacidad", opacidad);
            c.putString("Repeticion", repeticion.name());
            c.putInt("Semilla", semilla);
            c.putIntArray("Mezcla2", mezclasExtra[0]);
            c.putIntArray("Mezcla3", mezclasExtra[1]);
            c.putInt("Colores", colores);
            c.putBoolean("Contorno", contorno);
            c.putBoolean("Fondo", fondo);
            c.putString("Variacion", variacion.name());
            c.putInt("Editando", editando);
            c.putBoolean("Fijada", fijada);
            c.putBoolean("Oculta", oculta);
            if (patronFijado != null) c.putString("Patron", patronFijado.toString());
            c.putFloat("GiroMotivo", giroMotivo);
            c.putFloat("DistanciaH", distanciaH);
            c.putFloat("DistanciaV", distanciaV);
            c.putString("Espejo", espejo.name());
            c.putString("Alternancia", alternancia.name());
            c.putBoolean("Simetria", simetria);
            c.putString("GiroAzar", giroAzar.name());
            c.putFloat("PosX", posX);
            return c;
        }

        static Casilla deNbt(NbtCompound c) {
            Casilla r = new Casilla();
            int[] m = c.getIntArray("Mezcla");
            for (int i = 0; i < CANALES && i < m.length; i++) r.mezcla[i] = MathHelper.clamp(m[i], 0, NIVELES_MEZCLA - 1);
            try {
                if (c.contains("Tamano")) r.tamano = TamanoPatron.valueOf(c.getString("Tamano"));
                String f = c.getString("Forma");
                r.forma = f.isEmpty() || "AUTO".equals(f) ? null : com.modamod.render.PatronGenerador.Forma.valueOf(f);
                if (c.contains("Modo")) r.modo = com.modamod.region.ModoMezcla.valueOf(c.getString("Modo"));
                if (c.contains("Repeticion")) r.repeticion = com.modamod.render.Repeticion.valueOf(c.getString("Repeticion"));
                if (c.contains("Variacion")) r.variacion = com.modamod.render.Variacion.valueOf(c.getString("Variacion"));
            } catch (IllegalArgumentException ignorado) {
                // Valor de una versión que ya no existe: queda el default.
            }
            r.angulo = c.getFloat("Angulo");
            r.giroMotivo = c.getFloat("GiroMotivo");
            r.distanciaH = c.contains("DistanciaH") ? c.getFloat("DistanciaH") : 1f;
            r.distanciaV = c.contains("DistanciaV") ? c.getFloat("DistanciaV") : 1f;
            try {
                if (c.contains("Espejo")) r.espejo = com.modamod.render.DistribucionPatron.Espejo.valueOf(c.getString("Espejo"));
                if (c.contains("GiroAzar")) r.giroAzar = com.modamod.render.DistribucionPatron.GiroAzar.valueOf(c.getString("GiroAzar"));
                if (c.contains("Alternancia")) {
                    r.alternancia = com.modamod.render.DistribucionPatron.Alternancia.valueOf(c.getString("Alternancia"));
                }
            } catch (IllegalArgumentException ignorado) {
                // Valor que ya no existe: queda el default.
            }
            r.simetria = c.getBoolean("Simetria");
            r.posX = c.contains("PosX") ? c.getFloat("PosX") : 0.5f;
            r.posicion = c.contains("Posicion") ? c.getFloat("Posicion") : 0.5f;
            r.invertido = c.getBoolean("Invertido");
            r.opacidad = c.contains("Opacidad") ? MathHelper.clamp(c.getInt("Opacidad"), PASO_OPACIDAD, 100) : 100;
            r.semilla = c.getInt("Semilla");
            for (int i = 0; i < 2; i++) {
                int[] m2 = c.getIntArray(i == 0 ? "Mezcla2" : "Mezcla3");
                for (int j = 0; j < CANALES && j < m2.length; j++) r.mezclasExtra[i][j] = MathHelper.clamp(m2[j], 0, NIVELES_MEZCLA - 1);
            }
            r.colores = c.contains("Colores") ? MathHelper.clamp(c.getInt("Colores"), 1, 3) : 1;
            r.contorno = c.getBoolean("Contorno");
            r.fondo = c.getBoolean("Fondo");
            r.editando = MathHelper.clamp(c.getInt("Editando"), 0, r.colores - 1);
            r.fijada = c.getBoolean("Fijada");
            r.oculta = c.getBoolean("Oculta");
            r.patronFijado = c.contains("Patron") ? Identifier.tryParse(c.getString("Patron")) : null;
            return r;
        }
    }

    /**
     * "Memoria de seteo por prenda" (a pedido, 2026-09-18) — cada categoría
     * tiene sus PROPIOS cuadraditos, orden y seleccionado, mismo espíritu
     * que {@code ModeladoBlockEntity.Categoria}.
     */
    /** Siempre al FINAL: los cuadraditos de cada categoría se guardan por índice (ver {@link #casillaSlot}). */
    /**
     * {@code APLIQUE} (2026-10-04, fase 4: "Tintes con categoría Aplique"): el retazo de aplique con 3 cuadraditos,
     * uno por zona (1, 2, 3), de color liso por ahora; los patrones por zona vienen en la segunda tanda.
     */
    /**
     * MALETIN (2026-10-23), BANNER (2026-10-24, "tintes banners"), MASCARA y BARBIJO (2026-10-24): al final, el orden
     * viaja en lo guardado.
     */
    public enum Categoria { REMERA, PANTALON, MEDIAS, CALIENTABRAZOS, POLLERA, CAPA, APLIQUE, DELANTAL, SOMBRERO, CALZADO, BANDA, MALETIN, BANNER, MASCARA, BARBIJO }

    private Categoria categoria = Categoria.REMERA;
    /** Calzado: con la simetría (de fábrica) el pie izquierdo copia el diseño del derecho; apagada se pintan por separado. */
    private boolean simetriaCalzado = true;
    private final Casilla[][] casillas = new Casilla[Categoria.values().length][CASILLAS];
    /**
     * Orden de pintado POR CATEGORÍA: permutación de los 12 índices, de la
     * capa de más abajo a la de más arriba. Arranca con las de prenda
     * entera abajo y las de región arriba (un cuello de otro color se ve
     * encima de un color general), ver {@link #ordenInicial}; los botones
     * Subir/Bajar la cambian.
     */
    private final int[][] orden = new int[Categoria.values().length][];
    /** Qué cuadradito editan ahora los sliders/botones, por categoría. */
    private final int[] seleccionada = new int[Categoria.values().length];
    {
        for (Categoria c : Categoria.values()) {
            for (int i = 0; i < CASILLAS; i++) casillas[c.ordinal()][i] = new Casilla();
            orden[c.ordinal()] = ordenInicial(c);
            seleccionada[c.ordinal()] = primeraUsable(c);
        }
    }

    /**
     * A qué región pinta el cuadradito {@code i} del esquema de {@code cat}
     * — null si ese lugar no pinta nada (Calce, huecos). Mapeo aprobado
     * (2026-09-27): remera cuello/mangas/pecho(MAT3)/borde inferior(Torso)
     * con MAT1/MAT2 de prenda entera; pantalón cintura(Tiro)/botas con los
     * 3 Materiales enteros; medias/calientabrazos Sup/Inf por lado con los
     * 6 de Personalización enteros. Medias/calientabrazos van CRUZADOS,
     * mismo motivo que {@code ModeladoBlockEntity#ladoDePin}: el esquema se
     * lee de frente, así que el pin de la izquierda del dibujo es la
     * pierna/brazo DERECHO del jugador.
     */
    @Nullable
    public static com.modamod.region.RegionPintura regionDe(Categoria cat, int i) {
        if (i < 0 || i >= CASILLAS) return null;
        if (cat == Categoria.POLLERA) return i < CASILLAS_POLLERA ? com.modamod.region.RegionPintura.TODO : null;
        if (cat == Categoria.APLIQUE) return i < CASILLAS_APLIQUE ? com.modamod.region.RegionPintura.TODO : null;
        // Banner (2026-10-24): como la pollera, 3 cuadraditos de banner entero que se apilan.
        if (cat == Categoria.BANNER || cat == Categoria.MASCARA || cat == Categoria.BARBIJO) {
            return i < CASILLAS_POLLERA ? com.modamod.region.RegionPintura.TODO : null;   // y máscara y barbijo (2026-10-24)
        }
        // Las zonas de un accesorio de cajas: ZONA_1.. en orden (el calzado trae las del pie izquierdo a partir de la 4.ª).
        int zonas = switch (cat) {
            case SOMBRERO -> CASILLAS_SOMBRERO;
            case BANDA, MALETIN -> CASILLAS_BANDA;
            case CALZADO -> CASILLAS_CALZADO;
            default -> 0;
        };
        if (zonas > 0) return i < zonas ? com.modamod.region.RegionPintura.values()[com.modamod.region.RegionPintura.ZONA_1.ordinal() + i] : null;
        if (cat == Categoria.DELANTAL) {
            return switch (i) {
                case 0 -> com.modamod.region.RegionPintura.TODO;
                case 1 -> com.modamod.region.RegionPintura.DELANTAL_PETO;
                case 2 -> com.modamod.region.RegionPintura.DELANTAL_FALDA;
                default -> null;
            };
        }
        if (cat == Categoria.CAPA) {
            return switch (i) {
                case 0 -> com.modamod.region.RegionPintura.CAPA_EXTERIOR;
                case 1 -> com.modamod.region.RegionPintura.CAPA_FORRO;
                case 2 -> com.modamod.region.RegionPintura.CAPA_DETALLES;
                default -> null;
            };
        }
        if (cat == Categoria.REMERA) return i < ZONAS_REMERA.length ? ZONAS_REMERA[i] : null;
        // Pantalón, medias y calientabrazos: la foto de los roles de la Modeladora al 2026-10-08 (2026-10-08,
        // "fijate porque remera en la estacion de tintes tenia un monton de slots no usables": Tintes dejó de leer la
        // Modeladora, que se está reordenando). Índice = el del cuadradito; null = no pinta nada.
        return switch (cat) {
            case PANTALON -> i < ZONAS_PANTALON.length ? ZONAS_PANTALON[i] : null;
            case MEDIAS -> i < ZONAS_MEDIAS.length ? ZONAS_MEDIAS[i] : null;
            case CALIENTABRAZOS -> i < ZONAS_MEDIAS.length ? ZONAS_MEDIAS[i] : null;
            default -> null;
        };
    }

    private static final com.modamod.region.RegionPintura TODO = com.modamod.region.RegionPintura.TODO;
    /** Remera / Top: 2 de prenda entera, pecho, cuello, mangas, borde de abajo y solapas (los 8 marcos de su esquema). */
    private static final com.modamod.region.RegionPintura[] ZONAS_REMERA = {
            TODO, TODO, com.modamod.region.RegionPintura.PECHO, com.modamod.region.RegionPintura.CUELLO,
            com.modamod.region.RegionPintura.MANGA_IZQ, com.modamod.region.RegionPintura.MANGA_DER,
            com.modamod.region.RegionPintura.BORDE_INFERIOR, com.modamod.region.RegionPintura.SOLAPAS};
    private static final com.modamod.region.RegionPintura[] ZONAS_PANTALON = {
            com.modamod.region.RegionPintura.CINTURA, TODO, TODO, TODO, null,
            com.modamod.region.RegionPintura.INF_IZQ, com.modamod.region.RegionPintura.INF_DER};
    /** Medias y calientabrazos (se leen de frente: la izquierda del dibujo es el lado derecho). */
    private static final com.modamod.region.RegionPintura[] ZONAS_MEDIAS = {
            com.modamod.region.RegionPintura.SUP_DER, com.modamod.region.RegionPintura.SUP_IZQ,
            com.modamod.region.RegionPintura.INF_DER, com.modamod.region.RegionPintura.INF_IZQ, null,
            TODO, TODO, TODO, TODO, TODO, TODO};

    private static int[] ordenInicial(Categoria cat) {
        int[] o = new int[CASILLAS];
        int k = 0;
        for (int i = 0; i < CASILLAS; i++) if (regionDe(cat, i) == com.modamod.region.RegionPintura.TODO) o[k++] = i;
        // Zonas, y el Cuello al final: es el borde del escote y va ENCIMA
        // del Pecho, que ahora arranca en la fila 0 (2026-09-28).
        for (int i = 0; i < CASILLAS; i++) {
            com.modamod.region.RegionPintura r = regionDe(cat, i);
            if (r != null && r != com.modamod.region.RegionPintura.TODO
                    && r != com.modamod.region.RegionPintura.CUELLO) o[k++] = i;
        }
        for (int i = 0; i < CASILLAS; i++) if (regionDe(cat, i) == com.modamod.region.RegionPintura.CUELLO) o[k++] = i;
        for (int i = 0; i < CASILLAS; i++) if (regionDe(cat, i) == null) o[k++] = i;
        return o;
    }

    private static int primeraUsable(Categoria cat) {
        for (int i = 0; i < CASILLAS; i++) if (regionDe(cat, i) != null) return i;
        return 0;
    }

    /** Índice real en {@link #items} del slot de molde del cuadradito {@code i} de {@code cat}. */
    public static int casillaSlot(Categoria cat, int i) {
        return CASILLAS_INICIO + cat.ordinal() * CASILLAS + i;
    }

    /**
     * Diseños de color guardados con nombre (2026-09-27, "mismo sistema
     * para los colores" que ya tiene la Modeladora): una copia CON NOMBRE
     * de los 12 cuadraditos de una categoría (qué está fijado, con qué
     * color/patrón/modo) + su orden. Cargar los reemplaza enteros — los
     * moldes puestos en los slots no son parte del diseño.
     */
    public static final int DISENOS_MAXIMO = 8;
    public record DisenoGuardado(String nombre, Casilla[] casillas, int[] orden) {}
    private final Map<Categoria, List<DisenoGuardado>> disenosPorCategoria = new EnumMap<>(Categoria.class);
    {
        for (Categoria c : Categoria.values()) disenosPorCategoria.put(c, new ArrayList<>());
    }

    /** Sin private (2026-09-22, "cubos simples" del hueso prenda): {@link TinturasGeoModel} la necesita para elegir qué forma mostrar. */
    @Nullable
    static Categoria categoriaDe(ItemStack stack) {
        if (stack.getItem() instanceof com.modamod.sublimadora.RemeraItem) return Categoria.REMERA;
        if (stack.getItem() instanceof com.modamod.item.PantalonItem) return Categoria.PANTALON;
        if (stack.getItem() == com.modamod.item.ModamodItems.SOCKS_SOLID) return Categoria.MEDIAS;
        if (stack.getItem() instanceof com.modamod.item.CalientabrazosItem) return Categoria.CALIENTABRAZOS;
        if (stack.getItem() instanceof com.modamod.item.PolleraItem) return Categoria.POLLERA;
        if (stack.getItem() instanceof com.modamod.item.CapaItem) return Categoria.CAPA;
        if (stack.getItem() instanceof com.modamod.item.DelantalItem) return Categoria.DELANTAL;
        if (stack.getItem() instanceof com.modamod.aplique.RetazoApliqueItem) return Categoria.APLIQUE;
        if (stack.getItem() instanceof com.modamod.item.SombreroBrujaItem) return Categoria.SOMBRERO;
        if (stack.getItem() instanceof com.modamod.item.BorcegosItem) return Categoria.CALZADO;
        if (stack.getItem() instanceof com.modamod.item.BandaItem) return Categoria.BANDA;
        if (stack.getItem() instanceof com.modamod.guardarropas.MaletinItem) return Categoria.MALETIN;
        if (stack.getItem() instanceof net.minecraft.item.BannerItem) return Categoria.BANNER;
        if (stack.getItem() instanceof com.modamod.item.CaraItem c) return c.barbijo ? Categoria.BARBIJO : Categoria.MASCARA;
        return null;
    }

    /**
     * Ciclo de teñido — a pedido (2026-09-21, "que cada maquina tome su
     * tiempo... 10 la estacion de tintes"): antes aplicaba instantáneo
     * (ver el historial de {@link #iniciarTenido}, antes {@code aplicar}),
     * ahora se comporta como Modeladora/Sublimadora — arranca al insertar
     * una prenda válida (gasta la tinta AHÍ, no al terminar, mismo
     * criterio que {@code SublimadoraBlockEntity#intentarPrensar}) y
     * termina sola {@link #TICKS_TENIDO} después.
     */
    public enum Estado { REPOSO, TINIENDO }

    /** 10s a 20 ticks — a pedido (2026-09-21, "10 la estacion de tintes"). */
    public static final int TICKS_TENIDO = 200;
    private Estado estado = Estado.REPOSO;
    private int progreso = 0;

    public Estado estado() { return estado; }

    /** Trabajando de verdad: la GUI se cierra y los controles quedan trabados (2026-10-08, hallazgo H03). */
    public boolean trabajando() { return estado == Estado.TINIENDO; }
    public int progreso() { return progreso; }
    /** La prenda ya teñida esperando salir — "listo" de verdad es REPOSO + esto no vacío (no hay Estado.LISTO acá). */
    public ItemStack getSalida() { return salida; }
    /** La prenda que se está tiñendo AHORA (vacía si no hay ninguna en curso) — para el hueso "prenda" de {@link TinturasGeoModel}. */
    public ItemStack getPrendaEntrada() { return prendaEntrada; }

    // ── viales: últimos 2 colores usados, con vaciado/llenado (2026-09-21,
    // "que muestren los ultimos dos colores utilizados... habria que
    // animar los viales... que se vacien del color viejo y se vuelvan a
    // llenar") ──────────────────────────────────────────────────────────
    /**
     * Duración de CADA fase en ticks — el vaciado y el llenado duran lo
     * mismo. Corta a propósito: es un flourish visual al terminar un
     * teñido, no un proceso principal como {@link #TICKS_TENIDO}.
     */
    private static final int DURACION_FASE_VIAL = 10;
    /** [0]=vial_1 (el más reciente), [1]=vial_2 (el anterior a ese). */
    private final int[] colorVial = new int[2];
    private final int[] colorVialPrevio = new int[2];
    private final long[] tickCambioVial = new long[2];

    /**
     * Escala 0..1 del vial (pivote en la base, mismo mecanismo que
     * cualquier barra de nivel del mod) — 1 la mitad del tiempo
     * (VACIANDO, encogiendo desde 1) y la otra mitad LLENANDO (creciendo
     * desde 0), o 1 fijo (LLENO) fuera de la ventana de transición.
     */
    public float fraccionVial(int i) {
        if (world == null) return 1f;
        long transcurrido = world.getTime() - tickCambioVial[i];
        if (transcurrido < 0 || transcurrido >= 2L * DURACION_FASE_VIAL) return 1f;
        if (transcurrido < DURACION_FASE_VIAL) return 1f - transcurrido / (float) DURACION_FASE_VIAL;
        return (transcurrido - DURACION_FASE_VIAL) / (float) DURACION_FASE_VIAL;
    }

    /** El color RGB a pintar AHORA MISMO — el viejo mientras se vacía, el nuevo apenas empieza a llenarse (con la escala en 0 en el medio, invisible el instante del cambio). */
    public int colorVialActivo(int i) {
        if (world == null) return colorVial[i];
        long transcurrido = world.getTime() - tickCambioVial[i];
        return transcurrido < DURACION_FASE_VIAL ? colorVialPrevio[i] : colorVial[i];
    }

    /** Si este vial YA mostró algún teñido real alguna vez — mientras no, se deja el arte original del atlas sin pintar encima (ver {@code TinturasGeoModel#pintarViales}). */
    public boolean vialUsado(int i) {
        return colorVial[i] != 0;
    }

    /**
     * Se llama al terminar un teñido de verdad (ver {@link #tick}) con
     * el color representativo de la fijada aplicada
     * ({@code TinteFijado#color()}) — rota el historial (vial_2 ← lo
     * que tenía vial_1) y arranca la animación de los dos, ambos
     * cambiaron. Si el color es el mismo que ya mostraba vial_1, no
     * rota ni anima nada (sin cambio real que mostrar).
     */
    private void registrarColorUsado(int rgb) {
        if (rgb == colorVial[0]) return;
        long ahora = world != null ? world.getTime() : 0;
        colorVialPrevio[1] = colorVial[1];
        colorVial[1] = colorVial[0];
        tickCambioVial[1] = ahora;
        colorVialPrevio[0] = colorVial[0];
        colorVial[0] = rgb;
        tickCambioVial[0] = ahora;
    }

    /** 0..1, para animar/GUI. */
    public float progresoFraccion() {
        return estado == Estado.TINIENDO ? progreso / (float) TICKS_TENIDO : 0f;
    }

    public TinturasBlockEntity(BlockPos pos, BlockState state) {
        super(TinturasMod.TINTURAS_BLOCK_ENTITY, pos, state);
    }

    /**
     * Ticker (servidor): avanza {@link #progreso} mientras {@link #estado}
     * es TINIENDO y aplica el tinte al terminar — mismo patrón que
     * {@code ModeladoBlockEntity#tick}/{@code SublimadoraBlockEntity#tick}.
     * La fijada a aplicar se relee AHORA, no la que estaba seleccionada
     * al insertar (mismo criterio que {@code ModeladoBlockEntity#procesar},
     * que también relee fijadasPorCategoria en el momento de terminar) —
     * si para entonces no hay ninguna seleccionada, la prenda sale sin
     * tocar en vez de quedar trabada para siempre.
     */
    public static void tick(net.minecraft.world.World world, BlockPos pos, BlockState state, TinturasBlockEntity be) {
        // Con señal de redstone la máquina se detiene del todo (2026-10-05).
        if (!world.isClient && com.modamod.util.Redstone.pausada(world, pos)) return;
        if (!world.isClient && !be.salida.isEmpty()) be.empujarSalida();
        if (!world.isClient && world.getTime() % 20 == 0) be.drenarViejos();
        // Luz del LED (2026-09-29, "hace que las luces de las maquinas iluminen"):
        // mismo criterio que TinturasGeoModel#coloresLed.
        com.modamod.util.LuzMaquina.actualizar(world, pos, state,
                be.estado == Estado.TINIENDO || (be.estado == Estado.REPOSO && !be.salida.isEmpty()));
        if (world.isClient || be.estado != Estado.TINIENDO) return;

        be.progreso++;
        // Diseño sonoro (2026-09-22, "aplica los mismos [sonidos] a las
        // otras dos" — clon del de SublimadoraBlockEntity#tick): un
        // pitido por cada encendido del LED rojo. Mismo reloj que usa
        // TinturasGeoModel#coloresLed para el parpadeo, así el pitido cae
        // justo en el flanco de encendido.
        if (world.getTime() % 20 == 0) be.sonar(SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), 0.25f, 2.0f);
        // Goteo + salpicado del color que ya se está viendo en el vial
        // activo (2026-09-22, "sonido de aguas y goteo y salpicar
        // particulas del color de la tinta de los viales") — mismo color
        // que ya pinta TinturasGeoModel#pintarViales, no uno nuevo.
        if (world instanceof ServerWorld servidor && be.progreso % 8 == 0) {
            be.sonar(SoundEvents.BLOCK_POINTED_DRIPSTONE_DRIP_WATER, 0.4f, 1.0f + servidor.getRandom().nextFloat() * 0.4f);
            int colorSplash = be.vialUsado(0) ? be.colorVialActivo(0) : 0x3080FF;
            salpicar(servidor, pos, colorSplash);
            // También alrededor del rodillo (2026-09-23, "particulas de la
            // tintura que aparezcan alrededor del rodillo") — la prenda
            // gira ahí (ver TinturasGeoModel), tiene sentido que el
            // salpicado la seleccione a ella, no al vial.
            salpicarRodillo(servidor, pos, colorSplash);
        }

        if (be.progreso < com.modamod.util.MaquinaCreativa.duracion(be, TICKS_TENIDO)) {
            if (be.progreso % 20 == 0) be.sincronizar();
            return;
        }

        ItemStack resultado = be.prendaEntrada;
        Categoria cat = categoriaDe(resultado);
        if (cat != null) {
            // Solo lo FIJADO es el diseño (2026-09-27, "chincheta por
            // cuadradito") — se relee ahora, no al insertar.
            List<RegionResolver.CapaPatron> capas = be.capasDe(cat, false);
            if (!capas.isEmpty()) {
                resultado = resultado.copy();
                be.pintar(resultado, cat, false);
                be.registrarColorUsado(capas.get(capas.size() - 1).color() & 0xFFFFFF);
            }
        }
        be.salida = resultado;
        be.prendaEntrada = ItemStack.EMPTY;
        be.estado = Estado.REPOSO;
        be.progreso = 0;
        // Campanita + carrillón juntos, igual que el LED verde de
        // Sublimadora — clon exacto de su combo de "listo".
        be.sonar(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.0f, 1.5f);
        be.sonar(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f);
        be.empujarSalida();
        be.sincronizar();
    }

    /**
     * Salpicón de gotas coloreadas cerca de los viales, mismo criterio de
     * spawnParticles que {@code SublimadoraBlockEntity#vapor}. Punto de
     * salida relevado a mano desde el pivote real de vial_1/vial_2 en
     * {@code dye_station.geo.json} (X≈-2.1/-4.4, Y≈6.45, Z≈9.5 en espacio
     * de modelo Blockbench, /16 + centro de bloque).
     */
    private static void salpicar(ServerWorld world, BlockPos pos, int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        DustParticleEffect efecto = new DustParticleEffect(new org.joml.Vector3f(r, g, b), 1.2f);
        double cx = pos.getX() + 0.3, cy = pos.getY() + 0.45, cz = pos.getZ() + 0.95;
        world.spawnParticles(efecto, cx, cy, cz, 3, 0.08, 0.05, 0.06, 0.02);
    }

    /**
     * Salpicado alrededor del rodillo (2026-09-23, "particulas de la
     * tintura que aparezcan alrededor del rodillo") — punto relevado a
     * mano desde su pivote real ({@code dye_station.geo.json}, X≈0,
     * Y≈12.2, Z≈3.2 en espacio de modelo, /16 + centro de bloque);
     * dispersión más ancha en X que {@link #salpicar} porque el rodillo
     * es largo (12 de ancho contra el 1×1 de los viales).
     */
    private static void salpicarRodillo(ServerWorld world, BlockPos pos, int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        DustParticleEffect efecto = new DustParticleEffect(new org.joml.Vector3f(r, g, b), 1.0f);
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.76, cz = pos.getZ() + 0.7;
        world.spawnParticles(efecto, cx, cy, cz, 3, 0.3, 0.1, 0.1, 0.02);
    }

    private void sonar(SoundEvent evento, float tono) {
        if (world != null) world.playSound(null, pos, evento, SoundCategory.BLOCKS, 0.8f, tono);
    }

    private void sonar(SoundEvent evento, float volumen, float tono) {
        if (world != null) world.playSound(null, pos, evento, SoundCategory.BLOCKS, volumen, tono);
    }

    // ── animación (GeckoLib) ─────────────────────────────────────────
    // "trabajo" atada a Estado.TINIENDO por PlayState (2026-09-22, "que
    // funcione solo mientras esta funcionando") — antes se disparaba UNA
    // vez con triggerAnim al empezar y volvía sola a "reposo" a los 3.2s
    // (duración propia de la animación), aunque el teñido real dura
    // TICKS_TENIDO=200 ticks=10s: se veía "reposo" los últimos ~7s
    // mientras la máquina seguía trabajando de verdad. Mismo mecanismo
    // que ModeladoBlockEntity#registerControllers (predicate, no trigger).

    private static final RawAnimation REPOSO = RawAnimation.begin().thenLoop("animation.dye_station.reposo");
    private static final RawAnimation TRABAJO = RawAnimation.begin().thenLoop("animation.dye_station.trabajo");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "principal", 0,
                state -> state.setAndContinue(estado == Estado.TINIENDO ? TRABAJO : REPOSO)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ── estado para la GUI ──────────────────────────────────────────

    public int carga(int canal) { return cargas[canal]; }
    public Categoria categoria() { return categoria; }

    public boolean simetriaCalzado() { return simetriaCalzado; }

    /** Cuántas zonas muestra la pantalla: el pie izquierdo se esconde con la simetría prendida. */
    public int zonasVisibles() {
        return categoria == Categoria.CALZADO && simetriaCalzado ? 1 : ZonasTintes.cantidad(categoria);
    }
    /** El cuadradito que editan los sliders/botones ahora mismo (de la categoría actual). */
    public int seleccionada() { return seleccionada[categoria.ordinal()]; }
    /** El cuadradito {@code i} de la categoría actual. */
    public Casilla casilla(int i) { return casillas[categoria.ordinal()][i]; }
    private Casilla casillaSeleccionada() { return casilla(seleccionada()); }

    /** Nivel del canal CMYK del cuadradito seleccionado — lo que muestran los sliders. */
    /** Copia de la mezcla del color en edición del cuadradito seleccionado (para envasarla). */
    public int[] mezclaEnEdicion() {
        Casilla c = casillaSeleccionada();
        return c.mezclaDe(c.editando).clone();
    }

    /** Pone {@code mezcla} en el color en edición del cuadradito seleccionado (una muestra de color). */
    public void ponerMezclaEnEdicion(int[] mezcla) {
        Casilla c = casillaSeleccionada();
        int[] destino = c.mezclaDe(c.editando);
        for (int i = 0; i < CANALES && i < mezcla.length; i++) destino[i] = MathHelper.clamp(mezcla[i], 0, NIVELES_MEZCLA - 1);
        sincronizar();
    }

    public int nivelBorrador(int canal) {
        Casilla c = casillaSeleccionada();
        return c.mezclaDe(c.editando)[canal];
    }
    /** CMYK → RGB del color que se está editando en el cuadradito seleccionado. */
    public int colorBorrador() {
        Casilla c = casillaSeleccionada();
        return c.colorDe(c.editando);
    }

    /** Forma EFECTIVA del cuadradito seleccionado — la elegida a mano, o si no la del molde que tenga. */
    public com.modamod.render.PatronGenerador.Forma formaBorrador() {
        int i = seleccionada();
        return formaEfectiva(casilla(i), patronEnBorrador(categoria, i));
    }

    /** El motivo del molde del cuadradito seleccionado (en el slot o fijado), o null si es liso o de rayas. */
    @Nullable
    public com.modamod.render.Motivo motivoSeleccionado() {
        Identifier patron = patronEnBorrador(categoria, seleccionada());
        ClothingPatternItem item = patron == null ? null : ClothingPatternItem.porId(patron);
        return item == null ? null : item.motivo;
    }

    /** El patrón que usa el cuadradito para la vista previa: el molde del slot si hay uno, si no el fijado. */
    @Nullable
    public Identifier patronEnBorrador(Categoria cat, int i) {
        ItemStack molde = items.get(casillaSlot(cat, i));
        if (molde.getItem() instanceof ClothingPatternItem p) return p.patternId;
        return casillas[cat.ordinal()][i].patronFijado;
    }

    private static com.modamod.render.PatronGenerador.Forma formaEfectiva(Casilla c, @Nullable Identifier patron) {
        if (c.forma != null) return c.forma;
        ClothingPatternItem item = patron == null ? null : ClothingPatternItem.porId(patron);
        return item != null ? item.forma : com.modamod.render.PatronGenerador.Forma.ALTERNADO;
    }

    /**
     * ¿Entra este cuadradito en la vista previa? Solo lo fijado (lo que se
     * va a aplicar) más el que se está editando — 2026-09-28, "la remera
     * tintada no salio igual q la preview": antes también entraban los
     * cuadraditos con un molde puesto sin fijar, que la vista previa
     * mostraba y el teñido no aplicaba.
     */
    public boolean enBorrador(Categoria cat, int i) {
        if (regionDe(cat, i) == null) return false;
        // Solo lo fijado entra (2026-10-12, "cuando despinee un cuadrado que deje de pintar la preview"): antes el
        // cuadradito elegido también pintaba aunque no estuviera fijado.
        return casillas[cat.ordinal()][i].fijada;
    }

    /** Algún cuadradito fijado en la categoría actual — sin eso no hay diseño que aplicar ni guardar. */
    public boolean hayFijadas() { return hayFijadas(categoria); }

    private boolean borrarFijadas(Categoria cat) {
        boolean alguna = false;
        for (int i = 0; i < CASILLAS; i++) {
            Casilla c = casillas[cat.ordinal()][i];
            if (!c.fijada && c.patronFijado == null) continue;
            c.fijada = false;
            c.patronFijado = null;
            alguna = true;
        }
        return alguna;
    }

    private boolean hayFijadas(Categoria cat) {
        for (int i = 0; i < CASILLAS; i++) {
            if (regionDe(cat, i) != null && casillas[cat.ordinal()][i].fijada) return true;
        }
        return false;
    }

    /** Posición (1 = la de más abajo) del cuadradito {@code i} entre las capas de la vista previa, o 0 si no está. */
    public int posicionEnOrden(int i) {
        int c = categoria.ordinal(), pos = 0;
        for (int k = 0; k < CASILLAS; k++) {
            int j = orden[c][k];
            if (!enBorrador(categoria, j)) continue;
            pos++;
            if (j == i) return pos;
        }
        return 0;
    }

    /**
     * Las filas del panel de capas (Fase B, 2026-09-28): los cuadraditos
     * que participan (fijados + el seleccionado, ocultos incluidos), de la
     * capa de ARRIBA (la que tapa) a la de abajo — como en un editor de
     * imágenes.
     */
    public List<Integer> capasDelPanel() {
        int c = categoria.ordinal();
        List<Integer> filas = new ArrayList<>(CASILLAS);
        for (int k = CASILLAS - 1; k >= 0; k--) {
            int i = orden[c][k];
            if (enBorrador(categoria, i)) filas.add(i);
        }
        return filas;
    }

    /** Cuántas capas hay en la vista previa de la categoría actual. */
    public int capasEnBorrador() {
        int n = 0;
        for (int i = 0; i < CASILLAS; i++) if (enBorrador(categoria, i)) n++;
        return n;
    }

    /**
     * Las capas de {@code cat}, en orden de pintado. {@code borrador}=true
     * es la vista previa (fijadas + la seleccionada); false es lo que se
     * APLICA de verdad: solo las fijadas. En los dos casos el patrón es el
     * molde del slot si hay uno, y si no el fijado — 2026-09-28: antes el
     * teñido usaba SIEMPRE el fijado, así que poner un molde en un
     * cuadradito ya fijado (sin volver a clavar la chincheta) se veía en la
     * vista previa pero no salía en la prenda.
     */
    public List<RegionResolver.CapaPatron> capasDe(Categoria cat, boolean borrador) {
        List<RegionResolver.CapaPatron> capas = new ArrayList<>();
        int c = cat.ordinal();
        for (int k = 0; k < CASILLAS; k++) {
            int i = orden[c][k];
            com.modamod.region.RegionPintura region = regionDe(cat, i);
            if (region == null) continue;
            Casilla cas = casillas[c][i];
            Identifier patron;
            if (borrador ? !enBorrador(cat, i) : !cas.fijada) continue;
            if (cas.oculta) continue;
            patron = patronEnBorrador(cat, i);
            // Con fondo, el último color va liso debajo del patrón y el patrón usa los otros.
            boolean fondo = cas.fondo && patron != null && cas.colores > 1;
            int coloresPatron = fondo ? cas.colores - 1 : cas.colores;
            if (fondo) {
                capas.add(new RegionResolver.CapaPatron(null, cas.colorDe(cas.colores - 1), cas.tamano, cas.angulo,
                        cas.posicion, formaEfectiva(cas, null), false, region, cas.modo, cas.opacidad,
                        cas.repeticion, cas.semilla, List.of(), false, cas.variacion, false, cas.distribucion()));
            }
            List<Integer> extras = new ArrayList<>(2);
            for (int col = 1; col < coloresPatron; col++) extras.add(cas.colorDe(col));
            capas.add(new RegionResolver.CapaPatron(patron, cas.color(), cas.tamano, cas.angulo, cas.posicion,
                    formaEfectiva(cas, patron), cas.invertido, region, cas.modo, cas.opacidad,
                    cas.repeticion, cas.semilla, List.copyOf(extras), cas.contorno, cas.variacion,
                    false, cas.distribucion()));
        }
        return capas;
    }

    /**
     * CMYK (+T) → color. Los 3 bytes bajos son el RGB de siempre; el byte
     * ALTO es la transparencia (0 = opaco) — al revés que un alfa a
     * propósito: así todo color guardado antes de este canal (byte alto
     * en 0) sigue siendo opaco sin migrar nada.
     */
    private static int colorDeMezcla(int[] mezcla) {
        float c = mezcla[C] / (float) (NIVELES_MEZCLA - 1);
        float m = mezcla[M] / (float) (NIVELES_MEZCLA - 1);
        float y = mezcla[Y] / (float) (NIVELES_MEZCLA - 1);
        float k = mezcla[K] / (float) (NIVELES_MEZCLA - 1);
        int r = Math.round(255 * (1 - c) * (1 - k));
        int g = Math.round(255 * (1 - m) * (1 - k));
        int b = Math.round(255 * (1 - y) * (1 - k));
        int t = mezcla.length > T ? Math.round(255f * mezcla[T] / (NIVELES_MEZCLA - 1)) : 0;
        return (t << 24) | (r << 16) | (g << 8) | b;
    }

    // ── botones de la GUI (mismo mecanismo que ModeladoBlockEntity: ScreenHandler#onButtonClick) ──

    /**
     * 5 canales (C/M/Y/K/T) × {@link #NIVELES_MEZCLA} niveles, siempre sobre
     * el color en edición del cuadradito SELECCIONADO — {@code canal*NIVELES + nivel}.
     * Arriba de todo (400+) desde que son 21 niveles: con 5 niveles entraban
     * en 0..19, con 21 se pisaban con los rangos de cuadraditos (40/60/90).
     */
    public static final int BTN_MEZCLA_BASE = 400;
    /** Categoría anterior (flecha ‹ junto al botón de prenda, 2026-10-12). */
    public static final int BTN_CATEGORIA_ATRAS = 950;
    /** Carga el último diseño usado en la categoría actual (botón "★ Último", 2026-10-12). */
    public static final int BTN_CARGAR_ULTIMO = 951;
    /** Borrar los cuadraditos fijados de la categoría actual / de todas (2026-10-14); los diseños con nombre no se tocan. */
    public static final int BTN_BORRAR = 952, BTN_BORRAR_TODAS = 953;
    /** Prende o apaga la simetría del calzado (2026-10-15). */
    public static final int BTN_SIMETRIA_CALZADO = 954;
    /** Prende/apaga el fondo del cuadradito (2026-10-24); con un solo color, suma el segundo para el fondo. */
    public static final int BTN_FONDO = 955;
    public static final int BTN_TAMANO = 1;
    /** Ángulo en pasos de 15° (0..345, wrap), adelante/atrás. */
    public static final int BTN_ANGULO = BTN_TAMANO + 1;
    public static final int BTN_ANGULO_ATRAS = BTN_ANGULO + 1;
    /** Posición en pasos de 10% (0.0..1.0, wrap), adelante/atrás. */
    public static final int BTN_POSICION = BTN_ANGULO_ATRAS + 1;
    public static final int BTN_POSICION_ATRAS = BTN_POSICION + 1;
    public static final int BTN_FORMA = BTN_POSICION_ATRAS + 1;
    public static final int BTN_INVERTIR = BTN_FORMA + 1;
    public static final int BTN_CATEGORIA = BTN_INVERTIR + 1;
    /** Cicla Normal / Multiplicar / Superponer — ver {@link com.modamod.region.ModoMezcla}. */
    public static final int BTN_MODO = BTN_CATEGORIA + 1;
    /** Opacidad en pasos de {@link #PASO_OPACIDAD}% (10..100, wrap), adelante/atrás. */
    public static final int BTN_OPACIDAD = BTN_MODO + 1;
    public static final int BTN_OPACIDAD_ATRAS = BTN_OPACIDAD + 1;
    /** Sube/baja el cuadradito seleccionado en el orden de pintado (más arriba = tapa a los de abajo). */
    public static final int BTN_SUBIR = BTN_OPACIDAD_ATRAS + 1;
    public static final int BTN_BAJAR = BTN_SUBIR + 1;
    /** Arranca el teñido de la prenda que ya está en la Entrada — lo maneja {@link TinturasScreenHandler} (necesita al jugador para avisar el motivo si falla). */
    public static final int BTN_TENIR = BTN_BAJAR + 1;
    /** Nueva semilla para Repetición: Disperso (2026-09-28, motivos). */
    public static final int BTN_SEMILLA = BTN_TENIR + 1;
    /** Cicla cuántos colores usa la capa, 1→2→3→1 (Fase 2, 2026-09-28). */
    public static final int BTN_COLORES = BTN_SEMILLA + 1;
    public static final int BTN_CONTORNO = BTN_COLORES + 1;
    public static final int BTN_VARIACION = BTN_CONTORNO + 1;
    /** Muestras de color (2026-09-30): los maneja TinturasScreenHandler, que tiene al jugador. */
    public static final int BTN_ENVASAR = BTN_VARIACION + 1;
    public static final int BTN_USAR_MUESTRA = BTN_ENVASAR + 1;
    /** Giro de cada motivo en su lugar, de a 15° (2026-09-30), adelante/atrás. */
    public static final int BTN_GIRO_MOTIVO = BTN_USAR_MUESTRA + 1;
    public static final int BTN_GIRO_MOTIVO_ATRAS = BTN_GIRO_MOTIVO + 1;
    /** Ciclan el espejo del motivo y el espejo alternado; alterna la simetría del torso (2026-09-30). */
    public static final int BTN_ESPEJO = BTN_GIRO_MOTIVO_ATRAS + 1;
    public static final int BTN_ALTERNANCIA = BTN_ESPEJO + 1;
    public static final int BTN_SIMETRIA = BTN_ALTERNANCIA + 1;
    /** Cicla el giro al azar de cada motivo: No / 90° / ±45° / Libre (2026-10-12). */
    public static final int BTN_GIRO_AZAR = BTN_SIMETRIA + 1;
    /** Corre la grilla de motivos en horizontal, de a 10 % (2026-10-12). */
    public static final int BTN_POSICION_X = BTN_GIRO_AZAR + 1;
    public static final int BTN_POSICION_X_ATRAS = BTN_POSICION_X + 1;
    /** + nivel (0..{@code DistribucionPatron.NIVELES}-1): sliders de distancia horizontal y vertical (2026-09-30). */
    public static final int BTN_DISTANCIA_H_BASE = 170;
    public static final int BTN_DISTANCIA_V_BASE = 230;
    /** + 0..2: qué color editan los sliders (click en su muestra). */
    public static final int BTN_EDITAR_COLOR_BASE = 90;
    /** + índice de cuadradito: lo selecciona para editar (click en su slot). */
    public static final int BTN_SELECCIONAR_BASE = 40;
    /** + índice de cuadradito: su chincheta — ver {@link #chincheta}. */
    public static final int BTN_CHINCHETA_BASE = 60;
    /** + índice de diseño (0..{@link #DISENOS_MAXIMO}-1) — mismos valores que usa la Modeladora, sin colisión acá. */
    /** Panel de capas (Fase B, 2026-09-28): ojo, subir y bajar de CADA fila — {@code base + cuadradito}. */
    public static final int BTN_OJO_BASE = 110;
    public static final int BTN_SUBIR_BASE = 130;
    public static final int BTN_BAJAR_BASE = 150;
    public static final int BTN_CARGAR_DISENO_BASE = 300;
    public static final int BTN_BORRAR_DISENO_BASE = 320;

    public boolean onButtonClick(int id) {
        if (id == BTN_LINEA) {
            alternarLinea();
            return true;
        }
        // El diseño pagado al iniciar no se toca mientras tiñe (H03).
        if (trabajando()) return false;
        boolean cambio = aplicarBoton(id);
        if (cambio) sincronizar();
        return cambio;
    }

    private boolean aplicarBoton(int id) {
        Casilla sel = casillaSeleccionada();
        if (id >= BTN_MEZCLA_BASE && id < BTN_MEZCLA_BASE + CANALES * NIVELES_MEZCLA) {
            int rel = id - BTN_MEZCLA_BASE;
            sel.mezclaDe(sel.editando)[rel / NIVELES_MEZCLA] = rel % NIVELES_MEZCLA;
            return true;
        }
        int niveles = com.modamod.render.DistribucionPatron.NIVELES;
        if (id >= BTN_DISTANCIA_H_BASE && id < BTN_DISTANCIA_H_BASE + niveles) {
            sel.distanciaH = com.modamod.render.DistribucionPatron.distanciaDeNivel(id - BTN_DISTANCIA_H_BASE);
            return true;
        }
        if (id >= BTN_DISTANCIA_V_BASE && id < BTN_DISTANCIA_V_BASE + niveles) {
            sel.distanciaV = com.modamod.render.DistribucionPatron.distanciaDeNivel(id - BTN_DISTANCIA_V_BASE);
            return true;
        }
        if (id >= BTN_EDITAR_COLOR_BASE && id < BTN_EDITAR_COLOR_BASE + 3) {
            // Tocar una muestra apagada la prende (Colores sube hasta ahí).
            int i = id - BTN_EDITAR_COLOR_BASE;
            sel.editando = i;
            if (i >= sel.colores) sel.colores = i + 1;
            return true;
        }
        switch (id) {
            case BTN_TAMANO -> {
                TamanoPatron[] valores = TamanoPatron.values();
                sel.tamano = valores[(sel.tamano.ordinal() + 1) % valores.length];
                return true;
            }
            case BTN_ANGULO -> {
                sel.angulo = (Math.round(sel.angulo) + 360 + 15) % 360;
                return true;
            }
            case BTN_ANGULO_ATRAS -> {
                sel.angulo = (Math.round(sel.angulo) + 360 - 15) % 360;
                return true;
            }
            case BTN_GIRO_MOTIVO -> {
                sel.giroMotivo = (Math.round(sel.giroMotivo) + 360 + 15) % 360;
                return true;
            }
            case BTN_GIRO_MOTIVO_ATRAS -> {
                sel.giroMotivo = (Math.round(sel.giroMotivo) + 360 - 15) % 360;
                return true;
            }
            case BTN_ESPEJO -> {
                sel.espejo = sel.espejo.siguiente();
                return true;
            }
            case BTN_ALTERNANCIA -> {
                sel.alternancia = sel.alternancia.siguiente();
                return true;
            }
            case BTN_POSICION_X -> {
                sel.posX = ((Math.round(sel.posX * 10) + 1) % 11) / 10f;
                return true;
            }
            case BTN_POSICION_X_ATRAS -> {
                int anteriorX = Math.round(sel.posX * 10) - 1;
                sel.posX = ((anteriorX % 11 + 11) % 11) / 10f;
                return true;
            }
            case BTN_GIRO_AZAR -> {
                sel.giroAzar = sel.giroAzar.siguiente();
                return true;
            }
            case BTN_SIMETRIA -> {
                sel.simetria = !sel.simetria;
                return true;
            }
            case BTN_POSICION -> {
                sel.posicion = ((Math.round(sel.posicion * 10) + 1) % 11) / 10f;
                return true;
            }
            case BTN_POSICION_ATRAS -> {
                int anterior = Math.round(sel.posicion * 10) - 1;
                sel.posicion = ((anterior % 11 + 11) % 11) / 10f;
                return true;
            }
            case BTN_FORMA -> {
                // Con un molde de MOTIVO el mismo botón cicla la Repetición
                // (la forma de raya no le aplica) — así no suma otra fila.
                if (motivoSeleccionado() != null) {
                    sel.repeticion = sel.repeticion.siguiente();
                    return true;
                }
                com.modamod.render.PatronGenerador.Forma[] valores = com.modamod.render.PatronGenerador.Forma.values();
                sel.forma = valores[(formaBorrador().ordinal() + 1) % valores.length];
                return true;
            }
            case BTN_COLORES -> {
                sel.colores = sel.colores % 3 + 1;
                if (sel.editando >= sel.colores) sel.editando = sel.colores - 1;
                return true;
            }
            case BTN_CONTORNO -> {
                sel.contorno = !sel.contorno;
                return true;
            }
            case BTN_FONDO -> {
                sel.fondo = !sel.fondo;
                if (sel.fondo && sel.colores == 1) sel.colores = 2;
                return true;
            }
            case BTN_VARIACION -> {
                sel.variacion = sel.variacion.siguiente();
                return true;
            }
            case BTN_SEMILLA -> {
                sel.semilla = (sel.semilla + 1) & 0xFFFF;
                return true;
            }
            case BTN_INVERTIR -> {
                sel.invertido = !sel.invertido;
                return true;
            }
            case BTN_CARGAR_ULTIMO -> {
                return cargarUltimo();
            }
            case BTN_BORRAR -> {
                return borrarFijadas(categoria);
            }
            case BTN_BORRAR_TODAS -> {
                boolean alguna = false;
                for (Categoria c : Categoria.values()) alguna |= borrarFijadas(c);
                return alguna;
            }
            case BTN_SIMETRIA_CALZADO -> {
                simetriaCalzado = !simetriaCalzado;
                return true;
            }
            case BTN_CATEGORIA_ATRAS -> {
                Categoria[] valores = Categoria.values();
                categoria = valores[(categoria.ordinal() + valores.length - 1) % valores.length];
                return true;
            }
            case BTN_CATEGORIA -> {
                Categoria[] valores = Categoria.values();
                categoria = valores[(categoria.ordinal() + 1) % valores.length];
                return true;
            }
            case BTN_MODO -> {
                sel.modo = sel.modo.siguiente();
                return true;
            }
            case BTN_OPACIDAD -> {
                sel.opacidad = sel.opacidad >= 100 ? PASO_OPACIDAD : sel.opacidad + PASO_OPACIDAD;
                return true;
            }
            case BTN_OPACIDAD_ATRAS -> {
                sel.opacidad = sel.opacidad <= PASO_OPACIDAD ? 100 : sel.opacidad - PASO_OPACIDAD;
                return true;
            }
            case BTN_SUBIR -> {
                return mover(+1);
            }
            case BTN_BAJAR -> {
                return mover(-1);
            }
            default -> { }
        }
        if (id >= BTN_SELECCIONAR_BASE && id < BTN_SELECCIONAR_BASE + CASILLAS) {
            int i = id - BTN_SELECCIONAR_BASE;
            if (regionDe(categoria, i) == null || seleccionada() == i) return false;
            seleccionada[categoria.ordinal()] = i;
            return true;
        }
        if (id >= BTN_CHINCHETA_BASE && id < BTN_CHINCHETA_BASE + CASILLAS) {
            return chincheta(id - BTN_CHINCHETA_BASE);
        }
        if (id >= BTN_OJO_BASE && id < BTN_OJO_BASE + CASILLAS) {
            int i = id - BTN_OJO_BASE;
            if (!enBorrador(categoria, i)) return false;
            casilla(i).oculta = !casilla(i).oculta;
            return true;
        }
        // Flechas de una fila del panel: la selecciona y la mueve (el orden
        // es el mismo que usan ▼/▲ de abajo, ver mover).
        if (id >= BTN_SUBIR_BASE && id < BTN_SUBIR_BASE + CASILLAS
                || id >= BTN_BAJAR_BASE && id < BTN_BAJAR_BASE + CASILLAS) {
            boolean subir = id < BTN_BAJAR_BASE;
            int i = id - (subir ? BTN_SUBIR_BASE : BTN_BAJAR_BASE);
            if (!enBorrador(categoria, i)) return false;
            seleccionada[categoria.ordinal()] = i;
            mover(subir ? 1 : -1);
            return true;
        }
        if (id >= BTN_CARGAR_DISENO_BASE && id < BTN_CARGAR_DISENO_BASE + DISENOS_MAXIMO) {
            return cargarDiseno(id - BTN_CARGAR_DISENO_BASE);
        }
        if (id >= BTN_BORRAR_DISENO_BASE && id < BTN_BORRAR_DISENO_BASE + DISENOS_MAXIMO) {
            return borrarDiseno(id - BTN_BORRAR_DISENO_BASE);
        }
        return false;
    }

    /**
     * Chincheta del cuadradito {@code i} (2026-09-27, "chincheta por
     * cuadradito, como la Modeladora") — también lo selecciona:
     * <ul>
     *   <li>Con molde puesto: queda FIJADO con ese patrón y el molde vuelve
     *   al almacén (si no hay lugar en el almacén, no se fija). Si ya
     *   estaba fijado, así se le cambia el patrón.</li>
     *   <li>Sin molde y sin fijar: queda fijado como color LISO.</li>
     *   <li>Sin molde y fijado: se desfija (sale del diseño).</li>
     * </ul>
     */
    private boolean chincheta(int i) {
        if (regionDe(categoria, i) == null) return false;
        seleccionada[categoria.ordinal()] = i;
        Casilla cas = casilla(i);
        int slot = casillaSlot(categoria, i);
        ItemStack molde = items.get(slot);
        if (!molde.isEmpty()) {
            if (!(molde.getItem() instanceof ClothingPatternItem p)) return false;
            if (!guardarEnAlmacen(molde)) return false;
            items.set(slot, ItemStack.EMPTY);
            cas.patronFijado = p.patternId;
            cas.fijada = true;
            return true;
        }
        if (cas.fijada) {
            cas.fijada = false;
            cas.patronFijado = null;
        } else {
            cas.fijada = true;
            cas.patronFijado = null;
        }
        return true;
    }

    /**
     * Estación de Tintes creativa (2026-10-01, "tengan adentro todos los
     * patrones de cada maquina"): uno de cada patrón en el almacén.
     */
    @Override
    public void cargarCreativa() {
        for (com.modamod.item.ClothingPatternItem p : com.modamod.item.ClothingPatternItem.todos()) {
            bolsaPatrones.merge(net.minecraft.registry.Registries.ITEM.getId(p), 1, (x, y) -> Math.max(x, y));
        }
        markDirty();
        sincronizar();
    }

    // ── La bolsa de patrones (2026-10-12, plan de GUIs, mismo esquema que la bolsa de moldes de la Modeladora): un solo
    // depósito sin lugares contados (lista propia en el NBT) que reemplaza al almacén de 30. El desplegable de la pantalla
    // lista los patrones de la bolsa y elegir uno lo FIJA en el cuadradito sin gastarlo.

    /** Patrones guardados: id del ítem → cantidad. */
    private final java.util.LinkedHashMap<Identifier, Integer> bolsaPatrones = new java.util.LinkedHashMap<>();
    public static final int BOLSA_TOPE_POR_PATRON = 9999;

    public java.util.Map<Identifier, Integer> bolsaPatrones() { return java.util.Collections.unmodifiableMap(bolsaPatrones); }

    public static boolean esPatron(ItemStack stack) { return !stack.isEmpty() && stack.getItem() instanceof ClothingPatternItem; }

    /** Suma un patrón a la bolsa (servidor). */
    public boolean absorberPatron(ItemStack stack) {
        if (world != null && world.isClient) return false;
        if (!esPatron(stack)) return false;
        bolsaPatrones.merge(net.minecraft.registry.Registries.ITEM.getId(stack.getItem()), stack.getCount(),
                (x, y) -> Math.min(BOLSA_TOPE_POR_PATRON, x + y));
        sincronizar();
        return true;
    }

    /** Fija el patrón {@code id} de la bolsa en el cuadradito {@code i} de la categoría actual, sin gastarlo. */
    public boolean elegirPatron(int i, Identifier id) {
        if (estado == Estado.TINIENDO || regionDe(categoria, i) == null || bolsaPatrones.getOrDefault(id, 0) <= 0) return false;
        if (!(net.minecraft.registry.Registries.ITEM.get(id) instanceof ClothingPatternItem p)) return false;
        seleccionada[categoria.ordinal()] = i;
        Casilla cas = casilla(i);
        cas.patronFijado = p.patternId;
        cas.fijada = true;
        sincronizar();
        return true;
    }

    /** Saca un patrón de la bolsa al inventario del jugador. */
    public boolean sacarPatron(PlayerEntity jugador, Identifier id) {
        int n = bolsaPatrones.getOrDefault(id, 0);
        if (n <= 0) return false;
        if (n == 1) bolsaPatrones.remove(id); else bolsaPatrones.put(id, n - 1);
        jugador.getInventory().offerOrDrop(new ItemStack(net.minecraft.registry.Registries.ITEM.get(id)));
        sincronizar();
        return true;
    }

    /** Lo que quedó en el almacén viejo (30 lugares) y en los slots de los cuadraditos pasa a la bolsa. */
    private void drenarViejos() {
        boolean cambio = false;
        for (int i = 0; i < ALMACEN_TOTAL; i++) {
            int s = slotAlmacen(i);
            ItemStack en = getStack(s);
            if (en.isEmpty()) continue;
            if (s >= SLOT_ALMACEN_EXTRA) almacenExtra.set(s - SLOT_ALMACEN_EXTRA, ItemStack.EMPTY); else items.set(s, ItemStack.EMPTY);
            if (!absorberPatron(en) && world != null) {
                net.minecraft.util.ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, en);
            }
            cambio = true;
        }
        for (int s = CASILLAS_INICIO; s < CASILLAS_INICIO + CASILLAS * Categoria.values().length; s++) {
            ItemStack en = items.get(s);
            if (en.isEmpty()) continue;
            items.set(s, ItemStack.EMPTY);
            absorberPatron(en);
            cambio = true;
        }
        if (cambio) sincronizar();
    }

    // ── Diseños como ítems y bolsa de diseños (2026-10-12, "te falto convertir diseños de la estacion de tintes"): mismo
    // esquema que la Modeladora. El diseño guarda los 12 cuadraditos de la categoría y su orden como NBT.

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
    /** "★ Último": el diseño con el que se tiñó la última prenda de cada categoría (2026-10-12). */
    private final Map<Categoria, NbtCompound> ultimoPorCategoria = new EnumMap<>(Categoria.class);

    public List<ItemStack> disenosItem() { return java.util.Collections.unmodifiableList(disenosItem); }
    public boolean hayUltimo() { return ultimoPorCategoria.containsKey(categoria); }

    public static boolean esDiseno(ItemStack stack) {
        return stack.getItem() instanceof DisenoTintaItem && stack.get(com.modamod.item.ModamodComponents.DISENO_TINTE) != null;
    }

    /** Los cuadraditos y el orden de {@code cat} como un NBT. */
    private NbtCompound disenoANbt(Categoria cat) {
        NbtCompound d = new NbtCompound();
        int c = cat.ordinal();
        d.putString("Categoria", cat.name());
        d.put("Casillas", casillasANbt(casillas[c]));
        d.putIntArray("Orden", orden[c]);
        return d;
    }

    /** Carga un diseño en NBT: cambia a su categoría y reemplaza los cuadraditos y el orden. */
    private boolean aplicarDisenoNbt(NbtCompound d) {
        if (estado == Estado.TINIENDO) return false;
        Categoria cat;
        try {
            cat = Categoria.valueOf(d.getString("Categoria"));
        } catch (IllegalArgumentException e) {
            return false;
        }
        int c = cat.ordinal();
        Casilla[] leidas = casillasDeNbt(d.getList("Casillas", NbtElement.COMPOUND_TYPE));
        System.arraycopy(leidas, 0, casillas[c], 0, CASILLAS);
        orden[c] = ordenValido(d.getIntArray("Orden"), cat);
        if (regionDe(cat, seleccionada[c]) == null) seleccionada[c] = primeraUsable(cat);
        categoria = cat;
        sincronizar();
        return true;
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

    public boolean cargarDisenoItem(int idx) {
        if (idx < 0 || idx >= disenosItem.size()) return false;
        NbtCompound d = disenosItem.get(idx).get(com.modamod.item.ModamodComponents.DISENO_TINTE);
        return d != null && aplicarDisenoNbt(d);
    }

    public boolean cargarUltimo() {
        NbtCompound d = ultimoPorCategoria.get(categoria);
        return d != null && aplicarDisenoNbt(d);
    }

    public boolean sacarDiseno(PlayerEntity jugador, int idx) {
        if (idx < 0 || idx >= disenosItem.size()) return false;
        // Click derecho borra sin dar el ítem (2026-10-25, "cuando le das click secundario al diseño te da el molde sin
        // haber necesitado un diseño en blanco"): el ítem sale solo con Exportar.
        disenosItem.remove(idx);
        sincronizar();
        return true;
    }

    /** Fabrica el ítem "Diseño de tinte" con los cuadraditos de la categoría actual; gasta un diseño en blanco (la creativa, no). */
    public boolean crearDisenoItem(PlayerEntity jugador, String nombre) {
        if (estado == Estado.TINIENDO) { com.modamod.util.DisenosMaquina.avisarTrabajando(jugador); return false; }
        if (!hayFijadas()) { jugador.sendMessage(Text.translatable("modamod.disenos.sin_fijar_tinte"), true); return false; }
        ItemStack item = new ItemStack(TinturasMod.DISENO_TINTA);
        item.set(com.modamod.item.ModamodComponents.DISENO_TINTE, disenoANbt(categoria));
        String n = nombre == null ? "" : nombre.trim();
        if (n.length() > 24) n = n.substring(0, 24);
        item.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, (n.isEmpty()
                ? Text.translatable("modamod.tinturas.diseno.defecto",
                        Text.translatable("modamod.modelado.categoria." + categoria.name().toLowerCase(java.util.Locale.ROOT)))
                : Text.literal(n)).styled(st -> st.withItalic(false)));
        // Directo a la lista de la máquina (2026-10-25, "que en la pestaña de diseños de todas las maquinas quede").
        if (!com.modamod.util.DisenosMaquina.guardar(jugador, disenosItem, DISENOS_ITEM_MAXIMO, item)) return false;
        sincronizar();
        return true;
    }

    /** Compatibilidad: lo que antes iba al almacén ahora va a la bolsa (siempre entra). */
    private boolean guardarEnAlmacen(ItemStack stack) {
        return absorberPatron(stack);
    }

    /**
     * Sube ({@code +1}) o baja ({@code -1}) el cuadradito seleccionado en
     * el orden de pintado, saltando por encima de la próxima capa que SÍ
     * se ve (las que no están en la vista previa no cuentan, si no el
     * botón parecería no hacer nada).
     */
    private boolean mover(int dir) {
        int c = categoria.ordinal();
        int[] o = orden[c];
        int sel = seleccionada();
        int pos = -1;
        for (int k = 0; k < CASILLAS; k++) if (o[k] == sel) pos = k;
        if (pos < 0) return false;
        int destino = -1;
        for (int k = pos + dir; k >= 0 && k < CASILLAS; k += dir) {
            if (enBorrador(categoria, o[k])) { destino = k; break; }
        }
        if (destino < 0) return false;
        // Corre los del medio un lugar y deja al seleccionado en "destino".
        for (int k = pos; k != destino; k += dir) o[k] = o[k + dir];
        o[destino] = sel;
        return true;
    }

    public int disenosGuardados() { return disenosPorCategoria.get(categoria).size(); }

    /** Nombre del diseño guardado en ese casillero (para el hover), o null si está vacío. */
    @Nullable
    public String nombreDiseno(int idx) {
        List<DisenoGuardado> lista = disenosPorCategoria.get(categoria);
        return idx >= 0 && idx < lista.size() ? lista.get(idx).nombre() : null;
    }

    /**
     * Recibido desde {@link GuardarDisenoTinturasPayload} (el nombre lo
     * escribe el jugador en un {@code TextFieldWidget}, no entra en un
     * {@code clickButton(int)} común) — guarda los 12 cuadraditos de la
     * categoría actual + su orden con nombre, en el próximo casillero libre.
     */
    public boolean guardarDiseno(String nombreCrudo) {
        List<DisenoGuardado> lista = disenosPorCategoria.get(categoria);
        if (!hayFijadas() || lista.size() >= DISENOS_MAXIMO) return false;
        String nombre = nombreCrudo == null || nombreCrudo.isBlank()
                ? "#" + (lista.size() + 1) : nombreCrudo.trim();
        if (nombre.length() > 24) nombre = nombre.substring(0, 24);
        int c = categoria.ordinal();
        Casilla[] copia = new Casilla[CASILLAS];
        for (int i = 0; i < CASILLAS; i++) copia[i] = casillas[c][i].copia();
        lista.add(new DisenoGuardado(nombre, copia, orden[c].clone()));
        sincronizar();
        return true;
    }

    /** Carga el diseño {@code idx} de la categoría actual: reemplaza los 12 cuadraditos y su orden. */
    private boolean cargarDiseno(int idx) {
        List<DisenoGuardado> lista = disenosPorCategoria.get(categoria);
        if (idx < 0 || idx >= lista.size()) return false;
        DisenoGuardado d = lista.get(idx);
        int c = categoria.ordinal();
        for (int i = 0; i < CASILLAS; i++) casillas[c][i] = d.casillas()[i].copia();
        orden[c] = d.orden().clone();
        return true;
    }

    private boolean borrarDiseno(int idx) {
        List<DisenoGuardado> lista = disenosPorCategoria.get(categoria);
        if (idx < 0 || idx >= lista.size()) return false;
        lista.remove(idx);
        return true;
    }

    /** Shift + click derecho, mano vacía: vuelve a blanco los cuadraditos SIN fijar de la categoría actual, sin abrir la GUI. */
    public void resetearBorrador() {
        for (Casilla cas : casillas[categoria.ordinal()]) {
            if (!cas.fijada) java.util.Arrays.fill(cas.mezcla, 0);
        }
        sincronizar();
    }

    /** Directo desde el slider del lado cliente (ver TinturasScreen) para el cuadradito seleccionado, cuantizado a NIVELES_MEZCLA pasos. */
    public int botonMezcla(int canal, int nivel) {
        return BTN_MEZCLA_BASE + canal * NIVELES_MEZCLA + MathHelper.clamp(nivel, 0, NIVELES_MEZCLA - 1);
    }

    // ── tanque CMYK ─────────────────────────────────────────────────

    /** Click derecho con tinte en mano. Devuelve false si ese tanque ya está lleno. */
    public boolean cargar(int canal, int cantidad) {
        if (cargas[canal] >= CARGA_MAXIMA) return false;
        cargas[canal] = Math.min(CARGA_MAXIMA, cargas[canal] + cantidad);
        sincronizarVistaTanques();
        sincronizar();
        return true;
    }

    // ── arrancar el teñido de una prenda ─────────────────────────────

    /**
     * Prenda reconocida entrando a la máquina (slot de entrada, hopper o
     * click derecho directo — ver {@code TinturasBlock#onUseWithItem}).
     * Usa la fijada SELECCIONADA (no el borrador — hay que Fijar primero).
     *
     * <p>Antes (instantáneo) esto mutaba el stack ahí mismo y devolvía;
     * ahora (2026-09-21, "que cada maquina tome su tiempo... 10 la
     * estacion de tintes") solo VALIDA y ARRANCA el ciclo — quien llama
     * es responsable de dejar {@code stack} puesto en {@link #prendaEntrada}
     * en caso de éxito (esta función no lo toca, para que {@code setStack}
     * y {@code TinturasBlock#onUseWithItem} puedan decidir cada uno qué
     * hacer con el original en caso de RECHAZO — ver sus propios
     * comentarios). El tinte de verdad se aplica en {@link #tick} al
     * cabo de {@link #TICKS_TENIDO}, releyendo la fijada en ESE momento
     * (mismo criterio que {@code ModeladoBlockEntity#procesar}).
     *
     * <p>Costo de tinta: 1 dosis por cada canal que esté ARRIBA de 0 en
     * la mezcla de la fijada (mismo criterio "una dosis por uso" que la
     * Sublimadora, sin proporcionalidad fina al nivel exacto del
     * slider), gastada AHORA — mismo patrón que
     * {@code SublimadoraBlockEntity#intentarPrensar}. Se chequean los 4
     * canales ANTES de descontar ninguno, para no dejar un descuento a
     * medias si falta un solo canal.
     *
     * @return null si arrancó el ciclo; si no, el motivo por el que no.
     */
    @Nullable
    public Text iniciarTenido(ItemStack stack) {
        return iniciarTenidoInterno(stack);
    }

    /**
     * Botón Teñir de la GUI (2026-09-28, "la unica forma de activacion de
     * la maquina es saliendo de la gui o poniendo la prenda arriba"): una
     * prenda puesta en la Entrada ANTES de fijar el diseño quedaba cruda
     * esperando para siempre — no hay reintento solo. Esto reintenta con
     * la prenda que ya está en la Entrada.
     *
     * @return null si arrancó; si no, el motivo.
     */
    @Nullable
    public Text reintentarTenido() {
        if (prendaEntrada.isEmpty()) return Text.translatable("modamod.tinturas.aviso.sin_prenda");
        if (estado != Estado.REPOSO) return Text.translatable("modamod.tinturas.hint.tiniendo");
        ItemStack prenda = prendaEntrada;
        prendaEntrada = ItemStack.EMPTY;
        Text motivo = iniciarTenidoInterno(prenda);
        if (motivo != null) {
            prendaEntrada = prenda;
            sincronizar();
        }
        return motivo;
    }

    /** Slot de Entrada de la GUI: deja la prenda cruda esperando el botón Teñir, sin arrancar el ciclo. */
    public void cargarEntradaSinArrancar(ItemStack stack) {
        prendaEntrada = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        Categoria entra = prendaEntrada.isEmpty() ? null : categoriaDe(prendaEntrada);
        if (entra != null) categoria = entra;   // la GUI sigue a la prenda que entra (2026-10-04)
        sincronizar();
    }

    /** ¿Tiene sentido apretar Teñir ahora? (para habilitar el botón en la GUI) */
    public boolean puedeReintentar() {
        return estado == Estado.REPOSO && !prendaEntrada.isEmpty() && salida.isEmpty();
    }

    @Nullable
    private Text iniciarTenidoInterno(ItemStack stack) {
        if (estado != Estado.REPOSO) {
            return Text.translatable("modamod.tinturas.aviso.procesando");
        }
        if (!prendaEntrada.isEmpty()) {
            // Ya hay una prenda cruda trabada esperando (un intento
            // anterior que rechazó) — no la pisa.
            return Text.translatable("modamod.tinturas.aviso.entrada_ocupada");
        }
        if (!salida.isEmpty()) {
            // Bug real (2026-09-28): tick() pisaba salida sin mirar, así
            // que una prenda terminada que no se había podido empujar
            // (sin cofre a la derecha) se perdía al terminar la siguiente.
            return Text.translatable("modamod.tinturas.aviso.salida_ocupada");
        }
        // La categoría la decide el TIPO de la prenda en mano, no la que
        // esté mostrando la GUI en ese momento — así un click derecho
        // siempre usa "la fijada de pantalón" sobre un pantalón, sin
        // importar qué pestaña quedó abierta la última vez.
        Categoria cat = categoriaDe(stack);
        if (cat == null) {
            return Text.translatable("modamod.tinturas.aviso.no_es_prenda");
        }
        categoria = cat;   // la GUI sigue a la prenda que entra (2026-10-04)
        // El diseño son los cuadraditos FIJADOS de esa categoría (2026-09-27).
        if (!hayFijadas(cat)) {
            return Text.translatable("modamod.tinturas.aviso.sin_fijada_seleccionada");
        }
        // Fijadas hay, pero todas con el ojo cerrado: no hay nada que aplicar.
        if (capasDe(cat, false).isEmpty()) {
            return Text.translatable("modamod.tinturas.aviso.todas_ocultas");
        }

        Garment garment = Garments.de(stack);
        if (garment != null && garment.regionesDe(Operacion.TENIR).isEmpty()
                && garment.regionesDe(Operacion.PATRON).isEmpty()) {
            return Text.translatable("modamod.tinturas.aviso.sin_tenido_para_esta_prenda");
        }

        // Costo de tinta: la unión de canales usados por TODOS los
        // cuadraditos fijados — 1 dosis por canal, no por capa.
        boolean[] canalUsado = new boolean[4];
        for (int i = 0; i < CASILLAS; i++) {
            Casilla cas = casillas[cat.ordinal()][i];
            if (regionDe(cat, i) == null || !cas.fijada || cas.oculta) continue;
            for (int col = 0; col < cas.colores; col++) {
                // Solo C/M/Y/K — la transparencia (T) no gasta tinta.
                for (int canal = 0; canal < 4; canal++) if (cas.mezclaDe(col)[canal] > 0) canalUsado[canal] = true;
            }
        }
        for (int canal = 0; canal < 4; canal++) {
            if (canalUsado[canal] && cargas[canal] <= 0 && !com.modamod.util.MaquinaCreativa.es(this)) {
                return Text.translatable("modamod.tinturas.aviso.sin_tinte",
                        Text.translatable("modamod.sublimadora.tinta." + NOMBRE_CANAL[canal]));
            }
        }
        for (int canal = 0; canal < 4; canal++) {
            if (canalUsado[canal] && !com.modamod.util.MaquinaCreativa.es(this)) cargas[canal]--;
        }
        sincronizarVistaTanques();

        ultimoPorCategoria.put(cat, disenoANbt(cat));
        estado = Estado.TINIENDO;
        progreso = 0;
        prendaEntrada = stack;
        sincronizar();
        return null;
    }

    /**
     * Pinta el diseño sobre {@code stack} — compartido por {@link #tick}
     * (de verdad, al terminar el ciclo) y la vista previa. Desde
     * 2026-09-27 las capas de Tinturas viven en su propio componente
     * ({@link com.modamod.item.ModamodComponents#CAPAS_TINTE}, cada
     * una con su región/modo/opacidad): se borran los patrones viejos de
     * los slots fijos PATTERN_* para que el diseño nuevo no se sume a uno
     * anterior, y el color base de la prenda queda como estaba (una capa
     * lisa de prenda entera lo tapa si se quiere).
     */
    /** ¿Entra al slot de entrada? Las prendas y el retazo de aplique (2026-10-04, fase 4). */
    public static boolean aceptaEntrada(ItemStack stack) {
        return ModamodDye.isClothing(stack) || stack.getItem() instanceof com.modamod.aplique.RetazoApliqueItem
                || stack.getItem() instanceof com.modamod.item.ZonasTenibles || stack.getItem() instanceof net.minecraft.item.BannerItem
                || stack.getItem() instanceof com.modamod.item.CaraItem;
    }

    /**
     * Pinta {@code stack} con el diseño de {@code cat}: las capas de siempre, o, en un retazo de aplique, los 3
     * colores de sus zonas (2026-10-04, fase 4): cada cuadradito 1..3 fijado (o en el borrador) manda en su zona y
     * las zonas sin tocar conservan el color que ya traía el retazo.
     */
    private void pintar(ItemStack stack, Categoria cat, boolean borrador) {
        // El banner (2026-10-24): solo las capas; su color base es el del banner y lo dibuja BannerHD. Máscara y barbijo
        // igual, sobre blanco (CaraRenderer).
        if (cat == Categoria.BANNER || cat == Categoria.MASCARA || cat == Categoria.BARBIJO) {
            List<RegionResolver.CapaPatron> capas = capasDe(cat, borrador);
            if (capas.isEmpty()) stack.remove(com.modamod.item.ModamodComponents.CAPAS_TINTE);
            else stack.set(com.modamod.item.ModamodComponents.CAPAS_TINTE, List.copyOf(capas));
            return;
        }
        if (stack.getItem() instanceof com.modamod.item.ZonasTenibles zt) {
            pintarZonas(stack, zt, capasDe(cat, borrador));
            return;
        }
        if (cat != Categoria.APLIQUE) {
            pintarStack(stack, capasDe(cat, borrador));
            return;
        }
        List<Integer> actuales = com.modamod.aplique.RetazoApliqueItem.colores(stack);
        int c = cat.ordinal();
        int[] nuevos = new int[3];
        for (int i = 0; i < 3; i++) {
            Casilla cas = casillas[c][i];
            boolean aplica = (borrador ? enBorrador(cat, i) : cas.fijada) && !cas.oculta;
            nuevos[i] = aplica ? cas.color() & 0xFFFFFF : actuales.get(i);
        }
        com.modamod.aplique.RetazoApliqueItem.conColores(stack, nuevos[0], nuevos[1], nuevos[2]);
    }

    /**
     * Sombrero, banda y calzado (2026-10-15, "aplicar patrones... como las otras prendas"): las capas de cada cuadradito
     * van a su zona ({@code ZONA_n}, ver {@link com.modamod.render.ZonaTextura}) y el color base de la zona es el de su
     * capa lisa más arriba, como en {@link #pintarStack}. Con la simetría prendida el pie izquierdo copia las capas del
     * derecho (y los colores: el componente izquierdo se borra y vuelve a copiar al derecho).
     */
    private void pintarZonas(ItemStack stack, com.modamod.item.ZonasTenibles zt, List<RegionResolver.CapaPatron> capas) {
        boolean pares = zt.tienePares(), sim = pares && simetriaCalzado;
        int z1 = com.modamod.region.RegionPintura.ZONA_1.ordinal();
        List<RegionResolver.CapaPatron> todas = new ArrayList<>();
        for (RegionResolver.CapaPatron c : capas) {
            if (!sim || c.region().ordinal() - z1 < 3) todas.add(c);
        }
        if (sim) {
            for (RegionResolver.CapaPatron c : capas) {
                int z = c.region().ordinal() - z1;
                if (z < 3) todas.add(RegionResolver.conRegion(c, com.modamod.region.RegionPintura.values()[z1 + z + 3]));
            }
        }
        for (boolean izq : pares ? new boolean[]{false, true} : new boolean[]{false}) {
            if (izq && sim) break;
            List<Integer> actuales = zt.coloresDe(stack, izq);
            int[] nuevos = new int[3];
            for (int z = 0; z < 3; z++) {
                nuevos[z] = actuales.get(z);
                com.modamod.region.RegionPintura reg = com.modamod.render.ZonaTextura.region(z, izq);
                for (int k = todas.size() - 1; k >= 0; k--) {
                    RegionResolver.CapaPatron c = todas.get(k);
                    if (c.region() == reg && c.lisa() && c.opacidad() >= 100 && c.modo() == com.modamod.region.ModoMezcla.NORMAL
                            && c.extras().isEmpty() && (c.color() >>> 24) == 0) {
                        nuevos[z] = c.color() & 0xFFFFFF;
                        break;
                    }
                }
            }
            if (pares && izq) zt.conColoresDe(stack, nuevos[0], nuevos[1], nuevos[2], true);
            else zt.conColoresDe(stack, nuevos[0], nuevos[1], nuevos[2]);
        }
        if (todas.isEmpty()) stack.remove(com.modamod.item.ModamodComponents.CAPAS_TINTE);
        else stack.set(com.modamod.item.ModamodComponents.CAPAS_TINTE, List.copyOf(todas));
    }

    private static void pintarStack(ItemStack stack, List<RegionResolver.CapaPatron> capas) {
        RegionResolver.quitarPatron(stack, Lado.AMBAS);
        if (capas.isEmpty()) return;
        // Color base = la capa lisa de prenda entera, a pleno y en modo
        // Normal, que quede más arriba (2026-09-28): es lo que muestra el
        // ÍCONO del ítem (que no lee las capas). Debajo de esa capa el
        // color base no se ve nunca, así que la prenda puesta no cambia.
        for (int k = capas.size() - 1; k >= 0; k--) {
            RegionResolver.CapaPatron c = capas.get(k);
            if (c.lisa() && c.region() == com.modamod.region.RegionPintura.TODO && c.opacidad() >= 100
                    && c.modo() == com.modamod.region.ModoMezcla.NORMAL && c.extras().isEmpty()
                    && (c.color() >>> 24) == 0) {
                if (stack.getItem() instanceof com.modamod.sublimadora.RemeraItem) {
                    stack.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(c.color() & 0xFFFFFF, false));
                } else {
                    RegionResolver.ponerColorBase(stack, Lado.AMBAS, c.color() & 0xFFFFFF);
                }
                break;
            }
        }
        stack.set(com.modamod.item.ModamodComponents.CAPAS_TINTE, List.copyOf(capas));
    }

    /**
     * La prenda a mostrar en el visor 3D, con el BORRADOR ya pintado — a
     * pedido (2026-09-18, "quiero que sea un preview de la pieza no solo
     * del color"): una copia descartable, nunca gasta tinta ni toca el
     * inventario real. Prioriza lo que hay de VERDAD en la máquina: si ya
     * salió teñida, esa; si hay una cruda en la entrada, ESA con el
     * borrador encima; si no, el representativo de la categoría.
     */
    public ItemStack prendaDeVistaPrevia() { return prendaDeVistaPrevia(-1); }

    /**
     * Igual, con la zona del cuadradito {@code resaltada} (el que tiene el
     * mouse encima en el esquema o en el panel de capas, -1 = ninguno)
     * resaltada en 3D — Fase B (2026-09-28), "resto apagado": un velo
     * oscuro sobre todo lo que NO es esa zona. Solo en esta copia de la
     * vista previa, nunca en la prenda de verdad.
     */
    public ItemStack prendaDeVistaPrevia(int resaltada) {
        if (!salida.isEmpty()) return conVelo(salida.copy(), categoriaDe(salida), resaltada);
        if (!prendaEntrada.isEmpty()) {
            Categoria cat = categoriaDe(prendaEntrada);
            if (cat != null) {
                ItemStack copia = prendaEntrada.copy();
                pintar(copia, cat, true);
                return conVelo(copia, cat, resaltada);
            }
        }
        net.minecraft.item.Item item = itemRepresentativo(categoria);
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        pintar(stack, categoria, true);
        return conVelo(stack, categoria, resaltada);
    }

    /** Opacidad del velo de "resto apagado" — lo de afuera de la zona queda al 40% de brillo. */
    private static final int OPACIDAD_VELO = 60;

    private ItemStack conVelo(ItemStack stack, @Nullable Categoria cat, int resaltada) {
        if (resaltada < 0 || cat != categoria) return stack;
        com.modamod.region.RegionPintura region = regionDe(cat, resaltada);
        // Prenda entera: no hay "resto" que apagar.
        if (region == null || region == com.modamod.region.RegionPintura.TODO) return stack;
        List<RegionResolver.CapaPatron> capas = new ArrayList<>(
                stack.getOrDefault(com.modamod.item.ModamodComponents.CAPAS_TINTE, List.of()));
        capas.add(new RegionResolver.CapaPatron(null, 0x000000, TamanoPatron.GRANDE, 0f, 0.5f,
                com.modamod.render.PatronGenerador.Forma.ALTERNADO, false, region,
                com.modamod.region.ModoMezcla.MULTIPLICAR, OPACIDAD_VELO,
                com.modamod.render.Repeticion.GRILLA, 0, List.of(), false,
                com.modamod.render.Variacion.FIJO, true));
        stack.set(com.modamod.item.ModamodComponents.CAPAS_TINTE, List.copyOf(capas));
        return stack;
    }

    /**
     * La ÚLTIMA prenda de VERDAD procesada (no el representativo
     * genérico con el borrador encima) — a pedido (2026-09-21, "quiero
     * que las 3 muestren la ultima prenda con preview del ultimo
     * seteado"): la pantallita del bloque usa esto.
     */
    private ItemStack ultimaVistaPrevia = ItemStack.EMPTY;

    public ItemStack vistaPreviaPersistente() {
        if (!salida.isEmpty()) { ultimaVistaPrevia = salida; return ultimaVistaPrevia; }
        if (!prendaEntrada.isEmpty()) {
            Categoria cat = categoriaDe(prendaEntrada);
            if (cat != null) {
                ItemStack copia = prendaEntrada.copy();
                pintar(copia, cat, true);
                ultimaVistaPrevia = copia;
                return ultimaVistaPrevia;
            }
        }
        return ultimaVistaPrevia;
    }

    @Nullable
    private static net.minecraft.item.Item itemRepresentativo(Categoria cat) {
        return switch (cat) {
            case REMERA -> com.modamod.sublimadora.ModItems.REMERA;
            case PANTALON -> com.modamod.item.ModamodItems.PANTALON;
            case MEDIAS -> com.modamod.item.ModamodItems.SOCKS_SOLID;
            case CALIENTABRAZOS -> com.modamod.item.ModamodItems.CALIENTABRAZOS;
            case POLLERA -> com.modamod.item.ModamodItems.POLLERA;
            case CAPA -> com.modamod.item.ModamodItems.CAPA;
            case APLIQUE -> com.modamod.item.ModamodItems.RETAZO_APLIQUE;
            case DELANTAL -> com.modamod.item.ModamodItems.DELANTAL;
            case SOMBRERO -> com.modamod.item.ModamodItems.SOMBRERO_BRUJA;
            case CALZADO -> com.modamod.item.ModamodItems.BORCEGOS;
            case BANDA -> com.modamod.item.ModamodItems.BANDA;
            case MALETIN -> com.modamod.item.ModamodItems.MALETIN;
            case BANNER -> net.minecraft.item.Items.WHITE_BANNER;
            case MASCARA -> com.modamod.item.ModamodItems.ANTIFAZ;
            case BARBIJO -> com.modamod.item.ModamodItems.BARBIJO;
        };
    }

    // ── inventario (almacén + Activo) ────────────────────────────────

    @Override public int size() { return SLOT_ALMACEN_EXTRA + ALMACEN_EXTRA; }
    @Override public boolean isEmpty() {
        for (ItemStack s : items) if (!s.isEmpty()) return false;
        for (ItemStack s : almacenExtra) if (!s.isEmpty()) return false;
        if (!prendaEntrada.isEmpty() || !salida.isEmpty()) return false;
        for (int c : cargas) if (c > 0) return false;
        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        if (slot >= SLOT_ALMACEN_EXTRA) return almacenExtra.get(slot - SLOT_ALMACEN_EXTRA);
        if (slot == SLOT_PRENDA_ENTRADA) return prendaEntrada;
        if (slot == SLOT_SALIDA) return salida;
        // Misma instancia entre llamadas — ver el javadoc de tintaSlotView.
        if (slot >= SLOT_TINTA_BASE) return tintaSlotView[slot - SLOT_TINTA_BASE];
        return items.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        if (slot >= SLOT_ALMACEN_EXTRA) {
            ItemStack result = net.minecraft.inventory.Inventories.splitStack(almacenExtra, slot - SLOT_ALMACEN_EXTRA, amount);
            if (!result.isEmpty()) markDirty();
            return result;
        }
        if (slot == SLOT_PRENDA_ENTRADA) {
            ItemStack resultado = prendaEntrada.split(amount);
            if (!resultado.isEmpty()) sincronizar();
            return resultado;
        }
        if (slot == SLOT_SALIDA) {
            ItemStack resultado = salida.split(amount);
            if (!resultado.isEmpty()) sincronizar();
            return resultado;
        }
        if (slot >= SLOT_TINTA_BASE) {
            int canal = slot - SLOT_TINTA_BASE;
            int sacado = Math.min(cargas[canal], amount);
            if (sacado <= 0) return ItemStack.EMPTY;
            cargas[canal] -= sacado;
            sincronizarVistaTanques();
            sincronizar();
            return new ItemStack(TINTES[canal], sacado);
        }
        ItemStack result = net.minecraft.inventory.Inventories.splitStack(items, slot, amount);
        if (!result.isEmpty()) markDirty();
        return result;
    }

    @Override
    public ItemStack removeStack(int slot) {
        if (slot >= SLOT_ALMACEN_EXTRA) {
            return net.minecraft.inventory.Inventories.removeStack(almacenExtra, slot - SLOT_ALMACEN_EXTRA);
        }
        if (slot == SLOT_PRENDA_ENTRADA) {
            ItemStack resultado = prendaEntrada;
            prendaEntrada = ItemStack.EMPTY;
            sincronizar();
            return resultado;
        }
        if (slot == SLOT_SALIDA) {
            ItemStack resultado = salida;
            salida = ItemStack.EMPTY;
            sincronizar();
            return resultado;
        }
        if (slot >= SLOT_TINTA_BASE) {
            int canal = slot - SLOT_TINTA_BASE;
            if (cargas[canal] <= 0) return ItemStack.EMPTY;
            ItemStack resultado = new ItemStack(TINTES[canal], cargas[canal]);
            cargas[canal] = 0;
            sincronizarVistaTanques();
            sincronizar();
            return resultado;
        }
        return net.minecraft.inventory.Inventories.removeStack(items, slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot >= SLOT_ALMACEN_EXTRA) {
            almacenExtra.set(slot - SLOT_ALMACEN_EXTRA, stack);
            if (stack.getCount() > stack.getMaxCount()) stack.setCount(stack.getMaxCount());
            sincronizar();
            return;
        }
        if (slot == SLOT_PRENDA_ENTRADA) {
            if (stack.isEmpty()) {
                prendaEntrada = ItemStack.EMPTY;
            } else {
                // Arranca el ciclo (2026-09-21, 10s) — ver el javadoc de
                // iniciarTenido. Éxito o no, sincronizar() ya corrió adentro.
                ItemStack copia = stack.copyWithCount(1);
                Categoria catCadena = categoriaDe(copia);
                if (catCadena != null) categoria = catCadena;   // la GUI sigue a la prenda que entra (2026-10-04)
                if ((com.modamod.util.InventarioUtil.enCadena && linea) && estado == Estado.REPOSO
                        && prendaEntrada.isEmpty() && salida.isEmpty()
                        && (catCadena == null || capasDe(catCadena, false).isEmpty())) {
                    // Llegó por la cadena y no hay nada que teñir: pasa de
                    // largo (2026-10-04, "me parece perfecto que saltee").
                    salida = copia;
                    empujarSalida();
                    return;
                }
                Text motivo = iniciarTenido(copia);
                if (motivo != null) {
                    // No se pudo arrancar (sin fijada, sin tinta, ya
                    // ocupada): se queda CRUDA en la entrada esperando.
                    prendaEntrada = copia;
                    sincronizar();
                }
                // Si arrancó, iniciarTenido() ya dejó prendaEntrada = copia.
                return;
            }
        } else if (slot == SLOT_SALIDA) {
            salida = stack;
        } else if (slot >= SLOT_TINTA_BASE) {
            cargas[slot - SLOT_TINTA_BASE] = stack.isEmpty() ? 0 : MathHelper.clamp(stack.getCount(), 0, CARGA_MAXIMA);
            sincronizarVistaTanques();
        } else {
            items.set(slot, stack);
            if (stack.getCount() > stack.getMaxCount()) stack.setCount(stack.getMaxCount());
            int casilla = slot - CASILLAS_INICIO;
            if (!stack.isEmpty() && casilla >= 0 && casilla < CASILLAS * Categoria.values().length) {
                Categoria cat = Categoria.values()[casilla / CASILLAS];
                int i = casilla % CASILLAS;
                if (cat == categoria && regionDe(cat, i) != null && stack.getItem() instanceof ClothingPatternItem) {
                    if (stack.getCount() > 1) stack.setCount(1);
                    chincheta(i);
                }
            }
        }
        sincronizar();
    }

    // canPlayerUse ya está definido más abajo (sección "pantalla") — sirve para las dos interfaces (Inventory y el ScreenHandler).
    @Override
    public boolean isValid(int slot, ItemStack stack) {
        if (slot >= SLOT_ALMACEN_EXTRA) return stack.getItem() instanceof ClothingPatternItem;
        if (slot == SLOT_PRENDA_ENTRADA) {
            // estado == REPOSO también acá (2026-09-21): mientras está
            // TINIENDO, prendaEntrada NO está vacía (sigue mostrando la
            // prenda cruda todo el ciclo, ver tick()) así que esta
            // condición ya alcanzaría sola, pero el chequeo explícito
            // documenta la regla igual si el orden interno cambiara.
            return estado == Estado.REPOSO && prendaEntrada.isEmpty() && aceptaEntrada(stack);
        }
        // Salida: solo la máquina escribe acá — igual que SALIDA en
        // ModeladoBlockEntity/SublimadoraBlockEntity, no se puede insertar
        // a mano ni por hopper.
        if (slot == SLOT_SALIDA) return false;
        if (slot >= SLOT_TINTA_BASE) {
            int canal = slot - SLOT_TINTA_BASE;
            return stack.isOf(TINTES[canal]) && cargas[canal] < CARGA_MAXIMA;
        }
        return stack.getItem() instanceof ClothingPatternItem;
    }

    @Override
    public void clear() {
        items.clear();
        almacenExtra.clear();
        prendaEntrada = ItemStack.EMPTY;
        salida = ItemStack.EMPTY;
        java.util.Arrays.fill(cargas, 0);
        sincronizarVistaTanques();
        sincronizar();
    }

    // ── SidedInventory: prenda por el costado IZQUIERDO, tinta por ATRÁS
    // (2026-09-20). Desde 2026-10-15 el modelo ya no está invertido y esto es igual que en Modeladora/Sublimadora.
    private Direction ladoAtras() {
        return getCachedState().get(TinturasBlock.FACING).getOpposite();
    }

    private Direction ladoIzquierdo() {
        return getCachedState().get(TinturasBlock.FACING).getOpposite().rotateYCounterclockwise();
    }

    @Override
    public Direction ladoSalida() { return ladoDerecho(); }

    /** El lado opuesto al de carga — hacia ahí se empuja la prenda ya teñida. */
    private Direction ladoDerecho() {
        return ladoIzquierdo().getOpposite();
    }

    /**
     * Empuja la prenda de {@link #SLOT_SALIDA} hacia la derecha — a
     * pedido (2026-09-20, "las 3 maquinas hacen como el crafter..."),
     * mismo mecanismo que {@code SublimadoraBlockEntity#empujarSalida}.
     * Se llama UNA vez, justo al terminar de teñir (no hay tick propio en
     * esta máquina) — si no hay dónde depositarla, se queda en
     * {@link #salida} para sacarla a mano o esperar el próximo intento.
     */
    /**
     * Línea de producción (2026-10-08, "un boton activador de la linea de produccion en la gui para que se los pueda
     * usar individuales por default sin que se pasen los items"): apagada de fábrica, la máquina no empuja su salida
     * al vecino ni saltea las prendas que le llegan por la cadena; la prenda queda para sacarla a mano.
     */
    private boolean linea;

    public boolean linea() { return linea; }

    public static final int BTN_LINEA = 900;

    /** Alterna la línea de producción y avisa al cliente. */
    public void alternarLinea() {
        linea = !linea;
        sincronizar();
    }

    private void empujarSalida() {
        if (salida.isEmpty() || world == null || !linea) return;
        Direction derecha = ladoDerecho();
        ItemStack sobrante = com.modamod.util.InventarioUtil.empujarA(
                world, pos.offset(derecha), derecha.getOpposite(), salida);
        if (sobrante.getCount() != salida.getCount()) {
            salida = sobrante;
        }
        sincronizar();
    }

    // La prenda entra por ARRIBA (2026-09-21, "que las tres carguen por
    // arriba") — reemplaza el costado izquierdo que usaba hasta ahora;
    // tinta sigue por atrás, sin cambios.
    @Override
    public int[] getAvailableSlots(Direction side) {
        // También por la IZQUIERDA (2026-10-04, "poder cargarles prendas por la izquierda").
        if (side == Direction.UP || side == ladoIzquierdo()) return new int[]{SLOT_PRENDA_ENTRADA};
        if (side == ladoAtras()) return new int[]{SLOT_TINTA_BASE, SLOT_TINTA_BASE + 1, SLOT_TINTA_BASE + 2, SLOT_TINTA_BASE + 3};
        return new int[0];
    }

    @Override
    public int topeEntrada(int slot) { return slot == SLOT_PRENDA_ENTRADA ? 1 : getMaxCountPerStack(); }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        if (dir != null && com.modamod.util.Redstone.pausada(this)) return false;   // con señal, nada entra por automatización
        if (SLOT_PRENDA_ENTRADA == slot && !getStack(slot).isEmpty()) return false;   // una prenda por vez

        return isValid(slot, stack);
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return false;
    }

    // ── pantalla ────────────────────────────────────────────────────

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.modamod.tinturas");
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new TinturasScreenHandler(syncId, inv, this);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
        return this.pos;
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        if (world == null || world.getBlockEntity(pos) != this) return false;
        return player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    private void sincronizar() {
        markDirty();
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    /**
     * Vista → autoridad para la tinta (2026-09-20) — mismo mecanismo que
     * {@code SublimadoraBlockEntity#markDirty}: si un hopper mutó
     * {@code tintaSlotView[]} directo en memoria (ver su javadoc), acá es
     * donde se entera {@code cargas[]}. Si nada externo lo tocó no cambia
     * nada (ya estaban iguales por {@link #sincronizarVistaTanques}).
     */
    @Override
    public void markDirty() {
        for (int i = 0; i < 4; i++) {
            int deVista = tintaSlotView[i].isEmpty() ? 0 : tintaSlotView[i].getCount();
            if (deVista != cargas[i]) cargas[i] = MathHelper.clamp(deVista, 0, CARGA_MAXIMA);
        }
        super.markDirty();
    }

    void sonarClick() {
        if (world != null) {
            world.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.BLOCKS, 0.6f, 1.0f);
        }
    }

    // ── persistencia y sincronización — mismo patrón que ModeladoBlockEntity/SublimadoraBlockEntity ──

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        com.modamod.util.DisenosMaquina.escribir(nbt, blancoDiseno, registries);
        nbt.putBoolean("linea", linea);
        net.minecraft.nbt.NbtList listaBolsa = new net.minecraft.nbt.NbtList();
        for (var e : bolsaPatrones.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putString("id", e.getKey().toString());
            c.putInt("n", e.getValue());
            listaBolsa.add(c);
        }
        nbt.put("bolsa_patrones", listaBolsa);
        net.minecraft.nbt.NbtList listaDisenos = new net.minecraft.nbt.NbtList();
        for (ItemStack d : disenosItem) listaDisenos.add(d.encode(registries));
        nbt.put("disenos_item", listaDisenos);
        for (var e : ultimoPorCategoria.entrySet()) nbt.put("ultimo_" + e.getKey().name(), e.getValue());
        net.minecraft.inventory.Inventories.writeNbt(nbt, items, registries);
        // Aparte, en su propio compuesto: Inventories escribe siempre en "Items".
        NbtCompound extra = new NbtCompound();
        net.minecraft.inventory.Inventories.writeNbt(extra, almacenExtra, registries);
        nbt.put("AlmacenExtra", extra);
        if (!prendaEntrada.isEmpty()) nbt.put("PrendaEntrada", prendaEntrada.encode(registries));
        if (!salida.isEmpty()) nbt.put("Salida", salida.encode(registries));
        nbt.putString("Estado", estado.name());
        nbt.putInt("Progreso", progreso);
        nbt.putIntArray("Cargas", cargas);
        nbt.putIntArray("ColorVial", colorVial);
        nbt.putIntArray("ColorVialPrevio", colorVialPrevio);
        nbt.putLongArray("TickCambioVial", tickCambioVial);
        nbt.putString("Categoria", categoria.name());
        nbt.putBoolean("SimetriaCalzado", simetriaCalzado);
        for (Categoria cat : Categoria.values()) {
            int c = cat.ordinal();
            nbt.put("CasillasV3_" + cat.name(), casillasANbt(casillas[c]));
            nbt.putIntArray("Orden_" + cat.name(), orden[c]);
            nbt.putInt("Seleccionada_" + cat.name(), seleccionada[c]);

            // Diseños guardados con nombre — "DisenosV3_": los de antes de
            // los cuadraditos (listas de fijadas) no tienen equivalente.
            NbtList disenos = new NbtList();
            for (DisenoGuardado d : disenosPorCategoria.get(cat)) {
                NbtCompound dc = new NbtCompound();
                dc.putString("Nombre", d.nombre());
                dc.put("Casillas", casillasANbt(d.casillas()));
                dc.putIntArray("Orden", d.orden());
                disenos.add(dc);
            }
            nbt.put("DisenosV3_" + cat.name(), disenos);
        }
    }

    private static NbtList casillasANbt(Casilla[] casillas) {
        NbtList lista = new NbtList();
        for (Casilla cas : casillas) lista.add(cas.aNbt());
        return lista;
    }

    /** Lee 12 cuadraditos; si faltan (NBT viejo o corto), quedan en blanco sin fijar. */
    private static Casilla[] casillasDeNbt(NbtList lista) {
        Casilla[] r = new Casilla[CASILLAS];
        for (int i = 0; i < CASILLAS; i++) r[i] = i < lista.size() ? Casilla.deNbt(lista.getCompound(i)) : new Casilla();
        return r;
    }

    /** Un orden leído tiene que ser una permutación de 0..11 — si no (NBT roto/viejo), vuelve al inicial. */
    private static int[] ordenValido(int[] leido, Categoria cat) {
        if (leido.length != CASILLAS) return ordenInicial(cat);
        boolean[] visto = new boolean[CASILLAS];
        for (int v : leido) {
            if (v < 0 || v >= CASILLAS || visto[v]) return ordenInicial(cat);
            visto[v] = true;
        }
        return leido.clone();
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        com.modamod.util.DisenosMaquina.leer(nbt, blancoDiseno, registries);
        linea = nbt.getBoolean("linea");
        bolsaPatrones.clear();
        net.minecraft.nbt.NbtList listaBolsa = nbt.getList("bolsa_patrones", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < listaBolsa.size(); i++) {
            NbtCompound e = listaBolsa.getCompound(i);
            Identifier id = Identifier.tryParse(e.getString("id"));
            if (id != null && e.getInt("n") > 0) bolsaPatrones.put(id, e.getInt("n"));
        }
        disenosItem.clear();
        net.minecraft.nbt.NbtList listaDisenos = nbt.getList("disenos_item", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < listaDisenos.size(); i++) {
            ItemStack d = ItemStack.fromNbtOrEmpty(registries, listaDisenos.getCompound(i));
            if (!d.isEmpty()) disenosItem.add(d);
        }
        ultimoPorCategoria.clear();
        for (Categoria cat : Categoria.values()) {
            if (nbt.contains("ultimo_" + cat.name())) ultimoPorCategoria.put(cat, nbt.getCompound("ultimo_" + cat.name()));
        }
        items.clear();
        net.minecraft.inventory.Inventories.readNbt(nbt, items, registries);
        almacenExtra.clear();
        net.minecraft.inventory.Inventories.readNbt(nbt.getCompound("AlmacenExtra"), almacenExtra, registries);
        prendaEntrada = nbt.contains("PrendaEntrada")
                ? ItemStack.fromNbtOrEmpty(registries, nbt.getCompound("PrendaEntrada")) : ItemStack.EMPTY;
        salida = nbt.contains("Salida")
                ? ItemStack.fromNbtOrEmpty(registries, nbt.getCompound("Salida")) : ItemStack.EMPTY;
        estado = nbt.contains("Estado") ? Estado.valueOf(nbt.getString("Estado")) : Estado.REPOSO;
        progreso = nbt.getInt("Progreso");
        if (nbt.contains("Cargas")) {
            int[] leidas = nbt.getIntArray("Cargas");
            for (int i = 0; i < 4 && i < leidas.length; i++) {
                cargas[i] = MathHelper.clamp(leidas[i], 0, CARGA_MAXIMA);
            }
        }
        sincronizarVistaTanques();
        if (nbt.contains("ColorVial")) {
            int[] leidos = nbt.getIntArray("ColorVial");
            for (int i = 0; i < 2 && i < leidos.length; i++) colorVial[i] = leidos[i];
        }
        if (nbt.contains("ColorVialPrevio")) {
            int[] leidos = nbt.getIntArray("ColorVialPrevio");
            for (int i = 0; i < 2 && i < leidos.length; i++) colorVialPrevio[i] = leidos[i];
        }
        if (nbt.contains("TickCambioVial")) {
            long[] leidos = nbt.getLongArray("TickCambioVial");
            for (int i = 0; i < 2 && i < leidos.length; i++) tickCambioVial[i] = leidos[i];
        }
        simetriaCalzado = !nbt.contains("SimetriaCalzado") || nbt.getBoolean("SimetriaCalzado");
        categoria = Categoria.REMERA;
        if (nbt.contains("Categoria")) {
            try {
                categoria = Categoria.valueOf(nbt.getString("Categoria"));
            } catch (IllegalArgumentException ignorado) { }
        }

        for (Categoria cat : Categoria.values()) {
            int c = cat.ordinal();
            Casilla[] leidas = casillasDeNbt(nbt.getList("CasillasV3_" + cat.name(), NbtElement.COMPOUND_TYPE));
            System.arraycopy(leidas, 0, casillas[c], 0, CASILLAS);
            orden[c] = ordenValido(nbt.getIntArray("Orden_" + cat.name()), cat);
            int sel = nbt.contains("Seleccionada_" + cat.name()) ? nbt.getInt("Seleccionada_" + cat.name()) : -1;
            seleccionada[c] = regionDe(cat, sel) != null ? sel : primeraUsable(cat);

            List<DisenoGuardado> destino = disenosPorCategoria.get(cat);
            destino.clear();
            NbtList disenosNbt = nbt.getList("DisenosV3_" + cat.name(), NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < disenosNbt.size() && i < DISENOS_MAXIMO; i++) {
                NbtCompound dc = disenosNbt.getCompound(i);
                destino.add(new DisenoGuardado(dc.getString("Nombre"),
                        casillasDeNbt(dc.getList("Casillas", NbtElement.COMPOUND_TYPE)),
                        ordenValido(dc.getIntArray("Orden"), cat)));
            }
        }
    }

    /**
     * Solo el TANQUE viaja adentro del ítem al romper el bloque (mismo
     * mecanismo que la tinta de la Sublimadora) — el resto (borrador,
     * fijadas, almacén) se resetea, igual que la config de la Modeladora
     * no viaja con el bloque roto.
     */
    @Override
    protected void addComponents(ComponentMap.Builder builder) {
        super.addComponents(builder);
        List<Integer> lista = new ArrayList<>(4);
        for (int c : cargas) lista.add(c);
        builder.add(TinturasMod.CARGAS, lista);
    }

    @Override
    protected void readComponents(BlockEntity.ComponentsAccess componentes) {
        super.readComponents(componentes);
        List<Integer> lista = componentes.get(TinturasMod.CARGAS);
        if (lista == null) return;
        for (int i = 0; i < 4 && i < lista.size(); i++) {
            cargas[i] = MathHelper.clamp(lista.get(i), 0, CARGA_MAXIMA);
        }
        sincronizarVistaTanques();
    }

    @Override
    public void removeFromCopiedStackNbt(NbtCompound nbt) {
        super.removeFromCopiedStackNbt(nbt);
        nbt.remove("Cargas");
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    @Override
    public net.minecraft.network.packet.Packet<net.minecraft.network.listener.ClientPlayPacketListener> toUpdatePacket() {
        return net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket.create(this);
    }
}
