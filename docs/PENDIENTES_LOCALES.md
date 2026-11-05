# Pendientes para revisar en local (2026-11-05)

Todo esto necesita **ver el juego**: en la nube no se pudo verificar. Rama `rediseno-arquitectura`.
Orden sugerido: primero lo que puede romper (1–3), después lo visual.

## Herramientas para diagnosticar
- `./gradlew runClient` y leer `run/logs/latest.log`.
- Capturas con F2: quedan en `run/screenshots/`; leerlas directo.
- Texturas compuestas a disco: poner `ClothingTextureCache.DEBUG_DUMP = true` (no queda prendido), probar, mirar
  `run/modamod_debug/`. Sirve para separar si el problema está en la textura o en la geometría.
- Comando `/modamoddebug` (en `client/DebugApariencia.java`): `relieve`, `pieza`, `partes`, `perf`, `skin`, `slim`, `reset`…
- Pruebas: `./gradlew` + `MotorPruebas` (en este entorno fallan 27 de oversize/animal print por LWJGL; comparar
  contra ese mismo resultado, no contra cero).

## 1. Arranque y servidor
- **Mixin del migrador** (commit 4309797, `mixin/RegistroIdsViejosMixin`): ¿el cliente arranca sin error de mixin?
- **Servidor dedicado**: arrancar uno con el mod. La verificación automática de "nada del lado común carga clases del
  cliente" no llegó a correr. Sospechosos: `sublimadora/BannerHD`, `sublimadora/EstampaTextures`,
  `sublimadora/CuelloRecorte` (usan `NativeImage` y están en paquetes comunes).

## 2. Migrador de ids (4309797)
`socks_solid`→`medias`, `sombrero_bruja`→`sombrero`, `borcegos`→`calzado`. **Copiar el mundo antes**: si el migrador
no anda, los ítems con id viejo se borran al guardar.
- Mundo con esos ítems en inventario, puestos (trinkets) y en un cofre: ¿siguen con forma y colores?
- Íconos (no violeta y negro), nombres por variante, recetas, páginas del manual.

## 3. Caché de texturas con tope (c651f46, `render/CacheDeTexturas`)
- Editar en el Estilista un buen rato: ¿la memoria (F3) se estabiliza?
- ¿Aparece el cubo violeta y negro en alguna prenda? Eso sería alguien guardando un id de textura en vez de pedirlo
  cada frame (ver la regla en el javadoc de `CacheDeTexturas`).
- F3+T sigue recargando bien.

## 4. Largo de remera (5483eb6)
Los 7 niveles del molde de rango daban de a pares el mismo largo. Causa: `CuerpoGeometria.telaPorFilas` dibuja
**siempre las 12 filas**; el largo sale de la transparencia de la textura, y hay 3 texturas (5, 9, 12 filas) para
7 largos. Arreglo: `PiezasDelMod.piezasDeRemera` recorta la textura del torso al largo, como las mangas.
- ¿Los 7 niveles dan 7 largos distintos? ¿"Alto" (7 filas) deja el ombligo al aire?
- Probar también chaqueta, Top y hoodie: van por otro camino (`CuelloYCapucha.telaDelTorso`) y pueden tener el mismo
  problema.

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
