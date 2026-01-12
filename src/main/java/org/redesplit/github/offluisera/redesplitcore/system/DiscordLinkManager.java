package org.redesplit.github.offluisera.redesplitcore.system;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;

import java.sql.*;
import java.util.Random;
import java.util.UUID;

/**
 * Sistema de Vinculação Discord
 * Permite jogadores vincularem suas contas do Minecraft ao Discord
 */
public class DiscordLinkManager {

    private final RedeSplitCore plugin;

    // Tempo de expiração do código: 10 minutos
    private final long CODE_EXPIRATION_MS = 600000L;

    public DiscordLinkManager(RedeSplitCore plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    /**
     * Gera um código de verificação para o jogador
     * @return O código gerado (formato: ABC-1234)
     */
    public String generateCode(Player player) {
        UUID uuid = player.getUniqueId();
        String code = generateRandomCode();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getMySQL().getConnection()) {

                // Remove códigos antigos
                PreparedStatement delete = conn.prepareStatement(
                        "DELETE FROM rs_discord_links WHERE uuid = ? AND status = 'PENDING'"
                );
                delete.setString(1, uuid.toString());
                delete.executeUpdate();

                // Cria novo código
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO rs_discord_links (uuid, username, verification_code, code_expires_at, status) " +
                                "VALUES (?, ?, ?, DATE_ADD(NOW(), INTERVAL 10 MINUTE), 'PENDING')"
                );
                ps.setString(1, uuid.toString());
                ps.setString(2, player.getName());
                ps.setString(3, code);
                ps.executeUpdate();

                plugin.getLogger().info("§a[Discord] Código gerado para " + player.getName() + ": " + code);

            } catch (SQLException e) {
                e.printStackTrace();
            }
        });

        return code;
    }

    /**
     * Verifica se o jogador já está vinculado
     */
    public boolean isLinked(UUID uuid) {
        try (Connection conn = plugin.getMySQL().getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT discord_id FROM rs_discord_links WHERE uuid = ? AND status = 'LINKED'"
            );
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getString("discord_id") != null;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Obtém o Discord ID do jogador (se vinculado)
     */
    public String getDiscordId(UUID uuid) {
        try (Connection conn = plugin.getMySQL().getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT discord_id FROM rs_discord_links WHERE uuid = ? AND status = 'LINKED'"
            );
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("discord_id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Obtém o Discord Tag do jogador
     */
    public String getDiscordTag(UUID uuid) {
        try (Connection conn = plugin.getMySQL().getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT discord_tag FROM rs_discord_links WHERE uuid = ? AND status = 'LINKED'"
            );
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("discord_tag");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Gera código aleatório no formato ABC-1234
     */
    private String generateRandomCode() {
        Random random = new Random();

        // 3 letras maiúsculas
        StringBuilder letters = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            letters.append((char) ('A' + random.nextInt(26)));
        }

        // 4 números
        int numbers = 1000 + random.nextInt(9000);

        return letters.toString() + "-" + numbers;
    }

    /**
     * Limpa códigos expirados a cada 5 minutos
     */
    private void startCleanupTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getMySQL().getConnection()) {
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE rs_discord_links SET status = 'EXPIRED' " +
                                "WHERE status = 'PENDING' AND code_expires_at < NOW()"
                );
                int updated = ps.executeUpdate();
                if (updated > 0) {
                    plugin.getLogger().info("§e[Discord] " + updated + " códigos expirados removidos.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, 6000L, 6000L); // 5 minutos
    }

    /**
     * Desvincula conta (para admins)
     */
    public void unlink(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getMySQL().getConnection()) {
                PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM rs_discord_links WHERE uuid = ?"
                );
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
}