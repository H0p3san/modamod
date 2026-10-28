package com.modamod.client;

import com.modamod.client.DisposicionEstilista.R;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * La columna de controles de la Sublimadora (2026-10-13, "vamos con la siguiente maquina"): igual que
 * {@link DisposicionEstilista}, todas las posiciones salen de un flujo vertical por pestaña y {@link #verificar()} las
 * revisa sin abrir el juego ({@code java -cp build/classes/java/main com.modamod.client.DisposicionSublimadora}).
 */
public final class DisposicionSublimadora {
    private DisposicionSublimadora() {}

    public enum Modo { FOTO, MASCARA, CAPAS, BOLSAS, DISENOS }

    /** mascara: hay una máscara puesta (aparece "Editar"); simetria: la prenda es de a pares; dosCaras: hay Espalda. */
    public record Estado(Modo modo, boolean mascara, boolean simetria, boolean dosCaras) {}

    public static final int X = DisposicionEstilista.X, ANCHO = DisposicionEstilista.ANCHO;
    public static final int Y_CATEGORIA = 24, Y_PESTANAS = 42, Y_CONTENIDO = 60, Y_LIMITE = 266;
    public static final int H = 16, SEP = 4;
    public static final int Y_DEPOSITO = 60;

    public static Map<String, R> calcular(Estado e) {
        Map<String, R> m = new LinkedHashMap<>();
        m.put("categoria", new R(X, Y_CATEGORIA, ANCHO, 14));
        m.put("pestanas", new R(X, Y_PESTANAS, ANCHO, 13));
        switch (e.modo()) {
            case FOTO -> {
                caras(m, e);
                m.put("sel0", new R(X, 88, ANCHO, 28));
                m.put("quitarFoto", new R(X, 120, 112, H));
                m.put("escanear", new R(X + 116, 120, 112, H));
                controles(m, 140);
                extra(m, e, 180);
                m.put("estadoTexto", new R(X, 200, ANCHO, 10));
            }
            case MASCARA -> {
                caras(m, e);
                m.put("sel0", new R(X, 88, ANCHO, 28));
                m.put("quitarMascara", new R(X, 120, 112, H));
                controles(m, 140);
                extra(m, e, 180);
                m.put("estadoTexto", new R(X, 200, ANCHO, 10));
            }
            case CAPAS -> {
                m.put("capasTitulo", new R(X, 60, ANCHO, 10));
                for (int i = 0; i < 12; i++) m.put("cp" + i, new R(X + (i % 6) * 38, 74 + (i / 6) * 18, 37, H));
                m.put("cpSubir", new R(X, 114, 36, H));
                m.put("cpBajar", new R(X + 38, 114, 36, H));
                m.put("cpBorrar", new R(X + 76, 114, 36, H));
                m.put("editar", new R(X + 116, 114, 112, H));
            }
            case BOLSAS -> {
                m.put("depositoSlot", new R(X, Y_DEPOSITO, 18, 18));
                m.put("depositoAyuda", new R(X + 24, Y_DEPOSITO, ANCHO - 24, 20));
                m.put("bolsaTexto", new R(X, 84, ANCHO, 22));
            }
            case DISENOS -> {
                m.put("depositoSlot", new R(X, Y_DEPOSITO, 18, 18));
                m.put("depositoAyuda", new R(X + 24, Y_DEPOSITO, ANCHO - 24, 20));
                m.put("dUltimo", new R(X, 84, ANCHO, H));
                for (int i = 0; i < 6; i++) m.put("d" + i, new R(X, 104 + i * 18, ANCHO, H));
                m.put("dPag", new R(X, 214, ANCHO, H));
            }
        }
        return m;
    }

    private static void caras(Map<String, R> m, Estado e) {
        m.put("cara0", new R(X, 60, 114, 24));
        if (e.dosCaras()) m.put("cara1", new R(X + 118, 60, 114, 24));
    }

    /** Escala y X en una fila, Y y ángulo en la siguiente: cada control mide 112 (menos 14, valor 82, más 14). */
    private static void controles(Map<String, R> m, int y) {
        control(m, "escala", X, y);
        control(m, "x", X + 120, y);
        control(m, "y", X, y + 20);
        control(m, "angulo", X + 120, y + 20);
    }

    private static void control(Map<String, R> m, String n, int x, int y) {
        m.put(n + "Menos", new R(x, y, 14, H));
        m.put(n, new R(x + 15, y, 82, H));
        m.put(n + "Mas", new R(x + 98, y, 14, H));
    }

    private static void extra(Map<String, R> m, Estado e, int y) {
        if (e.mascara()) m.put("editar", new R(X, y, 112, H));
        if (e.simetria()) m.put("simetria", new R(X + 120, y, 112, H));
    }

    public static List<Estado> combinaciones() {
        List<Estado> l = new ArrayList<>();
        for (Modo mo : Modo.values()) {
            for (boolean a : new boolean[] {false, true}) {
                for (boolean b : new boolean[] {false, true}) {
                    for (boolean c : new boolean[] {false, true}) l.add(new Estado(mo, a, b, c));
                }
            }
        }
        return l;
    }

    public static List<String> verificar() {
        List<String> malos = new ArrayList<>();
        for (Estado e : combinaciones()) {
            List<Map.Entry<String, R>> es = new ArrayList<>(calcular(e).entrySet());
            for (int i = 0; i < es.size(); i++) {
                R a = es.get(i).getValue();
                if (a.x() < X || a.x() + a.w() > X + ANCHO || a.y() < Y_CATEGORIA || a.y() + a.h() > Y_LIMITE) {
                    malos.add(e + ": " + es.get(i).getKey() + " fuera de la columna " + a);
                }
                for (int j = i + 1; j < es.size(); j++) {
                    if (a.cruza(es.get(j).getValue())) malos.add(e + ": " + es.get(i).getKey() + " pisa a " + es.get(j).getKey());
                }
            }
        }
        return malos;
    }

    public static void autoverificar() {
        List<String> malos = verificar();
        if (!malos.isEmpty()) throw new IllegalStateException("Disposición de la Sublimadora con problemas:\n" + String.join("\n", malos));
    }

    public static void main(String[] args) {
        List<String> malos = verificar();
        if (malos.isEmpty()) {
            System.out.println("OK: " + combinaciones().size() + " combinaciones sin solapes");
        } else {
            malos.forEach(System.out::println);
            System.exit(1);
        }
    }
}
