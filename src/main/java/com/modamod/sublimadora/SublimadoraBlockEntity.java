package com.modamod.sublimadora;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Block entity animado con GeckoLib 4.x.
 *
 * Dos cosas animadas: la tapa (controlador "tapa", lee la propiedad OPEN) y
 * las cuatro barras del display CMYK, que no son una animacion keyframeada
 * sino escala de hueso aplicada en SublimadoraGeoModel#setCustomAnimations.
 * Aca solo vive el dato: cuanta tinta queda, y su suavizado por tick.
 */
public class SublimadoraBlockEntity extends BlockEntity
        implements GeoBlockEntity, ExtendedScreenHandlerFactory<BlockPos>, SidedInventory, com.modamod.util.ConSalida,
        com.modamod.util.MaquinaCreativa.Cargable , com.modamod.util.LimiteEntrada {

    public static final int C = 0, M = 1, Y = 2, K = 3;

    // 64 y no 16 (2026-09-18, "subime la capacidad de tinta") — un stack
    // entero de tinte llena el tanque justo.
    /** Cuantas cargas entran por color. Una carga = un tinte. */
    public static final int CARGA_MAXIMA = 64;
    /** 15 segundos a 20 ticks — a pedido (2026-09-21, "que cada maquina tome su tiempo... 15 la sublimadora"). */
    public static final int TICKS_PRENSADO = 300;
    /** Lo que tarda la tapa en bajar: 0.3 s del archivo de animacion. */
    private static final int TICKS_CIERRE = 6;

    public enum Estado { REPOSO, PRENSANDO, LISTO }

    private static final String[] CLAVES = { "TintaC", "TintaM", "TintaY", "TintaK" };

    // Nombres "sublimator" y no "sublimadora" — modelo nuevo (2026-09-19,
    // ver SublimadoraGeoModel), mismo hueso "lid" que el viejo llamaba tapa.
    private static final RawAnimation ABRIR = RawAnimation.begin()
            .thenPlay("animation.sublimator.abrir")
            .thenLoop("animation.sublimator.abierta");
    private static final RawAnimation CERRAR = RawAnimation.begin()
            .thenPlay("animation.sublimator.cerrar")
            .thenLoop("animation.sublimator.cerrada");

    // Poses estaticas, sin transicion: son las que hay que usar al cargar el
    // chunk. ABRIR y CERRAR arrancan desde la pose CONTRARIA, asi que usarlas
    // al cargar hacia que un bloque cerrado apareciera abierto y se cerrara
    // solo delante del jugador.
    private static final RawAnimation ABIERTA = RawAnimation.begin()
            .thenLoop("animation.sublimator.abierta");
    private static final RawAnimation CERRADA = RawAnimation.begin()
            .thenLoop("animation.sublimator.cerrada");

    /** Ventilador de atras — gira SOLO mientras prensa (hueso "fan", 2026-09-19). */
    private static final RawAnimation EN_MARCHA = RawAnimation.begin()
            .thenLoop("animation.sublimator.en_marcha");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean ultimoEstado = false;
    /**
     * Que animacion pedirle al controlador. Tiene que ser ESTABLE entre
     * cambios de estado: si cada frame se pide la transicion, GeckoLib la
     * reinicia y la tapa se queda reproduciendo "cerrar" para siempre, que
     * arranca en -104 grados. O sea, abierta.
     */
    private RawAnimation actual = null;

    /** tinta restante 0..1 por canal (valor real, objetivo del suavizado) */
    private final float[] tinta = { 0.85f, 0.60f, 0.35f, 0.70f };
    /** valor mostrado y valor del tick anterior, para interpolar el llenado */
    private final float[] mostrado = tinta.clone();
    private final float[] anterior = tinta.clone();

    /** Cargas 0..16 por canal. El nivel de la barra sale de aca. */
    private final int[] cargas = new int[4];
    /**
     * Tanque de papel — a pedido (2026-09-19, "usa papel y ya no consume la
     * imagen"): la foto queda cargada para reimprimir, y esto es lo que de
     * verdad se gasta por cada prensado (1 papel). Mismo tope que la tinta.
     */
    private int papelCargado = 0;

    /**
     * "Vista" de {@code papelCargado}/{@code cargas[]} como ItemStack real,
     * para el Inventory de hopper (2026-09-20) — bug real jugando: "el item
     * se vacia del hopper pero el indicador nunca sube". Causa: un hopper
     * vanilla, cuando el slot destino YA tiene algo, no vuelve a llamar
     * {@code setStack} — agarra el ItemStack que le devuelve {@code getStack},
     * lo incrementa EN MEMORIA, y listo llama {@code markDirty()} (ver
     * {@code HopperBlockEntity#transfer}). Devolver un ItemStack nuevo y
     * descartable en cada {@code getStack} (como hacía antes) hace que ese
     * incremento se pierda en el aire — nunca vuelve a nuestro lado. Estos
     * dos campos son la MISMA instancia entre llamadas (identidad estable),
     * así que la mutación del hopper cae adentro de un objeto real: se
     * "empuja" (autoridad→vista) cada vez que ESTE código cambia
     * papelCargado/cargas[], y se "tira" de vuelta (vista→autoridad) en
     * {@link #markDirty()} — así se captura tanto nuestros cambios propios
     * como los que un hopper haga directo sobre el objeto.
     */
    private ItemStack papelSlotView = ItemStack.EMPTY;
    private final ItemStack[] tintaSlotView = { ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY };

    /** Autoridad → vista: llamar después de cualquier cambio directo a papelCargado/cargas[]. */
    private void sincronizarVistaTanques() {
        papelSlotView = papelCargado <= 0 ? ItemStack.EMPTY : new ItemStack(net.minecraft.item.Items.PAPER, papelCargado);
        for (int i = 0; i < 4; i++) {
            tintaSlotView[i] = cargas[i] <= 0 ? ItemStack.EMPTY : new ItemStack(SublimadoraBlock.TINTES[i], cargas[i]);
        }
    }

    private Estado estado = Estado.REPOSO;
    private int progreso = 0;
    /** Cuantos ticks antes de terminar sale el poof de descarga. */
    private static final int TICKS_DESCARGA = 8;
    private ItemStack remera = ItemStack.EMPTY;
    private ItemStack salida = ItemStack.EMPTY;

    // Una foto y un ajuste de escala/posicion POR CARA: la maquina estampa
    // el frente y la espalda en la misma pasada. Todo esto va indexado por
    // Cara.ordinal().
    private final ItemStack[] fotos = { ItemStack.EMPTY, ItemStack.EMPTY };
    private final java.util.UUID[] pendientes = new java.util.UUID[2];
    /**
     * Escala/posicion libres, POR CARA — a pedido (2026-09-19, "hace gui
     * con preview con escalado y flechitas para posicion... sacamos los
     * controles del frente"): reemplaza los 3 presets viejos (Logo/
     * Centrada/Completo) por control fino de verdad, como ya preveia el
     * comentario de {@link Estampa.Modo} ("agregar control fino despues
     * no rompe nada de esto").
     */
    private final float[] escalaBorrador = { Estampa.ESCALA_DEFECTO, Estampa.ESCALA_DEFECTO };
    private final float[] xBorrador = { 0f, 0f };
    private final float[] yBorrador = { 0f, 0f };
    /** Rotación libre, POR CARA — a pedido (2026-09-20, "posibilidad de rotarla"). */
    private final float[] anguloBorrador = { 0f, 0f };
    private static final float PASO_ESCALA = 0.05f;
    private static final float PASO_POSICION = 0.05f;
    private static final float PASO_ANGULO = 15f;
    /** Que cara esta configurando la pantalla ahora mismo. */
    private Estampa.Cara seleccion = Estampa.Cara.FRENTE;
    /**
     * Chincheta por cara (2026-09-28, "cambiemos la gui de la sublimadora
     * para hacerla sintonizar con sus bloques hermanos"): mismo criterio
     * que los cuadraditos de Tintes — solo se estampan las caras FIJADAS
     * que tengan foto; la vista previa muestra las fijadas más la que se
     * está editando.
     */
    private final boolean[] caraFijada = { false, false };
    /**
     * Simetría lateral (2026-09-28, "a la sublimadora hay que agregarle
     * simetria lateral para medias y cubrebrazos"): el lado izquierdo lleva
     * el espejo del derecho. Solo cuenta en prendas de a pares, ver
     * {@link #admiteSimetria}.
     */
    private boolean simetria = false;

    /**
     * "Save por cada prenda" (a pedido, 2026-09-19): una combinación
     * escala/x/y de las DOS caras, guardada — la lista es POR ÍTEM (todos
     * los cortes de remera comparten lista, igual que categoriza
     * {@code TinturasBlockEntity.Categoria.REMERA}; medias/pantalón/
     * calientabrazos tienen la suya propia, un solo ítem cada una).
     */
    public record EstampaFijada(float escalaFrente, float xFrente, float yFrente, float anguloFrente,
                                 float escalaEspalda, float xEspalda, float yEspalda, float anguloEspalda) {}

    /**
     * Un diseño guardado con nombre (2026-09-28, mismo sistema que
     * Modeladora/Tintes: "Guardar diseño" + 8 casilleros, click carga,
     * click derecho borra) — reemplaza a las "fijadas" sin nombre, que se
     * migran como diseños "#n" al leer un mundo viejo.
     */
    public record DisenoEstampa(String nombre, EstampaFijada ajuste, boolean fijadaFrente, boolean fijadaEspalda,
                                boolean simetria) {}

    /** Medias y calientabrazos: las prendas de a pares que tienen simetría lateral. */
    public static boolean admiteSimetria(net.minecraft.item.Item item) {
        return item == com.modamod.item.ModamodItems.SOCKS_SOLID
                || item == com.modamod.item.ModamodItems.CALIENTABRAZOS;
    }

    public boolean simetria() { return simetria; }

    public static final int DISENOS_MAXIMO = 8;

    /**
     * Máscaras de sublimación (2026-10-02, "Quiero agregar un mecanismo a la
     * sublimadora mascaras... cuadrado franja circulo estrella triangulo y que
     * se pueda guardar varias layers 12 quizas entre frente y atras y que se
     * fije con la imagen, controles de posicion angulo tamaño tanto para la
     * imagen dentro de la mascara como para la mascara dentro de la prenda"):
     * con un {@link MoldeMascaraItem} en su slot, la chincheta de una cara ya
     * no fija la estampa entera sino que agrega una {@link CapaEstampa} (la
     * foto adentro de la máscara) a {@link #capas}; Prensar pone esa lista en
     * la prenda. La foto queda en su slot para fijar otra capa con ella.
     */
    private ItemStack mascaraSlot = ItemStack.EMPTY;
    /** Ajuste de la máscara sobre la prenda, por cara (el de la foto es el de siempre). */
    private final float[] mascaraEscala = { Mascara.ESCALA_DEFECTO, Mascara.ESCALA_DEFECTO };
    private final float[] mascaraX = { 0f, 0f };
    private final float[] mascaraY = { 0f, 0f };
    private final float[] mascaraAngulo = { 0f, 0f };
    /** true = los controles mueven la máscara; false = la foto adentro de ella. */
    private boolean editarMascara = false;
    private final java.util.List<CapaEstampa> capas = new java.util.ArrayList<>();
    /** La capa elegida en la lista (−1 = ninguna), para subirla, bajarla o borrarla. */
    private int capaElegida = -1;
    private final java.util.Map<net.minecraft.item.Item, java.util.List<DisenoEstampa>> disenosPorItem = new java.util.HashMap<>();

    /**
     * Qué "categoría" (en realidad el ÍTEM representativo — acá cada
     * categoría es un solo ítem, ver {@link ModItems#esEstampable})
     * muestra/edita la fila de fijadas ahora mismo — a pedido (2026-09-21,
     * "le falta el boton de ciclado de settings de las distintas
     * prendas en la gui que guarde el seteo"): antes {@link #fijadas()}
     * necesitaba una remera FÍSICA puesta para mostrar algo (devolvía
     * vacío si no), así que no había forma de ver/guardar el seteo de
     * "pantalón" sin tener un pantalón en la mano en ese momento. Ahora
     * sigue lo cargado automáticamente ({@link #ponerRemera}) pero
     * también se puede cambiar a mano con {@link #BTN_CATEGORIA}.
     */
    private static final net.minecraft.item.Item[] CATEGORIAS = {
            ModItems.REMERA,
            com.modamod.item.ModamodItems.SOCKS_SOLID,
            com.modamod.item.ModamodItems.PANTALON,
            com.modamod.item.ModamodItems.CALIENTABRAZOS,
            com.modamod.item.ModamodItems.POLLERA,
            com.modamod.item.ModamodItems.CAPA,
            com.modamod.item.ModamodItems.DELANTAL,
            net.minecraft.item.Items.WHITE_BANNER,      // todos los banners
            com.modamod.item.ModamodItems.ANTIFAZ,      // máscaras y barbijo (2026-10-24)
            com.modamod.item.ModamodItems.MEDIA_MASCARA,
            com.modamod.item.ModamodItems.MASCARA_COMPLETA,
            com.modamod.item.ModamodItems.BARBIJO,
            com.modamod.item.ModamodItems.SOMBRERO_BRUJA, // gorras con visera (2026-10-25)
    };
    private net.minecraft.item.Item categoria = ModItems.REMERA;

    /**
     * Un borrador por categoría (2026-10-14, "que cada categoria almacene sus propias fotos asi la sublimadora en linea
     * pueda procesar diseños diferentes en distintas prendas"): los campos de arriba son los de la categoría ACTIVA; los
     * de las demás esperan acá y se intercambian en {@link #irACategoria}. El banner y el delantal tienen el suyo
     * (a pedido, no comparten con la remera).
     */
    private static final class Juego {
        final ItemStack[] fotos = { ItemStack.EMPTY, ItemStack.EMPTY };
        final java.util.UUID[] pendientes = new java.util.UUID[2];
        final float[] escala = { Estampa.ESCALA_DEFECTO, Estampa.ESCALA_DEFECTO }, x = new float[2], y = new float[2], angulo = new float[2];
        final boolean[] caraFijada = new boolean[2];
        boolean simetria;
        ItemStack mascara = ItemStack.EMPTY;
        final float[] mascaraEscala = { Mascara.ESCALA_DEFECTO, Mascara.ESCALA_DEFECTO }, mascaraX = new float[2], mascaraY = new float[2], mascaraAngulo = new float[2];
        boolean editarMascara;
    }
    private final java.util.Map<net.minecraft.item.Item, Juego> juegos = new java.util.HashMap<>();

    /** La categoría que le toca a una prenda: toda remera (y chaqueta) comparte; el banner, el delantal y el resto tienen la suya. */
    public static net.minecraft.item.Item categoriaDe(ItemStack stack) {
        net.minecraft.item.Item i = stack.getItem();
        if (i instanceof RemeraItem) return ModItems.REMERA;
        if (i instanceof net.minecraft.item.BannerItem) return net.minecraft.item.Items.WHITE_BANNER;
        return i;
    }

    private void irACategoria(net.minecraft.item.Item nueva) {
        if (nueva == categoria || estado == Estado.PRENSANDO) return;
        Juego v = new Juego();
        for (int i = 0; i < 2; i++) {
            v.fotos[i] = fotos[i]; v.pendientes[i] = pendientes[i];
            v.escala[i] = escalaBorrador[i]; v.x[i] = xBorrador[i]; v.y[i] = yBorrador[i]; v.angulo[i] = anguloBorrador[i];
            v.caraFijada[i] = caraFijada[i];
            v.mascaraEscala[i] = mascaraEscala[i]; v.mascaraX[i] = mascaraX[i]; v.mascaraY[i] = mascaraY[i]; v.mascaraAngulo[i] = mascaraAngulo[i];
        }
        v.simetria = simetria; v.mascara = mascaraSlot; v.editarMascara = editarMascara;
        juegos.put(categoria, v);
        Juego n = juegos.remove(nueva);
        if (n == null) n = new Juego();
        for (int i = 0; i < 2; i++) {
            fotos[i] = n.fotos[i]; pendientes[i] = n.pendientes[i];
            escalaBorrador[i] = n.escala[i]; xBorrador[i] = n.x[i]; yBorrador[i] = n.y[i]; anguloBorrador[i] = n.angulo[i];
            caraFijada[i] = n.caraFijada[i];
            mascaraEscala[i] = n.mascaraEscala[i]; mascaraX[i] = n.mascaraX[i]; mascaraY[i] = n.mascaraY[i]; mascaraAngulo[i] = n.mascaraAngulo[i];
        }
        simetria = n.simetria; mascaraSlot = n.mascara; editarMascara = n.editarMascara;
        categoria = nueva;
    }

    private net.minecraft.nbt.NbtList juegosANbt(net.minecraft.registry.RegistryWrapper.WrapperLookup registries) {
        net.minecraft.nbt.NbtList l = new net.minecraft.nbt.NbtList();
        for (var e : juegos.entrySet()) {
            Juego j = e.getValue();
            NbtCompound c = new NbtCompound();
            c.putString("Item", net.minecraft.registry.Registries.ITEM.getId(e.getKey()).toString());
            for (int i = 0; i < 2; i++) {
                if (!j.fotos[i].isEmpty()) c.put("Foto" + i, j.fotos[i].encode(registries));
                if (j.pendientes[i] != null) c.putUuid("Pendiente" + i, j.pendientes[i]);
                c.putFloat("Escala" + i, j.escala[i]); c.putFloat("X" + i, j.x[i]); c.putFloat("Y" + i, j.y[i]); c.putFloat("Angulo" + i, j.angulo[i]);
                c.putBoolean("CaraFijada" + i, j.caraFijada[i]);
                c.putFloat("MascaraEscala" + i, j.mascaraEscala[i]); c.putFloat("MascaraX" + i, j.mascaraX[i]);
                c.putFloat("MascaraY" + i, j.mascaraY[i]); c.putFloat("MascaraAngulo" + i, j.mascaraAngulo[i]);
            }
            c.putBoolean("Simetria", j.simetria);
            if (!j.mascara.isEmpty()) c.put("MascaraItem", j.mascara.encode(registries));
            c.putBoolean("EditarMascara", j.editarMascara);
            l.add(c);
        }
        return l;
    }

    private void juegosDeNbt(NbtCompound nbt, net.minecraft.registry.RegistryWrapper.WrapperLookup registries) {
        juegos.clear();
        net.minecraft.nbt.NbtList l = nbt.getList("Juegos", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
        for (int k = 0; k < l.size(); k++) {
            NbtCompound c = l.getCompound(k);
            net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(c.getString("Item"));
            net.minecraft.item.Item item = id == null ? null : net.minecraft.registry.Registries.ITEM.get(id);
            if (item == null || item == net.minecraft.item.Items.AIR) continue;
            Juego j = new Juego();
            for (int i = 0; i < 2; i++) {
                j.fotos[i] = c.contains("Foto" + i) ? ItemStack.fromNbtOrEmpty(registries, c.getCompound("Foto" + i)) : ItemStack.EMPTY;
                j.pendientes[i] = c.containsUuid("Pendiente" + i) ? c.getUuid("Pendiente" + i) : null;
                j.escala[i] = c.getFloat("Escala" + i); j.x[i] = c.getFloat("X" + i); j.y[i] = c.getFloat("Y" + i); j.angulo[i] = c.getFloat("Angulo" + i);
                j.caraFijada[i] = c.getBoolean("CaraFijada" + i);
                j.mascaraEscala[i] = c.getFloat("MascaraEscala" + i); j.mascaraX[i] = c.getFloat("MascaraX" + i);
                j.mascaraY[i] = c.getFloat("MascaraY" + i); j.mascaraAngulo[i] = c.getFloat("MascaraAngulo" + i);
            }
            j.simetria = c.getBoolean("Simetria");
            j.mascara = c.contains("MascaraItem") ? ItemStack.fromNbtOrEmpty(registries, c.getCompound("MascaraItem")) : ItemStack.EMPTY;
            j.editarMascara = c.getBoolean("EditarMascara");
            juegos.put(item, j);
        }
    }

    // Solo para dibujar, no se guardan: hacen que la foto siga a la vista
    // mientras la tapa baja, en vez de evaporarse antes de que la cubra.
    private int arrastre = 0;
    private final java.util.UUID[] ultimasVistas = new java.util.UUID[2];

    public SublimadoraBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SUBLIMADORA_ENTITY, pos, state);
    }

    // ── animacion de la tapa ─────────────────────────────────────────
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "tapa", 0, state -> {
            boolean abierta = getCachedState().get(SublimadoraBlock.OPEN);
            if (actual == null) {
                // Al cargar el chunk: la POSE, no la transicion.
                ultimoEstado = abierta;
                actual = abierta ? ABIERTA : CERRADA;
            } else if (abierta != ultimoEstado) {
                // Cambio real: recien ahi la transicion, que ya termina
                // encadenando la pose por su thenLoop.
                ultimoEstado = abierta;
                actual = abierta ? ABRIR : CERRAR;
                state.getController().forceAnimationReset();
            }
            return state.setAndContinue(actual);
        })
        .triggerableAnim("abrir", ABRIR)
        .triggerableAnim("cerrar", CERRAR));

        // Ventilador de atras: gira SOLO mientras prensa, quieto el resto
        // del tiempo (sin animacion pedida, GeckoLib deja el hueso en su
        // pose de reposo del modelo) — a pedido (2026-09-19, "fan es el
        // ventilador de atras").
        controllers.add(new AnimationController<>(this, "ventilador", 0, state ->
                estado == Estado.PRENSANDO ? state.setAndContinue(EN_MARCHA) : PlayState.STOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ── tinta ────────────────────────────────────────────────────────

    /** Ticker (cliente y server): suaviza la barra hacia su valor real. */
    public static void tick(net.minecraft.world.World world, BlockPos pos, BlockState state, SublimadoraBlockEntity be) {
        if (!world.isClient && world.getTime() % 20 == 5) be.drenarAlmacen();
        for (int i = 0; i < 4; i++) {
            be.anterior[i] = be.mostrado[i];
            be.mostrado[i] += (be.tinta[i] - be.mostrado[i]) * 0.15f;
            if (Math.abs(be.tinta[i] - be.mostrado[i]) < 0.001f) be.mostrado[i] = be.tinta[i];
        }

        // Arrastre de la foto. Va antes del corte por lado porque es puro
        // dibujo y lo necesita el cliente.
        //
        // El contador se RECARGA mientras haya foto, en vez de arrancar
        // cuando se cierra la tapa. Al cerrar viajan dos cosas por separado
        // -el cambio de estado del bloque y el NBT del block entity- y no
        // llegan juntas: si el NBT sin foto llegaba primero, la foto
        // desaparecia, y un tick despues el cambio de tapa arrancaba el
        // arrastre y la hacia volver. Ese ida y vuelta era el parpadeo.
        // Recargando, el arrastre ya esta lleno pase lo que pase.
        boolean hayAlguna = false;
        for (Estampa.Cara cara : Estampa.Cara.values()) {
            java.util.UUID id = be.getFotoCargada(cara);
            if (id != null) {
                be.ultimasVistas[cara.ordinal()] = id;
                hayAlguna = true;
            }
        }
        if (hayAlguna) be.arrastre = TICKS_CIERRE;
        else if (be.arrastre > 0) be.arrastre--;

        // El avance del prensado lo lleva el SERVIDOR; el cliente solo dibuja
        // lo que le sincroniza el NBT.
        if (world.isClient) return;
        // Con señal de redstone la máquina se detiene del todo (2026-10-05).
        if (com.modamod.util.Redstone.pausada(world, pos)) return;

        // Luz del LED (2026-09-29, "hace que las luces de las maquinas iluminen").
        com.modamod.util.LuzMaquina.actualizar(world, pos, state,
                be.estado == Estado.PRENSANDO || be.estado == Estado.LISTO);

        if (be.estado == Estado.LISTO) {
            be.empujarSalida(world, pos);
        }

        if (be.estado != Estado.PRENSANDO) return;

        be.progreso++;

        // Sonido y vapor salen del servidor con spawnParticles y
        // playSound(null, ...): asi los ve y los oye cualquiera que este
        // cerca, no solo el que cerro la tapa.
        if (world instanceof ServerWorld servidor) {
            if (be.progreso == 1) {
                // El golpe de la plancha al apoyar.
                be.sonar(SoundEvents.BLOCK_PISTON_CONTRACT, 0.9f, 0.7f);
                vapor(servidor, pos, 14, 0.12);
            }
            if (be.progreso % 5 == 0) vapor(servidor, pos, 2, 0.12);

            // El poof de descarga sale ANTES de terminar, no al terminar. Asi
            // la secuencia queda: poof, y ocho ticks despues la luz verde y la
            // campanita juntas, que es como se lee que la maquina termino.
            if (be.progreso == TICKS_PRENSADO - TICKS_DESCARGA) {
                vapor(servidor, pos, 22, 0.16);
                be.sonar(SoundEvents.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.3f);
            }

            // Siseo continuo. El fizz dura casi un segundo, asi que
            // repitiendolo cada 14 ticks los coletazos se solapan y suena como
            // una sola perdida de vapor sostenida en vez de golpes sueltos.
            if (be.progreso % 14 == 0) be.sonar(SoundEvents.BLOCK_LAVA_EXTINGUISH, 0.3f, 1.6f);

            // Un pitido por cada encendido del LED rojo. El LED lo prende
            // SublimadoraGeoModel con (tiempo % 20) < 10 leyendo el reloj del
            // mundo, que es el mismo de los dos lados: mirando ese mismo reloj
            // aca, el pitido cae exactamente en el flanco de encendido.
            if (world.getTime() % 20 == 0) be.sonar(SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), 0.25f, 2.0f);
        }

        if (be.progreso >= com.modamod.util.MaquinaCreativa.duracion(be, TICKS_PRENSADO)) {
            be.progreso = 0;
            be.estado = Estado.LISTO;
            ItemStack hecha = be.remera;
            // pendientes[] NO se borra al terminar (2026-09-19, "ya no
            // consume la imagen") — la foto queda lista para reimprimir en
            // la proxima remera sin volver a cargarla.
            for (Estampa.Cara cara : Estampa.Cara.values()) {
                java.util.UUID id = be.pendientes[cara.ordinal()];
                if (id == null || !be.caraActiva(cara.ordinal())) continue;
                int ci = cara.ordinal();
                // "Cubrir" (full print, recorta en vez de encoger) se
                // deriva de la escala en vez de ser un toggle aparte: a
                // ESCALA_CUBRIR (100%) es exactamente lo que hacia el viejo
                // preset COMPLETO — por ENCIMA de eso ya es sobre-escala
                // (ver Estampa.ESCALA_MAXIMA), sigue siendo "cubrir".
                boolean cubrir = be.escalaBorrador[ci] >= Estampa.ESCALA_CUBRIR - 0.001f;
                hecha = RemeraItem.estampar(hecha, cara,
                        new Estampa(id, be.escalaBorrador[ci], be.xBorrador[ci], be.yBorrador[ci],
                                be.anguloBorrador[ci], cubrir, be.simetria && admiteSimetria(hecha.getItem())));
            }
            // Las capas con máscara de la máquina reemplazan las de la prenda
            // (la lista arrancó desde las de la prenda al fijar la primera).
            if (!be.capas.isEmpty()) hecha = RemeraItem.conCapas(hecha, sumarCapas(RemeraItem.capasDe(hecha), be.capas));
            be.salida = hecha;
            be.remera = ItemStack.EMPTY;
            // Esto es exactamente el tick en que se prende el LED verde, que
            // sale de estado == LISTO. La campanita va aca y no agendada
            // aparte: atada al mismo cambio de estado no se pueden desfasar.
            be.sonar(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.0f, 1.5f);
            be.sonar(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
            be.sincronizar();
        } else if (be.progreso % 20 == 0) {
            // Una sincronizacion por segundo alcanza para el parpadeo y la
            // barra; no hace falta mandar un paquete por tick.
            be.sincronizar();
        }
    }

    /**
     * Vaporcito por la junta entre la base y la tapa.
     *
     * Usa WHITE_SMOKE y no CLOUD, por dos razones que solo se ven mirando el
     * codigo de las particulas:
     *
     *   - PlayerCloudParticle busca al jugador mas cercano dentro de 2 bloques
     *     y, si lo encuentra, arrastra la particula hacia la altura de sus
     *     PIES un 20% por tick. Como para usar la maquina hay que estar al
     *     lado, el vapor se venia para abajo por mas velocidad hacia arriba
     *     que se le pusiera. Eso era lo que quedaba raro.
     *   - WhiteSmokeParticle tiene gravedad -0.1, o sea flotabilidad: sube
     *     sola, despacio y para siempre. Justo lo que queriamos, y gratis.
     *
     * Entonces la velocidad que le damos es casi toda lateral: sale disparada
     * al costado, la friccion de 0.96 la frena en un bloque, y de ahi la
     * flotabilidad la levanta sola.
     *
     * Las particulas colisionan (hasPhysics), asi que nacen APENAS AFUERA de
     * la cara del bloque y no en un circulo: un circulo de radio 0.48 cae
     * entero adentro del cubo, y las que nacian trabadas salian para
     * cualquier lado al ser expulsadas.
     */
    private static void vapor(ServerWorld world, BlockPos pos, int cantidad, double lateral) {
        double cy = pos.getY() + 11.0 / 16.0;
        for (int i = 0; i < cantidad; i++) {
            int lado = world.getRandom().nextInt(4);
            double corrida = (world.getRandom().nextDouble() - 0.5) * 0.9;
            double afuera = 0.62;
            double dx, dz, vx, vz;
            switch (lado) {
                case 0  -> { dx = corrida; dz = -afuera; vx = 0;        vz = -lateral; }
                case 1  -> { dx = corrida; dz =  afuera; vx = 0;        vz =  lateral; }
                case 2  -> { dx = -afuera; dz = corrida; vx = -lateral; vz = 0; }
                default -> { dx =  afuera; dz = corrida; vx =  lateral; vz = 0; }
            }
            // count = 0 NO significa "ninguna particula": cambia que son los
            // tres deltas. Con count > 0 sale una por cada count, los deltas
            // son dispersion de posicion y la velocidad es azar gaussiano por
            // "speed" en las tres direcciones. Con count = 0 sale una sola y
            // los deltas son su velocidad, que es lo que queremos.
            world.spawnParticles(ParticleTypes.WHITE_SMOKE,
                    pos.getX() + 0.5 + dx,
                    cy + (world.getRandom().nextDouble() - 0.5) * 0.12,
                    pos.getZ() + 0.5 + dz,
                    0,
                    vx, 0.015, vz,
                    1.0);
        }
    }

    private void sonar(SoundEvent evento, float volumen, float tono) {
        if (world != null) world.playSound(null, pos, evento, SoundCategory.BLOCKS, volumen, tono);
    }

    // ── ciclo de prensado ────────────────────────────────────────────

    public Estado getEstado() {
        return estado;
    }

    /** Trabajando de verdad: la GUI se cierra y no se toca (2026-10-08). */
    public boolean trabajando() { return estado == Estado.PRENSANDO; }

    /** 0..1, para animar. */
    public float getProgreso() {
        return estado == Estado.PRENSANDO ? progreso / (float) TICKS_PRENSADO : 0f;
    }

    public ItemStack getRemera() {
        return remera;
    }

    public ItemStack getSalida() {
        return salida;
    }

    /**
     * La remera de {@link #remera} con la estampa del borrador YA
     * aplicada (escala/posición/ángulo de las dos caras, en vivo) — a
     * pedido (2026-09-21, "que muestre el preview del setting de la
     * ultima prenda seteada"). Antes esto vivía SOLO adentro de
     * {@code SublimadoraScreen#dibujarPreview}; ahora también lo usa
     * {@link com.modamod.sublimadora.SublimadoraGeoModel} para la
     * pantallita del bloque, así que se comparte acá — mismo criterio
     * de nombre que {@code TinturasBlockEntity#prendaDeVistaPrevia}.
     */
    public ItemStack prendaDeVistaPrevia() { return prendaDeVistaPrevia(false); }

    /**
     * {@code representativa}=true (la GUI, 2026-09-28): sin prenda cargada
     * ni lista, muestra la de la categoría elegida con el borrador encima —
     * mismo criterio que {@code TinturasBlockEntity#prendaDeVistaPrevia}.
     * La pantallita del bloque sigue pidiendo solo prendas de verdad.
     */
    public ItemStack prendaDeVistaPrevia(boolean representativa) {
        ItemStack base = !remera.isEmpty() ? remera
                : representativa && salida.isEmpty() ? new ItemStack(categoria) : ItemStack.EMPTY;
        if (!base.isEmpty()) {
            ItemStack copia = base.copy();
            for (Estampa.Cara cara : Estampa.Cara.values()) {
                java.util.UUID id = getFotoCargada(cara);
                if (id == null) continue;
                // Solo lo fijado más la cara que se está editando — lo que va a salir.
                if (!caraFijada[cara.ordinal()] && cara != seleccion) continue;
                // Con máscara, la cara que se edita se ve recortada (abajo), no entera.
                if (!caraFijada[cara.ordinal()] && hayMascara()) continue;
                boolean cubrir = getEscala(cara) >= Estampa.ESCALA_CUBRIR - 0.001f;
                copia = RemeraItem.estampar(copia, cara,
                        new Estampa(id, getEscala(cara), getX(cara), getY(cara), getAngulo(cara), cubrir,
                                simetria && admiteSimetria(copia.getItem())));
            }
            java.util.List<CapaEstampa> vista = sumarCapas(RemeraItem.capasDe(copia), capas);
            CapaEstampa enCurso = capaEnCurso(seleccion);
            if (enCurso != null && vista.size() < CapaEstampa.MAXIMO) vista.add(enCurso);
            return RemeraItem.conCapas(copia, vista);
        }
        return salida;
    }

    /**
     * La ÚLTIMA vista previa no vacía — mismo criterio que
     * {@code ModeladoBlockEntity#vistaPreviaPersistente} (2026-09-21,
     * "quiero que las 3 muestren la ultima prenda con preview del
     * ultimo seteado"): la pantallita del bloque usa esto en vez de
     * {@link #prendaDeVistaPrevia} directo, para seguir mostrando la
     * última estampa aunque ya se haya retirado la remera.
     */
    private ItemStack ultimaVistaPrevia = ItemStack.EMPTY;

    public ItemStack vistaPreviaPersistente() {
        ItemStack actual = prendaDeVistaPrevia();
        if (!actual.isEmpty()) ultimaVistaPrevia = actual;
        return ultimaVistaPrevia;
    }

    /** True si hay al menos una carga de cada color. */
    public boolean hayTinta() {
        if (com.modamod.util.MaquinaCreativa.es(this)) return true;
        for (int i = 0; i < 4; i++) if (cargas[i] <= 0) return false;
        return true;
    }

    /** Carga un tinte. Devuelve false si ese tanque ya esta lleno. */
    public boolean cargarTinta(int canal, int cantidad) {
        if (cargas[canal] >= CARGA_MAXIMA) return false;
        cargas[canal] = Math.min(CARGA_MAXIMA, cargas[canal] + cantidad);
        tinta[canal] = cargas[canal] / (float) CARGA_MAXIMA;
        sincronizarVistaTanques();
        sincronizar();
        return true;
    }

    /** Carga papel. Devuelve false si el tanque ya esta lleno. */
    public boolean cargarPapel(int cantidad) {
        if (papelCargado >= CARGA_MAXIMA) return false;
        papelCargado = Math.min(CARGA_MAXIMA, papelCargado + cantidad);
        sincronizarVistaTanques();
        sincronizar();
        return true;
    }

    public int getPapel() {
        return papelCargado;
    }

    /** Gasta papel del tanque (la estampa escaneada, {@link EscanearEstampa}). */
    public void gastarPapel(int cantidad) {
        papelCargado = Math.max(0, papelCargado - cantidad);
        sincronizarVistaTanques();
        sincronizar();
    }

    /** Guarda una foto en el primer lugar libre del almacén; devuelve lo que no entró. */
    public ItemStack guardarEnAlmacen(ItemStack foto) {
        for (int i = 0; i < almacen.size(); i++) {
            if (almacen.get(i).isEmpty()) {
                almacen.set(i, foto);
                markDirty();
                return ItemStack.EMPTY;
            }
        }
        return foto;
    }

    /** La prenda a estampar: la remera de cualquier corte, o las medias. */
    public boolean ponerRemera(ItemStack stack) {
        if (!remera.isEmpty() || estado != Estado.REPOSO) return false;
        remera = stack.copyWithCount(1);
        soltarCapas();
        // La categoría de fijadas sigue automáticamente lo que se carga
        // — a pedido, ver el javadoc de {@link #categoria}.
        // La chaqueta comparte los diseños de la remera (misma tela, 2026-09-30).
        // El banner (2026-10-06) comparte los ajustes de la remera: es una sola foto, la del Frente.
        irACategoria(categoriaDe(remera));
        sincronizar();
        return true;
    }

    /**
     * Carga una foto en una cara concreta — a pedido (2026-09-19, "las
     * imagenes deberian agregarse aqui no en la estampadora"): antes iba
     * a la cara que tuviera elegida el selector físico del bloque; ahora
     * cada cara tiene su propio slot en la pantalla (ver
     * {@link #isValid}/{@link #setStack}), así que la cara la dice el
     * slot, no un selector aparte.
     */
    public boolean ponerFoto(Estampa.Cara cara, ItemStack stack, java.util.UUID id) {
        int i = cara.ordinal();
        if (!fotos[i].isEmpty() || estado != Estado.REPOSO) return false;
        // Sin UUID no hay nada que estampar: se rechaza en vez de gastar
        // tinta para producir una remera en blanco.
        if (id == null) return false;
        // Esa cara ya estampada: rechazar en vez de pisarla en silencio.
        if (!remera.isEmpty() && !hayMascara() && RemeraItem.estampaDe(remera, cara) != null) return false;
        if (cara == Estampa.Cara.ESPALDA && (soloFrente(remera))) return false;
        fotos[i] = stack.copyWithCount(1);
        pendientes[i] = id;
        sincronizar();
        return true;
    }

    // ── Inventory: los 2 slots de foto de la pantalla nueva (índice =
    // Estampa.Cara.ordinal(), 0=FRENTE, 1=ESPALDA) — a pedido (2026-09-19,
    // "las imagenes deberian agregarse aqui no en la estampadora"). Reusa
    // el mismo array fotos[] que ya guardaba esto (antes invisible,
    // cargado por click derecho sobre el bloque) como backing real.
    //
    // 2026-09-20, a pedido ("que carguen por hopper atrás [papel y
    // tintas] y que las 3 carguen prenda por hopper del lado izquierdo"):
    // se suman 6 slots más — remera, papel y las 4 tintas CMYK — cada uno
    // como una "ventana" sobre un campo que YA existía (remera/
    // papelCargado/cargas[]), no datos nuevos. Los tanques (papel/tinta)
    // se exponen como si fueran un slot con un stack del ítem real y
    // count=cantidad cargada — mismo truco que un tanque de combustible
    // expuesto a hoppers: el hopper hace su merge normal vía
    // getStack/setStack, sin que este código tenga que reimplementar esa
    // lógica. Ver getAvailableSlots/canInsert más abajo para qué lado
    // puede tocar qué slot — las fotos (0-1) siguen sin ser alcanzables
    // por hopper, a propósito (se cargan desde la pantalla, no de afuera).
    public static final int SLOT_REMERA = 2;
    public static final int SLOT_PAPEL = 3;
    public static final int SLOT_TINTA_BASE = 4; // .. +4 (C,M,Y,K, mismo orden que SublimadoraBlock.TINTES)
    /**
     * Salida visible — a pedido (2026-09-21, "que las tres tengan slot de
     * entrada y de salida"): antes {@link #salida} vivía solo como campo
     * (se sacaba con {@link #retirar()}, click derecho con la mano
     * vacía), ahora también es un slot real de verdad en pantalla —
     * mismo criterio que {@code SLOT_SALIDA} de Modeladora/Tinturas.
     * {@code retirar()} sigue andando igual, las dos formas conviven.
     */
    public static final int SLOT_SALIDA = SLOT_TINTA_BASE + 4;
    /**
     * Almacén de fotos — a pedido (2026-09-21, "slots" tras "tambien
     * podemos agregarle slots a la derecha para guardar imagenes"): antes
     * Frente/Espalda eran los ÚNICOS lugares donde vivía una foto, así
     * que cambiar de imagen significaba perder la anterior sin volver a
     * cargarla de afuera. Estos 9 son guardado nomás, ninguno alimenta un
     * prensado directo — se arrastran a Frente/Espalda como cualquier
     * slot vanilla. 27 (3 filas) desde 2026-09-28 ("quiero mas espacios
     * de almacenamiento"), como el almacén de la Modeladora: la lista va al
     * FINAL del inventario, así que agrandarla no corre ningún índice
     * guardado.
     */
    public static final int ALMACEN_TAMANO = 27;
    public static final int SLOT_ALMACEN_INICIO = SLOT_SALIDA + 1;
    private final net.minecraft.util.collection.DefaultedList<ItemStack> almacen =
            net.minecraft.util.collection.DefaultedList.ofSize(ALMACEN_TAMANO, ItemStack.EMPTY);

    /** Slot del molde de máscara (2026-10-02): al FINAL, para no correr los índices de antes. */
    public static final int SLOT_MASCARA = SLOT_ALMACEN_INICIO + ALMACEN_TAMANO;

    @Override public int size() { return SLOT_MASCARA + 1; }

    @Override
    public boolean isEmpty() {
        if (!fotos[0].isEmpty() || !fotos[1].isEmpty() || !remera.isEmpty() || !salida.isEmpty()) return false;
        if (!mascaraSlot.isEmpty()) return false;
        if (papelCargado > 0) return false;
        for (int c : cargas) if (c > 0) return false;
        for (ItemStack s : almacen) if (!s.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        if (slot == SLOT_MASCARA) return mascaraSlot;
        // Papel/tinta: se devuelve la MISMA instancia de vista entre
        // llamadas (nunca una nueva) — ver el javadoc de papelSlotView/
        // tintaSlotView, es lo que hace que un hopper mezclando de a uno
        // no pierda el incremento.
        if (slot >= SLOT_ALMACEN_INICIO) return almacen.get(slot - SLOT_ALMACEN_INICIO);
        return switch (slot) {
            case 0, 1 -> fotos[slot];
            case SLOT_REMERA -> remera;
            case SLOT_PAPEL -> papelSlotView;
            case SLOT_SALIDA -> salida;
            default -> tintaSlotView[slot - SLOT_TINTA_BASE];
        };
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        if (slot == SLOT_MASCARA) {
            ItemStack resultado = mascaraSlot.split(amount);
            if (!resultado.isEmpty()) sincronizar();
            return resultado;
        }
        if (slot >= SLOT_ALMACEN_INICIO) {
            ItemStack resultado = net.minecraft.inventory.Inventories.splitStack(almacen, slot - SLOT_ALMACEN_INICIO, amount);
            if (!resultado.isEmpty()) sincronizar();
            return resultado;
        }
        if (slot <= 1) {
            ItemStack resultado = fotos[slot].split(amount);
            if (!resultado.isEmpty()) {
                if (fotos[slot].isEmpty()) pendientes[slot] = null;
                sincronizar();
            }
            return resultado;
        }
        if (slot == SLOT_REMERA) {
            ItemStack resultado = remera.split(amount);
            if (!resultado.isEmpty()) sincronizar();
            return resultado;
        }
        if (slot == SLOT_SALIDA) {
            ItemStack resultado = salida.split(amount);
            if (!resultado.isEmpty()) {
                if (salida.isEmpty()) estado = Estado.REPOSO;
                sincronizar();
            }
            return resultado;
        }
        // Papel/tinta: no hay un ItemStack de verdad guardado, se
        // reconstruye a partir del contador — remover es simplemente
        // bajar el contador y devolver un stack equivalente.
        int actual = slot == SLOT_PAPEL ? papelCargado : cargas[slot - SLOT_TINTA_BASE];
        int sacado = Math.min(actual, amount);
        if (sacado <= 0) return ItemStack.EMPTY;
        setCarga(slot, actual - sacado);
        ItemStack resultado = getStackDeCarga(slot, sacado);
        sincronizar();
        return resultado;
    }

    @Override
    public ItemStack removeStack(int slot) {
        if (slot == SLOT_MASCARA) {
            ItemStack resultado = mascaraSlot;
            mascaraSlot = ItemStack.EMPTY;
            sincronizar();
            return resultado;
        }
        if (slot >= SLOT_ALMACEN_INICIO) {
            ItemStack resultado = net.minecraft.inventory.Inventories.removeStack(almacen, slot - SLOT_ALMACEN_INICIO);
            if (!resultado.isEmpty()) sincronizar();
            return resultado;
        }
        if (slot <= 1) {
            ItemStack resultado = fotos[slot];
            fotos[slot] = ItemStack.EMPTY;
            pendientes[slot] = null;
            sincronizar();
            return resultado;
        }
        if (slot == SLOT_REMERA) {
            ItemStack resultado = remera;
            remera = ItemStack.EMPTY;
            soltarCapas();
            sincronizar();
            return resultado;
        }
        if (slot == SLOT_SALIDA) {
            ItemStack resultado = salida;
            salida = ItemStack.EMPTY;
            estado = Estado.REPOSO;
            soltarCapas();
            sincronizar();
            return resultado;
        }
        int actual = slot == SLOT_PAPEL ? papelCargado : cargas[slot - SLOT_TINTA_BASE];
        ItemStack resultado = getStackDeCarga(slot, actual);
        setCarga(slot, 0);
        sincronizar();
        return resultado;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot == SLOT_MASCARA) {
            mascaraSlot = stack;
            sincronizar();
            return;
        }
        if (slot >= SLOT_ALMACEN_INICIO) {
            almacen.set(slot - SLOT_ALMACEN_INICIO, stack);
            if (stack.getCount() > stack.getMaxCount()) stack.setCount(stack.getMaxCount());
            sincronizar();
            return;
        }
        switch (slot) {
            case 0, 1 -> {
                if (stack.isEmpty()) {
                    fotos[slot] = ItemStack.EMPTY;
                    pendientes[slot] = null;
                } else {
                    ponerFoto(Estampa.Cara.values()[slot], stack, SublimadoraBlock.uuidDeFoto(stack));
                }
            }
            case SLOT_REMERA -> {
                ItemStack nueva = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
                if (!nueva.isEmpty() && (com.modamod.util.InventarioUtil.enCadena && linea) && estado == Estado.REPOSO
                        && remera.isEmpty() && salida.isEmpty() && !hayFijadas() && capas.isEmpty()) {
                    // Llegó por la cadena y no hay nada que estampar: pasa de
                    // largo (2026-10-04, "me parece perfecto que saltee").
                    salida = nueva;
                    estado = Estado.LISTO;
                    sincronizar();
                    return;
                }
                if (!ItemStack.areEqual(remera, nueva)) soltarCapas();
                remera = nueva;
                if (!nueva.isEmpty()) {
                    // Como en ponerRemera: la GUI muestra el tipo de prenda que entró (2026-10-04, "al poner un x
                    // input se seleccione automaticamente la gui de ese tipo de prenda").
                    irACategoria(categoriaDe(nueva));
                    // Llegó por tolva/cadena (no a mano ni desde la GUI) con un diseño fijado: arranca sola.
                    if (guiAbiertas == 0 && world != null && !world.isClient
                            && (caraActiva(0) || caraActiva(1) || !capas.isEmpty()) && intentarPrensar()) {
                        cerrarTapa();
                    }
                }
            }
            case SLOT_SALIDA -> {
                salida = stack;
                if (salida.isEmpty() && estado == Estado.LISTO) {
                    estado = Estado.REPOSO;
                    soltarCapas();
                }
            }
            default -> setCarga(slot, stack.isEmpty() ? 0
                    : MathHelper.clamp(stack.getCount(), 0, CARGA_MAXIMA));
        }
        sincronizar();
    }

    /** El tanque (papel o el canal de tinta que corresponda) a {@code valor}, sin pasar por cargarTinta/cargarPapel (que solo suman). */
    private void setCarga(int slot, int valor) {
        if (slot == SLOT_PAPEL) {
            papelCargado = valor;
        } else {
            int canal = slot - SLOT_TINTA_BASE;
            cargas[canal] = valor;
            tinta[canal] = cargas[canal] / (float) CARGA_MAXIMA;
        }
        sincronizarVistaTanques();
    }

    private ItemStack getStackDeCarga(int slot, int cantidad) {
        if (cantidad <= 0) return ItemStack.EMPTY;
        return slot == SLOT_PAPEL
                ? new ItemStack(net.minecraft.item.Items.PAPER, cantidad)
                : new ItemStack(SublimadoraBlock.TINTES[slot - SLOT_TINTA_BASE], cantidad);
    }

    /**
     * OJO: NO llamar {@code sincronizar()} acá — esa función llama
     * {@code markDirty()}, y como este método la pisa (override de
     * {@code Inventory}), las dos se llamaban entre sí para siempre
     * (StackOverflowError apenas se tocaba el bloque, 2026-09-20).
     * {@code super.markDirty()} es la de {@code BlockEntity} de toda la
     * vida, no la reimplementada acá.
     */
    @Override
    public void markDirty() {
        // Vista → autoridad, ANTES de todo lo demás (2026-09-20): si un
        // hopper mutó papelSlotView/tintaSlotView[] directo en memoria
        // (ver su javadoc), acá es donde se entera el resto del código.
        // Si nada externo lo tocó, esto no cambia nada (sincronizarVistaTanques
        // ya los había dejado iguales) — comparar antes de reasignar evita
        // relecturas de tinta[]/sincronizar() de más en el camino normal.
        int papelDeVista = papelSlotView.isEmpty() ? 0 : papelSlotView.getCount();
        if (papelDeVista != papelCargado) papelCargado = MathHelper.clamp(papelDeVista, 0, CARGA_MAXIMA);
        for (int i = 0; i < 4; i++) {
            int deVista = tintaSlotView[i].isEmpty() ? 0 : tintaSlotView[i].getCount();
            if (deVista != cargas[i]) {
                cargas[i] = MathHelper.clamp(deVista, 0, CARGA_MAXIMA);
                tinta[i] = cargas[i] / (float) CARGA_MAXIMA;
            }
        }
        super.markDirty();
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    @Override
    public void clear() {
        for (int i = 0; i < 2; i++) { fotos[i] = ItemStack.EMPTY; pendientes[i] = null; }
        remera = ItemStack.EMPTY;
        salida = ItemStack.EMPTY;
        papelCargado = 0;
        java.util.Arrays.fill(cargas, 0);
        java.util.Arrays.fill(tinta, 0f);
        almacen.clear();
        mascaraSlot = ItemStack.EMPTY;
        sincronizarVistaTanques();
        sincronizar();
    }

    /**
     * Solo fotos de Camerapture, y solo mientras se pueda cargar de
     * verdad — mismos guardas que {@link #ponerFoto} (reposo, cara sin
     * estampar todavía), para que un drag-and-drop rechazado rebote en
     * vez de aceptarse en silencio y fallar después.
     *
     * <p>Los slots 2-7 (remera/papel/tinta, 2026-09-20) mismos guardas
     * que ya usaban {@link #ponerRemera}/{@link #cargarPapel}/
     * {@link #cargarTinta} desde el click derecho — un hopper entra por
     * la misma puerta que un jugador, no una más permisiva.
     */
    @Override
    public boolean isValid(int slot, ItemStack stack) {
        if (slot == SLOT_MASCARA) return mascaraSlot.isEmpty() && stack.getItem() instanceof MoldeMascaraItem;
        // Almacén de fotos: guardado nomás, cualquier foto entra (2026-09-21,
        // "slots"), sin las restricciones de reposo/cara-sin-estampar de
        // Frente/Espalda (slot <= 1) porque no alimenta un prensado directo.
        if (slot >= SLOT_ALMACEN_INICIO) return SublimadoraBlock.esFoto(stack);
        if (slot <= 1) {
            if (estado != Estado.REPOSO) return false;
            if (!fotos[slot].isEmpty()) return false;
            Estampa.Cara cara = Estampa.Cara.values()[slot];
            if (!remera.isEmpty() && !hayMascara() && RemeraItem.estampaDe(remera, cara) != null) return false;
            if (cara == Estampa.Cara.ESPALDA && (soloFrente(remera))) return false;
            return SublimadoraBlock.esFoto(stack) && SublimadoraBlock.uuidDeFoto(stack) != null;
        }
        if (slot == SLOT_REMERA) {
            return remera.isEmpty() && estado == Estado.REPOSO
                    && ModItems.esEstampable(stack) && (!SublimadoraBlock.tieneLasDosCaras(stack) || hayMascara());
        }
        if (slot == SLOT_PAPEL) {
            return stack.isOf(net.minecraft.item.Items.PAPER) && papelCargado < CARGA_MAXIMA;
        }
        // Salida: solo la máquina escribe acá, igual que en Modeladora/Tinturas.
        if (slot == SLOT_SALIDA) return false;
        int canal = slot - SLOT_TINTA_BASE;
        return stack.isOf(SublimadoraBlock.TINTES[canal]) && cargas[canal] < CARGA_MAXIMA;
    }

    // ── SidedInventory: remera por ARRIBA (2026-09-21, "que las tres
    // carguen por arriba" — reemplaza el costado izquierdo de
    // 2026-09-20), papel/tinta siguen por ATRÁS, relativo a FACING como
    // si un jugador estuviera parado frente al panel mirándolo. Las fotos
    // (0-1) no aparecen en ningún lado: siguen siendo solo-GUI a propósito.
    private Direction ladoIzquierdo() {
        return getCachedState().get(SublimadoraBlock.FACING).getOpposite().rotateYCounterclockwise();
    }

    private Direction ladoAtras() {
        return getCachedState().get(SublimadoraBlock.FACING).getOpposite();
    }

    @Override
    public Direction ladoSalida() { return ladoDerecho(); }

    /** El lado opuesto al de carga — hacia ahí se empuja el resultado (ver {@link #empujarSalida}). */
    private Direction ladoDerecho() {
        return ladoIzquierdo().getOpposite();
    }

    /**
     * Empuja la remera lista hacia la derecha — a pedido (2026-09-20,
     * "las 3 maquinas hacen como el crafter y depositan el resultado en
     * el bloque siguiente si es una de las 3 maquinas o si es
     * contenedor"): mismo mecanismo que un Crafter vanilla, ver
     * {@link com.modamod.util.InventarioUtil#empujarA}. Si no hay
     * nada del otro lado (o está lleno) la remera simplemente se queda
     * en {@code salida}, recuperable como siempre.
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

    private void empujarSalida(net.minecraft.world.World world, BlockPos pos) {
        if (salida.isEmpty() || !linea) return;
        Direction derecha = ladoDerecho();
        ItemStack sobrante = com.modamod.util.InventarioUtil.empujarA(
                world, pos.offset(derecha), derecha.getOpposite(), salida);
        if (sobrante.getCount() != salida.getCount()) {
            salida = sobrante;
            if (salida.isEmpty()) estado = Estado.REPOSO;
            sincronizar();
        }
    }

    // La remera entra por ARRIBA (2026-09-21, "que las tres carguen por
    // arriba") — reemplaza el costado izquierdo que usaba hasta ahora;
    // papel/tinta siguen por atrás, sin cambios.
    @Override
    public int[] getAvailableSlots(Direction side) {
        // También por la IZQUIERDA (2026-10-04, "poder cargarles prendas por la izquierda").
        if (side == Direction.UP || side == ladoIzquierdo()) return new int[]{SLOT_REMERA};
        if (side == ladoAtras()) return new int[]{SLOT_PAPEL, SLOT_TINTA_BASE, SLOT_TINTA_BASE + 1, SLOT_TINTA_BASE + 2, SLOT_TINTA_BASE + 3};
        return new int[0];
    }

    @Override
    public int topeEntrada(int slot) { return slot == SLOT_REMERA ? 1 : getMaxCountPerStack(); }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @org.jetbrains.annotations.Nullable Direction dir) {
        if (dir != null && com.modamod.util.Redstone.pausada(this)) return false;   // con señal, nada entra por automatización
        if (SLOT_REMERA == slot && !getStack(slot).isEmpty()) return false;   // una prenda por vez

        return isValid(slot, stack);
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        // Solo inserción por ahora (2026-09-20) — extracción automática
        // (ej. sacar la remera terminada) queda pendiente a propósito.
        return false;
    }

    public Estampa.Cara getSeleccion() {
        return seleccion;
    }

    /** Da vuelta el selector de cara. No se puede en medio de un prensado. */
    public boolean cambiarSeleccion() {
        if (estado == Estado.PRENSANDO) return false;
        seleccion = seleccion == Estampa.Cara.FRENTE ? Estampa.Cara.ESPALDA : Estampa.Cara.FRENTE;
        sincronizar();
        return true;
    }

    /** La foto cargada para esa cara, o null. */
    @org.jetbrains.annotations.Nullable
    public java.util.UUID getFotoCargada(Estampa.Cara cara) {
        return fotos[cara.ordinal()].isEmpty() ? null : pendientes[cara.ordinal()];
    }

    /**
     * La foto que hay que DIBUJAR para esa cara, que no es siempre la que
     * esta cargada: el prensado la consume en el mismo tick en que se cierra
     * la tapa, pero la tapa tarda seis en bajar.
     */
    @org.jetbrains.annotations.Nullable
    public java.util.UUID getFotoVisible(Estampa.Cara cara) {
        if (getCachedState().get(SublimadoraBlock.OPEN)) {
            java.util.UUID cargada = getFotoCargada(cara);
            if (cargada != null) return cargada;
        }
        return arrastre > 0 ? ultimasVistas[cara.ordinal()] : null;
    }

    public float getEscala(Estampa.Cara cara) { return escalaBorrador[cara.ordinal()]; }
    public float getX(Estampa.Cara cara) { return xBorrador[cara.ordinal()]; }
    public float getY(Estampa.Cara cara) { return yBorrador[cara.ordinal()]; }
    public float getAngulo(Estampa.Cara cara) { return anguloBorrador[cara.ordinal()]; }

    /** Escala/posicion/angulo de la cara elegida, que es la que muestra la pantalla. */
    public float getEscalaBorrador() { return (editaMascara() ? mascaraEscala : escalaBorrador)[seleccion.ordinal()]; }
    public float getXBorrador() { return (editaMascara() ? mascaraX : xBorrador)[seleccion.ordinal()]; }
    public float getYBorrador() { return (editaMascara() ? mascaraY : yBorrador)[seleccion.ordinal()]; }
    public float getAnguloBorrador() { return (editaMascara() ? mascaraAngulo : anguloBorrador)[seleccion.ordinal()]; }

    // ── máscaras (2026-10-02) ──────────────────────────────────────────

    public boolean hayMascara() {
        return mascaraSlot.getItem() instanceof MoldeMascaraItem;
    }

    @org.jetbrains.annotations.Nullable
    public FormaMascara formaMascara() {
        return mascaraSlot.getItem() instanceof MoldeMascaraItem m ? m.forma : null;
    }

    /** Si los controles mueven la máscara (hay una puesta y se eligió "Editar: máscara"). */
    public boolean editaMascara() {
        return editarMascara && hayMascara();
    }

    public boolean editarMascaraElegido() { return editarMascara; }

    public java.util.List<CapaEstampa> capas() { return capas; }

    public int capaElegida() { return capaElegida; }

    /** La capa que se fijaría ahora en esa cara (foto + máscara puestas), o null. */
    @org.jetbrains.annotations.Nullable
    private CapaEstampa capaEnCurso(Estampa.Cara cara) {
        FormaMascara forma = formaMascara();
        java.util.UUID id = getFotoCargada(cara);
        if (forma == null || id == null) return null;
        int i = cara.ordinal();
        return new CapaEstampa(cara,
                new Estampa(id, escalaBorrador[i], xBorrador[i], yBorrador[i], anguloBorrador[i], false),
                new Mascara(forma, mascaraEscala[i], mascaraX[i], mascaraY[i], mascaraAngulo[i]));
    }

    /**
     * Suelta la lista de capas de la máquina cuando cambia la prenda (2026-10-04, "no puedo hacer que varias mascaras
     * se sumen en la remera"): la lista se arma desde las capas de la prenda puesta al fijar la primera y, si
     * quedaba de un trabajo anterior, reemplazaba las capas de la prenda siguiente al prensar y las perdía.
     */
    private void soltarCapas() {
        // 2026-10-04, "no esta guardando settings... tengo q pinear cada vez q le entra una prenda": la lista es el
        // DISEÑO de la máquina (como las fijadas de Modeladora/Tintes) y se queda entre prendas; al prensar se suma a
        // las capas que la prenda ya traía. Solo se suelta la selección.
        capaElegida = -1;
    }

    /** Las capas que ya traía la prenda y, encima, las del diseño de la máquina (hasta el tope). */
    private static java.util.List<CapaEstampa> sumarCapas(java.util.List<CapaEstampa> deLaPrenda, java.util.List<CapaEstampa> deLaMaquina) {
        java.util.List<CapaEstampa> todas = new java.util.ArrayList<>(deLaPrenda);
        todas.addAll(deLaMaquina);
        return todas.size() > CapaEstampa.MAXIMO ? new java.util.ArrayList<>(todas.subList(0, CapaEstampa.MAXIMO)) : todas;
    }

    /**
     * Cuántas capas de la máquina NO entrarían en la prenda cargada (tope {@link CapaEstampa#MAXIMO} entre las que ya
     * trae y las del diseño). 2026-10-08, hallazgo H04: antes {@link #sumarCapas} descartaba las nuevas en silencio
     * después de gastar tinta y papel.
     */
    private int capasDeMas() {
        if (remera.isEmpty() || capas.isEmpty()) return 0;
        return Math.max(0, RemeraItem.capasDe(remera).size() + capas.size() - CapaEstampa.MAXIMO);
    }

    /** Chincheta con máscara: agrega la capa en curso de esa cara. */
    private boolean fijarCapa(Estampa.Cara cara) {
        CapaEstampa capa = capaEnCurso(cara);
        if (capa == null) return false;
        if (capas.size() >= CapaEstampa.MAXIMO) {
            avisar(Text.translatable("modamod.sublimadora.capa_llena"));
            return false;
        }
        capas.add(capa);
        capaElegida = capas.size() - 1;
        // La foto ya quedó en la capa: la cara deja de estamparse entera.
        caraFijada[cara.ordinal()] = false;
        sincronizar();
        return true;
    }

    private void avisar(Text texto) {
        if (world == null || world.isClient) return;
        for (PlayerEntity p : world.getPlayers()) {
            if (p.currentScreenHandler instanceof SublimadoraScreenHandler h && h.be == this) {
                p.sendMessage(texto.copy().formatted(net.minecraft.util.Formatting.GOLD), true);
            }
        }
    }

    private boolean moverCapa(int delta) {
        int j = capaElegida + delta;
        if (capaElegida < 0 || capaElegida >= capas.size() || j < 0 || j >= capas.size()) return false;
        java.util.Collections.swap(capas, capaElegida, j);
        capaElegida = j;
        sincronizar();
        return true;
    }

    private boolean borrarCapa() {
        if (capaElegida < 0 || capaElegida >= capas.size()) return false;
        capas.remove(capaElegida);
        capaElegida = Math.min(capaElegida, capas.size() - 1);
        sincronizar();
        return true;
    }

    /**
     * Ajustan escala/posicion/angulo de la cara elegida — mismo criterio
     * que {@link #cambiarSeleccion()}: cambian SOLO la cara activa, no las
     * dos. {@code direccion} es +1 o -1 (flechitas ‹ ›).
     */
    public boolean cambiarEscala(int direccion) {
        if (estado == Estado.PRENSANDO) return false;
        int i = seleccion.ordinal();
        if (editaMascara()) {
            mascaraEscala[i] = MathHelper.clamp(mascaraEscala[i] + direccion * PASO_ESCALA,
                    Mascara.ESCALA_MINIMA, Mascara.ESCALA_MAXIMA);
            sincronizar();
            return true;
        }
        escalaBorrador[i] = MathHelper.clamp(escalaBorrador[i] + direccion * PASO_ESCALA,
                Estampa.ESCALA_MINIMA, Estampa.ESCALA_MAXIMA);
        sincronizar();
        return true;
    }

    public boolean cambiarX(int direccion) {
        if (estado == Estado.PRENSANDO) return false;
        int i = seleccion.ordinal();
        if (editaMascara()) {
            mascaraX[i] = MathHelper.clamp(mascaraX[i] + direccion * PASO_POSICION, -0.5f, 0.5f);
            sincronizar();
            return true;
        }
        xBorrador[i] = MathHelper.clamp(xBorrador[i] + direccion * PASO_POSICION, -0.5f, 0.5f);
        sincronizar();
        return true;
    }

    public boolean cambiarY(int direccion) {
        if (estado == Estado.PRENSANDO) return false;
        int i = seleccion.ordinal();
        if (editaMascara()) {
            mascaraY[i] = MathHelper.clamp(mascaraY[i] + direccion * PASO_POSICION, -0.5f, 0.5f);
            sincronizar();
            return true;
        }
        yBorrador[i] = MathHelper.clamp(yBorrador[i] + direccion * PASO_POSICION, -0.5f, 0.5f);
        sincronizar();
        return true;
    }

    /** Wrappea 0-345, mismo criterio que {@code TinturasBlockEntity#cambiarAngulo}. */
    public boolean cambiarAngulo(int direccion) {
        if (estado == Estado.PRENSANDO) return false;
        int i = seleccion.ordinal();
        if (editaMascara()) {
            mascaraAngulo[i] = ((Math.round(mascaraAngulo[i]) + 360 + direccion * (int) PASO_ANGULO) % 360);
            sincronizar();
            return true;
        }
        float actual = anguloBorrador[i];
        anguloBorrador[i] = ((Math.round(actual) + 360 + direccion * (int) PASO_ANGULO) % 360);
        sincronizar();
        return true;
    }

    // ── botones de la pantalla (mismo mecanismo que TinturasBlockEntity) ──
    public static final int BTN_SELECCION = 0;
    public static final int BTN_ESCALA_MAS = 1;
    public static final int BTN_ESCALA_MENOS = 2;
    public static final int BTN_X_MAS = 3;
    public static final int BTN_X_MENOS = 4;
    public static final int BTN_Y_MAS = 5;
    public static final int BTN_Y_MENOS = 6;
    public static final int BTN_ANGULO_MAS = 7;
    public static final int BTN_ANGULO_MENOS = 8;
    /** Cicla la categoría de diseños — ver el javadoc de {@link #categoria}. */
    public static final int BTN_CATEGORIA = 10;
    /** Click en el slot de foto de una cara: la elige para editar (+ Cara.ordinal()). */
    public static final int BTN_ELEGIR_CARA_BASE = 20;
    /** Chincheta de una cara (+ Cara.ordinal()). */
    public static final int BTN_CHINCHETA_BASE = 22;
    /** Botón Prensar — lo atiende SublimadoraScreenHandler (necesita avisarle al jugador). */
    public static final int BTN_PRENSAR = 25;
    /** Prende/apaga la simetría lateral (medias y calientabrazos). */
    public static final int BTN_SIMETRIA = 26;
    /** Alterna si los controles mueven la foto o la máscara (2026-10-02). */
    public static final int BTN_EDITAR = 27;
    /** Elegir capa 0..11, y subir/bajar/borrar la elegida (2026-10-02). */
    public static final int BTN_CAPA_BASE = 50;
    public static final int BTN_CAPA_SUBIR = BTN_CAPA_BASE + CapaEstampa.MAXIMO;
    public static final int BTN_CAPA_BAJAR = BTN_CAPA_SUBIR + 1;
    public static final int BTN_CAPA_BORRAR = BTN_CAPA_SUBIR + 2;
    public static final int BTN_CARGAR_ULTIMO = 28, BTN_CATEGORIA_ATRAS = 67;
    /** Suelta las dos caras fijadas y quita las capas (2026-10-14, "un boton de delete"); los diseños con nombre no se tocan. */
    public static final int BTN_BORRAR = 29, BTN_BORRAR_TODAS = 68;
    public static final int BTN_CARGAR_DISENO_BASE = 30; // .. + DISENOS_MAXIMO
    public static final int BTN_BORRAR_DISENO_BASE = 40; // .. + DISENOS_MAXIMO

    public boolean onButtonClick(int id) {
        if (id == BTN_LINEA) {
            alternarLinea();
            return true;
        }
        if (id == BTN_CATEGORIA || id == BTN_CATEGORIA_ATRAS) {
            cambiarCategoria(id == BTN_CATEGORIA_ATRAS ? -1 : 1);
            return true;
        }
        if (id == BTN_CARGAR_ULTIMO) return cargarUltimo();
        if (id == BTN_BORRAR_TODAS) {
            // Todas las categorías: el borrador activo y los de las demás (2026-10-14, "agrega el delete all a la sublimadora").
            if (estado == Estado.PRENSANDO) return false;
            boolean alguna = hayFijadas() || !capas.isEmpty();
            for (Juego j : juegos.values()) {
                alguna |= j.caraFijada[0] || j.caraFijada[1];
                j.caraFijada[0] = j.caraFijada[1] = false;
            }
            caraFijada[0] = caraFijada[1] = false;
            capas.clear();
            capaElegida = -1;
            if (alguna) sincronizar();
            return alguna;
        }
        if (id == BTN_BORRAR) {
            if (estado == Estado.PRENSANDO || (!hayFijadas() && capas.isEmpty())) return false;
            caraFijada[0] = caraFijada[1] = false;
            capas.clear();
            capaElegida = -1;
            sincronizar();
            return true;
        }
        if (id >= BTN_ELEGIR_CARA_BASE && id < BTN_ELEGIR_CARA_BASE + 2) {
            Estampa.Cara cara = Estampa.Cara.values()[id - BTN_ELEGIR_CARA_BASE];
            if (cara == seleccion || estado == Estado.PRENSANDO) return false;
            seleccion = cara;
            sincronizar();
            return true;
        }
        if (id >= BTN_CHINCHETA_BASE && id < BTN_CHINCHETA_BASE + 2) {
            if (estado == Estado.PRENSANDO) return false;
            int i = id - BTN_CHINCHETA_BASE;
            if (hayMascara() && !fotos[i].isEmpty()) {
                seleccion = Estampa.Cara.values()[i];
                return fijarCapa(seleccion);
            }
            caraFijada[i] = !caraFijada[i];
            seleccion = Estampa.Cara.values()[i];
            sincronizar();
            return true;
        }
        if (id == BTN_SIMETRIA) {
            if (estado == Estado.PRENSANDO || !admiteSimetria(categoria)) return false;
            simetria = !simetria;
            sincronizar();
            return true;
        }
        if (id == BTN_EDITAR) {
            if (estado == Estado.PRENSANDO) return false;
            editarMascara = !editarMascara;
            sincronizar();
            return true;
        }
        if (id >= BTN_CAPA_BASE && id < BTN_CAPA_BASE + CapaEstampa.MAXIMO) {
            int j = id - BTN_CAPA_BASE;
            if (j >= capas.size()) return false;
            capaElegida = capaElegida == j ? -1 : j;
            sincronizar();
            return true;
        }
        if (id == BTN_CAPA_SUBIR || id == BTN_CAPA_BAJAR || id == BTN_CAPA_BORRAR) {
            if (estado == Estado.PRENSANDO) return false;
            return id == BTN_CAPA_BORRAR ? borrarCapa() : moverCapa(id == BTN_CAPA_SUBIR ? 1 : -1);
        }
        if (id >= BTN_CARGAR_DISENO_BASE && id < BTN_CARGAR_DISENO_BASE + DISENOS_MAXIMO) {
            return cargarDiseno(id - BTN_CARGAR_DISENO_BASE);
        }
        if (id >= BTN_BORRAR_DISENO_BASE && id < BTN_BORRAR_DISENO_BASE + DISENOS_MAXIMO) {
            return borrarDiseno(id - BTN_BORRAR_DISENO_BASE);
        }
        return switch (id) {
            case BTN_SELECCION -> cambiarSeleccion();
            case BTN_ESCALA_MAS -> cambiarEscala(1);
            case BTN_ESCALA_MENOS -> cambiarEscala(-1);
            case BTN_X_MAS -> cambiarX(1);
            case BTN_X_MENOS -> cambiarX(-1);
            case BTN_Y_MAS -> cambiarY(1);
            case BTN_Y_MENOS -> cambiarY(-1);
            case BTN_ANGULO_MAS -> cambiarAngulo(1);
            case BTN_ANGULO_MENOS -> cambiarAngulo(-1);
            default -> false;
        };
    }

    public boolean caraFijada(Estampa.Cara cara) { return caraFijada[cara.ordinal()]; }

    /** Algo fijado para guardar como diseño. */
    public boolean hayFijadas() { return caraFijada[0] || caraFijada[1]; }

    /** Los diseños guardados de la categoría activa — ver {@link #categoria}. */
    public java.util.List<DisenoEstampa> disenos() {
        return disenosPorItem.getOrDefault(categoria, java.util.List.of());
    }

    /** Nombre del diseño de ese casillero (para el hover), o null si está vacío. */
    @org.jetbrains.annotations.Nullable
    public String nombreDiseno(int idx) {
        java.util.List<DisenoEstampa> lista = disenos();
        return idx >= 0 && idx < lista.size() ? lista.get(idx).nombre() : null;
    }

    public net.minecraft.item.Item categoria() { return categoria; }

    /** Cicla la categoría a mano — ver el javadoc de {@link #categoria}. */
    private void cambiarCategoria(int delta) {
        int i = java.util.Arrays.asList(CATEGORIAS).indexOf(categoria);
        irACategoria(CATEGORIAS[Math.floorMod(i + delta, CATEGORIAS.length)]);
        sincronizar();
    }

    /**
     * Recibido desde {@link GuardarDisenoSublimadoraPayload}: guarda el
     * ajuste de las dos caras y qué caras están fijadas, con nombre, en el
     * próximo casillero libre de la categoría activa.
     */
    public boolean guardarDiseno(String nombreCrudo) {
        java.util.List<DisenoEstampa> lista = disenosPorItem.computeIfAbsent(categoria, k -> new java.util.ArrayList<>());
        if (!hayFijadas() || lista.size() >= DISENOS_MAXIMO) return false;
        String nombre = nombreCrudo == null || nombreCrudo.isBlank() ? "#" + (lista.size() + 1) : nombreCrudo.trim();
        if (nombre.length() > 24) nombre = nombre.substring(0, 24);
        lista.add(new DisenoEstampa(nombre, new EstampaFijada(
                escalaBorrador[0], xBorrador[0], yBorrador[0], anguloBorrador[0],
                escalaBorrador[1], xBorrador[1], yBorrador[1], anguloBorrador[1]),
                caraFijada[0], caraFijada[1], simetria));
        sincronizar();
        return true;
    }

    private boolean cargarDiseno(int idx) {
        java.util.List<DisenoEstampa> lista = disenos();
        if (idx < 0 || idx >= lista.size() || estado == Estado.PRENSANDO) return false;
        aplicarDiseno(lista.get(idx));
        return true;
    }

    private void aplicarDiseno(DisenoEstampa d) {
        EstampaFijada f = d.ajuste();
        escalaBorrador[0] = f.escalaFrente(); xBorrador[0] = f.xFrente(); yBorrador[0] = f.yFrente(); anguloBorrador[0] = f.anguloFrente();
        escalaBorrador[1] = f.escalaEspalda(); xBorrador[1] = f.xEspalda(); yBorrador[1] = f.yEspalda(); anguloBorrador[1] = f.anguloEspalda();
        caraFijada[0] = d.fijadaFrente();
        caraFijada[1] = d.fijadaEspalda();
        simetria = d.simetria();
        sincronizar();
    }

    private boolean borrarDiseno(int idx) {
        java.util.List<DisenoEstampa> lista = disenosPorItem.get(categoria);
        if (lista == null || idx < 0 || idx >= lista.size()) return false;
        lista.remove(idx);
        sincronizar();
        return true;
    }

    // ── bolsas y diseños como ítems (2026-10-13, "vamos con la siguiente maquina"): fotos, máscaras y diseños son
    // referencias que no se gastan; elegir una pone una copia en el casillero interno de la cara o de la máscara ──
    public static final int FOTOS_MAX = 500, MASCARAS_MAX = 200, DISENOS_ITEM_MAX = 200;
    private final java.util.List<ItemStack> fotosBolsa = new java.util.ArrayList<>();
    private final java.util.List<ItemStack> mascarasBolsa = new java.util.ArrayList<>();
    private final java.util.List<ItemStack> disenosItem = new java.util.ArrayList<>();

    /** El casillero del diseño en blanco para exportar (2026-10-25, "un slot para poner un diseño en blanco y un botoncito de exportar"). */
    public final net.minecraft.inventory.SimpleInventory blancoDiseno = com.modamod.util.DisenosMaquina.casillero(this);

    /** Exporta el diseño {@code idx} de la lista como ítem, gastando un diseño en blanco del casillero. */
    public boolean exportarDiseno(PlayerEntity jugador, int idx) {
        boolean ok = com.modamod.util.DisenosMaquina.exportar(jugador, disenosItem, idx, blancoDiseno,
                com.modamod.util.MaquinaCreativa.es(this) || jugador.isCreative());
        if (ok) sincronizar();
        return ok;
    }
    private final java.util.Map<net.minecraft.item.Item, DisenoEstampa> ultimoPorItem = new java.util.HashMap<>();

    public java.util.List<ItemStack> fotosBolsa() { return java.util.Collections.unmodifiableList(fotosBolsa); }
    public java.util.List<ItemStack> mascarasBolsa() { return java.util.Collections.unmodifiableList(mascarasBolsa); }
    public java.util.List<ItemStack> disenosItem() { return java.util.Collections.unmodifiableList(disenosItem); }

    /** ¿Entra en el depósito de las bolsas? Una foto de Camerapture, una máscara o un diseño de sublimado. */
    public static boolean esDeposito(ItemStack s) {
        return (SublimadoraBlock.esFoto(s) && SublimadoraBlock.uuidDeFoto(s) != null)
                || s.getItem() instanceof MoldeMascaraItem || s.getItem() instanceof DisenoSublimadoItem;
    }

    /** Banner, delantal, máscaras y barbijo (2026-10-24) solo tienen frente. */
    public static boolean soloFrente(ItemStack s) {
        return s.getItem() instanceof net.minecraft.item.BannerItem || s.getItem() instanceof com.modamod.item.DelantalItem
                || s.getItem() instanceof com.modamod.item.CaraItem || s.getItem() instanceof com.modamod.item.SombreroBrujaItem;
    }

    /** La cara de atrás existe (un banner o un delantal solo tienen frente). */
    public boolean admiteEspalda() {
        return !(soloFrente(remera));
    }

    @Override
    public void cargarCreativa() {
        for (MoldeMascaraItem m : ModItems.MOLDES_MASCARA) absorber(new ItemStack(m));
        markDirty();
        sincronizar();
    }

    /** Suma un ítem a su bolsa (servidor). Una foto o máscara repetida cuenta como absorbida. */
    public boolean absorber(ItemStack pila) {
        if (world == null || world.isClient || pila.isEmpty()) return false;
        if (pila.getItem() instanceof DisenoSublimadoItem) {
            if (com.modamod.util.DisenosMaquina.yaEsta(disenosItem, pila)) return true;
            if (disenosItem.size() >= DISENOS_ITEM_MAX) return false;
            disenosItem.add(pila.copyWithCount(1));
        } else if (pila.getItem() instanceof MoldeMascaraItem) {
            for (ItemStack e : mascarasBolsa) if (e.getItem() == pila.getItem()) return true;
            if (mascarasBolsa.size() >= MASCARAS_MAX) return false;
            mascarasBolsa.add(pila.copyWithCount(1));
        } else if (SublimadoraBlock.esFoto(pila)) {
            java.util.UUID id = SublimadoraBlock.uuidDeFoto(pila);
            if (id == null) return false;
            for (ItemStack e : fotosBolsa) if (id.equals(SublimadoraBlock.uuidDeFoto(e))) return true;
            if (fotosBolsa.size() >= FOTOS_MAX) return false;
            fotosBolsa.add(pila.copyWithCount(1));
        } else {
            return false;
        }
        sincronizar();
        return true;
    }

    /** Pone la foto {@code idx} de la bolsa en la cara elegida (la que ya hubiera se reemplaza). */
    public boolean elegirFoto(int idx) {
        if (estado != Estado.REPOSO || idx < 0 || idx >= fotosBolsa.size()) return false;
        ItemStack f = fotosBolsa.get(idx);
        java.util.UUID id = SublimadoraBlock.uuidDeFoto(f);
        if (id == null) return false;
        int i = seleccion.ordinal();
        ItemStack previa = fotos[i];
        java.util.UUID previaId = pendientes[i];
        fotos[i] = ItemStack.EMPTY;
        pendientes[i] = null;
        if (!isValid(i, f) || !ponerFoto(seleccion, f, id)) {
            fotos[i] = previa;
            pendientes[i] = previaId;
            return false;
        }
        return true;
    }

    public boolean quitarFoto() {
        if (estado != Estado.REPOSO) return false;
        int i = seleccion.ordinal();
        if (fotos[i].isEmpty()) return false;
        fotos[i] = ItemStack.EMPTY;
        pendientes[i] = null;
        sincronizar();
        return true;
    }

    public boolean elegirMascara(int idx) {
        if (estado != Estado.REPOSO || idx < 0 || idx >= mascarasBolsa.size()) return false;
        mascaraSlot = mascarasBolsa.get(idx).copyWithCount(1);
        sincronizar();
        return true;
    }

    public boolean quitarMascara() {
        if (estado != Estado.REPOSO || mascaraSlot.isEmpty()) return false;
        mascaraSlot = ItemStack.EMPTY;
        editarMascara = false;
        sincronizar();
        return true;
    }

    public boolean sacarDeBolsa(PlayerEntity jugador, int tipo, int idx) {
        java.util.List<ItemStack> l = tipo == 0 ? fotosBolsa : mascarasBolsa;
        if (idx < 0 || idx >= l.size()) return false;
        jugador.getInventory().offerOrDrop(l.remove(idx));
        sincronizar();
        return true;
    }

    /** Lo que quedó en el almacén viejo de 27 casilleros pasa a la bolsa de fotos. */
    private void drenarAlmacen() {
        boolean cambio = false;
        for (int i = 0; i < almacen.size(); i++) {
            ItemStack s = almacen.get(i);
            if (s.isEmpty()) continue;
            if (absorber(s)) { almacen.set(i, ItemStack.EMPTY); cambio = true; }
        }
        if (cambio) sincronizar();
    }

    private DisenoEstampa disenoActual(String nombre) {
        return new DisenoEstampa(nombre, new EstampaFijada(
                escalaBorrador[0], xBorrador[0], yBorrador[0], anguloBorrador[0],
                escalaBorrador[1], xBorrador[1], yBorrador[1], anguloBorrador[1]),
                caraFijada[0], caraFijada[1], simetria);
    }

    private static NbtCompound disenoANbt(DisenoEstampa d) {
        EstampaFijada f = d.ajuste();
        NbtCompound fc = new NbtCompound();
        fc.putString("Nombre", d.nombre());
        fc.putFloat("EscalaF", f.escalaFrente());
        fc.putFloat("XF", f.xFrente());
        fc.putFloat("YF", f.yFrente());
        fc.putFloat("AnguloF", f.anguloFrente());
        fc.putFloat("EscalaE", f.escalaEspalda());
        fc.putFloat("XE", f.xEspalda());
        fc.putFloat("YE", f.yEspalda());
        fc.putFloat("AnguloE", f.anguloEspalda());
        fc.putBoolean("FijadaF", d.fijadaFrente());
        fc.putBoolean("FijadaE", d.fijadaEspalda());
        fc.putBoolean("Simetria", d.simetria());
        return fc;
    }

    private static DisenoEstampa disenoDeNbt(NbtCompound fc) {
        return new DisenoEstampa(fc.getString("Nombre"), new EstampaFijada(
                fc.getFloat("EscalaF"), fc.getFloat("XF"), fc.getFloat("YF"), fc.getFloat("AnguloF"),
                fc.getFloat("EscalaE"), fc.getFloat("XE"), fc.getFloat("YE"), fc.getFloat("AnguloE")),
                fc.getBoolean("FijadaF"), fc.getBoolean("FijadaE"), fc.getBoolean("Simetria"));
    }

    /** El último ajuste prensado con esta categoría, o null. */
    @org.jetbrains.annotations.Nullable
    public DisenoEstampa ultimo() { return ultimoPorItem.get(categoria); }

    private boolean cargarUltimo() {
        DisenoEstampa d = ultimo();
        if (d == null || estado == Estado.PRENSANDO) return false;
        aplicarDiseno(d);
        return true;
    }

    /** El "+" del panel: gasta un diseño en blanco y da un ítem con el ajuste de las dos caras. */
    public boolean crearDisenoItem(PlayerEntity jugador, String nombre) {
        if (estado == Estado.PRENSANDO || !hayFijadas()) {
            jugador.sendMessage(Text.translatable("modamod.sublimadora.gui.diseno.sin_fijadas"), true);
            return false;
        }
        String n = nombre == null ? "" : nombre.trim();
        if (n.length() > 24) n = n.substring(0, 24);
        NbtCompound c = disenoANbt(disenoActual(n));
        c.putString("Item", net.minecraft.registry.Registries.ITEM.getId(categoria).toString());
        ItemStack item = new ItemStack(ModBlocks.DISENO_SUBLIMADO);
        item.set(com.modamod.item.ModamodComponents.DISENO_SUBLIMADO, c);
        item.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, (n.isEmpty()
                ? Text.translatable("modamod.estilista.disenos.defecto", Text.translatable(categoria.getTranslationKey()))
                : Text.literal(n)).styled(st -> st.withItalic(false)));
        // Directo a la lista de la máquina (2026-10-25, "que en la pestaña de diseños de todas las maquinas quede").
        if (!com.modamod.util.DisenosMaquina.guardar(jugador, disenosItem, DISENOS_ITEM_MAX, item)) return false;
        sincronizar();
        return true;
    }

    public boolean cargarDisenoItem(PlayerEntity jugador, int idx) {
        if (idx < 0 || idx >= disenosItem.size() || estado == Estado.PRENSANDO) return false;
        NbtCompound c = disenosItem.get(idx).get(com.modamod.item.ModamodComponents.DISENO_SUBLIMADO);
        if (c == null) return false;
        net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(c.getString("Item"));
        net.minecraft.item.Item item = id == null ? null : net.minecraft.registry.Registries.ITEM.get(id);
        if (item != null && item != net.minecraft.item.Items.AIR && remera.isEmpty()) irACategoria(item);
        aplicarDiseno(disenoDeNbt(c));
        jugador.sendMessage(Text.translatable("modamod.estilista.disenos.cargado", disenosItem.get(idx).getName()), true);
        return true;
    }

    public boolean sacarDisenoItem(PlayerEntity jugador, int idx) {
        if (idx < 0 || idx >= disenosItem.size()) return false;
        // Click derecho borra sin dar el ítem (2026-10-25, "cuando le das click secundario al diseño te da el molde sin
        // haber necesitado un diseño en blanco"): el ítem sale solo con Exportar.
        disenosItem.remove(idx);
        sincronizar();
        return true;
    }

    public boolean canPlayerUse(PlayerEntity player) {
        if (world == null || world.getBlockEntity(pos) != this) return false;
        return player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public Text getDisplayName() {
        return getCachedState().getBlock().getName();
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new SublimadoraScreenHandler(syncId, inv, this);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
        return this.pos;
    }

    /**
     * Todo lo que la maquina tiene adentro, para devolverlo al romperla.
     *
     * La tinta cargada no se devuelve: son cuatro tanques con hasta 16 dosis
     * cada uno y no hay item que represente una dosis suelta, asi que
     * reintegrarla seria inventar tintes de la nada.
     */
    public java.util.List<ItemStack> contenido() {
        java.util.List<ItemStack> todo = new java.util.ArrayList<>();
        if (!remera.isEmpty()) todo.add(remera);
        for (ItemStack f : fotos) if (!f.isEmpty()) todo.add(f);
        if (!salida.isEmpty()) todo.add(salida);
        // El almacén también (2026-09-28): antes sus fotos se perdían al romper la máquina.
        for (ItemStack f : almacen) if (!f.isEmpty()) todo.add(f);
        if (!mascaraSlot.isEmpty()) todo.add(mascaraSlot);
        return todo;
    }

    /** Saca lo que haya para retirar: la remera lista, o lo que este cargado. */
    public ItemStack retirar() {
        if (!salida.isEmpty()) {
            ItemStack out = salida;
            salida = ItemStack.EMPTY;
            estado = Estado.REPOSO;
            soltarCapas();
            sincronizar();
            return out;
        }
        for (int i = 0; i < 2; i++) {
            if (fotos[i].isEmpty()) continue;
            ItemStack out = fotos[i];
            fotos[i] = ItemStack.EMPTY;
            pendientes[i] = null;
            sincronizar();
            return out;
        }
        if (!remera.isEmpty()) {
            ItemStack out = remera;
            remera = ItemStack.EMPTY;
            soltarCapas();
            sincronizar();
            return out;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Se llama al CERRAR la tapa. Arranca el prensado si estan las tres
     * condiciones, y ahi mismo se consume la foto y una carga de cada tinta.
     */
    public boolean intentarPrensar() {
        if (estado != Estado.REPOSO) return false;
        if (remera.isEmpty()) return false;
        // Hallazgo H04: si no entran todas las capas, no se prensa (antes se cobraba y se descartaban las nuevas).
        if (capasDeMas() > 0) return false;
        // Las capas con máscara cuentan como una pasada más (2026-10-02).
        int caras = (caraActiva(0) ? 1 : 0) + (caraActiva(1) ? 1 : 0) + (capas.isEmpty() ? 0 : 1);
        if (caras == 0) return false;
        // Una dosis de cada color POR CARA: hacer las dos en una pasada
        // ahorra el ciclo, no la tinta.
        // Sublimadora creativa (2026-10-01): no pide ni gasta tinta ni papel.
        boolean gratis = com.modamod.util.MaquinaCreativa.es(this);
        if (!gratis) for (int i = 0; i < 4; i++) if (cargas[i] < caras) return false;
        // 1 papel por prensado (a las dos caras juntas, no una por cara —
        // es UNA hoja de papel de sublimacion, no una por lado) — a pedido
        // (2026-09-19, "usa papel y ya no consume la imagen").
        if (!gratis && papelCargado < 1) return false;

        if (!gratis) for (int i = 0; i < 4; i++) {
            cargas[i] -= caras;
            tinta[i] = cargas[i] / (float) CARGA_MAXIMA;
        }
        if (!gratis) papelCargado -= 1;
        sincronizarVistaTanques();
        // Las fotos YA NO se consumen al cerrar la tapa (2026-09-19): quedan
        // cargadas para reimprimir la misma cara sin volver a subirla.
        ultimoPorItem.put(categoria, disenoActual("★"));
        estado = Estado.PRENSANDO;
        progreso = 0;
        sincronizar();
        return true;
    }

    /** ¿Esta cara se estampa? Foto cargada y chincheta puesta. */
    private boolean caraActiva(int i) {
        if (i == Estampa.Cara.ESPALDA.ordinal() && (soloFrente(remera))) return false;
        return !fotos[i].isEmpty() && pendientes[i] != null && caraFijada[i];
    }

    /**
     * Botón Prensar de la GUI (2026-09-28, como Teñir en Tintes): baja la
     * tapa y arranca, igual que cerrarla a mano.
     *
     * @return null si arrancó; si no, el motivo.
     */
    @org.jetbrains.annotations.Nullable
    public Text prensarDesdeGui() {
        if (!intentarPrensar()) {
            Text motivo = queFalta();
            return motivo != null ? motivo : Text.translatable("modamod.sublimadora.aviso.cargar");
        }
        if (world != null && getCachedState().get(SublimadoraBlock.OPEN)) {
            world.setBlockState(pos, getCachedState().with(SublimadoraBlock.OPEN, false), net.minecraft.block.Block.NOTIFY_ALL);
            world.playSound(null, pos, SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.BLOCKS, 1.2f, 1.0f);
        }
        return null;
    }

    // ── la tapa sigue a la GUI (2026-10-04, "que la tapa se abra cuando entre a la gui y cuando salga se cierre y
    // arranque como las otras maquinas") ──
    private int guiAbiertas = 0;

    private void cerrarTapa() {
        if (world != null && getCachedState().get(SublimadoraBlock.OPEN)) {
            world.setBlockState(pos, getCachedState().with(SublimadoraBlock.OPEN, false), net.minecraft.block.Block.NOTIFY_ALL);
            world.playSound(null, pos, SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.BLOCKS, 1.2f, 1.0f);
        }
    }

    /** Servidor: alguien abrió la pantalla — la tapa se abre (si no está prensando). */
    public void alAbrirGui() {
        guiAbiertas++;
        if (world != null && !world.isClient && estado != Estado.PRENSANDO && !getCachedState().get(SublimadoraBlock.OPEN)) {
            world.setBlockState(pos, getCachedState().with(SublimadoraBlock.OPEN, true), net.minecraft.block.Block.NOTIFY_ALL);
            world.playSound(null, pos, SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 1.2f, 1.0f);
        }
    }

    /**
     * Servidor: se cerró la pantalla — al irse el último, intenta prensar y baja la tapa. Con una prenda lista
     * esperando en la Salida la deja abierta para poder retirarla.
     */
    public void alCerrarGui() {
        guiAbiertas = Math.max(0, guiAbiertas - 1);
        if (guiAbiertas > 0 || world == null || world.isClient || !salida.isEmpty()) return;
        // Ya no prensa al cerrar la pantalla (2026-10-15, "desactivar la activacion de las maquinas por cerrado de la gui"):
        // solo el botón Prensar o la remera cargada por arriba con diseño.
        cerrarTapa();
    }

    /** Nombres de los cuatro tanques, para poder decir cual quedo vacio. */
    private static final String[] NOMBRE_TINTA = { "cian", "magenta", "amarillo", "negro" };

    /**
     * Por que no puede prensar, o null si podria arrancar.
     *
     * Devuelve Text y no String para que el texto viva en los archivos de
     * idioma: antes armaba la frase en castellano aca adentro y el jugador en
     * ingles la recibia igual en castellano.
     *
     * Son dos avisos distintos y salen de a uno, en orden. Que falte la remera
     * o la foto es no saber usar la maquina, y se contesta explicando el
     * procedimiento entero; recien cuando eso esta resuelto tiene sentido
     * hablar de insumos. Mostrar los dos juntos hacia un parrafo que se lee
     * como una lista de reproches en vez de como el proximo paso.
     *
     * El de tinta nombra los colores en cero y no los cuatro tanques con sus
     * numeros: lo unico accionable es cual hay que ir a buscar.
     */
    @org.jetbrains.annotations.Nullable
    public Text queFalta() {
        if (estado == Estado.PRENSANDO) return Text.translatable("modamod.sublimadora.aviso.prensando");
        if (estado == Estado.LISTO) return Text.translatable("modamod.sublimadora.aviso.retirar");

        if (capasDeMas() > 0) {
            return Text.translatable("modamod.sublimadora.aviso.capas_de_mas",
                    RemeraItem.capasDe(remera).size(), capasDeMas());
        }
        if (remera.isEmpty() || (fotos[0].isEmpty() && fotos[1].isEmpty() && capas.isEmpty())) {
            return Text.translatable("modamod.sublimadora.aviso.cargar");
        }
        if (!caraActiva(0) && !caraActiva(1) && capas.isEmpty()) {
            return Text.translatable("modamod.sublimadora.aviso.sin_fijar");
        }
        if (papelCargado < 1 && !com.modamod.util.MaquinaCreativa.es(this)) {
            return Text.translatable("modamod.sublimadora.aviso.papel");
        }
        if (!hayTinta()) {
            MutableText colores = Text.empty();
            boolean primero = true;
            for (int i = 0; i < 4; i++) {
                if (cargas[i] > 0) continue;
                if (!primero) colores.append(", ");
                colores.append(Text.translatable("modamod.sublimadora.tinta." + NOMBRE_TINTA[i]));
                primero = false;
            }
            return Text.translatable("modamod.sublimadora.aviso.tinta", colores);
        }
        return null;
    }

    private void sincronizar() {
        markDirty();
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    /** Nivel a dibujar este frame, interpolado entre ticks. */
    public float getNivelInterpolado(int canal, float parcial) {
        return MathHelper.lerp(parcial, anterior[canal], mostrado[canal]);
    }

    public float getTinta(int canal) {
        return tinta[canal];
    }

    /** Llena un canal (cartucho nuevo). */
    public void setTinta(int canal, float valor) {
        tinta[canal] = MathHelper.clamp(valor, 0f, 1f);
        markDirty();
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    /** Consume tinta al prensar. Devuelve false si algun canal esta vacio. */
    public boolean consumir(float cantidad) {
        for (int i = 0; i < 4; i++) if (tinta[i] <= 0f) return false;
        for (int i = 0; i < 4; i++) tinta[i] = Math.max(0f, tinta[i] - cantidad);
        markDirty();
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        return true;
    }

    // ── persistencia y sincronizacion ────────────────────────────────
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        com.modamod.util.DisenosMaquina.escribir(nbt, blancoDiseno, registries);
        nbt.putBoolean("linea", linea);
        for (int i = 0; i < 4; i++) {
            nbt.putFloat(CLAVES[i], tinta[i]);
            nbt.putInt(CLAVES[i] + "Cargas", cargas[i]);
        }
        nbt.putInt("PapelCargado", papelCargado);
        nbt.putString("Estado", estado.name());
        nbt.putInt("Progreso", progreso);
        if (!remera.isEmpty()) nbt.put("Remera", remera.encode(registries));
        for (int i = 0; i < 2; i++) {
            if (!fotos[i].isEmpty()) nbt.put("Foto" + i, fotos[i].encode(registries));
            if (pendientes[i] != null) nbt.putUuid("Pendiente" + i, pendientes[i]);
            nbt.putFloat("Escala" + i, escalaBorrador[i]);
            nbt.putFloat("X" + i, xBorrador[i]);
            nbt.putFloat("Y" + i, yBorrador[i]);
            nbt.putFloat("Angulo" + i, anguloBorrador[i]);
        }
        nbt.putString("Seleccion", seleccion.name());
        nbt.putString("Categoria", net.minecraft.registry.Registries.ITEM.getId(categoria).toString());
        if (!salida.isEmpty()) nbt.put("Salida", salida.encode(registries));

        for (int i = 0; i < 2; i++) nbt.putBoolean("CaraFijada" + i, caraFijada[i]);
        nbt.putBoolean("Simetria", simetria);
        nbt.put("Juegos", juegosANbt(registries));
        net.minecraft.nbt.NbtList disenosNbt = new net.minecraft.nbt.NbtList();
        for (var entry : disenosPorItem.entrySet()) {
            String claveItem = net.minecraft.registry.Registries.ITEM.getId(entry.getKey()).toString();
            for (DisenoEstampa d : entry.getValue()) {
                EstampaFijada f = d.ajuste();
                NbtCompound fc = new NbtCompound();
                fc.putString("Item", claveItem);
                fc.putString("Nombre", d.nombre());
                fc.putFloat("EscalaF", f.escalaFrente());
                fc.putFloat("XF", f.xFrente());
                fc.putFloat("YF", f.yFrente());
                fc.putFloat("AnguloF", f.anguloFrente());
                fc.putFloat("EscalaE", f.escalaEspalda());
                fc.putFloat("XE", f.xEspalda());
                fc.putFloat("YE", f.yEspalda());
                fc.putFloat("AnguloE", f.anguloEspalda());
                fc.putBoolean("FijadaF", d.fijadaFrente());
                fc.putBoolean("FijadaE", d.fijadaEspalda());
                fc.putBoolean("Simetria", d.simetria());
                disenosNbt.add(fc);
            }
        }
        nbt.put("Disenos", disenosNbt);
        net.minecraft.inventory.Inventories.writeNbt(nbt, almacen, registries);
        net.minecraft.nbt.NbtList lf = new net.minecraft.nbt.NbtList();
        for (ItemStack s : fotosBolsa) lf.add(s.encode(registries));
        nbt.put("BolsaFotos", lf);
        net.minecraft.nbt.NbtList lm = new net.minecraft.nbt.NbtList();
        for (ItemStack s : mascarasBolsa) lm.add(s.encode(registries));
        nbt.put("BolsaMascaras", lm);
        net.minecraft.nbt.NbtList ld = new net.minecraft.nbt.NbtList();
        for (ItemStack s : disenosItem) ld.add(s.encode(registries));
        nbt.put("DisenosItem", ld);
        net.minecraft.nbt.NbtList lu = new net.minecraft.nbt.NbtList();
        for (var e : ultimoPorItem.entrySet()) {
            NbtCompound c = disenoANbt(e.getValue());
            c.putString("Item", net.minecraft.registry.Registries.ITEM.getId(e.getKey()).toString());
            lu.add(c);
        }
        nbt.put("Ultimos", lu);

        // Máscaras (2026-10-02).
        if (!mascaraSlot.isEmpty()) nbt.put("MascaraItem", mascaraSlot.encode(registries));
        for (int i = 0; i < 2; i++) {
            nbt.putFloat("MascaraEscala" + i, mascaraEscala[i]);
            nbt.putFloat("MascaraX" + i, mascaraX[i]);
            nbt.putFloat("MascaraY" + i, mascaraY[i]);
            nbt.putFloat("MascaraAngulo" + i, mascaraAngulo[i]);
        }
        nbt.putBoolean("EditarMascara", editarMascara);
        nbt.putInt("CapaElegida", capaElegida);
        CapaEstampa.CODEC.listOf().encodeStart(registries.getOps(net.minecraft.nbt.NbtOps.INSTANCE), capas)
                .result().ifPresent(e -> nbt.put("CapasEstampa", e));
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        com.modamod.util.DisenosMaquina.leer(nbt, blancoDiseno, registries);
        linea = nbt.getBoolean("linea");
        for (int i = 0; i < 4; i++) {
            if (nbt.contains(CLAVES[i] + "Cargas")) {
                cargas[i] = nbt.getInt(CLAVES[i] + "Cargas");
                tinta[i] = cargas[i] / (float) CARGA_MAXIMA;
            } else if (nbt.contains(CLAVES[i])) {
                // Mundos de antes de que la tinta se contara por cargas.
                tinta[i] = nbt.getFloat(CLAVES[i]);
                cargas[i] = Math.round(tinta[i] * CARGA_MAXIMA);
            }
            mostrado[i] = tinta[i];
            anterior[i] = tinta[i];
        }
        papelCargado = MathHelper.clamp(nbt.getInt("PapelCargado"), 0, CARGA_MAXIMA);
        sincronizarVistaTanques();
        estado = nbt.contains("Estado") ? Estado.valueOf(nbt.getString("Estado")) : Estado.REPOSO;
        progreso = nbt.getInt("Progreso");
        remera = nbt.contains("Remera") ? ItemStack.fromNbtOrEmpty(registries, nbt.getCompound("Remera")) : ItemStack.EMPTY;
        for (int i = 0; i < 2; i++) {
            fotos[i] = nbt.contains("Foto" + i)
                    ? ItemStack.fromNbtOrEmpty(registries, nbt.getCompound("Foto" + i))
                    : ItemStack.EMPTY;
            pendientes[i] = nbt.containsUuid("Pendiente" + i) ? nbt.getUuid("Pendiente" + i) : null;
            if (nbt.contains("Escala" + i)) {
                escalaBorrador[i] = nbt.getFloat("Escala" + i);
                xBorrador[i] = nbt.getFloat("X" + i);
                yBorrador[i] = nbt.getFloat("Y" + i);
                // Angulo se agregó después (2026-09-20): ausente en
                // guardados viejos, default 0 (como venía la foto).
                anguloBorrador[i] = nbt.contains("Angulo" + i) ? nbt.getFloat("Angulo" + i) : 0f;
            } else if (nbt.contains("Modo" + i)) {
                // Compatibilidad con guardados de antes del control libre
                // (2026-09-19, "sacamos los controles del frente"): los 3
                // presets viejos (Logo/Centrada/Completo) pasan a ser su
                // escala/posicion equivalente.
                Estampa.Modo modoViejo = Estampa.Modo.valueOf(nbt.getString("Modo" + i));
                escalaBorrador[i] = modoViejo.escala;
                xBorrador[i] = modoViejo.x;
                yBorrador[i] = modoViejo.y;
                anguloBorrador[i] = 0f;
            }
        }
        if (nbt.contains("Seleccion")) seleccion = Estampa.Cara.valueOf(nbt.getString("Seleccion"));
        if (nbt.contains("Categoria")) {
            net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(nbt.getString("Categoria"));
            net.minecraft.item.Item item = id == null ? null : net.minecraft.registry.Registries.ITEM.get(id);
            if (item != null && item != net.minecraft.item.Items.AIR) categoria = item;
        }
        salida = nbt.contains("Salida") ? ItemStack.fromNbtOrEmpty(registries, nbt.getCompound("Salida")) : ItemStack.EMPTY;

        // Sin la clave (mundo de antes de las chinchetas, 2026-09-28): las
        // dos caras fijadas, que es como se comportaba — se estampaba
        // toda cara con foto.
        for (int i = 0; i < 2; i++) caraFijada[i] = !nbt.contains("CaraFijada" + i) || nbt.getBoolean("CaraFijada" + i);
        simetria = nbt.getBoolean("Simetria");
        juegosDeNbt(nbt, registries);
        disenosPorItem.clear();
        // "EstampaFijadas" = las fijadas sin nombre de antes: se leen como diseños "#n".
        boolean viejas = !nbt.contains("Disenos");
        net.minecraft.nbt.NbtList disenosNbt = nbt.getList(viejas ? "EstampaFijadas" : "Disenos", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < disenosNbt.size(); i++) {
            NbtCompound fc = disenosNbt.getCompound(i);
            net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(fc.getString("Item"));
            net.minecraft.item.Item item = id == null ? null : net.minecraft.registry.Registries.ITEM.get(id);
            if (item == null || item == net.minecraft.item.Items.AIR) continue;
            java.util.List<DisenoEstampa> lista = disenosPorItem.computeIfAbsent(item, k -> new java.util.ArrayList<>());
            if (lista.size() >= DISENOS_MAXIMO) continue;
            String nombre = viejas || fc.getString("Nombre").isEmpty() ? "#" + (lista.size() + 1) : fc.getString("Nombre");
            lista.add(new DisenoEstampa(nombre, new EstampaFijada(
                    fc.getFloat("EscalaF"), fc.getFloat("XF"), fc.getFloat("YF"), fc.getFloat("AnguloF"),
                    fc.getFloat("EscalaE"), fc.getFloat("XE"), fc.getFloat("YE"), fc.getFloat("AnguloE")),
                    viejas || fc.getBoolean("FijadaF"), viejas || fc.getBoolean("FijadaE"), fc.getBoolean("Simetria")));
        }
        almacen.clear();
        net.minecraft.inventory.Inventories.readNbt(nbt, almacen, registries);
        fotosBolsa.clear();
        net.minecraft.nbt.NbtList lf = nbt.getList("BolsaFotos", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < lf.size(); i++) { ItemStack s = ItemStack.fromNbtOrEmpty(registries, lf.getCompound(i)); if (!s.isEmpty()) fotosBolsa.add(s); }
        mascarasBolsa.clear();
        net.minecraft.nbt.NbtList lm = nbt.getList("BolsaMascaras", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < lm.size(); i++) { ItemStack s = ItemStack.fromNbtOrEmpty(registries, lm.getCompound(i)); if (!s.isEmpty()) mascarasBolsa.add(s); }
        disenosItem.clear();
        net.minecraft.nbt.NbtList ld = nbt.getList("DisenosItem", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < ld.size(); i++) { ItemStack s = ItemStack.fromNbtOrEmpty(registries, ld.getCompound(i)); if (!s.isEmpty()) disenosItem.add(s); }
        ultimoPorItem.clear();
        net.minecraft.nbt.NbtList lu = nbt.getList("Ultimos", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < lu.size(); i++) {
            NbtCompound c = lu.getCompound(i);
            net.minecraft.util.Identifier uid = net.minecraft.util.Identifier.tryParse(c.getString("Item"));
            net.minecraft.item.Item it = uid == null ? null : net.minecraft.registry.Registries.ITEM.get(uid);
            if (it != null && it != net.minecraft.item.Items.AIR) ultimoPorItem.put(it, disenoDeNbt(c));
        }

        mascaraSlot = nbt.contains("MascaraItem")
                ? ItemStack.fromNbtOrEmpty(registries, nbt.getCompound("MascaraItem")) : ItemStack.EMPTY;
        for (int i = 0; i < 2; i++) {
            mascaraEscala[i] = nbt.contains("MascaraEscala" + i) ? nbt.getFloat("MascaraEscala" + i) : Mascara.ESCALA_DEFECTO;
            mascaraX[i] = nbt.getFloat("MascaraX" + i);
            mascaraY[i] = nbt.getFloat("MascaraY" + i);
            mascaraAngulo[i] = nbt.getFloat("MascaraAngulo" + i);
        }
        editarMascara = nbt.getBoolean("EditarMascara");
        capas.clear();
        if (nbt.contains("CapasEstampa")) {
            CapaEstampa.CODEC.listOf().parse(registries.getOps(net.minecraft.nbt.NbtOps.INSTANCE), nbt.get("CapasEstampa"))
                    .result().ifPresent(l -> capas.addAll(l.subList(0, Math.min(CapaEstampa.MAXIMO, l.size()))));
        }
        capaElegida = nbt.contains("CapaElegida") ? Math.min(nbt.getInt("CapaElegida"), capas.size() - 1) : -1;
    }

    // ── la tinta viaja adentro del item ──────────────────────────────
    //
    // collectImplicit/applyImplicit es el mismo mecanismo con el que una caja
    // de shulker se lleva su contenido: la loot table copia el componente al
    // item al romper, y al colocar el bloque vuelve al block entity.

    @Override
    protected void addComponents(net.minecraft.component.ComponentMap.Builder builder) {
        super.addComponents(builder);
        java.util.List<Integer> lista = new java.util.ArrayList<>(4);
        for (int i = 0; i < 4; i++) lista.add(cargas[i]);
        builder.add(ModItems.CARGAS, lista);
        builder.add(ModItems.PAPEL_CARGADO, papelCargado);
    }

    @Override
    protected void readComponents(BlockEntity.ComponentsAccess componentes) {
        super.readComponents(componentes);
        java.util.List<Integer> lista = componentes.get(ModItems.CARGAS);
        if (lista != null) {
            for (int i = 0; i < 4 && i < lista.size(); i++) {
                cargas[i] = MathHelper.clamp(lista.get(i), 0, CARGA_MAXIMA);
                tinta[i] = cargas[i] / (float) CARGA_MAXIMA;
                mostrado[i] = tinta[i];
                anterior[i] = tinta[i];
            }
        }
        Integer papel = componentes.get(ModItems.PAPEL_CARGADO);
        if (papel != null) papelCargado = MathHelper.clamp(papel, 0, CARGA_MAXIMA);
        sincronizarVistaTanques();
    }

    /**
     * Sin esto el item quedaria con las cargas DOS veces -en el componente y
     * en el NBT del block entity copiado- y dos maquinas con la misma tinta
     * no apilarian entre si.
     */
    @Override
    public void removeFromCopiedStackNbt(NbtCompound nbt) {
        super.removeFromCopiedStackNbt(nbt);
        for (String clave : CLAVES) nbt.remove(clave + "Cargas");
        nbt.remove("PapelCargado");
    }

    /** El cliente necesita los niveles para dibujar el display. */
    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    @Override
    public net.minecraft.network.packet.Packet<net.minecraft.network.listener.ClientPlayPacketListener> toUpdatePacket() {
        return net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket.create(this);
    }
}
