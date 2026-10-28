// SPDX-License-Identifier: MIT
package dev.liwwyy.lazymod.mixin;

import dev.liwwyy.lazymod.LazyCommands;
import dev.liwwyy.lazymod.engine.ProfileNames;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fabric executes successful unattended client commands first; consume any fallback. */
@Mixin(value = ClientPacketListener.class, priority = 1100)
public abstract class CommandMixin {
  @Inject(method = "sendUnattendedCommand", at = @At("HEAD"), cancellable = true)
  private void lazymod$neverForward(String command, Screen after, CallbackInfo ci) {
    if (ProfileNames.isLazyCommand(command)) {
      LazyCommands.executeLocal(command);
      ci.cancel();
    }
  }
}
