package com.modamod.client;

import com.modamod.Modamod;
import com.modamod.aplique.ApliquesCustom;
import com.modamod.aplique.ModeloAplique;
import com.modamod.render.ApliqueRenderer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;

import java.nio.charset.StandardCharsets;

/**
 * Recibe los apliques custom del servidor ({@link ApliquesCustom}): hornea el modelo como lo haría GeckoLib con un
 * recurso y registra el ícono justo en la textura que {@link MoldeApliqueItemRenderer} ya busca para ese modelo.
 */
public final class ApliquesCustomCliente {

    private ApliquesCustomCliente() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ApliquesCustom.Payload.ID, (payload, context) -> context.client().execute(() -> {
            String clave = payload.clave();
            // Lo que manda el servidor no pisa los modelos ni las texturas del mod.
            if (!clave.matches(ApliquesCustom.PREFIJO + "[a-z0-9_]{1,32}")) return;
            try {
                Model crudo = KeyFramesAdapter.GEO_GSON.fromJson(new String(payload.geo(), StandardCharsets.UTF_8), Model.class);
                var horneado = BakedModelFactory.getForNamespace(Modamod.MOD_ID).constructGeoModel(GeometryTree.fromModel(crudo));
                NativeImage icono = NativeImage.read(payload.icono());
                NativeImage textura = payload.textura().length == 0 ? null : NativeImage.read(payload.textura());
                // La primera animación del archivo, leída como lo hace GeckoLib con un recurso (solo la sección "animations").
                // Todo se lee antes de guardar nada: un archivo roto no deja el aplique a medias.
                var animaciones = payload.animacion().length == 0 ? null : KeyFramesAdapter.GEO_GSON.fromJson(
                        com.google.gson.JsonParser.parseString(new String(payload.animacion(), StandardCharsets.UTF_8))
                                .getAsJsonObject().getAsJsonObject("animations"),
                        software.bernie.geckolib.loading.object.BakedAnimations.class);
                Identifier geo = ModeloAplique.de(clave).geo();
                ApliqueRenderer.MODELOS_CUSTOM.put(geo, horneado);
                // La anterior no se cierra: puede quedar un teñido a medio armar leyéndola (una imagen chica por recarga).
                if (textura != null) ApliqueRenderer.TEXTURAS_CUSTOM.put(geo, textura);
                else ApliqueRenderer.TEXTURAS_CUSTOM.remove(geo);
                if (animaciones != null && !animaciones.animations().isEmpty()) {
                    ApliqueRenderer.ANIMACIONES_CUSTOM.put(geo, animaciones.animations().values().iterator().next());
                } else {
                    ApliqueRenderer.ANIMACIONES_CUSTOM.remove(geo);
                }
                context.client().getTextureManager().registerTexture(
                        Identifier.of(Modamod.MOD_ID, "textures/item/molde_aplique_" + clave + ".png"), new NativeImageBackedTexture(icono));
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger("modamod").warn("Aplique custom {}: el modelo, la animación o el icono no se pudo leer", clave, e);
            }
        }));
    }

    private static boolean eligiendo;

    /**
     * El botón "Subir aplique" (2026-11-12, "que despliegue un explorador de archivos para elegir los archivos"): abre el
     * explorador en otro hilo, como el escáner de estampas. Se elige el modelo ({@code .json}) y, si se quiere, el ícono y la
     * textura ({@code .png}; la textura es la que termina en {@code .textura.png}). Lo que no se elige se busca al lado del
     * modelo con su mismo nombre. El aplique se llama como lo escrito en el casillero o, si está vacío, como el archivo.
     * La animación ({@code .animation.json}) también se puede elegir o dejar al lado.
     */
    public static void elegirYSubir(net.minecraft.util.math.BlockPos pos, String nombreEscrito) {
        if (eligiendo) return;
        eligiendo = true;
        java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("modamod").resolve("apliques");
        Thread hilo = new Thread(() -> {
            try {
                java.nio.file.Files.createDirectories(dir);
                String elegido;
                try (org.lwjgl.system.MemoryStack pila = org.lwjgl.system.MemoryStack.stackPush()) {
                    org.lwjgl.PointerBuffer filtros = pila.mallocPointer(2);
                    filtros.put(pila.UTF8("*.json")).put(pila.UTF8("*.png")).flip();
                    elegido = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(
                            net.minecraft.text.Text.translatable("modamod.apliques.subir.titulo").getString(), dir + java.io.File.separator,
                            filtros, net.minecraft.text.Text.translatable("modamod.apliques.subir.archivos").getString(), true);
                }
                if (elegido == null) return;
                java.nio.file.Path geo = null, textura = null, animacion = null, icono = null;
                for (String ruta : elegido.split("\\|")) {
                    java.nio.file.Path a = java.nio.file.Path.of(ruta);
                    String n = a.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    if (n.endsWith(".animation.json")) animacion = a;
                    else if (n.endsWith(".json")) geo = a;
                    else if (n.endsWith(".textura.png")) textura = a;
                    else if (icono == null) icono = a;
                    else { avisar("modamod.apliques.subir.dos_png"); return; }
                }
                if (geo == null) { avisar("modamod.apliques.subir.sin_modelo"); return; }
                String base = geo.getFileName().toString().replaceFirst("(?i)(\\.geo)?\\.json$", "");
                if (icono == null) icono = geo.resolveSibling(base + ".png");
                if (textura == null && java.nio.file.Files.isRegularFile(geo.resolveSibling(base + ".textura.png"))) {
                    textura = geo.resolveSibling(base + ".textura.png");
                }
                if (animacion == null && java.nio.file.Files.isRegularFile(geo.resolveSibling(base + ".animation.json"))) {
                    animacion = geo.resolveSibling(base + ".animation.json");
                }
                String nombre = nombreEscrito.isEmpty() ? base.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_") : nombreEscrito;
                if (nombre.length() > 32) nombre = nombre.substring(0, 32);
                java.nio.file.Path g = geo, t = textura, an = animacion, ic = icono;
                String n = nombre;
                net.minecraft.client.MinecraftClient.getInstance().execute(() -> subir(pos, n, g, t, an, ic));
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger("modamod").warn("No se pudo elegir el aplique custom", e);
            } finally {
                eligiendo = false;
            }
        }, "modamod-aplique-custom");
        hilo.setDaemon(true);
        hilo.start();
    }

    private static void avisar(String clave, Object... args) {
        var cliente = net.minecraft.client.MinecraftClient.getInstance();
        cliente.execute(() -> {
            if (cliente.player != null) cliente.player.sendMessage(net.minecraft.text.Text.translatable(clave, args), false);
        });
    }

    /** Los archivos de {@code config/modamod/apliques/} de este cliente que llevan ese nombre (lo usa el guion de pruebas). */
    public static void subir(net.minecraft.util.math.BlockPos pos, String nombre) {
        java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("modamod").resolve("apliques");
        java.nio.file.Path textura = dir.resolve(nombre + ".textura.png"), animacion = dir.resolve(nombre + ".animation.json");
        subir(pos, nombre, dir.resolve(nombre + ".geo.json"), java.nio.file.Files.isRegularFile(textura) ? textura : null,
                java.nio.file.Files.isRegularFile(animacion) ? animacion : null, dir.resolve(nombre + ".png"));
    }

    /**
     * Sube el modelo, la textura (o null), la animación (o null) y el ícono al servidor, en trozos (ver {@link ApliquesCustom.Subir}). El servidor
     * decide si los acepta y contesta por el chat; lo que acá ya se sabe que va a rechazar (nombre, peso) se avisa sin mandar.
     */
    public static void subir(net.minecraft.util.math.BlockPos pos, String nombre, java.nio.file.Path geo,
                             @org.jetbrains.annotations.Nullable java.nio.file.Path textura,
                             @org.jetbrains.annotations.Nullable java.nio.file.Path animacion, java.nio.file.Path icono) {
        int max = ApliquesCustom.Subir.TROZO;
        if (!nombre.matches("[a-z0-9_]{1,32}")) { avisar("modamod.apliques.subir.nombre", nombre); return; }
        try {
            // En el orden de ApliquesCustom: GEO, TEXTURA, ANIMACION, ICONO.
            byte[][] archivos = { java.nio.file.Files.readAllBytes(geo),
                    textura == null ? null : java.nio.file.Files.readAllBytes(textura),
                    animacion == null ? null : java.nio.file.Files.readAllBytes(animacion), java.nio.file.Files.readAllBytes(icono) };
            for (int a = 0; a < archivos.length; a++) {
                if (archivos[a] != null && archivos[a].length > ApliquesCustom.maximo(a)) {
                    avisar("modamod.apliques.subir.muy_pesado", ApliquesCustom.maximo(a) / 1024);
                    return;
                }
            }
            for (int a = 0; a < archivos.length; a++) {
                byte[] b = archivos[a];
                if (b == null) continue;
                int partes = Math.max(1, (b.length + max - 1) / max);
                for (int i = 0; i < partes; i++) {
                    ClientPlayNetworking.send(new ApliquesCustom.Subir(pos, nombre, a, i, i == partes - 1,
                            java.util.Arrays.copyOfRange(b, i * max, Math.min(b.length, (i + 1) * max))));
                }
            }
        } catch (java.io.IOException e) {
            avisar("modamod.apliques.subir.no_estan", geo.getFileName().toString(), icono.getFileName().toString(), String.valueOf(geo.getParent()));
        }
    }
}
