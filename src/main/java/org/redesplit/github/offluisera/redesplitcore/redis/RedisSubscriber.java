package org.redesplit.github.offluisera.redesplitcore.redis;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.redesplit.github.offluisera.redesplitcore.tasks.RestartTask;
import redis.clients.jedis.JedisPubSub;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import org.redesplit.github.offluisera.redesplitcore.player.SplitPlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class RedisSubscriber extends JedisPubSub {

    @Override
    public void onMessage(String channel, String message) {
        // Executa na Thread Principal do Bukkit
        Bukkit.getScheduler().runTask(RedeSplitCore.getInstance(), () -> {

            // --- CASO 1: ATUALIZAÇÃO BANCÁRIA (Vinda do Site) ---
            if (message.startsWith("BANK_UPDATE|")) {
                handleBankUpdate(message);
                return;
            }

            // --- CASO 2: ATUALIZAÇÃO DE PERMISSÕES (NOVO) ---
            if (message.startsWith("PERM_UPDATE|")) {
                handlePermUpdate(message);
                return;
            }

            // --- CASO 3: TRANSFERÊNCIA DE CASH (NOVO - Implementado) ---
            // Formato: CASH_TRANS|SENDER|RECEIVER|AMOUNT
            if (message.startsWith("CASH_TRANS|")) {
                handleCashTransfer(message);
                return;
            }

            // --- CASO 4: COMANDOS DE REDE (Ban, Kick, etc) ---
            if (message.contains(";")) {
                handleCommand(message);
            }

            if (message.startsWith("RESTART|")) {
                // Formato: RESTART|ALVO|MINUTOS
                String[] args = message.split("\\|");
                if (args.length < 3) return;

                String target = args[1];
                int minutes = Integer.parseInt(args[2]);
                String myId = RedeSplitCore.getInstance().getServerId();

                // Se for para MIM ou para TODOS
                if (target.equalsIgnoreCase("ALL") || target.equalsIgnoreCase(myId)) {

                    // Inicia a sequência na Thread Principal
                    Bukkit.getScheduler().runTask(RedeSplitCore.getInstance(), () -> {
                        if (RedeSplitCore.getInstance().isRestarting()) return; // Já está rodando

                        RedeSplitCore.getInstance().getLogger().info("Iniciando sequencia de reinicio (" + minutes + "m)");
                        new RestartTask(minutes).runTaskTimer(RedeSplitCore.getInstance(), 0L, 20L); // Roda a cada segundo
                    });
                }
                return;
            }
        });
    }

    // --- NOVA IMPLEMENTAÇÃO: TRANSFERÊNCIA DE CASH ---
    private void handleCashTransfer(String message) {
        try {
            // message = CASH_TRANS|Luis|Pedro|500
            String[] args = message.split("\\|");
            if (args.length < 4) return;

            String senderName = args[1];
            String receiverName = args[2];
            String amountStr = args[3];

            // Atualiza quem enviou (se estiver online neste servidor)
            updatePlayerBalance(senderName, "§c§lCASH: §eVocê enviou §c" + amountStr + " §epara " + receiverName);

            // Atualiza quem recebeu (se estiver online neste servidor)
            updatePlayerBalance(receiverName, "§a§lCASH: §eVocê recebeu §a" + amountStr + " §ede " + senderName);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updatePlayerBalance(String playerName, String notificationMsg) {
        Player p = Bukkit.getPlayer(playerName);

        // Só faz algo se o jogador estiver online neste servidor específico
        if (p != null) {
            // 1. Busca o valor ATUALIZADO no MySQL (Garante sincronia com o site)
            Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
                double newCash = 0;
                boolean found = false;

                try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {
                    PreparedStatement ps = conn.prepareStatement("SELECT cash FROM rs_players WHERE name = ?");
                    ps.setString(1, playerName);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        newCash = rs.getDouble("cash");
                        found = true;
                    }
                    rs.close();
                    ps.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (found) {
                    double finalCash = newCash;
                    // 2. Volta pra Thread Principal para atualizar a memória do servidor
                    Bukkit.getScheduler().runTask(RedeSplitCore.getInstance(), () -> {
                        SplitPlayer sp = RedeSplitCore.getInstance().getPlayerManager().getPlayer(p.getUniqueId());
                        if (sp != null) {
                            // Atualiza o objeto na memória para a Scoreboard mudar na hora
                            sp.setCash(finalCash);
                        }

                        // Avisos e Sons
                        p.sendMessage("");
                        p.sendMessage("§a§l[BANCO SPLIT]");
                        p.sendMessage(notificationMsg);
                        p.sendMessage("§eSeu novo saldo: §6✪ " + String.format("%,.0f", finalCash));
                        p.sendMessage("");

                        // Som seguro (Compatível com 1.8 e versões novas via string)
                        playSoundSafe(p, "ORB_PICKUP", "ENTITY_EXPERIENCE_ORB_PICKUP");
                    });
                }
            });
        }
    }

    private void playSoundSafe(Player p, String oldSound, String newSound) {
        try {
            p.playSound(p.getLocation(), Sound.valueOf(oldSound), 1f, 1f);
        } catch (Exception e) {
            try {
                p.playSound(p.getLocation(), Sound.valueOf(newSound), 1f, 1f);
            } catch (Exception ignored) {}
        }
    }
    // -------------------------------------------------------

    private void handlePermUpdate(String message) {
        try {
            String[] args = message.split("\\|");
            if (args.length < 3) return;

            String rankId = args[2];

            RedeSplitCore.getInstance().getLogger().info("[Redis] Atualizando permissões para o cargo: " + rankId);

            for (Player p : Bukkit.getOnlinePlayers()) {
                SplitPlayer sp = RedeSplitCore.getInstance().getPlayerManager().getPlayer(p.getUniqueId());
                if (sp != null && sp.getRankId().equalsIgnoreCase(rankId)) {
                    RedeSplitCore.getInstance().getPlayerManager().updatePermissions(p);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleBankUpdate(String message) {
        try {
            String[] args = message.split("\\|");
            if (args.length < 5) return;

            String nick = args[1];
            String origem = args[2];
            String tipo = args[3];
            double valor = Double.parseDouble(args[4]);

            Player p = Bukkit.getPlayer(nick);

            if (p != null && p.isOnline()) {
                RedeSplitCore.getInstance().getPlayerManager().refreshEconomy(p.getUniqueId());

                String moeda = tipo.equalsIgnoreCase("cash") ? "Cash" : "Coins";
                String cor = tipo.equalsIgnoreCase("cash") ? "§e" : "§2";

                p.sendMessage("");
                p.sendMessage(" §a§l[BANCO SPLIT]");
                p.sendMessage(" §aTransferência recebida de: §6" + origem.toUpperCase());
                p.sendMessage(" §aValor creditado: " + cor + String.format("%,.0f", valor) + " " + moeda);
                p.sendMessage("");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleCommand(String message) {
        String[] parts = message.split(";", 3);
        if (parts.length < 3) return;

        String type = parts[0];
        String target = parts[1];
        String content = parts[2];

        String myServerId = RedeSplitCore.getInstance().getServerId();

        if (target.equalsIgnoreCase("ALL") || target.equalsIgnoreCase(myServerId)) {
            if (type.equals("CMD")) {
                if (content.toLowerCase().startsWith("mute ") || content.toLowerCase().startsWith("unmute ")) {
                    String[] cmdArgs = content.split(" ");
                    if (cmdArgs.length > 1) {
                        RedeSplitCore.getInstance().getPlayerManager().refreshPunishments(cmdArgs[1]);
                    }
                }
                RedeSplitCore.getInstance().getLogger().info("[Redis] Comando Remoto: " + content);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), content);
            }
        }
    }
}