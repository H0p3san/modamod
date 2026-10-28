package com.modamod.pruebas;

import com.modamod.client.TinturasScreen;
import com.modamod.item.ModamodComponents;
import com.modamod.item.ModamodItems;
import com.modamod.tinturas.ElegirPatronPayload;
import com.modamod.tinturas.GuardarDisenoTinturasPayload;
import com.modamod.tinturas.TinturasBlockEntity;
import com.modamod.tinturas.TinturasBlockEntity.Categoria;
import com.modamod.tinturas.TinturasMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.function.Function;

import static com.modamod.pruebas.Corrida.enServidor;
import static com.modamod.pruebas.Corrida.jugador;
import static com.modamod.pruebas.Informe.verificar;

/**
 * Pasos compartidos para tenir una prenda en la Estacion de Tintes creativa con un molde de patron elegido como lo hace
 * el jugador: payload de la pantalla, botones del handler (categoria, mezcla, colores, contorno, Tenir).
 */
public final class FlujoTintes {
    /** La prenda que salio de la ultima tenida, lista para equipar. */
    public static ItemStack resultado = ItemStack.EMPTY;
    private static BlockPos pos;

    private FlujoTintes() {}

    private static <T> T be(MinecraftClient c, Function<TinturasBlockEntity, T> f) {
        return enServidor(c, () -> f.apply((TinturasBlockEntity) jugador(c).getServerWorld().getBlockEntity(pos)));
    }

    private static void boton(MinecraftClient c, int id) { c.interactionManager.clickButton(c.player.currentScreenHandler.syncId, id); }

    private static void abrir(MinecraftClient c) {
        enServidor(c, () -> { jugador(c).openHandledScreen((TinturasBlockEntity) jugador(c).getServerWorld().getBlockEntity(pos)); return null; });
    }

    /** Pone la Estacion de Tintes creativa dos bloques al costado del jugador. */
    public static Corrida colocar(Corrida r) {
        return r.hacer("colocar Estacion de Tintes creativa", 10, c -> {
            pos = jugador(c).getBlockPos().add(0, 0, 3);
            enServidor(c, () -> {
                var mundo = jugador(c).getServerWorld();
                var estado = TinturasMod.TINTURAS_CREATIVA.getDefaultState();
                mundo.setBlockState(pos, estado);
                // Lo que hace colocarla con el item: la creativa se carga con todos los patrones.
                com.modamod.util.MaquinaCreativa.alColocar(mundo, pos, estado, ItemStack.EMPTY);
                return null;
            });
        });
    }

    /** Reabre la pantalla si hace falta (la maquina cierra la GUI al tenir). */
    public static Corrida asegurarPantalla(Corrida r, String nombre) {
        return r.hacer("abrir Tintes (" + nombre + ")", 0, c -> { if (!(c.currentScreen instanceof TinturasScreen)) abrir(c); })
                .paso("pantalla Tintes abierta (" + nombre + ")", c -> c.currentScreen instanceof TinturasScreen);
    }

    /** Elige el molde {@code pattern_<animal>} en el cuadradito seleccionado de PANTALON y arma dos colores (negro y naranja). */
    public static Corrida configurar(Corrida r, String animal) {
        Item molde = Registries.ITEM.get(Identifier.of("modamod", "pattern_" + animal));
        Identifier patronId = Identifier.of("modamod", animal);
        r.paso("categoria PANTALON en Tintes (" + animal + ")", c -> {
            if (be(c, b -> b.categoria()) == Categoria.PANTALON) return true;
            boton(c, TinturasBlockEntity.BTN_CATEGORIA);
            return false;
        });
        // El primer cuadradito de PANTALON con region (no todos los lugares existen en cada categoria).
        r.hacer("elegir molde " + animal, 0, c -> {
            int casilla = -1;
            // Preferimos la prenda entera (TODO): con la cintura sola el print apenas se ve.
            for (int i = 0; i < TinturasBlockEntity.CASILLAS && casilla < 0; i++) if (TinturasBlockEntity.regionDe(Categoria.PANTALON, i) == com.modamod.region.RegionPintura.TODO) casilla = i;
            for (int i = 0; i < TinturasBlockEntity.CASILLAS && casilla < 0; i++) if (TinturasBlockEntity.regionDe(Categoria.PANTALON, i) != null) casilla = i;
            verificar(casilla >= 0, "PANTALON no tiene ningun cuadradito con region");
            ClientPlayNetworking.send(new ElegirPatronPayload(pos, casilla, Registries.ITEM.getId(molde), ElegirPatronPayload.ELEGIR));
        });
        r.paso("el cuadradito tiene el patron " + animal, c -> patronId.equals(be(c, b -> b.patronEnBorrador(b.categoria(), b.seleccionada()))));
        // Color 1 = negro (K al maximo), color 2 = naranja (M y Y altos); el contorno usa el color 2.
        int n = TinturasBlockEntity.NIVELES_MEZCLA;
        r.paso("dos colores", c -> {
            if (be(c, b -> b.casilla(b.seleccionada()).colores) >= 2) return true;
            boton(c, TinturasBlockEntity.BTN_COLORES);
            return false;
        });
        r.hacer("color 1 negro", 0, c -> {
            boton(c, TinturasBlockEntity.BTN_EDITAR_COLOR_BASE);
            for (int canal = 0; canal < 3; canal++) boton(c, TinturasBlockEntity.BTN_MEZCLA_BASE + canal * n);
            boton(c, TinturasBlockEntity.BTN_MEZCLA_BASE + 3 * n + (n - 1));
        });
        r.hacer("color 2 naranja", 0, c -> {
            boton(c, TinturasBlockEntity.BTN_EDITAR_COLOR_BASE + 1);
            boton(c, TinturasBlockEntity.BTN_MEZCLA_BASE + 1 * n + 14);
            boton(c, TinturasBlockEntity.BTN_MEZCLA_BASE + 2 * n + 18);
        });
        r.paso("contorno encendido", c -> {
            if (be(c, b -> b.casilla(b.seleccionada()).contorno)) return true;
            boton(c, TinturasBlockEntity.BTN_CONTORNO);
            return false;
        });
        return r.dejarPasar(15);
    }

    /** Pone un pantalon nuevo, aprieta Tenir y espera la prenda en la salida; la deja en {@link #resultado}. */
    public static Corrida tenir(Corrida r, String animal) {
        r.hacer("cargar pantalon (" + animal + ")", 5, c -> be(c, b -> { b.setStack(TinturasBlockEntity.SLOT_PRENDA_ENTRADA, new ItemStack(ModamodItems.PANTALON)); return null; }));
        r.hacer("apretar Tenir (" + animal + ")", 0, c -> boton(c, TinturasBlockEntity.BTN_TENIR));
        r.paso("la prenda sale tenida (" + animal + ")", c -> be(c, b -> b.estado() == TinturasBlockEntity.Estado.REPOSO && !b.getSalida().isEmpty()));
        r.hacer("revisar capas de " + animal, 0, c -> {
            resultado = be(c, b -> b.getSalida().copy());
            var capas = resultado.get(ModamodComponents.CAPAS_TINTE);
            verificar(capas != null && !capas.isEmpty(), "la prenda no trae capas de tinte");
            Identifier esperado = Identifier.of("modamod", animal);
            verificar(capas.stream().anyMatch(k -> esperado.equals(k.patronId())), "ninguna capa usa el patron " + animal + ": " + capas);
            be(c, b -> { b.setStack(TinturasBlockEntity.SLOT_SALIDA, ItemStack.EMPTY); return null; });
        });
        return r.paso("Tintes en reposo (" + animal + ")", c -> be(c, b -> b.estado() == TinturasBlockEntity.Estado.REPOSO && b.getSalida().isEmpty()));
    }

    /** Guarda el diseno como item, borra los cuadraditos, lo suelta en la bolsa de disenos, lo carga y comprueba que volvio el patron. */
    public static Corrida guardarYRecargar(Corrida r, String animal) {
        Identifier patronId = Identifier.of("modamod", animal);
        ItemStack[] diseno = {ItemStack.EMPTY};
        r.hacer("guardar diseno (" + animal + ")", 0, c -> ClientPlayNetworking.send(new GuardarDisenoTinturasPayload(pos, "animal-" + animal)));
        r.paso("el diseno llega al inventario como item", c -> {
            diseno[0] = enServidor(c, () -> {
                var inv = jugador(c).getInventory();
                for (int i = 0; i < inv.size(); i++) if (inv.getStack(i).isOf(TinturasMod.DISENO_TINTA)) return inv.getStack(i).copy();
                return ItemStack.EMPTY;
            });
            return !diseno[0].isEmpty() && diseno[0].get(ModamodComponents.DISENO_TINTE) != null;
        });
        r.hacer("borrar los cuadraditos", 0, c -> boton(c, TinturasBlockEntity.BTN_BORRAR));
        r.paso("sin patron tras borrar", c -> be(c, b -> b.patronEnBorrador(b.categoria(), b.seleccionada()) == null));
        r.hacer("soltar el diseno en la bolsa", 5, c -> verificar(be(c, b -> b.absorberDiseno(diseno[0])), "la bolsa no acepto el diseno"));
        r.hacer("cargar el diseno de la bolsa", 0, c -> ClientPlayNetworking.send(new ElegirPatronPayload(pos, 0,
                Registries.ITEM.getId(TinturasMod.DISENO_TINTA), ElegirPatronPayload.CARGAR_DISENO)));
        return r.paso("el patron volvio con el diseno", c -> patronId.equals(be(c, b -> b.patronEnBorrador(b.categoria(), b.seleccionada()))));
    }
}
