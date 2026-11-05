package com.modamod.pruebas;

import com.modamod.prenda.Parte;
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

    /** La misma ventana que RelieveCuerpo (smoothstep a 0 en los dos bordes). */
    private static float ventana(float t, float borde) {
        float a = Math.max(0f, Math.min(1f, t / borde)), b = Math.max(0f, Math.min(1f, (1f - t) / borde));
        return a * a * (3f - 2f * a) * b * b * (3f - 2f * b);
    }

    /** Cuántos anchos distintos (x mínima) tiene la malla a la altura del borde de fila {@code borde}. */
    static int contornosEnBorde(ModelPart p, int borde) throws Exception {
        List<float[]> vs = new java.util.ArrayList<>();
        for (Object c : (List<?>) campo(p, "cuboids")) for (Object q : (Object[]) campo(c, "sides")) for (Object v : (Object[]) campo(q, "vertices")) {
            Object pos = campo(v, "pos");
            vs.add(new float[]{((Number) pos.getClass().getMethod("x").invoke(pos)).floatValue(), ((Number) pos.getClass().getMethod("y").invoke(pos)).floatValue()});
        }
        // Los anillos: las alturas con muchos vértices (las tapas infladas no cuentan), ordenadas de arriba abajo.
        java.util.TreeMap<Float, Integer> porY = new java.util.TreeMap<>();
        for (float[] v : vs) porY.merge(Math.round(v[1] * 1000F) / 1000F, 1, Integer::sum);
        List<Float> anillos = porY.entrySet().stream().filter(e -> e.getValue() >= 16).map(java.util.Map.Entry::getKey).toList();
        if (anillos.size() < 13) throw new AssertionError("se esperaban 13 anillos y hay " + anillos.size() + ": " + anillos);
        float yBorde = anillos.get(borde);
        java.util.TreeSet<Float> xs = new java.util.TreeSet<>();
        for (float[] v : vs) if (Math.abs(v[1] - yBorde) < 1e-3F) xs.add(Math.round(v[0] * 1000F) / 1000F);
        if (xs.isEmpty()) throw new AssertionError("ningún vértice en el borde " + borde);
        // Las x del costado de x mínima: una por contorno (los puntos de las otras caras quedan a ~8 unidades, escala 8).
        float primera = xs.first();
        return (int) xs.stream().filter(x -> x <= primera + 4F).count();
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
        // Caída lisa (2026-11-05, "molestan los puntos"): con la holgura creciendo fila a fila, cada borde de fila tiene un solo
        // contorno (pared inclinada, sin reborde); en una junta dura quedan dos (el escalón a propósito, que cierra el anillo).
        try {
            float[] dil = new float[12];
            for (int f = 0; f < 12; f++) dil[f] = 0.25F + 0.1F * f;
            boolean[] duras = new boolean[13];
            duras[0] = duras[12] = true;
            Informe.verificar(contornosEnBorde(CuerpoGeometria.telaSuavePorFilas(Parte.PIERNA_DER, false, dil, 12, 0, dil[11], -1F, false, duras), 6) == 1,
                    "junta lisa con más de un contorno (queda reborde)");
            duras[6] = true;
            Informe.verificar(contornosEnBorde(CuerpoGeometria.telaSuavePorFilas(Parte.PIERNA_DER, false, dil, 12, 0, dil[11], -1F, false, duras), 6) == 2,
                    "junta dura sin escalón");
            inf.ok("caida lisa", "un contorno por borde liso, dos en la junta dura");
        } catch (Throwable t) { inf.fallo("caida lisa", t.toString()); }

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

        // Corte (rediseño, tanda 1): todos los ejes de la prenda en un solo componente. Ida y vuelta por NBT con un
        // enumerado, un nivel y un mapa; un eje desconocido (de una versión vieja) se salta sin romper el resto.
        try {
            com.modamod.prenda.Ejes.init();
            var ops = net.minecraft.nbt.NbtOps.INSTANCE;
            var c = com.modamod.prenda.Corte.VACIO
                    .con(com.modamod.prenda.Ejes.CALCE, com.modamod.item.Calce.values()[1])
                    .con(com.modamod.prenda.Ejes.FALDON, 3);
            var nbt = com.modamod.prenda.Corte.CODEC.encodeStart(ops, c).getOrThrow();
            Informe.verificar(com.modamod.prenda.Corte.CODEC.parse(ops, nbt).getOrThrow().equals(c), "el corte no vuelve igual: " + nbt);
            var conViejo = (net.minecraft.nbt.NbtCompound) nbt.copy();
            conViejo.putString("eje_que_ya_no_existe", "x");
            Informe.verificar(com.modamod.prenda.Corte.CODEC.parse(ops, conViejo).getOrThrow().equals(c), "un eje desconocido rompe el corte");
            var sinFaldon = c.con(com.modamod.prenda.Ejes.FALDON, null);
            Informe.verificar(sinFaldon.valor(com.modamod.prenda.Ejes.FALDON) == null && sinFaldon.valor(com.modamod.prenda.Ejes.CALCE) != null,
                    "sacar un eje no deja el resto");
            inf.ok("corte", "ida y vuelta por NBT, ejes desconocidos se saltan");
        } catch (Throwable t) { inf.fallo("corte", t.toString()); }

        // Molde (rediseño, tanda 2c): lo que fija la Modeladora es un Corte parcial con lado e ícono. Vuelve igual por NBT,
        // un nivel fuera de rango es "ninguno", y cada eje que un molde puede poner sabe su fábrica y en qué prendas vale
        // (el otro lado hereda las dos cosas).
        try {
            var ops = net.minecraft.nbt.NbtOps.INSTANCE;
            var ejes = com.modamod.prenda.Ejes.class;
            var m = com.modamod.modelado.Molde.ruedo(com.modamod.item.ZonaRuedo.BOTA_IZQ, com.modamod.item.Ruedo.RECTO, true)
                    .conLado(com.modamod.region.Lado.DERECHA).conIcono(net.minecraft.util.Identifier.of("modamod", "molde_prueba"));
            var nbt = com.modamod.modelado.Molde.CODEC.encodeStart(ops, m).getOrThrow();
            Informe.verificar(com.modamod.modelado.Molde.CODEC.parse(ops, nbt).getOrThrow().equals(m), "el molde no vuelve igual: " + nbt);
            Informe.verificar(com.modamod.modelado.Molde.nivel(com.modamod.prenda.Ejes.CAPA_EMBOZO, -1).corte()
                    .valor(com.modamod.prenda.Ejes.CAPA_EMBOZO) == com.modamod.prenda.EjeDeCorte.SIN_NIVEL, "el nivel -1 no es ninguno");
            java.util.Set<Object> otros = new java.util.HashSet<>();
            java.util.List<com.modamod.prenda.EjeDeCorte<?>> todos = new java.util.ArrayList<>();
            for (var f : ejes.getFields()) {
                if (!(f.get(null) instanceof com.modamod.prenda.EjeDeCorte<?> e)) continue;
                todos.add(e);
                if (e.otroLado() != null) otros.add(e.otroLado());
            }
            var sueltos = java.util.Set.of("ruedos", "orientacion", "frente_ahora", "capucha_arriba");
            for (var e : todos) {
                if (sueltos.contains(e.nombre())) continue;
                Informe.verificar(e.deFabrica() != null, "el eje " + e + " no sabe su valor de fábrica");
                Informe.verificar(e.valeEn(com.modamod.prenda.CategoriaDePrenda.REMERA) || java.util.Arrays.stream(com.modamod.prenda.CategoriaDePrenda.values()).anyMatch(e::valeEn),
                        "el eje " + e + " no vale en ninguna prenda");
                if (e.otroLado() != null) Informe.verificar(java.util.Objects.equals(e.otroLado().deFabrica(), e.deFabrica()),
                        "el otro lado de " + e + " no tiene la misma fábrica");
            }
            inf.ok("moldes", todos.size() + " ejes con fábrica y prendas; ida y vuelta por NBT");
        } catch (Throwable t) { inf.fallo("moldes", t.toString()); }

        // Relieve bajo la tela (2026-11-05, "reemplazar la skin por la prenda donde haya conflicto"): en las filas que tapa una
        // prenda el cuerpo va plano (no atraviesa la tela) y fuera de ellas conserva su relieve; las otras partes, intactas.
        try {
            com.modamod.render.relieve.MapaRelieve cuerpo = com.modamod.render.relieve.MapaRelieve.generar("prueba-cuerpo", false,
                    (parte, cara, fu, fv, ancho) -> 1f);
            com.modamod.render.relieve.MapaRelieve bajoTela = com.modamod.render.relieve.MapaRelieve.sinFilas(cuerpo, Parte.TORSO, 0, 7);
            for (int cara = 0; cara < 4; cara++) {
                Informe.verificar(bajoTela.altura(Parte.TORSO, cara, 0.5f, 3f / 12) == 0f, "cara " + cara + ": el cuerpo no quedó plano bajo la tela");
                Informe.verificar(bajoTela.altura(Parte.TORSO, cara, 0.5f, 10f / 12) == 1f, "cara " + cara + ": el cuerpo perdió el relieve fuera de la tela");
                Informe.verificar(bajoTela.altura(Parte.BRAZO_DER, cara, 0.5f, 3f / 12) == 1f, "cara " + cara + ": se aplanó otra parte");
            }
            Informe.verificar(cuerpo.altura(Parte.TORSO, 1, 0.5f, 3f / 12) == 1f, "sinFilas modificó el mapa original");
            inf.ok("relieve bajo la tela", "cuerpo plano en las filas tapadas, intacto en el resto");
        } catch (Throwable t) { inf.fallo("relieve bajo la tela", t.toString()); }

        // Aristas de la tela (2026-11-05, punto 5 "media abierta en una arista"): la tela no se levanta en el borde de una cara
        // más que el cuerpo, o cada cara se empuja por su normal y se despega de la vecina. Cuerpo como RelieveCuerpo: se
        // apaga en los bordes y tiene un bulto cerca de uno (el gemelo).
        try {
            com.modamod.render.relieve.MapaRelieve cuerpo = com.modamod.render.relieve.MapaRelieve.generar("prueba-gemelo", false,
                    (parte, cara, fu, fv, ancho) -> 1.5f * ventana(fu, 0.25f) * ventana(fv, 0.06f));
            for (com.modamod.item.Calce calce : com.modamod.item.Calce.values()) {
                com.modamod.render.relieve.MapaRelieve tela = com.modamod.render.relieve.RelieveTela.de(cuerpo, calce,
                        com.modamod.item.TexturaTela.LISA, null);
                for (Parte parte : new Parte[]{Parte.PIERNA_DER, Parte.TORSO}) {
                    for (float[] p : new float[][]{{0f, 0.5f}, {1f, 0.5f}, {0.5f, 0f}, {0.5f, 1f}}) {
                        float t = tela.altura(parte, 0, p[0], p[1]), c = cuerpo.altura(parte, 0, p[0], p[1]);
                        Informe.verificar(t <= c + 0.02f, calce + " " + parte + " en la arista " + p[0] + "," + p[1] + ": tela " + t + " > cuerpo " + c);
                    }
                }
            }
            inf.ok("relieve en las aristas", "la tela no se levanta en el borde de la cara (todos los calces)");
        } catch (Throwable t) { inf.fallo("relieve en las aristas", t.toString()); }

        // Logros de recetas (2026-11-05, "aparecen luego de craftearlas"): cada receta tiene el suyo, que la desbloquea en el
        // libro al conseguir un ingrediente. Si falla: python scripts/generar_logros_recetas.py
        try {
            Path recetas = Path.of(MotorPruebas.class.getClassLoader().getResource("data/modamod/recipe").toURI());
            Path logros = recetas.resolveSibling("advancement").resolve("recipes");
            java.util.Set<String> premiadas = new java.util.HashSet<>();
            try (var archivos = Files.walk(logros)) {
                for (Path p : (Iterable<Path>) archivos.filter(x -> x.toString().endsWith(".json"))::iterator) {
                    var json = com.google.gson.JsonParser.parseString(Files.readString(p)).getAsJsonObject();
                    for (var r : json.getAsJsonObject("rewards").getAsJsonArray("recipes")) premiadas.add(r.getAsString());
                }
            }
            List<String> sinLogro = new java.util.ArrayList<>();
            int total = 0;
            try (var archivos = Files.list(recetas)) {
                for (Path p : (Iterable<Path>) archivos.filter(x -> x.toString().endsWith(".json"))::iterator) {
                    total++;
                    String id = "modamod:" + p.getFileName().toString().replace(".json", "");
                    if (!premiadas.contains(id)) sinLogro.add(id);
                }
            }
            Informe.verificar(sinLogro.isEmpty(), "recetas sin logro (correr scripts/generar_logros_recetas.py): " + sinLogro);
            inf.ok("logros de recetas", total + " recetas, todas con su logro");
        } catch (Throwable t) { inf.fallo("logros de recetas", t.toString()); }

        Path dir = Path.of("").toAbsolutePath().resolve("pruebas");
        Files.createDirectories(dir);
        inf.escribir(dir);
        System.out.println("[pruebas] informe en " + dir.resolve("informe.md"));
        System.exit(inf.huboFallo() ? 1 : 0);
    }
}
