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
- **molde_manga**: ítem huérfano sin modelo ni receta, sacado junto con `MoldeItem` (6ba4e7e).

## Sueltos encontrados
- La imagen sublimada se mueve sobre la prenda (al usuario no le molesta).
- Por los agujeros de una red o encaje se ve el cuerpo plano (efecto de 6ef1113; no se notó en la prueba).

## 5. Media abierta en una arista
Se abre en la pierna **derecha por la arista de atrás/afuera** y en la **izquierda por la arista interna**: donde la
textura de la pierna da la vuelta (uv 0,16: oeste/norte/este/sur en columnas de 4 px). Ya se probó un margen de 0,5 px
en los anillos (`EPS_U`) y relleno de bordes de textura: no cambió.
- Primero `/modamoddebug relieve apagado`: si la línea desaparece, es el relieve empujando cada cara por su normal
  (`render/relieve`); si sigue, es UV o textura (mirar con DEBUG_DUMP).

## 6. Punta del guante / molde "Cerrado"
El molde `molde_ruedo_cerrado` existe pero la punta del guante sigue abierta. Código: `tapaPunta` en
`GarmentFeatureRenderer` (≈ línea 1440) y la tapa de mano en `CuerpoGeometria.telaSuavePorFilas`. Ver cuál de los dos
caminos se usa de verdad para un guante largo y si la tapa se emite. Sacado del manual hasta que ande.

## 7. Apliques
- El brillo varía según la rotación ("no parejos en ningún lado"). Normales en `render/ApliqueRenderer`: en la vista
  previa normal plana `(0,1,0)` (`enVisor = pos.determinant() < 0`), en el mundo la real.
- El color después de colocarlo.

## 8. Sin verificar todavía
- El cinto reemplaza la piel (c717b19: declara que gobierna el torso si su calce no es Oversize).
- Modeladora: ruedo a "distancia fija".
- Detalles de la vista previa del Estilista.

## Pendiente de diseño (no son bugs)
- Accesorios (capa, cinto, sombrero) dibujados en el orden de la lista en vez de posiciones fijas.
- Puño de piel de la bota: falta una pieza nueva en el catálogo JSON `assets/modamod/calzado`.
- Renombrar clases Java `SombreroBrujaItem` y `BorcegosItem` (cosmético).
