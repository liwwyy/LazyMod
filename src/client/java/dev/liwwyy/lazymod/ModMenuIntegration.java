package dev.liwwyy.lazymod;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.liwwyy.lazymod.ui.ConfigScreen;

public final class ModMenuIntegration implements ModMenuApi {
  public ConfigScreenFactory<?> getModConfigScreenFactory() {
    return parent -> ConfigScreen.create(LazyModClient.INSTANCE, parent);
  }
}
