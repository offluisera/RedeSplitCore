package org.redesplit.github.offluisera.redesplitcore.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.redesplit.github.offluisera.redesplitcore.managers.PollManager;

public class VoteCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;

        if (args.length != 1) {
            sender.sendMessage("§cUse: /votar <número>");
            return true;
        }

        try {
            int option = Integer.parseInt(args[0]);
            PollManager.vote((Player) sender, option);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cDigite um número válido.");
        }
        return true;
    }
}