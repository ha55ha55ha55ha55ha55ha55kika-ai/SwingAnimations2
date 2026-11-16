package com.swinganimations.mixin;

import com.swinganimations.link.ClientLink;
import com.swinganimations.module.ClientLinkModule;
import com.swinganimations.module.ModuleManager;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * ClientLink: значок логотипа в начале сообщения чата, если в его начале стоит ник игрока с нашим клиентом.
 * Меняется только отображаемая копия строки (сервер и подписи сообщений не затрагиваются).
 */
@Mixin(ChatHud.class)
public abstract class ChatHudBadgeMixin {
    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private Text swa$badge(Text message) {
        try {
            ClientLinkModule m = ModuleManager.clientLink;
            if (m == null || !m.isEnabled() || !m.badgeChat() || message == null) return message;
            return ClientLink.mentionsLinked(message.getString()) ? ClientLink.withBadge(message) : message;
        } catch (Throwable t) {
            return message;
        }
    }
}
