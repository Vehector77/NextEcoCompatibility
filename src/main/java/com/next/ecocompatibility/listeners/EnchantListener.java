package com.next.ecocompatibility.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class EnchantListener implements Listener {

    private final JavaPlugin plugin;
    private final String GUI_NAME = ChatColor.DARK_GRAY + "Yunque (Bedrock)";
    private final Map<UUID, Integer> activeCosts = new HashMap<>();
    
    private final Set<UUID> awaitingNameInput = new HashSet<>();
    private final Map<UUID, ItemStack[]> savedAnvilSession = new HashMap<>();
    private final Map<UUID, String> requestedNames = new HashMap<>();

    public EnchantListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isBedrockPlayer(Player player) {
        try {
            Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return false;
        }
    }

    @EventHandler
    public void onAnvilInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            if (event.getClickedBlock().getType() == Material.ANVIL || 
                event.getClickedBlock().getType() == Material.CHIPPED_ANVIL || 
                event.getClickedBlock().getType() == Material.DAMAGED_ANVIL) {
                
                Player player = event.getPlayer();
                
                if (isBedrockPlayer(player)) {
                    event.setCancelled(true);
                    requestedNames.remove(player.getUniqueId());
                    openCustomAnvil(player);
                }
            }
        }
    }

    private void openCustomAnvil(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, GUI_NAME);
        
        ItemStack separator = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = separator.getItemMeta();
        meta.setDisplayName(ChatColor.BLACK + " ");
        separator.setItemMeta(meta);

        ItemStack renameBtn = new ItemStack(Material.NAME_TAG);
        ItemMeta renameMeta = renameBtn.getItemMeta();
        renameMeta.setDisplayName(ChatColor.YELLOW + "Renombrar Objeto");
        List<String> renameLore = new ArrayList<>();
        renameLore.add(ChatColor.GRAY + "Haz clic aquí para escribir");
        renameLore.add(ChatColor.GRAY + "el nuevo nombre en el chat.");
        renameMeta.setLore(renameLore);
        renameBtn.setItemMeta(renameMeta);

        inv.setItem(2, renameBtn);
        inv.setItem(3, separator);
        inv.setItem(5, separator);
        inv.setItem(6, separator);
        inv.setItem(7, separator);
        inv.setItem(8, separator);
        player.openInventory(inv);
    }
    
    private void reopenSession(Player player) {
        openCustomAnvil(player);
        Inventory inv = player.getOpenInventory().getTopInventory();
        ItemStack[] saved = savedAnvilSession.remove(player.getUniqueId());
        if (saved != null) {
            if (saved[0] != null) inv.setItem(0, saved[0]);
            if (saved[1] != null) inv.setItem(1, saved[1]);
        }
        updateResultSlot(inv, player);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(GUI_NAME)) {
            Inventory inv = event.getInventory();
            Player player = (Player) event.getWhoClicked();

            if (event.getClickedInventory() != inv) {
                if (event.isShiftClick()) {
                    event.setCancelled(true);
                    ItemStack clicked = event.getCurrentItem();
                    if (clicked != null && clicked.getType() != Material.AIR) {
                        if (inv.getItem(0) == null || inv.getItem(0).getType() == Material.AIR) {
                            inv.setItem(0, clicked.clone());
                            event.setCurrentItem(null);
                        } else if (inv.getItem(1) == null || inv.getItem(1).getType() == Material.AIR) {
                            inv.setItem(1, clicked.clone());
                            event.setCurrentItem(null);
                        }
                    }
                    Bukkit.getScheduler().runTaskLater(plugin, () -> updateResultSlot(inv, player), 1L);
                }
                return;
            }

            int slot = event.getRawSlot();
            
            if (slot == 2) {
                event.setCancelled(true);
                ItemStack target = inv.getItem(0);
                if (target == null || target.getType() == Material.AIR) {
                    player.sendMessage(ChatColor.RED + "¡Pon un objeto en la primera ranura para renombrarlo!");
                    return;
                }
                
                ItemStack[] saved = new ItemStack[2];
                saved[0] = target.clone();
                if (inv.getItem(1) != null && inv.getItem(1).getType() != Material.AIR) {
                    saved[1] = inv.getItem(1).clone();
                }
                
                savedAnvilSession.put(player.getUniqueId(), saved);
                awaitingNameInput.add(player.getUniqueId());
                
                player.closeInventory();
                
                try {
                    org.geysermc.floodgate.api.player.FloodgatePlayer fPlayer = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
                    if (fPlayer != null) {
                        org.geysermc.cumulus.form.CustomForm form = org.geysermc.cumulus.form.CustomForm.builder()
                            .title("Renombrar Objeto")
                            .input("Escribe el nuevo nombre del objeto:", "Ejemplo: Espada Épica", "")
                            .validResultHandler(response -> {
                                String newName = response.asInput();
                                if (newName != null && !newName.trim().isEmpty()) {
                                    requestedNames.put(player.getUniqueId(), ChatColor.translateAlternateColorCodes('&', newName));
                                }
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    awaitingNameInput.remove(player.getUniqueId());
                                    reopenSession(player);
                                });
                            })
                            .closedOrInvalidResultHandler(response -> {
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    awaitingNameInput.remove(player.getUniqueId());
                                    reopenSession(player);
                                });
                            })
                            .build();
                        fPlayer.sendForm(form);
                    } else {
                        // Fallback por si fallara la API de Floodgate en devolver al jugador
                        awaitingNameInput.remove(player.getUniqueId());
                        reopenSession(player);
                        player.sendMessage(ChatColor.RED + "Error al intentar abrir el menú de renombrar.");
                    }
                } catch (Exception e) {
                    awaitingNameInput.remove(player.getUniqueId());
                    reopenSession(player);
                    player.sendMessage(ChatColor.RED + "No se pudo cargar la interfaz nativa.");
                }
                
                return;
            }

            if (slot == 3 || slot >= 5 && slot <= 8) {
                event.setCancelled(true);
                return;
            }

            if (slot == 4) {
                event.setCancelled(true);
                ItemStack result = inv.getItem(4);
                
                if (result != null && result.getType() != Material.AIR) {
                    
                    if (!event.isShiftClick() && event.getCursor() != null && event.getCursor().getType() != Material.AIR) {
                        return; // El cursor debe estar vacío para recoger el item
                    }
                    
                    int cost = activeCosts.getOrDefault(player.getUniqueId(), 0);
                    
                    if (player.getLevel() < cost && player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        player.sendMessage(ChatColor.RED + "¡No tienes suficiente experiencia! Necesitas " + cost + " niveles.");
                        return;
                    }
                    
                    // Descontar XP
                    if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                        player.setLevel(player.getLevel() - cost);
                    }
                    
                    // Remover el Lore visual del coste
                    ItemMeta resultMeta = result.getItemMeta();
                    if (resultMeta != null && resultMeta.hasLore()) {
                        List<String> lore = resultMeta.getLore();
                        lore.removeIf(line -> ChatColor.stripColor(line).contains("Coste de XP:"));
                        resultMeta.setLore(lore);
                        result.setItemMeta(resultMeta);
                    }

                    if (event.isShiftClick()) {
                        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(result);
                        for (ItemStack left : leftover.values()) {
                            player.getWorld().dropItem(player.getLocation(), left);
                        }
                    } else {
                        event.getView().setCursor(result);
                    }
                    
                    inv.setItem(0, null);
                    
                    ItemStack book = inv.getItem(1);
                    if (book != null) {
                        book.setAmount(book.getAmount() - 1);
                        inv.setItem(1, book.getAmount() > 0 ? book : null);
                    }
                    
                    inv.setItem(4, null);
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
                    activeCosts.remove(player.getUniqueId());
                    requestedNames.remove(player.getUniqueId());
                }
                return;
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> updateResultSlot(inv, player), 1L);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().equals(GUI_NAME)) {
            // Evitar arrastrar items sobre la decoración o el resultado
            for (int slot : event.getRawSlots()) {
                if (slot == 2 || slot == 3 || slot >= 4 && slot <= 8) {
                    event.setCancelled(true);
                    return;
                }
            }
            Player player = (Player) event.getWhoClicked();
            Inventory inv = event.getInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> updateResultSlot(inv, player), 1L);
        }
    }

    private void updateResultSlot(Inventory inv, Player player) {
        ItemStack target = inv.getItem(0);
        ItemStack book = inv.getItem(1);
        
        String renameTo = requestedNames.get(player.getUniqueId());
        boolean isRenaming = renameTo != null;

        if (target == null || target.getType() == Material.AIR) {
            inv.setItem(4, null);
            activeCosts.remove(player.getUniqueId());
            requestedNames.remove(player.getUniqueId());
            return;
        }

        boolean hasBook = book != null && book.getType() != Material.AIR;
        boolean isBook = hasBook && (book.getType() == Material.ENCHANTED_BOOK || book.getType() == Material.BOOK);
        boolean isSameType = hasBook && (target.getType() == book.getType());
        
        if (hasBook && !isBook && !isSameType) {
            inv.setItem(4, null);
            activeCosts.remove(player.getUniqueId());
            return;
        }

        ItemMeta cursorMeta = hasBook ? book.getItemMeta() : null;

        Map<Enchantment, Integer> storedEnchants = new HashMap<>();
        if (cursorMeta != null) {
            storedEnchants.putAll(cursorMeta.getEnchants());
            if (cursorMeta instanceof EnchantmentStorageMeta) {
                storedEnchants.putAll(((EnchantmentStorageMeta) cursorMeta).getStoredEnchants());
            }
        }

        if (!hasBook && !isRenaming) {
            inv.setItem(4, null);
            activeCosts.remove(player.getUniqueId());
            return;
        }

        ItemStack result = target.clone();
        
        if (result.getType() == Material.BOOK && !storedEnchants.isEmpty()) {
            result.setType(Material.ENCHANTED_BOOK);
            ItemMeta newMeta = Bukkit.getItemFactory().getItemMeta(Material.ENCHANTED_BOOK);
            if (target.getItemMeta().hasDisplayName()) {
                newMeta.setDisplayName(target.getItemMeta().getDisplayName());
            }
            if (target.getItemMeta().hasLore()) {
                newMeta.setLore(target.getItemMeta().getLore());
            }
            result.setItemMeta(newMeta);
        }

        ItemMeta resultMeta = result.getItemMeta();
        if (resultMeta == null) return;

        boolean applied = false;
        boolean bookApplied = false;
        int xpCost = 0;
        
        if (isRenaming) {
            String currentName = resultMeta.hasDisplayName() ? resultMeta.getDisplayName() : "";
            if (!currentName.equals(renameTo)) {
                resultMeta.setDisplayName(renameTo);
                applied = true;
                xpCost += 1;
            }
        }
        
        // Calcular penalización previa (Prior Work Penalty)
        int targetPenalty = 0;
        if (resultMeta instanceof Repairable) {
            targetPenalty = ((Repairable) resultMeta).getRepairCost();
        }
        
        int bookPenalty = 0;
        if (cursorMeta instanceof Repairable && hasBook) {
            bookPenalty = ((Repairable) cursorMeta).getRepairCost();
        }
        
        if (hasBook) {
            xpCost += targetPenalty + bookPenalty;
        } else if (isRenaming) {
            xpCost += targetPenalty;
        }

        // Lógica de reparación si son del mismo tipo
        if (isSameType && resultMeta instanceof Damageable && cursorMeta instanceof Damageable) {
            Damageable targetDamageable = (Damageable) resultMeta;
            Damageable bookDamageable = (Damageable) cursorMeta;
            
            if (targetDamageable.hasDamage() || bookDamageable.hasDamage()) {
                int maxDurability = result.getType().getMaxDurability();
                if (maxDurability > 0) {
                    int targetDurability = maxDurability - targetDamageable.getDamage();
                    int bookDurability = maxDurability - bookDamageable.getDamage();
                    
                    int newDurability = targetDurability + bookDurability + (int) (maxDurability * 0.12);
                    int newDamage = maxDurability - newDurability;
                    if (newDamage < 0) newDamage = 0;
                    
                    if (newDamage < targetDamageable.getDamage()) {
                        targetDamageable.setDamage(newDamage);
                        applied = true;
                        bookApplied = true;
                        xpCost += 2; 
                    }
                }
            }
        }

        for (Map.Entry<Enchantment, Integer> entry : storedEnchants.entrySet()) {
            Enchantment enchant = entry.getKey();
            int level = entry.getValue();

            boolean canEnchant = true;
            if (result.getType() != Material.ENCHANTED_BOOK) {
                canEnchant = enchant.canEnchantItem(result);
            }

            if (canEnchant) {
                boolean hasConflict = false;
                
                Map<Enchantment, Integer> existingEnchants;
                if (resultMeta instanceof EnchantmentStorageMeta) {
                    existingEnchants = ((EnchantmentStorageMeta) resultMeta).getStoredEnchants();
                } else {
                    existingEnchants = resultMeta.getEnchants();
                }
                
                for (Enchantment existing : existingEnchants.keySet()) {
                    if (!existing.equals(enchant) && (existing.conflictsWith(enchant) || enchant.conflictsWith(existing))) {
                        hasConflict = true;
                        break;
                    }
                }

                if (!hasConflict) {
                    int existingLevel = existingEnchants.getOrDefault(enchant, 0);
                    int newLevel = existingLevel;
                    
                    if (existingLevel == level) {
                        newLevel = level + 1;
                        if (newLevel > enchant.getMaxLevel()) {
                            newLevel = enchant.getMaxLevel();
                        }
                    } else if (level > existingLevel) {
                        newLevel = level;
                    }
                    
                    if (newLevel > existingLevel) {
                        if (resultMeta instanceof EnchantmentStorageMeta) {
                            ((EnchantmentStorageMeta) resultMeta).addStoredEnchant(enchant, newLevel, true);
                        } else {
                            resultMeta.addEnchant(enchant, newLevel, true);
                        }
                        applied = true;
                        bookApplied = true;
                        // Vanilla cobra más si combinas dos armas que si usas un libro.
                        int multiplier = isBook ? 1 : 2;
                        xpCost += newLevel * multiplier;
                    }
                }
            }
        }
        
        if (hasBook && !bookApplied) {
            inv.setItem(4, null);
            activeCosts.remove(player.getUniqueId());
            return;
        }

        if (applied) {
            if (xpCost < 1) xpCost = 1;
            
            int newPenalty = targetPenalty;
            if (hasBook) {
                newPenalty = Math.max(targetPenalty, bookPenalty) * 2 + 1;
            }
            
            if (resultMeta instanceof Repairable) {
                ((Repairable) resultMeta).setRepairCost(newPenalty);
            }
            
            result.setItemMeta(resultMeta);

            // Validar límites de EcoEnchants (o cualquier otro plugin) usando un PrepareAnvilEvent falso.
            Inventory dummyAnvil = Bukkit.createInventory(player, org.bukkit.event.inventory.InventoryType.ANVIL);
            dummyAnvil.setItem(0, target);
            if (hasBook) dummyAnvil.setItem(1, book);
            if (dummyAnvil instanceof org.bukkit.inventory.AnvilInventory) {
                ((org.bukkit.inventory.AnvilInventory) dummyAnvil).setRepairCost(xpCost);
            }

            org.bukkit.event.inventory.PrepareAnvilEvent prepareEvent = null;
            try {
                // Spigot 1.21+ requiere AnvilView en lugar de InventoryView
                Class<?> anvilViewClass = Class.forName("org.bukkit.inventory.view.AnvilView");
                Object dummyAnvilView = java.lang.reflect.Proxy.newProxyInstance(
                        anvilViewClass.getClassLoader(),
                        new Class<?>[]{anvilViewClass},
                        (proxy, method, args) -> {
                            String name = method.getName();
                            if (name.equals("getTopInventory")) return dummyAnvil;
                            if (name.equals("getBottomInventory")) return player.getInventory();
                            if (name.equals("getPlayer")) return player;
                            if (name.equals("getType")) return org.bukkit.event.inventory.InventoryType.ANVIL;
                            if (name.equals("getTitle")) return "Anvil";
                            if (name.equals("countSlots")) return dummyAnvil.getSize();
                            if (name.equals("equals")) return proxy == args[0];
                            if (name.equals("hashCode")) return System.identityHashCode(proxy);
                            if (name.equals("toString")) return "DummyAnvilView";
                            
                            if (name.equals("getItem")) {
                                int s = (int) args[0];
                                if (s < dummyAnvil.getSize()) return dummyAnvil.getItem(s);
                                return player.getInventory().getItem(s - dummyAnvil.getSize());
                            }
                            if (name.equals("setItem")) {
                                int s = (int) args[0];
                                if (s < dummyAnvil.getSize()) dummyAnvil.setItem(s, (ItemStack) args[1]);
                                else player.getInventory().setItem(s - dummyAnvil.getSize(), (ItemStack) args[1]);
                                return null;
                            }
                            
                            Class<?> returnType = method.getReturnType();
                            if (returnType == boolean.class) return false;
                            if (returnType == int.class) return 0;
                            return null;
                        }
                );
                
                java.lang.reflect.Constructor<?> constructor = org.bukkit.event.inventory.PrepareAnvilEvent.class.getConstructor(anvilViewClass, ItemStack.class);
                prepareEvent = (org.bukkit.event.inventory.PrepareAnvilEvent) constructor.newInstance(dummyAnvilView, result);
            } catch (Exception e) {
                try {
                    // Intento para versiones antiguas si fuera necesario
                    Class<?> invViewClass = org.bukkit.inventory.InventoryView.class;
                    Object dummyInvView = java.lang.reflect.Proxy.newProxyInstance(
                            invViewClass.getClassLoader(),
                            new Class<?>[]{invViewClass},
                            (proxy, method, args) -> {
                                String name = method.getName();
                                if (name.equals("getTopInventory")) return dummyAnvil;
                                if (name.equals("getBottomInventory")) return player.getInventory();
                                if (name.equals("getPlayer")) return player;
                                if (name.equals("getType")) return org.bukkit.event.inventory.InventoryType.ANVIL;
                                if (name.equals("getTitle")) return "Anvil";
                                if (name.equals("countSlots")) return dummyAnvil.getSize();
                                if (name.equals("equals")) return proxy == args[0];
                                if (name.equals("hashCode")) return System.identityHashCode(proxy);
                                if (name.equals("toString")) return "DummyAnvilView";
                                
                                if (name.equals("getItem")) {
                                    int s = (int) args[0];
                                    if (s < dummyAnvil.getSize()) return dummyAnvil.getItem(s);
                                    return player.getInventory().getItem(s - dummyAnvil.getSize());
                                }
                                if (name.equals("setItem")) {
                                    int s = (int) args[0];
                                    if (s < dummyAnvil.getSize()) dummyAnvil.setItem(s, (ItemStack) args[1]);
                                    else player.getInventory().setItem(s - dummyAnvil.getSize(), (ItemStack) args[1]);
                                    return null;
                                }
                                
                                Class<?> returnType = method.getReturnType();
                                if (returnType == boolean.class) return false;
                                if (returnType == int.class) return 0;
                                return null;
                            }
                    );
                    java.lang.reflect.Constructor<?> constructor = org.bukkit.event.inventory.PrepareAnvilEvent.class.getConstructor(invViewClass, ItemStack.class);
                    prepareEvent = (org.bukkit.event.inventory.PrepareAnvilEvent) constructor.newInstance(dummyInvView, result);
                } catch (Exception ignored) {}
            }

            if (prepareEvent != null) {
                Bukkit.getPluginManager().callEvent(prepareEvent);
                
                if (prepareEvent.getResult() == null || prepareEvent.getResult().getType() == Material.AIR) {
                    // EcoEnchants u otro plugin ha bloqueado la combinación (ej. excede límite de rareza)
                    inv.setItem(4, null);
                    activeCosts.remove(player.getUniqueId());
                    return;
                }
                
                result = prepareEvent.getResult();
                if (result.getItemMeta() != null) {
                    resultMeta = result.getItemMeta();
                }
                
                if (dummyAnvil instanceof org.bukkit.inventory.AnvilInventory) {
                    xpCost = ((org.bukkit.inventory.AnvilInventory) dummyAnvil).getRepairCost();
                }
            }

            List<String> lore = resultMeta.hasLore() ? resultMeta.getLore() : new ArrayList<>();
            lore.removeIf(line -> ChatColor.stripColor(line).contains("Coste de XP:"));
            
            String costColor = player.getLevel() >= xpCost ? ChatColor.GREEN.toString() : ChatColor.RED.toString();
            lore.add(costColor + "Coste de XP: " + xpCost + " niveles");
            
            resultMeta.setLore(lore);
            result.setItemMeta(resultMeta);
            
            inv.setItem(4, result);
            activeCosts.put(player.getUniqueId(), xpCost);
        } else {
            inv.setItem(4, null);
            activeCosts.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals(GUI_NAME)) {
            Player player = (Player) event.getPlayer();
            
            if (awaitingNameInput.contains(player.getUniqueId())) {
                return; // No devolver items todavía, están guardados en la sesión.
            }

            Inventory inv = event.getInventory();

            ItemStack slot0 = inv.getItem(0);
            if (slot0 != null && slot0.getType() != Material.AIR) {
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(slot0);
                for (ItemStack left : leftover.values()) {
                    player.getWorld().dropItem(player.getLocation(), left);
                }
            }

            ItemStack slot1 = inv.getItem(1);
            if (slot1 != null && slot1.getType() != Material.AIR) {
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(slot1);
                for (ItemStack left : leftover.values()) {
                    player.getWorld().dropItem(player.getLocation(), left);
                }
            }
            
            activeCosts.remove(player.getUniqueId());
            requestedNames.remove(player.getUniqueId());
            savedAnvilSession.remove(player.getUniqueId());
        }
    }
}
