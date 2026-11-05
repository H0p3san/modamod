# Genera el logro de desbloqueo de cada receta (data/modamod/advancement/recipes/<categoria>/<receta>.json), como vanilla:
# la receta aparece en el libro cuando el jugador consigue cualquiera de sus ingredientes, o al fabricarla.
# Correr despues de agregar o cambiar recetas:  python scripts/generar_logros_recetas.py
# MotorPruebas ("logros de recetas") falla si una receta queda sin su logro.
import json, pathlib, re, shutil

RAIZ = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/data/modamod"
RECETAS, LOGROS = RAIZ / "recipe", RAIZ / "advancement/recipes"


def ingredientes(receta):
    """Cada item o tag que pide la receta, sin repetir y en orden."""
    crudos = list(receta.get("key", {}).values()) + list(receta.get("ingredients", []))
    if "base" in receta: crudos.append(receta["base"])
    vistos = []
    def agregar(x):
        if isinstance(x, list):
            for y in x: agregar(y)
        elif isinstance(x, dict):
            v = ("#" + x["tag"]) if "tag" in x else x.get("item")
            if v and v not in vistos: vistos.append(v)
    for c in crudos: agregar(c)
    return vistos


def logro(nombre, receta):
    id_receta = "modamod:" + nombre
    criterios = {"has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": id_receta}}}
    for ing in ingredientes(receta):
        clave = "has_" + re.sub(r"[^a-z0-9_]", "_", ing.lstrip("#").split(":", 1)[-1])
        if clave in criterios: clave += "_" + str(len(criterios))
        criterios[clave] = {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": ing}]}}
    out = {}
    # Si la receta depende de otro mod, su logro tambien (si no, el logro apunta a una receta que no existe).
    if "fabric:load_conditions" in receta: out["fabric:load_conditions"] = receta["fabric:load_conditions"]
    out.update({
        "parent": "minecraft:recipes/root",
        "criteria": criterios,
        "requirements": [list(criterios)],
        "rewards": {"recipes": [id_receta]},
    })
    return out


def main():
    if LOGROS.exists(): shutil.rmtree(LOGROS)   # se regeneran todos: no quedan logros de recetas borradas
    n = 0
    for f in sorted(RECETAS.glob("*.json")):
        receta = json.loads(f.read_text(encoding="utf-8"))
        destino = LOGROS / receta.get("category", "misc") / f.name
        destino.parent.mkdir(parents=True, exist_ok=True)
        destino.write_text(json.dumps(logro(f.stem, receta), indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        n += 1
    print(f"{n} logros en {LOGROS}")


if __name__ == "__main__":
    main()
