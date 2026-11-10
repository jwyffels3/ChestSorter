package com.shweit.chestsorter.listeners;

import com.shweit.chestsorter.ChestSorter;
import com.shweit.chestsorter.PlayerData;
import com.shweit.chestsorter.sorting.SortHelper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;

/**
 * Listener for sneak+left-click sorting of containers.
 */
public class SneakSortListener implements Listener {

  private final ChestSorter plugin;

  /**
   * Constructor for SneakSortListener.
   *
   * @param plugin The plugin instance
   */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
      justification = "Plugin instance is intentionally shared in Bukkit plugins")
  public SneakSortListener(ChestSorter plugin) {
    this.plugin = plugin;
  }

  /**
   * Handles player interact events for sneak+left-click sorting.
   *
   * @param event The player interact event
   */
  @EventHandler
  public void onPlayerInteract(PlayerInteractEvent event) {
    // Check if sneak-sort is enabled
    if (!plugin.getConfig().getBoolean("sneak-sort.enabled")) {
      return;
    }

    // Check if player has permission
    Player player = event.getPlayer();
    if (!player.hasPermission("chestsorter.sneak.sort")) {
      return;
    }

    // Check if player has sorting enabled
    PlayerData playerData = plugin.getPlayerData();
    PlayerData.PlayerSettings settings = playerData.getSettings(player);
    if (!settings.isEnabled()) {
      return;
    }

    // Check if player is sneaking
    if (!player.isSneaking()) {
      return;
    }

    // Check if left-clicking a block
    if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
      return;
    }

    // Bare hand only, so crouch-mining with a tool always mines normally
    if (!player.getInventory().getItemInMainHand().isEmpty()) {
      return;
    }

    Block block = event.getClickedBlock();
    if (block == null) {
      return;
    }

    // Check if it's plain storage (chest, barrel, shulker box, ender chest); anything else,
    // like a furnace, is left alone so the punch just starts breaking it
    Inventory inventory = SortHelper.getBlockInventory(player, block);
    if (inventory == null || !SortHelper.isSortableStorage(inventory)) {
      return;
    }

    // Check location blacklist
    Location location = block.getLocation();
    if (plugin.getSortHelper().isBlacklisted(location)) {
      return;
    }

    // Check container type blacklist
    String blockKey = block.getType().getKey().toString();
    if (plugin.getConfig().getStringList("blacklist.container-types").contains(blockKey)) {
      return;
    }

    // Cancel the interaction (prevent opening the chest)
    event.setCancelled(true);

    // Sort the container using player's preferences
    plugin.getSortHelper().sortInventory(inventory,
        settings.getDefaultSortType(), settings.getDefaultSortOrder());

    // Send success message
    String message = Objects.requireNonNullElse(
        plugin.getConfig().getString("messages.sort-success",
            "Container sorted successfully!"),
        "Container sorted successfully!");
    player.sendActionBar(Component.text(message, NamedTextColor.GREEN));

    // Play success sound
    plugin.getSortHelper().playSuccessSound(player);
  }
}
