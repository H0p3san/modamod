package com.modamod.pruebas;

import com.modamod.guardarropas.GuardarropasBlockEntity;
import com.modamod.item.Calce;
import com.modamod.item.ModamodComponents;
import com.modamod.item.ModamodItems;
import com.modamod.item.PantalonItem;
import com.modamod.render.ClothingTextureCache;
import com.modamod.sublimadora.ModItems;
import dev.emi.trinkets.api.TrinketInventory;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.function.Function;

import static com.modamod.pruebas.Corrida.enServidor;
import static com.modamod.pruebas.Corrida.jugador;
import static com.modamod.pruebas.Informe.verificar;

/**
 * Prenda equipada (./gradlew.bat runWearableSmoke): viste al jugador por Trinkets desde el servidor, lo mira en tercera
 * persona de frente, y por cada prenda comprueba (1) que el cliente recibio el item, (2) que el dibujo cambio de verdad
 * (diferencia de pixeles de la zona central contra la captura anterior) y (3) saca una captura para revisar a ojo.
 * Ademas mira la textura que recibe la GPU: tinte de la base del pantalon (pixeles opacos y color pedido).
 */
public final class EscenarioPrenda {
    /** Fraccion minima de pixeles distintos para dar por cambiado el dibujo. */
    private static final double UMBRAL_CAMBIO = 0.0005;
    private static int[] anterior;

    private EscenarioPrenda() {}

    /** Pixeles ABGR de la banda central de la pantalla (donde esta el jugador en tercera persona de frente). */
    private static int[] muestra(MinecraftClient c) {
        try (NativeImage img = ScreenshotRecorder.takeScreenshot(c.getFramebuffer())) {
            int w = img.getWidth(), h = img.getHeight(), x0 = w * 3 / 10, x1 = w * 7 / 10, y0 = h / 10, y1 = h * 9 / 10;
            int[] out = new int[(x1 - x0) * (y1 - y0)];
            int k = 0;
            for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) out[k++] = img.getColor(x, y);
            return out;
        }
    }

    private static double diferencia(int[] a, int[] b) {
        int distintos = 0;
        for (int i = 0; i < a.length; i++) {
            int p = a[i], q = b[i];
            int d = Math.abs((p & 255) - (q & 255)) + Math.abs(((p >> 8) & 255) - ((q >> 8) & 255)) + Math.abs(((p >> 16) & 255) - ((q >> 16) & 255));
            if (d > 30) distintos++;
        }
        return distintos / (double) a.length;
    }

    private static void equipar(MinecraftClient c, Function<dev.emi.trinkets.api.TrinketComponent, TrinketInventory> inventario, ItemStack stack) {
        enServidor(c, () -> {
            TrinketsApi.getTrinketComponent(jugador(c)).ifPresent(comp -> inventario.apply(comp).setStack(0, stack));
            return null;
        });
    }

    private static Corrida prenda(Corrida r, String nombre, Function<dev.emi.trinkets.api.TrinketComponent, TrinketInventory> inventario, ItemStack stack) {
        r.hacer("equipar " + nombre, 0, c -> equipar(c, inventario, stack));
        r.paso("el cliente recibio " + nombre, c -> TrinketsApi.getTrinketComponent(c.player)
                .map(comp -> { TrinketInventory inv = inventario.apply(comp); return inv != null && inv.getStack(0).getItem() == stack.getItem(); }).orElse(false));
        // Al equipar la primera prenda el mod abre la eleccion de cuerpo: se descarta como el jugador con "Ahora no".
        r.dejarPasar(10);
        r.hacer("cerrar eleccion de cuerpo si se abrio", 0, c -> { if (c.currentScreen != null) c.setScreen(null); });
        r.dejarPasar(25);
        r.hacer("el dibujo cambio con " + nombre, 0, c -> {
            int[] ahora = muestra(c);
            double d = diferencia(anterior, ahora);
            r.informe().nota("cambio de pixeles con " + nombre + ": " + String.format("%.2f%%", d * 100));
            verificar(d > UMBRAL_CAMBIO, "la prenda " + nombre + " no cambio el dibujo (" + String.format("%.3f%%", d * 100) + ")");
            anterior = ahora;
        });
        return r.captura("prenda-" + nombre.replace(' ', '-'));
    }

    private static Function<dev.emi.trinkets.api.TrinketComponent, TrinketInventory> pantalonSlot() {
        return comp -> GuardarropasBlockEntity.inventarioTrinkets(comp, GuardarropasBlockEntity.PANTALON);
    }

    /**
     * Detalles de ropa (2026-10-19, "vamos con lo otro"): faldon del abrigo, volumen de las medias segun el calce (que cambie
     * de verdad), medias con sandalias, y la tapa de abajo de la cintura vista desde abajo. Todo con zoom (FOV 60: el cuerpo entero).
     */
    private static void detalles(Corrida r) {
        r.hacer("zoom a la prenda (FOV 60)", 0, c -> c.options.getFov().setValue(60));
        r.hacer("sacar el pantalon para ver las medias", 0, c -> equipar(c, pantalonSlot(), ItemStack.EMPTY));
        r.dejarPasar(30);
        r.hacer("muestra base de detalle", 0, c -> anterior = muestra(c));

        // Faldon: una remera con faldon de nivel 3 cuelga de la cintura (diferencia contra la remera lisa).
        ItemStack faldon = new ItemStack(ModItems.REMERA);
        com.modamod.item.TopCorte.setFaldon(faldon, 3);
        prenda(r, "remera con faldon", comp -> GuardarropasBlockEntity.inventarioTrinkets(comp, GuardarropasBlockEntity.REMERA), faldon);

        // Volumen de las medias: Pegado y Oversize tienen que verse DISTINTOS (el reclamo era "no observo ninguna variacion").
        java.util.function.Function<Calce, ItemStack> medias = calce -> {
            ItemStack m = new ItemStack(ModamodItems.SOCKS_SOLID);
            m.set(ModamodComponents.CALCE, calce);
            return m;
        };
        java.util.function.Function<dev.emi.trinkets.api.TrinketComponent, TrinketInventory> mediasSlot =
                comp -> GuardarropasBlockEntity.inventarioTrinkets(comp, GuardarropasBlockEntity.MEDIAS);
        prenda(r, "medias calce pegado", mediasSlot, medias.apply(Calce.PEGADO));
        prenda(r, "medias calce oversize", mediasSlot, medias.apply(Calce.OVERSIZE));

        // Medias con sandalias: calzado sin capellada ni cubo de punta; el pie lo dibuja la piel con la tela de la media.
        ItemStack sandalias = new ItemStack(ModamodItems.BORCEGOS);
        com.modamod.item.BorcegosItem.setCana(sandalias, com.modamod.item.BorcegoCana.NINGUNA, com.modamod.region.Lado.AMBAS);
        prenda(r, "sandalias con medias", GuardarropasBlockEntity::inventarioCalzado, sandalias);

        // Tapa de abajo de la cintura (el pantalon pinta su propia tapa): se mira desde abajo, con el jugador volando.
        r.hacer("subir al jugador y mirar hacia arriba", 0, c -> {
            enServidor(c, () -> {
                var sp = jugador(c);
                sp.requestTeleport(sp.getX(), sp.getY() + 40, sp.getZ());
                sp.getAbilities().flying = true;
                sp.sendAbilitiesUpdate();
                return null;
            });
            c.options.getFov().setValue(70);
        });
        r.dejarPasar(30);
        r.hacer("mirar al jugador desde abajo", 0, c -> c.player.setPitch(75));
        r.dejarPasar(30);
        r.hacer("muestra desde abajo sin pantalon", 0, c -> anterior = muestra(c));
        r.captura("detalle-tapa-cintura-sin-pantalon");
        ItemStack pantalon = new ItemStack(ModamodItems.PANTALON);
        prenda(r, "pantalon visto desde abajo", pantalonSlot(), pantalon);
        r.hacer("volver la camara a la normalidad", 0, c -> { c.player.setPitch(0); c.options.getFov().setValue(70); });
        r.dejarPasar(20);
    }

    public static Corrida armar() {
        Corrida r = new Corrida("prenda equipada");
        r.mundoPlano();
        r.hacer("limpiar corrida anterior", 0, Corrida::limpiar);
        r.hacer("GUI a escala 2x", 5, Corrida::guiDos);
        r.hacer("camara de frente, sin HUD", 0, c -> {
            c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            c.options.hudHidden = true;
        });
        r.dejarPasar(30);

        // Textura que recibe la GPU: el tinte de la base del pantalon es una imagen real, con pixeles opacos del color pedido.
        r.hacer("textura del pantalon en GPU", 0, c -> {
            Identifier id = ClothingTextureCache.tinted(PantalonItem.TEXTURA_BASE, 0xCC3333);
            AbstractTexture tex = c.getTextureManager().getTexture(id);
            verificar(tex instanceof NativeImageBackedTexture, "la textura tintada no esta en el TextureManager: " + tex);
            NativeImage img = ((NativeImageBackedTexture) tex).getImage();
            verificar(img != null, "textura sin imagen (ya subida y liberada)");
            long opacos = 0, rojizos = 0;
            for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getColor(x, y);
                if ((p >>> 24) == 0) continue;
                opacos++;
                if ((p & 255) > ((p >> 16) & 255) + 20) rojizos++;   // ABGR: R es el byte bajo
            }
            r.informe().nota("textura tintada " + img.getWidth() + "x" + img.getHeight() + ": " + opacos + " px opacos, " + rojizos + " rojizos");
            verificar(opacos > 0, "la textura tintada esta vacia");
            verificar(rojizos > opacos / 2, "el tinte rojo no llego a la textura (" + rojizos + "/" + opacos + ")");
        });

        r.captura("base-sin-ropa");
        r.hacer("muestra base", 0, c -> anterior = muestra(c));

        ItemStack pantalon = new ItemStack(ModamodItems.PANTALON);
        pantalon.set(ModamodComponents.CALCE, Calce.OVERSIZE);
        prenda(r, "pantalon oversize", comp -> GuardarropasBlockEntity.inventarioTrinkets(comp, GuardarropasBlockEntity.PANTALON), pantalon);

        ItemStack remera = new ItemStack(ModItems.REMERA);
        remera.set(ModamodComponents.CALCE, Calce.OVERSIZE);
        prenda(r, "remera oversize", comp -> GuardarropasBlockEntity.inventarioTrinkets(comp, GuardarropasBlockEntity.REMERA), remera);

        prenda(r, "medias", comp -> GuardarropasBlockEntity.inventarioTrinkets(comp, GuardarropasBlockEntity.MEDIAS), new ItemStack(ModamodItems.SOCKS_SOLID));
        prenda(r, "borcegos", GuardarropasBlockEntity::inventarioCalzado, new ItemStack(ModamodItems.BORCEGOS));

        detalles(r);   // al final: sube al jugador al aire
        return r;
    }
}
