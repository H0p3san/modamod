package com.modamod.item;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Material de armadura "vacío" (0 de defensa/toughness) para 1.21.1.
 * En esta versión ArmorMaterial es un record que se registra en
 * Registries.ARMOR_MATERIAL (igual que un encantamiento data-driven);
 * ArmorItem pide un RegistryEntry<ArmorMaterial>, no el record pelado.
 */
public final class ModamodArmorMaterials {

    public static final RegistryEntry<ArmorMaterial> CLOTH = register(
            "cloth",
            defenseMap(0, 0, 0, 0),
            5,
            SoundEvents.ITEM_ARMOR_EQUIP_LEATHER,
            () -> Ingredient.ofItems(Items.STRING),
            0.0f,
            0.0f,
            false
    );

    // Material propia para las medias color pleno: layer marcado dyeable=true,
    // así Minecraft aplica el tinte de DYED_COLOR automáticamente al renderizar
    // (igual que el cuero) — no hace falta ningún ArmorRenderer custom para esta.
    public static final RegistryEntry<ArmorMaterial> SOCKS_SOLID = register(
            "socks_solid",
            defenseMap(0, 0, 0, 0),
            5,
            SoundEvents.ITEM_ARMOR_EQUIP_LEATHER,
            () -> Ingredient.ofItems(Items.WHITE_WOOL),
            0.0f,
            0.0f,
            true
    );

    private static Map<ArmorItem.Type, Integer> defenseMap(int boots, int legs, int chest, int helmet) {
        Map<ArmorItem.Type, Integer> map = new EnumMap<>(ArmorItem.Type.class);
        map.put(ArmorItem.Type.BOOTS, boots);
        map.put(ArmorItem.Type.LEGGINGS, legs);
        map.put(ArmorItem.Type.CHESTPLATE, chest);
        map.put(ArmorItem.Type.HELMET, helmet);
        return map;
    }

    private static RegistryEntry<ArmorMaterial> register(String id, Map<ArmorItem.Type, Integer> defense,
            int enchantability, RegistryEntry<SoundEvent> equipSound, Supplier<Ingredient> repairIngredient,
            float toughness, float knockbackResistance, boolean dyeable) {
        ArmorMaterial material = new ArmorMaterial(
                defense,
                enchantability,
                equipSound,
                repairIngredient,
                List.of(new ArmorMaterial.Layer(Identifier.of("modamod", id), "", dyeable)),
                toughness,
                knockbackResistance
        );
        ArmorMaterial registered = Registry.register(Registries.ARMOR_MATERIAL,
                Identifier.of("modamod", id), material);
        return RegistryEntry.of(registered);
    }

    private ModamodArmorMaterials() {}
}
