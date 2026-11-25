# Reglas de trabajo (ModaMod)

- Hablar en español rioplatense.
- Hablar antes de implementar: contar el plan y esperar el OK.
- No dibujar assets (texturas, íconos): dejar un provisorio copiado y el prompt en `docs/prompts_iconos.md`.
- Con cada cambio, actualizar este archivo y `docs/manual/MANUAL.md` (y el `CHANGELOG.md`).
- Compilar en la nube: `java -Xmx64m -Xms64m -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain build -x test` (`./gradlew` no anda en el contenedor). No compilar ni empujar sin que se pida.
- Después de agregar o cambiar recetas: `python scripts/generar_logros_recetas.py` (y no commitear logros ajenos que cambie).

## Notas de arquitectura
- `Variante.Cuello` viaja por red y se guarda por ordinal: los valores nuevos van AL FINAL.
- Los cuellos cuadrado, corazón, camisa, redondo, caídos y tiradores no tienen texturas propias: se recortan en runtime de la
  textura del redondo (`sublimadora/CuelloRecorte`). Un cuello recortado nuevo toca `CuelloRecorte`, `Variante.claveBase()`,
  `IconoPrenda.plantillaDe`, `ModItems` (molde), `Modamod` (pestaña creativa), lang es_ar/en_us, modelo, receta y logro.
- **Tiradores** (2026-11-25): `CuelloRecorte.recortarTiradores`; peto con la curva de `DelantalForma.PETO`, tiras de 2
  texeles, escote de 3 filas adelante y 6 atrás, sisa de 5 filas en los costados. Ícono provisorio (copia del cuadrado).
