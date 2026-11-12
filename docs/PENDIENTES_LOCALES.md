# Pendientes para revisar en local (2026-11-05)

Todo esto necesita **ver el juego**: en la nube no se pudo verificar. Rama `rediseno-arquitectura`.
Orden sugerido: primero lo que puede romper (1–3), después lo visual.

## Herramientas para diagnosticar
- `./gradlew runClient` y leer `run/logs/latest.log`.
- Capturas con F2: quedan en `run/screenshots/`; leerlas directo.
- Texturas compuestas a disco: poner `ClothingTextureCache.DEBUG_DUMP = true` (no queda prendido), probar, mirar
  `run/modamod_debug/`. Sirve para separar si el problema está en la textura o en la geometría.
- Comando `/modamoddebug` (en `client/DebugApariencia.java`): `relieve`, `pieza`, `partes`, `perf`, `skin`, `slim`, `reset`…
- Pruebas: `.\gradlew.bat motorTest` (`MotorPruebas`). En Windows usar `gradlew.bat`: el `./gradlew` de bash falla.
  En la nube fallaban 27 por LWJGL; en local (2026-11-05) dan **28 OK, 0 FALLO** (contar líneas `[pruebas] FALLO`).
- Servidor dedicado: `.\gradlew.bat runServidor` (carpeta `run-servidor/`, EULA aceptado, `online-mode=false`, H0p3san
  es op). Cliente directo al servidor: `.\gradlew.bat runClient --args="--quickPlayMultiplayer localhost:25565"`.
- `/modamoddebug relieve cuerpo`: le saca el relieve solo al cuerpo (la tela conserva el suyo).

## Hecho en local (2026-11-05)
- **1. Arranque y servidor**: el cliente arranca sin error del mixin del migrador; el dedicado arranca, genera el
  mundo, acepta al cliente y las máquinas funcionan. Ninguna clase de cliente se carga del lado común.
- **4. Largo de remera**: los 7 largos dan 7 largos distintos. Las mangas salían completas en Alto, Bajo, Más alto y
  Muy bajo (el torso recortado no se registraba para `imagenBase`): 52cecdf.
- **Piel de las caderas con un cuerpo elegido**: de la cintura para abajo va la skin real (acca5d6); con "usar el
  cuerpo siempre" la pelvis sigue siendo del cuerpo.
- **Relieve**: el cuerpo atravesaba la tela en voxel (grillas corridas). Primer intento engrosando la tela (0d0e016)
  abría las prendas en las aristas; quedó el cuerpo plano en las filas que tapa cada prenda (6ef1113).
- **Nombres en inglés**: las máquinas creativas se llaman igual que las normales (fec6384).
- **2. Migrador de ids**: medias, sombrero y calzado guardados con id viejo en inventario, trinkets y cofre cargan
  iguales (lugar y color) y se guardan con el id nuevo. Íconos, nombres y manual bien. Sin cambios de código.
- **Recetas**: cada una tiene su logro de desbloqueo (`scripts/generar_logros_recetas.py`, a0db6b3); correr el script
  al agregar recetas, MotorPruebas avisa si falta alguno.
- **3. Caché de texturas**: `EstampaTextures.CACHE_CUERPO` había quedado sin tope (cada color de remera dejaba
  ~2,6 MB para siempre); con 600 colores en un maniquí el cliente subía 1586 MB, ahora 53 MB (9e8efe9). F3+T bien,
  sin cubos violetas. `/modamoddebug perf` muestra las texturas dinámicas vivas.
- **4. Largo de chaqueta, Top y hoodie**: son la misma remera (`piezasDeRemera`), así que 5483eb6 + 52cecdf ya los
  cubren. Verificado con 7 maniquíes por variante (hoodie; chaqueta abierta con solapa en pico): 7 largos distintos,
  capucha y solapas siguen el corte.
- **5. Media abierta en una arista**: era el puente de la tela del relieve levantado en el borde de la cara (374823a).
  De paso, caída lisa (6e0444d): las prendas con holgura que cambia por fila van en malla continua con esquinas rectas
  (sin rebordes que se veían como líneas de puntos), relieve empujado en horizontal y el pie de la media sin relieve.
- **6. Punta del guante**: la mano se cierra solo con el molde Cerrado (antes todo calientabrazos hasta la muñeca se
  tomaba por guante y dibujaba una tapa vacía: el píxel suelto). La tapa se pinta con la última fila del puño. Sigue fuera del manual: falta volver a agregar su página.
- **Planta del pantalón**: cuerpo y tela usaban la misma `cajaPierna` y sus plantas eran coplanares (rayas vistas de
  abajo); el margen es solo de la tela. El molde Cerrado no hace falta en el pantalón: ya cierra la planta.
- **7. Brillo y color de los apliques** ("el color después de colocarlo" era esto mismo): la orientación se aplicaba dos veces a las normales (multiplyPositionMatrix ya las
  gira en 1.21). El parche de la vista previa (normal fija `(0,1,0)` con `enVisor`) sigue haciendo falta: sin él
  los apliques se ven raros al girar la figura (probado 2026-11-08).
- **Cinto que aprieta**: nunca comprimía la piel (iba en su slot de Trinkets, fuera de las listas de ropa) y el Ajustado
  quedaba tapado (2116566). Con solo el cinto, el torso es la skin real y la segunda capa se borra solo en sus filas.
  En el maniquí la figura achica el torso con el cinto Ajustado, como con una prenda Ajustado.
- **molde_manga**: ítem huérfano sin modelo ni receta, sacado junto con `MoldeItem` (6ba4e7e).
- **Nombres de clase**: `SombreroBrujaItem` → `SombreroItem` y `BorcegosItem` → `CalzadoItem` (el ítem ya era sombrero y calzado genéricos).

## Sueltos encontrados
- La imagen sublimada se mueve sobre la prenda (al usuario no le molesta).
- La tapa de la mano del guante Cerrado no sale espejada entre los dos brazos (se copia la última fila del frente igual en
  los dos). Decidido dejarlo así; si molesta, en el brazo izquierdo se copia la fila al revés (`pintarTapaDeAbajo`).
- ~~Por los agujeros de una red o encaje se ve el cuerpo plano~~: resuelto el 2026-11-11 (5c68272, el cuerpo se aplana
  solo donde la tela de arriba es opaca).

## 8. Sin verificar todavía
- (2026-11-08) Descartados por falta de datos: "ruedo a distancia fija" de la Modeladora y "detalles de la vista previa
  del Estilista" (no hay rastro en código, textos ni commits; el usuario no recuerda qué eran).

## Pendiente de diseño (no son bugs)
- Accesorios (capa, cinto, sombrero) dibujados en el orden de la lista en vez de posiciones fijas.
- Puño de piel de la bota: falta una pieza nueva en el catálogo JSON `assets/modamod/calzado`.

## Pendiente de la sesión del 2026-11-11 (geometría y capas)

Se cortó acá a propósito: el mod quedó bien y lo que sigue es pulido. Nada de esto se vio como defecto jugando.

### Cómo retomar barato
- **Chequeo de capas**: en `run/modamod_debug/guion.txt`, vestir al jugador con `poner <lugar> <item con componentes>` y
  correr `capas <nombre>` (`ChequeoCapas`). Escribe en el log qué prenda asoma sobre cuál, cuánto y dónde. Con un nombre
  terminado en `_dbg` lista las capas y vuelca la geometría a `run/modamod_debug/capas_dump.txt`.
- **Fotos de los maniquíes del usuario**: copiar `saves/ModaMod Salon` de la instancia a `run/saves/Salon Usuario`, lanzar con
  `--quickPlaySingleplayer "Salon Usuario" --width 1920 --height 1080` y en el guion usar `cmd gamemode spectator`,
  `cmd tp @s x y z yaw pitch`, `esperar 25`, `pantalla <nombre>`. Los maniquíes miran al norte. El maniquí tiene su propio
  camino de dibujo para el cuerpo: lo que se arregla en el jugador hay que verlo también ahí.
- **Rendimiento**: `perf 1`, `tercera 1`, `esperar 200`, `perflog` en el guion (secciones de `Perf`).
- La parte mecánica (correr el cliente, recortar fotos, sacar líneas del log) se puede delegar a un agente más barato.

### Avisos del chequeo sin resolver (todos chicos, sin verificar en fotos)
- La skin (la cola) asoma 0,2 a 0,5 px sobre el pantalón o la remera a la altura de la cadera, a los costados.
- El cuerpo asoma hasta 1,26 px sobre la remera cerca del escote con busto 5 (24 celdas).
- La pollera asoma 0,38 px por la capa, atrás, a media pierna (no se ve en las fotos).
- Una rayita azul de media entre las dos piernas, arriba, en vista diagonal con botas (vista una vez, sin investigar).

### Decidido dejar así
- **Cinto con delantal**: el cinto de cintura va a la holgura de su calce (Oversize sin elegir), no a la de la ropa. Si
  el delantal sale más (busto grande, pollera muy abierta) lo tapa de frente. Se probó agrandar el cinto (queda un aro
  que atraviesa los brazos) y ceñir el delantal (se mezclan en rayas y asoma la ropa de abajo); ninguna sirvió. Para
  hacerlo bien el cinto tendría que ceñir también la ropa de abajo.
- **Remera oversize sobre la cintura del pantalón** y **pollera por fuera del pantalón**: cuelgan por fuera aunque la
  lista diga que van adentro.
- **Cuerpo del maniquí por su propio camino**: usa el modelo de vanilla achicado según el calce, y el mod CARAS inyecta
  en `dibujarCuerpoConRelieve` por nombre. Los accesorios y las reglas de aplanado sí son compartidos.

### Ideas que quedaron sin hacer
- **No dibujar lo totalmente tapado** (filas de una prenda bajo otra opaca): ahorra vértices; `MascaraOpaca` ya da el dato.
- **Armado de la geometría por pieza**: después de guardar el relieve (caa8334) es lo que más pesa en `dibujarPiezas`,
  unos 0,5 ms por jugador. Guardar las piezas armadas no rinde (medido: 0,03 ms) y choca con `CacheDeTexturas`, que
  exige volver a pedir cada textura.
- **Chequeo de capas automático sobre todos los maniquíes del Salón**, y que compare también capucha, cuello y calzado.
- **Partir los archivos gigantes** (`ModeladoBlockEntity`, `TinturasBlockEntity`, `GarmentFeatureRenderer`,
  `SublimadoraBlockEntity`).
- Descartado: las llamadas a `.render()` de `ManiquiRenderer` dibujan partes de vanilla, no fallan con Sodium.

