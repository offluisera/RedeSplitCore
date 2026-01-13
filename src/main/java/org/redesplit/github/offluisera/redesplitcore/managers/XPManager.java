package org.redesplit.github.offluisera.redesplitcore.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import org.redesplit.github.offluisera.redesplitcore.player.SplitPlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class XPManager {

    private final RedeSplitCore plugin;

    public XPManager(RedeSplitCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Carrega XP do banco de dados
     */
    public void loadXP(SplitPlayer sp) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getMySQL().getConnection()) {

                PreparedStatement ps = conn.prepareStatement(
                        "SELECT xp, level FROM rs_player_xp WHERE uuid = ?"
                );
                ps.setString(1, sp.getUuid().toString());
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    long xp = rs.getLong("xp");
                    int level = rs.getInt("level");

                    sp.setXp(xp);
                    sp.setLevel(level);
                } else {
                    // Cria registro inicial
                    createXPRecord(sp);
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Cria registro inicial de XP
     */
    private void createXPRecord(SplitPlayer sp) {
        try (Connection conn = plugin.getMySQL().getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO rs_player_xp (uuid, player_name, xp, level) VALUES (?, ?, 0, 1)"
            );
            ps.setString(1, sp.getUuid().toString());
            ps.setString(2, sp.getName());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Salva XP no banco de dados
     */
    public void saveXP(SplitPlayer sp) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getMySQL().getConnection()) {

                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO rs_player_xp (uuid, player_name, xp, level) " +
                                "VALUES (?, ?, ?, ?) " +
                                "ON DUPLICATE KEY UPDATE xp = VALUES(xp), level = VALUES(level), player_name = VALUES(player_name)"
                );
                ps.setString(1, sp.getUuid().toString());
                ps.setString(2, sp.getName());
                ps.setLong(3, sp.getXp());
                ps.setInt(4, sp.getLevel());
                ps.executeUpdate();

            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Adiciona XP a um jogador
     */
    public void addXP(Player player, long amount, String reason) {
        SplitPlayer sp = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (sp == null) return;

        int oldLevel = sp.getLevel();
        sp.addXp(amount);
        int newLevel = sp.getLevel();

        // Verifica se subiu de nível
        if (newLevel > oldLevel) {
            player.sendMessage("");
            player.sendMessage("§6§l⬆ VOCÊ SUBIU DE NÍVEL!");
            player.sendMessage("§e  Nível: §f" + oldLevel + " §7→ §a" + newLevel);
            player.sendMessage("§e  XP Total: §f" + sp.getXp());
            player.sendMessage("");

            // Som de level up
            player.playSound(player.getLocation(), "ENTITY_PLAYER_LEVELUP", 1.0f, 1.0f);
        } else {
            player.sendMessage("§a+ §e" + amount + " XP §7(" + reason + ")");
        }

        // Atualiza XP visual
        updateXPBar(player, sp);

        // Salva no banco
        saveXP(sp);

        // Log no histórico
        logXP(sp, amount, reason, "SISTEMA");

        // Publica no Redis
        publishXPUpdate(sp);
    }

    /**
     * Remove XP de um jogador
     */
    public void removeXP(Player player, long amount, String reason) {
        SplitPlayer sp = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (sp == null) return;

        sp.removeXp(amount);
        player.sendMessage("§c- §e" + amount + " XP §7(" + reason + ")");

        updateXPBar(player, sp);
        saveXP(sp);
        logXP(sp, -amount, reason, "SISTEMA");
        publishXPUpdate(sp);
    }

    /**
     * Define XP de um jogador (comando admin)
     */
    public void setXP(Player player, long amount, String operator) {
        SplitPlayer sp = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (sp == null) return;

        sp.setXp(amount);
        player.sendMessage("§e§lXP DEFINIDO!");
        player.sendMessage("§e  XP: §f" + amount);
        player.sendMessage("§e  Nível: §f" + sp.getLevel());

        updateXPBar(player, sp);
        saveXP(sp);
        logXP(sp, amount, "XP definido por " + operator, operator);
        publishXPUpdate(sp);
    }

    /**
     * Atualiza a barra de XP visual do jogador
     */
    public void updateXPBar(Player player, SplitPlayer sp) {
        // Calcula progresso para próximo nível
        double progress = sp.getProgressToNextLevel() / 100.0;

        // Define level visual
        player.setLevel(sp.getLevel());

        // Define progresso visual (0.0 a 1.0)
        player.setExp((float) Math.min(1.0, Math.max(0.0, progress)));
    }

    /**
     * Registra no histórico de XP
     */
    private void logXP(SplitPlayer sp, long amount, String reason, String operator) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getMySQL().getConnection()) {
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO rs_xp_history (uuid, player_name, xp_amount, reason, operator) " +
                                "VALUES (?, ?, ?, ?, ?)"
                );
                ps.setString(1, sp.getUuid().toString());
                ps.setString(2, sp.getName());
                ps.setLong(3, amount);
                ps.setString(4, reason);
                ps.setString(5, operator);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Publica atualização de XP no Redis
     */
    private void publishXPUpdate(SplitPlayer sp) {
        if (plugin.getRedisManager() != null) {
            String message = sp.getUuid() + ":" + sp.getXp() + ":" + sp.getLevel();
            plugin.getRedisManager().publish("xp-update", message);
        }
    }

    /**
     * Retorna o símbolo do nível
     */
    public static String getLevelBadge(int level) {
        if (level >= 11) {
            return "§b[" + level + " ✦]"; // Níveis 11-20: Azul com estrela diamante
        } else if (level >= 1) {
            return "§e[" + level + " ★]"; // Níveis 1-10: Amarelo com estrela
        }
        return "§7[" + level + "]";
    }
}