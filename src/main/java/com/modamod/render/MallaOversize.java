package com.modamod.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Las mallas de la ropa oversize, modeladas en Blockbench ({@code docs/modelo_base_oversize/base_oversize.bbmodel}) y
 * exportadas con {@code tools/exportar_oversize.py} a {@code assets/modamod/mallas/oversize.json} — a pedido
 * (2026-10-16, "la remera oversize ... incorporemos contemplando los moldes de remera" y "ahora al pantalon oversize").
 * Reemplazan a la malla procedural ({@link CuerpoGeometria#telaSuavePorFilas}) cuando existen: el modelo ya trae la silueta
 * (mangas ajustadas en el hombro que se abren, torso que cae, pantalón de una pieza) y acá solo se recorta por filas.
 *
 * <p>Partes: {@code torso}, {@code brazo_der}, {@code brazo_izq} (remera/chaqueta), {@code cintura} (la banda del pantalón, va
 * con el torso) y {@code pierna_der}, {@code pierna_izq} (pantalón). Coordenadas de Blockbench: y arriba, 1 unidad = 1 px,
 * hombros en y = 24, el frente es −Z y el costado derecho del jugador es −X.
 */
final class MallaOversize {
    private MallaOversize() {}

    /** Una cara: banda = n° del anillo de arriba (−1 = tapa), vértices (índices) y UV en unidades de skin (64x64). */
    record Cara(int banda, int[] v, float[][] uv) {}

    /** {@code anillo[i]}: n° de anillo (0 = el de más arriba) del vértice {@code i}, por su altura. */
    record Datos(float[][] v, List<Cara> caras, int bandas, int[] anillo) {}

    private static Map<String, Datos> datos;

    /** La malla de esa parte, o null si el recurso no está (queda la malla procedural). */
    static synchronized Datos de(String clave) {
        if (datos == null) datos = cargar();
        return datos.get(clave);
    }

    private static Map<String, Datos> cargar() {
        Map<String, Datos> m = new HashMap<>();
        try (InputStream in = MallaOversize.class.getResourceAsStream("/assets/modamod/mallas/oversize.json")) {
            if (in == null) return m;
            JsonObject raiz = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : raiz.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                JsonArray va = o.getAsJsonArray("v");
                float[][] v = new float[va.size()][];
                for (int i = 0; i < v.length; i++) v[i] = flotantes(va.get(i).getAsJsonArray());
                List<Cara> caras = new ArrayList<>();
                int bandas = 0;
                for (JsonElement fe : o.getAsJsonArray("f")) {
                    JsonArray f = fe.getAsJsonArray();
                    int banda = f.get(0).getAsInt();
                    JsonArray ids = f.get(1).getAsJsonArray(), uvs = f.get(2).getAsJsonArray();
                    int[] iv = new int[ids.size()];
                    float[][] uv = new float[iv.length][];
                    for (int i = 0; i < iv.length; i++) {
                        iv[i] = ids.get(i).getAsInt();
                        uv[i] = flotantes(uvs.get(i).getAsJsonArray());
                    }
                    caras.add(new Cara(banda, iv, uv));
                    bandas = Math.max(bandas, banda + 1);
                }
                java.util.TreeSet<Integer> alturas = new java.util.TreeSet<>(java.util.Comparator.reverseOrder());
                for (float[] p : v) alturas.add(Math.round(p[1] * 1000F));
                java.util.List<Integer> orden = new ArrayList<>(alturas);
                int[] anillo = new int[v.length];
                for (int i = 0; i < v.length; i++) anillo[i] = orden.indexOf(Math.round(v[i][1] * 1000F));
                m.put(e.getKey(), new Datos(v, caras, bandas, anillo));
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("modamod").warn("Oversize: no se pudieron leer las mallas", e);
            m.clear();
        }
        return m;
    }

    private static float[] flotantes(JsonArray a) {
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
        return f;
    }
}
