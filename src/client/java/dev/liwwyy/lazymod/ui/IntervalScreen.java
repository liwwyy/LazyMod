// SPDX-License-Identifier: MIT
package dev.liwwyy.lazymod.ui;

import dev.liwwyy.lazymod.engine.ActionTimer;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class IntervalScreen extends Screen {
  private final int original;
  private final IntConsumer done;
  private final Runnable cancel;
  private EditBox field;
  private QuickSlider slider;
  private String error = "";

  IntervalScreen(int value, IntConsumer done, Runnable cancel) {
    super(Component.literal("Custom interval (milliseconds)"));
    original = value;
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
                height / 2 - 30,
                300,
                20,
                Component.literal("Exact positive integer milliseconds")));
    field.setMaxLength(16);
    field.setValue(Integer.toString(original));
    setInitialFocus(field);
    slider = addRenderableWidget(new QuickSlider(width / 2 - 150, height / 2, original));
    field.setResponder(
        value -> {
          try {
            int n = ActionTimer.parseInterval(value);
            error = n < 50 || n > 5000 ? "Outside slider range; exact value will be retained." : "";
            slider.reflect(n);
          } catch (IllegalArgumentException e) {
            error = e.getMessage();
          }
        });
    if (original < 50 || original > 5000)
      error = "Outside slider range; exact value will be retained.";
    addRenderableWidget(
        Button.builder(
                Component.literal("Apply to draft"),
                b -> {
                  try {
                    done.accept(ActionTimer.parseInterval(field.getValue()));
                  } catch (IllegalArgumentException e) {
                    error = e.getMessage();
                  }
                })
            .bounds(width / 2 - 150, height / 2 + 33, 145, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal("Cancel"), b -> onClose())
            .bounds(width / 2 + 5, height / 2 + 33, 145, 20)
            .build());
  }

  private final class QuickSlider extends AbstractSliderButton {
    QuickSlider(int x, int y, int n) {
      super(x, y, 300, 20, Component.empty(), Math.clamp((n - 50) / 4950.0, 0.0, 1.0));
      updateMessage();
    }

    void reflect(int n) {
      value = Math.clamp((n - 50) / 4950.0, 0.0, 1.0);
      updateMessage();
    }

    @Override
    protected void updateMessage() {
      setMessage(
          Component.literal(
              "Quick interval: " + (50 + (int) Math.round(value * 4950)) + " ms (50–5000)"));
    }

    @Override
    protected void applyValue() {
      field.setValue(Integer.toString(50 + (int) Math.round(value * 4950)));
      updateMessage();
    }
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
    super.extractRenderState(g, x, y, delta);
    g.centeredText(font, title, width / 2, height / 2 - 60, 0xffffffff);
    g.textWithWordWrap(
        font, Component.literal(error), width / 2 - 150, height / 2 + 65, 300, 0xffffcc77);
  }

  @Override
  public void onClose() {
    cancel.run();
  }
}
