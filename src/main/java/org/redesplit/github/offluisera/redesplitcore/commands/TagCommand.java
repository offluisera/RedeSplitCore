package org.redesplit.github.offluisera.redesplitcore.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.redesplit.github.offluisera.redesplitcore.player.TagManager;

public class TagCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!sender.hasPermission("redesplitcore.admin")) {
            sender.sendMessage("§cVocê não tem permissão!");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§cUso: /tag <reload|update|list>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
            case "update":
                // Atualiza as tags de todos os jogadores
                TagManager.updateAll();
                sender.sendMessage("§aTags atualizadas para todos os jogadores!");

                // Log no console
                Bukkit.getConsoleSender().sendMessage("§a[RedeSplitCore] Tags atualizadas por " + sender.getName());
                break;

            case "list":
                // Lista todos os jogadores e suas tags
                sender.sendMessage("§e§m------------------------------");
                sender.sendMessage("§e  Lista de Tags Ativas");
                sender.sendMessage("§e§m------------------------------");

                for (Player p : Bukkit.getOnlinePlayers()) {
                    String tabName = p.getPlayerListName();
                    sender.sendMessage("  " + tabName);
                }

                sender.sendMessage("§e§m------------------------------");
                break;

            default:
                sender.sendMessage("§cUso: /tag <reload|update|list>");
                break;
        }

        return true;
    }
}