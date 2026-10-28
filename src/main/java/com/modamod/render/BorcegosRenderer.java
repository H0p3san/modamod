package com.modamod.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.modamod.item.BorcegoCana;
import com.modamod.item.BorcegoSuela;
import com.modamod.item.BorcegosItem;
import com.modamod.item.CalzadoCierre;
import com.modamod.item.CalzadoDetalle;
import com.modamod.item.CalzadoPunta;
import com.modamod.item.SombreroPatron;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * El calzado (2026-10-08 los borcegos con cajas; 2026-10-14, "agreguemos el calzado": zapatos, tenis y chanclas del zip de
 * Claude Design, de malla). Cada pie se arma con cinco ejes: la capellada, la suela, la punta, el cierre y el detalle
 * ({@link BorcegosItem}); qué piezas lleva cada valor sale de {@code assets/modamod/calzado/ejes.json} y las piezas son las
 * de {@link CalzadoMalla}. Tres zonas de color por pie: 0 cuerpo (cuero, tela), 1 suela y 2 detalles (cordones, tiras). Se
 * dibuja en el marco de cada pierna (el pie es el fondo de la caja de la pierna: y 12 es el piso, el frente es −Z) y el
 * pantalón se adapta ({@link GarmentFeatureRenderer}: por dentro se corta donde empieza la caña, por fuera se infla sobre
 * ella). Los apliques y las correas van en el pie en que se pusieron ({@link BorcegosItem#marco}).
 */
public final class BorcegosRenderer {

    private BorcegosRenderer() {}

    /** Lo que sale la caña de la piel de la pierna (px): la pared llega a |x| ≈ 2,75. */
    public static final float T = 0.75f;

    /**
     * La textura de piel de quien lleva el calzado (2026-10-14, "para las sandalias y ojotas tenemos que hacer un pie con
     * skin del player"): la fija quien dibuja (el feature renderer, el Maniquí) antes de pedir el calzado; sin ella no se
     * dibuja el pie.
     */
    public static Identifier pielActual;

    /**
     * La tela de la media de cada pierna cuando llega hasta el pie y no es de red (2026-10-18, "podemos envolver tambien el
     * pie q se genera para el calzado abierto"): el pie generado se dibuja con ella en vez de con la piel. La fija
     * {@code GarmentFeatureRenderer#dibujarSombrero} antes de pedir el calzado y la limpia después.
     */
    public static Identifier mediaIzq, mediaDer;

    // ── ejes.json ─────────────────────────────────────────────────────────

    private record Cierre(List<String> piezas, Map<String, List<String>> porCapellada) {}

    private record Ejes(Map<String, List<String>> capellada, Map<String, List<String>> suela, Map<String, List<String>> punta,
                        Map<String, Cierre> cierre, Map<String, List<String>> detalle) {}

    private static Ejes EJES;

    private static Ejes ejes() {
        if (EJES != null) return EJES;
        Map<String, List<String>> vacio = Map.of();
        try (InputStream in = BorcegosRenderer.class.getResourceAsStream("/assets/modamod/calzado/ejes.json")) {
            JsonObject e = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("ejes");
            Map<String, Cierre> cierres = new HashMap<>();
            for (var c : e.getAsJsonObject("cierre").entrySet()) {
                if (c.getValue().isJsonArray()) cierres.put(c.getKey(), new Cierre(lista(c.getValue().getAsJsonArray()), null));
                else {
                    Map<String, List<String>> por = new HashMap<>();
                    for (var k : c.getValue().getAsJsonObject().getAsJsonObject("porCapellada").entrySet()) por.put(k.getKey(), lista(k.getValue().getAsJsonArray()));
                    cierres.put(c.getKey(), new Cierre(null, por));
                }
            }
            EJES = new Ejes(mapa(e.getAsJsonObject("capellada")), mapa(e.getAsJsonObject("suela")), mapa(e.getAsJsonObject("punta")),
                    cierres, mapa(e.getAsJsonObject("detalle")));
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger("modamod").warn("Calzado: no se pudo leer ejes.json", ex);
            EJES = new Ejes(vacio, vacio, vacio, Map.of(), vacio);
        }
        return EJES;
    }

    private static Map<String, List<String>> mapa(JsonObject o) {
        Map<String, List<String>> m = new HashMap<>();
        for (var e : o.entrySet()) m.put(e.getKey(), lista(e.getValue().getAsJsonArray()));
        return m;
    }

    private static List<String> lista(JsonArray a) {
        List<String> l = new ArrayList<>();
        for (JsonElement e : a) l.add(e.getAsString());
        return l;
    }

    // ── armado de un pie ──────────────────────────────────────────────────

    private record Montado(List<CalzadoMalla.Pieza> piezas, float[][] plantilla, List<SombreroMalla.Quads> cuboides) {}

    private static final Map<String, Montado> MONTADOS = new HashMap<>();

    private static Montado montar(ItemStack calzado, boolean izq) {
        BorcegoCana cana = BorcegosItem.cana(calzado, izq);
        BorcegoSuela suela = BorcegosItem.suela(calzado, izq);
        CalzadoPunta punta = BorcegosItem.punta(calzado, izq);
        CalzadoCierre cierre = BorcegosItem.cierre(calzado, izq);
        CalzadoDetalle detalle = BorcegosItem.detalle(calzado, izq);
        boolean cajas = BorcegosItem.estilo(calzado) == com.modamod.item.SombreroEstilo.CAJAS;
        String clave = cana.eje + "|" + suela.eje + "|" + punta.clave + "|" + cierre.clave + "|" + detalle.clave + "|" + cajas;
        Montado hecho = MONTADOS.get(clave);
        if (hecho != null) return hecho;
        Ejes e = ejes();
        List<String> ids = new ArrayList<>();
        if (cana == BorcegoCana.NINGUNA) ids.add("pie_skin");   // sandalias y ojotas: se ve el pie del jugador
        ids.addAll(e.capellada().getOrDefault(cana.eje, List.of()));
        List<String> idsSuela = e.suela().getOrDefault(suela.eje, List.of());
        ids.addAll(idsSuela);
        ids.addAll(e.punta().getOrDefault(punta.clave, List.of()));
        Cierre c = e.cierre().get(cierre.clave);
        if (c != null) {
            if (c.piezas() != null) ids.addAll(c.piezas());
            else ids.addAll(c.porCapellada().getOrDefault(cana.eje, List.of()));   // sin pieza para esa capellada: no se ofrece
        }
        ids.addAll(e.detalle().getOrDefault(detalle.clave, List.of()));
        List<CalzadoMalla.Pieza> piezas = new ArrayList<>();
        for (String id : ids) {
            CalzadoMalla.Pieza p = CalzadoMalla.de(id);
            if (p != null) piezas.add(p);
        }
        float[][] plantilla = null;
        for (String id : idsSuela) {
            CalzadoMalla.Pieza p = CalzadoMalla.de(id);
            if (p != null && p.plantilla != null) plantilla = p.plantilla;
        }
        List<SombreroMalla.Quads> cuboides = null;
        if (cajas) {
            cuboides = new ArrayList<>();
            for (CalzadoMalla.Pieza p : piezas) cuboides.add(CalzadoMalla.cuboides(p, plantilla));
        }
        Montado m = new Montado(piezas, plantilla, cuboides);
        MONTADOS.put(clave, m);
        return m;
    }

    // ── dibujo ────────────────────────────────────────────────────────────

    public static void dibujar(ItemStack borcegos, BipedEntityModel<?> biped, MatrixStack matrices,
                               VertexConsumerProvider vertexConsumers, int luz) {
        VertexConsumerProvider sinEfecto = vertexConsumers;
        vertexConsumers = EfectoTrim.envolver(borcegos, vertexConsumers);   // acabado de trim (2026-10-22, "vamos con todo"), no en la piel
        for (boolean izq : new boolean[] {false, true}) {
            ModelPart marco = izq ? biped.leftLeg : biped.rightLeg;
            if (!marco.visible) continue;
            // Cada pie con lo suyo (2026-10-11, "no se puede independizar derecho e izquierdo?").
            List<Integer> colores = BorcegosItem.colores(borcegos, izq);
            List<SombreroPatron> patrones = BorcegosItem.patrones(borcegos, izq);
            Montado m = montar(borcegos, izq);
            matrices.push();
            marco.rotate(matrices);
            Matrix4f pos = new Matrix4f(matrices.peek().getPositionMatrix());
            Matrix3f nor = new Matrix3f(matrices.peek().getNormalMatrix());
            matrices.pop();
            for (int i = 0; i < m.piezas().size(); i++) {
                CalzadoMalla.Pieza p = m.piezas().get(i);
                int z = Math.max(0, Math.min(2, p.zona));
                Identifier media = p.esPiel() ? (izq ? mediaIzq : mediaDer) : null;
                if (p.esPiel() && pielActual == null && media == null) continue;
                Identifier tela = p.esPiel() ? (media != null ? media : pielActual) : ZonaTextura.de(borcegos, z, izq);
                int conMedia = media == null ? 0 : izq ? 2 : 1;
                VertexConsumer vc = (p.esPiel() ? sinEfecto : vertexConsumers).getBuffer(ClothingTextureCache.capaDeRender(tela));
                if (m.cuboides() != null) CalzadoMalla.emitir(m.cuboides().get(i), null, izq, p.esPiel(), conMedia, pos, nor, vc, luz);
                else CalzadoMalla.emitir(p, p.esSuela() ? null : m.plantilla(), izq, conMedia, pos, nor, vc, luz);
            }
        }
        EfectoTrim.volcar();
        // Apliques y correas: cada uno en el pie en que se puso (su parte es la pierna derecha o la izquierda).
        ApliqueRenderer.dibujar(borcegos, 0f, biped, matrices, vertexConsumers, luz);
    }

    /** Las cajas gruesas del pie derecho en el marco de la pierna. */
    public static List<SombreroRenderer.Caja> cajas(ItemStack borcegos) {
        return cajas(borcegos, false);
    }

    /**
     * Las cajas gruesas de un pie en el marco de su pierna (la forma de cada pie puede ser distinta), con la plantilla de su
     * suela aplicada y la X espejada en el izquierdo: para apuntar con el mouse y apoyar apliques y correas.
     */
    public static List<SombreroRenderer.Caja> cajas(ItemStack borcegos, boolean izq) {
        Montado m = montar(borcegos, izq);
        List<SombreroRenderer.Caja> out = new ArrayList<>();
        for (CalzadoMalla.Pieza p : m.piezas()) {
            for (SombreroRenderer.Caja c : p.apoyo) {
                float dy = p.esSuela() || m.plantilla() == null ? 0f
                        : CalzadoMalla.alturaPlantilla(m.plantilla(), (c.min()[2] + c.max()[2]) / 2f) - 11f;
                float x0 = izq ? -c.max()[0] : c.min()[0], x1 = izq ? -c.min()[0] : c.max()[0];
                out.add(new SombreroRenderer.Caja(c.zona(), new float[] {x0, c.min()[1] + dy, c.min()[2]},
                        new float[] {x1, c.max()[1] + dy, c.max()[2]}));
            }
        }
        return out;
    }
}
