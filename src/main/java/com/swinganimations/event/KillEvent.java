package com.swinganimations.event;

import net.minecraft.entity.LivingEntity;

/** Сущность умерла (клиент получил статус смерти). */
public record KillEvent(LivingEntity entity) {}
