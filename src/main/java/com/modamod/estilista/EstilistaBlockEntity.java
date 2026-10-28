package com.modamod.estilista;

import com.modamod.estilado.EstiladoBlockEntity;
import com.modamod.util.InventarioUtil;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * La Estilista automática, etapa 1 (2026-10-05, "rearmemos la estiladora como un bloque de la cadena"): una máquina de
 * la línea textil con la misma base que la Modeladora — entra la prenda por arriba o por la izquierda (cinta, tolva o
 * la salida de la máquina de al lado), sale por la derecha — y el pórtico de la tapa. Todavía NO aplica ningún diseño
 * (eso es la etapa 2): una prenda que llega por la cadena pasa de largo y a mano el botón Probar corre la animación.
 */
public class EstilistaBlockEntity extends BlockEntity implements SidedInventory, com.modamod.util.ConSalida,
        ExtendedScreenHandlerFactory<BlockPos>, GeoBlockEntity, com.modamod.util.MaquinaCreativa.Cargable , com.modamod.util.LimiteEntrada {

    /**
     * El buzón (2026-10-05, "los insumos pueden cargar invisibles") es un casillero que nadie ve: lo que entra por
     * tolva o cinta (hilo y cuero) se convierte al toque en los contadores. El almacén es visible: guarda plantillas,
     * retazos y los objetos que gastan los apliques de objeto.
     */
    public static final int SLOT_PRENDA = 0, SLOT_SALIDA = 1, SLOT_BUZON = 2, SLOT_ALMACEN = 3, ALMACEN = 18,
            TAMANO = SLOT_ALMACEN + ALMACEN;
    /**
     * El buzón del cuero (2026-10-24, "la estiladora no esta incorporando cueros por atras"): antes hilo y cuero
     * compartían {@link #SLOT_BUZON} y, con el hilo lleno, el hilo que sobraba lo trababa y el cuero se quedaba en la
     * cinta. Va después del almacén para no correr los casilleros guardados; {@link #SLOT_BUZON} queda para el hilo.
     */
    public static final int SLOT_BUZON_CUERO = TAMANO;
    /** Cuánto rinde cada ítem (2026-10-05, "rinde más por ítem") y cuánto aguanta cada contador. */
    public static final int APLIQUES_POR_HILO = 4, CORREAS_POR_CUERO = 2, TOPE_CONTADOR = 64;
    /** Cuántos diseños (uno por tipo de prenda) guarda la máquina. */
    public static final int MAX_DISENOS = 64;
    /** Diseños con nombre por tipo de prenda (2026-10-08, "poder guardar varios pero principalmente el último por prenda"). */
    public static final int MAX_NOMBRADOS = 8;
    /** Cargar y borrar el diseño con nombre número k (0..7) del tipo de la prenda de referencia. */
    public static final int BTN_DISENO_CARGAR_BASE = 640, BTN_DISENO_BORRAR_BASE = 660;
    /** Lo que dura el trabajo del pórtico: los 13 s de la animación. */
    public static final int TICKS_PROCESO = 260;
    /** Botones: aplicar el diseño a la prenda de la entrada, fijar esa prenda como muestra, olvidar el diseño de su tipo. */
    public static final int BTN_APLICAR = 600, BTN_FIJAR = 601, BTN_BORRAR = 602, BTN_BORRAR_TODO = 607, BTN_CARGAR_HILO = 603, BTN_CARGAR_CUERO = 604,
            BTN_CATEGORIA_ATRAS = 605, BTN_CATEGORIA_ADELANTE = 606;

    /** Lo que sale de intentar aplicar el diseño: ver {@link #planear}. */
    public enum Resultado { OK, SIN_DISENO, FALTA }

    /** Lo que copia un diseño de la prenda de muestra (lo demás de la prenda no se toca). */
    private static final java.util.List<net.minecraft.component.ComponentType<?>> COMPONENTES_DE_DISENO = java.util.List.of(
            com.modamod.item.ModamodComponents.APLIQUES, com.modamod.item.ModamodComponents.CORREAS,
            com.modamod.item.ModamodComponents.TEXTURA_TELA, com.modamod.item.ModamodComponents.ACABADO_TRIM,
            com.modamod.item.ModamodComponents.ACABADO_MATERIAL,
            com.modamod.item.ModamodComponents.COLORES_SOMBRERO, com.modamod.item.ModamodComponents.PATRONES_SOMBRERO,
            com.modamod.item.ModamodComponents.COLORES_BANDA, com.modamod.item.ModamodComponents.PATRONES_BANDA,
            com.modamod.item.ModamodComponents.COLORES_BORCEGOS, com.modamod.item.ModamodComponents.PATRONES_BORCEGOS,
            com.modamod.item.ModamodComponents.COLORES_BORCEGOS_IZQ, com.modamod.item.ModamodComponents.PATRONES_BORCEGOS_IZQ);

    /** Los tics del trabajo (de la animación `trabajo`) en que el pórtico arranca un recorrido: horizontal o de subida y bajada. */
    private static final int[] TICS_HORIZONTAL = {0, 18, 42, 52, 70, 94, 104, 122, 146, 156, 174, 198};
    private static final int[] TICS_VERTICAL = {10, 14, 31, 36, 62, 66, 83, 88, 114, 118, 135, 140, 166, 170, 187, 192};

    public enum Estado { REPOSO, PROCESANDO, LISTO }

    private static final RawAnimation TRABAJO = RawAnimation.begin().thenLoop("animation.estilista.trabajo");
    private static final RawAnimation QUIETO = RawAnimation.begin().thenLoop("animation.estilista.quieto");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(TAMANO + 1, ItemStack.EMPTY);
    private Estado estado = Estado.REPOSO;
    private int progreso;
    /** La prenda de la entrada llegó por la cadena (arranca sola apenas haya insumos) o la puso alguien a mano (espera los botones). */
    private boolean deCadena;
    /** Los contadores: cuántos apliques y cuántas correas alcanzan con lo cargado (unidades, no ítems). */
    private int hilo, cuero;
    /** Un diseño por tipo de prenda: el ítem de la muestra con solo lo que se copia (apliques, correas, colores). */
    private final DefaultedList<ItemStack> disenos = DefaultedList.ofSize(MAX_DISENOS, ItemStack.EMPTY);

    /**
     * El editor (2026-10-05, etapa 3: "no quiero perder el tamaño del visualizador"): una Mesa de estilado interna, fuera
     * del mundo, cuya prenda es la MUESTRA del diseño. La pantalla y los clicks de la vista 3D son los de la Mesa.
     */
    private final EstiladoBlockEntity editor;

    public EstilistaBlockEntity(BlockPos pos, BlockState state) {
        super(EstilistaMod.ESTILISTA_BLOCK_ENTITY, pos, state);
        editor = new EstiladoBlockEntity(pos, com.modamod.estilado.EstiladoMod.ESTILADO_BLOCK.getDefaultState());
        editor.anfitrionar(this);
    }

    public EstiladoBlockEntity editor() { return editor; }

    // ── Las tres bolsas (2026-10-12, "bolsa de apliques, bolsa de retazos y bolsa de moldes"): referencias que no se
    // gastan (el que se gasta es el hilo). Elegir una entrada pone una copia en el casillero interno del editor, que
    // la pantalla ya no muestra. Lo que sí se gasta (objetos, moldes de trim y materiales) queda en los Insumos.
    public static final int BOLSA_APLIQUES = 0, BOLSA_MOLDES = 1, BOLSA_RETAZOS = 2, BOLSA_MAXIMO = 200;
    private boolean creativaCargada = false;
    /**
     * Memoria por pestaña (2026-10-13, "al ser memorias diferentes guarden diferente"): lo elegido en Aplique, Correa y
     * Acabado (molde, retazo, material) no se comparte; al cambiar de pestaña se guarda lo de la que sale y se carga lo
     * de la que entra.
     */
    private final ItemStack[][] memoriaPestana = new ItemStack[][] {
            {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY}, {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY},
            {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY}};

    // ── diseños como ítems (2026-10-13, "instaura el mismo sistema de guardado de diseño") ──
    public static final int DISENOS_ITEM_MAXIMO = 200;
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

    public java.util.List<ItemStack> disenosItem() { return java.util.Collections.unmodifiableList(disenosItem); }

    public static boolean esDeposito(ItemStack s) {
        return bolsaDe(s) >= 0 || s.getItem() instanceof DisenoEstiloItem;
    }

    public boolean absorberDiseno(ItemStack pila) {
        if (world == null || world.isClient || !(pila.getItem() instanceof DisenoEstiloItem)) return false;
        if (com.modamod.util.DisenosMaquina.yaEsta(disenosItem, pila)) return true;
        if (disenosItem.size() >= DISENOS_ITEM_MAXIMO) return false;
        disenosItem.add(pila.copyWithCount(1));
        sincronizar();
        return true;
    }

    /** El "+" del panel: gasta un diseño en blanco y da un ítem con lo que lleva la muestra. */
    public boolean crearDisenoItem(PlayerEntity jugador, String nombre) {
        ItemStack m = editor.getStack(EstiladoBlockEntity.SLOT_PRENDA);
        if (world == null || m.isEmpty() || !llevaDiseno(m)) {
            jugador.sendMessage(Text.translatable("modamod.estilista.muestra_vacia"), true);
            return false;
        }
        ItemStack proto = protoDe(m);
        net.minecraft.nbt.NbtCompound c = new net.minecraft.nbt.NbtCompound();
        c.put("Diseno", proto.encode(world.getRegistryManager()));
        c.putString("Prenda", net.minecraft.registry.Registries.ITEM.getId(m.getItem()).toString());
        c.putString("Resumen", resumen(proto).getString());
        ItemStack item = new ItemStack(EstilistaMod.DISENO_ESTILO);
        item.set(com.modamod.item.ModamodComponents.DISENO_ESTILO, c);
        String n = nombre == null ? "" : nombre.trim();
        if (n.length() > 24) n = n.substring(0, 24);
        item.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, (n.isEmpty()
                ? Text.translatable("modamod.estilista.disenos.defecto", Text.translatable(m.getItem().getTranslationKey()))
                : Text.literal(n)).styled(st -> st.withItalic(false)));
        // Directo a la lista de la máquina (2026-10-25, "que en la pestaña de diseños de todas las maquinas quede").
        if (!com.modamod.util.DisenosMaquina.guardar(jugador, disenosItem, DISENOS_ITEM_MAXIMO, item)) return false;
        sincronizar();
        return true;
    }

    /** Lleva el diseño de la bolsa de diseños a la muestra (si cambia de tipo y la Entrada está libre, también cambia la muestra). */
    public boolean cargarDisenoItem(PlayerEntity jugador, int idx) {
        if (world == null || idx < 0 || idx >= disenosItem.size()) return false;
        net.minecraft.nbt.NbtCompound c = disenosItem.get(idx).get(com.modamod.item.ModamodComponents.DISENO_ESTILO);
        if (c == null) return false;
        ItemStack d = ItemStack.fromNbtOrEmpty(world.getRegistryManager(), c.getCompound("Diseno"));
        if (d.isEmpty()) return false;
        ItemStack m = editor.getStack(EstiladoBlockEntity.SLOT_PRENDA);
        if (m.isEmpty() || m.getItem() != d.getItem()) {
            ItemStack entrada = items.get(SLOT_PRENDA);
            if (!entrada.isEmpty() && entrada.getItem() != d.getItem()) {
                jugador.sendMessage(Text.translatable("modamod.estilista.disenos.otro_tipo"), true);
                return false;
            }
            m = new ItemStack(d.getItem());
            editor.setStack(EstiladoBlockEntity.SLOT_PRENDA, m);
        }
        copiarDiseno(d, m);
        editor.markDirty();
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

    /** Chincheta (2026-10-13, "para que no se cambie"): lo elegido en un casillero de una pestaña queda fijo hasta soltarlo. */
    private final boolean[][] fijados = new boolean[3][3];
    private int pestanaActual = 0;

    public boolean fijado(int pestana, int k) {
        return pestana >= 0 && pestana <= 2 && k >= 0 && k <= 2 && fijados[pestana][k];
    }

    /** Casillero del editor (0 molde, 1 retazo, 2 material) que cambia esta elección. */
    private boolean bloqueado(int k) {
        return fijados[pestanaActual][k];
    }

    public boolean alternarFijado(int k) {
        if (k < 0 || k > 2) return false;
        fijados[pestanaActual][k] = !fijados[pestanaActual][k];
        sincronizar();
        return true;
    }

    public boolean cambiarPestana(int antes, int ahora) {
        if (antes < 0 || antes > 2 || ahora < 0 || ahora > 2 || antes == ahora) return false;
        pestanaActual = ahora;
        int[] slots = {EstiladoBlockEntity.SLOT_MOLDE, EstiladoBlockEntity.SLOT_RETAZO, EstiladoBlockEntity.SLOT_MATERIAL};
        for (int k = 0; k < 3; k++) memoriaPestana[antes][k] = editor.getStack(slots[k]).copy();
        for (int k = 0; k < 3; k++) editor.setStack(slots[k], memoriaPestana[ahora][k].copy());
        markDirty();
        return true;
    }
    private final java.util.List<ItemStack> bolsaApliques = new java.util.ArrayList<>();
    private final java.util.List<ItemStack> bolsaMoldes = new java.util.ArrayList<>();
    private final java.util.List<ItemStack> bolsaRetazos = new java.util.ArrayList<>();

    /** A qué bolsa va un ítem, o -1 si no es de ninguna. */
    public static int bolsaDe(ItemStack s) {
        net.minecraft.item.Item i = s.getItem();
        if (i instanceof com.modamod.aplique.MoldeApliqueItem || i instanceof com.modamod.aplique.MoldeApliquePersonalizadoItem) return BOLSA_APLIQUES;
        if (i instanceof com.modamod.correa.MoldeCorreaItem || i instanceof com.modamod.item.MoldeTexturaItem) return BOLSA_MOLDES;
        if (i instanceof com.modamod.aplique.RetazoApliqueItem || i instanceof com.modamod.item.MuestraColorItem) return BOLSA_RETAZOS;
        return -1;
    }

    private java.util.List<ItemStack> lista(int tipo) {
        return tipo == BOLSA_APLIQUES ? bolsaApliques : tipo == BOLSA_MOLDES ? bolsaMoldes : bolsaRetazos;
    }

    public java.util.List<ItemStack> bolsa(int tipo) {
        return tipo < 0 || tipo > 2 ? java.util.List.of() : java.util.Collections.unmodifiableList(lista(tipo));
    }

    /** Suma un ítem a su bolsa (servidor); uno igual ya guardado cuenta como absorbido. Falso si no es de ninguna o está llena. */
    public boolean absorberEnBolsa(ItemStack pila) {
        if (world != null && world.isClient) return false;
        int t = bolsaDe(pila);
        if (t < 0) return false;
        java.util.List<ItemStack> l = lista(t);
        for (ItemStack e : l) if (ItemStack.areItemsAndComponentsEqual(e, pila)) return true;
        if (l.size() >= BOLSA_MAXIMO) return false;
        l.add(pila.copyWithCount(1));
        sincronizar();
        return true;
    }

    /** Pone la entrada {@code indice} de la bolsa en el casillero interno del editor (plantilla o retazo), sin gastarla. */
    public boolean elegirDeBolsa(int tipo, int indice) {
        if (tipo < 0 || tipo > 2) return false;
        // Un objeto olvidado en su casillero gana sobre el molde (2026-10-14, "ignora el aplique y pone tridentes").
        if (tipo == BOLSA_APLIQUES) editor.setStack(EstiladoBlockEntity.SLOT_OBJETO, ItemStack.EMPTY);
        if (bloqueado(tipo == BOLSA_RETAZOS ? 1 : 0)) return false;
        java.util.List<ItemStack> l = lista(tipo);
        if (indice < 0 || indice >= l.size()) return false;
        editor.setStack(tipo == BOLSA_RETAZOS ? EstiladoBlockEntity.SLOT_RETAZO : EstiladoBlockEntity.SLOT_MOLDE, l.get(indice).copyWithCount(1));
        return true;
    }

    /** Un molde de trim de la lista de posibles (por el ítem que lo representa): queda elegido en el editor. */
    public boolean elegirTrim(net.minecraft.util.Identifier id) {
        ItemStack s = new ItemStack(net.minecraft.registry.Registries.ITEM.get(id));
        if (s.isEmpty() || editor.patronDeTrim(s) == null || bloqueado(0)) return false;
        editor.setStack(EstiladoBlockEntity.SLOT_MOLDE, s);
        return true;
    }

    /** Un material de trim de la lista de posibles. */
    public boolean elegirMaterial(net.minecraft.util.Identifier id) {
        ItemStack s = new ItemStack(net.minecraft.registry.Registries.ITEM.get(id));
        if (s.isEmpty() || editor.materialDe(s) == null || bloqueado(2)) return false;
        editor.setStack(EstiladoBlockEntity.SLOT_MATERIAL, s);
        return true;
    }

    public boolean sacarDeBolsa(PlayerEntity jugador, int tipo, int indice) {
        if (tipo < 0 || tipo > 2) return false;
        java.util.List<ItemStack> l = lista(tipo);
        if (indice < 0 || indice >= l.size()) return false;
        jugador.getInventory().offerOrDrop(l.remove(indice));
        sincronizar();
        return true;
    }

    /** Lo que haya en los Insumos que sea de una bolsa (de antes, o soltado a mano) se pasa a su bolsa. */
    private void drenarAlmacen() {
        boolean cambio = false;
        for (int i = SLOT_ALMACEN; i < TAMANO; i++) {
            ItemStack s = items.get(i);
            if (s.getItem() instanceof DisenoEstiloItem) {
                if (absorberDiseno(s)) { items.set(i, ItemStack.EMPTY); cambio = true; }
                continue;
            }
            if (s.isEmpty() || bolsaDe(s) < 0) continue;
            if (absorberEnBolsa(s)) { items.set(i, ItemStack.EMPTY); cambio = true; }
        }
        if (cambio) sincronizar();
    }

    @Override
    public void setWorld(World world) {
        super.setWorld(world);
        editor.setWorld(world);
    }

    /** Lo llama el editor cuando cambia algo: se guarda y se manda al cliente con el resto de la máquina. */
    public void sincronizarEditor() {
        if (world != null && !world.isClient && !autoguardando) {
            autoguardando = true;
            try { autoGuardar(); } finally { autoguardando = false; }
        }
        sincronizar();
    }

    // ── estado visible ──

    public Estado estado() { return estado; }

    /** Trabajando de verdad: la GUI se cierra y no se toca (2026-10-08). */
    public boolean trabajando() { return estado == Estado.PROCESANDO; }
    public int progreso() { return progreso; }

    public ItemStack prendaVisible() {
        return !items.get(SLOT_PRENDA).isEmpty() ? items.get(SLOT_PRENDA) : items.get(SLOT_SALIDA);
    }

    private ItemStack ultimaVistaPrevia = ItemStack.EMPTY;

    /** La prenda de la pantallita: la última que hubo (salida o entrada), así no se apaga entre prendas. */
    public ItemStack vistaPreviaPersistente() {
        ItemStack actual = !items.get(SLOT_SALIDA).isEmpty() ? items.get(SLOT_SALIDA) : items.get(SLOT_PRENDA);
        if (!actual.isEmpty()) ultimaVistaPrevia = actual;
        return ultimaVistaPrevia;
    }

    public ItemStack salidaVisible() { return items.get(SLOT_SALIDA); }

    // ── diseños (etapa 2, 2026-10-05: "la primera que entre o la del input, vamos con 13 s, que gaste y recupere") ──

    /** El diseño guardado para el tipo de esa prenda, o null. */
    @Nullable
    public ItemStack diseno(ItemStack prenda) {
        if (prenda.isEmpty()) return null;
        for (ItemStack d : disenos) if (!d.isEmpty() && d.getItem() == prenda.getItem() && !esNombrado(d)) return d;
        return null;
    }

    public static boolean esNombrado(ItemStack d) {
        return d.contains(net.minecraft.component.DataComponentTypes.CUSTOM_NAME);
    }

    /** Los diseños con nombre de ese tipo de prenda, en el orden en que se guardaron. */
    public java.util.List<ItemStack> nombrados(@Nullable net.minecraft.item.Item tipo) {
        java.util.List<ItemStack> l = new java.util.ArrayList<>();
        if (tipo == null) return l;
        for (ItemStack d : disenos) if (!d.isEmpty() && d.getItem() == tipo && esNombrado(d)) l.add(d);
        return l;
    }

    /** El tipo de prenda al que se refieren los diseños: el de la entrada o, si no hay, el de la muestra del editor. */
    @Nullable
    public net.minecraft.item.Item tipoDeReferencia() {
        ItemStack ref = !items.get(SLOT_PRENDA).isEmpty() ? items.get(SLOT_PRENDA) : editor.getStack(EstiladoBlockEntity.SLOT_PRENDA);
        return ref.isEmpty() ? null : ref.getItem();
    }

    private static boolean llevaDiseno(ItemStack s) {
        for (net.minecraft.component.ComponentType<?> t : COMPONENTES_DE_DISENO) if (s.get(t) != null) return true;
        return false;
    }

    private static ItemStack protoDe(ItemStack muestra) {
        ItemStack proto = new ItemStack(muestra.getItem());
        copiarDiseno(muestra, proto);
        return proto;
    }

    // ── el último por prenda (automático) y los diseños con nombre (2026-10-08) ──

    private boolean autoguardando;
    @Nullable private net.minecraft.item.Item muestraAnterior;

    /**
     * Cada vez que cambia el editor: si apareció una muestra nueva sin diseño, se le carga el último de su tipo; si no,
     * lo que lleva la muestra pasa a ser el último de su tipo (si no lleva nada, se olvida).
     */
    private void autoGuardar() {
        ItemStack m = editor.getStack(EstiladoBlockEntity.SLOT_PRENDA);
        net.minecraft.item.Item tipo = m.isEmpty() ? null : m.getItem();
        boolean cambioTipo = tipo != muestraAnterior;
        muestraAnterior = tipo;
        if (tipo == null) return;
        if (cambioTipo && !llevaDiseno(m)) {
            ItemStack ultimo = diseno(m);
            if (ultimo != null) copiarDiseno(ultimo, m);
            return;
        }
        escribirUltimo(m);
    }

    private void escribirUltimo(ItemStack muestra) {
        ItemStack proto = protoDe(muestra);
        int libre = -1, propio = -1;
        for (int i = 0; i < MAX_DISENOS; i++) {
            ItemStack d = disenos.get(i);
            if (d.isEmpty()) { if (libre < 0) libre = i; }
            else if (d.getItem() == muestra.getItem() && !esNombrado(d)) propio = i;
        }
        if (!llevaDiseno(proto)) {
            if (propio >= 0) disenos.set(propio, ItemStack.EMPTY);
            return;
        }
        int donde = propio >= 0 ? propio : libre;
        if (donde >= 0) disenos.set(donde, proto);
    }

    /** Guarda lo que lleva la muestra del editor con ese nombre (el mismo nombre en el mismo tipo lo reemplaza). */
    public boolean guardarNombrado(PlayerEntity jugador, String nombre) {
        ItemStack m = editor.getStack(EstiladoBlockEntity.SLOT_PRENDA);
        if (m.isEmpty() || !llevaDiseno(m)) {
            jugador.sendMessage(Text.translatable("modamod.estilista.muestra_vacia"), true);
            return false;
        }
        String n = nombre == null ? "" : nombre.trim();
        if (n.isEmpty()) n = Text.translatable("modamod.estilista.disenos.nombre_defecto", nombrados(m.getItem()).size() + 1).getString();
        ItemStack proto = protoDe(m);
        proto.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(n));
        int libre = -1, igual = -1, cuantos = 0;
        for (int i = 0; i < MAX_DISENOS; i++) {
            ItemStack d = disenos.get(i);
            if (d.isEmpty()) { if (libre < 0) libre = i; continue; }
            if (d.getItem() != m.getItem() || !esNombrado(d)) continue;
            cuantos++;
            if (d.getName().getString().equals(n)) igual = i;
        }
        int donde = igual >= 0 ? igual : libre;
        if (donde < 0 || (igual < 0 && cuantos >= MAX_NOMBRADOS)) {
            jugador.sendMessage(Text.translatable("modamod.estilista.disenos.lleno", MAX_NOMBRADOS), true);
            return false;
        }
        disenos.set(donde, proto);
        sincronizar();
        jugador.sendMessage(Text.translatable("modamod.estilista.disenos.guardado", n), true);
        return true;
    }

    private int indiceNombrado(int k) {
        net.minecraft.item.Item tipo = tipoDeReferencia();
        if (tipo == null) return -1;
        int n = 0;
        for (int i = 0; i < MAX_DISENOS; i++) {
            ItemStack d = disenos.get(i);
            if (!d.isEmpty() && d.getItem() == tipo && esNombrado(d)) { if (n == k) return i; n++; }
        }
        return -1;
    }

    /** Lleva el diseño con nombre al editor (y de ahí pasa a ser el último de su tipo). */
    private boolean cargarNombrado(PlayerEntity jugador, int k) {
        int i = indiceNombrado(k);
        if (i < 0) return false;
        ItemStack d = disenos.get(i);
        ItemStack m = editor.getStack(EstiladoBlockEntity.SLOT_PRENDA);
        if (m.isEmpty()) {
            m = new ItemStack(d.getItem());
            editor.setStack(EstiladoBlockEntity.SLOT_PRENDA, m);
        }
        copiarDiseno(d, m);
        editor.markDirty();
        jugador.sendMessage(Text.translatable("modamod.estilista.disenos.cargado", d.getName()), true);
        return true;
    }

    private boolean borrarNombrado(PlayerEntity jugador, int k) {
        int i = indiceNombrado(k);
        if (i < 0) return false;
        disenos.set(i, ItemStack.EMPTY);
        sincronizar();
        jugador.sendMessage(Text.translatable("modamod.estilista.borrado"), true);
        return true;
    }

    public int cuantosDisenos() {
        int n = 0;
        for (ItemStack d : disenos) if (!d.isEmpty()) n++;
        return n;
    }

    private static <T> void copiar(net.minecraft.component.ComponentType<T> tipo, ItemStack de, ItemStack a) {
        T valor = de.get(tipo);
        if (valor != null) a.set(tipo, valor);
        else a.remove(tipo);
    }

    private static void copiarDiseno(ItemStack de, ItemStack a) {
        for (net.minecraft.component.ComponentType<?> t : COMPONENTES_DE_DISENO) copiar(t, de, a);
    }

    private static int cuenta(ItemStack s, net.minecraft.component.ComponentType<? extends java.util.Collection<?>> t) {
        java.util.Collection<?> c = s.get(t);
        return c == null ? 0 : c.size();
    }

    /** Qué lleva un diseño, para los avisos y el panel (2026-10-06: "decia 0 straps 0 apliques" aunque tuviera un acabado). */
    public static Text resumen(ItemStack d) {
        net.minecraft.text.MutableText t = Text.translatable("modamod.estilista.resumen_base", apliquesDe(d), correasDe(d));
        net.minecraft.util.Identifier trim = d.get(com.modamod.item.ModamodComponents.ACABADO_TRIM);
        if (trim != null) {
            t.append(", ").append(Text.translatable("modamod.acabado.tooltip", Text.translatable(
                    "modamod.efecto." + com.modamod.render.EfectoTrim.tipoDe(trim).name().toLowerCase())));
            net.minecraft.util.Identifier mat = d.get(com.modamod.item.ModamodComponents.ACABADO_MATERIAL);
            if (mat != null) t.append(" · ").append(Text.translatable("trim_material." + mat.getNamespace() + "." + mat.getPath()));
        }
        com.modamod.item.TexturaTela tx = d.get(com.modamod.item.ModamodComponents.TEXTURA_TELA);
        if (tx != null && tx != com.modamod.item.TexturaTela.LISA) {
            t.append(", ").append(Text.translatable("modamod.estilado.textura.actual", Text.translatable(tx.traduccion())));
        }
        if (d.get(com.modamod.item.ModamodComponents.COLORES_SOMBRERO) != null
                || d.get(com.modamod.item.ModamodComponents.COLORES_BANDA) != null) {
            t.append(", ").append(Text.translatable("modamod.estilista.resumen_colores"));
        }
        return t;
    }

    public static int apliquesDe(ItemStack s) { return cuenta(s, com.modamod.item.ModamodComponents.APLIQUES); }
    public static int correasDe(ItemStack s) { return cuenta(s, com.modamod.item.ModamodComponents.CORREAS); }

    /** Los objetos que gasta (y devuelve al quitarlos) lo que lleva una prenda: el de cada aplique de objeto y su muestra. */
    private static java.util.List<ItemStack> costoDe(ItemStack s) {
        java.util.List<ItemStack> costo = new java.util.ArrayList<>();
        java.util.List<com.modamod.aplique.Aplique> apl = s.get(com.modamod.item.ModamodComponents.APLIQUES);
        if (apl != null) {
            for (com.modamod.aplique.Aplique a : apl) {
                // Un objeto "de molde" no gastó nada al ponerse (ni vuelve).
                if (a.objeto() != null && !a.objeto().deMolde()) {
                    costo.add(a.objeto().item().copy());
                    if (!a.objeto().muestra().isEmpty()) costo.add(a.objeto().muestra().copy());
                }
            }
        }
        return costo;
    }

    /**
     * Lo que gasta el acabado de un diseño sobre {@code prenda} (2026-10-06, "el material por separado, lo gastemos"):
     * el molde de trim del patrón y el material, solo si cambian respecto de lo que la prenda ya lleva. No se devuelven
     * al reemplazarlos ni al quitarlos (el molde se duplica y el material es un lingote).
     */
    private java.util.List<ItemStack> costoAcabado(ItemStack d, ItemStack prenda) {
        java.util.List<ItemStack> costo = new java.util.ArrayList<>();
        net.minecraft.util.Identifier patron = d.get(com.modamod.item.ModamodComponents.ACABADO_TRIM);
        if (patron == null || world == null) return costo;
        net.minecraft.util.Identifier material = d.get(com.modamod.item.ModamodComponents.ACABADO_MATERIAL);
        boolean cambiaPatron = !patron.equals(prenda.get(com.modamod.item.ModamodComponents.ACABADO_TRIM));
        var reg = world.getRegistryManager();
        if (cambiaPatron) {
            reg.getOptional(net.minecraft.registry.RegistryKeys.TRIM_PATTERN).flatMap(r -> r.getEntry(patron))
                    .ifPresent(e -> costo.add(new ItemStack(e.value().templateItem())));
        }
        if (material != null && (cambiaPatron || !material.equals(prenda.get(com.modamod.item.ModamodComponents.ACABADO_MATERIAL)))) {
            reg.getOptional(net.minecraft.registry.RegistryKeys.TRIM_MATERIAL).flatMap(r -> r.getEntry(material))
                    .ifPresent(e -> costo.add(new ItemStack(e.value().ingredient())));
        }
        return costo;
    }

    /** Cuántos {@code item} hay en los Insumos (los trims y materiales que se pueden elegir, 2026-10-24). */
    public int cantidadEnInsumos(net.minecraft.item.Item item) {
        int n = 0;
        for (int i = SLOT_ALMACEN; i < TAMANO; i++) if (items.get(i).isOf(item)) n += items.get(i).getCount();
        return n;
    }

    public int hilo() { return hilo; }
    public int cuero() { return cuero; }

    /** Carga hilo o cuero en los contadores con lo que haya en {@code pila} (se le restan los ítems usados); devuelve cuántos. */
    public int absorber(ItemStack pila) {
        boolean esHilo = pila.isOf(net.minecraft.item.Items.STRING), esCuero = pila.isOf(net.minecraft.item.Items.LEATHER);
        if (!esHilo && !esCuero) return 0;
        int porItem = esHilo ? APLIQUES_POR_HILO : CORREAS_POR_CUERO;
        int actual = esHilo ? hilo : cuero;
        int caben = Math.max(0, (TOPE_CONTADOR - actual) / porItem);
        int n = Math.min(pila.getCount(), caben);
        if (n <= 0) return 0;
        pila.decrement(n);
        if (esHilo) hilo += n * porItem; else cuero += n * porItem;
        sincronizar();
        return n;
    }

    /** Lo que quedó en los buzones se vuelca a los contadores apenas hay lugar. */
    private void drenarBuzon() {
        for (int slot : new int[]{SLOT_BUZON, SLOT_BUZON_CUERO}) {
            ItemStack b = items.get(slot);
            if (b.isEmpty()) continue;
            absorber(b);
            if (b.isEmpty()) items.set(slot, ItemStack.EMPTY);
        }
    }

    /** Cuántos ítems de {@code pila} (hilo o cuero) caben ahora en su contador. */
    private int caben(ItemStack pila) {
        boolean esHilo = pila.isOf(net.minecraft.item.Items.STRING);
        int porItem = esHilo ? APLIQUES_POR_HILO : CORREAS_POR_CUERO;
        return Math.max(0, (TOPE_CONTADOR - (esHilo ? hilo : cuero)) / porItem);
    }

    /** Existencias del almacén (o una copia para simular): pila por tipo de ítem + componentes. */
    private static final class Existencias {
        final java.util.List<ItemStack> pilas = new java.util.ArrayList<>();

        void sumar(ItemStack s) {
            if (s.isEmpty()) return;
            for (ItemStack e : pilas) if (ItemStack.areItemsAndComponentsEqual(e, s)) { e.increment(s.getCount()); return; }
            pilas.add(s.copy());
        }

        boolean sacar(ItemStack s) {
            for (ItemStack e : pilas) {
                if (ItemStack.areItemsAndComponentsEqual(e, s)) {
                    if (e.getCount() < s.getCount()) return false;
                    e.decrement(s.getCount());
                    return true;
                }
            }
            return false;
        }

        Existencias copia() {
            Existencias c = new Existencias();
            for (ItemStack e : pilas) c.pilas.add(e.copy());
            return c;
        }
    }

    private Existencias existencias() {
        Existencias e = new Existencias();
        for (int i = SLOT_ALMACEN; i < TAMANO; i++) e.sumar(items.get(i));
        return e;
    }

    private void escribirExistencias(Existencias e) {
        for (int i = SLOT_ALMACEN; i < TAMANO; i++) items.set(i, ItemStack.EMPTY);
        int slot = SLOT_ALMACEN;
        for (ItemStack pila : e.pilas) {
            ItemStack resto = pila.copy();
            while (!resto.isEmpty()) {
                ItemStack parte = resto.split(Math.min(resto.getCount(), Math.max(1, resto.getMaxCount())));
                if (slot < TAMANO) items.set(slot++, parte);
                else if (world != null) net.minecraft.block.Block.dropStack(world, pos.up(), parte);   // sin lugar: cae al piso
            }
        }
    }

    /** Resultado de simular aplicar el diseño a esa prenda: sin tocar nada. {@code falta} (si lo hay) es lo que no alcanza. */
    public record Plan(Resultado resultado, @Nullable Existencias despues, int hilo, int cuero, ItemStack falta) {}

    public Plan planear(ItemStack prenda) {
        ItemStack d = diseno(prenda);
        if (d == null) return new Plan(Resultado.SIN_DISENO, null, hilo, cuero, ItemStack.EMPTY);
        if (com.modamod.util.MaquinaCreativa.es(this)) return new Plan(Resultado.OK, null, hilo, cuero, ItemStack.EMPTY);
        // Lo que la prenda ya traía vuelve (hilo, cuero y objetos); después se paga el diseño nuevo.
        int h = Math.min(TOPE_CONTADOR, hilo + apliquesDe(prenda)) - apliquesDe(d);
        int c = Math.min(TOPE_CONTADOR, cuero + correasDe(prenda)) - correasDe(d);
        if (h < 0) return new Plan(Resultado.FALTA, null, hilo, cuero,
                new ItemStack(net.minecraft.item.Items.STRING, (-h + APLIQUES_POR_HILO - 1) / APLIQUES_POR_HILO));
        if (c < 0) return new Plan(Resultado.FALTA, null, hilo, cuero,
                new ItemStack(net.minecraft.item.Items.LEATHER, (-c + CORREAS_POR_CUERO - 1) / CORREAS_POR_CUERO));
        Existencias e = existencias();
        for (ItemStack o : costoDe(prenda)) e.sumar(o);
        for (ItemStack o : costoDe(d)) {
            if (!e.sacar(o)) return new Plan(Resultado.FALTA, null, hilo, cuero, o);
        }
        for (ItemStack o : costoAcabado(d, prenda)) {
            if (!e.sacar(o)) return new Plan(Resultado.FALTA, null, hilo, cuero, o);
        }
        return new Plan(Resultado.OK, e, h, c, ItemStack.EMPTY);
    }

    /** Lo que falta para aplicar el diseño a la prenda de la entrada (para la pantalla), o vacío. */
    public ItemStack faltaParaLaEntrada() {
        Plan p = planear(items.get(SLOT_PRENDA));
        return p.resultado() == Resultado.FALTA ? p.falta() : ItemStack.EMPTY;
    }

    /** Arranca el trabajo: gasta los insumos, devuelve los de lo que la prenda ya traía y le pone el diseño. */
    public Resultado iniciar() {
        ItemStack prenda = items.get(SLOT_PRENDA);
        if (estado != Estado.REPOSO || prenda.isEmpty() || !items.get(SLOT_SALIDA).isEmpty()) return Resultado.SIN_DISENO;
        Plan plan = planear(prenda);
        if (plan.resultado() != Resultado.OK) return plan.resultado();
        if (plan.despues() != null) {
            escribirExistencias(plan.despues());
            hilo = plan.hilo();
            cuero = plan.cuero();
        }
        copiarDiseno(diseno(prenda), prenda);
        if (com.modamod.util.MaquinaCreativa.es(this)) {
            // La creativa no espera ni anima el pórtico (2026-10-15, "la estiladora automatica creativa tiene demora de
            // procesamiento y animacion, eso no deberia ser asi"): la prenda sale en el acto.
            items.set(SLOT_SALIDA, prenda);
            items.set(SLOT_PRENDA, ItemStack.EMPTY);
            estado = Estado.LISTO;
            progreso = 0;
            sincronizar();
            return Resultado.OK;
        }
        estado = Estado.PROCESANDO;
        progreso = 0;
        sincronizar();
        return Resultado.OK;
    }

    /**
     * Fija como diseño de su tipo la prenda de muestra del editor (o, si no hay, la de la entrada). La muestra del editor
     * queda ahí para seguir editándola; la de la entrada sale por la derecha como terminada.
     */
    private boolean fijar(PlayerEntity jugador) {
        ItemStack delEditor = editor.getStack(EstiladoBlockEntity.SLOT_PRENDA);
        boolean usaEditor = !delEditor.isEmpty();
        ItemStack muestra = usaEditor ? delEditor : items.get(SLOT_PRENDA);
        if (muestra.isEmpty()) return false;
        ItemStack proto = new ItemStack(muestra.getItem());
        copiarDiseno(muestra, proto);
        boolean hayAlgo = false;
        for (net.minecraft.component.ComponentType<?> t : COMPONENTES_DE_DISENO) hayAlgo |= proto.get(t) != null;
        if (!hayAlgo) {
            jugador.sendMessage(Text.translatable("modamod.estilista.muestra_vacia"), true);
            return false;
        }
        int libre = -1, propio = -1;
        for (int i = 0; i < MAX_DISENOS; i++) {
            if (disenos.get(i).isEmpty()) { if (libre < 0) libre = i; }
            else if (disenos.get(i).getItem() == muestra.getItem() && !esNombrado(disenos.get(i))) propio = i;
        }
        int donde = propio >= 0 ? propio : libre;
        if (donde < 0) {
            jugador.sendMessage(Text.translatable("modamod.estilista.sin_lugar"), true);
            return false;
        }
        disenos.set(donde, proto);
        if (!usaEditor) {
            items.set(SLOT_SALIDA, muestra);
            items.set(SLOT_PRENDA, ItemStack.EMPTY);
            estado = Estado.LISTO;
        }
        sincronizar();
        jugador.sendMessage(Text.translatable("modamod.estilista.fijado", resumen(proto)), true);
        return true;
    }

    /** El último de cada tipo ya se guarda solo al editar (ver {@link #autoGuardar}); queda por compatibilidad. */
    private void fijarDeLaMuestraDelEditor(ItemStack prenda) { }

    // ── animación + ticker ──

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "trabajo", 0,
                state -> estado == Estado.PROCESANDO ? state.setAndContinue(TRABAJO) : state.setAndContinue(QUIETO)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    public static void tick(World world, BlockPos pos, BlockState state, EstilistaBlockEntity be) {
        if (world.isClient) return;
        // Con señal de redstone la máquina se detiene del todo.
        if (com.modamod.util.Redstone.pausada(world, pos)) return;
        com.modamod.util.LuzMaquina.actualizar(world, pos, state, be.estado != Estado.REPOSO);
        if (world.getTime() % 20 == 7) {
            be.drenarAlmacen();
            // La creativa nunca queda con las bolsas vacías (2026-10-13, "me desapareciste los slots para aplicar los apliques").
            if (be.editor.creativa() && !be.creativaCargada) { be.creativaCargada = true; be.cargarCreativa(); }
        }
        switch (be.estado) {
            case REPOSO -> {
                if (world.getTime() % 20 == 0) be.drenarBuzon();
                // Una prenda que llegó por la cadena espera los insumos que le falten y arranca sola.
                if (be.deCadena && !be.items.get(SLOT_PRENDA).isEmpty() && be.items.get(SLOT_SALIDA).isEmpty()
                        && world.getTime() % 20 == 0) {
                    be.iniciar();
                }
            }
            case PROCESANDO -> {
                be.progreso++;
                be.sonidoDelPortico(world, pos);
                if (be.progreso % 20 == 0) be.sincronizar();
                if (be.progreso >= TICKS_PROCESO) { // 2026-10-05, "no se movio para nada el brazo": la demo dura siempre los 13 s, también en la creativa
                    be.items.set(SLOT_SALIDA, be.items.get(SLOT_PRENDA));
                    be.items.set(SLOT_PRENDA, ItemStack.EMPTY);
                    be.estado = Estado.LISTO;
                    world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.BLOCKS, 1.0f, 1.5f);
                    be.sincronizar();
                }
            }
            case LISTO -> {
                if (!be.items.get(SLOT_SALIDA).isEmpty()) be.empujarSalida(world, pos);
                if (be.items.get(SLOT_SALIDA).isEmpty()) {
                    be.estado = Estado.REPOSO;
                    be.progreso = 0;
                    be.sincronizar();
                }
            }
        }
    }

    /** Botones de la pantalla (los atiende el handler porque necesitan al jugador para avisarle). */
    public boolean onButtonClick(PlayerEntity jugador, int id) {
        if (id == BTN_LINEA) {
            alternarLinea();
            return true;
        }
        if (id >= BTN_DISENO_CARGAR_BASE && id < BTN_DISENO_CARGAR_BASE + MAX_NOMBRADOS) return cargarNombrado(jugador, id - BTN_DISENO_CARGAR_BASE);
        if (id >= BTN_DISENO_BORRAR_BASE && id < BTN_DISENO_BORRAR_BASE + MAX_NOMBRADOS) return borrarNombrado(jugador, id - BTN_DISENO_BORRAR_BASE);
        if (id == BTN_CATEGORIA_ATRAS || id == BTN_CATEGORIA_ADELANTE) return cambiarCategoria(id == BTN_CATEGORIA_ADELANTE ? 1 : -1);
        if (id == BTN_FIJAR) return fijar(jugador);
        if (id == BTN_BORRAR_TODO) {
            // Todos los diseños no nombrados (los "Último" de cada tipo de prenda), 2026-10-14.
            boolean alguno = false;
            for (int i = 0; i < MAX_DISENOS; i++) {
                if (disenos.get(i).isEmpty() || esNombrado(disenos.get(i))) continue;
                disenos.set(i, ItemStack.EMPTY);
                alguno = true;
            }
            ItemStack m = editor.getStack(EstiladoBlockEntity.SLOT_PRENDA);
            if (!m.isEmpty()) {
                for (net.minecraft.component.ComponentType<?> t : COMPONENTES_DE_DISENO) m.remove(t);
                editor.markDirty();
            }
            sincronizar();
            jugador.sendMessage(Text.translatable(alguno ? "modamod.estilista.borrado_todo" : "modamod.estilista.sin_diseno"), true);
            return alguno;
        }
        if (id == BTN_BORRAR) {
            // El tipo de la prenda de la entrada o, si no hay, el de la muestra del editor.
            ItemStack ref = !items.get(SLOT_PRENDA).isEmpty() ? items.get(SLOT_PRENDA) : editor.getStack(EstiladoBlockEntity.SLOT_PRENDA);
            for (int i = 0; i < MAX_DISENOS; i++) {
                if (!ref.isEmpty() && !disenos.get(i).isEmpty() && disenos.get(i).getItem() == ref.getItem() && !esNombrado(disenos.get(i))) {
                    disenos.set(i, ItemStack.EMPTY);
                    ItemStack m = editor.getStack(EstiladoBlockEntity.SLOT_PRENDA);
                    if (!m.isEmpty() && m.getItem() == ref.getItem()) {
                        for (net.minecraft.component.ComponentType<?> t : COMPONENTES_DE_DISENO) m.remove(t);
                        editor.markDirty();
                    }
                    sincronizar();
                    jugador.sendMessage(Text.translatable("modamod.estilista.borrado"), true);
                    return true;
                }
            }
            jugador.sendMessage(Text.translatable("modamod.estilista.sin_diseno"), true);
            return false;
        }
        if (id != BTN_APLICAR) return false;
        if (estado != Estado.REPOSO || items.get(SLOT_PRENDA).isEmpty() || !items.get(SLOT_SALIDA).isEmpty()) return false;
        // Sin diseño fijado para este tipo, pero con una muestra del mismo tipo en el editor: se usa esa (2026-10-06,
        // "cuando solo se aplica un finish no te deja activar la maquina"): no hace falta apretar Fijar antes.
        // 2026-10-06, "el acabado se ve en la vista previa pero no en el mundo": si ya había un diseño fijado de ese tipo,
        // Aplicar lo usaba tal cual y el acabado recién puesto en la muestra no llegaba a la prenda. La muestra del editor
        // (si es del mismo tipo y lleva algo) manda siempre.
        fijarDeLaMuestraDelEditor(items.get(SLOT_PRENDA));
        Resultado r = iniciar();
        switch (r) {
            case SIN_DISENO -> jugador.sendMessage(Text.translatable("modamod.estilista.sin_diseno"), true);
            case FALTA -> jugador.sendMessage(Text.translatable("modamod.estilista.falta", faltaParaLaEntrada().getName()), true);
            default -> { }
        }
        return r == Resultado.OK;
    }

    /** Los pistones del pórtico: un chasquido en cada recorrido de la animación (el reloj del cliente va a la par, a ojo). */
    private void sonidoDelPortico(World world, BlockPos pos) {
        for (int t : TICS_HORIZONTAL) {
            if (progreso == t) world.playSound(null, pos, SoundEvents.BLOCK_PISTON_CONTRACT, SoundCategory.BLOCKS, 0.25f, 1.9f);
        }
        for (int t : TICS_VERTICAL) {
            if (progreso == t) world.playSound(null, pos, SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.BLOCKS, 0.3f, 1.6f);
        }
    }

    // ── la cadena (igual que la Modeladora) ──

    private Direction ladoIzquierdo() {
        return getCachedState().get(EstilistaBlock.FACING).getOpposite().rotateYCounterclockwise();
    }

    private Direction ladoDerecho() { return ladoIzquierdo().getOpposite(); }

    @Override
    public Direction ladoSalida() { return ladoDerecho(); }

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

    private void empujarSalida(World world, BlockPos pos) {
        ItemStack actual = items.get(SLOT_SALIDA);
        if (actual.isEmpty() || !linea) return;
        Direction derecha = ladoDerecho();
        ItemStack sobrante = InventarioUtil.empujarA(world, pos.offset(derecha), derecha.getOpposite(), actual);
        if (sobrante.getCount() != actual.getCount()) {
            items.set(SLOT_SALIDA, sobrante);
            markDirty();
        }
    }

    @Override
    public int[] getAvailableSlots(Direction side) {
        if (side == Direction.UP || side == ladoIzquierdo()) return new int[]{SLOT_PRENDA};
        if (side == ladoDerecho()) return new int[0];
        return new int[]{SLOT_BUZON, SLOT_BUZON_CUERO};        // por los otros lados solo entra hilo y cuero (invisible)
    }

    @Override
    public int topeEntrada(int slot) {
        if (slot == SLOT_PRENDA) return 1;
        // Los buzones reciben solo lo que su contador puede tomar ya: así no queda nada esperando adentro (2026-10-24).
        if (slot == SLOT_BUZON) return caben(new ItemStack(net.minecraft.item.Items.STRING));
        if (slot == SLOT_BUZON_CUERO) return caben(new ItemStack(net.minecraft.item.Items.LEATHER));
        return getMaxCountPerStack();
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        if (dir != null && com.modamod.util.Redstone.pausada(this)) return false;
        if (SLOT_PRENDA == slot && !getStack(slot).isEmpty()) return false;   // una prenda por vez
        // Con el contador lleno no entra: queda en la cinta o la tolva (2026-10-24).
        if ((slot == SLOT_BUZON || slot == SLOT_BUZON_CUERO) && caben(stack) <= 0) return false;

        return isValid(slot, stack);
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) { return false; }

    // ── inventario ──

    @Override public int size() { return TAMANO + 1; }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : items) if (!s.isEmpty()) return false;
        return true;
    }

    @Override public ItemStack getStack(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeStack(int slot, int cantidad) {
        ItemStack r = Inventories.splitStack(items, slot, cantidad);
        if (slot == SLOT_PRENDA && items.get(slot).isEmpty()) deCadena = false;
        if (!r.isEmpty()) sincronizar();
        return r;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack r = Inventories.removeStack(items, slot);
        if (slot == SLOT_PRENDA) deCadena = false;
        if (!r.isEmpty()) sincronizar();
        return r;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot != SLOT_PRENDA && slot != SLOT_SALIDA) {          // buzón y almacén: pilas normales
            items.set(slot, stack);
            if (slot == SLOT_BUZON || slot == SLOT_BUZON_CUERO) drenarBuzon();
            sincronizar();
            return;
        }
        // Una prenda que llega por la cadena: con diseño para su tipo arranca sola (cuando haya insumos); sin diseño
        // pasa de largo (como en las otras máquinas).
        if (slot == SLOT_PRENDA && !stack.isEmpty() && (InventarioUtil.enCadena && linea)
                && items.get(SLOT_SALIDA).isEmpty() && estado == Estado.REPOSO) {
            if (diseno(stack) == null) {
                items.set(SLOT_SALIDA, stack.copyWithCount(1));
                estado = Estado.LISTO;
                sincronizar();
                return;
            }
            items.set(SLOT_PRENDA, stack.copyWithCount(1));
            muestraDesdeEntrada(stack);
            deCadena = true;
            iniciar();                                              // si faltan insumos queda esperando (el tick reintenta)
            sincronizar();
            return;
        }
        if (slot == SLOT_PRENDA) deCadena = false;
        items.set(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        if (slot == SLOT_PRENDA) muestraDesdeEntrada(stack);
        sincronizar();
    }

    /**
     * La prenda de la entrada ES la muestra del editor (2026-10-08, "para el aplique de la estiladora que baste con
     * poner la prenda en el input, saquemos el slot extra"): se copia con el último diseño de su tipo encima; si no hay
     * último, lo que la prenda ya traía pasa a ser el último. Al irse la prenda (sale terminada) la muestra queda en el
     * editor para seguir editando.
     */
    /**
     * Cambia el tipo de la muestra (2026-10-13, "en vez de zona del cuerpo el mismo boton de categoria"): sin prenda en la
     * Entrada, la muestra pasa a una prenda lisa del tipo siguiente o anterior y trae su último diseño.
     */
    /** Las prendas básicas que se pueden recorrer con las flechas (2026-10-13: solo las que tienen receta). */
    private static final com.modamod.telar.TelarPrenda[] CATEGORIAS = {
            com.modamod.telar.TelarPrenda.REMERA, com.modamod.telar.TelarPrenda.PANTALON, com.modamod.telar.TelarPrenda.MEDIAS,
            com.modamod.telar.TelarPrenda.CALIENTABRAZOS, com.modamod.telar.TelarPrenda.POLLERA, com.modamod.telar.TelarPrenda.CAPA,
            com.modamod.telar.TelarPrenda.DELANTAL, com.modamod.telar.TelarPrenda.SOMBRERO, com.modamod.telar.TelarPrenda.CINTO,
            com.modamod.telar.TelarPrenda.BORCEGOS};
    private int categoriaIdx = 0;

    private boolean cambiarCategoria(int delta) {
        if (!items.get(SLOT_PRENDA).isEmpty()) return false;
        // Se recuerda el índice (antes se buscaba por ítem y el cinto y el collar, o el sombrero y la vincha, se pisaban
        // y la flecha se trababa).
        categoriaIdx = Math.floorMod(categoriaIdx + delta, CATEGORIAS.length);
        muestraDesdeEntrada(CATEGORIAS[categoriaIdx].crear(0xFFFFFF));
        return true;
    }

    private void muestraDesdeEntrada(ItemStack entrada) {
        if (world == null || world.isClient || entrada.isEmpty()) return;
        for (int i = 0; i < CATEGORIAS.length; i++) if (CATEGORIAS[i].item() == entrada.getItem()) { categoriaIdx = i; break; }
        ItemStack m = entrada.copyWithCount(1);
        ItemStack ultimo = diseno(m);
        if (ultimo != null) copiarDiseno(ultimo, m);
        autoguardando = true;
        try {
            editor.setStack(EstiladoBlockEntity.SLOT_PRENDA, m);
            muestraAnterior = m.getItem();
        } finally {
            autoguardando = false;
        }
        if (ultimo == null) escribirUltimo(m);
    }

    @Override public int getMaxCountPerStack() { return 64; }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        if (slot == SLOT_BUZON) return stack.isOf(net.minecraft.item.Items.STRING);
        if (slot == SLOT_BUZON_CUERO) return stack.isOf(net.minecraft.item.Items.LEATHER);
        if (slot >= SLOT_ALMACEN) {
            return !stack.isEmpty() && !com.modamod.garment.Garments.esPrenda(stack)
                    && !stack.isOf(net.minecraft.item.Items.STRING) && !stack.isOf(net.minecraft.item.Items.LEATHER)
                    && com.modamod.aplique.ObjetoAplique.admite(stack);
        }
        return slot == SLOT_PRENDA && items.get(SLOT_PRENDA).isEmpty() && estado == Estado.REPOSO
                && EstiladoBlockEntity.admite(stack);
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return world != null && world.getBlockEntity(pos) == this
                && player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clear() { items.clear(); }

    @Override
    public void cargarCreativa() {
        // Uno de cada plantilla y molde en las bolsas, y un retazo de muestra (2026-10-12).
        for (net.minecraft.item.Item item : net.minecraft.registry.Registries.ITEM) {
            if (item instanceof com.modamod.aplique.MoldeApliquePersonalizadoItem || item instanceof com.modamod.item.MuestraColorItem) continue;
            ItemStack s = new ItemStack(item);
            if (bolsaDe(s) >= 0) absorberEnBolsa(s);
        }
        sincronizar();
    }

    // ── pantalla ──

    @Override
    public Text getDisplayName() { return Text.translatable("block.modamod.estilista"); }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayerEntity player) { return pos; }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new EstilistaScreenHandler(syncId, inv, this);
    }

    // ── NBT y sincronización ──

    private void sincronizar() {
        markDirty();
        if (world != null && !world.isClient) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        com.modamod.util.DisenosMaquina.escribir(nbt, blancoDiseno, registries);
        nbt.putBoolean("linea", linea);
        Inventories.writeNbt(nbt, items, registries);
        nbt.putInt("estado", estado.ordinal());
        nbt.putInt("progreso", progreso);
        nbt.putBoolean("de_cadena", deCadena);
        nbt.putInt("hilo", hilo);
        nbt.putInt("cuero", cuero);
        NbtCompound d = new NbtCompound();
        Inventories.writeNbt(d, disenos, registries);
        nbt.put("disenos", d);
        nbt.put("editor", editor.createNbt(registries));
        nbt.putBoolean("creativa_cargada", creativaCargada);
        nbt.putInt("pestana_actual", pestanaActual);
        net.minecraft.nbt.NbtList ld = new net.minecraft.nbt.NbtList();
        for (ItemStack s : disenosItem) ld.add(s.encode(registries));
        nbt.put("disenos_item", ld);
        for (int t = 0; t < 3; t++) for (int k = 0; k < 3; k++) if (fijados[t][k]) nbt.putBoolean("fijo_" + t + "_" + k, true);
        for (int t = 0; t < 3; t++) for (int k = 0; k < 3; k++) {
            if (!memoriaPestana[t][k].isEmpty()) nbt.put("mem_" + t + "_" + k, memoriaPestana[t][k].encode(registries));
        }
        for (int t = 0; t < 3; t++) {
            net.minecraft.nbt.NbtList l = new net.minecraft.nbt.NbtList();
            for (ItemStack s : lista(t)) l.add(s.encode(registries));
            nbt.put("bolsa_" + t, l);
        }
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        com.modamod.util.DisenosMaquina.leer(nbt, blancoDiseno, registries);
        linea = nbt.getBoolean("linea");
        // Vaciar antes de leer (2026-10-24, "les queda la figura de la prenda arriba incluso despues de haberla sacado"):
        // readNbt solo pisa los casilleros que vienen llenos, así que en el cliente la salida ya retirada seguía puesta.
        items.clear();
        Inventories.readNbt(nbt, items, registries);
        estado = Estado.values()[Math.max(0, Math.min(Estado.values().length - 1, nbt.getInt("estado")))];
        progreso = nbt.getInt("progreso");
        deCadena = nbt.getBoolean("de_cadena");
        hilo = Math.min(TOPE_CONTADOR, nbt.getInt("hilo"));
        cuero = Math.min(TOPE_CONTADOR, nbt.getInt("cuero"));
        disenos.clear();
        if (nbt.contains("disenos")) Inventories.readNbt(nbt.getCompound("disenos"), disenos, registries);
        if (nbt.contains("editor")) editor.read(nbt.getCompound("editor"), registries);
        creativaCargada = nbt.getBoolean("creativa_cargada");
        pestanaActual = Math.max(0, Math.min(2, nbt.getInt("pestana_actual")));
        disenosItem.clear();
        net.minecraft.nbt.NbtList ld = nbt.getList("disenos_item", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < ld.size(); i++) {
            ItemStack s = ItemStack.fromNbtOrEmpty(registries, ld.getCompound(i));
            if (!s.isEmpty()) disenosItem.add(s);
        }
        for (int t = 0; t < 3; t++) for (int k = 0; k < 3; k++) fijados[t][k] = nbt.getBoolean("fijo_" + t + "_" + k);
        for (int t = 0; t < 3; t++) for (int k = 0; k < 3; k++) {
            memoriaPestana[t][k] = nbt.contains("mem_" + t + "_" + k)
                    ? ItemStack.fromNbtOrEmpty(registries, nbt.getCompound("mem_" + t + "_" + k)) : ItemStack.EMPTY;
        }
        for (int t = 0; t < 3; t++) {
            lista(t).clear();
            net.minecraft.nbt.NbtList l = nbt.getList("bolsa_" + t, net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < l.size(); i++) {
                ItemStack s = ItemStack.fromNbtOrEmpty(registries, l.getCompound(i));
                if (!s.isEmpty()) lista(t).add(s);
            }
        }
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) { return createNbt(registries); }

    @Override
    public net.minecraft.network.packet.Packet<net.minecraft.network.listener.ClientPlayPacketListener> toUpdatePacket() {
        return net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket.create(this);
    }
}
