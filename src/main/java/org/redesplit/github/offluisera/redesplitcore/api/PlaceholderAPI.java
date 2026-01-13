package org.redesplit.github.offluisera.redesplitcore.api;

import org.bukkit.entity.Player;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import org.redesplit.github.offluisera.redesplitcore.player.SplitPlayer;
import org.redesplit.github.offluisera.redesplitcore.player.PlayerManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * API de Placeholders do RedeSplitCore
 * Permite que outros plugins acessem informações do core sem dependências externas
 *
 * Exemplo de uso:
 * String rank = RedeSplitCore.getPlaceholderAPI().parsePlaceholder(player, "splitcore_rank");
 * String mensagem = RedeSplitCore.getPlaceholderAPI().replace(player, "Olá %splitcore_player%!");
 */
public class PlaceholderAPI {

    private static PlaceholderAPI instance;
    private final RedeSplitCore plugin;
    private final DecimalFormat moneyFormat;
    private final NumberFormat numberFormat;

    public PlaceholderAPI(RedeSplitCore plugin) {
        instance = this;
        this.plugin = plugin;
        this.moneyFormat = new DecimalFormat("#,##0.00");
        this.numberFormat = NumberFormat.getNumberInstance(new Locale("pt", "BR"));
    }

    public static PlaceholderAPI getInstance() {
        return instance;
    }

    /**
     * Substitui uma placeholder específica pelo seu valor
     *
     * @param player O jogador
     * @param placeholder Nome da placeholder (ex: "splitcore_rank")
     * @return O valor da placeholder ou null se não existir
     */
    public String parsePlaceholder(Player player, String placeholder) {
        if (player == null || placeholder == null) {
            return null;
        }

        PlayerManager playerManager = plugin.getPlayerManager();
        SplitPlayer data = playerManager.getPlayer(player.getUniqueId());

        if (data == null) {
            return null;
        }

        // Remove % se vier com eles
        placeholder = placeholder.replace("%", "").toLowerCase();

        // ========== RANK ==========
        if (placeholder.equals("splitcore_rank")) {
            return getPrefix(data.getRankId());
        }

        if (placeholder.equals("splitcore_rank_name")) {
            String rankId = data.getRankId();
            return rankId != null ? rankId.toUpperCase() : "MEMBRO";
        }

        if (placeholder.equals("splitcore_rank_color")) {
            return getNameColor(data.getRankId());
        }

        if (placeholder.equals("splitcore_rank_prefix")) {
            return getPrefix(data.getRankId());
        }

        if (placeholder.equals("splitcore_rank_id")) {
            return data.getRankId() != null ? data.getRankId() : "membro";
        }

        // ========== PLAYER ==========
        if (placeholder.equals("splitcore_player")) {
            return player.getName();
        }

        if (placeholder.equals("splitcore_displayname")) {
            return player.getDisplayName();
        }

        if (placeholder.equals("splitcore_uuid")) {
            return player.getUniqueId().toString();
        }

        // ========== ECONOMIA ==========
        if (placeholder.equals("splitcore_money") || placeholder.equals("splitcore_coins")) {
            return moneyFormat.format(data.getCoins());
        }

        if (placeholder.equals("splitcore_money_raw") || placeholder.equals("splitcore_coins_raw")) {
            return String.valueOf(data.getCoins());
        }

        if (placeholder.equals("splitcore_cash")) {
            return moneyFormat.format(data.getCash());
        }

        if (placeholder.equals("splitcore_cash_raw")) {
            return String.valueOf(data.getCash());
        }

        // ========== ESTATÍSTICAS (SESSÃO) ==========
        if (placeholder.equals("splitcore_playtime")) {
            long sessionTime = data.getSessionPlaytime() / 1000; // ms para segundos
            return formatTime(sessionTime);
        }

        if (placeholder.equals("splitcore_playtime_hours")) {
            long sessionTime = data.getSessionPlaytime() / 1000;
            return String.valueOf(sessionTime / 3600);
        }

        if (placeholder.equals("splitcore_playtime_raw")) {
            return String.valueOf(data.getSessionPlaytime() / 1000);
        }

        // ========== ESTATÍSTICAS (BANCO DE DADOS - ASYNC) ==========
        // Nota: Estas placeholders buscam do banco, use com moderação
        if (placeholder.equals("splitcore_playtime_total")) {
            return getPlaytimeFromDatabase(player);
        }

        if (placeholder.equals("splitcore_firstjoin")) {
            return getFirstJoinFromDatabase(player);
        }

        if (placeholder.equals("splitcore_lastjoin")) {
            return getLastJoinFromDatabase(player);
        }

        // ========== MUTE ==========
        if (placeholder.equals("splitcore_muted")) {
            return data.isMuted() ? "§cMutado" : "§aNão mutado";
        }

        if (placeholder.equals("splitcore_mute_reason")) {
            return data.getMuteReason() != null ? data.getMuteReason() : "Nenhuma";
        }

        if (placeholder.equals("splitcore_mute_operator")) {
            return data.getMuteOperator() != null ? data.getMuteOperator() : "Nenhum";
        }

        if (placeholder.equals("splitcore_mute_expires")) {
            if (data.getMuteExpires() > 0) {
                return formatDate(data.getMuteExpires());
            }
            return "Nunca";
        }

        if (placeholder.equals("splitcore_mute_remaining")) {
            if (data.isMuted()) {
                long remaining = (data.getMuteExpires() - System.currentTimeMillis()) / 1000;
                return formatTime(remaining);
            }
            return "0s";
        }

        // ========== SERVIDOR ==========
        if (placeholder.equals("splitcore_server")) {
            return plugin.getServerId();
        }

        if (placeholder.equals("splitcore_server_name")) {
            return getServerName(plugin.getServerId());
        }

        return null; // Placeholder não encontrada
    }

    /**
     * Substitui todas as placeholders em um texto
     *
     * @param player O jogador
     * @param text Texto com placeholders (ex: "Olá %splitcore_player%!")
     * @return Texto com placeholders substituídas
     */
    public String replace(Player player, String text) {
        if (text == null || !text.contains("%")) {
            return text;
        }

        String result = text;
        int maxIterations = 50; // Previne loops infinitos
        int iterations = 0;

        while (result.contains("%splitcore_") && iterations < maxIterations) {
            int start = result.indexOf("%splitcore_");
            if (start == -1) break;

            int end = result.indexOf("%", start + 1);
            if (end == -1) break;

            String fullPlaceholder = result.substring(start, end + 1);
            String placeholder = result.substring(start + 1, end);

            String value = parsePlaceholder(player, placeholder);

            if (value != null) {
                result = result.replace(fullPlaceholder, value);
            } else {
                // Placeholder não encontrada, remove
                result = result.substring(0, start) + result.substring(end + 1);
            }

            iterations++;
        }

        return result;
    }

    /**
     * Verifica se uma placeholder existe
     *
     * @param placeholder Nome da placeholder
     * @return true se existir, false caso contrário
     */
    public boolean hasPlaceholder(String placeholder) {
        if (placeholder == null) return false;

        placeholder = placeholder.replace("%", "").toLowerCase();

        return placeholder.startsWith("splitcore_") && (
                // Rank
                placeholder.equals("splitcore_rank") ||
                        placeholder.equals("splitcore_rank_name") ||
                        placeholder.equals("splitcore_rank_color") ||
                        placeholder.equals("splitcore_rank_prefix") ||
                        placeholder.equals("splitcore_rank_id") ||
                        // Player
                        placeholder.equals("splitcore_player") ||
                        placeholder.equals("splitcore_displayname") ||
                        placeholder.equals("splitcore_uuid") ||
                        // Economia
                        placeholder.equals("splitcore_money") ||
                        placeholder.equals("splitcore_money_raw") ||
                        placeholder.equals("splitcore_coins") ||
                        placeholder.equals("splitcore_coins_raw") ||
                        placeholder.equals("splitcore_cash") ||
                        placeholder.equals("splitcore_cash_raw") ||
                        // Stats Sessão
                        placeholder.equals("splitcore_playtime") ||
                        placeholder.equals("splitcore_playtime_hours") ||
                        placeholder.equals("splitcore_playtime_raw") ||
                        // Stats Banco
                        placeholder.equals("splitcore_playtime_total") ||
                        placeholder.equals("splitcore_firstjoin") ||
                        placeholder.equals("splitcore_lastjoin") ||
                        // Mute
                        placeholder.equals("splitcore_muted") ||
                        placeholder.equals("splitcore_mute_reason") ||
                        placeholder.equals("splitcore_mute_operator") ||
                        placeholder.equals("splitcore_mute_expires") ||
                        placeholder.equals("splitcore_mute_remaining") ||
                        // Servidor
                        placeholder.equals("splitcore_server") ||
                        placeholder.equals("splitcore_server_name")
        );
    }

    // ========== MÉTODOS AUXILIARES ==========

    private String formatTime(long seconds) {
        if (seconds < 0) return "0s";

        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (days > 0) {
            return days + "d " + hours + "h";
        } else if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else if (minutes > 0) {
            return minutes + "m " + secs + "s";
        } else {
            return secs + "s";
        }
    }

    private String formatDate(long timestamp) {
        if (timestamp == 0) return "Nunca";

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm");
        return sdf.format(new java.util.Date(timestamp));
    }

    // ========== MÉTODOS DO SISTEMA DE TAGS ==========

    private String getPrefix(String rank) {
        if (rank == null) return "§7";
        switch (rank.toLowerCase()) {
            case "master": return "§6[MASTER] ";
            case "administrador": return "§c[ADMIN] ";
            case "moderador": return "§a[MOD] ";
            case "ajudante": return "§e[HELPER] ";
            case "elite": return "§3[ELITE] ";
            case "mvp+": return "§e[MVP§b+§e] ";
            case "mvp": return "§e[MVP] ";
            case "vip+": return "§a[VIP§b+§a] ";
            case "vip": return "§a[VIP] ";
            default: return "§7";
        }
    }

    private String getNameColor(String rank) {
        if (rank == null) return "§7";
        switch (rank.toLowerCase()) {
            case "master": return "§6";
            case "administrador": return "§c";
            case "moderador": return "§a";
            case "ajudante": return "§e";
            case "elite": return "§3";
            case "mvp+": case "mvp": return "§e";
            case "vip+": case "vip": return "§a";
            default: return "§7";
        }
    }

    private String getServerName(String serverId) {
        if (serverId == null) return "Desconhecido";
        switch (serverId.toLowerCase()) {
            case "geral": case "lobby": return "Lobby";
            case "skyblock": return "SkyBlock";
            case "rankup": return "RankUP";
            case "fullpvp": return "FullPvP";
            case "survival": return "Survival";
            case "bedwars": return "BedWars";
            case "skywars": return "SkyWars";
            default: return serverId;
        }
    }

    // ========== MÉTODOS DE BUSCA NO BANCO (CACHE RECOMENDADO) ==========

    private String getPlaytimeFromDatabase(Player player) {
        try (Connection conn = plugin.getMySQL().getConnection()) {
            String serverId = plugin.getServerId();
            boolean isLobby = serverId.equalsIgnoreCase("geral") || serverId.equalsIgnoreCase("lobby");

            String table = isLobby ? "rs_players" : getTableName(serverId);
            String sql = "SELECT playtime FROM " + table + " WHERE uuid = ?";

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, player.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                long playtime = rs.getLong("playtime");
                return formatTime(playtime / 1000); // Converte ms para segundos
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "0s";
    }

    private String getFirstJoinFromDatabase(Player player) {
        try (Connection conn = plugin.getMySQL().getConnection()) {
            PreparedStatement ps = conn.prepareStatement("SELECT first_join FROM rs_players WHERE uuid = ?");
            ps.setString(1, player.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                java.sql.Timestamp ts = rs.getTimestamp("first_join");
                if (ts != null) {
                    return formatDate(ts.getTime());
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "Desconhecido";
    }

    private String getLastJoinFromDatabase(Player player) {
        try (Connection conn = plugin.getMySQL().getConnection()) {
            PreparedStatement ps = conn.prepareStatement("SELECT last_login FROM rs_players WHERE uuid = ?");
            ps.setString(1, player.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                java.sql.Timestamp ts = rs.getTimestamp("last_login");
                if (ts != null) {
                    return formatDate(ts.getTime());
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "Desconhecido";
    }

    private String getTableName(String serverId) {
        switch (serverId.toLowerCase()) {
            case "skyblock": return "rs_stats_skyblock";
            case "rankup":   return "rs_stats_rankup";
            case "fullpvp":  return "rs_stats_fullpvp";
            case "survival": return "rs_stats_survival";
            case "bedwars":  return "rs_stats_bedwars";
            case "skywars":  return "rs_stats_skywars";
            default:         return "rs_players";
        }
    }
}