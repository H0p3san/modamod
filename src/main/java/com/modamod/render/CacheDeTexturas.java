package com.modamod.render;

import com.modamod.Modamod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Caché de texturas dinámicas con tope (2026-11-05, "que no le mate la instancia al cliente"). Antes cada caché era un
 * HashMap que solo se vaciaba con F3+T: cada combinación nueva de colores, estampas o cortes (y cada cambio en la vista
 * previa de las máquinas) dejaba una textura viva en RAM y en la placa de video. Con telas de 512×512 eso son ~2 MB por
 * entrada y en una tarde de edición llegaba a gigas.
 *
 * <p>Ahora, al pasar el tope, se destruyen las menos usadas, pero nunca una que se haya dibujado hace menos de
 * {@link #PROTEGIDA_MS}: lo que está en pantalla no se toca aunque haya más que el tope (una plaza llena de jugadores
 * vestidos). Quien la vuelve a necesitar la pide de nuevo con su clave y se rearma.
 *
 * <p>Regla para usarla: el id que devuelve se usa en el mismo frame y no se guarda en otro lado; hay que volver a
 * pedirlo con {@link #get} cada vez (eso es lo que marca que sigue en uso). Cada id es único (contador), así que dos
 * claves nunca comparten textura y destruir una no rompe la otra.
 */
public final class CacheDeTexturas {

    /** Lo que se dibujó hace menos que esto no se expulsa. */
    static final long PROTEGIDA_MS = 20_000;

    private static final List<CacheDeTexturas> TODAS = new ArrayList<>();

    private final String nombre;
    private final int tope;
    /** En orden de acceso: la primera es la que hace más que no se usa. */
    private final LinkedHashMap<String, Entrada> mapa = new LinkedHashMap<>(64, 0.75f, true);
    @Nullable private Consumer<Identifier> alExpulsar;
    private long contador;

    private static final class Entrada {
        final Identifier id;
        long uso;
        Entrada(Identifier id, long uso) { this.id = id; this.uso = uso; }
    }

    /**
     * @param nombre prefijo del id ({@code modamod:dynamic/<nombre>_<n>})
     * @param tope   cuántas texturas sin usar se toleran; elegirlo por el tamaño de la imagen
     */
    public CacheDeTexturas(String nombre, int tope) {
        this.nombre = nombre;
        this.tope = tope;
        TODAS.add(this);
    }

    /** Aviso para quien guarda algo propio por id (las translúcidas de la tela): se llama al destruir una. */
    public CacheDeTexturas alExpulsar(Consumer<Identifier> c) {
        this.alExpulsar = c;
        return this;
    }

    /** La textura de esa clave, o null si no está (o se expulsó). Marca que se está usando. */
    @Nullable
    public Identifier get(String clave) {
        Entrada e = mapa.get(clave);
        if (e == null) return null;
        e.uso = System.currentTimeMillis();
        return e.id;
    }

    /** Registra la imagen (la textura pasa a ser su dueña) bajo un id nuevo y la guarda con esa clave. */
    public Identifier registrar(String clave, NativeImage img) {
        Identifier id = Identifier.of(Modamod.MOD_ID, "dynamic/" + nombre + "_" + Long.toHexString(contador++));
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(img));
        Entrada vieja = mapa.put(clave, new Entrada(id, System.currentTimeMillis()));
        if (vieja != null) destruir(vieja.id);
        podar();
        return id;
    }

    public int tamano() {
        return mapa.size();
    }

    /** Destruye todas (recarga de recursos). */
    public void vaciar() {
        for (Entrada e : mapa.values()) destruir(e.id);
        mapa.clear();
    }

    public static void vaciarTodas() {
        for (CacheDeTexturas c : TODAS) c.vaciar();
    }

    /** Texturas vivas en todas las cachés, para el HUD de rendimiento. */
    public static int vivas() {
        int n = 0;
        for (CacheDeTexturas c : TODAS) n += c.mapa.size();
        return n;
    }

    private void podar() {
        if (mapa.size() <= tope) return;
        long ahora = System.currentTimeMillis();
        int objetivo = tope * 3 / 4;
        Iterator<Map.Entry<String, Entrada>> it = mapa.entrySet().iterator();
        while (it.hasNext() && mapa.size() > objetivo) {
            Entrada e = it.next().getValue();
            // En orden de acceso: si esta se usó hace poco, las que siguen también.
            if (ahora - e.uso < PROTEGIDA_MS) break;
            it.remove();
            destruir(e.id);
        }
    }

    private void destruir(Identifier id) {
        MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
        if (alExpulsar != null) alExpulsar.accept(id);
    }
}
