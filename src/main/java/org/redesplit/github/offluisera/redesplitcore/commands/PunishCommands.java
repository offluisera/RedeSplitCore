package org.redesplit.github.offluisera.redesplitcore.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import org.redesplit.github.offluisera.redesplitcore.player.SplitPlayer;
import org.redesplit.github.offluisera.redesplitcore.utils.TimeUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.stream.Collectors;

public class PunishCommands implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("redesplit.punish")) {
            sender.sendMessage("§cSem permissão.");
            return true;
        }

        // Lógica para UNBAN e UNMUTE
        String cmdName = label.toLowerCase();
        if (cmdName.equals("unban") || cmdName.equals("pardon") || cmdName.equals("unmute")) {
            if (args.length < 1) {
                sender.sendMessage("§cUso: /" + label + " <player>");
                return true;
            }
            handleRevoke(sender, args[0], cmdName);
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUso: /" + label + " <player> [tempo] <motivo>");
            return true;
        }

        String targetName = args[0];

        // Padroniza o TIPO baseado no comando digitado
        String type = "BAN"; // Default
        if (cmdName.contains("kick")) type = "KICK";
        else if (cmdName.contains("mute")) type = "MUTE";

        // Se for tempban ou tempmute, a lógica de tempo abaixo resolve.

        long durationMinutes = 0;
        String reason = "";

        if (type.equals("KICK")) {
            reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        } else {
            // Tenta achar tempo no argumento 1 (Padrão Admin/Site)
            long timeCheckFirst = TimeUtils.parseTime(args[1]);

            if (timeCheckFirst != -1) {
                durationMinutes = timeCheckFirst;
                reason = Arrays.stream(args).skip(2).collect(Collectors.joining(" "));
            } else {
                // Tenta achar tempo no último argumento
                long timeCheckLast = TimeUtils.parseTime(args[args.length - 1]);
                if (timeCheckLast != -1) {
                    durationMinutes = timeCheckLast;
                    reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1));
                } else {
                    durationMinutes = 0; // Permanente
                    reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                }
            }
        }

        if (reason.trim().isEmpty()) reason = "Sem motivo";

        // SE O COMANDO VIER DO CONSOLE (SITE), NÃO SALVA NO BANCO DE NOVO!
        // O site já salvou. O console só precisa aplicar o efeito (Kickar/Avisar).
        boolean saveToDb = !(sender instanceof ConsoleCommandSender);

        // Se você quiser testar pelo console manualmente e salvar, use /ban ... -s
        if (args[args.length-1].equalsIgnoreCase("-s")) {
            saveToDb = true;
            reason = reason.replace(" -s", "");
        }

        applyPunishment(sender, targetName, type, reason, durationMinutes, saveToDb);
        return true;
    }

    private void handleRevoke(CommandSender sender, String target, String cmd) {
        boolean isBan = cmd.contains("ban") || cmd.contains("pardon");
        String type = isBan ? "BAN" : "MUTE";

        // Apenas atualiza o banco se não for Console (Site já fez)
        if (!(sender instanceof ConsoleCommandSender)) {
            Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
                try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {
                    conn.createStatement().executeUpdate("UPDATE rs_punishments SET active = 0 WHERE player_name = '" + target + "' AND type = '" + type + "'");
                } catch (Exception e) { e.printStackTrace(); }
            });
        }

        sender.sendMessage("§aPunição (" + type + ") revogada para " + target);

        // Se for UNMUTE, avisa o PlayerManager na hora
        if (!isBan) {
            RedeSplitCore.getInstance().getPlayerManager().refreshPunishments(target);
            Player p = Bukkit.getPlayer(target);
            if (p != null) p.sendMessage("§aSeu silenciamento foi revogado!");
        }
    }

    private void applyPunishment(CommandSender sender, String targetName, String type, String reason, long duration, boolean saveToDb) {
        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {

            // 1. SALVAR NO BANCO (Só se for player digitando)
            if (saveToDb && !type.equals("KICK")) { // Kick não costuma salvar histórico ativo
                try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {
                    Date expires;
                    if (duration > 0) expires = new Date(System.currentTimeMillis() + (duration * 60 * 1000));
                    else expires = new Date(4102444800000L); // Ano 2100

                    PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO rs_punishments (player_name, operator, reason, type, expires, active) VALUES (?, ?, ?, ?, ?, 1)");
                    ps.setString(1, targetName);
                    ps.setString(2, sender.getName());
                    ps.setString(3, reason);
                    ps.setString(4, type);
                    ps.setTimestamp(5, new java.sql.Timestamp(expires.getTime()));
                    ps.executeUpdate();
                } catch (SQLException e) {
                    sender.sendMessage("§cErro ao salvar punição: " + e.getMessage());
                }
            }

            // 2. APLICAR EFEITO VISUAL (KICK/MUTE MESSAGE)
            long finalDuration = duration;
            Bukkit.getScheduler().runTask(RedeSplitCore.getInstance(), () -> {
                Player target = Bukkit.getPlayer(targetName);
                String durationText = (finalDuration <= 0) ? "Permanente" : TimeUtils.formatTime(finalDuration);

                // Avisa quem puniu
                if (!saveToDb) sender.sendMessage("§e[Site/Console] Aplicando efeito de " + type + " em " + targetName);
                else sender.sendMessage("§aPunição aplicada com sucesso em " + targetName);

                if (type.equals("KICK")) {
                    if (target != null) target.kickPlayer("§cVocê foi expulso!\n\n§eMotivo: §f" + reason);
                }
                else if (type.equals("BAN")) {
                    // O Listener de Login impede a entrada, aqui só chutamos quem tá online
                    if (target != null) {
                        target.kickPlayer("§cVocê foi banido!\n\n§eMotivo: §f" + reason + "\n§eDuração: §f" + durationText);
                    }
                }
                else if (type.equals("MUTE")) {
                    // Atualiza a memória RAM do PlayerManager para o Mute valer na hora
                    RedeSplitCore.getInstance().getPlayerManager().refreshPunishments(targetName);

                    if (target != null) {
                        target.sendMessage("");
                        target.sendMessage("§c§l[!] VOCÊ FOI MUTADO!");
                        target.sendMessage("§eMotivo: §f" + reason);
                        target.sendMessage("§eTempo: §f" + durationText);
                        target.sendMessage("");
                    }
                }
            });
        });
    }
}