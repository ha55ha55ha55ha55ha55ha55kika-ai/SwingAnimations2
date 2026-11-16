package com.swinganimations.event;

import com.swinganimations.module.Module;

/** Модуль включён или выключен. */
public record ModuleToggleEvent(Module module, boolean enabled) {}
