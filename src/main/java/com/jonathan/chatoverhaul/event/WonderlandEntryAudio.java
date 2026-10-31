package com.jonathan.chatoverhaul.event;

import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

import java.util.Set;

/** Move Wonderland's existing entry audio cleanup to BEFORE the client creates its new ambience. */
public final class WonderlandEntryAudio {
    // Verified against Wonderland 3.0.3's dimension -> entry procedure registrations.
    // Do not broaden this to a namespace rule: other entries do not schedule a stop-all task.
    private static final Set<String> DESTINATIONS = Set.of(
            "the_wonderland:acceptance", "the_wonderland:answer_dimension", "the_wonderland:corners",
            "the_wonderland:depths", "the_wonderland:dlrowmaerd", "the_wonderland:dream_6",
            "the_wonderland:dreamworld", "the_wonderland:endless_forest", "the_wonderland:fools_paradise",
            "the_wonderland:maze", "the_wonderland:no_mans_land", "the_wonderland:the_slip",
            "the_wonderland:tunnels", "the_wonderland:what_once_was");

    private WonderlandEntryAudio() {}

    public static boolean needsCleanup(boolean wonderlandLoaded, String from, String to) {
        return wonderlandLoaded && !from.equals(to) && DESTINATIONS.contains(to);
    }

    public static void beforeRespawn(ServerPlayer player, ServerLevel destination) {
        if (needsCleanup(ModList.get().isLoaded("the_wonderland"),
                player.level().dimension().location().toString(), destination.dimension().location().toString())) {
            // Same connection guarantees ordering: clear OLD sounds, then handle the respawn packet.
            // No @p selector: nearby players must not lose their ambience when someone else travels.
            player.connection.send(new ClientboundStopSoundPacket(null, null));
        }
    }
}
