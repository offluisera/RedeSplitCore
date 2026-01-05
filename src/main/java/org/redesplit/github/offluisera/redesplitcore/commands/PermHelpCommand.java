package org.redesplit.github.offluisera.redesplitcore.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class PermHelpCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("redesplit.admin")) {
            sender.sendMessage("§cSem permissão.");
            return true;
        }

        sender.sendMessage("");
        sender.sendMessage("§e§lSISTEMA DE PERMISSÕES - AJUDA");
        sender.sendMessage("");

        // Comandos de Jogador
        sendHelp(sender, "/setrank <player> <rank>", "Define o rank principal.");
        sendHelp(sender, "/settemprank <player> <tempo> <rank>", "Rank temporário (ex: 30d).");
        sendHelp(sender, "/check <player>", "Vê rank e informações.");

        // Comandos de Grupo
        sendHelp(sender, "/ranks", "Lista todos os cargos.");
        sendHelp(sender, "/setparent <filho> <pai>", "Adiciona herança.");
        sendHelp(sender, "/perm rank <grupo> add <perm>", "Adiciona permissão (exemplo).");

        // Comandos de Keys
        sendHelp(sender, "/ativar <codigo>", "Ativa uma key/cupom.");

        sender.sendMessage("");
        sender.sendMessage("§7* Use o Painel Web para configurações avançadas.");
        return true;
    }

    private void sendHelp(CommandSender sender, String cmd, String desc) {
        sender.sendMessage("§6" + cmd + " §7- " + desc);
    }
}