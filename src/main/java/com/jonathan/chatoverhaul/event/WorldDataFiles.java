package com.jonathan.chatoverhaul.event;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Resolves the per-world marker files used by DLTCore's one-shot trackers
 * (first join, day 60, operator notice).
 *
 * The files used to be named {@code chatoverhaul_*}; they are now named
 * {@code DLTCore_*}. A legacy file is copied to the new name the first time
 * it is resolved, so an existing world keeps its state (nobody re-sees the
 * intro, the Day 60 event does not re-fire, an operator is not re-notified)
 * instead of appearing brand new after the rename.
 *
 * Copy, never move: the legacy file stays as a backup, matching the
 * config migration's non-destructive behavior.
 */
final class WorldDataFiles {

    private static final Logger LOGGER = LogUtils.getLogger();

    private WorldDataFiles() {}

    static Path resolve(MinecraftServer server, String fileName, String legacyFileName) {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        Path current = worldRoot.resolve(fileName);
        if (!Files.exists(current)) {
            Path legacy = worldRoot.resolve(legacyFileName);
            if (Files.exists(legacy)) {
                try {
                    Files.copy(legacy, current, StandardCopyOption.COPY_ATTRIBUTES);
                    LOGGER.info("[DLTCore] Migrated world data {} -> {}", legacy, current);
                } catch (IOException e) {
                    LOGGER.warn("[DLTCore] Could not migrate world data {} -> {}", legacy, current, e);
                }
            }
        }
        return current;
    }
}
