package org.redesplit.github.offluisera.redesplitcore.web;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import org.redesplit.github.offluisera.redesplitcore.player.SplitPlayer;
import org.redesplit.github.offluisera.redesplitcore.player.TagManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

public class WebPollingTask extends BukkitRunnable {

    @Override
    public void run() {
        // Roda assincronamente para não lagar o servidor
        try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM rs_web_commands WHERE executed = 0");
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                int id = rs.getInt("id");
                String playerName = rs.getString("player_name");
                String action = rs.getString("action");
                String value = rs.getString("value");

                // --- ROTEAMENTO DE COMANDOS ---

                // 1. Alteração de Rank e Permissões
                if (action.equalsIgnoreCase("setrank")) {
                    processSetRank(id, playerName, value);
                }
                else if (action.equalsIgnoreCase("update_perms")) {
                    processUpdatePerms(id, value);
                }

                // 2. Punições (Ban, Kick, Mute)
                else if (action.equalsIgnoreCase("KICK") || action.equalsIgnoreCase("BAN") || action.equalsIgnoreCase("MUTE")) {
                    processPunish(id, playerName, action, value);
                }

                // 3. Revogar Punições (Unban, Unmute)
                else if (action.equalsIgnoreCase("UNBAN") || action.equalsIgnoreCase("UNMUTE")) {
                    processRevoke(id, playerName, action);
                }

                // 4. Sistema de Tickets
                else if (action.equalsIgnoreCase("TICKET_OPEN") || action.equalsIgnoreCase("TICKET_REPLY")) {
                    processTicket(id, playerName, action, value);
                }

                else {
                    // Comando desconhecido, marca como lido para não travar a fila
                    markAsExecuted(id);
                }
            }
        } catch (SQLException e) {
            RedeSplitCore.getInstance().getLogger().severe("Erro no WebPolling: " + e.getMessage());
        }
    }

    // --- PROCESSADORES ---

    private void processTicket(int id, String targetName, String action, String value) {
        Bukkit.getScheduler().runTask(RedeSplitCore.getInstance(), () -> {

            // CENÁRIO 1: Novo Ticket (Avisa Staff)
            if (action.equalsIgnoreCase("TICKET_OPEN")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission("redesplit.ticket")) {
                        p.sendMessage("");
                        p.sendMessage("§a§l[TICKET] §aUm novo ticket foi aberto no painel!");
                        p.sendMessage("§aVerifique em: §fhttps://redesplit.com.br/admin/tickets.php");
                        p.sendMessage("");
                        playSound(p);
                    }
                }
            }

            // CENÁRIO 2: Resposta de Ticket (Avisa Jogador)
            else if (action.equalsIgnoreCase("TICKET_REPLY")) {
                Player target = Bukkit.getPlayer(targetName);
                if (target != null && target.isOnline()) {
                    target.sendMessage("");
                    target.sendMessage("§e§l[SUPORTE] §eSeu ticket foi respondido pela equipe!");
                    target.sendMessage("§eAcesse o painel para ler a resposta.");
                    target.sendMessage("");
                    playSound(target);
                }
            }
        });
        markAsExecuted(id);
    }

    private void processPunish(int id, String playerName, String type, String value) {
        // O PHP envia "DURAÇÃO|MOTIVO"
        String[] parts = value.split("\\|", 2);
        long durationMin = 0;
        try {
            durationMin = Long.parseLong(parts[0]);
        } catch (NumberFormatException e) { durationMin = 0; }

        String reason = (parts.length > 1) ? parts[1] : "Sem motivo";

        // Ações que afetam o jogo precisam rodar na Thread Principal (Sync)
        long finalDurationMin = durationMin;

        Bukkit.getScheduler().runTask(RedeSplitCore.getInstance(), () -> {
            Player target = Bukkit.getPlayer(playerName);

            if (type.equalsIgnoreCase("KICK")) {
                if (target != null) {
                    target.kickPlayer("§cVocê foi expulso pelo Painel!\n\n§eMotivo: §f" + reason);
                }
            }
            else if (type.equalsIgnoreCase("BAN")) {
                // Aqui mantivemos o método nativo + comando console para garantir
                Date expires = (finalDurationMin > 0) ? new Date(System.currentTimeMillis() + (finalDurationMin * 60 * 1000)) : null;

                // Opção 1: Usar API Nativa (Mais limpo, mas só funciona se o servidor usar sistema nativo)
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(playerName, reason, expires, "PainelWeb");

                // Opção 2: (Recomendado) Executar o comando /ban ou /tempban para garantir compatibilidade com plugins
                // Se quiser usar o comando do console, descomente a linha abaixo e comente a linha acima:
                // Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban " + playerName + " " + reason);

                if (target != null) {
                    target.kickPlayer("§cVocê foi banido!\n\n§eMotivo: §f" + reason);
                }
                Bukkit.broadcastMessage("§cO jogador " + playerName + " foi banido via Painel.");
            }

            // --- CORREÇÃO DO MUTE ---
            else if (type.equalsIgnoreCase("MUTE")) {
                // Monta o argumento de tempo. Ex: "10m" ou "" (vazio = permanente)
                String timeArg = (finalDurationMin > 0) ? finalDurationMin + "m" : "";

                // Constrói o comando.
                // IMPORTANTE: Verifique se o seu comando /mute aceita essa ordem: /mute NICK TEMPO MOTIVO
                String cmd = "mute " + playerName + " " + timeArg + " " + reason;

                // Remove espaços duplos caso o tempo seja vazio
                cmd = cmd.replace("  ", " ");

                // O console executa o comando. Isso faz seu plugin de Chat/Punição registrar o mute de verdade.
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);

                // Log para debug no console
                RedeSplitCore.getInstance().getLogger().info("[Web] Executando punição: " + cmd);
            }
        });
        markAsExecuted(id);
    }

    private void processUpdatePerms(int id, String rankId) {
        Bukkit.getScheduler().runTask(RedeSplitCore.getInstance(), () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                SplitPlayer sp = RedeSplitCore.getInstance().getPlayerManager().getPlayer(p.getUniqueId());
                if (sp != null && sp.getRankId().equalsIgnoreCase(rankId)) {
                    RedeSplitCore.getInstance().getPlayerManager().updatePermissions(p);
                    p.sendMessage("§e[RedeSplit] Suas permissões foram atualizadas.");
                }
            }
        });
        markAsExecuted(id);
    }

    private void processSetRank(int id, String name, String rank) {
        Bukkit.getScheduler().runTask(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {
                PreparedStatement check = conn.prepareStatement("SELECT uuid FROM rs_players WHERE name = ?");
                check.setString(1, name);
                if (check.executeQuery().next()) {
                    PreparedStatement update = conn.prepareStatement("UPDATE rs_players SET rank_id = ? WHERE name = ?");
                    update.setString(1, rank.toLowerCase());
                    update.setString(2, name);
                    update.executeUpdate();
                }
            } catch (SQLException e) { e.printStackTrace(); }

            Player target = Bukkit.getPlayer(name);
            if (target != null) {
                SplitPlayer sp = RedeSplitCore.getInstance().getPlayerManager().getPlayer(target.getUniqueId());
                if (sp != null) {
                    sp.setRankId(rank.toLowerCase());
                    TagManager.update(target);
                    RedeSplitCore.getInstance().getPlayerManager().updatePermissions(target);
                    target.sendMessage("§a[RedeSplit] Seu cargo foi atualizado para " + rank.toUpperCase());
                    playSound(target);
                }
            }
        });
        markAsExecuted(id);
    }

    private void processRevoke(int id, String playerName, String type) {
        Bukkit.getScheduler().runTask(RedeSplitCore.getInstance(), () -> {

            if (type.equalsIgnoreCase("UNBAN")) {
                // Tenta pelo comando console para garantir
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "pardon " + playerName);
                // Ou nativo: Bukkit.getBanList(org.bukkit.BanList.Type.NAME).pardon(playerName);

                RedeSplitCore.getInstance().getLogger().info(playerName + " foi desbanido via Painel.");
            }

            else if (type.equalsIgnoreCase("UNMUTE")) {
                // Executa /unmute pelo console para limpar o cache do MuteManager
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "unmute " + playerName);

                Player target = Bukkit.getPlayer(playerName);
                if (target != null) {
                    target.sendMessage("§a§l[!] §aVocê foi desmutado por um membro da equipe.");
                    target.sendMessage("§aJá pode voltar a usar o chat.");
                    playSound(target);
                }
            }
        });

        markAsExecuted(id);
    }

    private void markAsExecuted(int id) {
        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {
                PreparedStatement ps = conn.prepareStatement("UPDATE rs_web_commands SET executed = 1 WHERE id = ?");
                ps.setInt(1, id);
                ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    private void playSound(Player p) {
        try {
            p.playSound(p.getLocation(), Sound.valueOf("NOTE_PLING"), 1.0f, 1.5f);
        } catch (IllegalArgumentException e) {
            try {
                p.playSound(p.getLocation(), Sound.valueOf("BLOCK_NOTE_BLOCK_PLING"), 1.0f, 1.5f);
            } catch (Exception ignored) {}
        }
    }
}