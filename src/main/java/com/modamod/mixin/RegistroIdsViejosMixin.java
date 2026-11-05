package com.modamod.mixin;

import com.modamod.IdsViejos;
import net.minecraft.registry.Registries;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Migrador de ids (2026-11-05): un ítem guardado con un id viejo ({@code modamod:socks_solid}...) se lee como el nuevo.
 * Todo lo que carga ítems (inventarios, cofres, trinkets, el maletín) pasa por {@code getEntry(Identifier)} del codec de
 * referencias del registro; {@code get} y {@code containsId} cubren los comandos y los mods que preguntan por id.
 * Solo toca el registro de ítems. Al guardar se escribe el id nuevo, así que el mundo queda migrado al primer guardado.
 */
@Mixin(SimpleRegistry.class)
public abstract class RegistroIdsViejosMixin {

    @ModifyVariable(method = {
            "getEntry(Lnet/minecraft/util/Identifier;)Ljava/util/Optional;",
            "get(Lnet/minecraft/util/Identifier;)Ljava/lang/Object;",
            "containsId(Lnet/minecraft/util/Identifier;)Z"
    }, at = @At("HEAD"), argsOnly = true)
    private Identifier modamod$idNuevo(Identifier id) {
        if (id == null) return id;
        Identifier nuevo = IdsViejos.nuevo(id);
        // Primero el id (barato y sin cargar nada); recién si es uno viejo de ModaMod se mira que sea el registro de ítems,
        // así esto nunca fuerza la carga de Registries durante el arranque.
        if (nuevo == id || (Object) this != Registries.ITEM) return id;
        return nuevo;
    }
}
