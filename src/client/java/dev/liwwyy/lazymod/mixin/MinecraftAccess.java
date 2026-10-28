// SPDX-License-Identifier: MIT
package dev.liwwyy.lazymod.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAccess {
  @Accessor("rightClickDelay")
  int lazymod$useDelay();

  @Accessor("rightClickDelay")
  void lazymod$useDelay(int ticks);

  @Invoker("startAttack")
  boolean lazymod$attack();

  @Invoker("startUseItem")
  void lazymod$use();

  @Invoker("pick")
  void lazymod$pick(float partialTick);
}
