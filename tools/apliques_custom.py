"""Apliques custom de ejemplo: peluca de dos colitas, lentes de sol y orejitas de gato animadas.

    python tools/apliques_custom.py [carpeta de salida]     (por defecto run/config/modamod/apliques)

Escribe por cada uno <nombre>.geo.json, <nombre>.textura.png, <nombre>.png (ícono) y, las orejas,
<nombre>.animation.json, listos para
`/modamod apliques recargar` o el botón "Subir aplique" de la Estilista creativa.

Medidas en px de skin. El origen es el punto donde se hace click: x a la derecha, y arriba, -z hacia afuera de la tela.
Los tres van sobre el FRENTE de la cabeza (la cabeza queda en x -4..4, z 0..8): la peluca con el click en el centro de
la cara (cabeza en y -4..4), las orejas igual, y los lentes entre los ojos.
"""
import json
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw

TEX_W, TEX_H = 48, 16          # tres zonas de 16 px, una al lado de la otra
CARAS = ("north", "south", "east", "west", "up", "down")


def cubo(x0, y0, z0, x1, y1, z1, zona):
    """Un cubo entre dos esquinas, con todas sus caras en la zona de color `zona` (0..2)."""
    sx, sy, sz = x1 - x0, y1 - y0, z1 - z0
    lados = {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz), "down": (sx, sz)}
    uv = {c: {"uv": [zona * 16 + 1, 1], "uv_size": [round(min(14, max(0.5, w)), 3), round(min(14, max(0.5, h)), 3)]}
          for c, (w, h) in lados.items()}
    return {"origin": [x0, y0, z0], "size": [round(sx, 3), round(sy, 3), round(sz, 3)], "uv": uv}


def espejo(c):
    """El mismo cubo del otro lado (x -> -x)."""
    x0, y0, z0 = c["origin"]
    sx = c["size"][0]
    d = json.loads(json.dumps(c))
    d["origin"] = [round(-(x0 + sx), 3), y0, z0]
    return d


def hueso(nombre, padre, pivote, cubos):
    h = {"name": nombre, "pivot": list(pivote)}
    if padre:
        h["parent"] = padre
    if cubos:
        h["cubes"] = cubos
    return h


def peluca():
    g = 0.6   # grosor del pelo por fuera de la cabeza
    casco = [
        cubo(-4 - g, 4, -g, 4 + g, 4 + g, 8 + g, 0),          # arriba
        cubo(-4 - g, -5, 8, 4 + g, 4, 8 + g, 0),              # nuca
        cubo(-4 - g, 2.6, -g, 4 + g, 4, 0, 0),                # nacimiento del flequillo
    ]
    lado = [cubo(4, -1, 1.5, 4 + g, 4, 8, 0),                 # costado, deja la oreja a la vista
            cubo(4, -3.5, 0.2, 4 + g, 4, 1.5, 0)]             # mechón de adelante de la oreja
    casco += lado + [espejo(c) for c in lado]
    # Flequillo en picos, más largo a los costados y al medio.
    for x0, x1, largo in [(-4.6, -3.2, 2.6), (-3.2, -1.9, 1.4), (-1.9, -0.7, 2.0), (-0.7, 0.7, 2.6),
                          (0.7, 1.9, 2.0), (1.9, 3.2, 1.4), (3.2, 4.6, 2.6)]:
        casco.append(cubo(x0, 2.6 - largo, -g, x1, 2.6, 0, 2))
    huesos = [hueso("root", None, (0, 0, 0), None), hueso("casco", "root", (0, 0, 0), casco)]
    # Las dos colitas: gomita (zona 2) y tres tramos anidados "cola*", que se mecen con la blandura.
    for nombre, s in (("izq", 1), ("der", -1)):
        def c(x0, y0, z0, x1, y1, z1, zona):
            k = cubo(x0, y0, z0, x1, y1, z1, zona)
            return k if s == 1 else espejo(k)
        huesos.append(hueso("gomita_" + nombre, "root", (0, 0, 0), [c(4.6, 2.2, 6.2, 5.6, 4.2, 8.4, 1)]))
        padre, y = "root", 3.4
        for i, (ancho, largo) in enumerate([(2.2, 6.0), (1.8, 6.0), (1.2, 5.0)], 1):
            x0 = 5.4 + (2.2 - ancho) / 2
            n = "cola_%s_%d" % (nombre, i)
            huesos.append(hueso(n, padre, (s * (x0 + ancho / 2), y, 7.3),
                                [c(x0, y - largo, 7.3 - ancho / 2, x0 + ancho, y, 7.3 + ancho / 2, 0 if i < 3 else 2)]))
            padre, y = n, y - largo
    return huesos


def lentes():
    f0, f1 = -0.9, -0.35       # el marco, de adelante hacia la cara
    marco = [cubo(-4.4, 1.0, f0, 4.4, 1.35, f1, 0),           # barra de arriba
             cubo(-0.6, 0.3, f0, 0.6, 1.0, f1, 0)]            # puente
    medio = [cubo(0.3, -1.3, f0, 3.9, -1.0, f1, 0),           # abajo
             cubo(3.6, -1.0, f0, 3.9, 1.0, f1, 0),            # afuera
             cubo(0.3, -1.0, f0, 0.6, 1.0, f1, 0),            # adentro
             cubo(4.05, 0.45, f0, 4.4, 1.0, 4.6, 0)]          # patilla
    marco += medio + [espejo(c) for c in medio]
    vidrio = [cubo(0.6, -1.0, -0.8, 3.6, 1.0, -0.5, 1)]
    brillo = [cubo(2.6, 0.2, -0.86, 3.2, 0.8, -0.8, 2)]
    return [hueso("root", None, (0, 0, 0), None), hueso("marco", "root", (0, 0, 0), marco),
            hueso("vidrios", "root", (0, 0, 0), vidrio + [espejo(c) for c in vidrio]),
            hueso("brillos", "root", (0, 0, 0), brillo + [espejo(c) for c in brillo])]


def orejas():
    """Orejitas de gato sobre la cabeza (click en el centro de la cara, como la peluca). Cada oreja es un hueso con el pivote
    en su base: los mueve la animación de `animacion_orejas`, no el nombre del hueso."""
    escalones = [(1.2, 4.2, 4.4, 5.4), (1.6, 3.8, 5.4, 6.3), (2.1, 3.3, 6.3, 7.1), (2.5, 2.9, 7.1, 7.7)]
    oreja = [cubo(x0, y0, 2.5, x1, y1, 3.5, 0) for x0, x1, y0, y1 in escalones]
    oreja += [cubo(1.9, 4.6, 2.4, 3.5, 5.4, 2.5, 1), cubo(2.3, 5.4, 2.4, 3.1, 6.2, 2.5, 1)]   # el rosa de adentro
    return [hueso("root", None, (0, 0, 0), None),
            hueso("oreja_izq", "root", (2.7, 4.4, 3.0), oreja),
            hueso("oreja_der", "root", (-2.7, 4.4, 3.0), [espejo(c) for c in oreja])]


def animacion_orejas():
    """Un tic de cada oreja, a destiempo, en un bucle de 3 segundos. Formato de Blockbench (giros en grados)."""
    return {"format_version": "1.8.0", "animations": {"animation.orejas_gato.idle": {
        "loop": True, "animation_length": 3.0, "bones": {
            "oreja_izq": {"rotation": {"0.0": [0, 0, 0], "2.3": [0, 0, 0], "2.45": [0, 0, -20], "2.6": [0, 0, 4],
                                       "2.75": [0, 0, -12], "3.0": [0, 0, 0]}},
            "oreja_der": {"rotation": {"0.0": [0, 0, 0], "0.9": [0, 0, 0], "1.05": [0, 0, 20], "1.2": [0, 0, -4],
                                       "1.4": [0, 0, 0], "3.0": [0, 0, 0]}}}}}}


def textura(mechones):
    """Gris con las 3 zonas en tercios; el color lo pone el retazo. `mechones`: rayas a lo largo, como pelo."""
    azar = random.Random(7)
    img = Image.new("RGBA", (TEX_W, TEX_H))
    for x in range(TEX_W):
        raya = azar.randint(-22, 10) if mechones else 0
        for y in range(TEX_H):
            v = 240 + raya - (y * 2 if mechones else (x % 16 + y) // 2)
            img.putpixel((x, y), (max(120, min(255, v)),) * 3 + (255,))
    return img


def icono(huesos):
    """Papel kraft con el modelo visto de frente en tinta (como los íconos de los moldes del mod)."""
    T = 64
    img = Image.new("RGBA", (T, T), (233, 204, 165, 255))
    d = ImageDraw.Draw(img)
    d.line([(T // 2, 0), (T // 2, T)], fill=(214, 186, 148, 255))
    d.line([(0, T // 2), (T, T // 2)], fill=(214, 186, 148, 255))
    cubos = [c for h in huesos for c in h.get("cubes", [])]
    x0 = min(c["origin"][0] for c in cubos); x1 = max(c["origin"][0] + c["size"][0] for c in cubos)
    y0 = min(c["origin"][1] for c in cubos); y1 = max(c["origin"][1] + c["size"][1] for c in cubos)
    k = 48 / max(x1 - x0, y1 - y0)
    ox, oy = (T - (x1 - x0) * k) / 2, (T - (y1 - y0) * k) / 2
    for c in sorted(cubos, key=lambda c: -c["origin"][2]):      # de atrás hacia adelante
        a = (ox + (c["origin"][0] - x0) * k, oy + (y1 - c["origin"][1] - c["size"][1]) * k)
        b = (ox + (c["origin"][0] + c["size"][0] - x0) * k, oy + (y1 - c["origin"][1]) * k)
        d.rectangle([a, b], fill=(196, 160, 116, 255), outline=(59, 36, 16, 255))
    d.rectangle([0, 0, T - 1, T - 1], outline=(59, 36, 16, 255))
    return img


def escribir(carpeta, nombre, huesos, mechones, animacion=None):
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry." + nombre, "texture_width": TEX_W, "texture_height": TEX_H,
                        "visible_bounds_width": 2, "visible_bounds_height": 2, "visible_bounds_offset": [0, 0, 0]},
        "bones": huesos}]}
    (carpeta / (nombre + ".geo.json")).write_text(json.dumps(geo, indent=1), encoding="utf-8")
    textura(mechones).save(carpeta / (nombre + ".textura.png"))
    icono(huesos).save(carpeta / (nombre + ".png"))
    if animacion:
        (carpeta / (nombre + ".animation.json")).write_text(json.dumps(animacion, indent=1), encoding="utf-8")


if __name__ == "__main__":
    salida = Path(sys.argv[1] if len(sys.argv) > 1 else "run/config/modamod/apliques")
    salida.mkdir(parents=True, exist_ok=True)
    escribir(salida, "peluca_miku", peluca(), True)
    escribir(salida, "lentes_sol", lentes(), False)
    escribir(salida, "orejas_gato", orejas(), False, animacion_orejas())
    print("escritos en", salida)
