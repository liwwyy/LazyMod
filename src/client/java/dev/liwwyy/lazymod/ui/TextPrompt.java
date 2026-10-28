// SPDX-License-Identifier: MIT
package dev.liwwyy.lazymod.ui;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class TextPrompt extends Screen {
  private final String initial;
  private final Consumer<String> done;
  private final Runnable cancel;
  private String error = "";
  private EditBox field;

  TextPrompt(String title, String initial, Consumer<String> done, Runnable cancel) {
    super(Component.literal(title));
    this.initial = initial;
    this.done = done;
    this.cancel = cancel;
  }

  @Override
  protected void init() {
    field =
        addRenderableWidget(
            new EditBox(
                font,
                width / 2 - 150,
                height / 2 - 20,
                300,
                20,
                Component.literal("Profile name")));
    field.setMaxLength(64);
    field.setValue(initial);
    setInitialFocus(field);
    addRenderableWidget(
        Button.builder(
                Component.literal("Done"),
                b -> {
                  try {
                    done.accept(field.getValue());
                  } catch (IllegalArgumentException e) {
                    error = e.getMessage();
                  }
                })
            .bounds(width / 2 - 150, height / 2 + 12, 145, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal("Cancel"), b -> onClose())
            .bounds(width / 2 + 5, height / 2 + 12, 145, 20)
            .build());
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
    super.extractRenderState(g, x, y, delta);
    g.centeredText(font, title, width / 2, height / 2 - 48, 0xffffffff);
    g.textWithWordWrap(
        font, Component.literal(error), width / 2 - 150, height / 2 + 45, 300, 0xffff7777);
  }

  @Override
  public void onClose() {
    cancel.run();
  }
}
