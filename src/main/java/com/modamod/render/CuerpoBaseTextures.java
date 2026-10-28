package com.modamod.render;

import com.modamod.body.CuerpoBase;
import com.modamod.body.PerfilCuerpo;
import com.modamod.garment.Parte;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Compone la textura del CUERPO BASE: el sustrato sobre el que se apoya toda
 * la ropa.
 *
 * Reemplaza la reconstruccion de piel. Antes, cada prenda rellenaba con tono
 * de piel todo lo que dejaba transparente, y por eso dos prendas en la misma
 * pierna se borraban entre si. Ahora la piel se dibuja UNA vez, abajo de
 * todo, y cada prenda deja transparente lo que no cubre.
 *
 * La textura sale en LAYOUT DE SKIN a {@link CuerpoGeometria#ESCALA_CUERPO}x
 * —o sea 1x, una skin comun de 64x64—, DISTINTA de la escala de la tela
 * ({@link CuerpoGeometria#ESCALA_TELA}). No hace falta que coincidan: el UV
 * de un cuboide es la MISMA fraccion sea cual sea la escala (ver el javadoc
 * de {@link CuerpoGeometria}), asi que dos superficies con resoluciones
 * distintas siguen cayendo en el mismo lugar del cuerpo. Y el cuerpo no
 * necesita mas que esto: no hay fotos ni patrones sobre piel, nada mas fino
 * que un pixel de skin. Ir a 8x ahi son 64 veces los texels para dibujar
 * exactamente lo mismo.
 *
 * <h2>Como se arma</h2>
 * <ol>
 *   <li>tono plano por cara, sacado de la rampa;</li>
 *   <li>el png del cuerpo elegido multiplicado encima, si existe;</li>
 *   <li>la ropa interior compuesta arriba, si existe.</li>
 * </ol>
 * Los dos png son OPCIONALES. Sin ninguno sale un cuerpo liso sombreado por
 * cara — que es exactamente lo que producia la reconstruccion vieja, o sea
 * que el sistema anda completo desde el dia uno y el arte se enchufa despues
 * soltando archivos, igual que un patron.
 *
 * <h2>Pensado para la ruta 3DSL</h2>
 * Todo el pintado esta en metodos que reciben la imagen y una {@link CajaSkin},
 * sin suponer la escala. Cuando llegue el camino de 3D Skin Layers —componer
 * la tela DENTRO de la capa externa de la skin para que 3DSL la extruya— las
 * mismas rutinas sirven apuntando a una skin de 64x64.
 */
public final class CuerpoBaseTextures {

    /**
     * Cuanta luz recibe cada cara.
     *
     * Siguen la convencion con la que estan pintadas las skins de Minecraft:
     * frente y espalda al tono base, los costados mas oscuros, la tapa de
     * arriba mas clara y la de abajo la mas oscura. Plano por cara y no un
     * degrade, que es como se ve una caja de verdad y como esta pintado el
     * resto del pixel art.
     */
    private static final float FRENTE = 0.55f;
    private static final float ATRAS = 0.50f;
    private static final float LADO = 0.32f;
    private static final float ARRIBA = 0.85f;
    private static final float ABAJO = 0.15f;

    private static final Map<String, Identifier> CACHE = new HashMap<>();

    private CuerpoBaseTextures() {}

    /**
     * La textura del cuerpo de este jugador, o null si no se pudo componer.
     *
     * Null pasa cuando el tono tiene que salir de la skin y la skin todavia
     * no bajo. Es transitorio y no se cachea: el frame siguiente reintenta.
     */
    @Nullable
    public static Identifier de(LivingEntity entidad, PerfilCuerpo perfil, boolean slim) {
        Integer tono = tonoDe(entidad, perfil);
        if (tono == null) return null;

        String clave = perfil.clave() + "|" + Integer.toHexString(tono) + "|" + slim;
        Identifier cacheada = CACHE.get(clave);
        if (cacheada != null) return cacheada;

        NativeImage img;
        try {
            img = componer(perfil, tono, slim);
        } catch (Exception e) {
            return null;
        }

        Identifier id = Identifier.of("modamod",
                "dynamic/cuerpo_" + Integer.toHexString(clave.hashCode()));
        MinecraftClient.getInstance().getTextureManager()
                .registerTexture(id, new NativeImageBackedTexture(img));
        CACHE.put(clave, id);
        if (com.modamod.render.ClothingTextureCache.DEBUG_DUMP) {
            try {
                java.nio.file.Path dir = java.nio.file.Paths.get("modamod_debug");
                java.nio.file.Files.createDirectories(dir);
                img.writeTo(dir.resolve("cuerpobase_" + Integer.toHexString(clave.hashCode()) + ".png"));
            } catch (java.io.IOException ignored) {}
        }
        return id;
    }

    /**
     * El tono a usar, en RGB.
     *
     * Si el perfil no fija uno, se saca de la skin del jugador UNA vez. Ese
     * es el default que no rompe la filosofia del mod: quien no elige nada
     * sigue viendose con su propio tono de piel.
     */
    @Nullable
    private static Integer tonoDe(LivingEntity entidad, PerfilCuerpo perfil) {
        if (!perfil.tonoDerivado()) return perfil.tono();
        return tonoDeLaSkin(entidad);
    }

    private static final Map<Identifier, java.util.List<Integer>> PALETAS = new HashMap<>();
    /** Distancia RGB mínima entre dos colores de la paleta: más cerca son "el mismo" con otro sombreado. */
    private static final int DISTANCIA_PALETA = 40;

    /**
     * Hasta 5 colores de la skin del jugador, los más usados primero (2026-09-29,
     * "poder seleccionar hasta 5 colores directamente de la skin"): cuenta los
     * píxeles de la capa base (cabeza incluida) y va tomando los más
     * frecuentes, salteando los que se parecen demasiado a uno ya tomado —
     * así un osito da el marrón del pelaje, el beige de la pancita, los ojos...
     * Lista vacía si la skin todavía no bajó.
     */
    public static java.util.List<Integer> paletaDeLaSkin(LivingEntity entidad) {
        if (!(entidad instanceof AbstractClientPlayerEntity jugador)) return java.util.List.of();
        SkinTextures skin = jugador.getSkinTextures();
        java.util.List<Integer> hecha = PALETAS.get(skin.texture());
        if (hecha != null) return hecha;
        NativeImage img = SkinTextureAccess.tryGetImage(skin);
        if (img == null) return java.util.List.of();
        Map<Integer, Integer> cuenta = new HashMap<>();
        for (int y = 0; y < Math.min(64, img.getHeight()); y++) {
            for (int x = 0; x < Math.min(64, img.getWidth()); x++) {
                boolean capaBase = (y < 16 && x < 32) || (y >= 16 && y < 32) || (y >= 48 && x >= 16 && x < 48);
                if (!capaBase) continue;
                int px = img.getColor(x, y);
                if (((px >>> 24) & 0xFF) < 128) continue;
                cuenta.merge(abgrARgb(px & 0xFFFFFF), 1, Integer::sum);
            }
        }
        java.util.List<Map.Entry<Integer, Integer>> orden = new java.util.ArrayList<>(cuenta.entrySet());
        orden.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        java.util.List<Integer> paleta = new java.util.ArrayList<>(5);
        for (Map.Entry<Integer, Integer> e : orden) {
            int c = e.getKey();
            boolean parecido = false;
            for (int p : paleta) {
                int dr = ((c >> 16) & 0xFF) - ((p >> 16) & 0xFF), dg = ((c >> 8) & 0xFF) - ((p >> 8) & 0xFF), db = (c & 0xFF) - (p & 0xFF);
                if (dr * dr + dg * dg + db * db < DISTANCIA_PALETA * DISTANCIA_PALETA) { parecido = true; break; }
            }
            if (parecido) continue;
            paleta.add(c);
            if (paleta.size() == 5) break;
        }
        java.util.List<Integer> fija = java.util.List.copyOf(paleta);
        PALETAS.put(skin.texture(), fija);
        return fija;
    }

    /** El tono sacado de la skin (RGB), o null si la skin todavía no bajó — también lo muestra la GUI de elegir cuerpo. */
    @Nullable
    public static Integer tonoDeLaSkin(LivingEntity entidad) {
        if (!(entidad instanceof AbstractClientPlayerEntity jugador)) {
            return abgrARgb(SkinToneSampler.fallbackTone());
        }
        SkinTextures skin = jugador.getSkinTextures();
        NativeImage img = SkinTextureAccess.tryGetImage(skin);
        if (img == null) return null;
        return abgrARgb(SkinToneSampler.sampleSkinTone(img, skin.model()));
    }

    private static NativeImage componer(PerfilCuerpo perfil, int tonoRgb, boolean slim) {
        NativeImage mascara = perfil.cuerpo().tieneTextura() ? mascaraPara(perfil.cuerpo(), slim) : null;
        // A la escala de la máscara (2026-09-29, "texturas con mayor
        // resolucion": las del zip vienen a 6x, 384x384): la geometría del
        // cuerpo usa UV normalizadas, así que una textura más grande calza
        // igual y solo gana detalle. Sin máscara (Tu skin), 1x como siempre.
        final int S = mascara != null ? Math.max(1, mascara.getWidth() / LayoutSkin.LADO) : CuerpoGeometria.ESCALA_CUERPO;
        final int lado = LayoutSkin.LADO * S;

        NativeImage img = new NativeImage(lado, lado, true);
        // Arranca vacia: solo se pinta el cuerpo, el resto queda transparente
        // y por ahi se sigue viendo la skin del jugador (la cara, sobre todo).
        for (int y = 0; y < lado; y++) {
            for (int x = 0; x < lado; x++) img.setColor(x, y, 0);
        }

        for (Parte parte : Parte.values()) {
            // La cabeza NO lleva cuerpo base: es la cara del jugador y
            // taparla con un tono plano le borraria los ojos. Solo se dibuja
            // si una prenda la pide (la banda de una polera), y esa prenda
            // trae su propia tela.
            if (parte == Parte.CABEZA) continue;
            CajaSkin caja = LayoutSkin.base(parte, slim).escalada(S);
            if (perfil.cuerpo().tieneTextura()) {
                // La mascara ya trae su propio sombreado: tono plano abajo,
                // si no se sombrea dos veces.
                for (CajaSkin.Rect cara : new CajaSkin.Rect[]{caja.arriba(), caja.abajo(), caja.derecha(),
                        caja.frente(), caja.izquierda(), caja.atras()}) {
                    pintarPlano(img, cara, tonoRgb);
                }
            } else {
                pintarCuerpo(img, caja, tonoRgb, parte);
            }
        }

        if (mascara != null) colorearPorZonas(img, mascara, perfil.cuerpo().animal, tonoRgb,
                perfil.tonoClaro(), perfil.tonoOscuro(), perfil.tonoRubor(),
                perfil.fuerzaRubor() / 100f);
        // Ropa interior en dos partes, teñida (2026-09-30): primero abajo, después arriba.
        com.modamod.body.RopaInterior interior = perfil.interior();
        NativeImage sombra = mascara != null && !perfil.cuerpo().animal ? mascara : null;
        if (interior.abajo().textura() != null) superponer(img, interior.abajo().textura(), interior.color(), sombra);
        if (interior.arriba().textura() != null) superponer(img, interior.arriba().textura(), interior.color(), sombra);
        return img;
    }

    /** Gris de referencia de una zona vacía (no hay píxeles de esa zona en la máscara). */
    private static final float GRIS_NEUTRO = 180f;

    /**
     * Zonas de color de una máscara de animal, por su gris (2026-09-29, "la
     * skin que uso yo es de un osito, entonces tiene un tono de pelaje y un
     * tono en la pancita"): las máscaras del zip separan solas pelaje
     * (80-160), zonas claras (pancita, hocico, manchas blancas: 165 para
     * arriba) y oscuras (rayas, manchas negras: menos de 75). Los humanos
     * son una sola zona, Base.
     */
    public static final int ZONA_BASE = 0, ZONA_CLARA = 1, ZONA_OSCURA = 2;
    /**
     * Rubor (2026-09-29, "y que pasa con una skin de colores frios?" → "3 por
     * default, mas selector propio"): no es una zona por gris sino una
     * cantidad por pixel — cuánto se aparta la máscara del gris hacia el
     * rosado (R por encima de G/B). La máscara solo dice DÓNDE; el color
     * sale del perfil o, en automático, del mismo color de la zona más
     * saturado y oscuro ({@link #ruborAutomatico}), así en una skin azul o
     * verde no aparecen manchas violetas.
     */
    public static final int ZONA_RUBOR = 3;
    /** Diferencia R - (G+B)/2 de la máscara que cuenta como rubor pleno. */
    // 10 y no 24 (2026-09-29, "no se si el blush esta aplicando, no veo cambios
    // con el slider"): el rosado de las máscaras es suave (casi todo entre 4 y
    // 12), así que con 24 el rubor pleno no llegaba ni a la mitad.
    private static final float RUBOR_PLENO = 10f;
    private static final int LIMITE_OSCURA = 75, LIMITE_CLARA = 165;

    public static int zonaDe(int gris, boolean animal) {
        if (!animal) return ZONA_BASE;
        return gris < LIMITE_OSCURA ? ZONA_OSCURA : gris >= LIMITE_CLARA ? ZONA_CLARA : ZONA_BASE;
    }

    private static void pintarPlano(NativeImage img, CajaSkin.Rect r, int tonoRgb) {
        int abgr = 0xFF000000 | ((tonoRgb & 0xFF) << 16) | (tonoRgb & 0xFF00) | ((tonoRgb >> 16) & 0xFF);
        for (int y = Math.max(0, r.y0()); y < Math.min(r.y1(), img.getHeight()); y++) {
            for (int x = Math.max(0, r.x0()); x < Math.min(r.x1(), img.getWidth()); x++) img.setColor(x, y, abgr);
        }
    }

    /**
     * Cada pixel del cuerpo toma el color de su zona, multiplicado por
     * gris/(gris típico de esa zona): el pixel típico sale del color elegido
     * y el sombreado de la máscara se conserva. Una zona Clara u Oscura sin
     * color propio ({@code 0}) sale del color Base, con el gris de la Base
     * como referencia (más clara o más oscura sola, como antes).
     */
    private static void colorearPorZonas(NativeImage img, NativeImage mascara, boolean animal,
                                         int base, int claro, int oscuro, int rubor, float fuerzaRubor) {
        float[] refs = referencias(mascara, animal);
        int[] colores = {base, claro != 0 ? claro : base, oscuro != 0 ? oscuro : base};
        float[] refUsada = {refs[ZONA_BASE], claro != 0 ? refs[ZONA_CLARA] : refs[ZONA_BASE],
                oscuro != 0 ? refs[ZONA_OSCURA] : refs[ZONA_BASE]};
        for (int y = 0; y < Math.min(img.getHeight(), mascara.getHeight()); y++) {
            for (int x = 0; x < Math.min(img.getWidth(), mascara.getWidth()); x++) {
                int m = mascara.getColor(x, y);
                if (((m >> 24) & 0xFF) == 0) continue;
                if (((img.getColor(x, y) >> 24) & 0xFF) == 0) continue;
                int gris = grisDe(m);
                int zona = zonaDe(gris, animal);
                int c = colores[zona];
                float k = gris / refUsada[zona];
                int r = sombrear((c >> 16) & 0xFF, k), g = sombrear((c >> 8) & 0xFF, k), b = sombrear(c & 0xFF, k);
                // Rubor: la máscara trae rosado donde va (rodillas, codos,
                // pecho, cara); se mezcla hacia el color de rubor con el
                // mismo sombreado.
                int mr = m & 0xFF, mg = (m >> 8) & 0xFF, mb = (m >> 16) & 0xFF;
                float cantidad = Math.min(1f, Math.max(0f, (mr - (mg + mb) / 2f) / RUBOR_PLENO)) * fuerzaRubor;
                if (cantidad > 0f) {
                    int rc = rubor != 0 ? rubor : ruborAutomatico(c);
                    r = Math.round(r + (sombrear((rc >> 16) & 0xFF, k) - r) * cantidad);
                    g = Math.round(g + (sombrear((rc >> 8) & 0xFF, k) - g) * cantidad);
                    b = Math.round(b + (sombrear(rc & 0xFF, k) - b) * cantidad);
                }
                img.setColor(x, y, 0xFF000000 | (b << 16) | (g << 8) | r);
            }
        }
    }

    /**
     * Un canal con el sombreado {@code k} de la máscara: para oscurecer se
     * multiplica; para aclarar se va hacia el blanco — multiplicar un marrón
     * x2 lo satura a naranja (la pancita automática del osito salía así).
     */
    private static int sombrear(int canal, float k) {
        if (k <= 1f) return Math.round(canal * k);
        float haciaBlanco = Math.min(1f, (k - 1f) * 0.6f);
        return Math.min(255, Math.round(canal + (255 - canal) * haciaBlanco));
    }

    /**
     * Rubor automático: el mismo color, más saturado y un poco más oscuro
     * (en HSV: saturación ×1.35 + 0.08, brillo ×0.85). En la piel da un
     * rosado tibio; en un azul, un azul más intenso.
     */
    public static int ruborAutomatico(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h = 0f;
        if (d > 0f) {
            if (max == r) h = ((g - b) / d) % 6f;
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h /= 6f;
            if (h < 0f) h += 1f;
        }
        float sat = max == 0f ? 0f : d / max;
        sat = Math.min(1f, sat * 1.35f + 0.08f);
        float val = max * 0.85f;
        // HSV → RGB
        float h6 = h * 6f;
        int i = (int) Math.floor(h6) % 6;
        float f = h6 - (float) Math.floor(h6);
        float p = val * (1 - sat), q = val * (1 - f * sat), t = val * (1 - (1 - f) * sat);
        float[] rgbF = switch (i) {
            case 0 -> new float[]{val, t, p};
            case 1 -> new float[]{q, val, p};
            case 2 -> new float[]{p, val, t};
            case 3 -> new float[]{p, q, val};
            case 4 -> new float[]{t, p, val};
            default -> new float[]{val, p, q};
        };
        int ri = Math.round(rgbF[0] * 255), gi = Math.round(rgbF[1] * 255), bi = Math.round(rgbF[2] * 255);
        return (ri << 16) | (gi << 8) | bi;
    }

    /** Luminancia de un pixel ABGR de la máscara (en una gris, su valor). */
    private static int grisDe(int abgr) {
        int r = abgr & 0xFF, g = (abgr >> 8) & 0xFF, b = (abgr >> 16) & 0xFF;
        return Math.min(255, (r * 299 + g * 587 + b * 114 + 500) / 1000);
    }

    private static final Map<NativeImage, float[]> REFERENCIAS = new java.util.IdentityHashMap<>();

    /** Gris típico (mediana) de cada zona de la máscara; {@link #GRIS_NEUTRO} si la zona no tiene píxeles. */
    private static float[] referencias(NativeImage mascara, boolean animal) {
        float[] hechas = REFERENCIAS.get(mascara);
        if (hechas != null) return hechas;
        int[][] histo = new int[3][256];
        int[] total = new int[3];
        int w = mascara.getWidth(), h = mascara.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int m = mascara.getColor(x, y);
                if (((m >> 24) & 0xFF) == 0) continue;
                // Solo la capa BASE del cuerpo (lo que se dibuja): sin cabeza
                // ni capas externas, que en el layout de 64x64 son las filas
                // 0-15, 32-47 y las esquinas de abajo (la máscara puede venir
                // a más resolución: se mira en coordenadas de 64).
                int sx = x * LayoutSkin.LADO / w, sy = y * LayoutSkin.LADO / h;
                boolean capaBase = (sy >= 16 && sy < 32) || (sy >= 48 && sx >= 16 && sx < 48);
                if (!capaBase) continue;
                int gris = grisDe(m);
                int zona = zonaDe(gris, animal);
                histo[zona][gris]++;
                total[zona]++;
            }
        }
        float[] refs = new float[3];
        for (int z = 0; z < 3; z++) {
            refs[z] = GRIS_NEUTRO;
            int acumulado = 0;
            for (int g = 0; g < 256 && total[z] > 0; g++) {
                acumulado += histo[z][g];
                if (acumulado * 2 >= total[z]) { refs[z] = Math.max(1, g); break; }
            }
        }
        REFERENCIAS.put(mascara, refs);
        return refs;
    }

    private static final Map<String, NativeImage> MASCARAS = new HashMap<>();

    /**
     * La mascara del cuerpo con los brazos del ancho de la skin del jugador
     * (2026-09-29, "adaptar la textura sola"): cada mascara viene pintada
     * para brazos classic o slim; si no coincide, cada cara del brazo se
     * reescala a lo ancho (vecino mas cercano — de 4 a 3 se pierde una
     * columna, de 3 a 4 se repite una).
     */
    @Nullable
    public static NativeImage mascaraPara(CuerpoBase cuerpo, boolean slim) {
        String clave = cuerpo.clave + "|" + slim;
        NativeImage hecha = MASCARAS.get(clave);
        if (hecha != null) return hecha;
        NativeImage original = ClothingTextureCache.imagenBase(cuerpo.textura());
        if (original == null) return null;
        if (cuerpo.slim == slim) {
            MASCARAS.put(clave, original);
            return original;
        }
        NativeImage adaptada = new NativeImage(original.getWidth(), original.getHeight(), true);
        adaptada.copyFrom(original);
        int escala = Math.max(1, original.getWidth() / LayoutSkin.LADO);
        for (Parte brazo : new Parte[]{Parte.BRAZO_DER, Parte.BRAZO_IZQ}) {
            CajaSkin desde = LayoutSkin.base(brazo, cuerpo.slim).escalada(escala);
            CajaSkin hacia = LayoutSkin.base(brazo, slim).escalada(escala);
            // Primero se limpia el lugar de las dos versiones (la classic es mas ancha).
            CajaSkin.Rect a = desde.todo(), b = hacia.todo();
            for (int y = Math.min(a.y0(), b.y0()); y < Math.max(a.y1(), b.y1()); y++) {
                for (int x = Math.min(a.x0(), b.x0()); x < Math.max(a.x1(), b.x1()); x++) adaptada.setColor(x, y, 0);
            }
            CajaSkin.Rect[] origen = {desde.arriba(), desde.abajo(), desde.derecha(), desde.frente(), desde.izquierda(), desde.atras()};
            CajaSkin.Rect[] destino = {hacia.arriba(), hacia.abajo(), hacia.derecha(), hacia.frente(), hacia.izquierda(), hacia.atras()};
            for (int k = 0; k < origen.length; k++) {
                CajaSkin.Rect o = origen[k], d = destino[k];
                for (int y = d.y0(); y < d.y1(); y++) {
                    int oy = o.y0() + (y - d.y0()) * o.altoRect() / Math.max(1, d.altoRect());
                    for (int x = d.x0(); x < d.x1(); x++) {
                        int ox = o.x0() + (x - d.x0()) * o.anchoRect() / Math.max(1, d.anchoRect());
                        adaptada.setColor(x, y, original.getColor(ox, oy));
                    }
                }
            }
        }
        MASCARAS.put(clave, adaptada);
        return adaptada;
    }

    /**
     * Cada cara del cuboide con su nivel de luz.
     *
     * 2026-09-16, probado en juego: la tapa de abajo del BRAZO es la
     * muñeca, no una zona lógicamente en sombra como la entrepierna del
     * torso o la planta del pie — con el mismo ABAJO=0.15 que esas dos
     * quedaba un cuadrado bien oscuro pegado directo contra el lateral
     * (LADO=0.32, mucho más claro), y esa diferencia tan brusca en un
     * área tan chica se leía como una línea recta de color, no un
     * sombreado suave. En el brazo se pinta la tapa de abajo al mismo
     * nivel que el lateral: mismo tono, sin costura.
     */
    private static void pintarCuerpo(NativeImage img, CajaSkin caja, int tonoRgb, Parte parte) {
        boolean brazo = parte == Parte.BRAZO_DER || parte == Parte.BRAZO_IZQ;
        pintar(img, caja.arriba(), tonoRgb, ARRIBA);
        pintar(img, caja.abajo(), tonoRgb, brazo ? LADO : ABAJO);
        pintar(img, caja.derecha(), tonoRgb, LADO);
        pintar(img, caja.frente(), tonoRgb, FRENTE);
        pintar(img, caja.izquierda(), tonoRgb, LADO);
        pintar(img, caja.atras(), tonoRgb, ATRAS);
    }

    /**
     * Un rectangulo con el tono a ese nivel, mas un ruido de un escalon.
     *
     * El ruido es lo que lo saca de "plancha de color": las skins pintadas a
     * mano nunca son un color uniforme, y una zona que si lo es canta al lado
     * de una que no. Es DETERMINISTA por pixel — si dependiera del azar
     * cambiaria en cada recomposicion y la piel titilaria.
     */
    private static void pintar(NativeImage img, CajaSkin.Rect r, int tonoRgb, float nivel) {
        for (int y = r.y0(); y < Math.min(r.y1(), img.getHeight()); y++) {
            for (int x = r.x0(); x < Math.min(r.x1(), img.getWidth()); x++) {
                int ruido = ((x * 73856093) ^ (y * 19349663)) & 0x7FFFFFFF;
                float desvio = (ruido % 3 - 1) * 0.02f;
                img.setColor(x, y, rampa(tonoRgb, nivel + desvio));
            }
        }
    }

    /**
     * El tono a esa altura de la escala, de 0 (sombra) a 1 (luz), en ABGR.
     *
     * Es una rampa y no tres tonos sueltos porque el sombreado de una caja
     * tiene cinco niveles distintos y elegir entre tres dejaba tres caras
     * pintadas iguales.
     */
    private static int rampa(int rgb, float nivel) {
        float f = 0.62f + 0.90f * Math.min(Math.max(nivel, 0f), 1f);
        int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * f));
        int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * f));
        int b = Math.min(255, Math.round((rgb & 0xFF) * f));
        // NativeImage empaqueta ABGR, no ARGB: el rojo va en los bits bajos.
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    /** La ropa interior, encima del cuerpo. Alfa parcial mezcla en vez de pisar. */
    /**
     * Pinta la ropa interior encima del cuerpo. La textura es gris: el gris
     * multiplica a {@code colorRgb}. Con {@code sombra} (la máscara de un
     * cuerpo humano), la tela además toma el relieve del cuerpo — el pecho,
     * la cola — relativo al gris medio de la máscara donde hay tela.
     */
    private static void superponer(NativeImage img, Identifier textura, int colorRgb, @Nullable NativeImage sombra) {
        NativeImage encima = ClothingTextureCache.imagenBase(textura);
        if (encima == null) return;
        // El cuerpo puede estar a otra escala que la ropa interior (las
        // máscaras HD van a 6x): se lee en la posición proporcional.
        int ew = encima.getWidth(), eh = encima.getHeight(), iw = img.getWidth(), ih = img.getHeight();
        int cr = (colorRgb >> 16) & 0xFF, cg = (colorRgb >> 8) & 0xFF, cb = colorRgb & 0xFF;
        float ref = 0f;
        if (sombra != null) {
            long suma = 0, n = 0;
            for (int y = 0; y < ih; y += 2) {
                for (int x = 0; x < iw; x += 2) {
                    if (((encima.getColor(x * ew / iw, y * eh / ih) >>> 24) & 0xFF) == 0) continue;
                    suma += grisDe(sombra.getColor(x * sombra.getWidth() / iw, y * sombra.getHeight() / ih));
                    n++;
                }
            }
            ref = n == 0 ? 0f : suma / (float) n;
        }
        for (int y = 0; y < ih; y++) {
            for (int x = 0; x < iw; x++) {
                int px = encima.getColor(x * ew / iw, y * eh / ih);
                int a = (px >>> 24) & 0xFF;
                if (a == 0) continue;
                float f = (px & 0xFF) / 255f;
                if (ref > 0f) {
                    float g = grisDe(sombra.getColor(x * sombra.getWidth() / iw, y * sombra.getHeight() / ih));
                    f *= Math.max(0.7f, Math.min(1.15f, g / ref));
                }
                int r = Math.min(255, Math.round(cr * f)), g2 = Math.min(255, Math.round(cg * f)),
                        b = Math.min(255, Math.round(cb * f));
                if (a < 255) {
                    int base = img.getColor(x, y);
                    r = mezclar(base & 0xFF, r, a);
                    g2 = mezclar((base >> 8) & 0xFF, g2, a);
                    b = mezclar((base >> 16) & 0xFF, b, a);
                }
                img.setColor(x, y, 0xFF000000 | (b << 16) | (g2 << 8) | r);
            }
        }
    }

    private static int mezclar(int abajo, int arriba, int alfa) {
        return (arriba * alfa + abajo * (255 - alfa)) / 255;
    }

    /** SkinToneSampler trabaja en ABGR; el resto del mod piensa en RGB. */
    private static int abgrARgb(int abgr) {
        return ((abgr & 0xFF) << 16) | (abgr & 0xFF00) | ((abgr >> 16) & 0xFF);
    }
}
