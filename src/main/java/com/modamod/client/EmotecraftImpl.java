package com.modamod.client;

import com.modamod.maniqui.ManiquiBlockEntity;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.util.Vec3f;
import io.github.kosmx.emotes.main.EmoteHolder;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Lado que sí toca Emotecraft / player-animation-lib. Evalúa la animación como lo hace
 * {@code AnimationApplier#updatePart} de la librería (posición, rotación y escala de cada parte, con el valor
 * actual como base), pero sobre el {@code PlayerEntityModel} del Maniquí y con reloj propio: la animación vive
 * en un {@link KeyframeAnimationPlayer} por maniquí que se avanza con el tiempo real (50 ms por tick) y se
 * reinicia al terminar, así se ve igual en el mundo y en la pantalla.
 */
final class EmotecraftImpl {

    private static final class Reproductor {
        UUID id;
        KeyframeAnimationPlayer player;
        long inicioNanos;
        int ticksDados;
    }

    private static final Map<ManiquiBlockEntity, Reproductor> REPRODUCTORES = new WeakHashMap<>();

    static List<UUID> lista() {
        List<EmoteHolder> todos = new ArrayList<>();
        for (EmoteHolder h : EmoteHolder.list) todos.add(h);
        todos.sort((a, b) -> a.name.getString().compareToIgnoreCase(b.name.getString()));
        List<UUID> out = new ArrayList<>(todos.size());
        for (EmoteHolder h : todos) out.add(h.getUuid());
        return out;
    }

    static String nombre(UUID id) {
        EmoteHolder h = EmoteHolder.list.get(id);
        return h == null ? "?" : h.name.getString();
    }

    static boolean aplicar(ManiquiBlockEntity be, UUID id, PlayerEntityModel<LivingEntity> m) {
        EmoteHolder holder = EmoteHolder.list.get(id);
        if (holder == null) return false;
        Reproductor r = REPRODUCTORES.computeIfAbsent(be, k -> new Reproductor());
        long ahora = System.nanoTime();
        if (r.player == null || !id.equals(r.id) || !r.player.isActive()) {
            r.id = id;
            r.player = new KeyframeAnimationPlayer(holder.emote);
            r.inicioNanos = ahora;
            r.ticksDados = 0;
        }
        float ticks = (ahora - r.inicioNanos) / 50_000_000f;
        int enteros = (int) ticks;
        while (r.ticksDados < enteros && r.player.isActive()) {
            r.player.tick();
            r.ticksDados++;
        }
        r.player.setupAnim(ticks - enteros);

        parte(r.player, "head", m.head);
        parte(r.player, "torso", m.body);
        parte(r.player, "rightArm", m.rightArm);
        parte(r.player, "leftArm", m.leftArm);
        parte(r.player, "rightLeg", m.rightLeg);
        parte(r.player, "leftLeg", m.leftLeg);
        return true;
    }

    private static void parte(KeyframeAnimationPlayer p, String nombre, ModelPart parte) {
        parte.resetTransform();
        float delta = 0f;
        Vec3f pos = p.get3DTransform(nombre, TransformType.POSITION, delta, new Vec3f(parte.pivotX, parte.pivotY, parte.pivotZ));
        parte.pivotX = pos.getX();
        parte.pivotY = pos.getY();
        parte.pivotZ = pos.getZ();
        Vec3f rot = p.get3DTransform(nombre, TransformType.ROTATION, delta, new Vec3f(parte.pitch, parte.yaw, parte.roll));
        parte.setAngles(rot.getX(), rot.getY(), rot.getZ());
    }

    private EmotecraftImpl() {}
}
