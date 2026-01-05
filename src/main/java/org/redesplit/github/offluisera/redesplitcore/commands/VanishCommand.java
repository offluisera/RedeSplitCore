package org.redesplit.github.offluisera.redesplitcore.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;

public class VanishCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cApenas jogadores.");
            return true;
        }

        Player p = (Player) sender;
        if (!p.hasPermission("redesplit.vanish")) {
            sender.sendMessage("§cSem permissão.");
            return true;
        }

        // 1. Executa a lógica de Vanish
        RedeSplitCore.getInstance().getVanishManager().toggleVanish(p);

        // 2. Pega os estados para atualizar o painel
        boolean vanished = RedeSplitCore.getInstance().getVanishManager().isVanished(p);
        // Se você tiver um StaffManager, pegue o estado do StaffMode aqui:
        boolean staffMode = false;

        // 3. Envia para o Banco de Dados
        RedeSplitCore.getInstance().getMySQL().updateStaffStatus(p.getName(), vanished, staffMode);

        p.sendMessage(vanished ? "§aVanish ativado!" : "§cVanish desativado!");
        return true;
    }
}