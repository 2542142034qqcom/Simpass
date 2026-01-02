package org.simple.simpass;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MovementListener implements Listener {
    private final Set<UUID> frozenPlayers;
    private final Simpass plugin;

    public MovementListener(Simpass plugin) {
        this.plugin = plugin;
        this.frozenPlayers = new HashSet<>();
    }

    public void freezePlayer(Player player) {
        frozenPlayers.add(player.getUniqueId());
    }

    public void unfreezePlayer(Player player) {
        frozenPlayers.remove(player.getUniqueId());
    }

    public boolean isPlayerFrozen(Player player) {
        return frozenPlayers.contains(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        // Check if player is in the frozen set
        if (frozenPlayers.contains(player.getUniqueId())) {
            // 检查是否只是视角改变（允许转动头部）还是位置改变（不允许移动）
            if (!event.getFrom().getBlock().getLocation().equals(event.getTo().getBlock().getLocation())) {
                // 位置改变，取消移动
                event.setCancelled(true);
                player.sendMessage("§c请先完成身份验证！");
            }
            // 允许视角改变（转动头部），不取消事件
        }
    }
    
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemDrop().getItemStack();
        
        // 如果玩家试图丢弃地图（特别是验证二维码地图），阻止此操作
        if (frozenPlayers.contains(player.getUniqueId()) && item.getType() == Material.FILLED_MAP) {
            // 检查是否是验证二维码地图（通过名称判断）
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName() && 
                item.getItemMeta().getDisplayName().contains("身份验证二维码")) {
                event.setCancelled(true);
                player.sendMessage("§c验证完成前无法丢弃验证二维码地图！");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // Remove player from frozen set when they quit
        frozenPlayers.remove(player.getUniqueId());
    }
}