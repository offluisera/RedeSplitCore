package org.redesplit.github.offluisera.redesplitcore.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import org.redesplit.github.offluisera.redesplitcore.managers.XPManager;
import org.redesplit.github.offluisera.redesplitcore.player.SplitPlayer;

public class XPCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        // /xp - Ver seu XP
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cApenas jogadores podem usar este comando!");
                return true;
            }

            Player p = (Player) sender;
            SplitPlayer sp = RedeSplitCore.getInstance().getPlayerManager().getPlayer(p.getUniqueId());

            if (sp == null) {
                p.sendMessage("§cDados não carregados!");
                return true;
            }

            p.sendMessage("");
            p.sendMessage("§6§l⭐ SEU XP");
            p.sendMessage("§e  Nível: " + XPManager.getLevelBadge(sp.getLevel()));
            p.sendMessage("§e  XP Total: §f" + sp.getXp());
            p.sendMessage("§e  XP para Próximo Nível: §f" + sp.getXpToNextLevel());
            p.sendMessage("§e  Progresso: §f" + String.format("%.1f", sp.getProgressToNextLevel()) + "%");
            p.sendMessage("");
            return true;
        }

        // /xp <add|remove|set> <player> <amount>
        if (!sender.hasPermission("redesplitcore.xp.admin")) {
            sender.sendMessage("§cVocê não tem permissão!");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§cUso: /xp <add|remove|set> <jogador> <quantidade>");
            return true;
        }

        String action = args[0].toLowerCase();
        Player target = Bukkit.getPlayer(args[1]);

        if (target == null) {
            sender.sendMessage("§cJogador não encontrado!");
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cQuantidade inválida!");
            return true;
        }

        XPManager xpManager = RedeSplitCore.getInstance().getXPManager();
        String operatorName = sender instanceof Player ? sender.getName() : "CONSOLE";

        switch (action) {
            case "add":
                xpManager.addXP(target, amount, "Adicionado por " + operatorName);
                sender.sendMessage("§a+ §e" + amount + " XP §apara §f" + target.getName());
                break;

            case "remove":
                xpManager.removeXP(target, amount, "Removido por " + operatorName);
                sender.sendMessage("§c- §e" + amount + " XP §cde §f" + target.getName());
                break;

            case "set":
                xpManager.setXP(target, amount, operatorName);
                sender.sendMessage("§eXP de §f" + target.getName() + " §edefinido para §f" + amount);
                break;

            default:
                sender.sendMessage("§cAção inválida! Use: add, remove ou set");
                break;
        }

        return true;
    }
}