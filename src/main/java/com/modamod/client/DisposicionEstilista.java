package com.modamod.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * La columna de controles de la Estilista (2026-10-13, "literal no se como aplicar nada. ordenalo bien chequea dos
 * veces... arma infraestructura para el exito"): TODAS las posiciones salen de acá, de un flujo vertical por pestaña,
 * así dos controles no pueden quedar en el mismo lugar. Es Java puro (sin Minecraft): {@link #autoverificar()} recorre
 * cada pestaña y cada combinación de estado y falla si algo se pisa o se sale de la columna; se corre con
 * {@code java -cp build/classes/java/main com.modamod.client.DisposicionEstilista} sin abrir el juego, y la pantalla la
 * vuelve a chequear en vivo con widgets reales ({@code EstiladoScreen.verificarSolapes}).
 */
public final class DisposicionEstilista {
    private DisposicionEstilista() {}

    /** Un rectángulo en px de la ventana. */
    public record R(int x, int y, int w, int h) {
        public boolean cruza(R o) {
            return x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h;
        }
    }

    public enum Modo { APLIQUE, CORREA, ACABADO, COLOR, BOLSAS, DISENOS }

    /** Lo que cambia qué controles hay: el modo, si la prenda tiene dos pies y si hay un aplique de objeto elegido (creativa). */
    public record Estado(Modo modo, boolean pares, boolean objeto) {}

    public static final int X = 318, ANCHO = 232, Y_CATEGORIA = 24, Y_PESTANAS = 42, Y_CONTENIDO = 60, Y_LIMITE = 266;
    public static final int H_ELEGIDOS = 18, H_SEL = 28, H_FILA = 16, SEP = 4;
    /** Slots del depósito y de los insumos (pestaña Bolsas): los usa el handler. */
    public static final int Y_DEPOSITO = 60, Y_INSUMOS = 136;

    public static Map<String, R> calcular(Estado e) {
        Map<String, R> m = new LinkedHashMap<>();
        m.put("categoria", new R(X, Y_CATEGORIA, ANCHO, 14));
        m.put("pestanas", new R(X, Y_PESTANAS, ANCHO, 13));
        int y = Y_CONTENIDO;
        switch (e.modo()) {
            case BOLSAS -> {
                m.put("depositoSlot", new R(X, Y_DEPOSITO, 18, 18));
                m.put("depositoAyuda", new R(X + 24, Y_DEPOSITO, ANCHO - 24, 20));
                m.put("bolsaTexto", new R(X, 84, ANCHO, 33));
                m.put("insumosRotulo", new R(X, 124, ANCHO, 9));
                m.put("insumosGrilla", new R(X, Y_INSUMOS, 162, 36));
            }
            case DISENOS -> {
                m.put("depositoSlot", new R(X, Y_DEPOSITO, 18, 18));
                m.put("depositoAyuda", new R(X + 24, Y_DEPOSITO, ANCHO - 24, 20));
                m.put("dUltimo", new R(X, 84, ANCHO, 16));
                for (int i = 0; i < 6; i++) m.put("d" + i, new R(X, 104 + i * 18, ANCHO, 16));
                m.put("dPag", new R(X, 214, ANCHO, 16));
            }
            case APLIQUE -> {
                y = elegidos(m, y);
                y = selector(m, 0, y);
                y = selector(m, 1, y);
                for (int i = 0; i < 12; i++) m.put("ap" + i, new R(X + i * 19, y, 19, H_FILA));
                y += H_FILA + SEP;
                m.put("giroAtras", new R(X, y, 14, H_FILA));
                m.put("giro", new R(X + 15, y, 64, H_FILA));
                m.put("giroAdelante", new R(X + 80, y, 14, H_FILA));
                m.put("quitar", new R(X + 98, y, 64, H_FILA));
                // Fila del aplique (2026-10-22, botonera): a la derecha del giro y de la escala.
                m.put("filaAtras", new R(X + 166, y, 14, H_FILA));
                m.put("fila", new R(X + 181, y, 36, H_FILA));
                m.put("filaAdelante", new R(X + 218, y, 14, H_FILA));
                y += H_FILA + SEP;
                m.put("escalaAtras", new R(X, y, 14, H_FILA));
                m.put("escala", new R(X + 15, y, 64, H_FILA));
                m.put("escalaAdelante", new R(X + 80, y, 14, H_FILA));
                m.put("sacudir", new R(X + 98, y, 64, H_FILA));
                m.put("sepAtras", new R(X + 166, y, 14, H_FILA));
                m.put("sep", new R(X + 181, y, 36, H_FILA));
                m.put("sepAdelante", new R(X + 218, y, 14, H_FILA));
                y += H_FILA + SEP;
                m.put("blandura", new R(X, y, ANCHO, H_FILA));
                y += H_FILA + SEP;
                if (e.objeto()) {
                    m.put("objModo", new R(X, y, 80, H_FILA));
                    m.put("objVariante", new R(X + 82, y, 80, H_FILA));
                    y += H_FILA + SEP;
                }
                if (e.pares()) m.put("espejo", new R(X, y, 112, H_FILA));
            }
            case CORREA -> {
                y = elegidos(m, y);
                y = selector(m, 0, y);
                y = selector(m, 1, y);
                for (int i = 0; i < 8; i++) m.put("cl" + i, new R(X + i * 20, y, 19, H_FILA));
                y += H_FILA + SEP;
                m.put("cModo", new R(X, y, 112, H_FILA));
                m.put("cAncho", new R(X + 116, y, 112, H_FILA));
                y += H_FILA + SEP;
                m.put("cQuitar", new R(X, y, 112, H_FILA));
                m.put("cColor", new R(X + 116, y, 112, H_FILA));
                y += H_FILA + SEP;
                m.put("cLargoMenos", new R(X, y, 14, H_FILA));
                m.put("cLargo", new R(X + 15, y, 64, H_FILA));
                m.put("cLargoMas", new R(X + 80, y, 14, H_FILA));
                y += H_FILA + SEP;
                if (e.pares()) m.put("espejo", new R(X, y, 112, H_FILA));
            }
            case ACABADO -> {
                y = elegidos(m, y);
                y = selector(m, 0, y);
                y = selector(m, 1, y);
                y = selector(m, 2, y);
                m.put("textura", new R(X, y, ANCHO, H_FILA));
                y += H_FILA + SEP;
                m.put("acabadoTexto", new R(X, y, ANCHO, 20));
            }
            case COLOR -> {
                y = elegidos(m, y);
                y = selector(m, 0, y);
                for (int z = 0; z < 3; z++) {
                    m.put("zr" + z, new R(X, y, 60, H_FILA));
                    for (int c = 0; c < 3; c++) m.put("zc" + z + c, new R(X + 62 + c * 18, y, 16, H_FILA));
                    m.put("zp" + z, new R(X + 118, y, 64, H_FILA));
                    y += H_FILA + 2;
                }
                y += 2;
                m.put("entero", new R(X, y, 112, H_FILA));
                if (e.pares()) m.put("pie", new R(X + 116, y, 90, H_FILA));
                y += H_FILA + SEP;
                if (e.pares()) {
                    m.put("espejo", new R(X, y, 112, H_FILA));
                    y += H_FILA + SEP;
                }
                m.put("avisoColor", new R(X, y, ANCHO, 10));
            }
        }
        return m;
    }

    private static int elegidos(Map<String, R> m, int y) {
        m.put("elegidos", new R(X, y, ANCHO, H_ELEGIDOS));
        return y + H_ELEGIDOS + SEP;
    }

    private static int selector(Map<String, R> m, int n, int y) {
        m.put("sel" + n, new R(X, y, ANCHO, H_SEL));
        return y + H_SEL + SEP;
    }

    /** Todas las combinaciones que la pantalla puede mostrar. */
    public static List<Estado> combinaciones() {
        List<Estado> l = new ArrayList<>();
        for (Modo mo : Modo.values()) {
            for (boolean pares : new boolean[] {false, true}) {
                for (boolean obj : new boolean[] {false, true}) l.add(new Estado(mo, pares, obj));
            }
        }
        return l;
    }

    /** Devuelve los problemas (vacío = todo bien): superposiciones y rectángulos fuera de la columna. */
    public static List<String> verificar() {
        List<String> malos = new ArrayList<>();
        for (Estado e : combinaciones()) {
            Map<String, R> m = calcular(e);
            List<Map.Entry<String, R>> es = new ArrayList<>(m.entrySet());
            for (int i = 0; i < es.size(); i++) {
                R a = es.get(i).getValue();
                if (a.x() < X || a.x() + a.w() > X + ANCHO || a.y() < Y_CATEGORIA || a.y() + a.h() > Y_LIMITE) {
                    malos.add(e + ": " + es.get(i).getKey() + " fuera de la columna " + a);
                }
                for (int j = i + 1; j < es.size(); j++) {
                    if (a.cruza(es.get(j).getValue())) {
                        malos.add(e + ": " + es.get(i).getKey() + " pisa a " + es.get(j).getKey());
                    }
                }
            }
        }
        return malos;
    }

    public static void autoverificar() {
        List<String> malos = verificar();
        if (!malos.isEmpty()) throw new IllegalStateException("Disposición de la Estilista con problemas:\n" + String.join("\n", malos));
    }

    public static void main(String[] args) {
        List<String> malos = verificar();
        if (malos.isEmpty()) {
            System.out.println("OK: " + combinaciones().size() + " combinaciones sin solapes");
            for (Estado e : combinaciones()) {
                if (e.pares() && e.objeto()) {
                    int max = calcular(e).values().stream().mapToInt(r -> r.y() + r.h()).max().orElse(0);
                    System.out.println(e.modo() + " termina en y=" + max);
                }
            }
        } else {
            malos.forEach(System.out::println);
            System.exit(1);
        }
    }
}
