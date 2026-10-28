package com.modamod.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * La matemática de cómo se apoya un aplique (2026-10-04, "quiero separar conceptualmente orientación,
 * pivote, cara de contacto, posición y profundidad"). Sin nada de Minecraft (solo JOML) para poder
 * probarla suelta.
 *
 * <p>Trabaja en el marco {@code S} de la superficie: origen sobre la tela, +X a la derecha, +Y hacia
 * arriba y +Z hacia ADENTRO de la tela (el objeto vive del lado -Z, hacia afuera). Y en el espacio del
 * objeto: el del modelo de Minecraft centrado en el cubo unitario (x este, y arriba, z sur; el frente de
 * un bloque es el norte, -z).
 *
 * <p>La cadena, de afuera hacia adentro, para un punto {@code v} del objeto:
 * <pre>
 *   S · T(0,0,-offset) · T(desplazamiento) · [T(p) · Rosc · T(-p)] · R_user · S_user · Q_f · k · T(-c_f)
 * </pre>
 * Esta clase arma la parte {@code R_user · S_user · Q_f · k · T(-c_f)} ({@link #calcular}) y los ángulos
 * del nodo de oscilación ({@link #angulos}) con su pivote ({@link #pivote}).
 */
public final class TransformAplique {

    private TransformAplique() {}

    /** La parte objeto → S, y el tamaño de la cara de contacto ya orientada y escalada, en los ejes de S. */
    public record Resultado(Matrix4f matriz, float ancho, float alto, float profundidad) {}

    /**
     * {@code Q_f}: la rotación que lleva la normal saliente de la cara {@code (nx, ny, nz)} del objeto a +Z
     * de S (hacia adentro de la tela). En las caras de los costados, el frente y el fondo, "arriba" del
     * objeto sigue siendo +Y; apoyado sobre su cara de arriba o de abajo, el frente del objeto queda hacia arriba.
     */
    public static Matrix4f rotacionBase(int nx, int ny, int nz) {
        final float pi = (float) Math.PI;
        Matrix4f q = new Matrix4f();
        if (nz > 0) return q;                                   // atrás (sur): lo de siempre
        if (nz < 0) return q.rotationY(pi);                     // frente (norte)
        if (nx > 0) return q.rotationY(-pi / 2f);               // este
        if (nx < 0) return q.rotationY(pi / 2f);                // oeste
        if (ny > 0) return q.rotationX(pi / 2f);                // arriba
        return q.rotationZ(pi).rotateX(-pi / 2f);               // abajo
    }

    /**
     * @param n    normal saliente de la cara elegida, en el espacio del objeto
     * @param min  esquina mínima de la caja del objeto (centrado en el cubo unitario)
     * @param max  esquina máxima
     * @param k    escala de normalización (que el lado más largo mida el tamaño base)
     * @param rot  rotación del usuario en grados (x, y, z), alrededor del centro de la cara de contacto
     * @param esc  escala del usuario (x, y, z) en los ejes de S
     */
    public static Resultado calcular(int[] n, float[] min, float[] max, float k, float[] rot, float[] esc) {
        float[] ctr = new float[3], mitad = new float[3], cara = new float[3];
        for (int i = 0; i < 3; i++) {
            ctr[i] = (min[i] + max[i]) / 2f;
            mitad[i] = (max[i] - min[i]) / 2f;
            cara[i] = ctr[i] + n[i] * mitad[i];                  // centro de la cara elegida
        }
        Matrix4f q = rotacionBase(n[0], n[1], n[2]);
        Matrix4f m = new Matrix4f()
                .rotationZYX((float) Math.toRadians(rot[2]), (float) Math.toRadians(rot[1]), (float) Math.toRadians(rot[0]))
                .scale(esc[0], esc[1], esc[2])
                .mul(q)
                .scale(k)
                .translate(-cara[0], -cara[1], -cara[2]);
        // El tamaño de la caja en los ejes de S (q solo permuta ejes): ancho (x), alto (y), profundidad (z).
        Vector3f dim = q.transformDirection(new Vector3f(max[0] - min[0], max[1] - min[1], max[2] - min[2]));
        return new Resultado(m, Math.abs(dim.x) * k * esc[0], Math.abs(dim.y) * k * esc[1], Math.abs(dim.z) * k * esc[2]);
    }

    /** Dónde está el pivote de la oscilación en S: {@code modo} 0 = borde superior, 1 = centro de la cara, 2 = centro del objeto, 3 = libre. */
    public static float[] pivote(int modo, float ancho, float alto, float profundidad, float ox, float oy, float oz) {
        float x = 0f, y = 0f, z = 0f;
        switch (modo) {
            case 0 -> y = alto / 2f;                 // centro del borde de arriba de la cara de contacto
            case 2 -> z = -profundidad / 2f;         // el centro del objeto, hacia afuera de la tela
            default -> { }
        }
        return new float[] { x + ox, y + oy, z + oz };
    }

    /**
     * Los giros del nodo de oscilación {@code {θx, θz}} (radianes).
     * {@code eje}: 0 = en el plano (gira alrededor de la normal de la tela: el objeto sigue paralelo a ella),
     * 1 = bisagra (se despega de la tela, nunca la atraviesa), 2 = ambos. {@code dx}, {@code dz} = cuánto lo
     * empuja la inercia a los lados y hacia adentro de la tela (-1..1); {@code idle} = el vaivén propio (-1..1).
     * Con intensidad 0 no se mueve.
     */
    public static float[] angulos(int eje, float amplitudRad, float intensidad, float dx, float dz, float idle) {
        if (intensidad <= 0f || amplitudRad <= 0f) return new float[] { 0f, 0f };
        float tz = 0f, tx = 0f;
        if (eje != 1) tz = amplitudRad * clamp((dx + idle) * intensidad, -1f, 1f);
        if (eje != 0) tx = amplitudRad * clamp((Math.max(0f, -dz) + Math.max(0f, idle)) * intensidad, 0f, 1f);
        return new float[] { tx, tz };
    }

    private static float clamp(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }
}
