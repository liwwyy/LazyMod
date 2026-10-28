// SPDX-License-Identifier: MIT
package dev.liwwyy.lazymod.mixin;

import dev.liwwyy.lazymod.LazyModClient;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
  @Inject(method = "setScreen", at = @At("HEAD"))
  private void lazymod$screen(Screen screen, CallbackInfo ci) {
    if (LazyModClient.INSTANCE != null)
      LazyModClient.INSTANCE.automation.screenChanging(screen != null);
  }
}
