package com.modamod.render;

import net.minecraft.client.model.ModelPart;
import net.minecraft.util.math.Direction;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * La pollera — cuarta vuelta (2026-09-16). Las tres anteriores (pétalos
 * gruesos, tubo de aritos, paneles anchos planos) se armaron a mano con
 * trigonometría propia y ninguna se vio bien — la última salió "un
 * amontonamiento de esquirlas" (ver captura). En vez de seguir adivinando
 * ángulos, se decompiló el modelo real de POLLERA de un mod de referencia
 * que el usuario tenía en Descargas: {@code MorePlayerModels-1.20.1...jar},
 * archivo {@code assets/moreplayermodels/parts/body/skirt.json} (autor
 * Noppes). La técnica de ESE archivo es la que se reproduce acá:
 *
 * <ul>
 *   <li>10 gajos, cada uno rotado {@code 360/10} grados alrededor de UN
 *   pivote compartido (no 10 pivotes distintos) — el offset hacia afuera
 *   pasa DESPUÉS, en un hijo, así que el resultado es un aro igual, sin el
 *   "efecto embudo" de compartir el punto de caída.</li>
 *   <li>Cada gajo es un hijo ("inner") desplazado {@code (2.4, 8.8, 0)}
 *   respecto de ese pivote — abajo y afuera — con una inclinación
 *   compuesta de solo {@code pitch=17.18°, yaw=-11.5°, roll=-11.5°}: MUCHO
 *   más suave que los 50-75° que se probaron antes (de ahí el aspecto
 *   "roto" — estaba todo tirado hacia afuera de más).</li>
 *   <li>Cada gajo NO es un panel único: son DOS planos finos, uno que
 *   cuelga derecho y otro plegado 90° desde un punto 2 unidades más
 *   afuera — un pliegue en V, no un panel plano. Es este pliegue el que
 *   cierra el hueco con el gajo vecino y da el aspecto de tela, no de
 *   esquirla.</li>
 * </ul>
 *
 * Los números (2.4, 8.8, 17.18, -11.5, tamaño 9x2) son los del archivo
 * original — ya están en la misma convención de unidades que este mod
 * (1/16 de bloque, Y creciendo hacia abajo), así que se reusan directo en
 * vez de reinventarlos.
 *
 * <p><b>Quinta vuelta (2026-09-18):</b> con 10 gajos el ruedo (y el borde
 * de arriba, donde pliega contra el cuerpo) salía muy dentado — cada gajo
 * es un rectángulo RÍGIDO (un {@code ModelPart.Cuboid} no se puede
 * angostar hacia la punta), así que al inclinarlo se arma un pico bien
 * marcado en vez de una caída pareja. Se dobló a {@link #GAJOS} = 20 (la
 * mitad de {@link #ANCHO_GAJO} y {@link #OFFSET_PLIEGUE} cada uno, mismo
 * radio total) — con el doble de pliegues más angostos cada pico es la
 * mitad de profundo, así que a ojo se ve una caída pareja en vez de
 * flecos. Se sumó además {@link #cinto(float, float)}: un aro de paneles SIN inclinar
 * a la altura de {@link #OFFSET_ABAJO} que tapa esa costura irregular
 * contra el torso (pedido explícito: "que la parte de arriba se integre
 * más como prenda").</p>
 */
public final class PolleraGeometria {

    private PolleraGeometria() {}

    private static final int GAJOS = 20;
    private static final float PASO_YAW = 360f / GAJOS;

    /**
     * El archivo original ancla estos números a SU propio jugador y
     * después escala TODA la pollera x[1.7, 1.04, 1.6] para que le quede
     * bien puesta — escala que nunca se copió (por eso "se afinaba de
     * golpe": nuestro aro (radio 2.4) quedaba bien adentro del torso,
     * medio-ancho 4). En vez de reproducir esa escala en el render (nuestro
     * root fuerza una escala UNIFORME después de copyTransform, ver
     * {@code GarmentFeatureRenderer#dibujarModelPart}), se hornea acá:
     * todas las medidas horizontales (radio, ancho de gajo, pliegue)
     * multiplicadas por el mismo factor (~1.79, lo que hace falta para que
     * el radio 2.4 llegue a 4.3 — el mismo radio de cintura ya usado en el
     * intento anterior de pétalos). La caída (Y) NO se escala — el
     * original casi no la tocaba (1.04) y ya coincidía con nuestra cintura.
     */
    private static final float ESCALA_RADIAL = 1.79f;

    /** Offset del gajo respecto del pivote compartido: afuera y abajo. */
    private static final float OFFSET_AFUERA = 2.4f * ESCALA_RADIAL;
    private static final float OFFSET_ABAJO = 8.8f;

    /** Inclinación compuesta de cada gajo (la misma para los 10, en su propio marco ya rotado). */
    private static final float TILT_PITCH = 17.18f;
    private static final float TILT_YAW = -11.5f;
    private static final float TILT_ROLL = -11.5f;

    private static final float LARGO_GAJO = 9f;
    /** Mitad que en la primera vuelta (10 gajos): al doble de gajos, cada
     *  uno tiene que ser la mitad de ancho para mantener el mismo radio. */
    private static final float ANCHO_GAJO = 1f * ESCALA_RADIAL;
    private static final float GROSOR = 0.1f;

    /** El segundo plano de cada gajo se pliega desde un punto más afuera. */
    private static final float OFFSET_PLIEGUE = 1.0f * ESCALA_RADIAL;

    /** Alto (en Y) del aro de cintura que tapa la costura contra el torso. */
    private static final float CINTO_ALTO = 2.5f;
    /** Cada panel del cinto un poco más ancho que el hueco entre gajos,
     *  para que se solapen y no se vea costura entre paneles del cinto. */
    private static final float CINTO_ANCHO = ANCHO_GAJO * 1.4f;

    /**
     * El torso de referencia (mismo que usa el resto del cuerpo) es una
     * caja de 8×4 (ancho×profundidad), NO un cilindro — pero el cinto es
     * un aro. Un radio fijo (probado primero, {@code OFFSET_AFUERA - 0.4}
     * ≈ 3.9) queda bien en los costados (medio-ancho real 4) pero flota
     * ~1.7 unidades separado del cuerpo adelante/atrás (medio-profundidad
     * real 2). Por eso el cinto es una ELIPSE — semieje X ≈ medio-ancho,
     * semieje Z ≈ medio-profundidad — en vez de un círculo, para que el
     * radio de cada panel siga la forma rectangular real del torso en
     * vez de la del aro de gajos (que si es circular, porque ahí no
     * importa: el vuelo de la tela ya tapa esa diferencia).
     */
    private static final float CINTO_SEMIEJE_X = OFFSET_AFUERA - 0.4f;
    private static final float CINTO_SEMIEJE_Z = 1.9f;

    /** Radio del cinto para el panel en {@code yaw} grados (aro elíptico, ver {@link #CINTO_SEMIEJE_X}). */
    private static float cintoRadio(float yaw) {
        double rad = Math.toRadians(yaw);
        double cos = Math.cos(rad), sin = Math.sin(rad);
        double a = CINTO_SEMIEJE_X, b = CINTO_SEMIEJE_Z;
        return (float) ((a * b) / Math.sqrt((b * cos) * (b * cos) + (a * sin) * (a * sin)));
    }

    private static final Map<Float, ModelPart> RAICES_POR_DILATACION = new java.util.HashMap<>();

    /** La pollera entera (los gajos plegados + el cinto), cacheada por dilatación (Calce). */
    public static ModelPart raiz(float dilatacion) {
        ModelPart cacheada = RAICES_POR_DILATACION.get(dilatacion);
        if (cacheada != null) return cacheada;

        Map<String, ModelPart> hijos = new java.util.HashMap<>();
        for (int i = 0; i < GAJOS; i++) {
            hijos.put("gajo" + i, gajo(i * PASO_YAW, dilatacion));
            hijos.put("cinto" + i, cinto(i * PASO_YAW, dilatacion));
        }
        ModelPart raiz = vacia(0, 0, 0, 0, 0, 0, hijos);
        RAICES_POR_DILATACION.put(dilatacion, raiz);
        return raiz;
    }

    /**
     * Un gajo: pivote compartido rotado, hijo "inner" desplazado+inclinado,
     * dos planos plegados en L.
     *
     * El plano plegado NO tiene pivote propio en (2,0,0) — eso fue el bug
     * de la primera vuelta ("los planos no se tocan"): en el archivo
     * original el offset de 2 unidades está horneado en la geometría del
     * cuboide, y la rotación de -90° pasa alrededor del origen de
     * "inner", NO alrededor de ese punto. Rotar alrededor de un pivote
     * propio da un pliegue distinto (y con hueco) que rotar un cuboide ya
     * desplazado alrededor del origen del padre — ver
     * {@code NopModelPart.compile()}/{@code addPlane()} del mod original.
     */
    private static ModelPart gajo(float yaw, float dilatacion) {
        ModelPart planoRecto = plano(0f, 0f, dilatacion);
        ModelPart planoPlegado = plano(OFFSET_PLIEGUE, -90f, dilatacion);

        ModelPart inner = vacia(OFFSET_AFUERA, OFFSET_ABAJO, 0,
                TILT_PITCH, TILT_YAW, TILT_ROLL,
                Map.of("recto", planoRecto, "plegado", planoPlegado));

        return vacia(0, 0, 0, 0, yaw, 0, Map.of("inner", inner));
    }

    /**
     * Un panel del cinto: SIN inclinar (a diferencia del gajo, que cuelga
     * con {@code TILT_*}), centrado en Z (a diferencia del plano del gajo,
     * que arranca "de un lado" para que el pliegue coincida con el borde).
     * Se ubica a {@link #cintoRadio(float)} de distancia del eje (elipse,
     * no círculo — ver esa función) y centrado en {@link #OFFSET_ABAJO} —
     * mitad tapando el borde de arriba de los gajos, mitad tapando el
     * borde de abajo del torso — así disimula la costura entre los dos en
     * vez de dejarla a la vista.
     */
    private static ModelPart cinto(float yaw, float dilatacion) {
        float radio = cintoRadio(yaw);
        EnumSet<Direction> caras = EnumSet.allOf(Direction.class);
        ModelPart.Cuboid cubo = new ModelPart.Cuboid(0, 0,
                radio - GROSOR / 2, OFFSET_ABAJO - CINTO_ALTO / 2, -CINTO_ANCHO / 2,
                GROSOR, CINTO_ALTO, CINTO_ANCHO,
                dilatacion, dilatacion, dilatacion, false, 64, 64, caras);
        ModelPart panel = new ModelPart(List.of(cubo), Map.of());
        return vacia(0, 0, 0, 0, yaw, 0, Map.of("panel", panel));
    }

    /**
     * Un plano fino: nace en Y:[0,LARGO_GAJO], Z:[0,ANCHO_GAJO] (NO
     * centrado en Z — mismo origen "de un lado" que usa el archivo
     * original, importa para que el plegado coincida con el borde del
     * plano recto), casi sin grosor en X. {@code offsetX} desplaza el
     * cuboide en X ANTES de rotar (horneado en la geometría, no en el
     * pivote de la ModelPart) — así es como el original arma el pliegue.
     */
    private static ModelPart plano(float offsetX, float yawPropio, float dilatacion) {
        EnumSet<Direction> caras = EnumSet.allOf(Direction.class);
        ModelPart.Cuboid cubo = new ModelPart.Cuboid(0, 0,
                offsetX - GROSOR / 2, 0, 0, GROSOR, LARGO_GAJO, ANCHO_GAJO,
                dilatacion, dilatacion, dilatacion, false, 64, 64, caras);
        ModelPart parte = new ModelPart(List.of(cubo), Map.of());
        parte.yaw = (float) Math.toRadians(yawPropio);
        return parte;
    }

    /** Un nodo sin geometría propia, solo transform — para los pivotes intermedios (partN/innerN de MPM). */
    private static ModelPart vacia(float pivotX, float pivotY, float pivotZ,
                                    float pitch, float yaw, float roll, Map<String, ModelPart> hijos) {
        ModelPart parte = new ModelPart(List.of(), hijos);
        parte.pivotX = pivotX;
        parte.pivotY = pivotY;
        parte.pivotZ = pivotZ;
        parte.pitch = (float) Math.toRadians(pitch);
        parte.yaw = (float) Math.toRadians(yaw);
        parte.roll = (float) Math.toRadians(roll);
        return parte;
    }
}
