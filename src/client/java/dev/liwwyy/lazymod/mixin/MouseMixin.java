// SPDX-License-Identifier: MIT
package dev.liwwyy.lazymod.mixin;

import dev.liwwyy.lazymod.LazyModClient;
import net.minecraft.client.*;
import net.minecraft.client.input.*;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseMixin {
  @Inject(method = "onButton", at = @At("HEAD"))
  private void lazymod$input(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
    var mod = LazyModClient.INSTANCE;
    var mc = Minecraft.getInstance();
    if (mod == null || action != GLFW.GLFW_PRESS || window != mc.getWindow().handle()) return;
    MouseButtonEvent event = new MouseButtonEvent(0, 0, info);
    if (mc.options.keyAttack.matchesMouse(event)) mod.automation.manualPress(true);
    if (mc.options.keyUse.matchesMouse(event)) mod.automation.manualPress(false);
  }
}
