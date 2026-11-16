package com.swinganimations.event;

import net.minecraft.entity.Entity;

/** Игрок ударил сущность (только клиентская сторона). */
public record AttackEvent(Entity target) {}
