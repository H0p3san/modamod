package com.modamod.prenda;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * La forma de una prenda: todos sus ejes de corte en un solo componente ({@link Tipo#COMPONENTE}). Lo ausente vale lo de
 * fábrica, que decide quien lee (cada prenda conoce los suyos). Un molde de la Modeladora es el mismo {@code Corte} con
 * solo algunos ejes puestos ({@code modelado.Molde}): aplicarlo es escribirlos encima con {@link #escribir}.
 */
public record Corte(Map<EjeDeCorte<?>, Object> valores) {
    private static final Logger LOG = LogUtils.getLogger();
    public static final Corte VACIO = new Corte(Map.of());

    public Corte {
        valores = Map.copyOf(valores);
    }

    public static final Codec<Corte> CODEC = Codec.unboundedMap(Codec.STRING, Codec.PASSTHROUGH)
            .xmap(Corte::leerMapa, Corte::escribirMapa);

    /** El componente vive aparte para que el codec se pueda usar (y probar) sin los registros del juego. */
    public static final class Tipo {
        public static final ComponentType<Corte> COMPONENTE = Registry.register(Registries.DATA_COMPONENT_TYPE,
                Identifier.of("modamod", "corte"),
                ComponentType.<Corte>builder().codec(CODEC).packetCodec(PacketCodecs.codec(CODEC)).build());

        private Tipo() {}
    }

    /** Fuerza el registro del componente. */
    public static void init() {
        Tipo.COMPONENTE.getClass();
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public <T> T valor(EjeDeCorte<T> eje) {
        return (T) valores.get(eje);
    }

    public <T> Corte con(EjeDeCorte<T> eje, @Nullable T valor) {
        Map<EjeDeCorte<?>, Object> m = new HashMap<>(valores);
        if (valor == null) m.remove(eje);
        else m.put(eje, valor);
        return new Corte(m);
    }

    public boolean vacio() {
        return valores.isEmpty();
    }

    // --- Sobre la prenda ---

    public static Corte de(ItemStack stack) {
        return stack.getOrDefault(Tipo.COMPONENTE, VACIO);
    }

    /** El valor guardado, o null si no está (vale el de fábrica). */
    @Nullable
    public static <T> T get(ItemStack stack, EjeDeCorte<T> eje) {
        Corte c = stack.get(Tipo.COMPONENTE);
        return c == null ? null : c.valor(eje);
    }

    public static <T> T getOrDefault(ItemStack stack, EjeDeCorte<T> eje, T porDefecto) {
        T v = get(stack, eje);
        return v == null ? porDefecto : v;
    }

    public static <T> void set(ItemStack stack, EjeDeCorte<T> eje, @Nullable T valor) {
        Corte nuevo = de(stack).con(eje, valor);
        if (nuevo.vacio()) stack.remove(Tipo.COMPONENTE);
        else stack.set(Tipo.COMPONENTE, nuevo);
    }

    public static void remove(ItemStack stack, EjeDeCorte<?> eje) {
        set(stack, eje, null);
    }

    /** Escribe {@code valor}: el de fábrica borra el eje. También vuelve a fábrica el eje que este reinicia. */
    public static <T> void escribir(ItemStack stack, EjeDeCorte<T> eje, T valor) {
        set(stack, eje, java.util.Objects.equals(valor, eje.deFabrica()) ? null : valor);
        if (eje.reinicia() != null) remove(stack, eje.reinicia());
    }

    // --- Codec ---

    private static Corte leerMapa(Map<String, Dynamic<?>> crudo) {
        Map<EjeDeCorte<?>, Object> m = new HashMap<>();
        for (var e : crudo.entrySet()) {
            EjeDeCorte<?> eje = EjeDeCorte.porNombre(e.getKey());
            if (eje == null) {
                LOG.warn("[ModaMod] Eje de corte desconocido, se ignora: {}", e.getKey());
                continue;
            }
            eje.codec().parse(e.getValue()).resultOrPartial(err -> LOG.warn("[ModaMod] Eje {} ilegible: {}", eje, err))
                    .ifPresent(v -> m.put(eje, v));
        }
        return new Corte(m);
    }

    private static Map<String, Dynamic<?>> escribirMapa(Corte corte) {
        Map<String, Dynamic<?>> m = new LinkedHashMap<>();
        corte.valores.forEach((eje, valor) -> m.put(eje.nombre(), codificar(eje, valor)));
        return m;
    }

    @SuppressWarnings("unchecked")
    private static <T> Dynamic<NbtElement> codificar(EjeDeCorte<T> eje, Object valor) {
        NbtElement nbt = eje.codec().encodeStart(NbtOps.INSTANCE, (T) valor).getOrThrow();
        return new Dynamic<>(NbtOps.INSTANCE, nbt);
    }
}
