package com.jonathan.chatoverhaul.event;

import com.jonathan.chatoverhaul.ChatOverhaul;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID)
public final class NullJoinCommandInterceptor {

    private static final Pattern TEXT_PATTERN = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern TEXT_ESCAPED = Pattern.compile("\\\\\"text\\\\\"\\s*:\\s*\\\\\"([^\\\\\"]+)\\\\");

    private NullJoinCommandInterceptor() {}

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        String input = event.getParseResults().getReader().getString();
        if (!input.startsWith("tellraw")) {
            return;
        }
        String lower = input.toLowerCase();
        if (lower.contains(" joined the game") || lower.contains(" left the game")) {
            event.setCanceled(true);
            try {
                String name = extractName(input);
                if (name.isEmpty()) {
                    return;
                }
                String nLower = name.toLowerCase();
                if (nLower.contains("has connected to local play") || nLower.contains("has disconnected from local play")) {
                    return;
                }
                if (nLower.contains(" joined the game")) {
                    name = name.substring(0, nLower.indexOf(" joined the game"));
                }
                if (nLower.contains(" left the game")) {
                    name = name.substring(0, nLower.indexOf(" left the game"));
                }
                if (name.trim().isEmpty()) {
                    return;
                }
                MinecraftServer server = event.getParseResults().getContext().getSource().getServer();
                if (server != null) {
                    String suffix = lower.contains(" joined the game") ? " has connected to Local Play!" : " has disconnected from Local Play!";
                    server.getPlayerList().broadcastSystemMessage(Component.literal(name + suffix).withStyle(ChatFormatting.WHITE), false);
                }
            } catch (Exception ignored) {
            }
            return;
        }
    }

    private static String extractName(String input) {
        try {
            int brace = input.indexOf('{');
            if (brace >= 0) {
                String json = input.substring(brace);
                Matcher m = TEXT_PATTERN.matcher(json);
                if (m.find()) {
                    return m.group(1);
                }
            }
        } catch (Exception ignored) {
        }
        Matcher m2 = TEXT_ESCAPED.matcher(input);
        if (m2.find()) {
            return m2.group(1);
        }
        return "";
    }
}