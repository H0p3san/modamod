package com.modamod.pruebas;

import com.modamod.guardarropas.GuardarropasBlockEntity;
import com.modamod.item.ModamodComponents;
import com.modamod.item.ModamodItems;
import com.modamod.maniqui.ManiquiBlock;
import com.modamod.maniqui.ManiquiBlockEntity;
import com.modamod.maniqui.ManiquiMod;
import com.modamod.sublimadora.ModItems;
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
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameRules;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static com.modamod.pruebas.Corrida.enServidor;
import static com.modamod.pruebas.Corrida.jugador;

/**
 * Modashow de los acabados de trim (2026-10-22, "haceme un modashow de prueba con todos los trims"): ./gradlew.bat runModashow
 * arma el mundo "ModaMod Modashow" en run/saves (el cliente de desarrollo) con tres pasarelas, cada una lejos de las otras para
 * que no se pisen en la caché de efectos: los 18 patrones de vanilla y uno de otro mod (metal) en un Top claro; los 10
 * materiales sobre el magma; y un conjunto completo (Top, pollera, capa, sombrero, borcegos, cinto) con un trim distinto en cada
 * prenda y el twirl prendido. Capturas de día y de noche (el brillo de verdad) en run/screenshots, y deja el mundo para recorrerlo.
 */
public final class EscenarioModashow {
    public static final String NOMBRE = "ModaMod Modashow";

    private static final String[] PATRONES = {"sentry", "dune", "coast", "wild", "ward", "eye", "vex", "tide", "snout", "rib",
            "spire", "wayfinder", "shaper", "silence", "raiser", "host", "flow", "bolt"};
    private static final String[] MATERIALES = {"quartz", "iron", "netherite", "redstone", "copper", "gold", "emerald", "diamond",
            "lapis", "amethyst"};
    /** Separación entre maniquíes de una pasarela y dónde arranca cada pasarela (en x). */
    private static final int PASO = 2, X_MATERIALES = 120, X_COMPLETO = 220;

    private EscenarioModashow() {}

    private static ItemStack conTrim(ItemStack s, String patron, String material, int color) {
        s.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(color, true));
        s.set(ModamodComponents.ACABADO_TRIM, patron.contains(":") ? Identifier.of(patron) : Identifier.ofVanilla(patron));
        if (material != null) s.set(ModamodComponents.ACABADO_MATERIAL, Identifier.ofVanilla(material));
        return s;
    }

    private static void cartel(ServerWorld w, BlockPos pos, String... lineas) {
        BlockState estado = Blocks.OAK_SIGN.getDefaultState().with(SignBlock.ROTATION, 8);
        w.setBlockState(pos, estado);
        if (w.getBlockEntity(pos) instanceof SignBlockEntity cartel) {
            SignText armado = new SignText();
            for (int i = 0; i < lineas.length && i < 4; i++) armado = armado.withMessage(i, Text.literal(lineas[i]));
            final SignText listo = armado;
            cartel.changeText(x -> listo, true);
        }
    }

    private static ManiquiBlockEntity maniqui(ServerWorld w, BlockPos pos) {
        w.setBlockState(pos, ManiquiMod.MANIQUI_BLOCK.getDefaultState().with(ManiquiBlock.FACING, Direction.NORTH));
        return w.getBlockEntity(pos) instanceof ManiquiBlockEntity be ? be : null;
    }

    private static void listo(ServerWorld w, BlockPos pos) { w.updateListeners(pos, w.getBlockState(pos), w.getBlockState(pos), 3); }

    private static void armar(MinecraftClient c) {
        enServidor(c, () -> {
            ServerWorld w = jugador(c).getServerWorld();
            int y = jugador(c).getBlockPos().getY();
            for (int x = -4; x <= X_COMPLETO + 8; x++) for (int z = -16; z <= 4; z++) {
                if (x > 44 && x < X_MATERIALES - 6 || x > X_MATERIALES + 26 && x < X_COMPLETO - 8) continue;   // sin piso entre pasarelas
                w.setBlockState(new BlockPos(x, y - 1, z), (x + z) % 2 == 0 ? Blocks.POLISHED_BLACKSTONE.getDefaultState() : Blocks.SMOOTH_QUARTZ.getDefaultState());
                for (int dy = 0; dy < 8; dy++) w.setBlockState(new BlockPos(x, y + dy, z), Blocks.AIR.getDefaultState());
            }
            // Pasarela 1: un Top claro por patrón (más el de otro mod, que cae en metal).
            int i = 0;
            for (String patron : PATRONES) {
                BlockPos pos = new BlockPos(i * PASO, y, 0);
                ManiquiBlockEntity be = maniqui(w, pos);
                if (be != null) be.setStack(GuardarropasBlockEntity.REMERA * GuardarropasBlockEntity.POR_CATEGORIA,
                        conTrim(new ItemStack(ModItems.REMERA), patron, null, 0xE8E4DC));
                listo(w, pos);
                cartel(w, pos.add(0, 0, -2), patron);
                i++;
            }
            BlockPos otro = new BlockPos(i * PASO, y, 0);
            ManiquiBlockEntity beOtro = maniqui(w, otro);
            if (beOtro != null) beOtro.setStack(GuardarropasBlockEntity.REMERA * GuardarropasBlockEntity.POR_CATEGORIA,
                    conTrim(new ItemStack(ModItems.REMERA), "otromod:algo", null, 0xE8E4DC));
            listo(w, otro);
            cartel(w, otro.add(0, 0, -2), "otro mod", "(metal)");
            // Pasarela 2: el magma con cada material.
            for (int k = 0; k < MATERIALES.length; k++) {
                BlockPos pos = new BlockPos(X_MATERIALES + k * PASO, y, 0);
                ManiquiBlockEntity be = maniqui(w, pos);
                if (be != null) be.setStack(GuardarropasBlockEntity.REMERA * GuardarropasBlockEntity.POR_CATEGORIA,
                        conTrim(new ItemStack(ModItems.REMERA), "snout", MATERIALES[k], 0xE8E4DC));
                listo(w, pos);
                cartel(w, pos.add(0, 0, -2), "snout", MATERIALES[k]);
            }
            // Pasarela 3: el conjunto completo, un trim por prenda, girando.
            BlockPos pc = new BlockPos(X_COMPLETO, y, 0);
            ManiquiBlockEntity be = maniqui(w, pc);
            if (be != null) {
                int cat = GuardarropasBlockEntity.POR_CATEGORIA;
                be.setStack(GuardarropasBlockEntity.REMERA * cat, conTrim(new ItemStack(ModItems.REMERA), "eye", null, 0xD8D0F0));
                be.setStack(GuardarropasBlockEntity.PANTALON * cat, conTrim(new ItemStack(ModamodItems.POLLERA), "snout", null, 0x302020));
                be.setStack(ManiquiBlockEntity.SLOT_CAPA, conTrim(new ItemStack(ModamodItems.CAPA), "spire", "amethyst", 0x282838));
                be.setStack(GuardarropasBlockEntity.SLOT_SOMBRERO, conTrim(new ItemStack(ModamodItems.SOMBRERO_BRUJA), "bolt", null, 0x202028));
                be.setStack(GuardarropasBlockEntity.SLOT_CALZADO, conTrim(new ItemStack(ModamodItems.BORCEGOS), "coast", "diamond", 0x404850));
                ItemStack cinto = new ItemStack(ModamodItems.BANDA);
                com.modamod.item.BandaItem.setZona(cinto, com.modamod.item.BandaZona.CINTURA);
                be.setStack(ManiquiBlockEntity.SLOT_CINTO, conTrim(cinto, "host", "gold", 0x806020));
                try {
                    var f = ManiquiBlockEntity.class.getDeclaredField("twirl");
                    f.setAccessible(true);
                    f.setBoolean(be, true);
                } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
            }
            listo(w, pc);
            cartel(w, pc.add(0, 0, -2), "Conjunto", "un trim por", "prenda, twirl");
            w.setSpawnPos(new BlockPos(18, y, -10), 0f);
            return null;
        });
    }

    /** Ir a {@code (x, z)} mirando a la pasarela (al sur), un poco desde arriba. */
    private static void mirar(Corrida r, String nombre, double x, double z, double alto, float pitch) {
        r.hacer("ir a " + nombre, 0, c -> {
            enServidor(c, () -> {
                var sp = jugador(c);
                sp.getAbilities().allowFlying = true;
                sp.getAbilities().flying = true;
                sp.sendAbilitiesUpdate();
                sp.requestTeleport(x, piso + alto, z);
                return null;
            });
            c.setScreen(null);   // si se pausó al perder el foco, el menú tapaba la captura y frenaba el mundo
            c.player.setYaw(0);
            c.player.setPitch(pitch);
            c.options.hudHidden = true;
        });
    }

    /** La Y del piso del modashow (la del jugador al armarlo). */
    private static double piso;

    private static void hora(Corrida r, long tiempo) {
        r.hacer(tiempo == 6000 ? "de dia" : "de noche", 0, c -> enServidor(c, () -> { jugador(c).getServerWorld().setTimeOfDay(tiempo); return null; }));
    }

    /** Una toma de día y la misma de noche, seguidas (los efectos de esa pasarela siguen en la caché). */
    private static void diaYNoche(Corrida r, String nombre, int espera) {
        hora(r, 6000);
        r.dejarPasar(espera).captura(nombre + "-dia");
        hora(r, 18000);
        r.dejarPasar(30).captura(nombre + "-noche");
    }

    /**
     * Por pasarela: llegar, esperar a que sus telas armen los 24 cuadros (generarlos lleva hasta un 20 % de un núcleo) y sacar
     * la toma de día y la de noche.
     */
    private static void tomas(Corrida r) {
        if (Boolean.getBoolean("modamod.modashow.rapido")) {   // solo de cerca (diagnóstico del brillo)
            mirar(r, "de cerca a snout", 8 * PASO + 0.5, -1.6, 1.4, 8);
            diaYNoche(r, "modashow-cerca-snout", 300);
            return;
        }
        mirar(r, "los patrones 1-10", 9.5, -9.5, 2.2, 12);
        diaYNoche(r, "modashow-patrones-1", 500);
        mirar(r, "los patrones 10-19", 27.5, -9.5, 2.2, 12);
        diaYNoche(r, "modashow-patrones-2", 200);
        // De cerca: el magma (snout), el rayo (bolt) y el ender (spire), para ver lo que brilla.
        for (String patron : new String[]{"snout", "bolt", "spire"}) {
            int k = java.util.Arrays.asList(PATRONES).indexOf(patron);
            mirar(r, "de cerca a " + patron, k * PASO + 0.5, -1.6, 1.4, 8);
            diaYNoche(r, "modashow-cerca-" + patron, 30);
        }
        mirar(r, "los materiales", X_MATERIALES + 9.5, -9.5, 2.2, 12);
        diaYNoche(r, "modashow-materiales", 400);
        mirar(r, "el conjunto", X_COMPLETO + 0.5, -4.2, 1.6, 10);
        diaYNoche(r, "modashow-conjunto", 300);
        r.dejarPasar(12).captura("modashow-conjunto-noche-giro");
    }

    public static Corrida armar() {
        Corrida r = new Corrida("modashow");
        r.hacer("borrar el modashow anterior", 0, c -> {
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
        r.hacer("sin pausa al perder el foco", 0, c -> { c.options.pauseOnLostFocus = false; c.setScreen(null); });
        r.hacer("anotar el piso", 0, c -> piso = Math.floor(c.player.getY()));
        r.hacer("armar las pasarelas", 60, EscenarioModashow::armar);
        tomas(r);
        r.hacer("volver a la entrada", 0, c -> enServidor(c, () -> {
            var sp = jugador(c);
            sp.requestTeleport(18.5, piso, -10.5);
            sp.getServerWorld().setTimeOfDay(13000);
            return null;
        }));
        r.dejarPasar(40);
        return r;
    }
}
