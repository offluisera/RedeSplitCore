package org.redesplit.github.offluisera.redesplitcore.managers;

import com.cryptomorin.xseries.XSound;
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
     * Carrega XP do banco de dados (ASSÍNCRONO)
     * Use este quando já tiver o SplitPlayer criado
     */
    public void loadXP(SplitPlayer sp) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            loadXPSync(sp);
        });
    }

    /**
     * Carrega XP do banco de dados (SÍNCRONO)
     * Use apenas quando já estiver em thread assíncrona
     */
    public void loadXPSync(SplitPlayer sp) {
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

                plugin.getLogger().info("§a[XP] Carregado para " + sp.getName() + ": " + xp + " XP (Nível " + level + ")");
            } else {
                // Cria registro inicial
                createXPRecord(sp);
                plugin.getLogger().info("§e[XP] Criado registro inicial para " + sp.getName());
            }

            rs.close();
            ps.close();

        } catch (SQLException e) {
            plugin.getLogger().severe("§c[XP] Erro ao carregar XP de " + sp.getName());
            e.printStackTrace();
        }
    }

    /**
     * Cria registro inicial de XP (já deve estar em thread assíncrona)
     */
    private void createXPRecord(SplitPlayer sp) {
        try (Connection conn = plugin.getMySQL().getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO rs_player_xp (uuid, player_name, xp, level) VALUES (?, ?, 0, 1)"
            );
            ps.setString(1, sp.getUuid().toString());
            ps.setString(2, sp.getName());
            ps.executeUpdate();
            ps.close();

            // Define valores padrão
            sp.setXp(0);
            sp.setLevel(1);

        } catch (SQLException e) {
            plugin.getLogger().severe("§c[XP] Erro ao criar registro para " + sp.getName());
            e.printStackTrace();
        }
    }

    /**
     * Salva XP no banco de dados
     */
    public void saveXP(SplitPlayer sp) {
        if (sp == null) return;

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
                ps.close();

                plugin.getLogger().info("§a[XP] Salvo para " + sp.getName() + ": " + sp.getXp() + " XP (Nível " + sp.getLevel() + ")");

            } catch (SQLException e) {
                plugin.getLogger().severe("§c[XP] Erro ao salvar XP de " + sp.getName());
                e.printStackTrace();
            }
        });
    }

    /**
     * Adiciona XP a um jogador
     */
    public void addXP(Player player, long amount, String reason) {
        SplitPlayer sp = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (sp == null) {
            player.sendMessage("§cErro: Seus dados não foram carregados!");
            return;
        }

        int oldLevel = sp.getLevel();
        sp.addXp(amount);
        int newLevel = sp.getLevel();

        // Verifica se subiu de nível
        if (newLevel > oldLevel) {
            player.sendMessage("");
            player.sendMessage("§6§l⬆ VOCÊ SUBIU DE NÍVEL!");
            player.sendMessage("§e  Nível: §f" + oldLevel + " §7→ §a" + newLevel);
            player.sendMessage("§e  XP Total: §f" + String.format("%,d", sp.getXp()));
            player.sendMessage("");

            // Som de level up usando XSound
            XSound.ENTITY_PLAYER_LEVELUP.play(player, 1.0f, 1.0f);

            // Efeito visual (partículas)
            try {
                player.getWorld().spawnParticle(
                        org.bukkit.Particle.VILLAGER_HAPPY,
                        player.getLocation().add(0, 1, 0),
                        20, 0.5, 0.5, 0.5, 0.1
                );
            } catch (Exception e) {
                // Fallback 1.8
                player.playEffect(player.getLocation(), org.bukkit.Effect.HAPPY_VILLAGER, null);
            }
        } else {
            player.sendMessage("§a+ §e" + String.format("%,d", amount) + " XP §7(" + reason + ")");
        }

        // Atualiza XP visual
        updateXPBar(player, sp);

        // Salva no banco
        saveXP(sp);

        // Log no histórico
        logXP(sp, amount, reason, "SISTEMA");
    }

    /**
     * Remove XP de um jogador
     */
    public void removeXP(Player player, long amount, String reason) {
        SplitPlayer sp = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (sp == null) {
            player.sendMessage("§cErro: Seus dados não foram carregados!");
            return;
        }

        sp.removeXp(amount);
        player.sendMessage("§c- §e" + String.format("%,d", amount) + " XP §7(" + reason + ")");

        updateXPBar(player, sp);
        saveXP(sp);
        logXP(sp, -amount, reason, "SISTEMA");
    }

    /**
     * Define XP de um jogador (comando admin)
     */
    public void setXP(Player player, long amount, String operator) {
        SplitPlayer sp = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (sp == null) {
            player.sendMessage("§cErro: Dados do jogador não foram carregados!");
            return;
        }

        sp.setXp(amount);
        player.sendMessage("§e§lXP DEFINIDO!");
        player.sendMessage("§e  XP: §f" + String.format("%,d", amount));
        player.sendMessage("§e  Nível: §f" + sp.getLevel());

        updateXPBar(player, sp);
        saveXP(sp);
        logXP(sp, amount, "XP definido por " + operator, operator);
    }

    /**
     * Atualiza a barra de XP visual do jogador
     */
    public void updateXPBar(Player player, SplitPlayer sp) {
        if (player == null || !player.isOnline() || sp == null) return;

        try {
            // Calcula progresso para próximo nível
            double progress = sp.getProgressToNextLevel() / 100.0;

            // Define level visual
            player.setLevel(sp.getLevel());

            // Define progresso visual (0.0 a 1.0)
            player.setExp((float) Math.min(1.0, Math.max(0.0, progress)));

        } catch (Exception e) {
            plugin.getLogger().warning("§c[XP] Erro ao atualizar barra de XP de " + player.getName());
        }
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
                ps.close();
            } catch (SQLException e) {
                plugin.getLogger().warning("§c[XP] Erro ao registrar log de XP");
                e.printStackTrace();
            }
        });
    }

    /**
     * Retorna o símbolo do nível
     */
    public static String getLevelBadge(int level) {
        if (level >= 21) {
            return "§d§l[" + level + " ✦]"; // Níveis 21+: Rosa/Magenta brilhante
        } else if (level >= 11) {
            return "§b[" + level + " ✦]"; // Níveis 11-20: Azul com estrela diamante
        } else if (level >= 1) {
            return "§e[" + level + " ★]"; // Níveis 1-10: Amarelo com estrela
        }
        return "§7[" + level + "]";
    }
}