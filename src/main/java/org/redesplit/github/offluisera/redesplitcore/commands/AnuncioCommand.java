package org.redesplit.github.offluisera.redesplitcore.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class AnuncioCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Apenas o console ou administradores podem usar
        if (args.length == 0) return true;

        // Junta os argumentos e traduz as cores (§ ou &)
        StringBuilder message = new StringBuilder();
        for (String arg : args) {
            message.append(arg).append(" ");
        }

        String finalMessage = ChatColor.translateAlternateColorCodes('&', message.toString().trim());

        // Envia para todos os jogadores online
        Bukkit.broadcastMessage(finalMessage);
        return true;
    }
}