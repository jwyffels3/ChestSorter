package com.shweit.chestsorter.listeners;

import com.shweit.chestsorter.ChestSorter;
import com.shweit.chestsorter.PlayerData;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Listener for sneak+left-click-air sorting of the player's own inventory.
 *
 * <p>Punching the air (left-click) is used rather than right-click: with an empty hand the
 * Java client sends nothing to the server for a right-click on air, while the arm swing of a
 * left-click always reaches the server (also from Bedrock players via Geyser).
 */
public class AirSortListener implements Listener {

  /** Ignore further swings for this long after a sort, so one punch sorts once. */
  private static final long COOLDOWN_MILLIS = 500;

  private final ChestSorter plugin;
  private final Map<UUID, Long> lastSort = new HashMap<>();

  /**
   * Constructor for AirSortListener.
   *
   * @param plugin The plugin instance
   */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
      justification = "Plugin instance is intentionally shared in Bukkit plugins")
  public AirSortListener(ChestSorter plugin) {
    this.plugin = plugin;
  }

  /**
   * Sorts the player's inventory when they sneak and punch the air with an empty hand.
   *
   * @param event The player interact event
   */
  @EventHandler
  public void onPlayerInteract(PlayerInteractEvent event) {
    // Default on, so existing config files without this section get the feature too
    if (!plugin.getConfig().getBoolean("air-sort.enabled", true)) {
      return;
    }

    if (event.getAction() != Action.LEFT_CLICK_AIR || event.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player player = event.getPlayer();
    if (!player.isSneaking() || !player.getInventory().getItemInMainHand().isEmpty()) {
      return;
    }

    if (!player.hasPermission("chestsorter.air.sort")) {
      return;
    }

    PlayerData.PlayerSettings settings = plugin.getPlayerData().getSettings(player);
    if (!settings.isEnabled()) {
      return;
    }

    long now = System.currentTimeMillis();
    Long last = lastSort.get(player.getUniqueId());
    if (last != null && now - last < COOLDOWN_MILLIS) {
      return;
    }
    lastSort.put(player.getUniqueId(), now);

    plugin.getSortHelper().sortInventory(player.getInventory(),
        settings.getDefaultSortType(), settings.getDefaultSortOrder());

    String message = Objects.requireNonNullElse(
        plugin.getConfig().getString("messages.sort-player-success",
            "Inventory sorted successfully!"),
        "Inventory sorted successfully!");
    player.sendActionBar(Component.text(message, NamedTextColor.GREEN));
    plugin.getSortHelper().playSuccessSound(player);
  }

  /**
   * Forgets the cooldown of players who leave.
   *
   * @param event The player quit event
   */
  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    lastSort.remove(event.getPlayer().getUniqueId());
  }
}
