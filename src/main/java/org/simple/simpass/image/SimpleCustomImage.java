package org.simple.simpass.image;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleCustomImage {
    
    private static final AtomicInteger ID_COUNTER = new AtomicInteger(1000000);
    
    private final byte[] pixels;
    private final BlockFace direction;
    private final int rotation;
    private final Location location;
    private final int frameId;
    private final int mapId;
    private final Set<UUID> shownPlayers = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    public SimpleCustomImage(Location location, BlockFace direction, int rotation, BufferedImage image) {
        this.rotation = rotation;
        this.frameId = ID_COUNTER.getAndIncrement();
        this.mapId = SimpleMapHelper.getNextMapId(location.getWorld());
        this.location = location;
        this.direction = direction;
        this.pixels = SimpleMapHelper.getPixels(image);
    }

    public int getFrameId() {
        return frameId;
    }

    public int getMapId() {
        return mapId;
    }

    public Location getLocation() {
        return location;
    }

    public BlockFace getDirection() {
        return direction;
    }

    public int getRotation() {
        return rotation;
    }

    public byte[] getPixels() {
        return pixels.clone();
    }

    public void show(Player player) {
        if (this.shownPlayers.add(player.getUniqueId())) {
            // 这里需要实际的NMS代码来在世界中创建ItemFrame并显示地图
            // 简化表示，实际显示需要更复杂的实现
        }
    }

    public void hide(Player player) {
        if (this.shownPlayers.remove(player.getUniqueId())) {
            // 简化实现：只是从显示列表中移除
        }
    }
}