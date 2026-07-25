package com.mohistmc.rankplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class Main extends JavaPlugin implements Listener, org.bukkit.command.CommandExecutor, org.bukkit.command.TabCompleter {

    private File rankFile;
    private FileConfiguration rankConfig;
    private final Map<String, String> playerRanks = new HashMap<>();
    private final Map<String, RankDef> rankDefs = new LinkedHashMap<>();
    private int effectTaskId = -1;

    private static final ChatColor[] RAINBOW = {
        ChatColor.RED, ChatColor.GOLD, ChatColor.YELLOW, ChatColor.GREEN,
        ChatColor.AQUA, ChatColor.BLUE, ChatColor.LIGHT_PURPLE
    };

    @Override
    public void onEnable() {
        loadRankConfig();
        loadPlayerData();
        for (Player p : Bukkit.getOnlinePlayers()) applyRank(p);
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("rank")).setExecutor(this);
        Objects.requireNonNull(getCommand("rank")).setTabCompleter(this);
        startEffectTask();
        getLogger().info("RankPlugin enabled!");
    }

    @Override
    public void onDisable() {
        savePlayerData();
        if (effectTaskId != -1) Bukkit.getScheduler().cancelTask(effectTaskId);
        cleanupTeams();
    }

    private void cleanupTeams() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team t : new ArrayList<>(board.getTeams())) {
            if (t.getName().startsWith("r-")) t.unregister();
        }
    }

    private void loadRankConfig() {
        rankFile = new File(getDataFolder(), "rank.yml");
        if (!rankFile.exists()) saveResource("rank.yml", false);
        rankConfig = YamlConfiguration.loadConfiguration(rankFile);
        rankDefs.clear();
        ConfigurationSection rs = rankConfig.getConfigurationSection("ranks");
        if (rs == null) return;
        for (String key : rs.getKeys(false)) {
            ConfigurationSection s = rs.getConfigurationSection(key);
            if (s == null) continue;
            rankDefs.put(key, new RankDef(
                s.getString("prefix", ""),
                s.getString("suffix", ""),
                s.getString("chat", "&7"),
                s.getBoolean("rainbow", false),
                s.getBoolean("blink", false),
                s.getString("blinkColor", "&f")
            ));
        }
    }

    private void loadPlayerData() {
        playerRanks.clear();
        ConfigurationSection ps = rankConfig.getConfigurationSection("players");
        if (ps == null) return;
        for (String k : ps.getKeys(false)) {
            playerRanks.put(k, ps.getString(k));
        }
    }

    public void savePlayerData() {
        rankConfig.set("players", null);
        for (Map.Entry<String, String> e : playerRanks.entrySet()) {
            rankConfig.set("players." + e.getKey(), e.getValue());
        }
        try { rankConfig.save(rankFile); } catch (IOException ex) { ex.printStackTrace(); }
    }

    /* ============================================================
     *  Team 管理 — 主计分板 + 同步到所有玩家的私有计分板
     *  这样 EconomyPlugin 等使用私有计分板的插件也能正常显示 rank
     * ============================================================ */

    /**
     * 创建/更新主计分板上的 rank Team，并同步到所有在线玩家的私有计分板
     */
    private Team createOrUpdateTeam(String teamName, String prefix, String suffix, ChatColor color) {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = main.getTeam(teamName);
        if (team == null) {
            team = main.registerNewTeam(teamName);
        }
        team.setPrefix(prefix);
        team.setSuffix(suffix);
        if (color != null) team.setColor(color);
        if (!team.hasEntry(teamName.substring(2))) {
            team.addEntry(teamName.substring(2));
        }

        // ★ 同步到所有在线玩家的私有计分板
        syncToAllPrivate(team);
        return team;
    }

    /**
     * 把主计分板上的 Team 状态同步到所有玩家的私有计分板
     */
    private void syncToAllPrivate(Team source) {
        String name = source.getName();
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Scoreboard sb = p.getScoreboard();
            if (sb == null || sb == main) continue;
            Team dest = sb.getTeam(name);
            if (dest == null) {
                try { dest = sb.registerNewTeam(name); } catch (Exception ex) { continue; }
            }
            dest.setPrefix(source.getPrefix());
            dest.setSuffix(source.getSuffix());
            dest.setColor(source.getColor());
            dest.setAllowFriendlyFire(source.allowFriendlyFire());
            dest.setCanSeeFriendlyInvisibles(source.canSeeFriendlyInvisibles());
            for (String entry : source.getEntries()) {
                if (!dest.hasEntry(entry)) dest.addEntry(entry);
            }
        }
    }

    /* ============================================================
     *  特效 & Apply
     * ============================================================ */

    private void startEffectTask() {
        int[] tick = {0};
        effectTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            int phase = ++tick[0];
            Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
            for (Player p : Bukkit.getOnlinePlayers()) {
                RankDef def = rankDefs.get(playerRanks.get(p.getName()));
                if (def == null || (!def.rainbow && !def.blink)) continue;
                Team t = main.getTeam("r-" + p.getName());
                if (t == null) continue;

                String prefix = ChatColor.translateAlternateColorCodes('&', def.prefix);
                String suffix = ChatColor.translateAlternateColorCodes('&', def.suffix);

                if (def.rainbow) {
                    ChatColor c = RAINBOW[(phase / 2) % 7];
                    t.setPrefix(c + prefix);
                    t.setSuffix(c + suffix);
                    t.setColor(c);
                } else if (def.blink) {
                    ChatColor cc = parseColor((phase % 6) < 3 ? def.suffix : def.blinkColor);
                    if (cc == null) cc = ChatColor.WHITE;
                    t.setPrefix(cc + prefix);
                    t.setSuffix(cc + suffix);
                    t.setColor(cc);
                }
                // ★ 特效更新后同步到所有私有计分板
                syncToAllPrivate(t);
            }
        }, 0L, 2L);
    }

    public void applyRank(Player p) {
        String rankName = playerRanks.get(p.getName());
        RankDef def = rankDefs.get(rankName);

        // 先清理旧 Team
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        Team old = main.getTeam("r-" + p.getName());
        if (old != null) old.unregister();

        if (def == null) {
            p.setPlayerListName(p.getName());
            // ★ 确保私有计分板上也没有旧 team
            for (Player online : Bukkit.getOnlinePlayers()) {
                Scoreboard sb = online.getScoreboard();
                if (sb == null || sb == main) continue;
                Team t = sb.getTeam("r-" + p.getName());
                if (t != null) t.unregister();
            }
            return;
        }

        String prefix = ChatColor.translateAlternateColorCodes('&', def.prefix);
        String suffix = ChatColor.translateAlternateColorCodes('&', def.suffix);
        ChatColor color = null;
        if (!def.rainbow && !def.blink) {
            color = parseColor(def.suffix);
        }

        createOrUpdateTeam("r-" + p.getName(), prefix, suffix, color);
    }

    /* ============================================================
     *  聊天显示 Rank（Mohist 兼容版）
     * ============================================================ */

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        String rankName = playerRanks.get(p.getName());
        RankDef def = rankDefs.get(rankName);
        if (def == null) return;

        // chat 字段作为完整格式模板，支持 %1$s=玩家名, %2$s=消息
        // 例: "&6[&eVIP&6] &r%1$s&7: &f%2$s"
        String chatFormat = ChatColor.translateAlternateColorCodes('&', def.chat);
        
        // 替换占位符
        String formatted = chatFormat
            .replace("%1$s", p.getDisplayName())
            .replace("%2$s", e.getMessage());
        
        // Mohist 兼容：取消原事件，手动广播完整格式消息
        e.setCancelled(true);
        Bukkit.broadcastMessage(formatted + ChatColor.RESET);
    }

    /* ============================================================
     *  事件
     * ============================================================ */

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(this, () -> applyRank(e.getPlayer()), 5L);
    }

    private ChatColor parseColor(String s) {
        if (s == null || s.isEmpty()) return null;
        for (int i = 0; i < s.length() - 1; i++) {
            if (s.charAt(i) == '&') {
                ChatColor c = ChatColor.getByChar(s.charAt(i + 1));
                if (c != null && c.isColor()) return c;
            }
        }
        return null;
    }

    /* ============================================================
     *  命令
     * ============================================================ */

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender s, org.bukkit.command.Command c, String l, String[] a) {
        if (!s.isOp()) { s.sendMessage("§c你没有权限"); return true; }
        if (a.length == 0) {
            s.sendMessage("§6===== RankPlugin =====");
            s.sendMessage("§e/rank give <玩家> <rank名> §7- 设置rank");
            s.sendMessage("§e/rank remove <玩家> §7- 移除rank");
            s.sendMessage("§e/rank list §7- 查看所有rank");
            s.sendMessage("§e/rank reload §7- 重载配置");
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "give": {
                if (a.length < 3) { s.sendMessage("§c用法: /rank give <玩家> <rank名>"); return true; }
                Player t = Bukkit.getPlayer(a[1]);
                if (t == null) { s.sendMessage("§c玩家不在线: " + a[1]); return true; }
                if (!rankDefs.containsKey(a[2])) { s.sendMessage("§cRank不存在: " + a[2]); return true; }
                playerRanks.put(t.getName(), a[2]);
                applyRank(t);
                savePlayerData();
                s.sendMessage("§a已设置 " + t.getName() + " 为 " + a[2]);
                t.sendMessage("§a你的rank已更新为 " + a[2]);
                return true;
            }
            case "remove": {
                if (a.length < 2) { s.sendMessage("§c用法: /rank remove <玩家>"); return true; }
                Player t = Bukkit.getPlayer(a[1]);
                if (t == null) { s.sendMessage("§c玩家不在线: " + a[1]); return true; }
                playerRanks.remove(t.getName());
                applyRank(t);
                savePlayerData();
                s.sendMessage("§a已移除 " + t.getName() + " 的rank");
                t.sendMessage("§a你的rank已被移除");
                return true;
            }
            case "list": {
                s.sendMessage("§6===== Rank列表 =====");
                rankDefs.forEach((name, d) -> {
                    String p = ChatColor.translateAlternateColorCodes('&', d.prefix + "玩家名" + d.suffix);
                    s.sendMessage(" §e" + name + " §7- " + p);
                });
                return true;
            }
            case "reload": {
                if (effectTaskId != -1) Bukkit.getScheduler().cancelTask(effectTaskId);
                loadRankConfig();
                loadPlayerData();
                cleanupTeams();
                for (Player p : Bukkit.getOnlinePlayers()) applyRank(p);
                startEffectTask();
                s.sendMessage("§a配置已重载");
                return true;
            }
            default: s.sendMessage("§c未知子命令");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(org.bukkit.command.CommandSender s, org.bukkit.command.Command c, String l, String[] a) {
        if (!s.isOp()) return Collections.emptyList();
        if (a.length == 1) return Arrays.asList("give","remove","list","reload").stream()
            .filter(x -> x.startsWith(a[0].toLowerCase())).collect(Collectors.toList());
        if (a.length == 2 && (a[0].equalsIgnoreCase("give") || a[0].equalsIgnoreCase("remove")))
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(x -> x.toLowerCase().startsWith(a[1].toLowerCase())).collect(Collectors.toList());
        if (a.length == 3 && a[0].equalsIgnoreCase("give"))
            return rankDefs.keySet().stream()
                .filter(x -> x.toLowerCase().startsWith(a[2].toLowerCase())).collect(Collectors.toList());
        return Collections.emptyList();
    }

    public static class RankDef {
        public final String prefix, suffix, chat, blinkColor;
        public final boolean rainbow, blink;
        public RankDef(String prefix, String suffix, String chat, boolean rainbow, boolean blink, String blinkColor) {
            this.prefix = prefix; this.suffix = suffix; this.chat = chat;
            this.rainbow = rainbow; this.blink = blink; this.blinkColor = blinkColor;
        }
    }
}
