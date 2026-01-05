package org.redesplit.github.offluisera.redesplitcore.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import org.redesplit.github.offluisera.redesplitcore.player.SplitPlayer;
import org.redesplit.github.offluisera.redesplitcore.player.TagManager;
import org.redesplit.github.offluisera.redesplitcore.utils.TimeUtils;

import java.sql.*;

public class RankCommands implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("redesplit.admin")) {
            sender.sendMessage("§cSem permissão.");
            return true;
        }

        // /setrank <nick> <rank>
        if (label.equalsIgnoreCase("setrank")) {
            if (args.length < 2) {
                sender.sendMessage("§cUso: /setrank <nick> <rank>");
                return true;
            }
            setRank(sender, args[0], args[1]);
            return true;
        }

        // /settemprank <nick> <tempo> <rank>
        if (label.equalsIgnoreCase("settemprank")) {
            if (args.length < 3) {
                sender.sendMessage("§cUso: /settemprank <nick> <tempo> <rank>");
                return true;
            }
            long minutes = TimeUtils.parseTime(args[1]);
            if (minutes <= 0) {
                sender.sendMessage("§cTempo inválido. Use formato: 10m, 1h, 30d.");
                return true;
            }
            setTempRank(sender, args[0], minutes, args[2]);
            return true;
        }

        // /rank <rank> addperm <permissão>
        if (label.equalsIgnoreCase("rank")) {
            if (args.length >= 3 && args[1].equalsIgnoreCase("addperm")) {
                addRankPermission(sender, args[0], args[2]);
                return true;
            }
            sender.sendMessage("§cUso: /rank <rank> addperm <permissão>");
            return true;
        }

        // /perm user <nick> ...
        if (label.equalsIgnoreCase("perm")) {
            if (args.length >= 1 && args[0].equalsIgnoreCase("user")) {
                if (args.length < 4) {
                    sender.sendMessage("§cUso: /perm user <nick> addperm <permissão>");
                    return true;
                }
                String target = args[1];
                String action = args[2];

                if (action.equalsIgnoreCase("addperm")) {
                    addUserPermission(sender, target, args[3], 0);
                } else if (action.equalsIgnoreCase("addtempperm")) {
                    if (args.length < 5) {
                        sender.sendMessage("§cUso: /perm user <nick> addtempperm <tempo> <permissão>");
                        return true;
                    }
                    long time = TimeUtils.parseTime(args[3]);
                    addUserPermission(sender, target, args[4], time);
                } else {
                    sender.sendMessage("§cAção inválida.");
                }
                return true;
            }
        }
        return false;
    }

    // --- CORREÇÃO DO SETRANK (Permanente) ---
    private void setRank(CommandSender sender, String targetName, String rankId) {
        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {

                // 1. PRIMEIRO VERIFICAMOS SE O JOGADOR EXISTE
                PreparedStatement check = conn.prepareStatement("SELECT uuid FROM rs_players WHERE name = ?");
                check.setString(1, targetName);
                ResultSet rs = check.executeQuery();

                if (rs.next()) {
                    // Jogador existe, pode atualizar
                    String uuid = rs.getString("uuid");
                    rs.close();
                    check.close();

                    PreparedStatement ps = conn.prepareStatement("UPDATE rs_players SET rank_id = ? WHERE name = ?");
                    ps.setString(1, rankId.toLowerCase());
                    ps.setString(2, targetName);
                    ps.executeUpdate(); // Não precisamos mais checar o retorno de linhas aqui

                    sender.sendMessage("§aRank de " + targetName + " alterado permanentemente para " + rankId + ".");

                    // Remove rank temporário se houver
                    PreparedStatement psDel = conn.prepareStatement("DELETE FROM rs_temp_ranks WHERE uuid = ?");
                    psDel.setString(1, uuid);
                    psDel.executeUpdate();

                    updatePlayerVisuals(targetName, rankId);
                } else {
                    // Jogador realmente não existe no banco
                    sender.sendMessage("§cJogador não encontrado no banco de dados.");
                }

            } catch (SQLException e) {
                e.printStackTrace();
                sender.sendMessage("§cErro SQL: " + e.getMessage());
            }
        });
    }

    // --- CORREÇÃO DO SETTEMPRANK ---
    private void setTempRank(CommandSender sender, String targetName, long durationMin, String rankId) {
        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {

                // 1. Busca informações primeiro
                PreparedStatement psInfo = conn.prepareStatement("SELECT uuid, rank_id FROM rs_players WHERE name = ?");
                psInfo.setString(1, targetName);
                ResultSet rs = psInfo.executeQuery();

                if (rs.next()) {
                    String uuid = rs.getString("uuid");
                    String currentRank = rs.getString("rank_id");

                    // Se o rank atual já for o temporário, mantemos, senão salvamos como previous
                    // (Lógica simplificada: sempre salva o que está no rs_players como anterior)

                    Timestamp expires = new Timestamp(System.currentTimeMillis() + (durationMin * 60 * 1000));

                    // Insere ou Atualiza na tabela temporária
                    PreparedStatement psTemp = conn.prepareStatement(
                            "REPLACE INTO rs_temp_ranks (uuid, rank_id, previous_rank, expires) VALUES (?, ?, ?, ?)");
                    psTemp.setString(1, uuid);
                    psTemp.setString(2, rankId.toLowerCase());
                    psTemp.setString(3, currentRank);
                    psTemp.setTimestamp(4, expires);
                    psTemp.executeUpdate();

                    // Atualiza o rank atual
                    PreparedStatement psUp = conn.prepareStatement("UPDATE rs_players SET rank_id = ? WHERE uuid = ?");
                    psUp.setString(1, rankId.toLowerCase());
                    psUp.setString(2, uuid);
                    psUp.executeUpdate();

                    sender.sendMessage("§aRank Temporário (" + TimeUtils.formatTime(durationMin) + ") aplicado para " + targetName);
                    updatePlayerVisuals(targetName, rankId);
                } else {
                    sender.sendMessage("§cJogador nunca entrou no servidor.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                sender.sendMessage("§cErro SQL: " + e.getMessage());
            }
        });
    }

    // --- (Restante do código: addUserPermission, addRankPermission e updatePlayerVisuals mantidos iguais) ---
    // MANTENHA OS OUTROS MÉTODOS AQUI IGUAIS AO SEU CÓDIGO ANTERIOR
    private void addUserPermission(CommandSender sender, String targetName, String perm, long duration) {
        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {
                PreparedStatement ps = conn.prepareStatement("SELECT uuid FROM rs_players WHERE name = ?");
                ps.setString(1, targetName);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String uuid = rs.getString("uuid");
                    Timestamp expires = (duration > 0) ? new Timestamp(System.currentTimeMillis() + (duration * 60 * 1000)) : null;
                    PreparedStatement psIn = conn.prepareStatement("INSERT INTO rs_user_permissions (uuid, permission, expires, active) VALUES (?, ?, ?, 1)");
                    psIn.setString(1, uuid); psIn.setString(2, perm); psIn.setTimestamp(3, expires);
                    psIn.executeUpdate();
                    sender.sendMessage("§aPermissão '" + perm + "' adicionada.");
                    Player p = Bukkit.getPlayer(targetName);
                    if (p != null) RedeSplitCore.getInstance().getPlayerManager().updatePermissions(p);
                } else { sender.sendMessage("§cJogador não encontrado."); }
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    private void addRankPermission(CommandSender sender, String rankId, String permission) {
        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {
                PreparedStatement ps = conn.prepareStatement("INSERT IGNORE INTO rs_ranks_permissions (rank_id, permission) VALUES (?, ?)");
                ps.setString(1, rankId.toLowerCase()); ps.setString(2, permission); ps.executeUpdate();
                sender.sendMessage("§aPermissão adicionada ao rank " + rankId);
                // Atualizar Redis/Players aqui...
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    private void updatePlayerVisuals(String name, String rankId) {
        Bukkit.getScheduler().runTask(RedeSplitCore.getInstance(), () -> {
            Player p = Bukkit.getPlayer(name);
            if (p != null) {
                SplitPlayer sp = RedeSplitCore.getInstance().getPlayerManager().getPlayer(p.getUniqueId());
                if (sp != null) {
                    sp.setRankId(rankId.toLowerCase());
                    TagManager.update(p);
                    RedeSplitCore.getInstance().getPlayerManager().updatePermissions(p);
                    p.sendMessage("§aSeu cargo foi atualizado para: " + rankId);
                }
            }
        });
    }
}