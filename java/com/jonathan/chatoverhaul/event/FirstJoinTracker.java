package com.jonathan.chatoverhaul.event;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks which players have already triggered chatoverhaul's first-join
 * chat sequence - stored as a plain UTF-8 text file (one player UUID per
 * line) inside the world's own save folder, not as NBT on any player
 * entity.
 *
 * WHY A PLAIN FILE, NOT player.getPersistentData():
 * The previous version stored the flag on the player entity's own
 * persistent NBT, forced with an explicit PlayerList#save(player) call
 * right after setting it. That was a real improvement over the very first
 * version (which just trusted an eventual autosave/graceful-disconnect to
 * write it), but it's still tied to that specific player entity's own save
 * path - and it kept replaying after kicks/crashes regardless. Rather than
 * guess at a third theory for exactly which part of that path was
 * unreliable, this sidesteps the whole entity-NBT lifecycle: a plain file,
 * read and written with nothing but standard java.nio.file calls, has no
 * dependency on player-save timing, entity lifecycle, or NBT serialization
 * behavior at all - there's simply nothing left in that chain to be wrong
 * about.
 *
 * Stored under the WORLD's own save folder (server.getWorldPath(ROOT)),
 * matching rus-patch's own "firstjoin" flag, which is also a per-world
 * (not global) concept - a fresh world should replay the sequence, but
 * this same world should never replay it for a player who's already seen
 * it, no matter how they left it last time.
 *
 * Writes are write-to-temp-then-atomic-rename, so even a crash that
 * happens mid-write can never leave a truncated/corrupted file behind -
 * the real file is always either the complete previous version or the
 * complete new version, never a partial one.
 */
final class FirstJoinTracker {

    private static final String FILE_NAME = "chatoverhaul_first_join.txt";

    private FirstJoinTracker() {}

    static boolean hasSeenIntro(MinecraftServer server, UUID uuid) {
        return load(server).contains(uuid.toString());
    }

    /** Synchronous - the write is on disk before this returns, not queued for later. */
    static void markSeen(MinecraftServer server, UUID uuid) {
        Set<String> seen = load(server);
        if (seen.add(uuid.toString())) {
            save(server, seen);
        }
    }

    private static Path filePathFor(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
    }

    private static Set<String> load(MinecraftServer server) {
        Path path = filePathFor(server);
        Set<String> result = new HashSet<>();
        if (!Files.exists(path)) {
            return result;
        }
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        } catch (IOException e) {
            // Fails open (treat as "not seen yet") rather than crash the
            // server - worst case a player sees the sequence one extra
            // time, which is far better than the mod breaking logins.
            e.printStackTrace();
        }
        return result;
    }

    private static void save(MinecraftServer server, Set<String> seen) {
        Path path = filePathFor(server);
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(FILE_NAME + ".tmp");
            Files.write(tmp, seen, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
