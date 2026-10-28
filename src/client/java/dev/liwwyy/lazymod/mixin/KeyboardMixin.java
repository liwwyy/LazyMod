// SPDX-License-Identifier: MIT
package dev.liwwyy.lazymod.mixin;

import dev.liwwyy.lazymod.LazyModClient;
import net.minecraft.client.*;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardMixin {
  @Inject(method = "keyPress", at = @At("HEAD"))
  private void lazymod$input(long window, int action, KeyEvent event, CallbackInfo ci) {
    var mod = LazyModClient.INSTANCE;
    var mc = Minecraft.getInstance();
    if (mod == null || action != GLFW.GLFW_PRESS || window != mc.getWindow().handle()) return;
    if (mc.options.keyAttack.matches(event)) mod.automation.manualPress(true);
    if (mc.options.keyUse.matches(event)) mod.automation.manualPress(false);
  }
}
