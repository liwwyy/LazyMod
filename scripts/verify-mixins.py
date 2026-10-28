#!/usr/bin/env python3
"""Check exact pinned game members used by mixins. Does not prove runtime mixin application."""
import subprocess
import sys

jar = sys.argv[1]
expected = {
    "net.minecraft.client.Minecraft": ["private void runTick(boolean);", "private boolean startAttack();", "private void startUseItem();", "private void handleKeybinds();", "private void pick(float);", "protected int missTime;", "private int rightClickDelay;"],
    "net.minecraft.client.gui.Gui": ["public void setScreen(net.minecraft.client.gui.screens.Screen);"],
    "net.minecraft.client.MouseHandler": ["private void onButton(long, net.minecraft.client.input.MouseButtonInfo, int);"],
    "net.minecraft.client.KeyboardHandler": ["private void keyPress(long, int, net.minecraft.client.input.KeyEvent);"],
    "net.minecraft.client.multiplayer.ClientPacketListener": ["public void sendUnattendedCommand(java.lang.String, net.minecraft.client.gui.screens.Screen);"],
    "net.minecraft.client.multiplayer.MultiPlayerGameMode": ["public void releaseUsingItem(net.minecraft.world.entity.player.Player);"]
}
for cls, members in expected.items():
    output = subprocess.check_output(["javap", "-p", "-classpath", jar, cls], text=True)
    for member in members:
        signature = member.removeprefix("private ").removeprefix("public ").removeprefix("protected ")
        if signature not in output:
            raise ValueError(f"Missing mixin target: {cls}: {member}")
print("Verified all pinned Minecraft 26.2 mixin target signatures (static inspection only)")
