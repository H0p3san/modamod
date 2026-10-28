package com.modamod.pruebas;

import com.modamod.garment.Parte;
import com.modamod.render.CuerpoGeometria;
import net.minecraft.client.model.ModelPart;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Pruebas de motor sin abrir el juego (./gradlew.bat motorTest): la geometria de las telas por filas, que es lo que
 * dibuja la ropa Oversize y el ruedo Fruncido. Lee los vertices de los ModelPart por reflexion, como el viejo Smoke.java.
 */
public final class MotorPruebas {

    private static Object campo(Object o, String nombre) throws Exception {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(nombre); f.setAccessible(true); return f.get(o); } catch (NoSuchFieldException e) { /* sigue */ }
        }
        throw new NoSuchFieldException(nombre);
    }

    /** {minX,maxX,minY,maxY,minZ,maxZ,cantidadDeVertices} crudo del ModelPart. */
    static float[] caja(ModelPart p) throws Exception {
        float[] r = {1e9f, -1e9f, 1e9f, -1e9f, 1e9f, -1e9f, 0};
        String[] ejes = {"x", "y", "z"};
        for (Object c : (List<?>) campo(p, "cuboids")) for (Object q : (Object[]) campo(c, "sides")) for (Object v : (Object[]) campo(q, "vertices")) {
            Object pos = campo(v, "pos");
            for (int i = 0; i < 3; i++) {
                float val = ((Number) pos.getClass().getMethod(ejes[i]).invoke(pos)).floatValue();
                if (!Float.isFinite(val)) throw new AssertionError("vertice no finito en " + ejes[i]);
                r[2 * i] = Math.min(r[2 * i], val); r[2 * i + 1] = Math.max(r[2 * i + 1], val);
            }
            r[6]++;
        }
        if (r[6] == 0) throw new AssertionError("sin vertices");
        return r;
    }

    private static float ancho(float[] c) { return c[1] - c[0]; }
    private static float prof(float[] c) { return c[5] - c[4]; }

    public static void main(String[] args) throws Exception {
        Informe inf = new Informe("motor (geometria)");
        // Oversize: cada parte con malla propia (Blockbench) responde y no se achica al inflar.
        Object[][] partes = {{Parte.TORSO, false}, {Parte.TORSO, true}, {Parte.BRAZO_DER, false}, {Parte.BRAZO_IZQ, false},
                {Parte.PIERNA_DER, false}, {Parte.PIERNA_IZQ, false}};
        for (Object[] pc : partes) {
            Parte parte = (Parte) pc[0];
            boolean cintura = (boolean) pc[1];
            String nombre = "oversize " + parte.clave() + (cintura ? " cintura" : "");
            try {
                ModelPart chico = CuerpoGeometria.telaOversize(parte, cintura, false, 0, 12, 0, 0F, 0F);
                ModelPart grande = CuerpoGeometria.telaOversize(parte, cintura, false, 0, 12, 0, 1F, 0F);
                Informe.verificar(chico != null && grande != null, "telaOversize devolvio null (sin malla para la parte)");
                float[] a = caja(chico), b = caja(grande);
                Informe.verificar(ancho(b) >= ancho(a) - 1e-3f && prof(b) >= prof(a) - 1e-3f, "inflar achico la malla");
                inf.nota(nombre + ": ancho " + ancho(a) + " -> " + ancho(b) + ", prof " + prof(a) + " -> " + prof(b) + ", vertices " + (int) a[6]);
                inf.ok(nombre, "malla ok");
            } catch (Throwable t) { inf.fallo(nombre, t.toString()); }
        }
        // Ruedo Fruncido: con dilatacion crece el ancho (el fruncido abulta) y la geometria es finita.
        try {
            float[] cero = new float[12], dil = new float[12];
            java.util.Arrays.fill(dil, 1.5F);
            ModelPart a = CuerpoGeometria.telaFruncida(Parte.PIERNA_DER, false, cero, 0, 12, true, false, 0, 0F, -1F, 0);
            ModelPart b = CuerpoGeometria.telaFruncida(Parte.PIERNA_DER, false, dil, 0, 12, true, false, 0, 0F, -1F, 0);
            Informe.verificar(a != null && b != null, "telaFruncida devolvio null");
            float[] ca = caja(a), cb = caja(b);
            Informe.verificar(ancho(cb) > ancho(ca), "la dilatacion no ensancho el fruncido: " + ancho(ca) + " vs " + ancho(cb));
            inf.nota("fruncido pierna: ancho " + ancho(ca) + " -> " + ancho(cb));
            inf.ok("fruncido pierna", "ensancha con la dilatacion");
        } catch (Throwable t) { inf.fallo("fruncido pierna", t.toString()); }

        // Animal print (2026-10-19): cobertura razonable, cambia con la semilla, usa los dos colores y cierra en la costura de atras.
        for (com.modamod.render.Motivo m : com.modamod.render.Motivo.values()) {
            if (!m.esAnimal()) continue;
            try {
                var rep = com.modamod.render.Repeticion.GRILLA;
                var a = com.modamod.render.PatronGenerador.mascaraMotivo("pantalon", m, 1.0, rep, 1, 0.5f);
                var b2 = com.modamod.render.PatronGenerador.mascaraMotivo("pantalon", m, 1.0, rep, 2, 0.5f);
                Informe.verificar(a != null && b2 != null, "mascara null");
                // Torso del pantalon: x 128..320, caras laterales y 192..288 (ver PatronGenerador.TORSO).
                long total = 0, cubiertos = 0, contornos = 0, distintos = 0;
                for (int y = 192; y < 288; y++) for (int x = 128; x < 320; x++) {
                    int p = a.getColor(x, y);
                    total++;
                    if ((p >>> 24) != 0) { cubiertos++; if ((p & 0x80) != 0) contornos++; }
                    if ((p >>> 24) != (b2.getColor(x, y) >>> 24)) distintos++;
                }
                double cob = cubiertos / (double) total;
                inf.nota(m + ": cobertura " + String.format("%.1f%%", cob * 100) + ", contorno " + String.format("%.1f%%", contornos * 100.0 / total)
                        + ", cambia con la semilla en " + String.format("%.1f%%", distintos * 100.0 / total));
                Informe.verificar(cob > 0.05 && cob < 0.70, "cobertura fuera de rango: " + cob);
                Informe.verificar(distintos > total / 50, "la semilla no cambia el dibujo");
                // Costura: la columna 319 es vecina de la 128. Su desacuerdo no puede pasar del doble del de columnas vecinas del medio.
                // El digital va en cuadraditos: cambia de golpe en cada borde de cuadradito y la costura es uno de esos
                // bordes, así que se compara con el borde más desparejo del medio, no con dos columnas cualquiera.
                boolean digital = m == com.modamod.render.Motivo.CAMUFLAJE_DIGITAL;
                long costura = 0, medio = 0;
                for (int y = 192; y < 288; y++) {
                    if (((a.getColor(319, y) >>> 24) != 0) != ((a.getColor(128, y) >>> 24) != 0)) costura++;
                }
                for (int x = digital ? 129 : 200; x <= (digital ? 317 : 200); x++) {
                    long d = 0;
                    for (int y = 192; y < 288; y++) if (((a.getColor(x, y) >>> 24) != 0) != ((a.getColor(x + 1, y) >>> 24) != 0)) d++;
                    medio = Math.max(medio, d);
                }
                inf.nota(m + ": desacuerdo en la costura " + costura + "/96 filas, entre columnas vecinas " + medio + "/96");
                Informe.verificar(costura <= 2 * medio + 6, "el dibujo no cierra en la costura de atras");
                inf.ok("animal print " + m, "cobertura, semilla y costura");
            } catch (Throwable t) { inf.fallo("animal print " + m, t.toString()); }
        }

        // Cuadros (2026-10-22, tartán, leñador y pata de gallo): cobertura razonable, contorno solo donde va la línea fina
        // (la grilla y la costura son las del vichy, sin cambios).
        for (var m : new com.modamod.render.Motivo[]{com.modamod.render.Motivo.TARTAN, com.modamod.render.Motivo.LENADOR,
                com.modamod.render.Motivo.PATA_DE_GALLO, com.modamod.render.Motivo.ARGYLE, com.modamod.render.Motivo.DIPLOMATICAS,
                com.modamod.render.Motivo.VENTANA, com.modamod.render.Motivo.ZIGZAG}) {
            try {
                var a = com.modamod.render.PatronGenerador.mascaraMotivo("pantalon", m, 1.0, com.modamod.render.Repeticion.GRILLA, 1, 0.5f);
                Informe.verificar(a != null, "mascara null");
                long total = 0, cubiertos = 0, contornos = 0;
                for (int y = 192; y < 288; y++) for (int x = 128; x < 320; x++) {
                    int p = a.getColor(x, y);
                    total++;
                    if ((p >>> 24) != 0) { cubiertos++; if ((p & 0x80) != 0) contornos++; }
                }
                double cob = cubiertos / (double) total, con = contornos / (double) total;
                inf.nota(m + ": cobertura " + String.format("%.1f%%", cob * 100) + ", contorno " + String.format("%.1f%%", con * 100));
                // Las de líneas finas (diplomáticas, ventana) cubren poco a propósito.
                boolean finas = m == com.modamod.render.Motivo.DIPLOMATICAS || m == com.modamod.render.Motivo.VENTANA;
                Informe.verificar(cob > (finas ? 0.03 : 0.25) && cob < (finas ? 0.30 : 0.85), "cobertura fuera de rango: " + cob);
                boolean conLinea = m == com.modamod.render.Motivo.TARTAN || m == com.modamod.render.Motivo.LENADOR
                        || m == com.modamod.render.Motivo.ARGYLE;
                Informe.verificar(conLinea ? con > 0.02 && con < 0.25 : con == 0, "contorno inesperado: " + con);
                inf.ok("cuadros " + m, "cobertura y linea fina");
            } catch (Throwable t) { inf.fallo("cuadros " + m, t.toString()); }
        }

        // Fila del aplique (2026-10-22, botonera): se guarda y se lee, sin datos sale con una sola copia y se acota. Solo el
        // codec de la fila: el de Aplique arrastra ItemStack (necesita los registros del juego).
        try {
            var ops = com.mojang.serialization.JsonOps.INSTANCE;
            var f = new com.modamod.aplique.Fila(4, 1.75f);
            var json = com.modamod.aplique.Fila.CODEC.encodeStart(ops, f).getOrThrow();
            Informe.verificar(com.modamod.aplique.Fila.CODEC.parse(ops, json).getOrThrow().equals(f), "la fila no vuelve igual");
            var vacia = com.modamod.aplique.Fila.CODEC.parse(ops, new com.google.gson.JsonObject()).getOrThrow();
            Informe.verificar(vacia.equals(com.modamod.aplique.Fila.DEFECTO), "sin datos no sale con una sola copia: " + vacia);
            var acotada = new com.modamod.aplique.Fila(99, 99f);
            Informe.verificar(acotada.cantidad() == com.modamod.aplique.Fila.CANTIDAD_MAX
                    && acotada.separacion() == com.modamod.aplique.Fila.SEPARACION_MAX, "la fila no se acota: " + acotada);
            inf.ok("fila de aplique", "se guarda, sin datos da una copia y se acota");
        } catch (Throwable t) { inf.fallo("fila de aplique", t.toString()); }

        // Hormonas (2026-10-22, "estrogeno aumenta testo baja indefinidamente"): suben y bajan de a un talle; la
        // testosterona del talle 1 o del busto del cuerpo (0) deja plano, y los estrógenos desde plano van al 1.
        try {
            int max = com.modamod.body.PerfilCuerpo.BUSTO_MAXIMO, plano = com.modamod.body.PerfilCuerpo.BUSTO_PLANO;
            var d = (java.util.function.BiFunction<Integer, Boolean, Integer>) com.modamod.item.EstrogenosItem::despuesDeDosis;
            Informe.verificar(d.apply(0, true) == 1 && d.apply(3, true) == 4 && d.apply(max, true) == max && d.apply(plano, true) == 1,
                    "los estrógenos no suben bien");
            Informe.verificar(d.apply(4, false) == 3 && d.apply(1, false) == plano && d.apply(0, false) == plano && d.apply(plano, false) == plano,
                    "la testosterona no baja bien");
            inf.ok("hormonas", "suben, bajan y llegan a plano");
        } catch (Throwable t) { inf.fallo("hormonas", t.toString()); }

        Path dir = Path.of("").toAbsolutePath().resolve("pruebas");
        Files.createDirectories(dir);
        inf.escribir(dir);
        System.out.println("[pruebas] informe en " + dir.resolve("informe.md"));
        System.exit(inf.huboFallo() ? 1 : 0);
    }
}
