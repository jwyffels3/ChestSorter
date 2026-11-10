package com.shweit.chestsorter.sorting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;

/**
 * Handles sorting of inventories based on different criteria.
 */
public class InventorySorter {

  /** First main-storage slot of a player inventory (slots 0-8 are the hotbar). */
  private static final int PLAYER_STORAGE_FIRST = 9;
  /** End (exclusive) of a player inventory's main storage (36-39 armor, 40 offhand). */
  private static final int PLAYER_STORAGE_END = 36;
  /** Player inventory slots whose partial stacks get refilled: hotbar (0-8) and offhand (40). */
  private static final int[] HAND_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 40};

  /**
   * Sorts an inventory based on the specified sort type and order.
   *
   * @param inventory The inventory to sort
   * @param sortType The type of sorting to apply
   * @param sortOrder The order to sort in (ASC/DESC)
   */
  public void sortInventory(Inventory inventory, SortType sortType, SortOrder sortOrder) {
    // A player inventory also holds the hotbar (0-8), armor (36-39) and offhand (40).
    // Only the 27 main storage slots are sorted, so gear never moves in or out of those.
    int from = 0;
    int to = inventory.getSize();
    if (inventory instanceof PlayerInventory) {
      from = PLAYER_STORAGE_FIRST;
      to = PLAYER_STORAGE_END;
    }

    List<ItemStack> items = new ArrayList<>();

    // Collect all non-null items in the sorted range
    ItemStack[] contents = Objects.requireNonNull(inventory.getContents(), "Inventory contents");
    for (int i = from; i < to; i++) {
      ItemStack item = contents[i];
      if (item != null && item.getType() != Material.AIR) {
        items.add(item.clone());
      }
    }

    // Refill partial hotbar and offhand stacks first; those slots themselves never move
    if (inventory instanceof PlayerInventory) {
      topUpHandSlots(inventory, contents, items);
    }

    // Combine partial stacks of identical items, so sorting also tidies up
    items = mergeStacks(items, inventory.getMaxStackSize());

    // Sort based on type
    Comparator<ItemStack> comparator = getComparator(sortType);

    // Reverse for descending order
    if (sortOrder == SortOrder.DESC) {
      comparator = comparator.reversed();
    }

    items.sort(comparator);

    // Clear the sorted range and repopulate it; slots outside the range are untouched
    for (int i = from; i < to; i++) {
      inventory.setItem(i, i - from < items.size() ? items.get(i - from) : null);
    }
  }

  /**
   * Fills partial hotbar and offhand stacks with identical items (see {@link ItemStack#isSimilar})
   * taken from the main inventory, e.g. 32 stone bricks on the hotbar and 64 in the inventory
   * become 64 and 32. These slots keep their position; only their amounts grow. Stacks emptied
   * this way are removed from {@code items}, so the total amount of every item is unchanged.
   *
   * @param inventory The player inventory
   * @param contents The inventory contents as read before sorting
   * @param items The main-inventory stacks (copies), reduced by what moves to the hands
   */
  private void topUpHandSlots(Inventory inventory, ItemStack[] contents, List<ItemStack> items) {
    for (int slot : HAND_SLOTS) {
      ItemStack held = contents[slot];
      if (held == null || held.getType() == Material.AIR) {
        continue;
      }
      int limit = Math.min(held.getMaxStackSize(), inventory.getMaxStackSize());
      ItemStack filled = held.clone();
      for (ItemStack item : items) {
        if (filled.getAmount() >= limit) {
          break;
        }
        if (item.getAmount() > 0 && item.isSimilar(filled)) {
          int moved = Math.min(limit - filled.getAmount(), item.getAmount());
          filled.setAmount(filled.getAmount() + moved);
          item.setAmount(item.getAmount() - moved);
        }
      }
      if (filled.getAmount() != held.getAmount()) {
        inventory.setItem(slot, filled);
      }
    }
    items.removeIf(item -> item.getAmount() <= 0);
  }

  /**
   * Combines partial stacks of identical items (same type, name, enchantments, damage and other
   * components, see {@link ItemStack#isSimilar}) up to the stack limit. The total amount of every
   * item is unchanged, and the result never needs more slots than the input.
   *
   * @param items The stacks to merge (not modified)
   * @param inventoryLimit The inventory's maximum stack size
   * @return The merged stacks, in first-seen order
   */
  private List<ItemStack> mergeStacks(List<ItemStack> items, int inventoryLimit) {
    List<ItemStack> merged = new ArrayList<>();
    for (ItemStack item : items) {
      int remaining = item.getAmount();
      for (ItemStack stack : merged) {
        if (remaining == 0) {
          break;
        }
        if (stack.isSimilar(item)) {
          int limit = Math.min(stack.getMaxStackSize(), inventoryLimit);
          int moved = Math.min(limit - stack.getAmount(), remaining);
          if (moved > 0) {
            stack.setAmount(stack.getAmount() + moved);
            remaining -= moved;
          }
        }
      }
      if (remaining > 0) {
        ItemStack rest = item.clone();
        rest.setAmount(remaining);
        merged.add(rest);
      }
    }
    return merged;
  }

  /**
   * Gets the appropriate comparator for the sort type.
   *
   * @param sortType The sort type
   * @return The comparator for that sort type
   */
  private Comparator<ItemStack> getComparator(SortType sortType) {
    return switch (sortType) {
      case NAME -> Comparator.comparing(item -> item.getType().name());
      case QUANTITY -> Comparator.comparingInt(ItemStack::getAmount);
      case MATERIAL_TYPE -> Comparator
          .comparing((ItemStack item) -> getMaterialCategory(item.getType()))
          .thenComparing(item -> item.getType().name());
      case DURABILITY -> Comparator
          .comparingInt(this::getDurabilityValue)
          .thenComparing(item -> item.getType().name());
    };
  }

  /**
   * Gets a category string for a material to group similar items.
   *
   * @param material The material to categorize
   * @return A category string
   */
  private String getMaterialCategory(Material material) {
    if (material.isBlock()) {
      // Group blocks by material properties
      if (isOre(material)) {
        return "A_ORE";
      } else if (isWood(material)) {
        return "B_WOOD";
      } else if (isStone(material)) {
        return "C_STONE";
      } else if (material.name().contains("GLASS")) {
        return "D_GLASS";
      } else if (material.name().contains("WOOL") || material.name().contains("CARPET")) {
        return "E_WOOL";
      } else if (material.name().contains("CONCRETE")) {
        return "F_CONCRETE";
      } else {
        return "G_BLOCK";
      }
    } else if (material.isEdible()) {
      return "H_FOOD";
    } else if (isTool(material)) {
      return "I_TOOL";
    } else if (isArmor(material)) {
      return "J_ARMOR";
    } else if (isWeapon(material)) {
      return "K_WEAPON";
    } else {
      return "Z_OTHER";
    }
  }

  /**
   * Checks if a material is an ore.
   *
   * @param material The material to check
   * @return true if it's an ore
   */
  private boolean isOre(Material material) {
    String name = material.name();
    return name.contains("_ORE") || name.contains("RAW_");
  }

  /**
   * Checks if a material is wood-related.
   *
   * @param material The material to check
   * @return true if it's wood
   */
  private boolean isWood(Material material) {
    String name = material.name();
    return name.contains("_LOG") || name.contains("_WOOD") || name.contains("_PLANKS");
  }

  /**
   * Checks if a material is stone-related.
   *
   * @param material The material to check
   * @return true if it's stone
   */
  private boolean isStone(Material material) {
    String name = material.name();
    return name.contains("STONE") || name.contains("COBBLESTONE")
        || name.contains("ANDESITE") || name.contains("DIORITE") || name.contains("GRANITE");
  }

  /**
   * Checks if a material is a tool.
   *
   * @param material The material to check
   * @return true if it's a tool
   */
  private boolean isTool(Material material) {
    String name = material.name();
    return name.contains("_PICKAXE") || name.contains("_AXE")
        || name.contains("_SHOVEL") || name.contains("_HOE");
  }

  /**
   * Checks if a material is armor.
   *
   * @param material The material to check
   * @return true if it's armor
   */
  private boolean isArmor(Material material) {
    String name = material.name();
    return name.contains("_HELMET") || name.contains("_CHESTPLATE")
        || name.contains("_LEGGINGS") || name.contains("_BOOTS");
  }

  /**
   * Checks if a material is a weapon.
   *
   * @param material The material to check
   * @return true if it's a weapon
   */
  private boolean isWeapon(Material material) {
    String name = material.name();
    return name.contains("_SWORD") || name.equals("BOW")
        || name.equals("CROSSBOW") || name.equals("TRIDENT");
  }

  /**
   * Gets the durability value for an item (for sorting).
   * Items without durability get max value.
   *
   * @param item The item to check
   * @return The remaining durability, or Integer.MAX_VALUE if not applicable
   */
  private int getDurabilityValue(ItemStack item) {
    if (item.getItemMeta() instanceof Damageable damageable) {
      Material type = item.getType();
      int maxDurability = type.getMaxDurability();
      if (maxDurability > 0) {
        return maxDurability - damageable.getDamage();
      }
    }
    return Integer.MAX_VALUE;
  }
}
