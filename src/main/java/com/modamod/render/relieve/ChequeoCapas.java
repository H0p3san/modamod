package com.modamod.render.relieve;

import com.modamod.prenda.CategoriaDePrenda;
import com.modamod.prenda.Parte;
import com.modamod.prenda.Prendas;
import com.modamod.render.ClothingTextureCache;
import com.modamod.render.EfectoTrim;
import com.modamod.render.GarmentFeatureRenderer;
import com.modamod.render.Pieza;
import com.modamod.render.PiezasDePrenda;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Chequeo de capas (2026-11-11, "la geometria es el punto mas complicado"): dibuja al jugador con el código de siempre pero
 * en un grabador, sin pose, y avisa dónde algo de más adentro queda por fuera de algo de más afuera que ahí es opaco. Es lo
 * que se ve como "asoma" o "atraviesa". Mide lo que de verdad se dibuja; lo llama el guion de prueba ({@code capas}).
 *
 * <p>Cada superficie se etiqueta con su prenda ({@link EfectoTrim#ultimaPrenda}) y su textura, y se vuelca en una grilla
 * polar (ángulo alrededor del eje del cuerpo, altura): en cada celda queda lo más lejos del eje. Sirve igual para las cajas
 * de la skin que para las mallas sueltas (pollera, faldón, delantal, capa). Los brazos van aparte, alrededor de su eje.
 *
 * <p>El orden esperado, de adentro hacia afuera: el cuerpo; las piezas y las polleras por su lugar en la lista de ropa; el
 * faldón de un Top por fuera de las polleras; el delantal y la capa por fuera de todo.
 *
 * <p>Límites: el personaje quieto y sin pose; lo translúcido (se dibuja diferido) puede quedar con la etiqueta de otra
 * prenda; cuello, capucha, apliques, sombrero y calzado no se comparan.
 */
public final class ChequeoCapas {

    private ChequeoCapas() {}

    /** Separación de las muestras que se toman de cada cuadro (px). */
    private static final float PASO = 0.06f;
    /** Lo que una capa de adentro puede pasar a la de afuera sin contar (px). */
    private static final float TOLERANCIA = 0.03f;
    /** Celdas con problema para avisar (menos es ruido de la grilla). */
    private static final int MINIMO = 4;
    public static boolean DEPURAR = false;
    // La celda tiene que ser más grande que PASO en las dos direcciones, o quedan celdas sin muestra y la capa de afuera
    // "falta" ahí (pasó: el busto del cuerpo parecía asomar 2,6 px sobre la remera). 2° son 0,07 px a 2 px del eje.
    private static final int ANGULOS = 180, POR_PX = 8;
    private static final float Y0 = -3f, Y1 = 27f;
    private static final int ALTO = Math.round((Y1 - Y0) * POR_PX);

    private static final Parte[] PARTES = {Parte.TORSO, Parte.BRAZO_DER, Parte.BRAZO_IZQ, Parte.PIERNA_DER, Parte.PIERNA_IZQ};
    private static final String[] ZONA = {"torso", "brazo derecho", "brazo izquierdo", "pierna derecha", "pierna izquierda"};
    /** x del eje de cada zona (px); los brazos, según slim. */
    private static final float EJE_PIERNA = 1.9f;
    /** Desde acá (px bajo el hombro) es el pie. */
    private static final float PIE_DESDE = 21f;

    /** Graba los vértices que le llegan: 5 floats cada uno (x, y, z en px; u, v de 0 a 1). */
    private static final class Grabador implements VertexConsumer {
        float[] v = new float[4096];
        int n;

        @Override public VertexConsumer vertex(float x, float y, float z) {
            if (n + 5 > v.length) v = Arrays.copyOf(v, v.length * 2);
            v[n] = x * 16f; v[n + 1] = y * 16f; v[n + 2] = z * 16f;
            n += 5;
            return this;
        }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer texture(float u, float w) { v[n - 2] = u; v[n - 1] = w; return this; }
        @Override public VertexConsumer overlay(int u, int w) { return this; }
        @Override public VertexConsumer light(int u, int w) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
    }

    private record Clave(@Nullable ItemStack prenda, @Nullable Identifier textura) {}

    /** Una capa: su lugar en el orden y, por zona, lo más lejos del eje en cada celda (−∞ sin nada). */
    private static final class Capa {
        final String nombre;
        final float rango;
        final boolean deSkin;
        final float[][] lejos = new float[ZONA.length][];
        Capa(String nombre, float rango, boolean deSkin) { this.nombre = nombre; this.rango = rango; this.deSkin = deSkin; }

        float[] zona(int z) {
            if (lejos[z] == null) { lejos[z] = new float[ANGULOS * ALTO]; Arrays.fill(lejos[z], Float.NEGATIVE_INFINITY); }
            return lejos[z];
        }
    }

    private static Parte parteDe(float u, float w, boolean slim) {
        for (Parte p : PARTES) {
            int arriba = MapaRelieve.arribaCostados(p);
            if (w >= arriba && w <= arriba + MapaRelieve.FILAS_PX && MapaRelieve.caraEn(p, u, slim) >= 0) return p;
        }
        return null;
    }

    /** Una línea por cada par que se atraviesa (vacío = todo en orden), o null si no se pudo dibujar al jugador. */
    @Nullable
    public static List<String> revisar(AbstractClientPlayerEntity jugador) {
        Map<Clave, Grabador> grabado = new LinkedHashMap<>();
        VertexConsumerProvider vcp = capa -> grabado.computeIfAbsent(
                new Clave(EfectoTrim.ultimaPrenda, ClothingTextureCache.ultimaTextura), c -> new Grabador());
        EfectoTrim.ultimaPrenda = null;
        List<Identifier> cuerpo = GarmentFeatureRenderer.dibujarSinPose(jugador, vcp);
        if (cuerpo == null) return null;
        boolean slim = jugador.getSkinTextures().model() == net.minecraft.client.util.SkinTextures.Model.SLIM;
        List<ItemStack> prendas = GarmentFeatureRenderer.equipadas(jugador);

        int ultimaPollera = -1;
        for (int k = 0; k < prendas.size(); k++) if (Prendas.es(prendas.get(k), CategoriaDePrenda.POLLERA)) ultimaPollera = k;
        Map<String, Capa> capas = new LinkedHashMap<>();
        Map<Identifier, Optional<NativeImage>> imagenes = new HashMap<>();
        float brazoX = slim ? 5.5f : 6f;
        for (Map.Entry<Clave, Grabador> e : grabado.entrySet()) {
            ItemStack prenda = e.getKey().prenda();
            Identifier textura = e.getKey().textura();
            String nombre;
            float rango;
            boolean deSkin;   // usa el layout de la skin: se sabe de qué parte es cada cuadro
            if (textura != null && cuerpo.contains(textura)) {
                // El cuerpo elegido y la skin del jugador, por separado: se dibujan con reglas distintas.
                nombre = textura.equals(jugador.getSkinTextures().texture()) ? "la skin" : "el cuerpo"; rango = -1f; deSkin = true;
            } else if (prenda == null || prenda.isEmpty()) {
                continue;
            } else {
                String item = Registries.ITEM.getId(prenda.getItem()).getPath();
                int k = -1;
                for (int i = 0; i < prendas.size(); i++) if (prendas.get(i) == prenda) k = i;
                boolean pieza = false;
                for (Pieza p : PiezasDePrenda.de(prenda, null)) if (p.textura().equals(textura)) pieza = true;
                if (k >= 0 && pieza) { nombre = item + " (" + (k + 1) + "ª de adentro)"; rango = k; deSkin = true; }
                else if (k >= 0 && Prendas.es(prenda, CategoriaDePrenda.POLLERA)) { nombre = "pollera (" + (k + 1) + "ª de adentro)"; rango = k; deSkin = false; }
                else if (k >= 0 && Prendas.es(prenda, CategoriaDePrenda.REMERA)) { nombre = "faldón de " + item + " (" + (k + 1) + "ª de adentro)"; rango = Math.max(k, ultimaPollera) + 0.5f; deSkin = false; }
                else if (Prendas.es(prenda, CategoriaDePrenda.DELANTAL)) { nombre = "delantal"; rango = prendas.size() + 1; deSkin = false; }
                else if (Prendas.es(prenda, CategoriaDePrenda.CAPA)) { nombre = "capa"; rango = prendas.size() + 2; deSkin = false; }
                // El cinto va a la holgura de su calce, por diseño (puede quedar bajo la ropa o el delantal), y el collar no
                // envuelve nada: no se comparan.
                else continue;
            }
            if (DEPURAR) volcarArchivo(nombre, e.getValue());
            if (DEPURAR) org.slf4j.LoggerFactory.getLogger("modamod").info("[capasdbg] " + nombre + " <- " + (prenda == null ? "-" : Registries.ITEM.getId(prenda.getItem()).getPath()) + " " + textura + " cuadros=" + e.getValue().n / 20);
            Capa capa = capas.computeIfAbsent(nombre, x -> new Capa(nombre, rango, deSkin));
            NativeImage img = textura == null ? null : imagenes.computeIfAbsent(textura, t ->
                    MinecraftClient.getInstance().getTextureManager().getOrDefault(t, null) instanceof NativeImageBackedTexture nt
                            ? Optional.ofNullable(nt.getImage()) : Optional.empty()).orElse(null);
            volcar(capa, e.getValue(), deSkin, slim, brazoX, img);
        }

        List<Capa> orden = new ArrayList<>(capas.values());
        orden.sort((a, b) -> Float.compare(a.rango, b.rango));
        List<String> avisos = new ArrayList<>();
        for (int a = 0; a < orden.size(); a++) {
            for (int b = a + 1; b < orden.size(); b++) {
                Capa adentro = orden.get(a), afuera = orden.get(b);
                if (adentro.rango == afuera.rango) continue;
                for (int z = 0; z < ZONA.length; z++) {
                    // Una malla (pollera, faldón) que está más adentro en la lista que una pieza: la pieza de torso se abre
                    // por fuera de ella, pero las piernas van siempre por dentro de la pollera. Solo se compara el torso.
                    if (!adentro.deSkin && afuera.deSkin && z != 0) continue;
                    // Y la pollera va siempre por fuera del pantalón, también de su cintura, esté donde esté en la lista.
                    if (!adentro.deSkin && afuera.nombre.startsWith("pantalon")) continue;
                    float[] ra = adentro.lejos[z], rb = afuera.lejos[z];
                    if (ra == null || rb == null) continue;
                    int malas = 0, peorEn = 0;
                    float peor = 0f;
                    for (int i = 0; i < ra.length; i++) {
                        if (rb[i] == Float.NEGATIVE_INFINITY) continue;   // ahí la de afuera no está: nada que tapar
                        float pasa = ra[i] - rb[i];
                        if (pasa > TOLERANCIA) { malas++; if (pasa > peor) { peor = pasa; peorEn = i; } }
                    }
                    if (malas >= MINIMO) {
                        float grados = (peorEn % ANGULOS) * 360f / ANGULOS - 180f, y = Y0 + (peorEn / ANGULOS + 0.5f) / POR_PX;
                        avisos.add(String.format("%s: %s asoma sobre %s en %d celdas, hasta %.2f px (lo peor %s, a %.1f px bajo el hombro)",
                                ZONA[z], adentro.nombre, afuera.nombre, malas, peor, lado(grados), y));
                    }
                }
            }
        }
        return avisos;
    }

    /** Depuración: agrega los cuadros grabados (4 vértices: x y z u v) a modamod_debug/capas_dump.txt. */
    private static void volcarArchivo(String nombre, Grabador g) {
        StringBuilder sb = new StringBuilder();
        for (int q = 0; q + 20 <= g.n; q += 20) {
            sb.append(nombre);
            for (int k = 0; k < 20; k++) sb.append(' ').append(g.v[q + k]);
            sb.append((char) 10);
        }
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of("modamod_debug", "capas_dump.txt"), sb.toString(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException ignorado) { }
    }

    /** Hacia dónde mira ese ángulo (atan2(z, x)): el frente del modelo es −z. */
    private static String lado(float grados) {
        if (grados > -135f && grados <= -45f) return "adelante";
        if (grados > 45f && grados <= 135f) return "atrás";
        return grados > -45f && grados <= 45f ? "del lado +x" : "del lado −x";
    }

    /** Vuelca los cuadros de {@code g} en la capa: muestras cada {@link #PASO} px, cada una en su celda polar. */
    private static void volcar(Capa capa, Grabador g, boolean deSkin, boolean slim, float brazoX, @Nullable NativeImage img) {
        float[] v = g.v;
        float[] ejes = {0f, -brazoX, brazoX, -EJE_PIERNA, EJE_PIERNA};
        for (int q = 0; q + 20 <= g.n; q += 20) {
            // Tapas, bocas y anillos (horizontales) no son la superficie que tapa: afuera.
            float ax = v[q + 5] - v[q], ay = v[q + 6] - v[q + 1], az = v[q + 7] - v[q + 2];
            float bx = v[q + 15] - v[q], by = v[q + 16] - v[q + 1], bz = v[q + 17] - v[q + 2];
            if (ax * ax + ay * ay + az * az < 1e-8f) { ax = v[q + 10] - v[q]; ay = v[q + 11] - v[q + 1]; az = v[q + 12] - v[q + 2]; }
            float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            if (Math.abs(ny) > 0.7f * (float) Math.sqrt(nx * nx + ny * ny + nz * nz)) continue;
            int zonaSkin = -1;
            if (deSkin) {
                float u = 0, w = 0;
                for (int k = 0; k < 4; k++) { u += v[q + k * 5 + 3]; w += v[q + k * 5 + 4]; }
                Parte parte = parteDe(u * 16f, w * 16f, slim);   // promedio de 4, a px de skin (× 64 / 4)
                if (parte == null) continue;   // cabeza
                zonaSkin = switch (parte) { case BRAZO_DER -> 1; case BRAZO_IZQ -> 2; case PIERNA_DER -> 3; case PIERNA_IZQ -> 4; default -> 0; };
            }
            int ns = pasos(v, q, 0, 1), nt = pasos(v, q, 1, 2);
            for (int i = 0; i <= ns; i++) {
                float s = i / (float) ns;
                for (int j = 0; j <= nt; j++) {
                    float t = j / (float) nt;
                    // Bilineal entre los 4 vértices (0-1 arriba, 3-2 abajo).
                    float x = mezcla(v, q, 0, s, t), y = mezcla(v, q, 1, s, t), z = mezcla(v, q, 2, s, t);
                    if (img != null) {
                        int px = Math.max(0, Math.min(img.getWidth() - 1, (int) (mezcla(v, q, 3, s, t) * img.getWidth())));
                        int py = Math.max(0, Math.min(img.getHeight() - 1, (int) (mezcla(v, q, 4, s, t) * img.getHeight())));
                        if (((img.getColor(px, py) >>> 24) & 0xFF) < 128) continue;   // ahí la tela deja ver
                    }
                    int iy = (int) Math.floor((y - Y0) * POR_PX);
                    if (iy < 0 || iy >= ALTO) continue;
                    // Las mallas sueltas no son de una parte: arriba de la cadera rodean el torso; abajo, las dos piernas.
                    // El pie no se compara: la punta de la media y la del cuerpo usan otra zona de la textura y no se
                    // reconocen como de la pierna, así que una de las dos faltaría siempre.
                    if (zonaSkin >= 3 && y > PIE_DESDE) continue;
                    if (zonaSkin >= 0) anotar(capa, zonaSkin, ejes[zonaSkin], x, z, iy);
                    else if (y <= 12f) anotar(capa, 0, 0f, x, z, iy);
                    else { anotar(capa, 3, ejes[3], x, z, iy); anotar(capa, 4, ejes[4], x, z, iy); }
                }
            }
        }
    }

    private static void anotar(Capa capa, int zona, float cx, float x, float z, int iy) {
        int ia = Math.floorMod(Math.round((float) Math.toDegrees(Math.atan2(z, x - cx)) * ANGULOS / 360f) + ANGULOS / 2, ANGULOS);
        float[] lejos = capa.zona(zona);
        lejos[iy * ANGULOS + ia] = Math.max(lejos[iy * ANGULOS + ia], (float) Math.hypot(x - cx, z));
    }

    private static int pasos(float[] v, int q, int a, int b) {
        float dx = v[q + a * 5] - v[q + b * 5], dy = v[q + a * 5 + 1] - v[q + b * 5 + 1], dz = v[q + a * 5 + 2] - v[q + b * 5 + 2];
        return Math.max(1, Math.min(600, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) / PASO)));
    }

    private static float mezcla(float[] v, int q, int c, float s, float t) {
        float arriba = v[q + c] + (v[q + 5 + c] - v[q + c]) * s, abajo = v[q + 15 + c] + (v[q + 10 + c] - v[q + 15 + c]) * s;
        return arriba + (abajo - arriba) * t;
    }
}
