// SPDX-License-Identifier: MIT
package dev.liwwyy.lazymod.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.liwwyy.lazymod.LazyModClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
  @Inject(method = "runTick", at = @At("HEAD"))
  private void lazymod$frame(boolean render, CallbackInfo ci) {
    if (LazyModClient.INSTANCE != null) LazyModClient.INSTANCE.automation.frame();
  }

  @Inject(method = "startAttack", at = @At("HEAD"))
  private void lazymod$manualAttack(CallbackInfoReturnable<Boolean> ci) {
    if (LazyModClient.INSTANCE != null) LazyModClient.INSTANCE.automation.manualAttempt(true);
  }

  @Inject(method = "startUseItem", at = @At("HEAD"))
  private void lazymod$manualUse(CallbackInfo ci) {
    if (LazyModClient.INSTANCE != null) LazyModClient.INSTANCE.automation.manualAttempt(false);
  }

  @ModifyExpressionValue(
      method = "startAttack",
      at =
          @At(
              value = "FIELD",
              target = "Lnet/minecraft/client/Minecraft;missTime:I",
              opcode = org.objectweb.asm.Opcodes.GETFIELD))
  private int lazymod$screenMissTime(int original) {
    var mod = LazyModClient.INSTANCE;
    return mod != null
            && mod.automation.automatic()
            && mod.settings.allowInScreens
            && Minecraft.getInstance().gui.screen() != null
        ? 0
        : original;
  }

  @WrapOperation(
      method = "handleKeybinds",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;releaseUsingItem(Lnet/minecraft/world/entity/player/Player;)V"))
  private void lazymod$keepOwnedUse(
      MultiPlayerGameMode gameMode, Player player, Operation<Void> original) {
    if (LazyModClient.INSTANCE == null || !LazyModClient.INSTANCE.automation.keepUsing())
      original.call(gameMode, player);
  }
}
