// SPDX-License-Identifier: MIT
package dev.liwwyy.lazymod;

import static org.junit.jupiter.api.Assertions.*;

import dev.liwwyy.lazymod.engine.ProfileNames;
import org.junit.jupiter.api.Test;

class CommandNamesTest {
  @Test
  void namesRoundTripWithQuotesAndSpaces() {
    for (String name : new String[] {"Raidfarm", "My Farm", "A \"quoted\" farm", "A\\B"})
      assertEquals(name, ProfileNames.parse(ProfileNames.quote(name)));
    assertEquals("My Farm", ProfileNames.parse("My Farm"));
  }

  @Test
  void malformedNamesStayErrors() {
    for (String s : new String[] {"\"open", "\"closed\" extra", "\"bad\\x\""})
      assertThrows(IllegalArgumentException.class, () -> ProfileNames.parse(s));
  }

  @Test
  void onlyExactLazyRootIsIntercepted() {
    for (String s : new String[] {"lazy", "lazy ac bogus", "lazy\tbad", "  lazy config"})
      assertTrue(ProfileNames.isLazyCommand(s));
    for (String s : new String[] {"lazymod", "lazyother ac", "other lazy", "/lazy"})
      assertFalse(ProfileNames.isLazyCommand(s));
  }
}
