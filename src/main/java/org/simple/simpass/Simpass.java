package org.simple.simpass;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class Simpass extends JavaPlugin implements Listener {

    private String developerUuid = "";
    private int verificationLevel = 2; // 默认验证等级为V2
    private int reauthHours = 24; // 默认24小时后需要重新验证
    private final Map<UUID, VerificationTask> verificationTasks = new HashMap<>();
    private MovementListener movementListener;
    
    // 白名单
    private final Set<Integer> whitelist = new HashSet<>();
    
    // 保存玩家背包的映射
    private final Map<UUID, ItemStack[]> playerInventories = new WeakHashMap<>();
    
    // 保存玩家最后验证时间的映射
    private final Map<UUID, Long> lastVerificationTime = new HashMap<>();
    
    // 保存玩家原始游戏模式的映射
    private final Map<UUID, org.bukkit.GameMode> originalGameModes = new HashMap<>();
    
    // 保存玩家IP地址的映射
    private final Map<UUID, String> playerIPs = new HashMap<>();
    
    // 保存玩家位置的映射
    private final Map<UUID, org.bukkit.Location> playerLocations = new HashMap<>();
    
    // 跟踪玩家验证状态的映射（0=未验证，1=验证中，2=已验证）
    private final Map<UUID, Integer> playerVerifiedStatus = new HashMap<>();
    
    // 保存玩家验证前的原始状态
    private final Map<UUID, org.bukkit.GameMode> preVerificationGameModes = new HashMap<>();
    private final Map<UUID, ItemStack[]> preVerificationInventories = new HashMap<>();
    
    // 封禁相关数据结构
    private final Map<UUID, BanInfo> bannedPlayers = new HashMap<>();
    private final Map<Integer, BanInfo> bannedUids = new HashMap<>();

    @Override
    public void onEnable() {
        // Initialize movement listener
        this.movementListener = new MovementListener(this);
        
        // Plugin startup logic
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(movementListener, this);
        this.getCommand("simpass").setExecutor(new SimpassCommandExecutor());
        this.getCommand("auth").setExecutor(new AuthCommandExecutor());
        this.getCommand("image").setExecutor(new ImageCommandExecutor());
        this.getCommand("simpban").setExecutor(new SimpbanCommandExecutor());
        this.getCommand("unsimpban").setExecutor(new UnsimpbanCommandExecutor());
        
        // Load stored developer UUID if exists
        loadDeveloperUuid();
        
        getLogger().info("Simpass plugin has been enabled!");
        
        // 加载封禁信息
        loadBanInfo();
        
        // 加载之前保存的玩家位置
        loadPlayerLocations();
        
        // 加载验证状态信息
        loadVerifiedStatus();
        
        // 加载验证前的状态信息
        loadPreVerificationStates();
    }

    @Override
    public void onDisable() {
        // Cancel all verification tasks
        for (VerificationTask task : verificationTasks.values()) {
            task.cancel();
        }
        // 保存封禁信息
        saveBanInfo();
        // 保存玩家位置信息
        savePlayerLocations();
        // 保存验证状态信息
        saveVerifiedStatus();
        // 保存验证前的状态信息
        savePreVerificationStates();
        getLogger().info("Simpass plugin has been disabled!");
    }

    private class SimpassCommandExecutor implements org.bukkit.command.CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (cmd.getName().equalsIgnoreCase("simpass")) {
                if (args.length >= 2 && args[0].equalsIgnoreCase("uuid")) {
                    if (!sender.hasPermission("simpass.admin") && !sender.isOp()) {
                        sender.sendMessage("§c你没有权限执行此命令！");
                        return true;
                    }
                    
                    String newUuid = args[1];
                    if (!isValidUUID(newUuid)) {
                        sender.sendMessage("§c无效的UUID格式！");
                        return true;
                    }
                    
                    setDeveloperUuid(newUuid);
                    sender.sendMessage("§a开发者UUID已设置为: " + newUuid);
                    return true;
                } else if (args.length >= 1 && args[0].equalsIgnoreCase("v1")) {
                    if (!sender.hasPermission("simpass.admin") && !sender.isOp()) {
                        sender.sendMessage("§c你没有权限执行此命令！");
                        return true;
                    }
                    
                    setVerificationLevel(1);
                    sender.sendMessage("§a验证等级已设置为: V1 (Level 1)");
                    return true;
                } else if (args.length >= 1 && args[0].equalsIgnoreCase("v2")) {
                    if (!sender.hasPermission("simpass.admin") && !sender.isOp()) {
                        sender.sendMessage("§c你没有权限执行此命令！");
                        return true;
                    }
                    
                    setVerificationLevel(2);
                    sender.sendMessage("§a验证等级已设置为: V2 (Level 2)");
                    return true;
                } else if (args.length >= 2 && args[0].equalsIgnoreCase("whitelist")) {
                    if (!sender.hasPermission("simpass.whitelist") && !sender.isOp()) {
                        sender.sendMessage("§c你没有权限执行此命令！");
                        return true;
                    }
                    
                    String userId = args[1];
                    try {
                        int userIdInt = Integer.parseInt(userId);
                        addToWhitelist(userIdInt);
                        sender.sendMessage("§a已将用户ID " + userIdInt + " 添加到白名单");
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§c无效的用户ID格式！");
                    }
                    return true;
                } else if (args.length >= 2 && args[0].equalsIgnoreCase("unwhitelist")) {
                    if (!sender.hasPermission("simpass.whitelist") && !sender.isOp()) {
                        sender.sendMessage("§c你没有权限执行此命令！");
                        return true;
                    }
                    
                    String userId = args[1];
                    try {
                        int userIdInt = Integer.parseInt(userId);
                        removeFromWhitelist(userIdInt);
                        sender.sendMessage("§a已将用户ID " + userIdInt + " 从白名单移除");
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§c无效的用户ID格式！");
                    }
                    return true;
                } else if (args.length >= 2 && args[0].equalsIgnoreCase("time")) {
                    if (!sender.hasPermission("simpass.admin") && !sender.isOp()) {
                        sender.sendMessage("§c你没有权限执行此命令！");
                        return true;
                    }
                    
                    try {
                        int hours = Integer.parseInt(args[1]);
                        if (hours < 0) {
                            sender.sendMessage("§c时间不能为负数！");
                            return true;
                        }
                        setReauthHours(hours);
                        sender.sendMessage("§a重新验证时间已设置为: " + hours + " 小时");
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§c无效的时间格式！请输入数字。");
                    }
                    return true;
                } else if (args[0].equalsIgnoreCase("retime")) {
                    if (!sender.hasPermission("simpass.admin") && !sender.isOp()) {
                        sender.sendMessage("§c你没有权限执行此命令！");
                        return true;
                    }
                    
                    // 让所有验证过的玩家重新验证
                    retimeAllVerifiedPlayers(sender);
                    sender.sendMessage("§a已重置所有验证玩家的验证时间，他们将需要重新验证！");
                    return true;
                } else if (args.length >= 1 && args[0].equalsIgnoreCase("help")) {
                    showHelp(sender);
                    return true;
                } else {
                    showHelp(sender);
                    return true;
                }
            }
            return false;
        }
    }
    
    private class AuthCommandExecutor implements org.bukkit.command.CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (cmd.getName().equalsIgnoreCase("auth")) {
                if (args.length >= 2) {
                    String userId = args[0];
                    String verifyCode = args[1];
                    
                    // 执行手动验证
                    if (sender instanceof Player) {
                        Player player = (Player) sender;
                        manualVerification(player, userId, verifyCode);
                    } else {
                        sender.sendMessage("§c此命令只能由玩家执行！");
                        return true;
                    }
                    return true;
                } else {
                    sender.sendMessage("§c使用方法: §f/auth <uid> <验证码>");
                    sender.sendMessage("§7或者: §f/simpass auth <uid> <验证码>");
                    return true;
                }
            } else if (cmd.getName().equalsIgnoreCase("simpass")) {
                // 处理 /simpass auth 命令
                if (args.length >= 3 && args[0].equalsIgnoreCase("auth")) {
                    String userId = args[1];
                    String verifyCode = args[2];
                    
                    // 执行手动验证
                    if (sender instanceof Player) {
                        Player player = (Player) sender;
                        manualVerification(player, userId, verifyCode);
                    } else {
                        sender.sendMessage("§c此命令只能由玩家执行！");
                        return true;
                    }
                    return true;
                }
            }
            return false;
        }
    }
    
    private void manualVerification(Player player, String userId, String verifyCode) {
        try {
            // 检查是否有开发者UUID
            if (developerUuid.isEmpty()) {
                player.sendMessage("§c服务器尚未配置验证系统，请联系管理员！");
                return;
            }
            
            // 调用验证API
            String apiUrl = "https://pass.simpfun.cn/api/dev/auth?uuid=" + URLEncoder.encode(developerUuid, StandardCharsets.UTF_8) +
                           "&user_id=" + URLEncoder.encode(userId, StandardCharsets.UTF_8) +
                           "&verify_code=" + URLEncoder.encode(verifyCode, StandardCharsets.UTF_8);
            
            String response = sendHttpRequest(apiUrl);
            JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();
            
            if (jsonResponse.has("code") && jsonResponse.get("code").getAsInt() == 200) {
                if (jsonResponse.has("user_info")) {
                    JsonObject userInfo = jsonResponse.getAsJsonObject("user_info");
                    int returnedUserId = userInfo.get("simpass_uid").getAsInt();
                    int userLevel = 1; // 默认等级为1
                    
                    // 检查用户等级
                    if (userInfo.has("level")) {
                        userLevel = userInfo.get("level").getAsInt();
                    }
                    
                    // 检查是否在白名单中，白名单用户不受验证等级限制
                    if (!isWhitelisted(returnedUserId)) {
                        // 检查是否满足服务器的验证等级要求
                        if (userLevel < this.verificationLevel) {
                            player.sendMessage("§c您的验证等级 (" + userLevel + ") 低于服务器要求的等级 (" + this.verificationLevel + ")，无法进入服务器！");
                            player.kickPlayer("验证等级不足，无法进入服务器");
                            return;
                        }
                    } else {
                        player.sendMessage("§a白名单用户验证成功！欢迎回来！（白名单用户）");
                    }
                    
                    // 检查是否同一个玩家使用了不同的uid登录
                    if (isUserAlreadyRegisteredWithDifferentId(player.getUniqueId(), returnedUserId)) {
                        player.sendMessage("§c检测到您使用了不同的验证ID登录，已被踢出服务器！");
                        player.kickPlayer("检测到使用不同的验证ID登录");
                        return;
                    }
                    
                                            // 检查是否同一个uid被其他玩家绑定
                                            if (isUidAlreadyRegisteredByAnotherPlayer(player.getUniqueId(), returnedUserId)) {
                                                player.sendMessage("§c该验证ID已被其他账号绑定，一个ID只能绑定一个账号！（UID绑定多个人）");
                                                player.kickPlayer("该验证ID已被其他账号绑定，一个ID只能绑定一个账号！（UID绑定多个人）");
                                                return;
                                            }                    
                    // 保存验证信息
                    String playerIP = player.getAddress().getAddress().getHostAddress();
                    saveVerifiedUser(player.getUniqueId(), returnedUserId, player.getName(), userLevel, playerIP);
                    
                    if (!isWhitelisted(returnedUserId)) {
                        player.sendMessage("§a手动验证成功！欢迎回来！您的验证等级为: " + userLevel);
                    }
                    
                    // 恢复玩家背包
                    restorePlayerInventory(player);
                    
                    // 在主线程中恢复玩家原始游戏模式和位置
                    Bukkit.getScheduler().runTask(this, () -> {
                        // 恢复玩家原始游戏模式
                        org.bukkit.GameMode originalMode = originalGameModes.get(player.getUniqueId());
                        if (originalMode != null) {
                            player.setGameMode(originalMode);
                        }
                        
                        // 恢复玩家之前的位置，如果存在的话
                        org.bukkit.Location previousLocation = playerLocations.get(player.getUniqueId());
                        if (previousLocation != null) {
                            // 确保位置在同一个世界
                            if (previousLocation.getWorld().equals(player.getWorld())) {
                                player.teleport(previousLocation);
                                player.sendMessage("§a已恢复您之前的位置！");
                            }
                        }
                        
                        // 恢复玩家背包
                        restorePlayerInventory(player);
                        
                        // 设置玩家验证状态为已验证
                        playerVerifiedStatus.put(player.getUniqueId(), 2);
                        
                        // 保存验证后的状态，以便在服务器重启后恢复
                        savePlayerInventory(player);
                        originalGameModes.put(player.getUniqueId(), player.getGameMode());
                        playerLocations.put(player.getUniqueId(), player.getLocation());
                        
                        // 解冻玩家
                        movementListener.unfreezePlayer(player);
                    });
                } else {
                    player.sendMessage("§c验证信息不完整！");
                }
            } else {
                String errorMsg = jsonResponse.has("msg") ? jsonResponse.get("msg").getAsString() : "验证失败";
                player.sendMessage("§c验证失败: " + errorMsg);
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "手动验证时出错", e);
            player.sendMessage("§c手动验证时发生错误: " + e.getMessage());
        }
    }
    
    private void showHelp(CommandSender sender) {
        sender.sendMessage("§6=================================");
        sender.sendMessage("§eSimpass 验证插件 帮助菜单");
        sender.sendMessage("§6=================================");
        sender.sendMessage("§a/simpass uuid <UUID> §7- 设置开发者UUID");
        sender.sendMessage("§a/simpass v1 §7- 设置验证等级为V1 (Level 1)");
        sender.sendMessage("§a/simpass v2 §7- 设置验证等级为V2 (Level 2)");
        sender.sendMessage("§a/simpass time <小时数> §7- 设置重新验证时间（小时）");
        sender.sendMessage("§a/simpass whitelist <uid> §7- 将用户添加到白名单");
        sender.sendMessage("§a/simpass unwhitelist <uid> §7- 将用户从白名单移除");
        sender.sendMessage("§a/simpass auth <uid> <验证码> §7- 手动验证");
        sender.sendMessage("§a/auth <uid> <验证码> §7- 手动验证");
        sender.sendMessage("§a/simpass help §7- 显示此帮助菜单");
        sender.sendMessage("§6=================================");
    }

    private boolean isValidUUID(String uuid) {
        try {
            UUID.fromString(uuid);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void setDeveloperUuid(String uuid) {
        try {
            // 直接存储开发者UUID，不加密
            this.developerUuid = uuid;
            saveDeveloperUuid();
            getLogger().info("开发者UUID已更新: " + uuid);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "设置开发者UUID时出错", e);
        }
    }

    private void setVerificationLevel(int level) {
        try {
            this.verificationLevel = level;
            saveDeveloperUuid();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "设置验证等级时出错", e);
        }
    }

    private void setReauthHours(int hours) {
        try {
            this.reauthHours = hours;
            saveDeveloperUuid();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "设置重新验证时间时出错", e);
        }
    }

    private void saveDeveloperUuid() {
        try {
            java.io.File dataFile = new java.io.File(getDataFolder(), "config.txt");
            if (!dataFile.getParentFile().exists()) {
                dataFile.getParentFile().mkdirs();
            }
            
            // 保存开发者UUID、验证等级和重新验证时间 - 不再加密
            String configData = developerUuid + ":" + verificationLevel + ":" + reauthHours;
            java.nio.file.Files.write(dataFile.toPath(), configData.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "保存开发者UUID时出错", e);
        }
    }

    private void loadDeveloperUuid() {
        try {
            java.io.File dataFile = new java.io.File(getDataFolder(), "config.txt");
            if (dataFile.exists()) {
                String content = new String(java.nio.file.Files.readAllBytes(dataFile.toPath()), StandardCharsets.UTF_8);
                String[] parts = content.split(":");
                if (parts.length >= 1) {
                    // 直接加载开发者UUID，不加密
                    this.developerUuid = parts[0];
                }
                if (parts.length >= 2) {
                    try {
                        this.verificationLevel = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException e) {
                        // 如果解析失败，使用默认值
                        this.verificationLevel = 2;
                    }
                }
                if (parts.length >= 3) {
                    try {
                        this.reauthHours = Integer.parseInt(parts[2]);
                    } catch (NumberFormatException e) {
                        // 如果解析失败，使用默认值
                        this.reauthHours = 24;
                    }
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "加载开发者UUID时出错", e);
        }
        
        // 加载白名单
        loadWhitelist();
    }
    
    private void loadWhitelist() {
        try {
            java.io.File whitelistFile = new java.io.File(getDataFolder(), "whitelist.txt");
            if (whitelistFile.exists()) {
                List<String> lines = java.nio.file.Files.readAllLines(whitelistFile.toPath(), StandardCharsets.UTF_8);
                for (String line : lines) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        try {
                            int userId = Integer.parseInt(line);
                            whitelist.add(userId);
                        } catch (NumberFormatException e) {
                            getLogger().warning("白名单中存在无效的用户ID: " + line);
                        }
                    }
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "加载白名单时出错", e);
        }
    }
    
    private void saveWhitelist() {
        try {
            java.io.File whitelistFile = new java.io.File(getDataFolder(), "whitelist.txt");
            if (!whitelistFile.getParentFile().exists()) {
                whitelistFile.getParentFile().mkdirs();
            }
            
            // 将白名单写入文件
            java.nio.file.Files.write(
                whitelistFile.toPath(),
                whitelist.stream().map(String::valueOf).toList(),
                StandardCharsets.UTF_8
            );
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "保存白名单时出错", e);
        }
    }
    
    private void addToWhitelist(int userId) {
        whitelist.add(userId);
        saveWhitelist();
    }
    
    private void removeFromWhitelist(int userId) {
        whitelist.remove(userId);
        saveWhitelist();
    }
    
    private boolean isWhitelisted(int userId) {
        return whitelist.contains(userId);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        
        // 检查玩家是否被封禁
        // 从用户文件中获取UID
        int playerUid = 0;
        try {
            java.io.File userFile = new java.io.File(getDataFolder(), "users.txt");
            if (userFile.exists()) {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(userFile.toPath(), StandardCharsets.UTF_8);
                for (String line : lines) {
                    String[] parts = line.split(":");
                    if (parts.length >= 4 && UUID.fromString(parts[0]).equals(playerUuid)) {
                        playerUid = Integer.parseInt(parts[1]);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "检查封禁状态时获取玩家UID出错", e);
        }
        
        if (isPlayerBanned(playerUuid, playerUid)) {
            BanInfo banInfo = bannedPlayers.get(playerUuid);
            if (banInfo == null) {
                // 如果玩家UUID没被封禁，检查UID是否被封禁
                for (Map.Entry<Integer, BanInfo> entry : bannedUids.entrySet()) {
                    if (entry.getKey() == playerUid) {
                        banInfo = entry.getValue();
                        break;
                    }
                }
            }
            
            if (banInfo != null) {
                player.kickPlayer("您已被封禁！\n原因: " + banInfo.getReason() + 
                                 "\n封禁时间: " + banInfo.getBanTimeFormatted() + 
                                 "\n封禁者: " + banInfo.getBannedBy());
                return;
            }
        }
        
        // 保存玩家当前的背包状态（验证前状态）
        savePlayerInventory(player);
        
        // 保存玩家原始游戏模式（验证前状态）
        org.bukkit.GameMode currentGameMode = player.getGameMode();
        originalGameModes.put(playerUuid, currentGameMode);
        preVerificationGameModes.put(playerUuid, currentGameMode);
        
        // 保存验证前的背包内容
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = new ItemStack[inventory.getContents().length];
        for (int i = 0; i < inventory.getContents().length; i++) {
            ItemStack item = inventory.getContents()[i];
            contents[i] = item != null ? item.clone() : null;
        }
        preVerificationInventories.put(playerUuid, contents);
        
        // 获取玩家IP地址
        String playerIP = player.getAddress().getAddress().getHostAddress();
        
        // 获取玩家之前的位置，如果存在的话
        org.bukkit.Location previousLocation = playerLocations.get(playerUuid);
        
        // Check if player is in the verified users list with sufficient level and time hasn't expired
        if (isPlayerVerifiedOnJoin(playerUuid, playerIP)) {
            // 设置玩家验证状态
            playerVerifiedStatus.put(playerUuid, 2); // 2表示已验证
            
            // 恢复玩家背包
            restorePlayerInventory(player);
            player.sendMessage("§a身份验证已通过，欢迎回来！");
            
            // 恢复玩家原始游戏模式
            org.bukkit.GameMode originalMode = originalGameModes.get(playerUuid);
            if (originalMode != null) {
                player.setGameMode(originalMode);
            }
            
            // 恢复玩家之前的位置，如果存在的话
            if (previousLocation != null) {
                // 确保位置在同一个世界
                if (previousLocation.getWorld().equals(player.getWorld())) {
                    player.teleport(previousLocation);
                    player.sendMessage("§a已恢复您之前的位置！");
                }
            }
            
            // 恢复玩家背包
            restorePlayerInventory(player);
            return;
        } else {
            // 检查玩家是否之前已经登录过但未完成验证
            if (previousLocation != null) {
                // 玩家之前已经登录过，恢复其位置，但保持验证前的状态
                if (previousLocation.getWorld().equals(player.getWorld())) {
                    player.teleport(previousLocation);
                    player.sendMessage("§a已恢复您之前的位置！");
                }
                
                // 恢复玩家验证前的原始游戏模式（如果已保存）
                org.bukkit.GameMode preVerificationMode = preVerificationGameModes.get(playerUuid);
                if (preVerificationMode != null) {
                    player.setGameMode(preVerificationMode);
                } else {
                    // 如果没有保存的原始模式，则设置为冒险模式
                    player.setGameMode(org.bukkit.GameMode.ADVENTURE);
                }
                
                // 恢复玩家验证前的背包内容（如果已保存）
                ItemStack[] preVerificationInventory = preVerificationInventories.get(playerUuid);
                if (preVerificationInventory != null) {
                    player.getInventory().setContents(preVerificationInventory);
                    player.updateInventory();
                } else {
                    // 如果没有保存的原始背包，则清空背包并给予验证二维码
                    player.getInventory().clear();
                    player.updateInventory();
                }
            } else {
                // 这是玩家首次登录，设置验证前的状态
                // 如果玩家未验证，则清空背包并强制设置为冒险模式
                player.getInventory().clear();
                player.updateInventory();
                
                // 强制切换为冒险模式
                player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            }
            
            // 设置玩家验证状态
            playerVerifiedStatus.put(playerUuid, 0); // 0表示未验证
            
            // 给予验证二维码
            if (developerUuid.isEmpty()) {
                player.sendMessage("§c服务器尚未配置验证系统，请联系管理员！");
            } else {
                // 开始验证过程
                startPlayerVerification(player);
            }
        }
        
        // 传送玩家到世界出生点
        org.bukkit.Location spawnLocation = player.getWorld().getSpawnLocation();
        player.teleport(spawnLocation);
        player.sendMessage("§e请用微信小程序简幻通扫码进行验证");
        player.sendMessage("§7或使用 §f/auth <uid> <验证码> §7进行手动验证");
        movementListener.freezePlayer(player);
        
        // Check if we have a developer UUID set
        if (developerUuid.isEmpty()) {
            player.sendMessage("§c服务器尚未配置验证系统，请联系管理员！");
            movementListener.unfreezePlayer(player);
            // 恢复玩家背包
            restorePlayerInventory(player);
            return;
        }
        
        // Start verification process
        startPlayerVerification(player);
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        
        // 只有在验证成功后（状态为2）才保存玩家完整状态
        if (playerVerifiedStatus.getOrDefault(playerUuid, 0) == 2) {
            // 保存玩家退出时的位置
            playerLocations.put(playerUuid, player.getLocation());
            // 保存玩家背包内容
            savePlayerInventory(player);
            // 保存玩家当前游戏模式
            originalGameModes.put(playerUuid, player.getGameMode());
        } else {
            // 验证中途退出（状态为0或1），不保存完整状态，但移除可能存在的临时保存数据
            // 注意：这里不保存任何状态，但保留必要清理操作
        }
        
        // 移除玩家背包缓存
        playerInventories.remove(playerUuid);
        
        // 移除验证前的状态缓存
        preVerificationGameModes.remove(playerUuid);
        preVerificationInventories.remove(playerUuid);
        
        // 移除玩家验证状态
        playerVerifiedStatus.remove(playerUuid);
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        
        // 检查玩家是否已验证，如果没有验证则取消操作
        if (!isPlayerVerified(playerUuid) && !movementListener.isPlayerFrozen(player)) {
            // 如果玩家未验证但没有被冻结（例如在验证过程中），则冻结玩家
            movementListener.freezePlayer(player);
            player.sendMessage("§c请先完成身份验证后才能进行游戏！");
        } else if (!isPlayerVerified(playerUuid) && movementListener.isPlayerFrozen(player)) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        
        // 检查玩家是否已验证，如果没有验证则取消操作
        if (!isPlayerVerified(playerUuid) && !movementListener.isPlayerFrozen(player)) {
            movementListener.freezePlayer(player);
            player.sendMessage("§c请先完成身份验证后才能进行游戏！");
        } else if (!isPlayerVerified(playerUuid) && movementListener.isPlayerFrozen(player)) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            UUID playerUuid = player.getUniqueId();
            
            // 检查玩家是否已验证，如果没有验证则取消操作
            if (!isPlayerVerified(playerUuid) && !movementListener.isPlayerFrozen(player)) {
                movementListener.freezePlayer(player);
                player.sendMessage("§c请先完成身份验证后才能进行游戏！");
            } else if (!isPlayerVerified(playerUuid) && movementListener.isPlayerFrozen(player)) {
                event.setCancelled(true);
            }
        }
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        
        // 检查玩家是否已验证，如果没有验证则取消操作（除了使用/auth命令的情况）
        if (!isPlayerVerified(playerUuid) && !movementListener.isPlayerFrozen(player)) {
            movementListener.freezePlayer(player);
            player.sendMessage("§c请先完成身份验证后才能进行游戏！");
        } else if (!isPlayerVerified(playerUuid) && movementListener.isPlayerFrozen(player)) {
            // 允许与特定物品交互（如获取验证二维码），但阻止其他交互
            if (event.getClickedBlock() != null) {
                event.setCancelled(true);
            }
        }
    }
    
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // 检查是否是玩家受到攻击
        if (event.getEntity() instanceof Player) {
            Player damagedPlayer = (Player) event.getEntity();
            UUID playerUuid = damagedPlayer.getUniqueId();
            
            // 检查被攻击的玩家是否已验证，如果没有验证则取消攻击
            if (!isPlayerVerified(playerUuid)) {
                event.setCancelled(true);
                
                // 如果攻击者是玩家，也发送提示信息
                if (event.getDamager() instanceof Player) {
                    Player attacker = (Player) event.getDamager();
                    attacker.sendMessage("§c该玩家尚未完成身份验证，无法被攻击！");
                }
            }
        }
        // 检查攻击者是否是未验证的玩家
        else if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            UUID playerUuid = attacker.getUniqueId();
            
            // 如果攻击者未验证，则取消攻击
            if (!isPlayerVerified(playerUuid)) {
                event.setCancelled(true);
                attacker.sendMessage("§c请先完成身份验证后才能进行攻击！");
            }
        }
    }

    private void startPlayerVerification(Player player) {
        UUID playerUuid = player.getUniqueId();
        
        // Cancel any existing verification task for this player
        if (verificationTasks.containsKey(playerUuid)) {
            verificationTasks.get(playerUuid).cancel();
        }
        
        // 清空玩家背包
        player.getInventory().clear();
        player.updateInventory();
        
        // Create new verification task
        VerificationTask task = new VerificationTask(player);
        verificationTasks.put(playerUuid, task);
        
        // Run the task immediately
        task.run();
    }

    private class VerificationTask implements Runnable {
        private final Player player;
        private final org.bukkit.scheduler.BukkitTask task;
        private String otpId;
        private int attempts = 0;
        private static final int MAX_ATTEMPTS = 60; // 60 seconds max
        private boolean cancelled = false;

        public VerificationTask(Player player) {
            // Schedule the task to run every second (20 ticks)
            this.task = Bukkit.getScheduler().runTaskTimerAsynchronously(this.getPlugin(), this, 0L, 20L);
            this.player = player;
        }

        @Override
        public void run() {
            if (cancelled) {
                return;
            }
            
            if (attempts == 0) {
                // First run - get OTP ID
                getOtpId();
            } else {
                // Subsequent runs - check OTP status
                checkOtpStatus();
            }

            attempts++;
            
            // Cancel if max attempts reached
            if (attempts >= MAX_ATTEMPTS) {
                cancel();
                player.sendMessage("§c身份验证超时，请重新加入服务器！");
                // 恢复玩家背包
                Simpass.this.restorePlayerInventory(player);
                // 踢出玩家 - 需要在主线程中执行
                Bukkit.getScheduler().runTask(Simpass.this, () -> {
                    player.kickPlayer("身份验证超时，请重新加入服务器！");
                });
            }
        }

        private void getOtpId() {
            try {
                String apiUrl = "https://pass.simpfun.cn/api/dev/otp?uuid=" + developerUuid;
                
                String response = sendHttpRequest(apiUrl);
                JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();
                
                if (jsonResponse.has("otp_id")) {
                    this.otpId = jsonResponse.get("otp_id").getAsString();
                    showQrCodeToPlayer(player, this.otpId);
                } else {
                    player.sendMessage("§c获取验证信息失败，请联系管理员！");
                    cancel();
                    // 恢复玩家背包
                    Simpass.this.restorePlayerInventory(player);
                    // 踢出玩家 - 需要在主线程中执行
                    Bukkit.getScheduler().runTask(Simpass.this, () -> {
                        player.kickPlayer("获取验证信息失败，请联系管理员！");
                    });
                }
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "获取OTP ID时出错", e);
                player.sendMessage("§c获取验证ID时发生错误！");
                cancel();
                // 恢复玩家背包
                Simpass.this.restorePlayerInventory(player);
                // 踢出玩家 - 需要在主线程中执行
                Bukkit.getScheduler().runTask(Simpass.this, () -> {
                    player.kickPlayer("获取验证ID时发生错误！");
                });
            }
        }

        private void checkOtpStatus() {
            if (this.otpId == null) {
                return;
            }
            
            try {
                String apiUrl = "https://pass.simpfun.cn/api/dev/otp?otp_id=" + otpId;
                
                String response = sendHttpRequest(apiUrl);
                JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();
                
                if (jsonResponse.has("status") && jsonResponse.get("status").getAsString().equals("verified")) {
                    // Verification successful
                    if (jsonResponse.has("user_info")) {
                        JsonObject userInfo = jsonResponse.getAsJsonObject("user_info");
                        int userId = userInfo.get("simpass_uid").getAsInt();
                        int userLevel = 1; // 默认等级为1
                        
                        // 检查用户等级
                        if (userInfo.has("level")) {
                            userLevel = userInfo.get("level").getAsInt();
                        }
                        
                        // 检查是否满足服务器的验证等级要求
                        if (userLevel < Simpass.this.verificationLevel) {
                            player.sendMessage("§c您的验证等级 (" + userLevel + ") 低于服务器要求的等级 (" + Simpass.this.verificationLevel + ")，无法进入服务器！");
                            player.kickPlayer("验证等级不足，无法进入服务器");
                            cancel();
                            return;
                        }
                        
                        // 检查是否同一个玩家使用了不同的uid登录
                        if (isUserAlreadyRegisteredWithDifferentId(player.getUniqueId(), userId)) {
                            player.sendMessage("§c检测到您使用了不同的验证ID登录，已被踢出服务器！");
                            player.kickPlayer("检测到使用不同的验证ID登录");
                            cancel();
                            return;
                        }
                        
                        // 检查是否同一个uid被其他玩家绑定
                        if (isUidAlreadyRegisteredByAnotherPlayer(player.getUniqueId(), userId)) {
                            player.sendMessage("§c该验证ID已被其他账号绑定，一个ID只能绑定一个账号！（UID绑定多个人）");
                            player.kickPlayer("该验证ID已被其他账号绑定，一个ID只能绑定一个账号！（UID绑定多个人）");
                            cancel();
                            return;
                        }
                        
                        // 保存用户信息
                        String playerIP = player.getAddress().getAddress().getHostAddress();
                        saveVerifiedUser(player.getUniqueId(), userId, player.getName(), userLevel, playerIP);
                        
                        // Cancel the task and allow player to move
                        UUID playerUUID = player.getUniqueId();
                        int finalUserLevel = userLevel;
                        cancel();
                        // 在主线程中执行后续操作
                        Bukkit.getScheduler().runTask(Simpass.this, () -> {
                            Player currentPlayer = Bukkit.getPlayer(playerUUID);
                            if (currentPlayer != null) {
                                currentPlayer.sendMessage("§a身份验证成功！欢迎回来！您的验证等级为: " + finalUserLevel);
                                
                                // 恢复玩家背包
                                Simpass.this.restorePlayerInventory(currentPlayer);
                                
                                // 恢复玩家原始游戏模式
                                org.bukkit.GameMode originalMode = Simpass.this.originalGameModes.get(currentPlayer.getUniqueId());
                                if (originalMode != null) {
                                    currentPlayer.setGameMode(originalMode);
                                }
                                
                                // 恢复玩家之前的位置，如果存在的话
                                org.bukkit.Location previousLocation = Simpass.this.playerLocations.get(currentPlayer.getUniqueId());
                                if (previousLocation != null) {
                                    // 确保位置在同一个世界
                                    if (previousLocation.getWorld().equals(currentPlayer.getWorld())) {
                                        currentPlayer.teleport(previousLocation);
                                        currentPlayer.sendMessage("§a已恢复您之前的位置！");
                                    }
                                }
                                
                                // 恢复玩家背包
                                Simpass.this.restorePlayerInventory(currentPlayer);
                                
                                // 在主线程中保存验证后的状态（游戏模式、背包等）
                                Bukkit.getScheduler().runTask(Simpass.this, () -> {
                                    // 设置玩家验证状态为已验证
                                    Simpass.this.playerVerifiedStatus.put(currentPlayer.getUniqueId(), 2);
                                    
                                    // 保存验证后的状态，以便在服务器重启后恢复
                                    Simpass.this.savePlayerInventory(currentPlayer);
                                    Simpass.this.originalGameModes.put(currentPlayer.getUniqueId(), currentPlayer.getGameMode());
                                    Simpass.this.playerLocations.put(currentPlayer.getUniqueId(), currentPlayer.getLocation());
                                });
                                
                                // Unfreeze the player so they can move
                                Simpass.this.movementListener.unfreezePlayer(currentPlayer);
                            }
                        });
                    } else {
                        player.sendMessage("§c验证信息不完整！");
                        cancel();
                        // 恢复玩家背包
                        Simpass.this.restorePlayerInventory(player);
                        // 踢出玩家 - 需要在主线程中执行
                        Bukkit.getScheduler().runTask(Simpass.this, () -> {
                            player.kickPlayer("验证信息不完整！");
                        });
                    }
                } else {
                    // Still waiting for verification - no message needed as it was already shown on join
                }
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "检查OTP状态时出错", e);
                player.sendMessage("§c检查验证状态时发生错误！");
                // 恢复玩家背包
                Simpass.this.restorePlayerInventory(player);
                // 踢出玩家 - 需要在主线程中执行
                Bukkit.getScheduler().runTask(Simpass.this, () -> {
                    player.kickPlayer("检查验证状态时发生错误！");
                });
            }
        }

        public void cancel() {
            if (!cancelled) {
                cancelled = true;
                if (task != null && !task.isCancelled()) {
                    task.cancel();
                }
                verificationTasks.remove(player.getUniqueId());
            }
        }

        public JavaPlugin getPlugin() {
            return Simpass.this;
        }
    }

    private void showQrCodeToPlayer(Player player, String otpId) {
        try {
            // 使用外部API生成二维码图片，设置适合地图的尺寸
            String qrUrl = "https://uapis.cn/api/v1/image/qrcode?text=" + URLEncoder.encode("https://pass.simpfun.cn/api/otp?otp_id=" + otpId, StandardCharsets.UTF_8) + "&size=128";
            
            // 使用内部功能下载并显示二维码
            try {
                java.awt.image.BufferedImage image = org.simple.simpass.image.ImageDownloader.downloadImage(qrUrl);
                
                // 直接创建一个地图物品并添加到玩家背包
                ItemStack mapItem = generateQrCodeMap(player, qrUrl);
                
                if (mapItem != null) {
                    player.getInventory().addItem(mapItem);
                    player.sendMessage("§a已将验证二维码放入您的物品栏，请查看！");
                    
                    // 同时提供链接作为备用
                    String originalQrUrl = "https://pass.simpfun.cn/api/otp?otp_id=" + otpId;
                    player.sendMessage("§7或者复制以下链接到微信完成认证: §f§n" + originalQrUrl);
                } else {
                    player.sendMessage("§c生成二维码时出现错误！");
                }
                
            } catch (Exception e) {
                // 如果内部功能失败，回退到原来的实现
                getLogger().warning("使用内部二维码功能失败: " + e.getMessage());
                ItemStack mapItem = generateQrCodeMap(player, qrUrl);
                
                if (mapItem != null) {
                    player.getInventory().addItem(mapItem);
                    player.sendMessage("§a已将验证二维码放入您的物品栏，请查看！");
                    player.sendMessage("§7或者复制以下链接到微信完成认证: §f§nhttps://pass.simpfun.cn/api/otp?otp_id=" + otpId);
                } else {
                    player.sendMessage("§c生成二维码时出现错误！");
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "生成二维码时出错", e);
            player.sendMessage("§c生成二维码时出现错误！");
        }
    }

    private ItemStack generateQrCodeMap(Player player, String url) {
        try {
            // Create a map item for QR code
            ItemStack map = new ItemStack(Material.FILLED_MAP);
            MapMeta meta = (MapMeta) map.getItemMeta();
            
            // Create a map view
            MapView mapView = Bukkit.createMap(player.getWorld());
            // 设置地图名称为"身份验证二维码"，用绿色显示
            meta.setDisplayName("§a身份验证二维码");
            meta.setMapView(mapView);
            
            // 设置地图缩放级别
            mapView.setScale(org.bukkit.map.MapView.Scale.NORMAL);
            
            // 尝试从URL下载二维码图像并应用到地图
            try {
                java.awt.image.BufferedImage qrImage = org.simple.simpass.image.ImageDownloader.downloadImage(url);
                
                // 添加一个渲染器来显示二维码
                mapView.getRenderers().clear(); // 清除默认渲染器
                mapView.addRenderer(new org.bukkit.map.MapRenderer() {
                    private boolean rendered = false;
                    
                    @Override
                    public void render(MapView mapView, org.bukkit.map.MapCanvas canvas, Player player) {
                        if (!rendered && qrImage != null) {
                            // 将BufferedImage转换为地图像素
                            byte[] pixels = org.simple.simpass.image.SimpleMapHelper.getPixels(qrImage, true); // 二维码模式
                            
                            // 将像素数据应用到地图画布
                            int idx = 0;
                            for (int y = 0; y < 128 && idx < pixels.length; y++) {
                                for (int x = 0; x < 128 && idx < pixels.length; x++) {
                                    canvas.setPixel(x, y, pixels[idx]);
                                    idx++;
                                }
                            }
                            
                            rendered = true;
                        }
                    }
                });
            } catch (Exception e) {
                getLogger().warning("无法下载或应用二维码图像: " + e.getMessage());
                
                // 如果无法应用二维码，使用基本的渲染器
                mapView.getRenderers().clear();
                mapView.addRenderer(new org.bukkit.map.MapRenderer() {
                    private boolean rendered = false;
                    
                    @Override
                    public void render(MapView mapView, org.bukkit.map.MapCanvas canvas, Player player) {
                        if (!rendered) {
                            // 使用黑白像素绘制一个简化的二维码框架
                            byte black = 0;      // 黑色
                            byte white = 127;    // 白色
                            
                            // 清空整个画布为白色
                            for (int x = 0; x < 128; x++) {
                                for (int y = 0; y < 128; y++) {
                                    canvas.setPixel(x, y, white);
                                }
                            }
                            
                            // 绘制二维码的三个角落定位标记
                            drawSquare(canvas, 0, 0, 8, black);   // 左上角
                            drawSquare(canvas, 120, 0, 8, black); // 右上角
                            drawSquare(canvas, 0, 120, 8, black); // 左下角
                            
                            rendered = true;
                        }
                    }
                });
            }
            
            map.setItemMeta(meta);
            
            return map;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "生成地图时出错", e);
            return null;
        }
    }
    
    private void drawSquare(org.bukkit.map.MapCanvas canvas, int x, int y, int size, byte color) {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (x + i >= 0 && x + i < 128 && y + j >= 0 && y + j < 128) {
                    canvas.setPixel(x + i, y + j, color);
                }
            }
        }
    }

    private void saveVerifiedUser(UUID playerUuid, int userId, String playerName, int level, String ipAddress) {
        try {
            java.io.File userFile = new java.io.File(getDataFolder(), "users.txt");
            if (!userFile.getParentFile().exists()) {
                userFile.getParentFile().mkdirs();
            }
            
            // 添加IP地址记录 - 不再加密用户信息
            long currentTime = System.currentTimeMillis();
            String userInfo = playerUuid.toString() + ":" + userId + ":" + playerName + ":" + level + ":" + currentTime + ":" + ipAddress;
            
            // Append to file
            java.nio.file.Files.write(
                userFile.toPath(), 
                (userInfo + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE, 
                java.nio.file.StandardOpenOption.APPEND
            );
            
            // 记录玩家最后验证时间
            lastVerificationTime.put(playerUuid, currentTime);
            
            // 记录玩家IP地址
            playerIPs.put(playerUuid, ipAddress);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "保存用户信息时出错", e);
        }
    }
    
    // 重载方法，保持向后兼容
    private void saveVerifiedUser(UUID playerUuid, int userId, String playerName, int level) {
        // 获取玩家IP地址
        Player player = Bukkit.getPlayer(playerUuid);
        String ipAddress = player != null ? player.getAddress().getAddress().getHostAddress() : "unknown";
        saveVerifiedUser(playerUuid, userId, playerName, level, ipAddress);
    }

    private boolean isPlayerVerified(UUID playerUuid, String currentIP) {
        // Check if player is in the verified users list
        try {
            java.io.File userFile = new java.io.File(getDataFolder(), "users.txt");
            if (userFile.exists()) {
                List<String> lines = java.nio.file.Files.readAllLines(userFile.toPath(), StandardCharsets.UTF_8);
                for (String line : lines) {
                    String[] parts = line.split(":");
                    if (parts.length >= 6) { // 现在有6个字段: UUID:userId:playerName:level:time:ip
                        UUID existingPlayerUuid = UUID.fromString(parts[0]);
                        int userLevel = Integer.parseInt(parts[3]);
                        String storedIP = parts[5]; // IP地址在第6个位置（索引为5）
                        
                        if (existingPlayerUuid.equals(playerUuid)) {
                            // 检查验证等级是否满足要求
                            if (userLevel >= this.verificationLevel) {
                                // 对于在线玩家，不再检查是否超过重新验证时间
                                // 这样已登录的用户即使验证时间到期也可以继续游戏
                                // 只有在重新加入服务器时才会检查时间
                                
                                // 检查IP地址是否匹配（可选，根据安全策略决定）
                                if (!storedIP.equals(currentIP)) {
                                    return false; // IP地址不匹配，需要重新验证
                                }
                                
                                return true;
                            } else {
                                return false; // 等级不足
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "检查用户验证状态时出错", e);
        }
        return false;
    }
    
    // 专门用于玩家加入时的验证方法，会检查时间是否过期
    private boolean isPlayerVerifiedOnJoin(UUID playerUuid, String currentIP) {
        // Check if player is in the verified users list
        try {
            java.io.File userFile = new java.io.File(getDataFolder(), "users.txt");
            if (userFile.exists()) {
                List<String> lines = java.nio.file.Files.readAllLines(userFile.toPath(), StandardCharsets.UTF_8);
                for (String line : lines) {
                    String[] parts = line.split(":");
                    if (parts.length >= 6) { // 现在有6个字段: UUID:userId:playerName:level:time:ip
                        UUID existingPlayerUuid = UUID.fromString(parts[0]);
                        int userLevel = Integer.parseInt(parts[3]);
                        String storedIP = parts[5]; // IP地址在第6个位置（索引为5）
                        
                        if (existingPlayerUuid.equals(playerUuid)) {
                            // 检查验证等级是否满足要求
                            if (userLevel >= this.verificationLevel) {
                                // 加入时需要检查是否超过重新验证时间
                                if (isReauthRequired(playerUuid)) {
                                    return false; // 需要重新验证
                                }
                                
                                // 检查IP地址是否匹配
                                if (!storedIP.equals(currentIP)) {
                                    return false; // IP地址不匹配，需要重新验证
                                }
                                
                                return true;
                            } else {
                                return false; // 等级不足
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "检查用户验证状态时出错", e);
        }
        return false;
    }
    
    // 重载方法，保持向后兼容
    private boolean isPlayerVerified(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        String currentIP = player != null ? player.getAddress().getAddress().getHostAddress() : "unknown";
        return isPlayerVerified(playerUuid, currentIP);
    }

    private boolean isUserAlreadyRegisteredWithDifferentId(UUID playerUuid, int userId) {
        // Check if the same player has already registered with a different userId
        try {
            java.io.File userFile = new java.io.File(getDataFolder(), "users.txt");
            if (userFile.exists()) {
                List<String> lines = java.nio.file.Files.readAllLines(userFile.toPath(), StandardCharsets.UTF_8);
                for (String line : lines) {
                    String[] parts = line.split(":");
                    if (parts.length >= 3) {
                        UUID existingPlayerUuid = UUID.fromString(parts[0]);
                        int existingUserId = Integer.parseInt(parts[1]);
                        
                        if (existingPlayerUuid.equals(playerUuid) && existingUserId != userId) {
                            // Same player but different userId
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "检查用户ID冲突时出错", e);
        }
        return false;
    }
    
    /**
     * 检查是否同一个uid被多个玩家账号绑定
     */
    private boolean isUidAlreadyRegisteredByAnotherPlayer(UUID currentPlayerUuid, int userId) {
        try {
            java.io.File userFile = new java.io.File(getDataFolder(), "users.txt");
            if (userFile.exists()) {
                List<String> lines = java.nio.file.Files.readAllLines(userFile.toPath(), StandardCharsets.UTF_8);
                for (String line : lines) {
                    String[] parts = line.split(":");
                    if (parts.length >= 3) {
                        UUID existingPlayerUuid = UUID.fromString(parts[0]);
                        int existingUserId = Integer.parseInt(parts[1]);
                        
                        // 检查是否同一个uid被不同的玩家UUID使用
                        if (existingUserId == userId && !existingPlayerUuid.equals(currentPlayerUuid)) {
                            return true; // 同一个uid被另一个玩家绑定了
                        }
                    }
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "检查UID绑定冲突时出错", e);
        }
        return false;
    }
    
    private boolean isReauthRequired(UUID playerUuid) {
        // 检查是否需要重新验证
        // 如果reauthHours为0，表示永不强制重新验证
        if (reauthHours == 0) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastVerificationTime.get(playerUuid);
        
        if (lastTime == null) {
            // 如果没有记录，则检查用户文件中的时间戳
            try {
                java.io.File userFile = new java.io.File(getDataFolder(), "users.txt");
                if (userFile.exists()) {
                    List<String> lines = java.nio.file.Files.readAllLines(userFile.toPath(), StandardCharsets.UTF_8);
                    for (String line : lines) {
                        String[] parts = line.split(":");
                        if (parts.length >= 6) {
                            UUID existingPlayerUuid = UUID.fromString(parts[0]);
                            if (existingPlayerUuid.equals(playerUuid)) {
                                try {
                                    long storedTime = Long.parseLong(parts[4]); // 时间戳在第5个位置（索引为4）
                                    lastTime = storedTime;
                                    lastVerificationTime.put(playerUuid, storedTime);
                                    break;
                                } catch (NumberFormatException e) {
                                    // 如果解析时间戳失败，忽略
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "检查重新验证要求时出错", e);
            }
        }
        
        if (lastTime != null) {
            // 计算小时数差异
            long diffHours = (currentTime - lastTime) / (1000 * 60 * 60); // 毫秒转小时
            return diffHours >= reauthHours;
        }
        
        // 如果没有找到时间记录，假设需要验证
        return true;
    }
    
    private void retimeAllVerifiedPlayers(CommandSender sender) {
        // 重置所有验证玩家的验证时间，使他们需要重新验证
        lastVerificationTime.clear();
        
        // 同时踢出当前在线的已验证玩家，让他们重新验证
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerUuid = player.getUniqueId();
            
            // 检查玩家是否已验证（在管理命令下，即使在线也应检查时间）
            String playerIP = player.getAddress().getAddress().getHostAddress();
            if (isPlayerVerifiedOnJoin(playerUuid, playerIP)) {
                // 更新玩家验证状态为未验证
                playerVerifiedStatus.put(playerUuid, 0);
                
                // 保存玩家原始游戏模式
                originalGameModes.put(playerUuid, player.getGameMode());
                
                // 清空玩家背包
                player.getInventory().clear();
                player.updateInventory();
                
                // 强制切换为冒险模式
                player.setGameMode(org.bukkit.GameMode.ADVENTURE);
                
                // 解冻玩家（以防被冻结）
                movementListener.unfreezePlayer(player);
                
                player.sendMessage("§c管理员已重置验证时间，您需要重新验证！");
                
                // 开始验证过程
                startPlayerVerification(player);
            }
        }
    }
    
    private void savePlayerInventory(Player player) {
        try {
            PlayerInventory inventory = player.getInventory();
            // 保存玩家背包内容的深拷贝
            ItemStack[] contents = new ItemStack[inventory.getContents().length];
            for (int i = 0; i < inventory.getContents().length; i++) {
                ItemStack item = inventory.getContents()[i];
                contents[i] = item != null ? item.clone() : null;
            }
            playerInventories.put(player.getUniqueId(), contents);
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "保存玩家背包时出错", e);
        }
    }
    
    private void restorePlayerInventory(Player player) {
        try {
            UUID playerUuid = player.getUniqueId();
            if (playerInventories.containsKey(playerUuid)) {
                ItemStack[] savedContents = playerInventories.get(playerUuid);
                if (savedContents != null) {
                    // 恢复玩家背包
                    player.getInventory().setContents(savedContents);
                    player.updateInventory();
                    // 移除缓存
                    playerInventories.remove(playerUuid);
                    player.sendMessage("§a玩家背包已恢复");
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "恢复玩家背包时出错", e);
        }
    }

    private String sendHttpRequest(String urlString) throws Exception {
        // 根据URL智能选择请求方法：手动验证使用POST，其他使用GET
        boolean usePost = urlString.contains("/api/dev/auth");
        
        URL url = new URL(urlString);
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
        
        if (usePost) {
            // 对于手动验证API使用POST请求
            // 解析URL参数并转换为POST请求体
            String baseUrl = urlString.split("\\?")[0];
            String query = urlString.contains("?") ? urlString.split("\\?", 2)[1] : "";
            
            connection.setRequestMethod("POST");
            connection.setRequestProperty("User-Agent", "Simpass-Plugin/1.0");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000); // 10秒连接超时
            connection.setReadTimeout(10000);    // 10秒读取超时
            
            // 发送POST请求体
            try (java.io.OutputStream os = connection.getOutputStream()) {
                byte[] input = query.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
        } else {
            // 对于其他API使用GET请求
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Simpass-Plugin/1.0");
            connection.setConnectTimeout(10000); // 10秒连接超时
            connection.setReadTimeout(10000);    // 10秒读取超时
        }
        
        int responseCode = connection.getResponseCode();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            
            return response.toString();
        } catch (java.io.IOException e) {
            // 如果获取输入流失败，尝试获取错误流
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                
                // 记录错误响应以便调试，但不显示完整的URL（包含开发者UUID）
                String baseUrl = urlString.split("\\?")[0];
                String sanitizedUrl = baseUrl + "?[参数已隐藏]?";
                getLogger().warning("API请求失败，响应码: " + responseCode + ", 响应内容: " + response.toString());
                throw new java.io.IOException("Server returned HTTP response code: " + responseCode + " for URL: " + sanitizedUrl + ", Response: " + response.toString());
            }
        }
    }
    
    // Image Command Executor for handling /image url command
    private class ImageCommandExecutor implements org.bukkit.command.CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (cmd.getName().equalsIgnoreCase("image")) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c此命令只能由玩家执行！");
                    return true;
                }
                
                Player player = (Player) sender;
                
                if (args.length < 2 || !args[0].equalsIgnoreCase("url")) {
                    player.sendMessage("§c使用方法: §f/image url <image_url>");
                    return true;
                }
                
                String imageUrl = args[1];
                
                // 验证URL格式
                if (!isValidUrl(imageUrl)) {
                    player.sendMessage("§c无效的URL格式！");
                    return true;
                }
                
                // 在异步线程中处理图像下载和处理
                Bukkit.getScheduler().runTaskAsynchronously(this.getPlugin(), () -> {
                    try {
                        player.sendMessage("§a正在下载图像...");
                        
                        // 从URL下载图像
                        java.awt.image.BufferedImage originalImage = org.simple.simpass.image.ImageDownloader.downloadImage(imageUrl);
                        
                        if (originalImage == null) {
                            // 在主线程中发送错误消息
                            Bukkit.getScheduler().runTask(this.getPlugin(), () -> {
                                player.sendMessage("§c无法下载图像，请检查URL是否有效！");
                            });
                            return;
                        }
                        
                        // 自动缩放图像以适应地图尺寸
                        java.awt.image.BufferedImage scaledImage = org.simple.simpass.image.ImageScaler.scaleImageToFitMap(originalImage);
                        
                        // 在主线程中创建地图物品
                        Bukkit.getScheduler().runTask(this.getPlugin(), () -> {
                            try {
                                ItemStack mapItem = createMapFromImage(player, scaledImage);
                                
                                if (mapItem != null) {
                                    player.getInventory().addItem(mapItem);
                                    player.sendMessage("§a图像已成功生成为地图，并放入您的物品栏！");
                                } else {
                                    player.sendMessage("§c生成地图时出现错误！");
                                }
                            } catch (Exception e) {
                                getLogger().log(Level.SEVERE, "创建地图时出错", e);
                                player.sendMessage("§c创建地图时出现错误: " + e.getMessage());
                            }
                        });
                        
                    } catch (Exception e) {
                        // 在主线程中发送错误消息
                        Bukkit.getScheduler().runTask(this.getPlugin(), () -> {
                            getLogger().log(Level.SEVERE, "处理图像时出错", e);
                            player.sendMessage("§c处理图像时出现错误: " + e.getMessage());
                        });
                    }
                });
                
                return true;
            }
            return false;
        }
        
        private boolean isValidUrl(String url) {
            try {
                new java.net.URL(url);
                String lowerUrl = url.toLowerCase();
                return lowerUrl.startsWith("http://") || 
                       lowerUrl.startsWith("https://") ||
                       lowerUrl.endsWith(".png") || 
                       lowerUrl.endsWith(".jpg") || 
                       lowerUrl.endsWith(".jpeg") ||
                       lowerUrl.endsWith(".gif") ||
                       lowerUrl.contains("image") ||
                       lowerUrl.contains("img");
            } catch (java.net.MalformedURLException e) {
                return false;
            }
        }
        
        private ItemStack createMapFromImage(Player player, java.awt.image.BufferedImage image) {
            try {
                // Create a map item for the image
                ItemStack map = new ItemStack(Material.FILLED_MAP);
                MapMeta meta = (MapMeta) map.getItemMeta();
                
                // Create a map view
                MapView mapView = Bukkit.createMap(player.getWorld());
                meta.setMapView(mapView);
                
                // 设置地图名称为"自定义图像"，用蓝色显示
                meta.setDisplayName("§9自定义图像");
                
                // 尝试将BufferedImage转换为地图像素
                try {
                    // 将图像转换为地图像素
                    byte[] pixels = org.simple.simpass.image.SimpleMapHelper.getPixels(image, false); // 彩色图片模式
                    
                    // 添加一个渲染器来显示图像
                    mapView.getRenderers().clear(); // 清除默认渲染器
                    mapView.addRenderer(new org.bukkit.map.MapRenderer() {
                        private boolean rendered = false;
                        
                        @Override
                        public void render(MapView mapView, org.bukkit.map.MapCanvas canvas, Player player) {
                            if (!rendered) {
                                // 将像素数据应用到地图画布
                                int width = Math.min(image.getWidth(), 128);
                                int height = Math.min(image.getHeight(), 128);
                                
                                for (int y = 0; y < height && y < 128; y++) {
                                    for (int x = 0; x < width && x < 128; x++) {
                                        canvas.setPixel(x, y, pixels[y * 128 + x]);
                                    }
                                }
                                
                                rendered = true;
                            }
                        }
                    });
                } catch (Exception e) {
                    getLogger().warning("无法应用图像到地图: " + e.getMessage());
                    
                    // 如果无法应用图像，使用基本的渲染器
                    mapView.getRenderers().clear();
                    mapView.addRenderer(new org.bukkit.map.MapRenderer() {
                        private boolean rendered = false;
                        
                        @Override
                        public void render(MapView mapView, org.bukkit.map.MapCanvas canvas, Player player) {
                            if (!rendered) {
                                // 使用黑白像素绘制一个简单的占位符
                                byte black = 0;      // 黑色
                                byte white = 127;    // 白色
                                
                                // 清空整个画布为白色
                                for (int x = 0; x < 128; x++) {
                                    for (int y = 0; y < 128; y++) {
                                        canvas.setPixel(x, y, white);
                                    }
                                }
                                
                                rendered = true;
                            }
                        }
                    });
                }
                
                map.setItemMeta(meta);
                
                return map;
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "生成地图时出错", e);
                return null;
            }
        }
        
        private JavaPlugin getPlugin() {
            return Simpass.this;
        }
    }
    
    // 新增的Simpban命令执行器
    private class SimpbanCommandExecutor implements org.bukkit.command.CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (cmd.getName().equalsIgnoreCase("simpban")) {
                if (args.length < 1) {
                    sender.sendMessage("§c使用方法: §f/simpban <玩家名> [原因]");
                    return true;
                }
                
                if (!sender.hasPermission("simpass.ban") && !sender.isOp()) {
                    sender.sendMessage("§c你没有权限执行此命令！");
                    return true;
                }
                
                String playerName = args[0];
                String reason = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "违反服务器规定";
                
                // 检查是否在验证用户列表中找到该玩家
                int uid = getPlayerUidFromUsersFile(playerName);
                if (uid <= 0) {
                    // 如果没找到，尝试从在线玩家中获取
                    Player targetPlayer = Bukkit.getPlayer(playerName);
                    if (targetPlayer != null) {
                        // 对于未验证但在线的玩家，我们仍然可以封禁，但没有UID
                        uid = 0;
                    } else {
                        sender.sendMessage("§c找不到玩家: " + playerName);
                        return true;
                    }
                }
                
                // 执行封禁
                banPlayer(playerName, uid, reason, sender.getName());
                
                if (uid > 0) {
                    sender.sendMessage("§a玩家 " + playerName + " (UID: " + uid + ") 已被封禁！\n原因: " + reason);
                } else {
                    sender.sendMessage("§a玩家 " + playerName + " 已被封禁！\n原因: " + reason);
                }
                
                // 记录到日志
                getLogger().info("玩家 " + playerName + " 被 " + sender.getName() + " 封禁，原因: " + reason);
                
                return true;
            }
            return false;
        }
        
        private int getPlayerUidFromUsersFile(String playerName) {
            try {
                java.io.File userFile = new java.io.File(getDataFolder(), "users.txt");
                if (userFile.exists()) {
                    java.util.List<String> lines = java.nio.file.Files.readAllLines(userFile.toPath(), StandardCharsets.UTF_8);
                    for (String line : lines) {
                        String[] parts = line.split(":");
                        if (parts.length >= 4) {
                            String name = parts[2]; // 玩家名称在第3个位置（索引为2）
                            if (name.equalsIgnoreCase(playerName)) {
                                return Integer.parseInt(parts[1]); // UID在第2个位置（索引为1）
                            }
                        }
                    }
                }
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "从用户文件获取UID时出错", e);
            }
            return 0;
        }
    }
    
    // 新增的Unsimpban命令执行器
    private class UnsimpbanCommandExecutor implements org.bukkit.command.CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (cmd.getName().equalsIgnoreCase("unsimpban")) {
                if (args.length != 1) {
                    sender.sendMessage("§c使用方法: §f/unsimpban <玩家名>");
                    return true;
                }
                
                if (!sender.hasPermission("simpass.ban") && !sender.isOp()) {
                    sender.sendMessage("§c你没有权限执行此命令！");
                    return true;
                }
                
                String playerName = args[0];
                
                // 检查玩家是否被封禁
                BanInfo banInfo = getPlayerBanInfo(playerName);
                if (banInfo == null) {
                    sender.sendMessage("§c玩家 " + playerName + " 没有被封禁！");
                    return true;
                }
                
                // 执行解封
                unbanPlayer(playerName);
                
                sender.sendMessage("§a玩家 " + playerName + " 已被解封！");
                
                // 记录到日志
                getLogger().info("玩家 " + playerName + " 被 " + sender.getName() + " 解封");
                
                return true;
            }
            return false;
        }
    }
    
    // 封禁信息类
    private static class BanInfo {
        private final String playerName;
        private final String reason;
        private final long banTime;
        private final String bannedBy;
        
        public BanInfo(String playerName, String reason, String bannedBy) {
            this.playerName = playerName;
            this.reason = reason;
            this.banTime = System.currentTimeMillis();
            this.bannedBy = bannedBy;
        }
        
        // 用于从文件加载时创建对象
        public BanInfo(String playerName, String reason, String bannedBy, long banTime) {
            this.playerName = playerName;
            this.reason = reason;
            this.banTime = banTime;
            this.bannedBy = bannedBy;
        }
        
        public String getPlayerName() {
            return playerName;
        }
        
        public String getReason() {
            return reason;
        }
        
        public long getBanTime() {
            return banTime;
        }
        
        public String getBannedBy() {
            return bannedBy;
        }
        
        public String getBanTimeFormatted() {
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(banTime));
        }
    }
    
    // 封禁相关方法
    private void banPlayer(String playerName, int uid, String reason, String bannedBy) {
        // 获取玩家UUID（如果在线）
        Player player = Bukkit.getPlayer(playerName);
        UUID playerUuid = null;
        if (player != null) {
            playerUuid = player.getUniqueId();
        } else {
            // 尝试从用户文件中获取UUID
            playerUuid = getPlayerUuidFromUsersFile(playerName);
        }
        
        BanInfo banInfo = new BanInfo(playerName, reason, bannedBy);
        
        // 封禁玩家
        if (playerUuid != null) {
            bannedPlayers.put(playerUuid, banInfo);
        }
        
        // 封禁UID
        if (uid > 0) {
            bannedUids.put(uid, banInfo);
        }
        
        // 如果玩家在线，踢出玩家
        if (player != null) {
            player.kickPlayer("您已被封禁！\n原因: " + reason + "\n封禁时间: " + banInfo.getBanTimeFormatted() + "\n封禁者: " + bannedBy);
        }
        
        // 保存封禁信息到文件
        saveBanInfo();
    }
    
    private void unbanPlayer(String playerName) {
        // 从封禁列表中移除
        UUID playerUuid = getPlayerUuidFromUsersFile(playerName);
        if (playerUuid != null) {
            bannedPlayers.remove(playerUuid);
        }
        
        // 遍历查找并移除玩家名匹配的封禁记录（通过遍历bannedUids中的BanInfo）
        for (Map.Entry<Integer, BanInfo> entry : new java.util.ArrayList<>(bannedUids.entrySet())) {
            if (entry.getValue().getPlayerName().equalsIgnoreCase(playerName)) {
                bannedUids.remove(entry.getKey());
            }
        }
        
        // 保存封禁信息到文件
        saveBanInfo();
    }
    
    private BanInfo getPlayerBanInfo(String playerName) {
        // 检查玩家UUID是否被封禁
        UUID playerUuid = getPlayerUuidFromUsersFile(playerName);
        if (playerUuid != null && bannedPlayers.containsKey(playerUuid)) {
            return bannedPlayers.get(playerUuid);
        }
        
        // 遍历封禁的UID，查找匹配的玩家名
        for (BanInfo banInfo : bannedUids.values()) {
            if (banInfo.getPlayerName().equalsIgnoreCase(playerName)) {
                return banInfo;
            }
        }
        
        return null;
    }
    
    private boolean isPlayerBanned(UUID playerUuid, int uid) {
        return bannedPlayers.containsKey(playerUuid) || bannedUids.containsKey(uid);
    }
    
    private UUID getPlayerUuidFromUsersFile(String playerName) {
        try {
            java.io.File userFile = new java.io.File(getDataFolder(), "users.txt");
            if (userFile.exists()) {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(userFile.toPath(), StandardCharsets.UTF_8);
                for (String line : lines) {
                    String[] parts = line.split(":");
                    if (parts.length >= 3) {
                        String name = parts[2]; // 玩家名称在第3个位置
                        if (name.equalsIgnoreCase(playerName)) {
                            return UUID.fromString(parts[0]); // UUID在第1个位置
                        }
                    }
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "从用户文件获取UUID时出错", e);
        }
        return null;
    }
    
    private void saveBanInfo() {
        try {
            java.io.File banFile = new java.io.File(getDataFolder(), "bans.txt");
            if (!banFile.getParentFile().exists()) {
                banFile.getParentFile().mkdirs();
            }
            
            java.util.List<String> lines = new java.util.ArrayList<>();
            
            // 保存被封禁的玩家UUID
            for (Map.Entry<UUID, BanInfo> entry : bannedPlayers.entrySet()) {
                BanInfo info = entry.getValue();
                String line = "PLAYER:" + entry.getKey().toString() + ":" + info.getPlayerName() + ":" + 
                             info.getReason() + ":" + info.getBannedBy() + ":" + info.getBanTime();
                lines.add(line);
            }
            
            // 保存被封禁的UID
            for (Map.Entry<Integer, BanInfo> entry : bannedUids.entrySet()) {
                BanInfo info = entry.getValue();
                String line = "UID:" + entry.getKey() + ":" + info.getPlayerName() + ":" + 
                             info.getReason() + ":" + info.getBannedBy() + ":" + info.getBanTime();
                lines.add(line);
            }
            
            java.nio.file.Files.write(banFile.toPath(), lines, StandardCharsets.UTF_8);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "保存封禁信息时出错", e);
        }
    }
    
    private void loadBanInfo() {
        try {
            java.io.File banFile = new java.io.File(getDataFolder(), "bans.txt");
            if (banFile.exists()) {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(banFile.toPath(), StandardCharsets.UTF_8);
                for (String line : lines) {
                    String[] parts = line.split(":", 6); // 最多分成6部分
                    if (parts.length >= 6) {
                        String type = parts[0];
                        if ("PLAYER".equals(type)) {
                            UUID playerUuid = UUID.fromString(parts[1]);
                            String playerName = parts[2];
                            String reason = parts[3];
                            String bannedBy = parts[4];
                            long banTime = Long.parseLong(parts[5]);
                            
                            bannedPlayers.put(playerUuid, new BanInfo(playerName, reason, bannedBy, banTime));
                        } else if ("UID".equals(type)) {
                            int uid = Integer.parseInt(parts[1]);
                            String playerName = parts[2];
                            String reason = parts[3];
                            String bannedBy = parts[4];
                            long banTime = Long.parseLong(parts[5]);
                            
                            bannedUids.put(uid, new BanInfo(playerName, reason, bannedBy, banTime));
                        }
                    }
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "加载封禁信息时出错", e);
        }
    }
    
    private void savePlayerLocations() {
        try {
            java.io.File locationFile = new java.io.File(getDataFolder(), "player_locations.txt");
            if (!locationFile.getParentFile().exists()) {
                locationFile.getParentFile().mkdirs();
            }
            
            java.util.List<String> lines = new java.util.ArrayList<>();
            
            // 保存所有玩家的位置信息
            for (Map.Entry<UUID, org.bukkit.Location> entry : playerLocations.entrySet()) {
                org.bukkit.Location location = entry.getValue();
                String line = entry.getKey().toString() + ":" + 
                             location.getWorld().getName() + ":" + 
                             location.getX() + ":" + 
                             location.getY() + ":" + 
                             location.getZ() + ":" + 
                             location.getYaw() + ":" + 
                             location.getPitch();
                lines.add(line);
            }
            
            java.nio.file.Files.write(locationFile.toPath(), lines, StandardCharsets.UTF_8);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "保存玩家位置时出错", e);
        }
    }
    
    private void loadPlayerLocations() {
        try {
            java.io.File locationFile = new java.io.File(getDataFolder(), "player_locations.txt");
            if (locationFile.exists()) {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(locationFile.toPath(), StandardCharsets.UTF_8);
                for (String line : lines) {
                    String[] parts = line.split(":", 7); // 最多分成7部分
                    if (parts.length >= 7) {
                        UUID playerUuid = UUID.fromString(parts[0]);
                        String worldName = parts[1];
                        double x = Double.parseDouble(parts[2]);
                        double y = Double.parseDouble(parts[3]);
                        double z = Double.parseDouble(parts[4]);
                        float yaw = Float.parseFloat(parts[5]);
                        float pitch = Float.parseFloat(parts[6]);
                        
                        // 获取世界对象
                        org.bukkit.World world = Bukkit.getWorld(worldName);
                        if (world != null) {
                            org.bukkit.Location location = new org.bukkit.Location(world, x, y, z, yaw, pitch);
                            playerLocations.put(playerUuid, location);
                        }
                    }
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "加载玩家位置时出错", e);
        }
    }
    
    private void saveVerifiedStatus() {
        try {
            java.io.File statusFile = new java.io.File(getDataFolder(), "player_verified_status.txt");
            if (!statusFile.getParentFile().exists()) {
                statusFile.getParentFile().mkdirs();
            }
            
            java.util.List<String> lines = new java.util.ArrayList<>();
            
            // 保存所有玩家的验证状态
            for (Map.Entry<UUID, Integer> entry : playerVerifiedStatus.entrySet()) {
                String line = entry.getKey().toString() + ":" + entry.getValue();
                lines.add(line);
            }
            
            java.nio.file.Files.write(statusFile.toPath(), lines, StandardCharsets.UTF_8);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "保存玩家验证状态时出错", e);
        }
    }
    
    private void loadVerifiedStatus() {
        try {
            java.io.File statusFile = new java.io.File(getDataFolder(), "player_verified_status.txt");
            if (statusFile.exists()) {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(statusFile.toPath(), StandardCharsets.UTF_8);
                for (String line : lines) {
                    String[] parts = line.split(":", 2); // 分成2部分
                    if (parts.length >= 2) {
                        UUID playerUuid = UUID.fromString(parts[0]);
                        int verifiedStatus = Integer.parseInt(parts[1]);
                        playerVerifiedStatus.put(playerUuid, verifiedStatus);
                    }
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "加载玩家验证状态时出错", e);
        }
    }
    
    // 添加保存和加载验证前状态的方法
    private void savePreVerificationStates() {
        try {
            java.io.File stateFile = new java.io.File(getDataFolder(), "pre_verification_states.txt");
            if (!stateFile.getParentFile().exists()) {
                stateFile.getParentFile().mkdirs();
            }
            
            java.util.List<String> lines = new java.util.ArrayList<>();
            
            // 保存所有玩家验证前的游戏模式
            for (Map.Entry<UUID, org.bukkit.GameMode> entry : preVerificationGameModes.entrySet()) {
                String line = "GAMEMODE:" + entry.getKey().toString() + ":" + entry.getValue().name();
                lines.add(line);
            }
            
            // 由于物品栈不能直接序列化，我们只保存基本概念，实际物品栏在验证前的状态主要依靠验证流程管理
            // 这个功能更多是通过在onPlayerJoin时保存状态来实现的
            
            java.nio.file.Files.write(stateFile.toPath(), lines, StandardCharsets.UTF_8);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "保存验证前状态时出错", e);
        }
    }
    
    private void loadPreVerificationStates() {
        try {
            java.io.File stateFile = new java.io.File(getDataFolder(), "pre_verification_states.txt");
            if (stateFile.exists()) {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(stateFile.toPath(), StandardCharsets.UTF_8);
                for (String line : lines) {
                    String[] parts = line.split(":", 3); // 分成最多3部分
                    if (parts.length >= 3) {
                        String type = parts[0];
                        UUID playerUuid = UUID.fromString(parts[1]);
                        
                        if ("GAMEMODE".equals(type)) {
                            try {
                                org.bukkit.GameMode gameMode = org.bukkit.GameMode.valueOf(parts[2]);
                                preVerificationGameModes.put(playerUuid, gameMode);
                            } catch (IllegalArgumentException e) {
                                getLogger().warning("无效的游戏模式: " + parts[2]);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "加载验证前状态时出错", e);
        }
    }
}