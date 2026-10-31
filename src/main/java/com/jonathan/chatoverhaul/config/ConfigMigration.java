package com.jonathan.chatoverhaul.config;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * One-time, non-destructive migration of DLTCore's Forge config files from
 * their historical {@code chatoverhaul-*} names to the {@code DLTCore-*}
 * names.
 *
 * Forge does not look for a renamed config file automatically: with an
 * explicit file name it only ever reads that exact file, so an existing
 * {@code chatoverhaul-server.toml} would silently be replaced by a fresh
 * defaults file. Copying the legacy file to the new name before
 * {@code registerConfig} is called preserves every value the player/server
 * owner already set.
 *
 * The legacy file is deliberately copied, not moved or deleted - it stays
 * behind as a plain backup, so nothing is ever lost even if the user wants
 * to roll back to an older DLTCore build.
 *
 * Where the files live:
 *  - CLIENT config: {@code config/DLTCore-client.toml}
 *  - SERVER config template: {@code config/DLTCore-server.toml}
 *  - SERVER config in use: {@code <world>/serverconfig/DLTCore-server.toml}
 *    (Forge loads SERVER configs per-world, from {@code LevelResource}
 *    "serverconfig"; {@code config/} only seeds that file when it is
 *    missing). Both the template and every existing world's
 *    {@code serverconfig} folder are migrated, so an already-running world
 *    keeps its values instead of picking up defaults.
 *
 * A dedicated server whose level-name is not "world" is covered too: every
 * immediate subdirectory of the game directory is checked for a
 * {@code serverconfig} folder, which is exactly where a world folder keeps
 * it, whatever it is called.
 */
public final class ConfigMigration {

    public static final String LEGACY_CLIENT_FILE = "chatoverhaul-client.toml";
    public static final String CLIENT_FILE = "DLTCore-client.toml";
    public static final String LEGACY_SERVER_FILE = "chatoverhaul-server.toml";
    public static final String SERVER_FILE = "DLTCore-server.toml";

    private static final Logger LOGGER = LogUtils.getLogger();

    private ConfigMigration() {}

    /** Call once from the mod constructor, before registerConfig. */
    public static void run() {
        Path configDir = FMLPaths.CONFIGDIR.get();
        copyIfMissing(configDir.resolve(LEGACY_CLIENT_FILE), configDir.resolve(CLIENT_FILE));
        copyIfMissing(configDir.resolve(LEGACY_SERVER_FILE), configDir.resolve(SERVER_FILE));

        Path gameDir = FMLPaths.GAMEDIR.get();

        // Dedicated server world folders (any level-name) plus anything else
        // that happens to keep a serverconfig folder at the top level.
        migrateServerConfigFolders(gameDir);

        // Integrated server worlds (singleplayer / LAN).
        Path saves = gameDir.resolve("saves");
        if (Files.isDirectory(saves)) {
            try (Stream<Path> worlds = Files.list(saves)) {
                worlds.filter(Files::isDirectory)
                        .map(world -> world.resolve("serverconfig"))
                        .forEach(ConfigMigration::migrateServerConfigFolder);
            } catch (IOException | UncheckedIOException e) {
                LOGGER.warn("[DLTCore] Could not scan {} for legacy server configs", saves, e);
            }
        }
    }

    private static void migrateServerConfigFolders(Path gameDir) {
        if (!Files.isDirectory(gameDir)) {
            return;
        }
        try (Stream<Path> children = Files.list(gameDir)) {
            children.filter(Files::isDirectory)
                    .map(child -> child.resolve("serverconfig"))
                    .forEach(ConfigMigration::migrateServerConfigFolder);
        } catch (IOException | UncheckedIOException e) {
            LOGGER.warn("[DLTCore] Could not scan {} for legacy server configs", gameDir, e);
        }
    }

    private static void migrateServerConfigFolder(Path serverConfigDir) {
        if (Files.isDirectory(serverConfigDir)) {
            copyIfMissing(serverConfigDir.resolve(LEGACY_SERVER_FILE),
                    serverConfigDir.resolve(SERVER_FILE));
        }
    }

    private static void copyIfMissing(Path legacy, Path current) {
        if (!Files.exists(legacy) || Files.exists(current)) {
            return;
        }
        try {
            Files.copy(legacy, current, StandardCopyOption.COPY_ATTRIBUTES);
            LOGGER.info("[DLTCore] Migrated config {} -> {}", legacy, current);
        } catch (IOException e) {
            LOGGER.warn("[DLTCore] Could not migrate config {} -> {}", legacy, current, e);
        }
    }
}
