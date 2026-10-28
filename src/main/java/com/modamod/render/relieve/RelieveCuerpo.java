package com.modamod.render.relieve;

import com.modamod.body.CuerpoBase;
import com.modamod.body.InteriorArriba;
import com.modamod.body.PerfilCuerpo;
import com.modamod.garment.Parte;

import java.util.HashMap;
import java.util.Map;

/**
 * La forma de cada cuerpo base (2026-10-01, "1 las dos si los podes
 * generar vos"): en vez de un mapa dibujado por cuerpo, cada uno tiene una
 * receta de músculos y curvas ({@link Forma}) que se arma por código, más
 * lo que elige el jugador: el busto (los Estrógenos, "generara pechos de
 * distintos tamaños") y la definición (cuánto se marcan los músculos).
 *
 * <p>Alturas en px de skin hacia afuera; la cintura de las curvas es
 * negativa (la caja se angosta). Todo se apaga contra los bordes de cada
 * cara ({@link #ventana}) para que las caras vecinas sigan pegadas en las
 * esquinas.
 */
public final class RelieveCuerpo {

    private RelieveCuerpo() {}

    /**
     * Receta de un cuerpo, cada valor de 0 (nada) a ~1 (muy marcado).
     * {@code busto} es el tamaño base (0..6) que el cuerpo trae de fábrica.
     */
    public record Forma(float pecho, float abdomen, float panza, float cola, float brazos, float piernas,
                        float cintura, int busto) {}

    private static final Map<CuerpoBase, Forma> FORMAS = new HashMap<>();
    static {
        FORMAS.put(CuerpoBase.SKIN_REAL, new Forma(0.15f, 0.05f, 0f, 0.3f, 0.15f, 0.2f, 0f, 0));
        FORMAS.put(CuerpoBase.ESTANDAR, new Forma(0.3f, 0.15f, 0f, 0.4f, 0.25f, 0.3f, 0f, 0));
        FORMAS.put(CuerpoBase.DELGADO, new Forma(0.15f, 0.05f, 0f, 0.25f, 0.12f, 0.18f, 0f, 0));
        FORMAS.put(CuerpoBase.ATLETICO, new Forma(0.65f, 0.6f, 0f, 0.55f, 0.55f, 0.55f, 0f, 0));
        FORMAS.put(CuerpoBase.MUSCULOSO, new Forma(1f, 1f, 0f, 0.65f, 1f, 0.8f, 0f, 0));
        FORMAS.put(CuerpoBase.GORDITO, new Forma(0.35f, 0f, 1f, 0.6f, 0.3f, 0.45f, 0f, 0));
        FORMAS.put(CuerpoBase.VELLUDO, new Forma(0.45f, 0.25f, 0.2f, 0.45f, 0.35f, 0.4f, 0f, 0));
        FORMAS.put(CuerpoBase.FEM_ESTANDAR, new Forma(0.1f, 0.05f, 0f, 0.65f, 0.2f, 0.35f, 0.25f, 2));
        FORMAS.put(CuerpoBase.FEM_ATLETICA, new Forma(0.25f, 0.45f, 0f, 0.75f, 0.4f, 0.55f, 0.25f, 1));
        FORMAS.put(CuerpoBase.FEM_CURVAS, new Forma(0.1f, 0f, 0.1f, 1f, 0.25f, 0.6f, 0.55f, 3));
        FORMAS.put(CuerpoBase.FEM_GORDITA, new Forma(0.1f, 0f, 0.8f, 0.9f, 0.3f, 0.6f, 0.15f, 3));
        // Animales (2026-10-01, "animales tambien"): panza redonda los
        // gorditos, pecho de pelaje el lobo y el zorro, felinos atléticos.
        FORMAS.put(CuerpoBase.CEBRA, new Forma(0.4f, 0.3f, 0f, 0.5f, 0.4f, 0.5f, 0f, 0));
        FORMAS.put(CuerpoBase.DALMATA, new Forma(0.3f, 0.15f, 0f, 0.4f, 0.25f, 0.3f, 0f, 0));
        FORMAS.put(CuerpoBase.LEOPARDO, new Forma(0.6f, 0.5f, 0f, 0.55f, 0.55f, 0.55f, 0.1f, 0));
        FORMAS.put(CuerpoBase.TIGRE, new Forma(0.7f, 0.55f, 0f, 0.55f, 0.65f, 0.6f, 0f, 0));
        FORMAS.put(CuerpoBase.LOBO, new Forma(0.75f, 0.1f, 0f, 0.4f, 0.35f, 0.4f, 0f, 0));
        FORMAS.put(CuerpoBase.ZORRO, new Forma(0.55f, 0.05f, 0f, 0.45f, 0.25f, 0.35f, 0.15f, 0));
        FORMAS.put(CuerpoBase.OSITO, new Forma(0.3f, 0f, 1f, 0.6f, 0.3f, 0.45f, 0f, 0));
        FORMAS.put(CuerpoBase.PANDA, new Forma(0.3f, 0f, 1.1f, 0.6f, 0.3f, 0.45f, 0f, 0));
        FORMAS.put(CuerpoBase.VACA, new Forma(0.3f, 0f, 0.7f, 0.5f, 0.3f, 0.4f, 0f, 0));
    }

    /** Tope del busto (base del cuerpo + Estrógenos), en las unidades de {@link Forma#busto}. */
    public static final float BUSTO_MAXIMO = 10f;
    /**
     * Cuánto busto suma cada dosis acumulada de Estrógenos (2026-10-01, "3
     * tamaños de mamas... a traves de la acumulacion de tres ingestas" → "le
     * demos 5 tamaños a los pechos llegando a uno bastante mas grande"): el
     * último salta bastante más que los otros. Más dos chicos al principio
     * (2026-10-02, "hace dos tamaños mas chicos de busto").
     */
    private static final float[] BUSTO_POR_DOSIS = {0f, 0.5f, 0.9f, 1.3f, 2.6f, 3.9f, 5.4f, 8f};

    /** Busto (unidades de {@link Forma#busto}) de un talle 0..{@link PerfilCuerpo#BUSTO_MAXIMO} — el maniquí lo usa directo. */
    public static float bustoDeTalle(int talle) {
        return BUSTO_POR_DOSIS[Math.max(0, Math.min(BUSTO_POR_DOSIS.length - 1, talle))];
    }

    private static final Map<String, MapaRelieve> CACHE = new HashMap<>();

    public static Forma formaDe(CuerpoBase cuerpo) {
        return FORMAS.getOrDefault(cuerpo, FORMAS.get(CuerpoBase.ESTANDAR));
    }

    /**
     * El busto que se ve: con Estrógenos activos, SOLO el de las dosis — el
     * del cuerpo se apaga (2026-10-02, "se veia muy grande porque se sumaba
     * al busto de la skin base reconstruida que te dije que desactives
     * cuando tenga busto por estrogenos"); sin Estrógenos, el del cuerpo.
     */
    public static float bustoDe(PerfilCuerpo perfil) {
        if (perfil.busto() == PerfilCuerpo.BUSTO_PLANO) return 0f;   // testosterona: ni el del cuerpo
        int dosis = Math.max(0, Math.min(BUSTO_POR_DOSIS.length - 1, perfil.busto()));
        if (dosis > 0) return BUSTO_POR_DOSIS[dosis];
        return Math.min(BUSTO_MAXIMO, formaDe(perfil.cuerpo()).busto());
    }

    /**
     * Cuánto sale la cola (px) con este busto (2026-10-02, "el trasero deberia
     * crecer con el busto"): la del cuerpo ({@code colaCuerpo} 0..1) más lo que
     * suma el talle del busto.
     */
    public static float colaDeTalle(float busto, float colaCuerpo) {
        return busto <= 0f ? 0f : 0.35f + 0.45f * colaCuerpo + 0.17f * busto;
    }

    /** La cola de un perfil (0 sin busto: ahí la dan las nalgas del relieve). */
    public static float colaDe(PerfilCuerpo perfil) {
        return colaDeTalle(bustoDe(perfil), formaDe(perfil.cuerpo()).cola());
    }

    public static MapaRelieve de(PerfilCuerpo perfil, boolean slim) {
        Forma f = formaDe(perfil.cuerpo());
        float busto = bustoDe(perfil);
        float def = perfil.definicion() / 100f;
        // La ropa interior también da forma (2026-10-01): el binder aplana el
        // pecho y el top deportivo lo sujeta un poco.
        InteriorArriba arriba = perfil.interior().arriba();
        float sujecion = arriba == InteriorArriba.BINDER ? 0.3f : arriba == InteriorArriba.DEPORTIVO ? 0.75f : 1f;
        String clave = "cuerpo/" + perfil.cuerpo().clave + "/" + busto + "/" + perfil.definicion() + "/" + sujecion + "/" + slim;
        MapaRelieve m = CACHE.get(clave);
        if (m != null) return m;
        if (CACHE.size() > 64) CACHE.clear();
        m = MapaRelieve.generar(clave, slim, (parte, cara, fu, fv, ancho) -> altura(f, busto, def, sujecion, parte, cara, fu, fv, ancho));
        CACHE.put(clave, m);
        return m;
    }

    // ── la receta ──────────────────────────────────────────────────────────

    static float altura(Forma f, float busto, float def, float sujecion, Parte parte, int cara, float fu, float fv, int ancho) {
        boolean frente = cara == 1, espalda = cara == 3;
        boolean derecha = parte == Parte.BRAZO_DER || parte == Parte.PIERNA_DER;
        // El costado de afuera de un brazo o una pierna.
        boolean afuera = (derecha && cara == 0) || (!derecha && cara == 2);
        float h = 0f;
        switch (parte) {
            case TORSO -> {
                if (frente) {
                    // Pectorales: dos placas anchas arriba, con el surco del medio.
                    h += 0.75f * f.pecho() * def * (bulto(fu, fv, 0.27f, 0.25f, 0.23f, 0.13f, 0.6f)
                            + bulto(fu, fv, 0.73f, 0.25f, 0.23f, 0.13f, 0.6f));
                    // Abdominales: 3 pares de cuadraditos con la línea del medio.
                    float abs = 0f;
                    for (float y : new float[]{0.48f, 0.6f, 0.72f}) {
                        abs += bulto(fu, fv, 0.385f, y, 0.1f, 0.05f, 0.6f) + bulto(fu, fv, 0.615f, y, 0.1f, 0.05f, 0.6f);
                    }
                    h += 0.32f * f.abdomen() * def * abs;
                    // Panza: una cúpula grande y baja.
                    h += 0.9f * f.panza() * domo(fu, fv, 0.5f, 0.66f, 0.48f, 0.36f);
                    // El busto ya no va en el relieve (2026-10-01, "sacaria los pechos
                    // de la normal de la skin reconstruida porque se superponen"):
                    // es geometría propia, estilo Only Jugs — ver render.relieve.BustoRender.
                } else if (espalda) {
                    // Omóplatos y, abajo, el principio de la cola.
                    h += 0.3f * f.pecho() * def * (bulto(fu, fv, 0.3f, 0.22f, 0.2f, 0.14f, 1.5f)
                            + bulto(fu, fv, 0.7f, 0.22f, 0.2f, 0.14f, 1.5f));
                    // Nalgas (2026-10-01, "estan apareciendo en las piernas en lugar
                    // del torso"): abajo de la espalda del torso, redondas.
                    // Con busto, la cola es geometría propia (BustoRender.formasCola, 2026-10-02).
                    if (busto <= 0f) h += 0.8f * f.cola() * (domo(fu, fv, 0.29f, 0.86f, 0.27f, 0.17f)
                            + domo(fu, fv, 0.71f, 0.86f, 0.27f, 0.17f));
                    h += 0.25f * f.panza() * domo(fu, fv, 0.5f, 0.75f, 0.5f, 0.3f);
                } else {
                    // Costados: la panza asoma y la cintura de las curvas se angosta.
                    h += 0.45f * f.panza() * domo(fu, fv, 0.5f, 0.68f, 0.6f, 0.32f);
                    h -= 0.45f * f.cintura() * bulto(fu, fv, 0.5f, 0.66f, 0.7f, 0.16f, 1f);
                    h += 0.3f * f.cintura() * bulto(fu, fv, 0.5f, 0.97f, 0.7f, 0.12f, 1f);
                }
                // Arriba (cuello/hombros) y en las esquinas, sin relieve. Abajo no:
                // la cola y la panza llegan hasta la cadera.
                return h * ventana(fu, 0.15f) * rampa(fv, 0.06f);
            }
            case BRAZO_DER, BRAZO_IZQ -> {
                float m = f.brazos() * def;
                if (frente) h += 0.6f * m * bulto(fu, fv, 0.5f, 0.32f, 0.42f, 0.17f, 1.3f);       // bíceps
                if (espalda) h += 0.4f * m * bulto(fu, fv, 0.5f, 0.3f, 0.42f, 0.17f, 1.3f);        // tríceps
                if (afuera) h += 0.4f * m * bulto(fu, fv, 0.5f, 0.08f, 0.5f, 0.12f, 1.3f);         // deltoides
                h += 0.15f * m * bulto(fu, fv, 0.5f, 0.66f, 0.45f, 0.12f, 1f);                     // antebrazo
                return h * ventana(fu, 0.25f) * rampa(fv, 0.03f) * rampa(1f - fv, 0.06f);
            }
            case PIERNA_DER, PIERNA_IZQ -> {
                float m = f.piernas() * def;
                // Muslos más definidos (2026-10-01, "quizas le daria mas definicion
                // a los muslos"): cuádriceps en dos cabezas al frente, isquios
                // atrás, el muslo de afuera y la rodilla marcada.
                if (frente) {
                    h += 0.5f * m * (bulto(fu, fv, 0.36f, 0.26f, 0.3f, 0.2f, 0.9f)
                            + bulto(fu, fv, 0.66f, 0.3f, 0.28f, 0.18f, 0.9f));
                    h += 0.2f * m * bulto(fu, fv, 0.5f, 0.47f, 0.3f, 0.05f, 1f);                     // rodilla
                }
                if (espalda) {
                    h += 0.3f * m * bulto(fu, fv, 0.5f, 0.24f, 0.42f, 0.16f, 1f);                    // isquios
                    h += 0.55f * m * bulto(fu, fv, 0.5f, 0.62f, 0.4f, 0.16f, 1.3f);                  // gemelos
                }
                if (afuera) {
                    h += 0.35f * f.cintura() * bulto(fu, fv, 0.5f, 0.08f, 0.55f, 0.16f, 1f);         // cadera
                    h += 0.35f * m * bulto(fu, fv, 0.5f, 0.27f, 0.45f, 0.2f, 1f);                    // muslo de afuera
                }
                // Arriba no se apaga: la cola sigue desde el torso.
                return h * ventana(fu, 0.25f) * rampa(1f - fv, 0.06f);
            }
            default -> {
                return 0f;
            }
        }
    }

    /** Lomita suave (1 − d²)^k en una elipse de radios ru × rv. */
    static float bulto(float fu, float fv, float cu, float cv, float ru, float rv, float k) {
        float du = (fu - cu) / ru, dv = (fv - cv) / rv;
        float d2 = du * du + dv * dv;
        if (d2 >= 1f) return 0f;
        return (float) Math.pow(1f - d2, k + 1f);
    }

    /** Cúpula redonda (media esfera aplastada), con el borde suavizado. */
    static float domo(float fu, float fv, float cu, float cv, float ru, float rv) {
        float du = (fu - cu) / ru, dv = (fv - cv) / rv;
        float d2 = du * du + dv * dv;
        if (d2 >= 1f) return 0f;
        float s = (float) Math.sqrt(1f - d2);
        return s * s * (3f - 2f * s);
    }

    /** 0 en los bordes izquierdo/derecho de la cara, 1 adentro (suave). */
    static float ventana(float fu, float borde) {
        return rampa(fu, borde) * rampa(1f - fu, borde);
    }

    static float rampa(float t, float borde) {
        if (t <= 0f) return 0f;
        if (t >= borde) return 1f;
        float x = t / borde;
        return x * x * (3f - 2f * x);
    }
}
