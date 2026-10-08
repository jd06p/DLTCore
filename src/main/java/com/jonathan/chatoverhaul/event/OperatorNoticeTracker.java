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
 * Tracks which players have already been shown the "Admin has been granted"
 * notice in a given singleplayer/LAN world.
 *
 * WHY THIS EXISTS:
 * A singleplayer world created with cheats enabled grants the owner command
 * access from the very first tick - there is no permission *event* to hook,
 * so the notice is shown on login. Without a persistent record that would
 * repeat on every re-entry, which is exactly the "don't spam" behavior this
 * feature is supposed to avoid. Storing it per-world (not globally) means a
 * brand-new world still shows the notice once.
 *
 * Only the integrated (singleplayer/LAN) side uses this file; dedicated
 * servers rely purely on the real permission-change event, where re-op
 * must always warn - so nothing is persisted there.
 *
 * Mirrors FirstJoinTracker's approach deliberately: plain UTF-8 file under
 * the world's own save folder, atomic write-to-temp-then-rename, fail-open
 * on read errors (worst case one extra notice, never a broken login).
 */
final class OperatorNoticeTracker {

    private static final String FILE_NAME = "chatoverhaul_operator_notice.txt";

    private OperatorNoticeTracker() {}

    static boolean hasNotified(MinecraftServer server, UUID uuid) {
        return load(server).contains(uuid.toString());
    }

    static void markNotified(MinecraftServer server, UUID uuid) {
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