package com.modamod.sublimadora;

import net.minecraft.block.BlockState;
import com.modamod.maquina.MaquinaBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Sublimadora: bloque de 16x16x16 con tapa abisagrada.
 * El render lo hace GeckoLib desde el block entity, asi que para el
 * renderer de mundo el bloque es INVISIBLE (asi lo pide la wiki de GeckoLib).
 */
public class SublimadoraBlock extends MaquinaBlock {
    public static final MapCodec<SublimadoraBlock> CODEC = createCodec(SublimadoraBlock::new);

    public static final BooleanProperty OPEN = Properties.OPEN;

    public SublimadoraBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(OPEN, false));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(OPEN);
    }

    /**
     * Interaccion CON item en la mano. En 1.21.1 esto es lo que se llama
     * primero; onUse solo corre despues, y solo si aca devolvemos PASS.
     * Antes tenia esta logica dentro de onUse leyendo getActiveHand(), que es
     * la mano de "usar" un item (comer, tensar el arco) y no la de la
     * interaccion — por eso no matcheaba y terminaba cerrando la tapa.
     */
    @Override
    protected ItemActionResult usarConItem(ItemStack stack, BlockState state, World world,
                                             BlockPos pos, PlayerEntity player, Hand hand,
                                             BlockHitResult hit) {
        // Abajo (cualquier costado) se abre la pantalla aunque tengas algo en la mano (2026-10-08).
        if (queControl(state, pos, hit) != 0) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (world.isClient) return ItemActionResult.SUCCESS;
        if (!(world.getBlockEntity(pos) instanceof SublimadoraBlockEntity be)) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // Solo se carga con la tapa abierta.
        if (!state.get(OPEN)) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        // Si lo que tenes en la mano NO entra -el tanque lleno, una remera
        // cuando ya hay una- el click NO se consume y pasa a onUse, que abre
        // o cierra la tapa. Antes se lo tragaba y con un tinte en la mano no
        // habia forma de cerrar la maquina.
        int canal = canalDeTinte(stack);
        if (canal >= 0) {
            if (!be.cargarTinta(canal, 1)) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (!player.isCreative()) stack.decrement(1);
            sonar(world, pos, SoundEvents.ITEM_BUCKET_FILL, 0.8f);
            return ItemActionResult.CONSUME;
        }
        // Papel — a pedido (2026-09-19, "usa papel y ya no consume la
        // imagen"): tanque tipo tinta, se gasta 1 por prensado en vez de la
        // foto misma.
        if (stack.isOf(net.minecraft.item.Items.PAPER)) {
            if (!be.cargarPapel(1)) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (!player.isCreative()) stack.decrement(1);
            sonar(world, pos, SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0f);
            return ItemActionResult.CONSUME;
        }
        // Se acepta mientras le quede alguna cara sin estampar: una remera
        // con el frente hecho vuelve a entrar para imprimirle la espalda.
        if (ModItems.esEstampable(stack) && !tieneLasDosCaras(stack)) {
            if (!be.ponerPrenda(stack)) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (!player.isCreative()) stack.decrement(1);
            sonar(world, pos, SoundEvents.BLOCK_WOOL_PLACE, 1.0f);
            return ItemActionResult.CONSUME;
        }
        // La foto YA NO se carga con click derecho acá (2026-09-19, "las
        // imagenes deberian agregarse aqui no en la estampadora") — tiene
        // sus propios 2 slots (Frente/Espalda) en la pantalla nueva, ver
        // SublimadoraScreenHandler.
        return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Interaccion con la MANO VACIA: retirar, o abrir y cerrar la tapa. */
    @Override
    protected ActionResult usar(BlockState state, World world, BlockPos pos, PlayerEntity player,
                                 BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(world.getBlockEntity(pos) instanceof SublimadoraBlockEntity be)) return ActionResult.PASS;

        // La parte de abajo del frente (la BASE, que no se mueve cuando la
        // tapa se abre) abre la pantalla de escala/posición/cara — a pedido
        // (2026-09-19, "hace gui con preview... sacamos los controles del
        // frente"): antes esta misma zona tenía dos controles físicos
        // (palanca de modo + selector de cara), que ya no existen en el
        // modelo nuevo. Va primero que todo lo demás: es la única
        // interacción anclada a una parte concreta del bloque.
        // Con una prenda terminada esperando, el click en cualquier parte del cuerpo es de la tapa y de retirarla, no de la
        // pantalla (2026-10-15, "click abre la tapa click saca la prenda"): antes casi todo el cuerpo abría la GUI y no había
        // forma de sacarla sin entrar.
        if (queControl(state, pos, hit) != 0 && be.getSalida().isEmpty()) {
            // A pedido (2026-09-21, "mientras la maquina funciona no se
            // puede abrir la gui... te prende fuego"): antes este botón
            // abría la config SIN importar el estado, incluso prensando.
            if (be.getEstado() == SublimadoraBlockEntity.Estado.PRENSANDO) {
                quemarPorInterrumpir(world, pos, player);
                return ActionResult.CONSUME;
            }
            player.openHandledScreen(be);
            return ActionResult.CONSUME;
        }

        // Retirar NO puede tener prioridad sobre cerrar: si la tuviera, una vez
        // cargada la remera el click te la sacaria en vez de arrancar el
        // prensado, y no habria forma de empezar.
        //
        // - Remera lista (luz verde): el click la retira. Es lo que uno espera.
        // - Agachado: descarga lo que haya, para poder corregir.
        // - Si no: abre y cierra.
        if (state.get(OPEN) && (!be.getSalida().isEmpty() || player.isSneaking())) {
            ItemStack sacado = be.retirar();
            if (!sacado.isEmpty()) {
                player.getInventory().offerOrDrop(sacado);
                sonar(world, pos, SoundEvents.ENTITY_ITEM_PICKUP, 1.2f);
                return ActionResult.CONSUME;
            }
        }

        boolean abriendo = !state.get(OPEN);

        // La prensa esta apoyada y caliente: la tapa queda trabada hasta que
        // termine. Sin esto se podia abrir en medio del ciclo y ver la remera
        // flotando con el prensado corriendo igual por debajo.
        if (abriendo && be.getEstado() == SublimadoraBlockEntity.Estado.PRENSANDO) {
            quemarPorInterrumpir(world, pos, player);
            return ActionResult.CONSUME;
        }

        world.setBlockState(pos, state.with(OPEN, abriendo), Block.NOTIFY_ALL);
        sonar(world, pos, abriendo ? SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN
                                   : SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, 1.2f);

        // Cerrar la tapa es lo que dispara el prensado. Si no arranca, la
        // maquina lo dice: antes cerrabas la tapa, no pasaba nada, y no habia
        // forma de saber si faltaba tinta, la remera, la foto, o si estaba
        // rota. El silencio era indistinguible de un bug.
        if (!abriendo && !be.intentarPrensar()) {
            net.minecraft.text.Text aviso = be.queFalta();
            if (aviso != null) {
                sonar(world, pos, SoundEvents.BLOCK_DISPENSER_FAIL, 1.0f);
                player.sendMessage(aviso.copy().formatted(net.minecraft.util.Formatting.GOLD), true);
            }
        }
        return ActionResult.SUCCESS;
    }

    /**
     * Cual de los dos controles del frente recibio el click: 1 el slider de
     * modo, -1 el selector de cara, 0 ninguno.
     *
     * Los dos viven en la mitad de abajo del frente de la BASE, que no se
     * mueve cuando la tapa se abre. El de modo esta a un lado y el selector
     * al otro, asi que alcanza con mirar de que lado del frente cayo el
     * click.
     */
    private static int queControl(BlockState state, BlockPos pos, BlockHitResult hit) {
        if (hit.getSide() == Direction.UP) return 0;
        if (hit.getPos().y - pos.getY() >= 11.0 / 16.0) return 0;
        double lx = hit.getPos().x - pos.getX();
        double lz = hit.getPos().z - pos.getZ();
        double lateral = switch (state.get(FACING)) {
            case NORTH -> lx;
            case SOUTH -> 1.0 - lx;
            case WEST -> 1.0 - lz;
            default -> lz;          // EAST
        };
        // Invertido a proposito: GeckoLib dibuja el modelo ESPEJADO EN X, asi
        // que la palanca de modo, que en el modelo esta en x=+6, termina
        // cayendo del lado -x del bloque. Sin esta vuelta los dos controles
        // responden al reves de donde se ven.
        return lateral > 0.5 ? -1 : 1;
    }

    /** Package-private: reusado por SublimadoraBlockEntity para el slot de hopper de remera. */
    static boolean tieneLasDosCaras(ItemStack stack) {
        return RemeraItem.estampaDe(stack, Estampa.Cara.FRENTE) != null
                && RemeraItem.estampaDe(stack, Estampa.Cara.ESPALDA) != null;
    }

    /**
     * En que cara se estampa, segun en que mitad de la plancha soltaste la
     * foto: la mitad del lado del panel es el frente de la remera, la de
     * atras es la espalda.
     *
     * Se eligio esto y no un boton porque no necesita ni estado nuevo en el
     * bloque ni un indicador en el modelo, y porque apoyar la foto sobre la
     * mitad que queres imprimir es lo que harias con una sublimadora de
     * verdad.
     */
    private static Estampa.Cara caraSegunDondeClickeaste(BlockState state, BlockPos pos, BlockHitResult hit) {
        double lx = hit.getPos().x - pos.getX();
        double lz = hit.getPos().z - pos.getZ();
        double haciaElPanel = switch (state.get(FACING)) {
            case NORTH -> 1.0 - lz;
            case SOUTH -> lz;
            case WEST -> 1.0 - lx;
            default -> lx;          // EAST
        };
        return haciaElPanel > 0.5 ? Estampa.Cara.FRENTE : Estampa.Cara.ESPALDA;
    }

    private static void sonar(World world, BlockPos pos, net.minecraft.sound.SoundEvent ev, float tono) {
        world.playSound(null, pos, ev, SoundCategory.BLOCKS, 0.6f, tono);
    }

    /**
     * Consecuencia de meter mano en la prensa mientras está caliente
     * (2026-09-21, "la sublimadora te prende fuego") — reemplaza el
     * viejo mensaje "trabada" (que solo avisaba, sin costo) en los dos
     * lugares donde antes se podía interrumpir: abrir la tapa a mitad de
     * prensado, y el botón de config. Mismo espíritu que
     * {@code ModeladoBlock}/{@code TinturasBlock}, cada una con su
     * propio sabor de peligro.
     */
    @Override
    protected void castigar(World world, BlockPos pos, PlayerEntity player) {
        quemarPorInterrumpir(world, pos, player);
    }

    private static void quemarPorInterrumpir(World world, BlockPos pos, PlayerEntity player) {
        player.setOnFireFor(4);
        world.playSound(null, pos, net.minecraft.sound.SoundEvents.ENTITY_GENERIC_BURN, SoundCategory.BLOCKS, 1.0f, 1.0f);
        player.sendMessage(net.minecraft.text.Text.translatable("modamod.sublimadora.aviso.procesando")
                .formatted(net.minecraft.util.Formatting.RED), true);
    }

    /**
     * Los 4 tintes vanilla, en el mismo orden que {@code SublimadoraBlockEntity.C/M/Y/K}
     * — reusado por {@link SublimadoraBlockEntity} para exponer el tanque
     * como slot de hopper (2026-09-20, "que carguen por hopper atrás").
     */
    static final net.minecraft.item.Item[] TINTES = {
            net.minecraft.item.Items.CYAN_DYE, net.minecraft.item.Items.MAGENTA_DYE,
            net.minecraft.item.Items.YELLOW_DYE, net.minecraft.item.Items.BLACK_DYE };

    /** Canal CMYK del tinte que se tenga en la mano, o -1. Package-private: reusado por SublimadoraBlockEntity. */
    static int canalDeTinte(ItemStack stack) {
        if (stack.getItem() == net.minecraft.item.Items.CYAN_DYE) return SublimadoraBlockEntity.C;
        if (stack.getItem() == net.minecraft.item.Items.MAGENTA_DYE) return SublimadoraBlockEntity.M;
        if (stack.getItem() == net.minecraft.item.Items.YELLOW_DYE) return SublimadoraBlockEntity.Y;
        if (stack.getItem() == net.minecraft.item.Items.BLACK_DYE) return SublimadoraBlockEntity.K;
        return -1;
    }

    /**
     * Reconoce la foto de Camerapture SIN depender del mod: se compara el id
     * registrado. Asi la sublimadora compila y corre igual si Camerapture no
     * esta instalado — simplemente no vas a tener fotos que poner.
     */
    // Package-private (no "private") — SublimadoraBlockEntity los reusa
    // para el slot de foto de la pantalla nueva (2026-09-19, "las imagenes
    // deberian agregarse aqui no en la estampadora").
    static boolean esFoto(ItemStack stack) {
        return net.minecraft.registry.Registries.ITEM.getId(stack.getItem())
                .toString().equals("camerapture:picture");
    }

    /**
     * UUID de la foto, leido del componente PICTURE_DATA de Camerapture.
     *
     * El acceso vive en una clase aparte para que la JVM no la resuelva si
     * Camerapture no esta instalado — mismo patron que el puente con 3D Skin
     * Layers en modamod.
     */
    static java.util.UUID uuidDeFoto(ItemStack stack) {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("camerapture")) return null;
        try {
            return CameraptureCompat.uuidDe(stack);
        } catch (Throwable ignorado) {
            return null;   // cambio de version del mod: mejor sin estampa que crashear
        }
    }
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SublimadoraBlockEntity(pos, state);
    }

    /** Ticker en los dos lados: el cliente lo necesita para suavizar las barras de tinta. */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return validateTicker(type, ModBlocks.SUBLIMADORA_ENTITY, SublimadoraBlockEntity::tick);
    }
}
