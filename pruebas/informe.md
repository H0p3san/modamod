# Informe de pruebas: motor (geometria)

Duracion: 1 s. Resultado: **FALLO**

| Paso | Resultado | Detalle |
|---|---|---|
| oversize torso | ok | malla ok |
| oversize torso cintura | ok | malla ok |
| oversize brazo_der | ok | malla ok |
| oversize brazo_izq | ok | malla ok |
| oversize pierna_der | ok | malla ok |
| oversize pierna_izq | ok | malla ok |
| fruncido pierna | ok | ensancha con la dilatacion |
| animal print LEOPARDO | FALLO | java.lang.UnsatisfiedLinkError: Failed to locate library: liblwjgl.so |
| animal print CEBRA | FALLO | java.lang.NoClassDefFoundError: Could not initialize class org.lwjgl.system.MemoryUtil |
| animal print TIGRE | FALLO | java.lang.NoClassDefFoundError: Could not initialize class org.lwjgl.system.MemoryUtil |
| animal print VACA | FALLO | java.lang.NoClassDefFoundError: Could not initialize class org.lwjgl.system.MemoryUtil |
| animal print JIRAFA | FALLO | java.lang.NoClassDefFoundError: Could not initialize class org.lwjgl.system.MemoryUtil |
| animal print SERPIENTE | FALLO | java.lang.NoClassDefFoundError: Could not initialize class org.lwjgl.system.MemoryUtil |
| animal print DALMATA | FALLO | java.lang.NoClassDefFoundError: Could not initialize class org.lwjgl.system.MemoryUtil |
| animal print CAMUFLAJE | FALLO | java.lang.NoClassDefFoundError: Could not initialize class org.lwjgl.system.MemoryUtil |
| animal print CAMUFLAJE_DIGITAL | FALLO | java.lang.NoClassDefFoundError: Could not initialize class org.lwjgl.system.MemoryUtil |
| cuadros TARTAN | FALLO | java.lang.NoClassDefFoundError: Could not initialize class org.lwjgl.system.MemoryUtil |
| cuadros LENADOR | FALLO | java.lang.NoClassDefFoundError: Could not initialize class org.lwjgl.system.MemoryUtil |
| cuadros PATA_DE_GALLO | FALLO | java.lang.NoClassDefFoundError: Could not initialize class org.lwjgl.system.MemoryUtil |
| cuadros ARGYLE | FALLO | java.lang.NoClassDefFoundError: Could not initialize class org.lwjgl.system.MemoryUtil |
| cuadros DIPLOMATICAS | FALLO | java.lang.NoClassDefFoundError: Could not initialize class org.lwjgl.system.MemoryUtil |
| cuadros VENTANA | FALLO | java.lang.NoClassDefFoundError: Could not initialize class org.lwjgl.system.MemoryUtil |
| cuadros ZIGZAG | FALLO | java.lang.NoClassDefFoundError: Could not initialize class org.lwjgl.system.MemoryUtil |
| fila de aplique | ok | se guarda, sin datos da una copia y se acota |
| hormonas | ok | suben, bajan y llegan a plano |
| corte | FALLO | java.lang.ExceptionInInitializerError |

## Mediciones
- oversize torso: ancho 77.27999 -> 93.27999, prof 69.73746 -> 85.73746, vertices 1248
- oversize torso cintura: ancho 76.36 -> 92.36, prof 50.326004 -> 66.326004, vertices 1152
- oversize brazo_der: ancho 63.392654 -> 79.392654, prof 60.245255 -> 75.50485, vertices 832
- oversize brazo_izq: ancho 63.392654 -> 79.392654, prof 60.245255 -> 75.50485, vertices 832
- oversize pierna_der: ancho 47.71292 -> 63.71292, prof 60.40503 -> 75.88972, vertices 768
- oversize pierna_izq: ancho 47.71292 -> 63.71292, prof 60.40503 -> 75.88972, vertices 768
- fruncido pierna: ancho 43.694725 -> 67.694725
