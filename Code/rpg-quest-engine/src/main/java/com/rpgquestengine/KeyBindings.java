package com.rpgquestengine;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static final String KEY_CATEGORY_QUESTS = "key.categories.rpgquestengine";
    public static final String KEY_OPEN_JOURNAL = "key.rpgquestengine.open_journal";
    public static final String KEY_TRACK_PROMPT = "key.rpgquestengine.track_prompt";

    public static KeyMapping OPEN_JOURNAL_KEY = new KeyMapping(
            KEY_OPEN_JOURNAL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            KEY_CATEGORY_QUESTS
    );

    public static KeyMapping TRACK_PROMPT_KEY = new KeyMapping(
            KEY_TRACK_PROMPT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            KEY_CATEGORY_QUESTS
    );

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_JOURNAL_KEY);
        event.register(TRACK_PROMPT_KEY);
    }
}

