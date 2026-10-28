package com.modamod.pruebas;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Resultados de una corrida: informe.md siempre, y pruebas-ok.txt o pruebas-fallo.txt segun el resultado. */
public final class Informe {
    private record Linea(String nombre, boolean ok, String detalle) {}

    private final String suite;
    private final List<Linea> lineas = new ArrayList<>();
    private final List<String> capturas = new ArrayList<>();
    private final List<String> notas = new ArrayList<>();
    private final long inicio = System.currentTimeMillis();

    public Informe(String suite) { this.suite = suite; }

    public void ok(String nombre, String detalle) { lineas.add(new Linea(nombre, true, detalle)); System.out.println("[pruebas] OK    " + nombre + " " + detalle); }
    public void fallo(String nombre, String detalle) { lineas.add(new Linea(nombre, false, detalle)); System.out.println("[pruebas] FALLO " + nombre + " " + detalle); }
    public void captura(String archivo) { capturas.add(archivo); }
    /** Dato medido (no es un chequeo): aparece en el informe para revisar a mano. */
    public void nota(String texto) { notas.add(texto); }
    public boolean huboFallo() { return lineas.stream().anyMatch(l -> !l.ok); }

    /** Falla con un mensaje claro si la condicion no se cumple. */
    public static void verificar(boolean condicion, String mensaje) {
        if (!condicion) throw new AssertionError(mensaje);
    }

    public void escribir(Path dir) {
        try {
            Files.createDirectories(dir);
            Files.deleteIfExists(dir.resolve("pruebas-ok.txt"));
            Files.deleteIfExists(dir.resolve("pruebas-fallo.txt"));
            StringBuilder sb = new StringBuilder("# Informe de pruebas: " + suite + "\n\n");
            sb.append("Duracion: ").append((System.currentTimeMillis() - inicio) / 1000).append(" s. Resultado: ")
              .append(huboFallo() ? "**FALLO**" : "**OK**").append("\n\n| Paso | Resultado | Detalle |\n|---|---|---|\n");
            for (Linea l : lineas) sb.append("| ").append(l.nombre).append(" | ").append(l.ok ? "ok" : "FALLO").append(" | ")
                    .append(l.detalle.replace("|", "/").replace("\n", " ")).append(" |\n");
            if (!notas.isEmpty()) { sb.append("\n## Mediciones\n"); notas.forEach(n -> sb.append("- ").append(n).append("\n")); }
            if (!capturas.isEmpty()) { sb.append("\n## Capturas (revisar a ojo)\n"); capturas.forEach(c -> sb.append("- ").append(c).append("\n")); }
            Files.writeString(dir.resolve("informe.md"), sb.toString());
            if (huboFallo()) {
                StringBuilder f = new StringBuilder();
                lineas.stream().filter(l -> !l.ok).forEach(l -> f.append(l.nombre).append(": ").append(l.detalle).append("\n"));
                Files.writeString(dir.resolve("pruebas-fallo.txt"), f.toString());
            } else {
                Files.writeString(dir.resolve("pruebas-ok.txt"), lineas.size() + " pasos ok\n");
            }
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
}
