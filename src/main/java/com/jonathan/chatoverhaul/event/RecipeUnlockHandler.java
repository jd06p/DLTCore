package com.jonathan.chatoverhaul.event;

import com.jonathan.chatoverhaul.ChatOverhaul;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;

/**
 * Unlocks every recipe the server's recipe manager knows about for a player
 * when they join the world.
 *
 * Uses the vanilla recipe-book API rather than touching recipe-book data
 * directly:
 *
 *  - {@link RecipeManager#getRecipes()} returns every registered recipe,
 *    including mod-added ones, so nothing is hardcoded.
 *  - {@link ServerPlayer#awardRecipes(Collection)} already skips recipes the
 *    player has learned and only sends an update packet for the newly
 *    unlocked ones (it returns the number added), so reconnecting is
 *    idempotent and does no unnecessary network work.
 *
 * Hooked on {@link PlayerEvent.PlayerLoggedInEvent}, which fires once per
 * login (and not on dimension change or respawn), so it never runs every
 * tick and changing dimensions does not re-run it.
 *
 * Only recipe knowledge is granted - no advancements, items, experience,
 * statistics, inventory changes, or permissions.
 */
@Mod.EventBusSubscriber(modid = ChatOverhaul.MODID)
public final class RecipeUnlockHandler {

    private RecipeUnlockHandler() {}

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        RecipeManager recipeManager = server.getRecipeManager();
        Collection<Recipe<?>> recipes = recipeManager.getRecipes();
        if (recipes.isEmpty()) {
            return;
        }
        player.awardRecipes(recipes);
    }
}
