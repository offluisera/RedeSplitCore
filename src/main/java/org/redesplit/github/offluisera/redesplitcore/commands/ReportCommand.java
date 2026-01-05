package org.redesplit.github.offluisera.redesplitcore.commands;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import redis.clients.jedis.Jedis;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;

public class ReportCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cApenas jogadores podem reportar.");
            return true;
        }

        // /report <nick> <motivo>
        if (args.length < 2) {
            sender.sendMessage("§cUtilize: /report <jogador> <motivo>");
            return true;
        }

        String target = args[0];
        // Junta todos os argumentos a partir do índice 1 para formar o motivo
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        Player reporter = (Player) sender;

        if (target.equalsIgnoreCase(reporter.getName())) {
            sender.sendMessage("§cVocê não pode reportar a si mesmo.");
            return true;
        }

        reporter.sendMessage("§eEnviando denúncia...");

        // Executa assincronamente para não travar o servidor (MySQL + Redis)
        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {

                // 1. INSERIR NO MYSQL
                // Nota: Não precisamos definir 'solved_by' ou 'solved_at' aqui, pois são NULL por padrão.
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO rs_reports (reporter, reported, reason, status) VALUES (?, ?, ?, 'ABERTO')");
                ps.setString(1, reporter.getName());
                ps.setString(2, target);
                ps.setString(3, reason);
                ps.executeUpdate();

                // 2. ENVIAR PARA O REDIS (Integração Cross-Server/Web)
                try (Jedis jedis = new Jedis("82.39.107.62", 6379)) {
                    jedis.auth("UHAFDjbnakfye@@jouiayhfiqwer903");
                    // Formato: REPORT;Reporter;Acusado;Motivo
                    jedis.publish("redesplit:channel", "REPORT;" + reporter.getName() + ";" + target + ";" + reason);
                } catch (Exception e) {
                    RedeSplitCore.getInstance().getLogger().warning("Erro ao publicar Report no Redis (MySQL salvo): " + e.getMessage());
                }

                // 3. FEEDBACK NO JOGO (Volta para a Thread Principal)
                Bukkit.getScheduler().runTask(RedeSplitCore.getInstance(), () -> {
                    // Mensagem para quem reportou
                    reporter.sendMessage("");
                    reporter.sendMessage("§a§lSUCESSO! §aSua denúncia foi enviada.");
                    reporter.sendMessage("§aA nossa equipe irá analisar o caso de §f" + target + "§a.");
                    reporter.sendMessage("");

                    // Som de sucesso
                    try {
                        reporter.playSound(reporter.getLocation(), Sound.valueOf("ORB_PICKUP"), 1f, 1f);
                    } catch (Exception ignored) {} // Proteção para versões diferentes de som

                    // Avisar Staffs Online NESTE servidor
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        // Permissão atualizada para bater com seu plugin.yml
                        if (p.hasPermission("redesplit.reports.notify") || p.hasPermission("redesplit.report.view") || p.isOp()) {
                            p.sendMessage("§c§l[REPORT] §eNovo report de §f" + reporter.getName());
                            p.sendMessage("§c> §eAcusado: §f" + target);
                            p.sendMessage("§c> §eMotivo: §f" + reason);

                            // Som de alerta para a Staff
                            try {
                                p.playSound(p.getLocation(), Sound.valueOf("NOTE_PLING"), 1f, 2f);
                            } catch (Exception ignored) {}
                        }
                    }
                });

            } catch (SQLException e) {
                reporter.sendMessage("§cErro ao enviar report. Tente novamente.");
                e.printStackTrace();
            }
        });

        return true;
    }
}