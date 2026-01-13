// ============================================
// 1. TagManager.java - ATUALIZADO
// ============================================
package org.redesplit.github.offluisera.redesplitcore.player;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;

public class TagManager {

    /**
     * Atualiza a tag de um jogador específico
     */
    public static void update(Player player) {
        SplitPlayer sp = RedeSplitCore.getInstance().getPlayerManager().getPlayer(player.getUniqueId());
        if (sp == null) return;

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String rank = sp.getRankId().toLowerCase();

        // Ordem Alfabética define a posição no TAB
        String teamName = getOrder(rank) + rank;
        if (teamName.length() > 16) teamName = teamName.substring(0, 16);

        Team team = board.getTeam(teamName);
        if (team == null) team = board.registerNewTeam(teamName);

        // Define o PREFIX (tag que aparece ANTES do nome)
        String prefix = getPrefix(rank);
        team.setPrefix(prefix);

        // Adiciona o jogador ao time
        team.addEntry(player.getName());

        // Aplica a scoreboard
        player.setScoreboard(board);

        // Atualiza o nome no TAB
        String tabName = getTabDisplayName(player.getName(), rank);
        player.setPlayerListName(tabName);
    }

    /**
     * Atualiza as tags de TODOS os jogadores online
     * Use este método quando um novo jogador entrar no servidor
     */
    public static void updateAll() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            update(online);
        }
    }

    /**
     * Atualiza as tags para um jogador específico ver todos os outros
     * Útil quando o jogador acabou de entrar e precisa ver as tags de todos
     */
    public static void updateForPlayer(Player viewer) {
        // Atualiza a tag do próprio jogador
        update(viewer);

        // Garante que o jogador veja as tags de todos os outros
        Scoreboard mainBoard = Bukkit.getScoreboardManager().getMainScoreboard();
        viewer.setScoreboard(mainBoard);

        // Força a atualização das tags de todos para este jogador
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(viewer)) continue;

            SplitPlayer sp = RedeSplitCore.getInstance().getPlayerManager().getPlayer(online.getUniqueId());
            if (sp == null) continue;

            String rank = sp.getRankId().toLowerCase();
            String tabName = getTabDisplayName(online.getName(), rank);

            // Atualiza o nome no TAB do viewer
            online.setPlayerListName(tabName);
        }
    }

    /**
     * Retorna o nome completo para exibir no TAB
     */
    private static String getTabDisplayName(String playerName, String rank) {
        String prefix = getPrefix(rank).trim();
        String color = getNameColor(rank);

        // Se não tiver tag (membro), retorna apenas o nick cinza
        if (prefix.equals("§7") || prefix.isEmpty()) {
            return "§7" + playerName;
        }

        // Retorna: [TAG] NickColorido
        return prefix + " " + color + playerName;
    }

    private static String getOrder(String rank) {
        switch (rank) {
            case "master": return "a_";
            case "administrador": return "b_";
            case "moderador": return "c_";
            case "ajudante": return "d_";
            case "elite": return "e_";
            case "mvp+": return "f_";
            case "mvp": return "g_";
            case "vip+": return "h_";
            case "vip": return "i_";
            default: return "z_";
        }
    }

    private static String getPrefix(String rank) {
        switch (rank) {
            case "master": return "§6[MASTER]";
            case "administrador": return "§c[ADMIN]";
            case "moderador": return "§a[MOD]";
            case "ajudante": return "§e[HELPER]";
            case "elite": return "§3[ELITE]";
            case "mvp+": return "§e[MVP§b+§e]";
            case "mvp": return "§e[MVP]";
            case "vip+": return "§a[VIP§b+§a]";
            case "vip": return "§a[VIP]";
            default: return "§7";
        }
    }

    private static String getNameColor(String rank) {
        switch (rank) {
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
}