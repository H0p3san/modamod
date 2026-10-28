package com.modamod.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;

import java.util.UUID;

/**
 * Candado del Maniquí y del Guardarropas (2026-10-04, "le pongamos un lock al maniqui cosa que si algun jugador
 * quiere ponerlo en su tienda no le roben"): propietario + bandera. Si está cerrado solo lo tocan el dueño, los
 * operadores y los jugadores en creativo; el resto ve el mueble de solo lectura. Un mueble sin dueño (puesto antes
 * de esto) queda a nombre de quien aprieta el candado primero.
 */
public final class Candado {

    @org.jetbrains.annotations.Nullable
    private UUID propietario;
    private String nombre = "";
    private boolean cerrado = false;

    public boolean cerrado() { return cerrado; }

    public String nombre() { return nombre; }

    public void ponerDueno(PlayerEntity jugador) {
        propietario = jugador.getUuid();
        nombre = jugador.getGameProfile().getName();
    }

    public boolean tieneDueno() { return propietario != null; }

    public static boolean administra(PlayerEntity jugador) {
        return jugador.isCreative() || jugador.hasPermissionLevel(2);
    }

    public boolean esDe(PlayerEntity jugador) {
        return propietario != null && propietario.equals(jugador.getUuid());
    }

    /** ¿Puede este jugador sacar, poner o cambiar cosas? */
    public boolean puedeTocar(PlayerEntity jugador) {
        return !cerrado || propietario == null || esDe(jugador) || administra(jugador);
    }

    /** ¿Puede abrir/cerrar el candado? Cualquiera si el mueble no tiene dueño todavía. */
    public boolean puedeAlternar(PlayerEntity jugador) {
        return propietario == null || esDe(jugador) || administra(jugador);
    }

    /** Cierra o abre. Devuelve false si el jugador no puede. */
    public boolean alternar(PlayerEntity jugador) {
        if (!puedeAlternar(jugador)) return false;
        if (propietario == null) ponerDueno(jugador);
        cerrado = !cerrado;
        return true;
    }

    public void guardar(NbtCompound nbt) {
        if (propietario != null) nbt.putUuid("Propietario", propietario);
        nbt.putString("PropietarioNombre", nombre);
        nbt.putBoolean("Cerrado", cerrado);
    }

    public void leer(NbtCompound nbt) {
        propietario = nbt.containsUuid("Propietario") ? nbt.getUuid("Propietario") : null;
        nombre = nbt.getString("PropietarioNombre");
        cerrado = nbt.getBoolean("Cerrado");
    }

    /** Bits que viajan al cliente por la propiedad del handler: 1 = cerrado, 2 = puede tocar, 4 = puede alternar. */
    public int estado(PlayerEntity jugador) {
        return (cerrado ? 1 : 0) | (puedeTocar(jugador) ? 2 : 0) | (puedeAlternar(jugador) ? 4 : 0);
    }

    public static boolean cerrado(int estado) { return (estado & 1) != 0; }

    public static boolean puedeTocar(int estado) { return (estado & 2) != 0; }

    public static boolean puedeAlternar(int estado) { return (estado & 4) != 0; }
}
