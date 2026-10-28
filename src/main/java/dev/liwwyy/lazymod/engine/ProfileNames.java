// SPDX-License-Identifier: MIT
package dev.liwwyy.lazymod.engine;

public final class ProfileNames {
  private ProfileNames() {}

  public static boolean isLazyCommand(String command) {
    String s = command.stripLeading();
    return s.equals("lazy")
        || s.startsWith("lazy") && s.length() > 4 && Character.isWhitespace(s.charAt(4));
  }

  public static String quote(String name) {
    return "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  public static String parse(String value) {
    String s = value.strip();
    if (!s.startsWith("\"")) return s;
    StringBuilder out = new StringBuilder();
    boolean escaped = false;
    for (int i = 1; i < s.length(); i++) {
      char c = s.charAt(i);
      if (escaped) {
        if (c != '\"' && c != '\\') throw new IllegalArgumentException("Invalid name escape");
        out.append(c);
        escaped = false;
      } else if (c == '\\') escaped = true;
      else if (c == '\"') {
        if (!s.substring(i + 1).isBlank())
          throw new IllegalArgumentException("Unexpected text after quoted profile name");
        return out.toString();
      } else out.append(c);
    }
    throw new IllegalArgumentException("Unclosed quoted profile name");
  }
}
