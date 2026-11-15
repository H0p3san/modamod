# Apliques custom

Cómo sumar apliques propios (modelo, ícono, textura y animación) a un servidor que ya está andando, sin recompilar el mod.

## 1. Los archivos

Un aplique son hasta cuatro archivos con el mismo nombre. El nombre solo lleva `a-z`, `0-9` y `_`, hasta 32 letras.

| Archivo | Qué es | Obligatorio | Peso máximo |
|---|---|---|---|
| `<nombre>.geo.json` | El modelo, exportado de Blockbench como GeckoLib | Sí | 512 KB |
| `<nombre>.png` | El ícono del molde | Sí | 64 KB |
| `<nombre>.textura.png` | La textura del modelo | No | 256 KB |
| `<nombre>.animation.json` | La animación, exportada de Blockbench | No | 128 KB |

Van en la carpeta `config/modamod/apliques/` (del servidor si los cargás por comando, de tu juego si los subís con el botón).

### El modelo

- El origen (0, 0, 0) es el punto de la prenda donde se hace click.
- El frente del aplique mira a **-Z** y se apoya en **z = 0**; lo que va hacia adentro de la prenda tiene z positiva.
- **+Y** es arriba. Las medidas son píxeles de skin (la cabeza mide 8).
- Cada cara toma su color de la zona donde cae su UV: el tercio izquierdo de la textura es la zona 1, el del medio la 2 y el derecho la 3.

### La textura

- En **gris**: el color final sale de multiplicar cada píxel por el color de la zona. Blanco da el color pleno; los grises dan el sombreado.
- Tres zonas en **tercios del ancho**, una al lado de la otra. El tamaño es libre.
- Sin textura, el modelo usa el atlas de apliques del mod.

Los colores de cada zona salen del retazo, así que lo que teñís en la Estación de Tintes le llega igual que a los apliques del mod.

### La animación

- Se reproduce en bucle la **primera** animación del archivo.
- Mueve los huesos que se llamen igual que en el modelo: giro, posición y escala.
- Todas las copias del aplique van sincronizadas.
- No funcionan las expresiones que dependen del tiempo (`query.anim_time`); sí los cuadros clave comunes.

Además, sin archivo de animación, algunos nombres de hueso se mueven solos con la blandura del aplique: `cola…` y `cinta…` cuelgan y se mecen, `ala…izq` / `ala…der` se pliegan, `petalo…` se inclina y `hojas` cuelga.

## 2. Cargarlos

Hay dos caminos. Los dos son solo para operadores.

### Con el botón (desde tu juego)

1. Abrí una **Estilista creativa**.
2. En el panel lateral, andá a la página **Moldes**.
3. Tocá **Subir aplique**. Se abre el explorador de archivos.
4. Elegí el modelo (`.json`). Si el ícono, la textura y la animación están al lado con el mismo nombre, se suben solos; si no, elegilos también en la misma ventana.
5. Te llega por chat que se subió, y el molde al inventario.

El aplique se llama como el archivo. Para otro nombre, escribilo antes en el casillero de nombre.

En la ventana, la textura es la imagen que termina en `.textura.png` y la animación el archivo que termina en `.animation.json`.

### Con comandos (en el servidor)

1. Copiá los archivos a `config/modamod/apliques/` del servidor.
2. `/modamod apliques recargar` — los lee y los manda a todos los conectados.
3. `/modamod apliques dar <nombre>` — te da el molde.

Quien entra después los recibe al entrar, y al reiniciar el servidor se cargan solos.

## 3. Ponerlos en una prenda

El molde es un molde de aplique personalizado como cualquier otro:

1. En la Estilista, poné la prenda, el molde y un retazo.
2. Hacé click en la vista previa donde lo querés.

Para apliques de cabeza (pelucas, orejas, lentes) sirve el **Casco para apliques**: rodea toda la cabeza, sin teñir no se ve y no borra el relieve de la cabeza.

### Ajustar el aplique elegido

En la columna de controles, al lado de la blandura, están el botón **Eje** y el control **Pos**. El eje vale para los dos controles, el de giro y el de posición:

| Eje | Giro (`<` `>` de Giro) | Posición (`<` `>` de Pos) |
|---|---|---|
| Z | El giro de siempre, sobre la tela | Sacarlo o meterlo |
| X | Inclinarlo hacia adelante o atrás | Correrlo de costado |
| Y | Girarlo de costado | Correrlo hacia arriba o abajo |

El giro va de a 15° (con Shift, de a 1°) y la posición de a 1 px (con Shift, de a un cuarto).

También con el teclado y el mouse:

| Qué | Cómo |
|---|---|
| Mudarlo a otro punto | Shift + click en la vista previa |
| Correrlo sobre la tela | Flechas del teclado |
| Inclinarlo | Ctrl + flechas |
| Sacarlo o meterlo | Re Pág / Av Pág |
| Pasos grandes | Shift con las tres anteriores |

En la Estilista creativa, el panel con todos los deslizadores y el casillero de objeto está a la izquierda de la ventana.

## 4. Ejemplos

`python tools/apliques_custom.py [carpeta]` escribe tres apliques de ejemplo (por defecto en `run/config/modamod/apliques`):

- `peluca_miku` — peluca de dos colitas que se mecen. Click en el centro de la cara.
- `lentes_sol` — lentes de sol. Click entre los ojos.
- `orejas_gato` — orejitas con animación propia. Click en el centro de la cara.

Sirven de plantilla: abrí el `.geo.json` y el `.animation.json` de las orejas para ver el formato.

## 5. Si algo no anda

- **No aparece el molde ni el mensaje**: no sos operador, o no estás frente a una Estilista creativa.
- **"No pude leer…"**: falta el modelo o el ícono en la carpeta que dice el mensaje.
- **El aplique no se ve**: el modelo, la animación o el ícono están mal formados. El motivo queda en el log del juego, buscando `Aplique custom`.
- **Sacaste un archivo y se sigue viendo**: los clientes conectados lo conservan hasta reiniciar el juego.
