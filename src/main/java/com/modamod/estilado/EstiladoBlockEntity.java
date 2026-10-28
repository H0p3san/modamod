package com.modamod.estilado;

import com.modamod.aplique.Aplique;
import com.modamod.aplique.Colocacion;
import com.modamod.aplique.Oscilacion;
import com.modamod.aplique.ModeloAplique;
import com.modamod.aplique.MoldeApliqueItem;
import com.modamod.aplique.ObjetoAplique;
import com.modamod.aplique.RetazoApliqueItem;
import com.modamod.garment.Garments;
import com.modamod.garment.Parte;
import com.modamod.item.ModamodComponents;
import com.modamod.item.ModamodItems;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Mesa de estilado (2026-10-01, "que uno pueda poner cualquier prenda y
 * agregarles modelos 3d anclados en la geometria de la prenda"): tres slots —
 * la PRENDA, el MOLDE de aplique (forma, no se gasta) y el RETAZO (colores, se
 * gasta uno por aplique). En la pantalla se hace click sobre la prenda en la
 * vista 3D y ahí queda el aplique ({@link #poner}); los puestos se eligen de
 * una lista para girarlos, agrandarlos o quitarlos (el retazo vuelve).
 *
 * <p>Se sincroniza al cliente: la vista previa dibuja la prenda del slot con
 * sus apliques al toque.
 */
public class EstiladoBlockEntity extends BlockEntity
        implements GeoBlockEntity, Inventory, ExtendedScreenHandlerFactory<BlockPos>,
        com.modamod.util.MaquinaCreativa.Cargable {

    /** SLOT_OBJETO (2026-10-04, apliques de objeto): cualquier ítem; si hay uno, el aplique que se pone es ese objeto. */
    public static final int SLOT_PRENDA = 0, SLOT_MOLDE = 1, SLOT_RETAZO = 2, SLOT_OBJETO = 3;
    /**
     * La biblioteca de moldes de la Mesa creativa (2026-10-04, "si perdi un molde de aplique que cree con la mesa
     * de estilado donde lo encuentro" → "vamos con las dos"): una copia de cada molde que fabrica, en slots
     * escondidos al FINAL del inventario (así el handler los sincroniza solo y el NBT guardado no se corre).
     */
    public static final int SLOT_BIBLIOTECA = 4, BIBLIOTECA = 24, BIBLIOTECA_FIN = SLOT_BIBLIOTECA + BIBLIOTECA;
    /**
     * El material del acabado (2026-10-06, "el material por separado, opcional"): un material de trim de vanilla (lingote,
     * cuarzo, redstone...). Cambia la paleta del efecto del acabado y se gasta 1 al aplicarlo. Al FINAL del inventario
     * para no correr los índices guardados.
     */
    public static final int SLOT_MATERIAL = BIBLIOTECA_FIN, TAMANO = SLOT_MATERIAL + 1;
    /** Botones de la lista de la biblioteca: dar una copia y borrar, uno por fila. */
    public static final int BTN_BIBLIO_DAR_BASE = 200, BTN_BIBLIO_BORRAR_BASE = 240;

    /**
     * Colorear el sombrero de bruja (2026-10-05, "que se le apliquen los colores en la mesa de estilado sobre todo si
     * tiene tres areas"): {@code BTN_COLOR_BASE + zona*4 + color} pasa el color {@code color} (0..2) del retazo a la
     * zona {@code zona} (0 ala, 1 cono, 2 cinta); {@code BTN_COLOR_ENTERO} pasa los 3 colores del retazo a las 3 zonas.
     * No se gasta el retazo.
     */
    public static final int BTN_COLOR_BASE = 300, BTN_COLOR_ENTERO = 320;
    /** {@code BTN_PATRON_BASE + zona}: pasa la zona del sombrero al siguiente dibujo (liso, rayas, lunares...) — 2026-10-05, patrones por zona. */
    public static final int BTN_PATRON_BASE = 330;
    /**
     * Los mismos botones de color y dibujo para el pie IZQUIERDO de los borcegos (2026-10-11, "no se puede independizar
     * derecho e izquierdo?"): {@code BTN_PIE_IZQ + id} (800..832). Sin este corrimiento, el pie derecho.
     */
    public static final int BTN_PIE_IZQ = 1000;   // 1300..1332: libre (500 chocaba con el largo de las correas, 760..887)
    /** Quita la última correa libre de la prenda (2026-10-05, "correas libres"). */
    public static final int BTN_CORREA_QUITAR = 360;
    /**
     * Por índice de correa (0..7): quitar, recolorear con el retazo, ancho (+ i*4 + ancho-1) y, desde que hay 4 modos y
     * el largo (2026-10-06), modo (+ i*4 + modo) y largo (+ i*16 + paso, paso = largo/2 - 1).
     */
    public static final int BTN_CORREA_QUITAR_BASE = 480, BTN_CORREA_COLOREAR_BASE = 490, BTN_CORREA_ANCHO_BASE = 520,
            BTN_CORREA_MODO_BASE = 700, BTN_CORREA_LARGO_BASE = 760;
    public static final int BTN_SELECCIONAR_BASE = 100;        // + 0..11 (antes 0..5: chocaba con el resto al subir a 12 apliques)
    public static final int BTN_GIRO = 10, BTN_GIRO_ATRAS = 11;
    public static final int BTN_ESCALA = 12, BTN_ESCALA_ATRAS = 13;
    /** Lo atiende el ScreenHandler: necesita al jugador para devolverle el retazo. */
    public static final int BTN_QUITAR = 14;
    /**
     * Pone o saca la textura del Molde de textura que esté en el slot del
     * molde (2026-10-01, relieve: "pongamos un par de moldes de prueba").
     */
    public static final int BTN_TEXTURA = 15;
    /**
     * Mesa creativa (2026-10-01): pasa al siguiente molde (de aplique o de
     * textura) sin tener que tenerlo — la mesa tiene un solo slot de molde,
     * así que "todos los moldes adentro" es poder elegirlos acá.
     */
    public static final int BTN_SIGUIENTE_MOLDE = 16;
    /** Blandura del aplique elegido: BASE + 0..10 = 0..100 % (2026-10-04, "tela blanda... un slider"). */
    public static final int BTN_BLANDURA_BASE = 20;
    public static final int BLANDURA_PASOS = 10;
    /** Aplique de objeto (2026-10-04): Ítem/Bloque, variante del bloque (vela encendida...) e inclinación de a 15°. */
    public static final int BTN_OBJ_MODO = 40, BTN_OBJ_VARIANTE = 41;
    public static final float ESCALA_MIN_OBJETO = 0.25f;
    /** La que trae un aplique recién puesto. */
    public static final float BLANDURA_INICIAL = 0.5f;

    public static final float ESCALA_MIN = 0.5f, ESCALA_MAX = 2.5f, PASO_ESCALA = 0.25f;
    /** Fila del aplique (2026-10-22, botonera): cantidad y separación, cada uno con su menos y su más. */
    public static final int BTN_FILA_MAS = 50, BTN_FILA_MENOS = 51, BTN_SEPARACION_MAS = 52, BTN_SEPARACION_MENOS = 53;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(TAMANO, ItemStack.EMPTY);
    /** Qué aplique de la prenda editan los botones (-1 = ninguno). */
    private int seleccionado = -1;

    public EstiladoBlockEntity(BlockPos pos, BlockState state) {
        super(EstiladoMod.ESTILADO_BLOCK_ENTITY, pos, state);
    }

    /**
     * La Estilista automática (2026-10-05, etapa 3: "no quiero perder el tamaño del visualizador") usa una Mesa interna
     * como editor de la prenda de muestra: no está en el mundo, vive dentro de la máquina y la pantalla es la misma.
     * Edita gratis (el costo real —hilo y cuero— se paga al aplicar el diseño a cada prenda).
     */
    @Nullable
    private com.modamod.estilista.EstilistaBlockEntity anfitrion;

    public void anfitrionar(com.modamod.estilista.EstilistaBlockEntity host) { this.anfitrion = host; }

    /** ¿Es una Mesa/editor creativo? (la de la Estilista creativa también). */
    public boolean creativa() {
        return com.modamod.util.MaquinaCreativa.es(anfitrion != null ? anfitrion : this);
    }

    /** ¿Edita sin gastar ni devolver insumos? */
    public boolean gratis() { return anfitrion != null || creativa(); }

    /** La Mesa que atiende ese punto: la del bloque o, si ahí hay una Estilista, su editor interno. */
    @Nullable
    public static EstiladoBlockEntity en(net.minecraft.world.World mundo, BlockPos pos) {
        BlockEntity be = mundo.getBlockEntity(pos);
        if (be instanceof EstiladoBlockEntity e) return e;
        if (be instanceof com.modamod.estilista.EstilistaBlockEntity h) return h.editor();
        return null;
    }

    public int seleccionado() { return seleccionado; }

    public List<Aplique> apliques() {
        List<Aplique> a = items.get(SLOT_PRENDA).get(ModamodComponents.APLIQUES);
        return a == null ? List.of() : a;
    }

    private void guardarApliques(List<Aplique> lista) {
        ItemStack prenda = items.get(SLOT_PRENDA);
        if (lista.isEmpty()) prenda.remove(ModamodComponents.APLIQUES);
        else prenda.set(ModamodComponents.APLIQUES, List.copyOf(lista));
        markDirty();
    }

    /**
     * Pone un aplique donde se hizo click (lo valida el servidor): hace falta
     * prenda, molde y retazo, y que la parte sea de la prenda. El punto se
     * acota a la caja de esa parte, por si llega cualquier cosa.
     */
    public boolean poner(Parte parte, float x, float y, float z, Direction cara) {
        return poner(parte, x, y, z, cara, Aplique.Superficie.CAJA);
    }

    /**
     * Con la superficie: en la pollera y la capa (2026-10-02) x, y son la u, v
     * de su tela (px de 64) y la prenda tiene que ser de ese tipo.
     */
    public boolean poner(Parte parte, float x, float y, float z, Direction cara, Aplique.Superficie superficie) {
        return poner(parte, x, y, z, cara, superficie, -1);
    }

    /**
     * Sobre otro aplique (2026-10-04, "podemos poner apliques sobre apliques?"): con superficie APLIQUE, {@code padre}
     * es el índice del aplique que lo lleva y x, y, z el punto de su caja (px, centrado en su espacio de objeto).
     */
    public boolean poner(Parte parte, float x, float y, float z, Direction cara, Aplique.Superficie superficie, int padre) {
        ItemStack prenda = items.get(SLOT_PRENDA), molde = items.get(SLOT_MOLDE), retazo = items.get(SLOT_RETAZO);
        ItemStack objeto = items.get(SLOT_OBJETO);
        // Mesa creativa (2026-10-01): el retazo no hace falta ni se gasta (sin retazo, sale blanco).
        boolean gratis = gratis();
        // Con un objeto en su slot (2026-10-04), el aplique es ese objeto y el molde no cuenta; la muestra de
        // color en el slot del retazo lo tiñe (opcional).
        // El editor de la Estilista no tiene casillero de objeto (2026-10-14, "la autoestiladora solo aplica tridentes": un
        // objeto que quedó en ese casillero escondido volvía tridente todo aplique que se ponía).
        if (anfitrion != null && !creativa() && !objeto.isEmpty()) { items.set(SLOT_OBJETO, ItemStack.EMPTY); objeto = ItemStack.EMPTY; }
        boolean esObjeto = !objeto.isEmpty() && gratis;       // el slot de objeto es solo de la Mesa creativa
        // Un molde personalizado (2026-10-04): trae el modelo u objeto y su colocación; no se gasta nada de él.
        Aplique plantilla = !esObjeto && molde.getItem() instanceof com.modamod.aplique.MoldeApliquePersonalizadoItem
                ? com.modamod.aplique.MoldeApliquePersonalizadoItem.plantilla(molde) : null;
        if (esObjeto) {
            if (prenda.isEmpty() || !ObjetoAplique.admite(objeto)) return false;
        } else if (plantilla != null) {
            // Un objeto del molde se tiñe con la muestra (opcional); un modelo del mod pide su retazo.
            if (prenda.isEmpty() || (plantilla.objeto() == null && !(retazo.getItem() instanceof RetazoApliqueItem) && !gratis)) return false;
        } else if (prenda.isEmpty() || !(molde.getItem() instanceof MoldeApliqueItem)
                || (!(retazo.getItem() instanceof RetazoApliqueItem) && !gratis)) {
            return false;
        }
        if (!admite(prenda) || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) return false;
        List<Aplique> actuales = new ArrayList<>(apliques());
        if (actuales.size() >= Aplique.MAXIMO_POR_PRENDA) return false;
        switch (superficie) {
            case POLLERA -> {
                if (!(prenda.getItem() instanceof com.modamod.item.PolleraItem)) return false;
                x = MathHelper.clamp(x, 0, 64);
                y = MathHelper.clamp(y, 0, 64);
                z = 0;
            }
            case CAPA -> {
                if (!(prenda.getItem() instanceof com.modamod.item.CapaItem)) return false;
                x = MathHelper.clamp(x, 0, 64);
                y = MathHelper.clamp(y, 0, 64);
                z = 0;
            }
            case DELANTAL -> {
                if (!(prenda.getItem() instanceof com.modamod.item.DelantalItem)) return false;
                x = MathHelper.clamp(x, 0, 64);
                y = MathHelper.clamp(y, 0, 64);
                z = 0;
            }
            case CAPUCHA -> {
                // La capucha del Top o de la capa (2026-10-24).
                boolean conCapucha = com.modamod.item.TopCorte.tieneCapucha(prenda)
                        || (prenda.getItem() instanceof com.modamod.item.CapaItem && com.modamod.item.CapaItem.capucha(prenda));
                if (!conCapucha) return false;
                x = MathHelper.clamp(x, 0, 64);
                y = MathHelper.clamp(y, 0, 64);
                z = 0;
            }
            case APLIQUE -> {
                if (padre < 0 || padre >= actuales.size()) return false;
                parte = actuales.get(padre).parte();      // la parte del cuerpo del aplique que lo lleva
                x = MathHelper.clamp(x, -24, 24);
                y = MathHelper.clamp(y, -24, 24);
                z = MathHelper.clamp(z, -24, 24);
            }
            default -> {
                if (prenda.getItem() instanceof com.modamod.item.ZonasTenibles) {
                    // Sobre el ala o el cono (2026-10-05): el punto es de una caja del sombrero en el marco de la cabeza (y
                    // negativo = arriba), así que el rango llega hasta la punta y el borde del ala ancha.
                    x = MathHelper.clamp(x, -11, 11);
                    y = MathHelper.clamp(y, -32, 18);   // hasta el piso de los borcegos (y 12 de la pierna) y el fondo del maletín
                    z = MathHelper.clamp(z, -11, 11);
                } else {
                    x = MathHelper.clamp(x, -8, 8);
                    y = MathHelper.clamp(y, -10, 14);
                    z = MathHelper.clamp(z, -6, 6);
                }
            }
        }
        if (esObjeto) {
            ItemStack muestra = retazo.getItem() instanceof com.modamod.item.MuestraColorItem ? retazo : ItemStack.EMPTY;
            actuales.add(new Aplique(ModeloAplique.OBJETO, parte, x, y, z, cara, 0f, 1f, RetazoApliqueItem.BLANCO, superficie,
                    BLANDURA_INICIAL, ObjetoAplique.de(objeto, muestra).paraPoner(muestra)));
            if (!gratis) {
                objeto.decrement(1);
                if (!muestra.isEmpty()) muestra.decrement(1);
            }
        } else if (plantilla != null) {
            ItemStack muestra = plantilla.objeto() != null && retazo.getItem() instanceof com.modamod.item.MuestraColorItem
                    ? retazo : ItemStack.EMPTY;
            ObjetoAplique ob = plantilla.objeto() == null ? null : plantilla.objeto().paraPoner(muestra);
            java.util.List<Integer> colores = plantilla.objeto() == null && retazo.getItem() instanceof RetazoApliqueItem
                    ? RetazoApliqueItem.colores(retazo) : RetazoApliqueItem.BLANCO;
            actuales.add(new Aplique(plantilla.modelo(), parte, x, y, z, cara, plantilla.giro(), plantilla.escala(), colores,
                    superficie, plantilla.blandura(), ob, plantilla.colocacion(), plantilla.oscilacion()).conFila(plantilla.fila()));
            // Un objeto de molde no gasta nada (ni el objeto ni la muestra de color); un modelo del mod gasta su retazo.
            if (!gratis && plantilla.objeto() == null) retazo.decrement(1);
        } else {
            MoldeApliqueItem m = (MoldeApliqueItem) molde.getItem();
            actuales.add(new Aplique(m.modelo, parte, x, y, z, cara, 0f, 1f, RetazoApliqueItem.colores(retazo), superficie, BLANDURA_INICIAL)
                    .conFila(m.fila));
            if (!gratis) retazo.decrement(1);
        }
        if (superficie == Aplique.Superficie.APLIQUE) {
            actuales.set(actuales.size() - 1, actuales.get(actuales.size() - 1).conPadre(padre));
        }
        seleccionado = actuales.size() - 1;
        guardarApliques(actuales);
        return true;
    }

    /** Saca el aplique elegido y devuelve su retazo (con sus colores). */
    public void quitar(PlayerEntity jugador) {
        List<Aplique> actuales = new ArrayList<>(apliques());
        if (seleccionado < 0 || seleccionado >= actuales.size()) return;
        // Se va el elegido y todo lo que lleva encima (2026-10-04, apliques sobre apliques); los hijos siempre
        // tienen un índice mayor que su padre, así que alcanza con una pasada.
        boolean[] sale = new boolean[actuales.size()];
        sale[seleccionado] = true;
        for (int i = seleccionado + 1; i < actuales.size(); i++) {
            Aplique h = actuales.get(i);
            if (h.superficie() == Aplique.Superficie.APLIQUE && h.padre() >= 0 && h.padre() < i && sale[h.padre()]) sale[i] = true;
        }
        int[] nuevoIndice = new int[actuales.size()];
        List<Aplique> quedan = new ArrayList<>();
        for (int i = 0; i < actuales.size(); i++) {
            Aplique a = actuales.get(i);
            if (sale[i]) {
                devolver(jugador, a);
                nuevoIndice[i] = -1;
            } else {
                nuevoIndice[i] = quedan.size();
                quedan.add(a);
            }
        }
        // Los que quedan apuntan a su padre por índice: se corrigen.
        for (int i = 0; i < quedan.size(); i++) {
            Aplique a = quedan.get(i);
            if (a.superficie() == Aplique.Superficie.APLIQUE) quedan.set(i, a.conPadre(a.padre() >= 0 ? nuevoIndice[a.padre()] : -1));
        }
        seleccionado = Math.min(seleccionado, quedan.size() - 1);
        guardarApliques(quedan);
    }

    /** Le devuelve al jugador lo que gastó ese aplique (retazo, o objeto y muestra), como antes. */
    private void devolver(PlayerEntity jugador, Aplique a) {
        if (a.objeto() != null) {
            // Vuelve el objeto y la muestra, tal cual. Si el aplique se puso en la Mesa creativa o salió de un molde
            // (marcado "de molde") no se gastó nada: no se devuelve nada, si no se duplicarían.
            if (!gratis() && !a.objeto().deMolde()) {
                jugador.getInventory().offerOrDrop(a.objeto().item().copy());
                if (!a.objeto().muestra().isEmpty()) jugador.getInventory().offerOrDrop(a.objeto().muestra().copy());
            }
        } else if (anfitrion == null) {                // el editor de la Estilista no gasta retazos: no hay nada que devolver
            ItemStack retazo = RetazoApliqueItem.conColores(new ItemStack(ModamodItems.RETAZO_APLIQUE),
                    a.color(0), a.color(1), a.color(2));
            jugador.getInventory().offerOrDrop(retazo);
        }
    }

    /**
     * Fabrica un molde de aplique personalizado con el aplique elegido (2026-10-04, "que pueda producir un molde
     * ahí para que se use en la normal"): sin posición (esa se elige al ponerlo), con el objeto sin muestra y
     * marcado "de molde". Solo la Mesa creativa; no gasta nada.
     */
    public void crearMolde(PlayerEntity jugador, String nombre) {
        List<Aplique> actuales = apliques();
        if (!creativa() || seleccionado < 0 || seleccionado >= actuales.size()) return;
        Aplique a = actuales.get(seleccionado);
        Aplique plantilla = new Aplique(a.modelo(), Parte.TORSO, 0f, 0f, 0f, Direction.NORTH, a.giro(), a.escala(),
                RetazoApliqueItem.BLANCO, Aplique.Superficie.CAJA, a.blandura(),
                a.objeto() == null ? null : a.objeto().paraMolde(), a.colocacion(), a.oscilacion()).conFila(a.fila());
        ItemStack molde = new ItemStack(ModamodItems.MOLDE_APLIQUE_PERSONALIZADO);
        molde.set(ModamodComponents.APLIQUE_PLANTILLA, plantilla);
        // El nombre que escribió el jugador en el casillero del panel (2026-10-04, "me falta un casillero para
        // ponerle el nombre al aplique").
        String limpio = nombre == null ? "" : nombre.strip();
        if (limpio.length() > 40) limpio = limpio.substring(0, 40);
        if (!limpio.isEmpty()) molde.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(limpio));
        guardarEnBiblioteca(molde.copy());
        // El historial del jugador en el mundo (comando /modamod moldes): sobrevive aunque se pierda la Mesa.
        if (jugador instanceof ServerPlayerEntity sp && sp.getServer() != null) {
            MoldesGuardados.registrar(sp.getServer(), sp, molde.copy());
        }
        jugador.getInventory().offerOrDrop(molde);
    }

    /** Guarda una copia en el primer lugar libre de la biblioteca; si está llena, se va el más viejo. */
    private void guardarEnBiblioteca(ItemStack molde) {
        for (int i = SLOT_BIBLIOTECA; i < BIBLIOTECA_FIN; i++) {
            if (items.get(i).isEmpty()) {
                items.set(i, molde);
                markDirty();
                return;
            }
        }
        for (int i = SLOT_BIBLIOTECA; i < BIBLIOTECA_FIN - 1; i++) items.set(i, items.get(i + 1));
        items.set(BIBLIOTECA_FIN - 1, molde);
        markDirty();
    }

    /** Una copia del molde {@code i} de la biblioteca (solo la Mesa creativa), o vacío. */
    public ItemStack copiaDeBiblioteca(int i) {
        if (!creativa() || i < 0 || i >= BIBLIOTECA) return ItemStack.EMPTY;
        return items.get(SLOT_BIBLIOTECA + i).copy();
    }

    public boolean borrarDeBiblioteca(int i) {
        if (!creativa() || i < 0 || i >= BIBLIOTECA) return false;
        if (items.get(SLOT_BIBLIOTECA + i).isEmpty()) return false;
        // Se corre la lista para que no queden huecos en el medio.
        for (int k = SLOT_BIBLIOTECA + i; k < BIBLIOTECA_FIN - 1; k++) items.set(k, items.get(k + 1));
        items.set(BIBLIOTECA_FIN - 1, ItemStack.EMPTY);
        markDirty();
        return true;
    }

    public boolean onButtonClick(int id) {
        if (id == BTN_TEXTURA) return alternarTextura();
        if (id == BTN_SIGUIENTE_MOLDE) return siguienteMolde();
        if (id == BTN_CORREA_QUITAR || (id >= BTN_CORREA_QUITAR_BASE && id < BTN_CORREA_QUITAR_BASE + 8)) {
            List<com.modamod.correa.Correa> l = new ArrayList<>(correas());
            if (l.isEmpty()) return false;
            int k = id == BTN_CORREA_QUITAR ? l.size() - 1 : id - BTN_CORREA_QUITAR_BASE;
            if (k < 0 || k >= l.size()) return false;
            l.remove(k);
            guardarCorreas(l);
            return true;
        }
        boolean esColorear = id >= BTN_CORREA_COLOREAR_BASE && id < BTN_CORREA_COLOREAR_BASE + 8;
        boolean esAncho = id >= BTN_CORREA_ANCHO_BASE && id < BTN_CORREA_ANCHO_BASE + 32;
        boolean esModo = id >= BTN_CORREA_MODO_BASE && id < BTN_CORREA_MODO_BASE + 32;
        boolean esLargo = id >= BTN_CORREA_LARGO_BASE && id < BTN_CORREA_LARGO_BASE + 128;
        if (esColorear || esAncho || esModo || esLargo) {
            List<com.modamod.correa.Correa> l = new ArrayList<>(correas());
            int k = esAncho ? (id - BTN_CORREA_ANCHO_BASE) / 4 : esModo ? (id - BTN_CORREA_MODO_BASE) / 4
                    : esLargo ? (id - BTN_CORREA_LARGO_BASE) / 16 : id - BTN_CORREA_COLOREAR_BASE;
            if (k < 0 || k >= l.size()) return false;
            com.modamod.correa.Correa c = l.get(k);
            if (esAncho) {
                c = c.conAncho(1 + (id - BTN_CORREA_ANCHO_BASE) % 4);
            } else if (esModo) {
                var modos = com.modamod.correa.ModoCorrea.values();
                int m = (id - BTN_CORREA_MODO_BASE) % 4;
                if (m >= modos.length) return false;
                c = c.conModo(modos[m]);
                // Pasar a ANCLADA deja un solo punto: el 2.º coincide con el 1.º.
                if (modos[m] == com.modamod.correa.ModoCorrea.ANCLADA) {
                    c = new com.modamod.correa.Correa(c.parte(), c.desde(), c.desde(), c.estilo(), c.modo(), c.ancho(), c.colores(),
                            c.blandura(), c.superficie(), c.largo());
                }
            } else if (esLargo) {
                c = c.conLargo(com.modamod.correa.Correa.LARGO_PASO * (1 + (id - BTN_CORREA_LARGO_BASE) % 16));
            } else {
                // Recolorear con el retazo (o la muestra) del slot, sin gastarlo.
                List<Integer> fuente = coloresDeLaFuente();
                if (fuente == null) return false;
                c = c.conColores(List.of(fuente.get(0), fuente.get(1), fuente.get(2)));
            }
            l.set(k, c);
            guardarCorreas(l);
            return true;
        }
        if (id >= BTN_COLOR_BASE && id <= BTN_PATRON_BASE + 2) return botonDeZona(id, false);
        if (id >= BTN_PIE_IZQ + BTN_COLOR_BASE && id <= BTN_PIE_IZQ + BTN_PATRON_BASE + 2) return botonDeZona(id - BTN_PIE_IZQ, true);
        List<Aplique> actuales = new ArrayList<>(apliques());
        if (id >= BTN_SELECCIONAR_BASE && id < BTN_SELECCIONAR_BASE + Aplique.MAXIMO_POR_PRENDA) {
            int i = id - BTN_SELECCIONAR_BASE;
            if (i >= actuales.size()) return false;
            seleccionado = seleccionado == i ? -1 : i;
            markDirty();
            return true;
        }
        if (seleccionado < 0 || seleccionado >= actuales.size()) return false;
        Aplique a = actuales.get(seleccionado);
        if (id >= BTN_BLANDURA_BASE && id <= BTN_BLANDURA_BASE + BLANDURA_PASOS) {
            actuales.set(seleccionado, a.conBlandura((id - BTN_BLANDURA_BASE) / (float) BLANDURA_PASOS));
            guardarApliques(actuales);
            return true;
        }
        switch (id) {
            case BTN_GIRO -> actuales.set(seleccionado, a.conGiro((Math.round(a.giro()) + 15) % 360));
            case BTN_GIRO_ATRAS -> actuales.set(seleccionado, a.conGiro((Math.round(a.giro()) + 345) % 360));
            case BTN_ESCALA -> actuales.set(seleccionado, a.conEscala(Math.min(ESCALA_MAX, a.escala() + PASO_ESCALA)));
            case BTN_ESCALA_ATRAS -> actuales.set(seleccionado, a.conEscala(
                    Math.max(a.objeto() != null ? ESCALA_MIN_OBJETO : ESCALA_MIN, a.escala() - PASO_ESCALA)));
            case BTN_FILA_MAS, BTN_FILA_MENOS, BTN_SEPARACION_MAS, BTN_SEPARACION_MENOS -> {
                if (a.objeto() != null) return false;
                com.modamod.aplique.Fila f = a.fila();
                int paso = id == BTN_FILA_MAS || id == BTN_SEPARACION_MAS ? 1 : -1;
                actuales.set(seleccionado, a.conFila(id == BTN_FILA_MAS || id == BTN_FILA_MENOS
                        ? new com.modamod.aplique.Fila(f.cantidad() + paso, f.separacion())
                        : new com.modamod.aplique.Fila(f.cantidad(), f.separacion() + paso * com.modamod.aplique.Fila.PASO_SEPARACION)));
            }
            case BTN_OBJ_MODO, BTN_OBJ_VARIANTE -> {
                ObjetoAplique o = a.objeto();
                if (o == null) return false;
                switch (id) {
                    case BTN_OBJ_MODO -> {
                        if (ObjetoAplique.bloqueDe(o.item()) == null) return false;
                        o = o.conBloque(!o.bloque()).conVariante(0);
                    }
                    case BTN_OBJ_VARIANTE -> {
                        if (!o.bloque() || o.cantidadDeVariantes() < 2) return false;
                        o = o.conVariante((o.variante() + 1) % o.cantidadDeVariantes());
                    }
                    default -> { return false; }
                }
                actuales.set(seleccionado, a.conObjeto(o));
            }
            default -> { return false; }
        }
        guardarApliques(actuales);
        return true;
    }

    /** Con un Molde de textura: si la prenda ya la tiene, se la saca; si no, se la pone. El molde no se gasta. */
    /**
     * Ajusta la colocación, la oscilación y la blandura del aplique {@code indice} (2026-10-04, panel lateral de
     * la Mesa): llega por {@code AjustarApliquePayload} con valores ya acotados por los records. Para los modelos
     * del mod la cara base no cuenta (siempre se apoyan por atrás).
     */
    public boolean ajustar(int indice, Colocacion colocacion, Oscilacion oscilacion, float blandura) {
        List<Aplique> actuales = new ArrayList<>(apliques());
        if (indice < 0 || indice >= actuales.size() || !Float.isFinite(blandura)) return false;
        Aplique a = actuales.get(indice);
        // En la Mesa normal solo se toca la blandura (la colocación y el movimiento son de la creativa).
        if (!creativa()) {
            colocacion = a.colocacion();
            oscilacion = a.oscilacion();
        }
        if (a.objeto() == null) colocacion = colocacion.conCara(Colocacion.DEFECTO.caraBase());
        actuales.set(indice, a.conColocacion(colocacion).conOscilacion(oscilacion)
                .conBlandura(MathHelper.clamp(blandura, 0f, 1f)));
        guardarApliques(actuales);
        return true;
    }

    /** Los 3 colores que ofrece lo que hay en el slot del retazo (el retazo, o una muestra de color repetida), o null. */
    @org.jetbrains.annotations.Nullable
    public List<Integer> coloresDeLaFuente() {
        ItemStack fuente = items.get(SLOT_RETAZO);
        if (fuente.getItem() instanceof RetazoApliqueItem) return RetazoApliqueItem.colores(fuente);
        if (fuente.getItem() instanceof com.modamod.item.MuestraColorItem) {
            int[] m = com.modamod.item.MuestraColorItem.mezcla(fuente);
            if (m == null) return null;
            int rgb = com.modamod.item.MuestraColorItem.rgb(m);
            return List.of(rgb, rgb, rgb);
        }
        return null;
    }

    /** Las correas libres de la prenda de la Mesa. */
    public List<com.modamod.correa.Correa> correas() {
        List<com.modamod.correa.Correa> l = items.get(SLOT_PRENDA).get(ModamodComponents.CORREAS);
        return l == null ? List.of() : l;
    }

    /**
     * Pone una correa libre (2026-10-05, "correas libres"): los dos puntos son sobre la caja sin inflar de {@code parte}
     * (px, espacio local de la parte), el estilo sale del molde de correa de su slot y los colores del retazo (si hay;
     * no se gasta, "cuánto gasta lo vemos más adelante"). Solo sobre prendas y wearables con caja de cuerpo: no
     * sobre el sombrero ni la banda (que dibujan sus propias cajas) ni la malla de la pollera y la capa.
     */
    public boolean ponerCorrea(com.modamod.garment.Parte parte, com.modamod.correa.Correa.Punto desde,
                               com.modamod.correa.Correa.Punto hasta, com.modamod.correa.ModoCorrea modo, float ancho,
                               com.modamod.correa.Correa.Superficie superficie, float largo) {
        ItemStack prenda = items.get(SLOT_PRENDA), molde = items.get(SLOT_MOLDE);
        if (prenda.isEmpty() || !admite(prenda) || !(molde.getItem() instanceof com.modamod.correa.MoldeCorreaItem m)) return false;
        // La superficie tiene que ser la de la prenda: la malla de la pollera o de la capa, o las cajas (cuerpo, sombrero, banda).
        boolean pollera = prenda.getItem() instanceof com.modamod.item.PolleraItem;
        boolean capa = prenda.getItem() instanceof com.modamod.item.CapaItem;
        boolean delantal = prenda.getItem() instanceof com.modamod.item.DelantalItem;
        switch (superficie) {
            case POLLERA -> { if (!pollera) return false; }
            case CAPA -> { if (!capa) return false; }
            case DELANTAL -> { if (!delantal) return false; }
            default -> { if (pollera || capa || delantal) return false; }
        }
        // Anclada: un solo punto (el 2.º es el mismo).
        if (modo == com.modamod.correa.ModoCorrea.ANCLADA) hasta = desde;
        float[] v = {desde.x(), desde.y(), desde.z(), hasta.x(), hasta.y(), hasta.z(), ancho, largo};
        for (float f : v) if (!Float.isFinite(f)) return false;
        List<com.modamod.correa.Correa> actuales = new ArrayList<>(correas());
        if (actuales.size() >= com.modamod.correa.Correa.MAXIMO_POR_PRENDA) return false;
        if (superficie != com.modamod.correa.Correa.Superficie.CAJA) {
            // En la malla x, y son u, v de la tela (px de 64).
            parte = com.modamod.garment.Parte.TORSO;
            desde = new com.modamod.correa.Correa.Punto(MathHelper.clamp(desde.x(), 0, 64), MathHelper.clamp(desde.y(), 0, 64), 0, desde.cara());
            hasta = new com.modamod.correa.Correa.Punto(MathHelper.clamp(hasta.x(), 0, 64), MathHelper.clamp(hasta.y(), 0, 64), 0, hasta.cara());
        } else {
            desde = limitar(desde);
            hasta = limitar(hasta);
        }
        // Una correa de menos de 1 px no se ve: se rechaza.
        double dist = Math.sqrt((desde.x() - hasta.x()) * (desde.x() - hasta.x()) + (desde.y() - hasta.y()) * (desde.y() - hasta.y())
                + (desde.z() - hasta.z()) * (desde.z() - hasta.z()));
        if (dist < 1.0 && modo.dosPuntos()) return false;
        List<Integer> fuente = coloresDeLaFuente();
        List<Integer> colores = fuente != null ? List.of(fuente.get(0), fuente.get(1), fuente.get(2))
                : com.modamod.correa.Correa.DE_FABRICA;
        actuales.add(new com.modamod.correa.Correa(parte, desde, hasta, m.estilo, modo, ancho, colores, BLANDURA_INICIAL, superficie, largo));
        guardarCorreas(actuales);
        return true;
    }

    private void guardarCorreas(List<com.modamod.correa.Correa> l) {
        ItemStack prenda = items.get(SLOT_PRENDA);
        if (l.isEmpty()) prenda.remove(ModamodComponents.CORREAS);
        else prenda.set(ModamodComponents.CORREAS, List.copyOf(l));
        markDirty();
    }

    private static com.modamod.correa.Correa.Punto limitar(com.modamod.correa.Correa.Punto p) {
        return new com.modamod.correa.Correa.Punto(MathHelper.clamp(p.x(), -24, 24), MathHelper.clamp(p.y(), -24, 24),
                MathHelper.clamp(p.z(), -24, 24), p.cara());
    }

    /** Dibujo o color de una zona de un accesorio de 3 zonas; {@code izq}: el pie izquierdo de los borcegos. */
    private boolean botonDeZona(int id, boolean izq) {
        if (id >= BTN_PATRON_BASE && id < BTN_PATRON_BASE + 3) {
            ItemStack prenda = items.get(SLOT_PRENDA);
            if (!(prenda.getItem() instanceof com.modamod.item.ZonasTenibles z)) return false;
            int zona = id - BTN_PATRON_BASE;
            z.conPatronDe(prenda, zona, z.patronesDe(prenda, izq).get(zona).siguiente(), izq);
            markDirty();
            return true;
        }
        if (id >= BTN_COLOR_BASE && id <= BTN_COLOR_ENTERO) return colorearSombrero(id, izq);
        return false;
    }

    private boolean colorearSombrero(int id, boolean izq) {
        ItemStack prenda = items.get(SLOT_PRENDA);
        List<Integer> fuente = coloresDeLaFuente();
        if (!(prenda.getItem() instanceof com.modamod.item.ZonasTenibles z) || fuente == null) return false;
        List<Integer> actuales = z.coloresDe(prenda, izq);
        int[] nuevos = {actuales.get(0), actuales.get(1), actuales.get(2)};
        if (id == BTN_COLOR_ENTERO) {
            for (int i = 0; i < 3; i++) nuevos[i] = fuente.get(i);
        } else {
            int zona = (id - BTN_COLOR_BASE) / 4, color = (id - BTN_COLOR_BASE) % 4;
            if (zona > 2 || color > 2) return false;
            nuevos[zona] = fuente.get(color);
        }
        z.conColoresDe(prenda, nuevos[0], nuevos[1], nuevos[2], izq);
        markDirty();
        return true;
    }

    /** El registro de patrones de trim: el del mundo, o el del anfitrión si esta es el editor de la Estilista. */
    @org.jetbrains.annotations.Nullable
    private net.minecraft.registry.RegistryWrapper.WrapperLookup registros() {
        if (world != null) return world.getRegistryManager();
        if (anfitrion != null && anfitrion.getWorld() != null) return anfitrion.getWorld().getRegistryManager();
        return null;
    }

    /** El id del patrón de trim de este molde de vanilla (ej. {@code minecraft:snout}), o null si no es uno. */
    @org.jetbrains.annotations.Nullable
    public net.minecraft.util.Identifier patronDeTrim(ItemStack molde) {
        if (molde.isEmpty()) return null;
        net.minecraft.registry.RegistryWrapper.WrapperLookup reg = registros();
        if (reg == null) return null;
        return net.minecraft.item.trim.ArmorTrimPatterns.get(reg, molde)
                .flatMap(e -> e.getKey()).map(k -> k.getValue()).orElse(null);
    }

    /** El id del material de trim de este ítem de vanilla (ej. {@code minecraft:gold}), o null si no es uno. */
    @org.jetbrains.annotations.Nullable
    public net.minecraft.util.Identifier materialDe(ItemStack material) {
        if (material.isEmpty()) return null;
        net.minecraft.registry.RegistryWrapper.WrapperLookup reg = registros();
        if (reg == null) return null;
        return net.minecraft.item.trim.ArmorTrimMaterials.get(reg, material)
                .flatMap(e -> e.getKey()).map(k -> k.getValue()).orElse(null);
    }

    /**
     * Acabado con un molde de trim (2026-10-06, "en la de estilo pero no hace falta trim ya hay un slot de acabado"):
     * el mismo botón de la textura. Con el molde de trim en el slot, lo pone (y el molde se gasta: no se duplica) o,
     * si la prenda ya lleva ese patrón, lo quita. El material del slot de material (opcional, "dale opcional") le da la
     * paleta y también se gasta; sin material, la paleta de fábrica del patrón. Sin molde pero con material y un
     * acabado puesto, el botón cambia solo el material. {@code gratis()} no gasta nada.
     *
     * @param patron el patrón del molde del slot, o null si no hay molde de trim
     */
    private boolean aplicarAcabado(@org.jetbrains.annotations.Nullable net.minecraft.util.Identifier patron) {
        ItemStack prenda = items.get(SLOT_PRENDA);
        if (prenda.isEmpty() || !admiteAcabado(prenda)) return false;
        net.minecraft.util.Identifier mat = materialDe(items.get(SLOT_MATERIAL));
        net.minecraft.util.Identifier patronActual = prenda.get(ModamodComponents.ACABADO_TRIM);
        net.minecraft.util.Identifier matActual = prenda.get(ModamodComponents.ACABADO_MATERIAL);
        if (patron == null) {
            // Solo cambiar el material de un acabado que ya está.
            if (patronActual == null || mat == null || mat.equals(matActual)) return false;
            prenda.set(ModamodComponents.ACABADO_MATERIAL, mat);
            if (!gratis()) items.get(SLOT_MATERIAL).decrement(1);
            markDirty();
            return true;
        }
        boolean cambiaPatron = !patron.equals(patronActual);
        if (!cambiaPatron && (mat == null || mat.equals(matActual))) {
            prenda.remove(ModamodComponents.ACABADO_TRIM);
            prenda.remove(ModamodComponents.ACABADO_MATERIAL);
        } else {
            if (cambiaPatron) {
                prenda.set(ModamodComponents.ACABADO_TRIM, patron);
                if (!gratis()) items.get(SLOT_MOLDE).decrement(1);
                if (mat == null) prenda.remove(ModamodComponents.ACABADO_MATERIAL);   // paleta de fábrica
            }
            if (mat != null && (cambiaPatron || !mat.equals(matActual))) {
                prenda.set(ModamodComponents.ACABADO_MATERIAL, mat);
                if (!gratis()) items.get(SLOT_MATERIAL).decrement(1);
            }
        }
        markDirty();
        return true;
    }

    /** Qué prendas llevan acabado: las de tela que se dibujan por piezas (remera, chaqueta, pantalón, medias, calentabrazos). */
    /** Todas las prendas, la pollera, la capa, el delantal, el sombrero, las bandas y el calzado (2026-10-22, "vamos con todo"). */
    public static boolean admiteAcabado(ItemStack prenda) {
        return com.modamod.garment.Garments.esPrenda(prenda) || prenda.getItem() instanceof com.modamod.item.SombreroBrujaItem
                || prenda.getItem() instanceof com.modamod.item.BandaItem || prenda.getItem() instanceof com.modamod.item.BorcegosItem;
    }

    private boolean alternarTextura() {
        ItemStack prenda = items.get(SLOT_PRENDA);
        net.minecraft.util.Identifier trim = patronDeTrim(items.get(SLOT_MOLDE));
        if (trim != null) return aplicarAcabado(trim);
        if (!(items.get(SLOT_MOLDE).getItem() instanceof com.modamod.item.MoldeTexturaItem)
                && materialDe(items.get(SLOT_MATERIAL)) != null) return aplicarAcabado(null);
        if (prenda.isEmpty() || !(items.get(SLOT_MOLDE).getItem() instanceof com.modamod.item.MoldeTexturaItem m)) return false;
        if (prenda.get(ModamodComponents.TEXTURA_TELA) == m.textura) prenda.remove(ModamodComponents.TEXTURA_TELA);
        else prenda.set(ModamodComponents.TEXTURA_TELA, m.textura);
        markDirty();
        return true;
    }

    /** Todos los moldes que entran en la mesa, en el orden del registro. */
    private static List<net.minecraft.item.Item> moldes() {
        List<net.minecraft.item.Item> lista = new ArrayList<>();
        for (net.minecraft.item.Item item : net.minecraft.registry.Registries.ITEM) {
            if (item instanceof MoldeApliqueItem || item instanceof com.modamod.item.MoldeTexturaItem
                    || item instanceof com.modamod.correa.MoldeCorreaItem) lista.add(item);
        }
        return lista;
    }

    private boolean siguienteMolde() {
        if (!creativa()) return false;
        List<net.minecraft.item.Item> lista = moldes();
        if (lista.isEmpty()) return false;
        int i = lista.indexOf(items.get(SLOT_MOLDE).getItem());
        items.set(SLOT_MOLDE, new ItemStack(lista.get((i + 1) % lista.size())));
        markDirty();
        return true;
    }

    /** La mesa creativa viene con el primer molde puesto (los demás, con el botón). */
    @Override
    public void cargarCreativa() {
        if (items.get(SLOT_MOLDE).isEmpty() && !moldes().isEmpty()) {
            items.set(SLOT_MOLDE, new ItemStack(moldes().get(0)));
            markDirty();
        }
    }

    // ── GeckoLib ──────────────────────────────────────────────────────────
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ── Inventory ─────────────────────────────────────────────────────────
    @Override public int size() { return TAMANO; }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : items) if (!s.isEmpty()) return false;
        return true;
    }

    @Override public ItemStack getStack(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack r = Inventories.splitStack(items, slot, amount);
        if (!r.isEmpty()) {
            if (slot == SLOT_PRENDA) seleccionado = -1;
            markDirty();
        }
        return r;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack r = Inventories.removeStack(items, slot);
        if (slot == SLOT_PRENDA) seleccionado = -1;
        markDirty();
        return r;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        ItemStack antes = items.get(slot);
        items.set(slot, stack);
        // Solo se suelta la selección si cambió la PRENDA (2026-10-04, "cuando clickeo algo del panel lateral se me
        // deselecciona"): el servidor reenvía el slot cada vez que se ajusta un aplique (la prenda cambia de
        // componentes) y ese reenvío no es una prenda nueva.
        if (slot == SLOT_PRENDA && (antes.isEmpty() || stack.isEmpty() || !ItemStack.areItemsEqual(antes, stack))) {
            seleccionado = -1;
        }
        if (seleccionado >= apliques().size()) seleccionado = apliques().size() - 1;
        markDirty();
    }

    /**
     * Qué se puede estilar (2026-10-02, "extender apliques para toda armadura
     * o wearable vanilla o de mods"): las prendas del mod y cualquier ítem que
     * se pone — armaduras vanilla y de mods ({@code Equipment}: cascos,
     * pecheras, pantalones, botas, cabezas, élitros) y los de Trinkets.
     */
    public static boolean admite(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (Garments.esPrenda(stack)) return true;
        if (net.minecraft.item.Equipment.fromStack(stack) != null) return true;
        // El maletín (2026-10-23, "editable en la estacion de tintes y de apliques"): un accesorio de cajas más.
        if (stack.getItem() instanceof com.modamod.guardarropas.MaletinItem) return true;
        return stack.getItem() instanceof dev.emi.trinkets.api.Trinket;
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_PRENDA -> admite(stack);
            case SLOT_MOLDE -> stack.getItem() instanceof MoldeApliqueItem
                    || stack.getItem() instanceof com.modamod.aplique.MoldeApliquePersonalizadoItem
                    || stack.getItem() instanceof com.modamod.correa.MoldeCorreaItem
                    || stack.getItem() instanceof com.modamod.item.MoldeTexturaItem
                    || patronDeTrim(stack) != null;   // molde de trim de vanilla (2026-10-06)
            case SLOT_RETAZO -> stack.getItem() instanceof RetazoApliqueItem
                    || stack.getItem() instanceof com.modamod.item.MuestraColorItem;
            case SLOT_OBJETO -> creativa() && ObjetoAplique.admite(stack);
            case SLOT_MATERIAL -> materialDe(stack) != null;   // material de trim de vanilla (2026-10-06)
            default -> false;
        };
    }

    @Override
    public int getMaxCount(ItemStack stack) {
        return stack.getItem() instanceof RetazoApliqueItem || materialDe(stack) != null ? 64 : 1;
    }

    @Override
    public void clear() {
        items.clear();
        markDirty();
    }

    @Override
    public void markDirty() {
        super.markDirty();
        if (anfitrion != null) { anfitrion.sincronizarEditor(); return; }
        if (world != null && !world.isClient) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        if (anfitrion != null) return anfitrion.canPlayerUse(player);
        return world != null && world.getBlockEntity(pos) == this
                && player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    // ── pantalla ──────────────────────────────────────────────────────────
    @Override
    public Text getDisplayName() {
        return getCachedState().getBlock().getName();
    }

    @Override
    @Nullable
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new EstiladoScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
        return pos;
    }

    // ── persistencia y sincronización ─────────────────────────────────────
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        Inventories.writeNbt(nbt, items, registries);
        nbt.putInt("Seleccionado", seleccionado);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        items.clear();
        Inventories.readNbt(nbt, items, registries);
        seleccionado = nbt.contains("Seleccionado") ? nbt.getInt("Seleccionado") : -1;
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
