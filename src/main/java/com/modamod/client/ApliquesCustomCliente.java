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
                Identifier geo = ModeloAplique.de(clave).geo();
                ApliqueRenderer.MODELOS_CUSTOM.put(geo, horneado);
                // La anterior no se cierra: puede quedar un teñido a medio armar leyéndola (una imagen chica por recarga).
                if (textura != null) ApliqueRenderer.TEXTURAS_CUSTOM.put(geo, textura);
                else ApliqueRenderer.TEXTURAS_CUSTOM.remove(geo);
                context.client().getTextureManager().registerTexture(
                        Identifier.of(Modamod.MOD_ID, "textures/item/molde_aplique_" + clave + ".png"), new NativeImageBackedTexture(icono));
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger("modamod").warn("Aplique custom {}: el modelo o el icono no se pudo leer", clave, e);
            }
        }));
    }

    /**
     * Sube {@code <nombre>.geo.json}, {@code <nombre>.textura.png} (si está) y {@code <nombre>.png} de {@code config/modamod/apliques/} de ESTE cliente al servidor,
     * en trozos (ver {@link ApliquesCustom.Subir}). El servidor decide si los acepta y contesta por el chat.
     */
    public static void subir(net.minecraft.util.math.BlockPos pos, String nombre) {
        java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("modamod").resolve("apliques");
        int max = ApliquesCustom.Subir.TROZO;
        try {
            java.nio.file.Files.createDirectories(dir);
            if (!nombre.matches("[a-z0-9_]{1,32}")) throw new java.io.IOException("nombre");
            java.nio.file.Path textura = dir.resolve(nombre + ".textura.png");
            // En el orden de ApliquesCustom: GEO, TEXTURA, ICONO.
            byte[][] archivos = { java.nio.file.Files.readAllBytes(dir.resolve(nombre + ".geo.json")),
                    java.nio.file.Files.isRegularFile(textura) ? java.nio.file.Files.readAllBytes(textura) : null,
                    java.nio.file.Files.readAllBytes(dir.resolve(nombre + ".png")) };
            for (int a = 0; a < 3; a++) {
                byte[] b = archivos[a];
                if (b == null) continue;
                int partes = Math.max(1, (b.length + max - 1) / max);
                for (int i = 0; i < partes; i++) {
                    ClientPlayNetworking.send(new ApliquesCustom.Subir(pos, nombre, a, i, i == partes - 1,
                            java.util.Arrays.copyOfRange(b, i * max, Math.min(b.length, (i + 1) * max))));
                }
            }
        } catch (java.io.IOException e) {
            var jugador = net.minecraft.client.MinecraftClient.getInstance().player;
            if (jugador != null) jugador.sendMessage(
                    net.minecraft.text.Text.translatable("modamod.apliques.subir.no_estan", nombre, dir.toString()), false);
        }
    }
}
