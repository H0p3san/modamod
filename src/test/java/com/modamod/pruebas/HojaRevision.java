package com.modamod.pruebas;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Arma la hoja de revision (./gradlew.bat runRevision): una grilla con TODAS las capturas de las corridas de juego, cada
 * una rotulada con su nombre, y un pendientes.md con "que mirar" por captura y el resultado de cada informe. La idea es
 * que la inspeccion ocular sea abrir una sola imagen. Args: carpeta de salida y las carpetas run (run-smoke ...).
 */
public final class HojaRevision {
    private static final int ANCHO = 560, ALTO = 315, MARGEN = 12, ROTULO = 26, COLUMNAS = 3, POR_HOJA = 12;

    /** Que mirar segun el nombre de la captura (primer fragmento que coincida). */
    private static final Map<String, String> QUE_MIRAR = new TreeMap<>(Map.of(
            "modeladora", "Pantalla completa, sin textos pisados; el molde elegido aparece fijado y la prenda del visor cambia.",
            "telar", "Prendas tildadas con borde, boton Tejer visible, barra de progreso y salida coherentes.",
            "tintes-", "El visor muestra el animal print sobre toda la prenda, con color 1 en las manchas/rayas y color 2 en el borde.",
            "prenda-", "La prenda equipada se ve y respeta el cuerpo; sin agujeros ni telas flotando.",
            "-espalda", "Costura de atras: el dibujo no se corta ni se duplica en el medio.",
            "base-sin-ropa", "Referencia: jugador sin prendas equipadas.",
            "fallo", "Captura del momento del fallo: mirar que pantalla estaba abierta."));

    private HojaRevision() {}

    private static String queMirar(String nombre) {
        for (var e : QUE_MIRAR.entrySet()) if (nombre.contains(e.getKey())) return e.getValue();
        return "Revisar que se vea bien.";
    }

    public static void main(String[] args) throws Exception {
        Path salida = Path.of(args[0]);
        Files.createDirectories(salida);
        List<File> capturas = new ArrayList<>();
        StringBuilder md = new StringBuilder("# Revision ocular\n\nAbri las hojas `hoja-N.png` y marcame cuales estan mal. Cada captura trae abajo su nombre.\n\n");
        for (int i = 1; i < args.length; i++) {
            Path run = Path.of(args[i]);
            Path informe = run.resolve("pruebas/informe.md");
            md.append("## ").append(run.getFileName()).append("\n");
            if (Files.exists(informe)) {
                String resultado = Files.readString(informe).lines().filter(l -> l.startsWith("Duracion")).findFirst().orElse("(sin resultado)");
                md.append(resultado).append("\n\n");
            } else md.append("(no hay informe: la corrida no se hizo)\n\n");
            File[] shots = run.resolve("screenshots").toFile().listFiles((d, n) -> n.endsWith(".png"));
            if (shots == null) continue;
            java.util.Arrays.sort(shots, java.util.Comparator.comparingLong(File::lastModified));
            for (File f : shots) {
                capturas.add(f);
                md.append("- [ ] `").append(run.getFileName()).append('/').append(f.getName()).append("`: ").append(queMirar(f.getName())).append("\n");
            }
            md.append("\n");
        }
        int hojas = Math.max(1, (capturas.size() + POR_HOJA - 1) / POR_HOJA);
        for (int h = 0; h < hojas; h++) {
            int n = Math.min(POR_HOJA, capturas.size() - h * POR_HOJA);
            int filas = (Math.max(n, 1) + COLUMNAS - 1) / COLUMNAS;
            BufferedImage hoja = new BufferedImage(COLUMNAS * (ANCHO + MARGEN) + MARGEN, filas * (ALTO + ROTULO + MARGEN) + MARGEN, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = hoja.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(30, 30, 34));
            g.fillRect(0, 0, hoja.getWidth(), hoja.getHeight());
            g.setFont(new Font("SansSerif", Font.BOLD, 15));
            for (int k = 0; k < n; k++) {
                File f = capturas.get(h * POR_HOJA + k);
                BufferedImage img = ImageIO.read(f);
                int x = MARGEN + (k % COLUMNAS) * (ANCHO + MARGEN), y = MARGEN + (k / COLUMNAS) * (ALTO + ROTULO + MARGEN);
                String nom = f.getName();
                if (nom.startsWith("prenda-") || nom.startsWith("base-")) {
                    // El jugador ocupa poco del cuadro: se recorta y se agranda para poder juzgar la prenda.
                    int cx = (int) (img.getWidth() * 0.40), cw = (int) (img.getWidth() * 0.20), cy = (int) (img.getHeight() * 0.44), ch = (int) (img.getHeight() * 0.42);
                    BufferedImage rec = img.getSubimage(cx, cy, cw, ch);
                    double esc = Math.min((double) ANCHO / cw, (double) ALTO / ch);
                    int dw = (int) (cw * esc), dh = (int) (ch * esc);
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    g.drawImage(rec, x + (ANCHO - dw) / 2, y + (ALTO - dh) / 2, dw, dh, null);
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                } else {
                    g.drawImage(img, x, y, ANCHO, ALTO, null);
                }
                g.setColor(Color.WHITE);
                g.drawString(f.getParentFile().getParentFile().getName() + " / " + f.getName().replace(".png", ""), x + 2, y + ALTO + 18);
            }
            g.dispose();
            ImageIO.write(hoja, "png", salida.resolve("hoja-" + (h + 1) + ".png").toFile());
        }
        Files.writeString(salida.resolve("pendientes.md"), md.toString());
        System.out.println("[revision] " + capturas.size() + " capturas en " + hojas + " hoja(s): " + salida.toAbsolutePath());
    }
}
