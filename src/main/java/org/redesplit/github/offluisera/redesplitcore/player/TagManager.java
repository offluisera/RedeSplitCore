package org.redesplit.github.offluisera.redesplitcore.player;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;

public class TagManager {

    public static void update(Player player) {
        SplitPlayer sp = RedeSplitCore.getInstance().getPlayerManager().getPlayer(player.getUniqueId());
        if (sp == null) return;

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String rank = sp.getRankId().toLowerCase();

        // Ordem Alfabética define a posição no TAB (01_Master, 02_Admin...)
        String teamName = getOrder(rank) + rank;
        if (teamName.length() > 16) teamName = teamName.substring(0, 16);

        Team team = board.getTeam(teamName);
        if (team == null) team = board.registerNewTeam(teamName);

        String prefix = getPrefix(rank);
        team.setPrefix(prefix);
        team.addEntry(player.getName());

        // Aplica no TAB e acima da cabeça
        player.setScoreboard(board);

        // Atualiza a cor do nick no TAB
        String color = getNameColor(rank);
        player.setPlayerListName(color + player.getName());
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