package net.gigabit101.rebornstorage.client;

import net.minecraft.client.util.InputMappings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static final KeyBinding OPEN_WIRELESS_CRAFTING_GRID = new KeyBinding(
        "key.rebornstorage.openWirelessCraftingGrid",
        KeyConflictContext.IN_GAME,
        KeyModifier.CONTROL,
        InputMappings.Type.KEYSYM,
        GLFW.GLFW_KEY_G,
        "Reborn Storage"
    );
    public static final KeyBinding MODE_SWITCH_WIRELESS_CRAFTING_GRID = new KeyBinding(
            "key.rebornstorage.switchModeWirelessCraftingGrid",
            KeyConflictContext.IN_GAME,
            KeyModifier.CONTROL,
            InputMappings.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "Reborn Storage"
    );
}
