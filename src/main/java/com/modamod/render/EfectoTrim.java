package com.modamod.render;

import com.modamod.Modamod;
import com.modamod.item.ModamodComponents;
import com.modamod.sublimadora.EstampaTextures;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Acabados de tela con un trim (2026-10-06, "los trims puedan agregar a las prendas texturas animadas, brillo,
 * policromatismo, reflejos especiales, relacionados quizas a la estructura en q se genera" + "los trims tienen q ser
 * epicos"): el patrón del trim elige un efecto que se mezcla en la tela, y lo caliente de ese efecto va además en una
 * máscara emisiva (ver {@link #envolver}); el material cambia el color y el comportamiento ({@link Material}).
 *
 * <p>Cómo: de la textura de la tela se saca una máscara (qué texeles son tela, a 256×256) y de ella se arman
 * {@link #CUADROS} cuadros del efecto, que se generan de a uno por dibujo para no trabar el juego. El efecto se
 * anima pasando de un cuadro al siguiente con un fundido (el alfa de cada cuadro sube y baja), así que ocho cuadros se
 * ven continuos. Todos los efectos son periódicos en el tiempo y en la textura: el bucle no tiene corte.
 */
public final class EfectoTrim {

    private EfectoTrim() {}

    /**
     * Un efecto por patrón de trim (2026-10-06, "18"): cada uno de los 18 de vanilla tiene el suyo; los de otros mods
     * caen en {@link #METAL}. Agregar valores nuevos al final.
     */
    public enum Tipo {
        SENTRY, DUNE, COAST, WILD, WARD, EYE, VEX, TIDE, SNOUT, RIB, SPIRE, WAYFINDER, SHAPER, SILENCE, RAISER, HOST,
        FLOW, BOLT, METAL
    }

    /** El efecto de un patrón de trim (por el nombre del patrón; los desconocidos, de otros mods, son metálicos). */
    public static Tipo tipoDe(Identifier patron) {
        for (Tipo t : Tipo.values()) if (t.name().equalsIgnoreCase(patron.getPath())) return t;
        return Tipo.METAL;
    }

    /** El color base (RGB) de un material de trim de vanilla, o -1 si no se conoce (otro mod). */
    public static int colorDeMaterial(@Nullable Identifier material) {
        if (material == null) return -1;
        return switch (material.getPath()) {
            case "quartz" -> 0xE3D4C4;
            case "iron" -> 0xECECEC;
            case "netherite" -> 0x625859;
            case "redstone" -> 0x971607;
            case "copper" -> 0xB4684D;
            case "gold" -> 0xDEB12D;
            case "emerald" -> 0x11A036;
            case "diamond" -> 0x6EECD2;
            case "lapis" -> 0x416E97;
            case "amethyst" -> 0x9A5CC6;
            default -> -1;
        };
    }

    /**
     * Cuántos cuadros tiene el bucle, su duración y cada cuánto se genera un cuadro. 2026-10-06, "puede tener mas
     * definicion el efecto y mas fps": 24 cuadros en 2,4 s (10 por segundo) y el efecto se calcula a la resolución
     * de la propia tela (antes, a 256² y estirado).
     */
    private static final int CUADROS = 24, PERIODO_MS = 2400;

    /**
     * Lo que cada material le hace al efecto además del color (2026-10-22, "los trims hay que hacerlo épico" + "vamos con
     * todo"): la velocidad del bucle, cuánto brilla, un latido del brillo, el glint de los encantamientos y retoques que se
     * hornean en los cuadros (destellos de amatista, pátina del cobre, el reflejo del hierro y el cuarzo, la netherita
     * oscura con reflejo).
     */
    enum Material {
        NINGUNO(1f, 1f, 0, 0f, false), QUARTZ(1f, 0.8f, 0, 0f, false), IRON(1f, 0.9f, 0, 0f, false),
        NETHERITE(0.8f, 0.6f, 0, 0f, false), REDSTONE(1f, 1.2f, 1200, 0.75f, false), COPPER(1f, 1f, 0, 0f, false),
        GOLD(1.5f, 1.2f, 0, 0f, false), EMERALD(0.75f, 1.1f, 0, 0f, false), DIAMOND(1f, 1f, 0, 0f, true),
        LAPIS(1f, 1f, 3000, 0.4f, false), AMETHYST(1f, 1f, 0, 0f, false);

        final float velocidad, luz, profundidadPulso;
        final int periodoPulso;
        final boolean glint;

        Material(float velocidad, float luz, int periodoPulso, float profundidadPulso, boolean glint) {
            this.velocidad = velocidad;
            this.luz = luz;
            this.periodoPulso = periodoPulso;
            this.profundidadPulso = profundidadPulso;
            this.glint = glint;
        }

        static Material de(@Nullable Identifier id) {
            if (id == null) return NINGUNO;
            for (Material m : values()) if (m.name().equalsIgnoreCase(id.getPath())) return m;
            return NINGUNO;
        }

        /** El factor del brillo ahora (1 sin latido). */
        float pulso(long ahora) {
            if (periodoPulso == 0) return 1f;
            float s = (float) Math.sin(Math.PI * (ahora % periodoPulso) / periodoPulso);
            return 1f - profundidadPulso + profundidadPulso * s * s;
        }
    }

    /**
     * Lo que se dibuja con el acabado de {@code prenda}: el proveedor de siempre si no lleva ninguno.
     *
     * <p>2026-10-06, "se ve el tooltip pero no el efecto": el efecto se mezcla EN la textura de la tela (un cuadro por vez)
     * y la prenda se dibuja por el mismo camino que sin acabado. 2026-10-22 ("brillo de verdad"): esa tela ya no va a luz
     * máxima, respeta la luz del lugar, y lo que está caliente (grietas, rayos, estrellas, runas) va además en una máscara
     * emisiva aditiva ({@code RenderLayer.getEyes}) con los MISMOS vértices (grabados y repetidos en {@link #volcar}): de
     * noche brilla solo eso. El diamante suma el glint de los encantamientos.
     */
    public static VertexConsumerProvider envolver(@Nullable ItemStack prenda, VertexConsumerProvider real) {
        if (prenda == null || real instanceof Proveedor) return real;
        Identifier patron = prenda.get(ModamodComponents.ACABADO_TRIM);
        if (patron == null) return real;
        Identifier mat = prenda.get(ModamodComponents.ACABADO_MATERIAL);
        return new Proveedor(real, tipoDe(patron), colorDeMaterial(mat), Material.de(mat));
    }

    private static final class Proveedor implements VertexConsumerProvider {
        private final VertexConsumerProvider real;
        private final Tipo tipo;
        private final int color;
        private final Material material;

        Proveedor(VertexConsumerProvider real, Tipo tipo, int color, Material material) {
            this.real = real;
            this.tipo = tipo;
            this.color = color;
            this.material = material;
        }

        @Override
        public VertexConsumer getBuffer(RenderLayer capa) {
            volcar();   // la pieza anterior ya terminó
            Identifier base = ClothingTextureCache.ultimaTextura;
            ClothingTextureCache.ultimaTextura = null;   // vale para este pedido solamente
            if (base == null) return real.getBuffer(capa);
            Cuadros c = cuadros(tipo, color, material, base);
            if (c == null || c.hechos == 0) return real.getBuffer(capa);   // todavía no hay ninguno: se ve la tela sola
            long ahora = System.currentTimeMillis();
            // En double: la hora en ms como float no distingue un cuadro del siguiente y el efecto quedaba quieto (2026-10-22,
            // "el wayfinder... quiero que se mueva").
            long fase = (long) (ahora * (double) material.velocidad) % PERIODO_MS;
            int i = Math.min((int) (fase * CUADROS / PERIODO_MS), c.hechos - 1);
            VertexConsumer tela = real.getBuffer(ClothingTextureCache.esTranslucida(base)
                    ? RenderLayer.getEntityTranslucent(c.ids[i]) : RenderLayer.getArmorCutoutNoCull(c.ids[i]));
            // La máscara emisiva no se puede pedir a la vez que la tela (el Immediate cierra la tela: "Not building!", modashow
            // 2026-10-22): se graban los vértices y se repiten en la máscara cuando la pieza terminó (ver volcar).
            pendiente = new Grabacion(real, c.luces[i], material.pulso(ahora), material.glint);
            return new Grabador(tela, pendiente);
        }
    }

    /** Los vértices (ya transformados) de la última pieza con acabado, para repetirlos en la máscara emisiva y el glint. */
    private record Grabacion(VertexConsumerProvider real, Identifier luz, float k, boolean glint,
                             it.unimi.dsi.fastutil.floats.FloatArrayList datos) {
        Grabacion(VertexConsumerProvider real, Identifier luz, float k, boolean glint) {
            this(real, luz, k, glint, new it.unimi.dsi.fastutil.floats.FloatArrayList());
        }
    }

    /** Por vértice: x, y, z, u, v, nx, ny, nz. */
    private static final int POR_VERTICE = 8;

    @Nullable private static Grabacion pendiente;

    /**
     * Repite en la máscara emisiva (y en el glint) los vértices de la última pieza con acabado. Lo llama el siguiente pedido de
     * buffer con acabado y, al final, cada render que envuelve con {@link #envolver}: así la tela de la pieza ya está entera
     * cuando se pide la otra capa.
     */
    public static void volcar() {
        Grabacion g = pendiente;
        pendiente = null;
        if (g == null || g.datos.size() < POR_VERTICE * 4) return;
        float[] d = g.datos.elements();
        int n = g.datos.size() / POR_VERTICE / 4 * 4 * POR_VERTICE;   // vértices de quads enteros
        int c = Math.max(0, Math.min(255, Math.round(255 * g.k)));
        VertexConsumer luz = g.real.getBuffer(RenderLayer.getEyes(g.luz));
        // La tela (ArmorCutoutNoCull) se acerca a la cámara con VIEW_OFFSET_Z_LAYERING (×0,99975) y la máscara, en el mismo
        // lugar, perdía contra su profundidad: no se veía nunca en el mundo (2026-10-22, diagnóstico con la máscara amarilla).
        // Se acerca un poco más que la tela.
        final float cerca = 0.9995f;
        for (int i = 0; i < n; i += POR_VERTICE) {
            luz.vertex(d[i] * cerca, d[i + 1] * cerca, d[i + 2] * cerca, 0xFF000000 | c << 16 | c << 8 | c, d[i + 3], d[i + 4],
                    net.minecraft.client.render.OverlayTexture.DEFAULT_UV, 0xF000F0, d[i + 5], d[i + 6], d[i + 7]);
        }
        if (!g.glint) return;
        VertexConsumer brillo = g.real.getBuffer(RenderLayer.getArmorEntityGlint());
        for (int i = 0; i < n; i += POR_VERTICE) {
            brillo.vertex(d[i], d[i + 1], d[i + 2], 0xFFFFFFFF, d[i + 3], d[i + 4],
                    net.minecraft.client.render.OverlayTexture.DEFAULT_UV, 0xF000F0, d[i + 5], d[i + 6], d[i + 7]);
        }
    }

    /** Pasa todo a la tela y anota posición, UV y normal de cada vértice. */
    private static final class Grabador implements VertexConsumer {
        private final VertexConsumer vc;
        private final it.unimi.dsi.fastutil.floats.FloatArrayList d;

        Grabador(VertexConsumer vc, Grabacion g) { this.vc = vc; this.d = g.datos(); }

        @Override public VertexConsumer vertex(float x, float y, float z) {
            vc.vertex(x, y, z);
            d.add(x); d.add(y); d.add(z); d.add(0f); d.add(0f); d.add(0f); d.add(1f); d.add(0f);
            return this;
        }
        @Override public VertexConsumer color(int r, int g, int b, int a) { vc.color(r, g, b, a); return this; }
        @Override public VertexConsumer texture(float u, float v) {
            vc.texture(u, v);
            int i = d.size() - POR_VERTICE;
            if (i >= 0) { d.set(i + 3, u); d.set(i + 4, v); }
            return this;
        }
        @Override public VertexConsumer overlay(int u, int v) { vc.overlay(u, v); return this; }
        @Override public VertexConsumer light(int u, int v) { vc.light(u, v); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) {
            vc.normal(x, y, z);
            int i = d.size() - POR_VERTICE;
            if (i >= 0) { d.set(i + 5, x); d.set(i + 6, y); d.set(i + 7, z); }
            return this;
        }
    }

    // ── los cuadros de cada tela ─────────────────────────────────────────────

    private static final class Cuadros {
        NativeImage base;                        // la tela tal cual; se suelta cuando están todos los cuadros
        final Tipo tipo;
        final int[][] paleta;
        final Material material;
        final Identifier[] ids = new Identifier[CUADROS];
        /** La máscara emisiva de cada cuadro, a media resolución (lo que brilla en la oscuridad). */
        final Identifier[] luces = new Identifier[CUADROS];
        int hechos = 0;
        long usado;

        Cuadros(Tipo tipo, int[][] paleta, Material material, NativeImage base) {
            this.tipo = tipo; this.paleta = paleta; this.material = material; this.base = base;
        }
    }

    /** Con el modashow (2026-10-22) hay hasta 19 telas con efecto a la vez a la vista. */
    private static final int MAXIMO_EN_CACHE = 24;
    private static final Map<String, Cuadros> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private static final Map<String, Long> REINTENTO = new LinkedHashMap<>();
    private static int numero = 0;
    /**
     * Presupuesto para generar cuadros (2026-10-22, modashow: con pocos FPS, "uno cada 20 ms y uno por dibujo" dejaba las telas
     * sin efecto un buen rato): hasta 10 ms de cada ventana de 50 ms (un 20 % de un núcleo), sin importar cuántos FPS haya.
     */
    private static long ventana = 0, gastadoNs = 0;

    private static boolean hayPresupuesto(long ahora) {
        if (ahora - ventana >= 50) { ventana = ahora; gastadoNs = 0; }
        return gastadoNs < 10_000_000L;
    }

    /** Para el HUD de rendimiento: telas con efecto en caché y cuadros generados entre todas. */
    public static int[] estadoCache() {
        int cuadros = 0;
        for (Cuadros c : CACHE.values()) cuadros += c.hechos;
        return new int[]{CACHE.size(), cuadros};
    }

    /** Los cuadros del efecto sobre esa tela (genera uno más si toca), o null si la tela todavía no se puede leer. */
    @Nullable
    private static Cuadros cuadros(Tipo tipo, int color, Material material, Identifier base) {
        String clave = tipo.name() + "|" + Integer.toHexString(color) + "|" + material + "|" + base;
        long ahora = System.currentTimeMillis();
        Cuadros c = CACHE.get(clave);
        if (c == null) {
            Long luego = REINTENTO.get(clave);
            if (luego != null && ahora < luego) return null;
            NativeImage img = EstampaTextures.leerTextura(base);
            if (img == null) {
                REINTENTO.put(clave, ahora + 500);
                return null;
            }
            // Llena: solo se hace lugar sacando una que no se dibuja hace rato (2026-10-22, modashow: con más telas a la vista
            // que lugares, cada nueva sacaba a otra a medio armar y ninguna llegaba a tener efecto). Si no hay, esta se ve lisa.
            if (CACHE.size() >= MAXIMO_EN_CACHE) {
                var it = CACHE.entrySet().iterator();
                Cuadros viejo = it.next().getValue();
                if (ahora - viejo.usado < 2000) {
                    img.close();
                    REINTENTO.put(clave, ahora + 1000);
                    return null;
                }
                it.remove();
                liberar(viejo);
            }
            c = new Cuadros(tipo, paletaDe(tipo, color), material, img);
            CACHE.put(clave, c);
        }
        c.usado = ahora;
        while (c.hechos < CUADROS && hayPresupuesto(System.currentTimeMillis())) {
            long t0 = System.nanoTime();
            generarSiguiente(c);
            gastadoNs += System.nanoTime() - t0;
        }
        return c;
    }

    private static void liberar(Cuadros c) {
        var tm = MinecraftClient.getInstance().getTextureManager();
        for (int i = 0; i < c.hechos; i++) { tm.destroyTexture(c.ids[i]); tm.destroyTexture(c.luces[i]); }
        if (c.base != null) { c.base.close(); c.base = null; }
    }

    /** La tela con el efecto del cuadro {@code c.hechos} mezclado encima (solo donde hay tela, a la resolución de la tela). */
    private static void generarSiguiente(Cuadros c) {
        long t0 = Perf.ini();
        try {
            generarSiguiente0(c);
        } finally {
            Perf.fin(Perf.Seccion.TRIM, t0);
        }
    }

    /** Diagnóstico (-Dmodamod.trimDebug=true): la máscara emisiva amarilla entera, para ver si la capa se dibuja. */
    private static final boolean DEBUG_LUZ = Boolean.getBoolean("modamod.trimDebug");

    /** Verdín del cobre: el color de la pátina. */
    private static final int[] VERDIN = {80, 168, 140};

    private static void generarSiguiente0(Cuadros c) {
        int i = c.hechos;
        float t = i / (float) CUADROS;
        int w = c.base.getWidth(), h = c.base.getHeight();
        NativeImage img = new NativeImage(w, h, true);
        NativeImage luces = new NativeImage(Math.max(1, w / 2), Math.max(1, h / 2), true);
        float[] o = new float[3];
        for (int y = 0; y < h; y++) {
            float v = (y + 0.5f) / h;
            for (int x = 0; x < w; x++) {
                int px = c.base.getColor(x, y);
                int alfa = px >>> 24;
                int brilla = 0xFF000000;   // aditivo: negro = no brilla
                if (alfa > 0) {
                    float u = (x + 0.5f) / w;
                    int e = pixel(c.tipo, c.paleta, u, v, t, i, o);
                    float luz = retocar(c.material, u, v, t) + luzUltima;
                    int ea = e >>> 24;
                    int r = px & 0xFF, g = (px >> 8) & 0xFF, b = (px >> 16) & 0xFF;
                    if (c.material == Material.COPPER && ruido(u, v, 12, 21) > 0.62f) {   // manchas de pátina, quietas
                        r = (r + VERDIN[0]) / 2; g = (g + VERDIN[1]) / 2; b = (b + VERDIN[2]) / 2;
                    }
                    if (c.material == Material.NETHERITE) { r = r * 3 / 4; g = g * 3 / 4; b = b * 3 / 4; }
                    r = (r * (255 - ea) + (e & 0xFF) * ea) / 255;
                    g = (g * (255 - ea) + ((e >> 8) & 0xFF) * ea) / 255;
                    b = (b * (255 - ea) + ((e >> 16) & 0xFF) * ea) / 255;
                    px = (alfa << 24) | (b << 16) | (g << 8) | r;
                    // Lo que brilla: el color del efecto, aclarado, por cuánto brilla.
                    // Fuerte (2026-10-22, modashow: de noche la veta del magma apenas se notaba).
                    float k = lim(luz * c.material.luz * 1.8f);
                    if (k > 0.02f) {
                        int lr = Math.round(Math.min(255, (e & 0xFF) + 60) * k);
                        int lg = Math.round(Math.min(255, ((e >> 8) & 0xFF) + 60) * k);
                        int lb = Math.round(Math.min(255, ((e >> 16) & 0xFF) + 60) * k);
                        brilla = 0xFF000000 | (lb << 16) | (lg << 8) | lr;
                    }
                }
                if (DEBUG_LUZ && alfa > 0) brilla = 0xFF00FFFF;
                img.setColor(x, y, px);
                if ((x & 1) == 0 && (y & 1) == 0 && x / 2 < luces.getWidth() && y / 2 < luces.getHeight()) {
                    luces.setColor(x / 2, y / 2, brilla);
                }
            }
        }
        var tm = MinecraftClient.getInstance().getTextureManager();
        NativeImageBackedTexture tex = new NativeImageBackedTexture(img);   // se sube al crearla
        tex.setImage(null);   // ya está en la placa: la copia en memoria sobra (24 cuadros por tela pesan)
        c.ids[i] = tm.registerDynamicTexture(Modamod.MOD_ID + "_trim_" + (numero++), tex);
        NativeImageBackedTexture texLuz = new NativeImageBackedTexture(luces);
        texLuz.setImage(null);
        c.luces[i] = tm.registerDynamicTexture(Modamod.MOD_ID + "_trim_luz_" + (numero++), texLuz);
        c.hechos = i + 1;
        if (c.hechos == CUADROS && c.base != null) { c.base.close(); c.base = null; }   // ya no hace falta
    }

    /**
     * Lo que el material le suma al brillo de un punto: destellos sueltos de la amatista, el reflejo que barre el hierro, el
     * cuarzo y la netherita.
     */
    private static float retocar(Material m, float u, float v, float t) {
        return switch (m) {
            case AMETHYST -> {
                int n = 26, cx = (int) (u * n), cy = (int) (v * n);
                float e = azar(cx + 41, cy + 7);
                if (e < 0.9f) yield 0f;
                float fu = u * n - cx - 0.5f, fv = v * n - cy - 0.5f;
                float forma = Math.max(0f, 1f - (Math.abs(fu) + Math.abs(fv)) * 3f);   // destello en rombo
                yield forma * (float) Math.pow(Math.max(0f, sen(TAU * (t + e * 11))), 4);
            }
            case IRON, QUARTZ, NETHERITE -> {
                float banda = Math.max(0f, 1f - Math.abs(frac(u * 0.6f + v * 0.8f - t) - 0.5f) * 10f);
                yield banda * banda * (m == Material.NETHERITE ? 0.6f : 0.35f);
            }
            default -> 0f;
        };
    }

    // ── paletas ───────────────────────────────────────────────────────────────

    /** La rampa de color (oscuro, medio, claro) de cada efecto, en RGB. */
    private static int[][] paletaDeFabrica(Tipo tipo) {
        return switch (tipo) {
            case SENTRY -> rampa(50, 60, 80, 140, 160, 190, 235, 245, 255);
            case DUNE -> rampa(150, 105, 45, 225, 180, 95, 255, 240, 185);
            case COAST -> rampa(15, 85, 120, 50, 165, 185, 235, 255, 255);
            case WILD -> rampa(15, 65, 25, 55, 150, 45, 190, 255, 130);
            case WARD -> rampa(10, 60, 70, 30, 170, 185, 120, 255, 245);
            case EYE -> rampa(60, 20, 110, 130, 60, 200, 230, 170, 255);
            case VEX -> rampa(120, 170, 200, 180, 220, 245, 240, 252, 255);
            case TIDE -> rampa(5, 40, 90, 20, 110, 170, 120, 215, 235);
            case SNOUT -> rampa(50, 5, 0, 230, 80, 10, 255, 235, 130);
            case RIB -> rampa(45, 5, 5, 200, 40, 15, 255, 170, 60);
            case SPIRE -> rampa(40, 190, 220, 130, 70, 210, 255, 120, 230);   // cian → violeta → magenta: iridiscente
            case WAYFINDER -> rampa(90, 70, 30, 190, 155, 70, 255, 240, 170);
            case SHAPER -> rampa(80, 40, 25, 200, 110, 60, 255, 215, 170);
            case SILENCE -> rampa(3, 8, 16, 15, 45, 60, 120, 230, 240);
            case RAISER -> rampa(80, 25, 10, 230, 120, 30, 255, 220, 120);
            case HOST -> rampa(70, 40, 20, 215, 120, 50, 255, 225, 160);
            case FLOW -> rampa(110, 170, 200, 200, 235, 250, 255, 255, 255);
            case BOLT -> rampa(170, 90, 50, 110, 210, 190, 255, 255, 255);
            default -> rampa(120, 130, 150, 220, 230, 245, 255, 255, 255);
        };
    }

    private static int[][] rampa(int... v) {
        return new int[][]{{v[0], v[1], v[2]}, {v[3], v[4], v[5]}, {v[6], v[7], v[8]}};
    }

    /** Con material (2026-10-06, "el material por separado, opcional"): la rampa sale del color del material. */
    private static int[][] paletaDe(Tipo tipo, int material) {
        if (material < 0) return paletaDeFabrica(tipo);
        int r = (material >> 16) & 0xFF, g = (material >> 8) & 0xFF, b = material & 0xFF;
        int[][] p = {
                {Math.round(r * 0.35f), Math.round(g * 0.35f), Math.round(b * 0.35f)},
                {r, g, b},
                {Math.round(r + (255 - r) * 0.6f), Math.round(g + (255 - g) * 0.6f), Math.round(b + (255 - b) * 0.6f)}};
        // La oscuridad del Silencio sigue siendo oscura: solo se tiñe.
        if (tipo == Tipo.SILENCE) {
            p[0] = new int[]{3, 6, 12};
            p[1] = new int[]{Math.round(r * 0.18f), Math.round(g * 0.18f), Math.round(b * 0.18f)};
        }
        return p;
    }

    // ── los efectos (todos periódicos en u, v y t) ──────────────────────────

    private static final float TAU = (float) (Math.PI * 2);

    private static float sen(float x) { return (float) Math.sin(x); }

    private static float frac(float x) { return x - (float) Math.floor(x); }

    private static float lim(float x) { return Math.max(0f, Math.min(1f, x)); }

    private static float suave(float h) { h = lim(h); return h * h * (3 - 2 * h); }

    private static float azar(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    /** Ruido de valores suave, periódico: {@code n} celdas por vuelta en u y en v. */
    private static float ruido(float u, float v, int n, int semilla) {
        float x = frac(u) * n, y = frac(v) * n;
        int x0 = (int) x, y0 = (int) y;
        float fx = suave(x - x0), fy = suave(y - y0);
        int x1 = (x0 + 1) % n, y1 = (y0 + 1) % n;
        float a = azar(x0 + semilla * 31, y0), b = azar(x1 + semilla * 31, y0),
                c = azar(x0 + semilla * 31, y1), d = azar(x1 + semilla * 31, y1);
        return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy;
    }

    /**
     * Voronoi periódico de {@code n} celdas por vuelta: deja en {@code o} la distancia al punto más cercano, al segundo
     * más cercano y el id de la celda del más cercano.
     */
    private static void voronoi(float u, float v, int n, int semilla, float[] o) {
        float x = frac(u) * n, y = frac(v) * n;
        int cx = (int) x, cy = (int) y;
        float d1 = 9f, d2 = 9f;
        int id = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int gx = cx + dx, gy = cy + dy;
                int wx = Math.floorMod(gx, n), wy = Math.floorMod(gy, n);
                float px = gx + 0.15f + 0.7f * azar(wx + semilla * 13, wy);
                float py = gy + 0.15f + 0.7f * azar(wx, wy + semilla * 7 + 101);
                float d = (float) Math.sqrt((px - x) * (px - x) + (py - y) * (py - y));
                if (d < d1) { d2 = d1; d1 = d; id = wx * 131 + wy; }
                else if (d < d2) d2 = d;
            }
        }
        o[0] = d1;
        o[1] = d2;
        o[2] = id;
    }

    private static int mezclar(int[] a, int[] b, float k, int canal) {
        return Math.round(a[canal] + (b[canal] - a[canal]) * k);
    }

    /** ABGR con alfa: la rampa según {@code h}, aclarada hacia el blanco según {@code brillo}. */
    private static int color(int[][] p, float h, float brillo, float alfa) {
        h = lim(h);
        int[] c = new int[3];
        for (int k = 0; k < 3; k++) {
            c[k] = h < 0.5f ? mezclar(p[0], p[1], h * 2f, k) : mezclar(p[1], p[2], (h - 0.5f) * 2f, k);
            c[k] = Math.round(c[k] + (255 - c[k]) * lim(brillo));
        }
        int ai = Math.max(0, Math.min(255, Math.round(255 * lim(alfa))));
        return (ai << 24) | (Math.min(255, c[2]) << 16) | (Math.min(255, c[1]) << 8) | Math.min(255, c[0]);
    }

    /** Cuánto brilla (0..1) el último punto de {@link #pixel}: lo caliente de cada efecto (va a la máscara emisiva). */
    private static float luzUltima;

    /** Un punto de un cuadro; {@code o} es un arreglo de trabajo ({@code h, alfa, brillo}). */
    private static int pixel(Tipo tipo, int[][] p, float u, float v, float t, int cuadro, float[] o) {
        float h = 0f, alfa = 0f, brillo = 0f, luz = -1f;   // luz < 0: la del brillo
        switch (tipo) {
            case SENTRY -> {   // un barrido de acero frío en diagonal sobre líneas finas de escaneo
                float banda = Math.max(0f, 1f - Math.abs(frac(u + v - t) - 0.5f) * 8f);
                float linea = 0.5f + 0.5f * sen(TAU * v * 48);
                h = banda * banda * 0.8f + 0.18f * linea;
                alfa = 0.22f + 0.6f * banda;
                brillo = banda * banda * 0.55f;
            }
            case DUNE -> {   // ondas de arena empujadas por el viento y granos que centellean
                float onda = 0.5f + 0.5f * sen(TAU * (4 * v + 2 * ruido(u, v, 4, 3) + t));
                int cx = (int) (u * 40), cy = (int) (v * 40);
                float e = azar(cx + 5, cy + 9);
                float grano = e > 0.9f ? Math.max(0f, sen(TAU * (t + e * 7))) : 0f;
                h = 0.25f + 0.6f * onda;
                alfa = 0.5f + 0.15f * onda + 0.3f * grano;
                brillo = grano * 0.8f;
            }
            case COAST -> {   // olas que suben y rompen en espuma
                float w = sen(TAU * (3 * v + 0.3f * sen(TAU * 2 * u) - t));
                float ola = suave(w * 1.3f);
                float espuma = (float) Math.pow(Math.max(0f, w), 6);
                h = 0.3f + 0.5f * ola;
                alfa = 0.42f + 0.4f * ola;
                brillo = espuma;
            }
            case WILD -> {   // venas de hoja y motas de luz de selva
                float x = 3 * u + 1.2f * sen(TAU * (2 * v + t));
                float vena = (float) Math.pow(1f - Math.abs(sen(TAU * x)), 6);
                int cx = (int) (u * 30), cy = (int) (v * 30);
                float e = azar(cx + 2, cy + 6);
                float mota = e > 0.92f ? Math.max(0f, sen(TAU * (t + e * 5))) : 0f;
                h = 0.3f + 0.7f * vena;
                alfa = 0.38f + 0.5f * vena;
                brillo = mota * 0.8f;
            }
            case WARD -> {   // pulsos lentos de sculk: un latido de manchas cian
                float n = 6f;
                int cx = (int) (u * n), cy = (int) (v * n);
                float fase = azar(cx, cy), fu = u * n - cx - 0.5f, fv = v * n - cy - 0.5f;
                float blob = Math.max(0f, 1f - (float) Math.sqrt(fu * fu + fv * fv) * 2.2f);
                float pulso = 0.5f + 0.5f * sen(TAU * (t + fase));
                float i = 0.10f + blob * blob * pulso * 1.3f;
                h = lim(i);
                alfa = i;
                brillo = blob * blob * pulso * 0.3f;
                luz = blob * blob * pulso;
            }
            case EYE -> {   // ojos del End que parpadean sobre una bruma violeta
                int n = 3;
                int cx = (int) (u * n), cy = (int) (v * n);
                float fu = u * n - cx - 0.5f, fv = v * n - cy - 0.5f;
                float ciclo = frac(t + azar(cx, cy));
                float apert = ciclo < 0.82f ? 1f : Math.abs(ciclo - 0.91f) / 0.09f;
                float ex = fu / 0.44f, ey = fv / (0.06f + 0.2f * apert);
                float ojo = ex * ex + ey * ey;
                if (ojo < 1f) {
                    float ir = (float) Math.sqrt((fu / 0.17f) * (fu / 0.17f) + (fv / (0.02f + 0.15f * apert)) * (fv / (0.02f + 0.15f * apert)));
                    if (ir < 0.45f) { h = 0f; alfa = 1f; luz = 0f; }
                    else if (ir < 1f) { h = 0.55f; alfa = 0.95f; brillo = 0.15f; luz = 0.8f; }
                    else { h = 0.92f; alfa = 0.9f; brillo = 0.4f; luz = 0.5f; }
                } else {
                    h = 0.35f + 0.1f * sen(TAU * (u + v + t));
                    alfa = 0.32f;
                }
            }
            case VEX -> {   // niebla espectral que sube
                float n1 = ruido(u, v - t, 6, 5), n2 = ruido(u, v + t, 12, 8);
                float n = lim(n1 * 0.7f + n2 * 0.3f);
                h = n;
                alfa = 0.9f * n * n;
                brillo = n * n * 0.5f;
            }
            case TIDE -> {   // cáusticas profundas, de ondas largas
                float s1 = sen(TAU * (2 * u + 0.25f * sen(TAU * (2 * v + t))));
                float s2 = sen(TAU * (2 * v + 0.25f * sen(TAU * (2 * u - t))));
                float vena = (float) Math.pow(1f - Math.abs(s1 * s2), 5);
                h = 0.15f + 0.85f * vena;
                alfa = 0.5f + 0.4f * vena;
                brillo = vena * vena * 0.3f;
            }
            case SNOUT -> {
                // Magma (2026-10-06, "mas que tanto movimiento el magma es mas un shifteo de brillos y endurecimientos
                // magmaticos"): placas de corteza quietas con grietas incandescentes; lo que se mueve es el calor: cada
                // placa se enfría y se endurece y vuelve a calentarse, y el brillo de las grietas se corre despacio.
                voronoi(u, v, 7, 3, o);
                float borde = o[1] - o[0];
                float fase = azar((int) o[2], 77);
                float grieta = lim(1f - borde / 0.2f);
                grieta *= grieta;
                float calor = 0.5f + 0.5f * sen(TAU * (t + fase));
                float pulso = 0.6f + 0.4f * sen(TAU * (t * 2 + fase * 3 + u));
                float corteza = ruido(u, v, 28, 5);
                float placa = 0.06f + 0.10f * corteza + 0.30f * calor * calor * (0.5f + 0.5f * corteza);
                h = grieta * (0.55f + 0.45f * pulso * (0.5f + 0.5f * calor)) + (1f - grieta) * placa;
                alfa = 0.94f;
                brillo = grieta * 0.35f * pulso * calor;
                luz = grieta * (0.45f + 0.55f * pulso * calor) + (1f - grieta) * calor * calor * 0.12f;
            }
            case RIB -> {   // brasas sueltas que se encienden y se apagan sobre una corteza quemada
                int n = 18;
                int cx = (int) (u * n), cy = (int) (v * n);
                float e = azar(cx + 11, cy + 4);
                float brasa = 0f;
                if (e > 0.68f) {
                    float fu = u * n - cx - 0.5f, fv = v * n - cy - 0.5f;
                    float vida = frac(t + e * 7);
                    float forma = Math.max(0f, 1f - (float) Math.sqrt(fu * fu + fv * fv) * 2.4f);
                    brasa = forma * (1f - vida) * (1f - vida);
                }
                h = 0.12f + 0.88f * brasa;
                alfa = 0.3f + 0.7f * brasa;
                brillo = brasa * 0.5f;
                luz = brasa;
            }
            case SPIRE -> {   // iridiscencia cian → violeta → magenta, con estrellas
                h = 0.5f + 0.5f * sen(TAU * (u + v + t));
                int n = 22;
                int cx = (int) (u * n), cy = (int) (v * n);
                float e = azar(cx, cy);
                float estrella = 0f;
                if (e > 0.84f) {
                    float fu = u * n - cx - 0.5f, fv = v * n - cy - 0.5f;
                    float br = 0.5f + 0.5f * sen(TAU * (t * 2 + azar(cy, cx)));
                    estrella = Math.max(0f, 1f - (float) Math.sqrt(fu * fu + fv * fv) * 5f) * br;
                }
                alfa = 0.58f + 0.4f * estrella;
                brillo = estrella;
                luz = estrella + 0.12f;
            }
            case WAYFINDER -> {
                // Brújulas repartidas por la tela (2026-10-22, "el wayfinder lo vi muy sutil": un solo haz girando alrededor del
                // centro de la textura casi no pasaba por la prenda): en cada celda una aguja que gira con estela, una rosa de
                // los vientos marcada y el centro encendido; cada brújula con su propio desfase.
                int n = 4;
                int cx = (int) (u * n), cy = (int) (v * n);
                float dx = u * n - cx - 0.5f, dy = v * n - cy - 0.5f;
                float r = (float) Math.sqrt(dx * dx + dy * dy);
                float ang = (float) Math.atan2(dy, dx) / TAU;
                float cola = frac(ang - t - azar(cx, cy));
                float haz = (float) Math.pow(1f - cola, 3) * lim(1f - r * 2.1f);
                float aguja = cola > 0.985f || cola < 0.015f ? lim(1f - r * 2.1f) : 0f;
                float rosa = (float) Math.pow(Math.abs(sen(TAU * 2 * ang)), 24) * lim(1f - Math.abs(r - 0.36f) / 0.06f);
                float anillo = lim(1f - Math.abs(r - 0.42f) / 0.025f);
                float centro = lim(1f - r / 0.07f);
                h = lim(0.15f + 0.85f * Math.max(haz, Math.max(aguja, centro)) + 0.4f * (rosa + anillo));
                alfa = lim(0.28f + 0.6f * haz + aguja + 0.5f * (rosa + anillo) + centro);
                brillo = lim(haz * haz * 0.6f + aguja * 0.8f + centro);
                luz = lim(haz * 0.85f + aguja + centro + 0.25f * anillo);
            }
            case SHAPER -> {   // líneas de cantera con un pulso de luz que las recorre
                float fu = frac(u * 4), fv = frac(v * 4);
                float d = Math.min(Math.min(fu, 1f - fu), Math.min(fv, 1f - fv));
                float linea = Math.max(0f, 1f - d / 0.05f);
                float pulso = Math.max(0f, 1f - frac(2 * u + 2 * v - t) * 3f);
                h = 0.1f + linea * (0.3f + 0.7f * pulso);
                alfa = 0.1f + 0.8f * linea;
                brillo = linea * pulso * 0.7f;
            }
            case SILENCE -> {   // una oscuridad que absorbe la luz, con puntitos pálidos
                float niebla = ruido(u, v, 5, 12);
                alfa = 0.34f + 0.16f * sen(TAU * t) + 0.25f * niebla;
                int n = 30;
                int cx = (int) (u * n), cy = (int) (v * n);
                float e = azar(cx + 3, cy + 17);
                if (e > 0.94f) {
                    float fu = u * n - cx - 0.5f, fv = v * n - cy - 0.5f;
                    float punto = Math.max(0f, 1f - (float) Math.sqrt(fu * fu + fv * fv) * 3f);
                    float pulso = 0.5f + 0.5f * sen(TAU * (t + e * 9));
                    h = punto * pulso;
                    alfa = Math.max(alfa, punto * (0.5f + 0.5f * pulso));
                    brillo = punto * pulso;
                }
            }
            case RAISER -> {   // calor que asciende en columnas
                float col = 0.5f + 0.5f * sen(TAU * (6 * u + 1.5f * ruido(u, v, 4, 9)));
                float pluma = Math.max(0f, 1f - frac(3 * v + t) * 1.5f);
                h = 0.25f * col + 0.75f * pluma * col;
                alfa = 0.22f + 0.6f * pluma * col;
                brillo = pluma * col * 0.5f;
            }
            case HOST -> {   // runas de vasija que se encienden de a una
                int n = 7;
                int cx = (int) (u * n), cy = (int) (v * n);
                float fu = u * n - cx - 0.5f, fv = v * n - cy - 0.5f;
                float e = azar(cx, cy);
                float pulso = (float) Math.pow(Math.max(0f, sen(TAU * (t + e))), 8);
                boolean runa = (Math.abs(fu) < 0.07f && Math.abs(fv) < 0.3f) || (Math.abs(fv) < 0.07f && Math.abs(fu) < 0.3f)
                        || (Math.abs(fu) < 0.07f && Math.abs(fv - 0.38f) < 0.07f);
                if (runa) { h = 0.5f + 0.5f * pulso; alfa = 0.35f + 0.65f * pulso; brillo = pulso * 0.8f; luz = 0.15f + 0.85f * pulso; }
                else { h = 0.1f; alfa = 0.06f; }
            }
            case FLOW -> {   // remolinos de viento que giran
                int n = 3;
                int cx = (int) (u * n), cy = (int) (v * n);
                float fu = u * n - cx - 0.5f, fv = v * n - cy - 0.5f;
                float r = (float) Math.sqrt(fu * fu + fv * fv);
                float ang = (float) Math.atan2(fv, fu) / TAU;
                float espiral = 0.5f + 0.5f * sen(TAU * (2 * ang + 4 * r - t));
                float borde = lim(1f - r * 2f);
                h = espiral;
                alfa = (0.18f + 0.62f * borde) * (0.4f + 0.6f * espiral);
                brillo = (float) Math.pow(espiral, 6) * borde;
            }
            case BOLT -> {   // arcos eléctricos que parpadean
                float x = 3 * v + 4.8f * (ruido(u, v, 10, 4) - 0.5f);
                float fino = (float) Math.pow(1f - Math.abs(sen(TAU * x)), 20);
                float parpadeo = cuadro % 3 == 0 ? 1f : 0.15f;
                h = fino;
                alfa = 0.05f + 0.9f * fino * parpadeo;
                brillo = fino * parpadeo * 0.8f;
                luz = fino * parpadeo;
            }
            default -> {   // METAL: un barrido de brillo en diagonal
                float banda = Math.max(0f, 1f - Math.abs(frac(u * 0.7f + v * 0.7f - t) - 0.5f) * 9f);
                float debil = Math.max(0f, 1f - Math.abs(frac(u * 0.7f + v * 0.7f - t + 0.5f) - 0.5f) * 14f) * 0.35f;
                h = banda * banda * 0.9f + debil;
                alfa = h;
                brillo = banda * banda * 0.5f;
            }
        }
        luzUltima = luz < 0f ? lim(brillo * 1.4f) * lim(alfa + 0.2f) : lim(luz);
        return color(p, h, brillo, alfa);
    }
}
