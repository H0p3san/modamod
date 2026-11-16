# Changelog

## 0.2.0-alpha (2026-11-15)

Rediseño grande por dentro y una tanda larga de arreglos de cómo se ve la ropa. Sigue siendo una alpha.

### Antes de actualizar
- **Hacé una copia del mundo.** Se sacaron ítems que ya no se usaban: medias 3/4, medias de red y traje de maid (la
  ropa de armadura vanilla), la Mesa de estilado (queda solo la Estilista), el molde de manga cíclico, los moldes de
  botamanga y el molde de capucha común (quedan Malla y Cajas). Si los tenías, desaparecen.
- Los ids internos viejos (medias, sombrero, calzado) se migran solos.

### Ropa
- **Una sola lista de 16 lugares de ropa**: la ropa se dibuja en el orden de la lista, de adentro hacia afuera, y la
  fila de la armadura es la cosmética. El Maniquí y el Guardarropas usan los mismos lugares.
- **Traje de Papá Noel**: apliques pompón, hebilla, barba y bigote; ribete de piel en torso, puños, botamangas, medias,
  pollera y sombrero; solapa de piel para la chaqueta; textura de felpa.
- **Molde de ruedo Cerrado**: tapa la punta del guante, la media o la manga.
- **Cuellos caídos**: de hombros (parejo de los dos lados) o de un solo hombro (izquierdo o derecho, en diagonal). La remera y la manga empiezan por debajo del hombro.
- Máscaras y barbijo en una sola categoría, con apliques.
- La capa del mod tapa los élitros.
- El faldón sigue al Top; el largo de la remera corta donde dice y las mangas respetan su largo.
- Las prendas con holgura que cambia por fila caen lisas, en malla continua.

### Busto, cola y capas
- La tela sigue la forma del busto (redondo y cuadrado) en todos los calces: sin cortes, sin z-fighting con remeras
  ajustadas o pegadas y sin que la remera de abajo asome por los costados.
- Con frente abierto, red, encaje o tela translúcida se ve el busto de abajo.
- El delantal sigue la forma del busto en vez de inflarse en bloque.
- La cola: el pantalón o la pollera la cubren entera; sin cuerpo entero usa tu skin.
- El cinto que aprieta comprime de verdad, solo en sus filas.
- Pantalón, medias y botas: el pantalón metido entra en la caña y las medias con calzado van sin punta.
- Capucha y sombreros: las esquinas de la cabeza ya no los atraviesan.

### Máquinas
- **Estación de Tintes**:
  - teñí un aplique ya puesto sin sacarlo: las flechas eligen la prenda, cada aplique o **Todos los apliques**, y la
    vista previa es la prenda puesta;
  - en línea de producción también tiñe los apliques;
  - regiones que siguen el largo de la prenda (borde inferior, pecho, puños, superior/inferior de medias).
- **Estilista** (queda sola, sin Mesa de estilado):
  - apliques sobre el casco;
  - mover, girar y ajustar el aplique por eje (Z/X/Y), con teclado y paso fino;
  - en la creativa, el panel va a la izquierda (ya no choca con JEI).
- **Apliques custom**: subí tu propio modelo de Blockbench (con ícono, textura y animación opcionales) a un servidor
  andando, con el botón **Subir aplique** o por comando. Ver `docs/APLIQUES_CUSTOM.md`.
- **Sublimadora**: fotos también en el cinto (placa y correa) y en el costado del calzado.
- Las máquinas muestran los accesorios (sombrero, calzado, banda, maletín) sobre la mesa.
- **Maniquí**: manos para escudos, herramientas y armas.
- Cada receta se desbloquea con su logro, como en vanilla.

### Apliques y accesorios
- La mariposa pliega las alas hacia atrás, sin meterse en la tela.
- El maletín se agarra de la manija, colgando debajo del puño (en la mano y como accesorio).
- Iconos propios para los moldes de barba, bigote, hebilla y pompón.

### Rendimiento y compatibilidad
- **Shaders (Iris)**: las mallas propias (busto, pollera, remeras holgadas) ya no se ven con la luz al revés con
  LITE shaders.
- Los Tops se ven con Sodium; los LED y la pantallita de la Estación de Tintes se ven prendidos con Sodium + Iris.
- Las texturas de la ropa entran en cachés con tope: ya no se acumula memoria en una sesión larga.
- El relieve calcula la geometría de cada cara una sola vez.

### Se sabe que
- La ropa no se dibuja en el brazo de primera persona.
- Las copias de un aplique en fila no siguen la curva del busto.
- El outfit de otro jugador puede tardar un momento en actualizarse (sincronización de Trinkets).

## 0.1.0-alpha (2026-10-22)

Primera versión pública. Es una alpha: puede tener bugs, y los mundos pueden no cargar bien en versiones futuras.

### Ropa
- Remera, pantalón, pollera, medias, calientabrazos, capa, delantal, chaqueta (hoodie), sombrero, borcegos, bandas
  (cinto y choker) y traje de maid, que se visten pegados al cuerpo y en capas, en slots propios de Trinkets.
- Pantalla de **Ropa** (botón en el inventario o tecla K) con la armadura real, la cosmética y los slots de ropa.
- Cuerpo configurable: tipo de cuerpo, tono de piel, ropa interior, busto y cola con física.
- **Estrógenos** y **Testosterona**: suben o bajan el busto un talle por dosis, sin vencimiento. Se le compran al
  clérigo aprendiz.

### Máquinas
- **Modeladora**: largo, mangas, cuello, calce, ruedos, volados, tramas (redes y arneses) y materiales (denim, cuero,
  pana), con diseños guardables como ítems.
- **Estación de Tintes**: colores por zona y capas con patrones: rayas, corazones, estrellas, lunares, vichy, tartán,
  leñador, pata de gallo, argyle, rayas diplomáticas, ventana, zigzag, 7 animal print, camuflaje y camuflaje digital.
- **Sublimadora**: estampa fotos de Camerapture en el frente y la espalda.
- **Estilista**: apliques 3D (moño, mariposa, flor, canguro, corbata, cordones, botón, botonera, tres bolsillos, tacha,
  tira de tachas y parches bordados), repetibles en fila, con tela blanda; correas y acabados.
- **Telar**, **Guardarropas** (outfits y maletín), **Maniquí** (con emotes si está Emotecraft), cintas
  transportadoras, empalmes, tubos y válvulas para armar una línea de producción.

### Se sabe que
- La ropa no se dibuja en el brazo de primera persona.
- Las telas con transparencia a medias no están probadas con shaders (Iris).
- Las copias de un aplique en fila no siguen la curva del busto.
- El outfit de otro jugador puede tardar un momento en actualizarse (sincronización de Trinkets).
