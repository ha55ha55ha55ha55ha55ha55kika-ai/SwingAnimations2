package com.swinganimations.render;

/** Флаг в состоянии рендера сущности: «рисовать полупрозрачным призраком» (SeeInvisibles). */
public interface InvisibleAccess {
    boolean swa$isGhost();

    void swa$setGhost(boolean v);
}
