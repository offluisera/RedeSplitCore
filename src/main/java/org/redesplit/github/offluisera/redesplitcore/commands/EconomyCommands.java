package org.redesplit.github.offluisera.redesplitcore.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import org.redesplit.github.offluisera.redesplitcore.player.SplitPlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;

public class EconomyCommands implements CommandExecutor {

    private final DecimalFormat df = new DecimalFormat("#,###.##");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // --- COMANDO /MONEY ou /SALDO ---
        if (label.equalsIgnoreCase("money") || label.equalsIgnoreCase("saldo")) {
            if (args.length == 0) {
                if (!(sender instanceof Player)) return true;
                Player p = (Player) sender;
                SplitPlayer sp = RedeSplitCore.getInstance().getPlayerManager().getPlayer(p.getUniqueId());
                if (sp != null) {
                    p.sendMessage("§a§lECONOMIA");
                    p.sendMessage("");
                    p.sendMessage("§7Coins: §a$ " + df.format(sp.getCoins()));
                    p.sendMessage("§7Cash: §6✪ " + df.format(sp.getCash()));
                    p.sendMessage("");
                }
                return true;
            }
            // Ver saldo de outro jogador (Admin ou Player curioso)
            if (sender.hasPermission("redesplit.eco.others")) {
                viewOtherBalance(sender, args[0]);
            } else {
                sender.sendMessage("§cUse apenas /money");
            }
            return true;
        }

        // --- COMANDO /PAY <nick> <valor> ---
        if (label.equalsIgnoreCase("pay") || label.equalsIgnoreCase("pagar")) {
            if (!(sender instanceof Player)) return true;
            if (args.length < 2) {
                sender.sendMessage("§cUso: /pay <jogador> <quantia>");
                return true;
            }

            Player payer = (Player) sender;
            Player target = Bukkit.getPlayer(args[0]);

            if (target == null || !target.isOnline()) {
                sender.sendMessage("§cO jogador precisa estar online para receber.");
                return true;
            }
            if (target.getName().equals(payer.getName())) {
                sender.sendMessage("§cVocê não pode pagar a si mesmo.");
                return true;
            }

            double amount;
            try {
                amount = Double.parseDouble(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cQuantia inválida.");
                return true;
            }

            if (amount <= 0) {
                sender.sendMessage("§cA quantia deve ser maior que zero.");
                return true;
            }

            transferMoney(payer, target, amount);
            return true;
        }

        // --- COMANDO /ECO (Coins Admin) e /CASH (Cash Admin) ---
        if (label.equalsIgnoreCase("eco") || label.equalsIgnoreCase("cash")) {
            if (!sender.hasPermission("redesplit.admin")) {
                sender.sendMessage("§cSem permissão.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage("§cUso: /" + label + " <give/take/set> <player> <quantia>");
                return true;
            }

            String type = label.equalsIgnoreCase("cash") ? "cash" : "coins";
            String action = args[0].toLowerCase();
            String targetName = args[1];
            double amount;
            try {
                amount = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cQuantia inválida.");
                return true;
            }

            adminTransaction(sender, targetName, type, action, amount);
            return true;
        }

        return false;
    }

    // --- LÓGICA: TRANSFERÊNCIA (/PAY) ---
    private void transferMoney(Player from, Player to, double amount) {
        SplitPlayer spFrom = RedeSplitCore.getInstance().getPlayerManager().getPlayer(from.getUniqueId());
        SplitPlayer spTo = RedeSplitCore.getInstance().getPlayerManager().getPlayer(to.getUniqueId());

        if (spFrom == null || spTo == null) return;

        if (spFrom.getCoins() < amount) {
            from.sendMessage("§cVocê não tem coins suficientes.");
            return;
        }

        // Transação
        spFrom.setCoins(spFrom.getCoins() - amount);
        spTo.setCoins(spTo.getCoins() + amount);

        // Salva ambos
        RedeSplitCore.getInstance().getPlayerManager().savePlayer(spFrom);
        RedeSplitCore.getInstance().getPlayerManager().savePlayer(spTo);

        from.sendMessage("§aVocê enviou $ " + df.format(amount) + " para " + to.getName());
        to.sendMessage("§aVocê recebeu $ " + df.format(amount) + " de " + from.getName());
    }

    // --- LÓGICA: ADMINISTRAÇÃO (/ECO e /CASH) ---
    private void adminTransaction(CommandSender sender, String targetName, String currencyType, String action, double amount) {
        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {
                // Verifica se jogador existe e pega saldo atual
                PreparedStatement psCheck = conn.prepareStatement("SELECT uuid, " + currencyType + " FROM rs_players WHERE name = ?");
                psCheck.setString(1, targetName);
                ResultSet rs = psCheck.executeQuery();

                if (rs.next()) {
                    String uuid = rs.getString("uuid");
                    double currentBalance = rs.getDouble(currencyType);
                    double newBalance = currentBalance;

                    switch (action) {
                        case "give": newBalance += amount; break;
                        case "take": newBalance = Math.max(0, newBalance - amount); break;
                        case "set": newBalance = amount; break;
                        default:
                            sender.sendMessage("§cAção inválida. Use: give, take, set.");
                            return;
                    }

                    // Atualiza DB
                    PreparedStatement psUp = conn.prepareStatement("UPDATE rs_players SET " + currencyType + " = ? WHERE uuid = ?");
                    psUp.setDouble(1, newBalance);
                    psUp.setString(2, uuid);
                    psUp.executeUpdate();

                    sender.sendMessage("§aSaldo de " + targetName + " atualizado para " + df.format(newBalance) + " (" + currencyType + ")");

                    // Atualiza em tempo real se online
                    double finalBalance = newBalance;
                    Bukkit.getScheduler().runTask(RedeSplitCore.getInstance(), () -> {
                        Player p = Bukkit.getPlayer(targetName);
                        if (p != null) {
                            SplitPlayer sp = RedeSplitCore.getInstance().getPlayerManager().getPlayer(p.getUniqueId());
                            if (sp != null) {
                                if (currencyType.equals("coins")) sp.setCoins(finalBalance);
                                else sp.setCash(finalBalance);
                                p.sendMessage("§eSeu saldo de " + currencyType + " foi alterado.");
                            }
                        }
                    });

                } else {
                    sender.sendMessage("§cJogador não encontrado no banco de dados.");
                }

            } catch (SQLException e) {
                e.printStackTrace();
                sender.sendMessage("§cErro SQL.");
            }
        });
    }

    private void viewOtherBalance(CommandSender sender, String targetName) {
        Bukkit.getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try (Connection conn = RedeSplitCore.getInstance().getMySQL().getConnection()) {
                PreparedStatement ps = conn.prepareStatement("SELECT coins, cash FROM rs_players WHERE name = ?");
                ps.setString(1, targetName);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    double c = rs.getDouble("coins");
                    double ca = rs.getDouble("cash");
                    sender.sendMessage("§eSaldo de " + targetName + ": §fCoins: " + df.format(c) + " | Cash: " + df.format(ca));
                } else {
                    sender.sendMessage("§cJogador não encontrado.");
                }
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }
}