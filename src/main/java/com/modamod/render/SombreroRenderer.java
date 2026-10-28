package com.modamod.render;

import com.modamod.Modamod;
import com.modamod.item.SombreroAla;
import com.modamod.item.SombreroBrujaItem;
import com.modamod.item.SombreroPatron;
import com.modamod.item.SombreroPlanta;
import com.modamod.item.SombreroPunta;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * El sombrero (2026-10-04 de bruja con cajas; 2026-10-14, "sombreros estilo mallas": ahora de malla). Un sombrero es una
 * copa ({@link SombreroPunta}), un ala ({@link SombreroAla}) y la planta de las alas planas ({@link SombreroPlanta}); cada
 * uno es una pieza de {@link SombreroMalla} (los JSON de {@code assets/modamod/sombreros/}) con su zona de color: 0 ala,
 * 1 copa, 2 cinta y moños. El ala se baja o se sube hasta apoyar en la altura de la copa elegida; la punta blanda se
 * mece con la tela blanda ({@link FisicaApliques#actual}). Y negativo es arriba; el frente de la cabeza es −Z.
 */
public final class SombreroRenderer {

    private SombreroRenderer() {}

    private static final int TEX = 64;
    private static final Set<Direction> TODAS = EnumSet.allOf(Direction.class);
    /** Cuánto gira la punta por bloque de desplazamiento de la tela blanda (rad). */
    private static final float GANANCIA = 0.35f;
    private static final float TOPE = 0.6f;

    /** Una pieza del sombrero armado y cuánto se baja (px) para apoyar en la copa. */
    private record Armada(SombreroMalla.Pieza pieza, float dy) {}

    private static final Map<String, List<Armada>> ARMADOS = new HashMap<>();

    /** Las piezas de un sombrero: la copa y su cinta, el ala (con su planta) o, en la vincha y la cofia, la banda y el moño. */
    private static List<Armada> armar(ItemStack sombrero) {
        SombreroAla ala = SombreroBrujaItem.ala(sombrero);
        SombreroPunta copa = SombreroBrujaItem.punta(sombrero);
        SombreroPlanta planta = SombreroBrujaItem.planta(sombrero);
        boolean gruesa = SombreroBrujaItem.grosor(sombrero) == com.modamod.item.SombreroGrosor.GRUESA;
        boolean banda = SombreroBrujaItem.banda(sombrero);
        String clave = ala.clave + "|" + copa.clave + "|" + planta.clave + "|" + gruesa + "|" + banda;
        List<Armada> hecho = ARMADOS.get(clave);
        if (hecho != null) return hecho;
        List<Armada> out = new ArrayList<>();
        if (ala.esDiadema()) {
            agregar(out, SombreroMalla.de("fina", ala.clave), 0f);
            agregar(out, SombreroMalla.de("fina", "mono_" + ala.clave), 0f);
        } else {
            SombreroMalla.Pieza c = SombreroMalla.de("fina", copa.clave);
            agregar(out, c, 0f);
            if (banda) agregar(out, SombreroMalla.de("fina", "cinta_" + copa.clave), 0f);
            if (ala != SombreroAla.SIN_ALA) {
                // La carpeta del ala: fina o gruesa, redonda o cuadrada (si hay versión cuadrada de esa ala).
                String g = gruesa ? "gruesa" : "fina";
                SombreroMalla.Pieza a = planta == SombreroPlanta.CUADRADA ? SombreroMalla.de(gruesa ? "cuadrada-gruesa" : "cuadrada", ala.clave) : null;
                if (a == null) a = SombreroMalla.de(g, ala.clave);
                if (a == null) a = SombreroMalla.de("fina", ala.clave);
                if (a != null) agregar(out, a, c == null ? 0f : c.yBase - a.yBase);
            }
        }
        ARMADOS.put(clave, out);
        return out;
    }

    private static void agregar(List<Armada> out, SombreroMalla.Pieza p, float dy) {
        if (p != null) out.add(new Armada(p, dy));
    }

    public static void dibujar(ItemStack sombrero, BipedEntityModel<?> biped, MatrixStack matrices,
                               VertexConsumerProvider vertexConsumers, int luz) {
        if (!CabezaCaras.visible(biped)) return;
        vertexConsumers = EfectoTrim.envolver(sombrero, vertexConsumers);
        // Sobre una cabeza de CARAS, un 18 % más grande desde el cuello: la copa queda arriba de las que suben ~1,3 px.
        matrices.push();
        CabezaCaras.agrandar(matrices, biped, 1.18F);

        List<Integer> colores = SombreroBrujaItem.colores(sombrero);
        List<SombreroPatron> patrones = SombreroBrujaItem.patrones(sombrero);
        // La punta: lo que la inercia le suma a cada tramo (el de arriba también, el doble por ser hijo del de abajo).
        float pitch = 0f, roll = 0f;
        FisicaApliques.Desplazamiento d = FisicaApliques.actual;
        if (d != null) {
            roll = clamp(d.blando().x * GANANCIA);
            pitch = clamp(-d.blando().z * GANANCIA);
        }
        boolean cuboides = SombreroBrujaItem.estilo(sombrero) == com.modamod.item.SombreroEstilo.CAJAS;
        matrices.push();
        biped.head.rotate(matrices);
        SombreroMalla.MatrixStackLike m = new SombreroMalla.MatrixStackLike(
                new org.joml.Matrix4f(matrices.peek().getPositionMatrix()), new org.joml.Matrix3f(matrices.peek().getNormalMatrix()));
        matrices.pop();
        for (Armada a : armar(sombrero)) {
            int z = Math.max(0, Math.min(2, a.pieza().zona));
            Identifier tela = ZonaTextura.de(sombrero, z, false);
            VertexConsumer vc = vertexConsumers.getBuffer(ClothingTextureCache.capaDeRender(tela));
            SombreroMalla.dibujar(a.pieza(), a.dy(), m, vc, luz, pitch, roll, cuboides);
        }
        // Gorras con foto de la Sublimadora en el frente de la copa (2026-10-25), a 16x encima del tinte.
        if (SombreroBrujaItem.ala(sombrero).esVisera()) {
            Identifier foto = com.modamod.sublimadora.BannerHD.foto(com.modamod.sublimadora.BannerHD.Forma.GORRA,
                    sombrero.get(com.modamod.sublimadora.ModItems.ESTAMPA_FRENTE));
            if (foto != null) {
                VertexConsumer vc = vertexConsumers.getBuffer(ClothingTextureCache.capaDeRender(foto));
                for (Armada a : armar(sombrero)) {
                    if (a.pieza().zona == 1) SombreroMalla.dibujarFoto(a.pieza(), a.dy(), m, vc, luz, cuboides, new float[] {1f, 1f, 48f, 24f});
                }
            }
        }
        // Apliques apoyados en el sombrero (2026-10-05): su punto es de una caja del sombrero en el marco de la
        // cabeza, así que ya no flotan medio píxel como en un wearable cualquiera (dil 0).
        EfectoTrim.volcar();   // el brillo de lo último con acabado (2026-10-22)
        ApliqueRenderer.dibujar(sombrero, 0f, biped, matrices, vertexConsumers, luz);
        matrices.pop();
    }

    /** Una caja del sombrero en el marco de la cabeza (px): para saber qué zona se clickea en la Mesa de estilado. */
    public record Caja(int zona, float[] min, float[] max) {}

    /**
     * Las cajas gruesas de cada zona (0 ala, 1 copa con su punta, 2 cinta y moños) que trae cada pieza ({@code apoyo}),
     * con el ala bajada hasta la copa elegida; la punta va recta (la inercia no se tiene en cuenta: alcanza para apuntar).
     */
    public static List<Caja> cajas(ItemStack sombrero) {
        List<Caja> out = new ArrayList<>();
        for (Armada a : armar(sombrero)) {
            for (Caja c : a.pieza().apoyo) {
                out.add(new Caja(c.zona(), new float[] {c.min()[0], c.min()[1] + a.dy(), c.min()[2]},
                        new float[] {c.max()[0], c.max()[1] + a.dy(), c.max()[2]}));
            }
        }
        return out;
    }

    private static float clamp(float v) {
        return Math.max(-TOPE, Math.min(TOPE, v));
    }

    // ── cajas sueltas (las usan la banda, los borcegos y los tirantes del delantal) ───────────────────────

    static ModelPart.Cuboid caja(float x, float y, float z, float sx, float sy, float sz) {
        return new ModelPart.Cuboid(0, 0, x, y, z, sx, sy, sz, 0, 0, 0, false, TEX, TEX, TODAS);
    }

    // ── textura lisa por color ────────────────────────────────────────────
    private static final Map<Integer, Identifier> TEXTURAS = new HashMap<>();

    static Identifier textura(int rgb, SombreroPatron patron) {
        int key = rgb & 0xFFFFFF;
        int clave = key | (patron.ordinal() << 24);
        Identifier id = TEXTURAS.get(clave);
        if (id != null) return id;
        NativeImage img = new NativeImage(TEX, TEX, true);
        int abgr = abgr(key);
        int abgr2 = abgr(SombreroPatron.tonoDeContraste(key));
        for (int y = 0; y < TEX; y++) for (int x = 0; x < TEX; x++) img.setColor(x, y, patron.contraste(x, y) ? abgr2 : abgr);
        id = Identifier.of(Modamod.MOD_ID, "dynamic/sombrero_" + Integer.toHexString(key) + "_" + patron.clave);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(img));
        TEXTURAS.put(clave, id);
        return id;
    }

    private static int abgr(int rgb) {
        return 0xFF000000 | ((rgb & 0xFF) << 16) | (rgb & 0xFF00) | ((rgb >> 16) & 0xFF);
    }
}
