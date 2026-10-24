package de.spotifylyrics;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpotifyLyricsClient implements ClientModInitializer {
    public static final String MOD_ID = "spotifylyrics";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Config CONFIG;
    public static SpotifyAuth AUTH;
    public static SpotifyPoller POLLER;
    public static LyricsManager LYRICS;

    private static KeyMapping toggleKey;

    @Override
    public void onInitializeClient() {
        CONFIG = Config.load();
        LYRICS = new LyricsManager();
        AUTH = new SpotifyAuth(CONFIG);
        POLLER = new SpotifyPoller(CONFIG, AUTH, LYRICS);

        if (CONFIG.clientId == null || CONFIG.clientId.isBlank()) {
            LOGGER.warn("Keine Spotify Client-ID gesetzt! Trage sie in config/spotify-lyrics.json ein.");
        } else if (!AUTH.isLoggedIn()) {
            AUTH.startLogin();
        }
        POLLER.start();

        // Taste zum Ein-/Ausblenden (Standard: L, in den Steuerungs-Einstellungen aenderbar)
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.spotifylyrics.toggle",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_L,
                category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.consumeClick()) {
                CONFIG.enabled = !CONFIG.enabled;
                CONFIG.save();
                if (client.player != null) {
                    client.player.sendSystemMessage(Component.literal(
                            "Spotify Lyrics: " + (CONFIG.enabled ? "an" : "aus")));
                }
            }
        });

        // HUD: wird direkt vor dem Chat gezeichnet
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "lyrics"),
                LyricsHud::render);
    }
}
