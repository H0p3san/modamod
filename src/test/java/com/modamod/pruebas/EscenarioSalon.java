package com.modamod.pruebas;

import com.modamod.guardarropas.GuardarropasBlockEntity;
import com.modamod.item.Calce;
import com.modamod.item.ModamodComponents;
import com.modamod.item.ModamodItems;
import com.modamod.maniqui.ManiquiBlock;
import com.modamod.maniqui.ManiquiBlockEntity;
import com.modamod.maniqui.ManiquiMod;
import com.modamod.sublimadora.ModBlocks;
import com.modamod.sublimadora.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SignBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameRules;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static com.modamod.pruebas.Corrida.enServidor;
import static com.modamod.pruebas.Corrida.jugador;

/**
 * Salon de pruebas para recorrer a mano (./gradlew.bat runSalon): crea el mundo "ModaMod Salon" en run/saves (el cliente de
 * desarrollo, no el perfil de Modrinth) y se cierra solo. Maniquies con todas las prendas con calce, una columna por calce
 * (Pegado, Ajustado, Normal, Suelto, Oversize), dos filas (conjunto con pantalon y con pollera) con el color de cada prenda al azar, un cartel por fila y
 * columna, y a un costado las maquinas creativas. Sin mobs, siempre de dia. Se regenera de cero cada vez.
 */
public final class EscenarioSalon {
    public static final String NOMBRE = "ModaMod Salon";
    private static final Calce[] CALCES = {Calce.PEGADO, Calce.AJUSTADO, Calce.NORMAL, Calce.SUELTO, Calce.OVERSIZE};
    private static final String[] NOMBRES_CALCE = {"Pegado", "Ajustado", "Normal", "Suelto", "Oversize"};
    private static final java.util.Random AZAR = new java.util.Random();

    /** Color de base al azar (a pedido, 2026-10-19: "aleatorio"): saturado y no muy oscuro, para que se vea el calce. */
    private static int colorAlAzar() {
        return java.awt.Color.HSBtoRGB(AZAR.nextFloat(), 0.45f + 0.5f * AZAR.nextFloat(), 0.6f + 0.35f * AZAR.nextFloat()) & 0xFFFFFF;
    }
    private static final int PASO = 4;

    private EscenarioSalon() {}

    private static ItemStack prenda(ItemStack s, Calce calce) {
        s.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(colorAlAzar(), true));
        if (calce != Calce.NORMAL) s.set(ModamodComponents.CALCE, calce);
        return s;
    }

    private static void texto(ServerWorld w, BlockPos pos, int rotacion, String... lineas) {
        BlockState estado = Blocks.OAK_SIGN.getDefaultState().with(SignBlock.ROTATION, rotacion);
        w.setBlockState(pos, estado);
        if (w.getBlockEntity(pos) instanceof SignBlockEntity cartel) {
            SignText armado = new SignText();
            for (int i = 0; i < lineas.length && i < 4; i++) armado = armado.withMessage(i, Text.literal(lineas[i]));
            final SignText listo = armado;
            cartel.changeText(x -> listo, true);
        }
    }

    private static void maniqui(ServerWorld w, BlockPos pos, boolean pollera, Calce calce) {
        w.setBlockState(pos, ManiquiMod.MANIQUI_BLOCK.getDefaultState().with(ManiquiBlock.FACING, Direction.NORTH));
        if (!(w.getBlockEntity(pos) instanceof ManiquiBlockEntity be)) return;
        int c = GuardarropasBlockEntity.POR_CATEGORIA;
        be.setStack(GuardarropasBlockEntity.REMERA * c, prenda(new ItemStack(ModItems.REMERA), calce));
        be.setStack(GuardarropasBlockEntity.PANTALON * c, prenda(new ItemStack(pollera ? ModamodItems.POLLERA : ModamodItems.PANTALON), calce));
        be.setStack(GuardarropasBlockEntity.MEDIAS * c, prenda(new ItemStack(ModamodItems.SOCKS_SOLID), calce));
        if (pollera) be.setStack(GuardarropasBlockEntity.SLOT_DELANTAL, prenda(new ItemStack(ModamodItems.DELANTAL), calce));
        else be.setStack(GuardarropasBlockEntity.CALIENTABRAZOS * c, prenda(new ItemStack(ModamodItems.CALIENTABRAZOS), calce));
        be.setStack(GuardarropasBlockEntity.SLOT_CALZADO, new ItemStack(ModamodItems.BORCEGOS));
        if (pollera) {
            // Busto grande en el conjunto con delantal (el delantal con busto: reporte 2026-10-19). Campo privado: reflexion.
            try {
                var f = ManiquiBlockEntity.class.getDeclaredField("busto");
                f.setAccessible(true);
                f.setInt(be, 5);
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        w.updateListeners(pos, w.getBlockState(pos), w.getBlockState(pos), 3);
    }

    /** Top con el frente abierto sobre busto grande, uno por calce (2026-10-20, "en una chaqueta con frente abierto las tetas salen partidas"). */
    private static void maniquiAbierto(ServerWorld w, BlockPos pos, Calce calce) {
        w.setBlockState(pos, ManiquiMod.MANIQUI_BLOCK.getDefaultState().with(ManiquiBlock.FACING, Direction.NORTH));
        if (!(w.getBlockEntity(pos) instanceof ManiquiBlockEntity be)) return;
        ItemStack top = prenda(new ItemStack(ModItems.REMERA), calce);
        com.modamod.item.TopCorte.setFrente(top, com.modamod.item.ChaquetaFrente.ABIERTA);
        be.setStack(GuardarropasBlockEntity.CHAQUETA * GuardarropasBlockEntity.POR_CATEGORIA, top);
        try {
            var f = ManiquiBlockEntity.class.getDeclaredField("busto");
            f.setAccessible(true);
            f.setInt(be, 5);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        w.updateListeners(pos, w.getBlockState(pos), w.getBlockState(pos), 3);
    }

    /** Tubos de transporte (2026-10-20): tres tubos hacia arriba, un empalme de tubo con filtros y un tramo horizontal con cofre. */
    private static void tubos(ServerWorld w, BlockPos base) {
        for (int i = 0; i < 3; i++) w.setBlockState(base.up(i), com.modamod.cinta.CintaMod.TUBO_BLOCK.getDefaultState());
        com.modamod.cinta.TuboBlock.llenar(w, base, true);
        BlockPos nudo = base.up(3);
        w.setBlockState(nudo, com.modamod.cinta.CintaMod.EMPALME_TUBO_BLOCK.getDefaultState());
        w.setBlockState(nudo.east(), com.modamod.cinta.CintaMod.TUBO_BLOCK.getDefaultState());
        w.setBlockState(nudo.east(2), Blocks.CHEST.getDefaultState());
        w.setBlockState(nudo.west(), Blocks.CHEST.getDefaultState());
        // Bocas hacia una tolva y hacia una cinta (2026-10-21, "tiene que conectar fisicamente con un contenedor"): se alargan hasta tocarlas.
        for (int k = 0; k < 2; k++) {
            BlockPos col = base.south(3 + 2 * k).east(1);
            w.setBlockState(col, k == 0 ? Blocks.HOPPER.getDefaultState() : com.modamod.cinta.CintaMod.CINTA_BLOCK.getDefaultState());
            w.setBlockState(col.up(), com.modamod.cinta.CintaMod.TUBO_BLOCK.getDefaultState());
            w.setBlockState(col.up(2), com.modamod.cinta.CintaMod.TUBO_BLOCK.getDefaultState());
        }
        // Un extractor (2026-10-21) que saca piedra de un cofre y la mete de costado al tubo de abajo, y cintas para ver los items.
        w.setBlockState(base.west(2), Blocks.CHEST.getDefaultState());
        if (w.getBlockEntity(base.west(2)) instanceof net.minecraft.inventory.Inventory cofre) cofre.setStack(0, new ItemStack(net.minecraft.item.Items.COBBLESTONE, 64));
        w.setBlockState(base.west(), com.modamod.cinta.CintaMod.EXTRACTOR_BLOCK.getDefaultState().with(com.modamod.cinta.ExtractorBlock.FACING, Direction.EAST));
        if (w.getBlockEntity(base.west()) instanceof com.modamod.cinta.ExtractorBlockEntity ext) ext.setFiltro(new ItemStack(net.minecraft.item.Items.COBBLESTONE));
        for (int i = 0; i < 3; i++) {
            w.setBlockState(base.west(4 + i), com.modamod.cinta.CintaMod.CINTA_BLOCK.getDefaultState().with(com.modamod.cinta.CintaBlock.FACING, Direction.WEST)
                    .with(com.modamod.cinta.CintaBlock.POWERED, true));
        }
        if (w.getBlockEntity(nudo) instanceof com.modamod.cinta.EmpalmeTuboBlockEntity be) {
            be.setFiltro(Direction.NORTH, new ItemStack(net.minecraft.item.Items.DIAMOND));
            be.setFiltro(Direction.SOUTH, new ItemStack(net.minecraft.item.Items.OAK_PLANKS));
            be.alternarTag(Direction.SOUTH);
            be.setFiltro(Direction.UP, new ItemStack(net.minecraft.item.Items.IRON_INGOT));
        }
    }

    /** Medias con sandalias y calientabrazos cortos (2026-10-21, "el piecito es un cuadrado despegado de la pierna" y "pintan la tapa del hombro"). */
    private static void maniquiExtremidades(ServerWorld w, BlockPos pos) {
        w.setBlockState(pos, ManiquiMod.MANIQUI_BLOCK.getDefaultState().with(ManiquiBlock.FACING, Direction.NORTH));
        if (!(w.getBlockEntity(pos) instanceof ManiquiBlockEntity be)) return;
        int c = GuardarropasBlockEntity.POR_CATEGORIA;
        be.setStack(GuardarropasBlockEntity.MEDIAS * c, prenda(new ItemStack(ModamodItems.SOCKS_SOLID), Calce.OVERSIZE));
        // Solo medias, SIN calzado (2026-10-21, "lo del pie es con medias solamente, sin calzado"): se ve la punta de la media.
        ItemStack cal = prenda(new ItemStack(ModamodItems.CALIENTABRAZOS), Calce.AJUSTADO);
        cal.set(com.modamod.item.ModamodComponents.CALIENTABRAZOS_COBERTURA_INFERIOR, com.modamod.sublimadora.Variante.Manga.CORTA);
        be.setStack(GuardarropasBlockEntity.CALIENTABRAZOS * c, cal);
        w.updateListeners(pos, w.getBlockState(pos), w.getBlockState(pos), 3);
    }

    private static void maniquiCompleto(ServerWorld w, BlockPos pos) {
        w.setBlockState(pos, ManiquiMod.MANIQUI_BLOCK.getDefaultState().with(ManiquiBlock.FACING, Direction.NORTH));
        if (!(w.getBlockEntity(pos) instanceof ManiquiBlockEntity be)) return;
        int c = GuardarropasBlockEntity.POR_CATEGORIA;
        ItemStack top = prenda(new ItemStack(ModItems.REMERA), Calce.NORMAL);
        com.modamod.item.TopCorte.setConCapucha(top, true);
        com.modamod.item.TopCorte.setCapuchaArriba(top, true);
        com.modamod.item.TopCorte.setFaldon(top, 2);
        be.setStack(GuardarropasBlockEntity.CHAQUETA * c, top);
        be.setStack(GuardarropasBlockEntity.REMERA * c, prenda(new ItemStack(ModItems.REMERA), Calce.AJUSTADO));
        be.setStack(GuardarropasBlockEntity.PANTALON * c, prenda(new ItemStack(ModamodItems.PANTALON), Calce.SUELTO));
        be.setStack(GuardarropasBlockEntity.PANTALON * c + 1, prenda(new ItemStack(ModamodItems.POLLERA), Calce.NORMAL));
        be.setStack(GuardarropasBlockEntity.MEDIAS * c, prenda(new ItemStack(ModamodItems.SOCKS_SOLID), Calce.AJUSTADO));
        be.setStack(GuardarropasBlockEntity.CALIENTABRAZOS * c, prenda(new ItemStack(ModamodItems.CALIENTABRAZOS), Calce.NORMAL));
        be.setStack(GuardarropasBlockEntity.SLOT_DELANTAL, prenda(new ItemStack(ModamodItems.DELANTAL), Calce.NORMAL));
        be.setStack(GuardarropasBlockEntity.SLOT_SOMBRERO, new ItemStack(ModamodItems.SOMBRERO_BRUJA));
        be.setStack(GuardarropasBlockEntity.SLOT_CALZADO, new ItemStack(ModamodItems.BORCEGOS));
        be.setStack(ManiquiBlockEntity.SLOT_CAPA, prenda(new ItemStack(ModamodItems.CAPA), Calce.NORMAL));
        ItemStack cinto = new ItemStack(ModamodItems.BANDA);
        com.modamod.item.BandaItem.setZona(cinto, com.modamod.item.BandaZona.CINTURA);
        be.setStack(ManiquiBlockEntity.SLOT_CINTO, cinto);
        ItemStack collar = new ItemStack(ModamodItems.BANDA);
        com.modamod.item.BandaItem.setZona(collar, com.modamod.item.BandaZona.CUELLO);
        be.setStack(ManiquiBlockEntity.SLOT_COLLAR, collar);
        try {
            var f = ManiquiBlockEntity.class.getDeclaredField("twirl");
            f.setAccessible(true);
            f.setBoolean(be, true);
            var g = ManiquiBlockEntity.class.getDeclaredField("busto");
            g.setAccessible(true);
            g.setInt(be, 4);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        w.updateListeners(pos, w.getBlockState(pos), w.getBlockState(pos), 3);
    }

    private static void maquina(ServerWorld w, BlockPos pos, Block bloque, String nombre) {
        BlockState estado = bloque.getDefaultState();
        if (estado.contains(Properties.HORIZONTAL_FACING)) estado = estado.with(Properties.HORIZONTAL_FACING, Direction.EAST);
        w.setBlockState(pos, estado);
        com.modamod.util.MaquinaCreativa.alColocar(w, pos, estado, ItemStack.EMPTY);
        texto(w, pos.add(2, 0, 0), 12, nombre, "(creativa)");
    }

    private static void armarSalon(MinecraftClient c) {
        enServidor(c, () -> {
            ServerWorld w = jugador(c).getServerWorld();
            int y = jugador(c).getBlockPos().getY();
            int filas = 2;
            // Plataforma lisa y despejada (las maquinas van en fila a la izquierda, de ahi el largo).
            for (int x = -14; x <= 22; x++) for (int z = -8; z <= 6 * PASO + 2; z++) {
                w.setBlockState(new BlockPos(x, y - 1, z), Blocks.SMOOTH_STONE.getDefaultState());
                for (int dy = 0; dy < 6; dy++) w.setBlockState(new BlockPos(x, y + dy, z), Blocks.AIR.getDefaultState());
            }
            for (int i = 0; i < CALCES.length; i++) texto(w, new BlockPos(i * PASO + 2, y, -3), 8, NOMBRES_CALCE[i]);
            for (int tipo = 0; tipo < filas; tipo++) {
                int z = tipo * PASO;
                texto(w, new BlockPos(-2, y, z), 12, tipo == 0 ? "Pantalon" : "Pollera", tipo == 0 ? "+ calientabrazos" : "+ delantal", "(colores al azar)");
                for (int i = 0; i < CALCES.length; i++) maniqui(w, new BlockPos(i * PASO + 2, y, z), tipo == 1, CALCES[i]);
            }
            // Empalme con filtros (2026-10-19): izquierda diamante, derecha tablones por tag, atras piedra y tolva hierro.
            BlockPos ep = new BlockPos(6, y, -6);
            // Pausado (POWERED) y con un item adentro, para ver que se distingue por debajo de la plataforma.
            w.setBlockState(ep, com.modamod.cinta.CintaMod.EMPALME_BLOCK.getDefaultState().with(com.modamod.cinta.EmpalmeBlock.FACING, Direction.EAST).with(com.modamod.cinta.EmpalmeBlock.POWERED, true));
            if (w.getBlockEntity(ep) instanceof com.modamod.cinta.EmpalmeBlockEntity eb) {
                eb.setFiltro(com.modamod.cinta.EmpalmeBlockEntity.IZQUIERDA, new ItemStack(net.minecraft.item.Items.DIAMOND));
                eb.setFiltro(com.modamod.cinta.EmpalmeBlockEntity.DERECHA, new ItemStack(net.minecraft.item.Items.SPRUCE_PLANKS));
                eb.alternarTag(com.modamod.cinta.EmpalmeBlockEntity.DERECHA);
                eb.setFiltro(com.modamod.cinta.EmpalmeBlockEntity.ATRAS, new ItemStack(net.minecraft.item.Items.STONE));
                eb.setFiltro(com.modamod.cinta.EmpalmeBlockEntity.TOLVA, new ItemStack(net.minecraft.item.Items.IRON_INGOT));
                eb.setStack(0, new ItemStack(net.minecraft.item.Items.COAL));
            }
            // El maniqui completo (2026-10-19): todos los slots llenos y el twirl prendido, a la derecha de las columnas de calce.
            maniquiCompleto(w, new BlockPos(CALCES.length * PASO + 2, y, 0));
            // Chaquetas abiertas (Normal, Suelto, Oversize) a la derecha del completo.
            maniquiAbierto(w, new BlockPos(CALCES.length * PASO + 6, y, 0), Calce.NORMAL);
            maniquiAbierto(w, new BlockPos(CALCES.length * PASO + 10, y, 0), Calce.SUELTO);
            maniquiAbierto(w, new BlockPos(CALCES.length * PASO + 14, y, 0), Calce.OVERSIZE);
            maniquiExtremidades(w, new BlockPos(CALCES.length * PASO + 28, y, 0));
            tubos(w, new BlockPos(CALCES.length * PASO + 20, y, 0));
            // Maquinas creativas a la izquierda, en fila, mirando al salon.
            Object[][] maquinas = {
                    {com.modamod.modelado.ModeladoMod.MODELADO_CREATIVA, "Modeladora"},
                    {com.modamod.tinturas.TinturasMod.TINTURAS_CREATIVA, "Estacion de Tintes"},
                    {com.modamod.telar.TelarMod.TELAR_CREATIVA, "Telar"},
                    {ModBlocks.SUBLIMADORA_CREATIVA, "Sublimadora"},
                    {com.modamod.estilista.EstilistaMod.ESTILISTA_CREATIVA, "Estilista"},
                    {com.modamod.guardarropas.GuardarropasMod.GUARDARROPAS_BLOCK, "Guardarropas"}};
            for (int i = 0; i < maquinas.length; i++) maquina(w, new BlockPos(-10, y, i * PASO), (Block) maquinas[i][0], (String) maquinas[i][1]);
            texto(w, new BlockPos(-8, y, -3), 8, "ModaMod Salon", "Columnas: calce", "Filas: tipo de", "conjunto");
            w.setSpawnPos(new BlockPos(8, y, -6), 0f);
            return null;
        });
    }

    public static Corrida armar() {
        Corrida r = new Corrida("salon");
        r.hacer("borrar el salon anterior", 0, c -> {
            Path carpeta = c.runDirectory.toPath().resolve("saves").resolve(NOMBRE);
            // Solo la carpeta con este nombre exacto, dentro de run/saves.
            if (Files.isDirectory(carpeta) && carpeta.getParent().getFileName().toString().equals("saves")) {
                try (var s = Files.walk(carpeta)) { s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete()); }
            }
        });
        GameRules reglas = new GameRules();
        reglas.get(GameRules.DO_MOB_SPAWNING).set(false, null);
        reglas.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, null);
        reglas.get(GameRules.DO_WEATHER_CYCLE).set(false, null);
        r.mundo(NOMBRE, reglas);
        r.hacer("limpiar corrida anterior", 0, Corrida::limpiar);
        r.hacer("sin pausa al perder el foco", 0, c -> c.options.pauseOnLostFocus = false);
        r.hacer("armar maniquies, carteles y maquinas", 60, EscenarioSalon::armarSalon);
        r.hacer("mediodia y cielo despejado", 0, c -> enServidor(c, () -> { jugador(c).getServerWorld().setTimeOfDay(6000); return null; }));
        r.hacer("poner al jugador en la entrada", 30, c -> enServidor(c, () -> {
            var sp = jugador(c);
            sp.requestTeleport(8.5, sp.getY(), -5.5);
            return null;
        }));
        r.dejarPasar(60).captura("salon-entrada");
        // Detalle de los borcegos desde atras y de costado (reporte: "estan destapados por atras, les falta cierre").
        r.hacer("acercarse a los pies del primer maniqui (espalda)", 0, c -> {
            enServidor(c, () -> { jugador(c).requestTeleport(2.5, jugador(c).getY(), 2.9); return null; });
            c.player.setYaw(180);
            c.player.setPitch(24);
            c.options.hudHidden = true;
        });
        r.dejarPasar(40).captura("salon-borcegos-espalda");
        r.hacer("de costado", 0, c -> {
            enServidor(c, () -> { jugador(c).requestTeleport(4.6, jugador(c).getY(), 0.5); return null; });
            c.player.setYaw(90);
            c.player.setPitch(24);
        });
        r.dejarPasar(40).captura("salon-borcegos-costado");
        // Pantalones sueltos y oversize de frente (reporte: "rotura escalonada en el maniqui"): fila 0, columnas Suelto y Oversize.
        for (int col : new int[]{3, 4}) {
            r.hacer("de frente al pantalon, columna " + NOMBRES_CALCE[col], 0, c -> {
                enServidor(c, () -> { jugador(c).requestTeleport(col * PASO + 2.5, jugador(c).getY(), -2.2); return null; });
                c.player.setYaw(0);
                c.player.setPitch(32);
            });
            r.dejarPasar(40).captura("salon-pantalon-" + NOMBRES_CALCE[col].toLowerCase());
        }
        for (int lado = 0; lado < 2; lado++) {
            final int l = lado;
            r.hacer("de " + (l == 0 ? "frente" : "espalda") + " al maniqui completo", 0, c -> {
                enServidor(c, () -> { jugador(c).requestTeleport(CALCES.length * PASO + 2.5, jugador(c).getY(), l == 0 ? -2.6 : 3.8); return null; });
                c.player.setYaw(l == 0 ? 0 : 180);
                c.player.setPitch(5);
            });
            r.dejarPasar(40).captura("salon-maniqui-completo-" + (l == 0 ? "frente" : "espalda"));
        }
        for (int k = 0; k < 3; k++) {
            final int kk = k;
            r.hacer("de frente a la chaqueta abierta " + k, 0, c -> {
                enServidor(c, () -> { jugador(c).requestTeleport(CALCES.length * PASO + 6.5 + 4 * kk, jugador(c).getY(), -2.6); return null; });
                c.player.setYaw(0);
                c.player.setPitch(-4);
            });
            r.dejarPasar(40).captura("salon-chaqueta-abierta-" + new String[]{"normal", "suelto", "oversize"}[k]);
        }
        r.hacer("de frente a los tubos", 0, c -> {
            enServidor(c, () -> { jugador(c).requestTeleport(CALCES.length * PASO + 20.5, jugador(c).getY(), -3.5); return null; });
            c.player.setYaw(0);
            c.player.setPitch(-20);
        });
        r.dejarPasar(40).captura("salon-tubos");
        // Items viajando (2026-10-21, "hay items que viajan por la superficie del tubo... otros como la ropa aparecen planos"): hilo,
        // una remera y un bloque en las cintas (paradas) y en los tubos.
        r.hacer("poner hilo, remera y bloque en las cintas y los tubos", 0, c -> enServidor(c, () -> {
            var w = jugador(c).getServerWorld();
            BlockPos base = new BlockPos(CALCES.length * PASO + 20, (int) Math.floor(jugador(c).getY()), 0);
            ItemStack[] cosas = {new ItemStack(net.minecraft.item.Items.STRING), prenda(new ItemStack(ModItems.REMERA), Calce.NORMAL),
                    new ItemStack(net.minecraft.item.Items.OAK_LOG)};
            for (int i = 0; i < 3; i++) {
                if (w.getBlockEntity(base.west(4 + i)) instanceof com.modamod.cinta.CintaBlockEntity cinta) cinta.setStack(0, cosas[i].copy());
                if (w.getBlockEntity(base.up(i)) instanceof com.modamod.cinta.TuboBlockEntity tubo) tubo.setStack(0, cosas[i].copy());
            }
            return null;
        }));
        r.dejarPasar(8).captura("salon-tubos-con-items");
        // De cerca (2026-10-21, "que las tapas tengan una abertura visible... y conecten con el extractor" + "esquinas recubiertas"):
        // el codo de abajo con el extractor, y el empalme con su boca hacia el cofre.
        r.hacer("de cerca al codo y al extractor", 0, c -> {
            enServidor(c, () -> {
                var j = jugador(c);
                j.getAbilities().allowFlying = true; j.getAbilities().flying = true; j.sendAbilitiesUpdate();
                j.requestTeleport(CALCES.length * PASO + 19.0, j.getY() + 0.6, -1.6);
                return null;
            });
            c.player.setYaw(-30);
            c.player.setPitch(30);
        });
        r.dejarPasar(20).captura("salon-tubos-codo-extractor");
        r.hacer("de cerca al empalme y su boca", 0, c -> {
            enServidor(c, () -> { jugador(c).requestTeleport(CALCES.length * PASO + 18.6, jugador(c).getY() + 2.6, -1.4); return null; });
            c.player.setYaw(-35);
            c.player.setPitch(25);
        });
        r.dejarPasar(20).captura("salon-tubos-empalme-boca");
        r.hacer("volver al piso", 0, c -> enServidor(c, () -> {
            var j = jugador(c);
            j.getAbilities().flying = false; j.sendAbilitiesUpdate();
            j.requestTeleport(j.getX(), j.getY() - 3.2, j.getZ());
            return null;
        }));
        r.dejarPasar(20);
        r.hacer("de costado a las cintas", 0, c -> {
            enServidor(c, () -> { jugador(c).requestTeleport(CALCES.length * PASO + 15.5, jugador(c).getY(), -2.0); return null; });
            c.player.setYaw(0);
            c.player.setPitch(45);
        });
        r.dejarPasar(10).captura("salon-cintas-con-items");
        // Calientabrazos y pies (2026-10-21, "los arm warmers me pintan la tapa de arriba del brazo y los pies se ven muy feos").
        r.hacer("desde arriba al hombro del maniqui completo", 0, c -> {
            enServidor(c, () -> {
                var j = jugador(c);
                j.getAbilities().allowFlying = true; j.getAbilities().flying = true; j.sendAbilitiesUpdate();
                j.requestTeleport(CALCES.length * PASO + 3.4, j.getY() + 1.6, -1.4);
                return null;
            });
            c.player.setYaw(35);
            c.player.setPitch(40);
        });
        r.dejarPasar(30).captura("salon-hombro-desde-arriba");
        r.hacer("a los pies del maniqui completo", 0, c -> {
            enServidor(c, () -> { jugador(c).requestTeleport(CALCES.length * PASO + 2.5, jugador(c).getY() - 1.0, -1.5); return null; });
            c.player.setYaw(0);
            c.player.setPitch(50);
        });
        r.dejarPasar(30).captura("salon-pies-frente");
        r.hacer("a los pies de costado", 0, c -> {
            enServidor(c, () -> { jugador(c).requestTeleport(CALCES.length * PASO + 3.9, jugador(c).getY(), -0.3); return null; });
            c.player.setYaw(90);
            c.player.setPitch(40);
        });
        r.dejarPasar(30).captura("salon-pies-costado");
        // El maletin modelado (2026-10-21): flotando frente a la camara y en la mano.
        // Extremidades: el hombro desde arriba y el pie con la media, de cerca y de costado.
        r.hacer("de cerca a las extremidades", 0, c -> {
            c.options.pauseOnLostFocus = false; if (c.currentScreen != null) c.setScreen(null);
            enServidor(c, () -> {
                var j = jugador(c);
                j.getAbilities().allowFlying = true; j.getAbilities().flying = true; j.sendAbilitiesUpdate();
                j.requestTeleport(CALCES.length * PASO + 29.6, j.getY() + 1.9, -1.2);
                return null;
            });
            c.player.setYaw(25);
            c.player.setPitch(45);
        });
        r.dejarPasar(30).captura("salon-extremidades-hombro");
        r.hacer("al pie, de frente y de cerca", 0, c -> {
            c.options.pauseOnLostFocus = false; if (c.currentScreen != null) c.setScreen(null);
            enServidor(c, () -> { jugador(c).requestTeleport(CALCES.length * PASO + 28.5, jugador(c).getY() - 1.6, -1.1); return null; });
            c.player.setYaw(0);
            c.player.setPitch(55);
        });
        r.dejarPasar(30).captura("salon-extremidades-pie-frente");
        r.hacer("al pie, de costado", 0, c -> {
            c.options.pauseOnLostFocus = false; if (c.currentScreen != null) c.setScreen(null);
            enServidor(c, () -> { jugador(c).requestTeleport(CALCES.length * PASO + 29.9, jugador(c).getY() - 0.3, 0.5); return null; });
            c.player.setYaw(90);
            c.player.setPitch(35);
        });
        r.dejarPasar(30).captura("salon-extremidades-pie-costado");
        // El pie con zoom (FOV 15) a 3 bloques: de frente, 3/4 y de costado, a la altura de los pies.
        for (int v = 0; v < 3; v++) {
            final int vv = v;
            r.hacer("zoom al pie " + v, 0, c -> {
                c.options.pauseOnLostFocus = false; if (c.currentScreen != null) c.setScreen(null);
                double[][] pos = {{0.0, -1.35}, {-0.95, -0.95}, {-1.35, 0.0}};
                float[] yaw = {0f, -45f, -90f};
                enServidor(c, () -> {
                    var j = jugador(c);
                    j.getAbilities().flying = false; j.sendAbilitiesUpdate();
                    j.requestTeleport(CALCES.length * PASO + 28.5 + pos[vv][0], j.getY(), 0.5 + pos[vv][1] + 0.0);
                    return null;
                });
                c.player.setYaw(yaw[vv]);
                c.player.setPitch(42);
                c.options.getFov().setValue(30);
            });
            r.dejarPasar(30).captura("salon-pie-zoom-" + v);
        }
        r.hacer("volver el FOV", 0, c -> c.options.getFov().setValue(70));
        // El pie de las medias SOLAS (sin calzado, sin pantalon) en la vista previa del jugador con zoom al pie (focoY): 3/4 y de costado.
        for (Calce calce : new Calce[]{Calce.PEGADO, Calce.AJUSTADO}) {
            for (int ang : new int[]{30, 90}) {
                r.hacer("vista previa de las medias " + calce + " a " + ang, 0, c -> {
                    c.options.pauseOnLostFocus = false;
                    ItemStack medias = prenda(new ItemStack(ModamodItems.SOCKS_SOLID), calce);
                    c.setScreen(new net.minecraft.client.gui.screen.Screen(net.minecraft.text.Text.empty()) {
                        @Override
                        public void render(net.minecraft.client.gui.DrawContext ctx, int mx, int my, float d) {
                            ctx.fill(0, 0, this.width, this.height, 0xFF5E8F45);
                            com.modamod.render.GarmentFeatureRenderer.previewOverride = java.util.List.of(medias);
                            try {
                                com.modamod.client.PreviewJugador.dibujar(ctx, c.player, 0, 0, this.width, this.height, 520, ang, this.height * 0.5f, -14f, 0.2f);
                            } finally {
                                com.modamod.render.GarmentFeatureRenderer.previewOverride = null;
                            }
                        }
                    });
                });
                r.dejarPasar(25).captura("salon-medias-pie-" + calce.name().toLowerCase() + "-" + ang);
            }
        }
        r.hacer("cerrar la vista previa", 0, c -> c.setScreen(null));
        r.hacer("de cerca a las bocas con manguito", 0, c -> {
            c.options.pauseOnLostFocus = false; if (c.currentScreen != null) c.setScreen(null);
            enServidor(c, () -> {
                var j = jugador(c);
                j.getAbilities().flying = false; j.sendAbilitiesUpdate();
                j.requestTeleport(CALCES.length * PASO + 24.0, j.getY(), 4.5);
                return null;
            });
            c.player.setYaw(-90);
            c.player.setPitch(20);
            c.options.getFov().setValue(40);
        });
        r.dejarPasar(30).captura("salon-tubos-manguitos");
        r.hacer("volver el FOV", 0, c -> c.options.getFov().setValue(70));
        r.hacer("el Maletin en el aire y en la mano", 0, c -> {
            enServidor(c, () -> {
                var j = jugador(c);
                j.requestTeleport(CALCES.length * PASO + 24.5, j.getY(), 0.5);
                var w = j.getServerWorld();
                var e = new net.minecraft.entity.ItemEntity(w, CALCES.length * PASO + 24.5, j.getY() + 1.6, 2.2, new ItemStack(com.modamod.item.ModamodItems.MALETIN));
                e.setNoGravity(true);
                e.setVelocity(0, 0, 0);
                e.setPickupDelay(32767);
                w.spawnEntity(e);
                j.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(com.modamod.item.ModamodItems.MALETIN));
                return null;
            });
            c.player.setYaw(0);
            c.player.setPitch(12);
        });
        r.dejarPasar(30).captura("salon-maletin");
        for (int col : new int[]{0, 2, 4}) {
            r.hacer("de frente al delantal, columna " + NOMBRES_CALCE[col], 0, c -> {
                enServidor(c, () -> { jugador(c).requestTeleport(col * PASO + 2.5, jugador(c).getY(), 2.0); return null; });
                c.player.setYaw(0);
                c.player.setPitch(8);
            });
            r.dejarPasar(40).captura("salon-delantal-" + NOMBRES_CALCE[col].toLowerCase());
        }
        r.hacer("DEPURAR orden del jugador", 0, c -> {
            enServidor(c, () -> {
                dev.emi.trinkets.api.TrinketsApi.getTrinketComponent(jugador(c)).ifPresent(comp -> {
                    int k = GuardarropasBlockEntity.POR_CATEGORIA;
                    ItemStack[] pr = {new ItemStack(ModItems.REMERA), new ItemStack(ModamodItems.PANTALON), new ItemStack(ModamodItems.POLLERA),
                            new ItemStack(ModamodItems.SOCKS_SOLID), new ItemStack(ModamodItems.CALIENTABRAZOS), new ItemStack(ModamodItems.DELANTAL)};
                    int[] cat = {GuardarropasBlockEntity.REMERA, GuardarropasBlockEntity.PANTALON, GuardarropasBlockEntity.PANTALON,
                            GuardarropasBlockEntity.MEDIAS, GuardarropasBlockEntity.CALIENTABRAZOS, -1};
                    int[] capa = {0, 0, 1, 0, 0, 0};
                    for (int i = 0; i < pr.length; i++) {
                        var inv = cat[i] < 0 ? GuardarropasBlockEntity.inventarioDelantal(comp) : GuardarropasBlockEntity.inventarioTrinkets(comp, cat[i]);
                        if (inv != null) inv.setStack(capa[i], pr[i]);
                    }
                    var chaq = GuardarropasBlockEntity.inventarioTrinkets(comp, GuardarropasBlockEntity.CHAQUETA);
                    if (chaq != null) chaq.setStack(0, new ItemStack(ModItems.REMERA));
                });
                return null;
            });
        });
        r.dejarPasar(40);
        r.hacer("DEPURAR imprimir orden", 0, c -> {
            StringBuilder sb = new StringBuilder("[orden-jugador]");
            for (ItemStack st : com.modamod.render.GarmentFeatureRenderer.equipadas(c.player)) sb.append(' ').append(st.getItem().getClass().getSimpleName());
            System.out.println(sb);
        });
        r.hacer("mirar el empalme con filtros", 0, c -> {
            if (c.currentScreen != null) c.setScreen(null);   // la eleccion de cuerpo de la primera vez
            enServidor(c, () -> { jugador(c).requestTeleport(6.5, jugador(c).getY(), -7.7); return null; });
            c.player.setYaw(0);
            c.player.setPitch(52);
        });
        r.dejarPasar(40).captura("salon-empalme-filtros");
        r.hacer("vista aerea", 0, c -> {
            enServidor(c, () -> {
                var sp = jugador(c);
                sp.requestTeleport(8.5, sp.getY() + 25, 14);
                sp.getAbilities().flying = true;
                sp.sendAbilitiesUpdate();
                return null;
            });
            c.player.setPitch(75);
            c.options.hudHidden = true;
        });
        r.dejarPasar(60).captura("salon-vista-aerea");
        r.hacer("volver a la entrada y guardar", 0, c -> enServidor(c, () -> {
            var sp = jugador(c);
            sp.requestTeleport(8.5, sp.getY() - 25, -5.5);
            sp.getAbilities().flying = false;
            sp.sendAbilitiesUpdate();
            return null;
        }));
        r.dejarPasar(40);
        return r;
    }
}
