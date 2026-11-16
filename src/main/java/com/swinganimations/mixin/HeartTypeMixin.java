package com.swinganimations.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.swinganimations.module.ModuleManager;
import com.swinganimations.module.RemovalsModule;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Removals: «плохие» сердца в HUD (яд, иссушение, заморозка). Выбор вида сердца делает InGameHud.HeartType.fromPlayerState;
 * мы лишь отвечаем «эффекта нет» в этом одном месте, сам статус-эффект и геймплей не меняются.
 */
@Mixin(targets = "net.minecraft.client.gui.hud.InGameHud$HeartType")
public abstract class HeartTypeMixin {
    private static final String TARGET_METHOD =
            "fromPlayerState(Lnet/minecraft/entity/player/PlayerEntity;)Lnet/minecraft/client/gui/hud/InGameHud$HeartType;";

    @WrapOperation(method = TARGET_METHOD,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z"),
            require = 0)
    private static boolean swa$effectHearts(PlayerEntity player, RegistryEntry<StatusEffect> effect, Operation<Boolean> original) {
        RemovalsModule r = ModuleManager.removals;
        if (r != null) {
            if (effect == StatusEffects.WITHER && r.witherHeartOff()) return false;
            if (effect == StatusEffects.POISON && r.badHeartsOff()) return false;
        }
        return original.call(player, effect);
    }

    @WrapOperation(method = TARGET_METHOD,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;isFrozen()Z"),
            require = 0)
    private static boolean swa$frozenHearts(PlayerEntity player, Operation<Boolean> original) {
        RemovalsModule r = ModuleManager.removals;
        if (r != null && r.badHeartsOff()) return false;
        return original.call(player);
    }
}
