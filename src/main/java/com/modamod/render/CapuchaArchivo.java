package com.modamod.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.modamod.Modamod;
import com.modamod.item.SombreroEstilo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * La capucha armada en Blockbench (2026-10-23, "resolveme z fighting en las dos capuchas que trabajamos y metelas al
 * mod con dos moldes nuevos para cambiar entre cuboide o malla"): reemplaza a la de CapuchaMalla. Las caras salen de
 * {@code assets/modamod/capuchas/<estilo>_<pose>.json} (los arma {@code tools/capuchas_al_mod.py} desde
 * {@code docs/modelo_capucha/capuchas.bbmodel}), en px del marco de la parte (Y hacia abajo), cada esquina {x, y, z, u, v}.
 * La tela es la lisa (o con patrones) de {@link CuelloYCapucha}: u &lt; 0,5 exterior, u &gt; 0,5 forro.
 */
final class CapuchaArchivo {
    private CapuchaArchivo() {}

    // shortcut: se lee una sola vez por partida; con F3+T no se recarga, hay que reiniciar para ver un cambio del archivo.
    private static final Map<String, float[][][]> CACHE = new HashMap<>();

    /** {@code ruta}: el archivo sin {@code .json}, desde {@code assets/modamod/} ("capuchas/..." o "cara/..."). */
    private static float[][][] caras(String ruta) {
        return CACHE.computeIfAbsent(ruta, n -> {
            Identifier id = Identifier.of(Modamod.MOD_ID, n + ".json");
            try (Reader r = MinecraftClient.getInstance().getResourceManager().getResourceOrThrow(id).getReader()) {
                JsonArray lista = JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("caras");
                float[][][] out = new float[lista.size()][4][5];
                for (int i = 0; i < lista.size(); i++) {
                    JsonArray cara = lista.get(i).getAsJsonArray();
                    for (int k = 0; k < 4; k++) {
                        JsonArray esq = cara.get(Math.min(k, cara.size() - 1)).getAsJsonArray();
                        int j = 0;
                        for (JsonElement e : esq) out[i][k][j++] = e.getAsFloat();
                    }
                }
                return out;
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger("modamod").error("No se pudo leer la capucha {}", id, e);
                return new float[0][][];
            }
        });
    }

    /** Puesta, en el marco de la cabeza. {@code d} = holgura del top (se agranda desde el centro de la cabeza). */
    static void puesta(SombreroEstilo estilo, Identifier tela, float d, ModelPart cabeza, MatrixStack matrices,
                       VertexConsumerProvider vcp, int luz) {
        dibujar("capuchas/" + estilo.clave + "_puesta", tela, d, cabeza, new Vector3f(0F, -4F, 0F), matrices, vcp, luz, 0F);
    }

    /**
     * La de la capa (2026-10-23, "versiones abiertas abajo para capa y las cerradas que ya tenemos para chaquetas"): la
     * misma, sin la tela bajo el mentón y con la base abierta sobre los hombros.
     */
    static void puestaCapa(SombreroEstilo estilo, Identifier tela, float d, ModelPart cabeza, MatrixStack matrices,
                           VertexConsumerProvider vcp, int luz) {
        dibujar("capuchas/" + estilo.clave + "_puesta_capa", tela, d, cabeza, new Vector3f(0F, -4F, 0F), matrices, vcp, luz, 0F);
    }

    /** Caída sobre la espalda, en el marco del torso; {@code atras} px más afuera (por encima del embozo de la capa). */
    static void caida(SombreroEstilo estilo, Identifier tela, float d, float atras, ModelPart torso, MatrixStack matrices,
                      VertexConsumerProvider vcp, int luz) {
        dibujar("capuchas/" + estilo.clave + "_caida", tela, d, torso, new Vector3f(0F, 3F, 1F + atras), matrices, vcp, luz, atras);
    }

    /** Máscara o barbijo (2026-10-24): {@code cara/<modelo>.json}, en el marco de la cabeza, sin agrandar. */
    static void cara(String modelo, Identifier tela, ModelPart cabeza, MatrixStack matrices, VertexConsumerProvider vcp, int luz) {
        dibujar("cara/" + modelo, tela, 0F, cabeza, new Vector3f(0F, -4F, 0F), matrices, vcp, luz, 0F);
    }

    /** {@code atras}: corrimiento hacia +z (la espalda) de todas las caras; {@code centro} ya viene corrido igual. */
    private static void dibujar(String nombre, Identifier tela, float d, ModelPart parte, Vector3f centro,
                                MatrixStack matrices, VertexConsumerProvider vcp, int luz, float atras) {
        float[][][] caras = caras(nombre);
        if (caras.length == 0) return;
        VertexConsumer vc = vcp.getBuffer(ClothingTextureCache.capaDeRender(tela));
        float k = 1F + 0.05F * Math.max(0F, d);
        matrices.push();
        parte.rotate(matrices);
        Matrix4f m = matrices.peek().getPositionMatrix();
        Matrix3f nm = matrices.peek().getNormalMatrix();
        Vector3f[] p = new Vector3f[4];
        for (float[][] cara : caras) {
            for (int i = 0; i < 4; i++) {
                p[i] = new Vector3f(cara[i][0], cara[i][1], cara[i][2] + atras).sub(centro).mul(k).add(centro);
            }
            // La normal hacia afuera del centro de la parte (la capa de render no descarta caras, solo es para la luz).
            Vector3f n = new Vector3f(p[1]).sub(p[0]).cross(new Vector3f(p[2]).sub(p[0]));
            if (n.lengthSquared() < 1e-10F) n.set(p[3]).sub(p[0]).cross(new Vector3f(p[2]).sub(p[0]));
            if (n.dot(new Vector3f(p[0]).sub(centro)) < 0F) n.negate();
            if (n.lengthSquared() < 1e-10F) n.set(0F, -1F, 0F);
            n.normalize();
            Vector3f nn = nm.transform(n, new Vector3f()).normalize();
            for (int i = 0; i < 4; i++) {
                // La malla para los apliques de la capucha (2026-10-24).
                if (MallaCapturada.grabando != null) {
                    MallaCapturada.grabando.agregar(m, nm, p[i].x / 16F, p[i].y / 16F, p[i].z / 16F, cara[i][3], cara[i][4], n.x, n.y, n.z);
                }
                Vector3f w = m.transformPosition(p[i].x / 16F, p[i].y / 16F, p[i].z / 16F, new Vector3f());
                vc.vertex(w.x, w.y, w.z, 0xFFFFFFFF, cara[i][3], cara[i][4], OverlayTexture.DEFAULT_UV, luz, nn.x, nn.y, nn.z);
            }
        }
        matrices.pop();
    }
}
