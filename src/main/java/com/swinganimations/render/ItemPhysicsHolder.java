package com.swinganimations.render;

import com.swinganimations.module.ItemPhysicsModule;

/** Анимация ItemPhysics, прикреплённая к состоянию рендера предмета. */
public interface ItemPhysicsHolder {
    ItemPhysicsModule.Anim swa$anim();

    void swa$setAnim(ItemPhysicsModule.Anim a);
}
