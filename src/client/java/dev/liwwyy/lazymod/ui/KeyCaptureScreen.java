// SPDX-License-Identifier: MIT
package dev.liwwyy.lazymod.ui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

final class KeyCaptureScreen extends Screen {
  private final Consumer<InputConstants.Key> done;
  private final Runnable cancel;
  private String error = "";

  KeyCaptureScreen(Consumer<InputConstants.Key> done, Runnable cancel) {
    super(Component.literal("Press a key or mouse button"));
    this.done = done;
    this.cancel = cancel;
  }

  @Override
  protected void init() {
    addRenderableWidget(
        Button.builder(Component.literal("Clear binding"), b -> choose(InputConstants.UNKNOWN))
            .bounds(width / 2 - 150, height / 2 + 20, 145, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal("Cancel (Esc)"), b -> onClose())
            .bounds(width / 2 + 5, height / 2 + 20, 145, 20)
            .build());
  }

  private void choose(InputConstants.Key key) {
    try {
      done.accept(key);
    } catch (IllegalArgumentException e) {
      error = e.getMessage();
    }
  }

  @Override
  public boolean keyPressed(KeyEvent event) {
    if (event.key() == GLFW.GLFW_KEY_ESCAPE) onClose();
    else choose(InputConstants.getKey(event));
    return true;
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
    if (event.y() >= height / 2 + 20
        && event.y() < height / 2 + 40
        && event.x() >= width / 2 - 150
        && event.x() < width / 2 + 150) return super.mouseClicked(event, twice);
    choose(InputConstants.Type.MOUSE.getOrCreate(event.button()));
    return true;
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
    super.extractRenderState(g, x, y, delta);
    g.centeredText(font, title, width / 2, height / 2 - 20, 0xffffffff);
    g.textWithWordWrap(
        font, Component.literal(error), width / 2 - 150, height / 2 + 55, 300, 0xffff7777);
  }

  @Override
  public void onClose() {
    cancel.run();
  }
}
