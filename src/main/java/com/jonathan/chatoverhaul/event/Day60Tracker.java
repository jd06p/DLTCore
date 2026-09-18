package com.jonathan.chatoverhaul.event;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Durable one-shot flag for the Day 60 event (see Day60Event).
 *
 * "Once per world" must survive restarts, so the flag is stored as a plain
 * UTF-8 text file in the world's own save folder (server.getWorldPath(ROOT))
 * - the same pattern FirstJoinTracker uses, for the same reasons:
 * entity-NBT persistence is tied to timing/player-lifecycle subtleties,
 * whereas a file either exists or it doesn't, with nothing in the chain to
 * be wrong. Presence of the file means the event has already fired; absence
 * means it hasn't.
 *
 * A fresh world gets its own save folder and therefore replays the event;
 * the same world never replays it, no matter how it was shut down.
 *
 * Writes are write-to-temp-then-atomic-rename, so even a crash mid-write
 * can never leave a truncated file behind - the marker is always either the
 * complete previous state or the complete new one.
 */
final class Day60Tracker {

    private static final String FILE_NAME = "chatoverhaul_day60.txt";

    private Day60Tracker() {}

    static boolean alreadyTriggered(MinecraftServer server) {
        return Files.exists(filePathFor(server));
    }

    /** Synchronous - the marker is on disk before this returns, so a crash right after cannot re-fire the event. */
    static void markTriggered(MinecraftServer server) {
        Path path = filePathFor(server);
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(FILE_NAME + ".tmp");
            Files.write(tmp, "triggered".getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // Fails open (treat as "not yet triggered") rather than crash the
            // server - worst case the event could replay once, which is far
            // better than breaking the server because of a file write.
            e.printStackTrace();
        }
    }

    private static Path filePathFor(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
    }
}