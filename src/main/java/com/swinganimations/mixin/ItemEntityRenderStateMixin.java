package com.swinganimations.mixin;

import com.swinganimations.module.ItemPhysicsModule;
import com.swinganimations.render.ItemPhysicsHolder;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public class ItemEntityRenderStateMixin implements ItemPhysicsHolder {
    @Unique
    private ItemPhysicsModule.Anim swa$anim;

    @Override
    public ItemPhysicsModule.Anim swa$anim() { return swa$anim; }

    @Override
    public void swa$setAnim(ItemPhysicsModule.Anim a) { swa$anim = a; }
}
