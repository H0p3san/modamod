# ModaMod

*Human-designed, AI-crafted parametric clothing mod for Minecraft: fabricate, shape, dye, sublimate and style your clothes.*

Mod de ropa para **Minecraft 1.21.1 (Fabric)**: ropa que se viste pegada al cuerpo, en capas, en slots de Trinkets, y
máquinas para cortarla, teñirla, estamparle fotos y decorarla con apliques. Versión **0.1.0-alpha**: puede tener bugs y
los mundos de una alpha pueden no cargar bien en versiones futuras.

## Requisitos

| Mod | Versión |
|---|---|
| Fabric Loader | 0.16.10 o más nuevo |
| Fabric API | 0.116.0+1.21.1 o más nuevo |
| Trinkets | 3.10.0 o más nuevo |
| GeckoLib | 4.9.3 o más nuevo |
| Cardinal Components API | el que trae Trinkets |

Opcionales: **Camerapture** (la Sublimadora estampa sus fotos), **Emotecraft** (el Maniquí hace los emotes),
**Patchouli** (el libro **Manual de Moda**), **3D Skin Layers** y **CARAS** (la capucha y los sombreros van sobre sus
cabezas).

## Qué trae

- **Modeladora**: corte, largo, mangas, cuello, calce, ruedos, tramas y materiales.
- **Estación de Tintes**: colores por zona, capas y patrones (rayas, motivos, cuadros, animal print, camuflaje...).
- **Sublimadora**: estampa fotos de Camerapture en la ropa.
- **Estilista**: apliques 3D (moños, botones, bolsillos, tachas, parches...), correas y acabados.
- **Telar**, **Guardarropas**, **Maniquí** y cintas transportadoras para armar una línea de producción.

Todo está explicado en el [manual](docs/manual/MANUAL.md).

## Compilar

```
./gradlew.bat build -x test
```

El jar queda en `build/libs/modamod-0.1.0-alpha.jar`.

Para el cliente de desarrollo (`./gradlew.bat runClient`) con Camerapture y 3D Skin Layers, hay que sacar de sus jars
(`META-INF/jars`) `webp4j-1.3.0.jar`, `TRansition-1.0.21-1.21.1-fabric-SNAPSHOT.jar` y
`TRender-1.0.15-1.21.1-fabric-SNAPSHOT.jar` y ponerlos en `libs/` (no se versionan: son de terceros). Sin ellos el mod
compila igual.

## Licencia

El código es [MIT](LICENSE). Los assets (texturas, modelos, animaciones, íconos, idiomas) tienen todos los derechos
reservados: ver [LICENSE-ASSETS](LICENSE-ASSETS).

La forma del collar sale de Leashable Collars (jlortiz, MIT): ver [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
