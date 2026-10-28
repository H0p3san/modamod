package com.modamod.body;

import com.modamod.Modamod;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Donde vive el {@link PerfilCuerpo} de cada jugador.
 *
 * Attachment de Fabric y no Cardinal Components: CCA esta en el proyecto solo
 * porque lo pide Cosmetic Armor Updated, y la API de attachments hace
 * exactamente esto con menos ceremonia.
 *
 * Las tres opciones del builder son las tres cosas que hacen falta:
 * - persistent: el cuerpo tiene que sobrevivir al cierre del mundo.
 * - copyOnDeath: morir no te cambia el cuerpo.
 * - syncWith(..., all()): lo compone el CLIENTE, y no solo el del dueño —
 *   todos los que lo ven tienen que dibujarle el mismo sustrato, asi que la
 *   sincronizacion va a todos y no solo al target.
 */
public final class PerfilesDeCuerpo {

    public static final AttachmentType<PerfilCuerpo> PERFIL =
            AttachmentRegistry.<PerfilCuerpo>builder()
                    .initializer(() -> PerfilCuerpo.DEFECTO)
                    .persistent(PerfilCuerpo.CODEC)
                    .copyOnDeath()
                    .syncWith(PerfilCuerpo.PACKET_CODEC, AttachmentSyncPredicate.all())
                    .buildAndRegister(Identifier.of(Modamod.MOD_ID, "perfil_cuerpo"));

    private PerfilesDeCuerpo() {}

    public static PerfilCuerpo de(PlayerEntity jugador) {
        PerfilCuerpo perfil = jugador.getAttached(PERFIL);
        return perfil == null ? PerfilCuerpo.DEFECTO : perfil;
    }

    public static void poner(PlayerEntity jugador, PerfilCuerpo perfil) {
        jugador.setAttached(PERFIL, perfil);
    }

    public static void init() {
        // fuerza class-loading: sin esto el attachment no queda registrado
        // hasta la primera lectura, y una lectura del cliente antes de que el
        // servidor lo registre llega sin tipo conocido.
    }
}
