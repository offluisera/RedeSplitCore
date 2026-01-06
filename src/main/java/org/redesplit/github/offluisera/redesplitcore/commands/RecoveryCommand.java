package org.redesplit.github.offluisera.redesplitcore.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.redesplit.github.offluisera.redesplitcore.system.PasswordRecovery;

/**
 * Comando /recovery para recuperar senha perdida
 *
 * Uso:
 * /recovery request <email> - Solicita código de recuperação
 * /recovery <codigo> <nova_senha> - Define nova senha
 */
public class RecoveryCommand implements CommandExecutor {

    private final PasswordRecovery recovery;

    public RecoveryCommand(PasswordRecovery recovery) {
        this.recovery = recovery;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cApenas jogadores podem usar este comando!");
            return true;
        }

        Player player = (Player) sender;

        // === SOLICITAR CÓDIGO ===
        // /recovery request <email>
        if (args.length >= 2 && args[0].equalsIgnoreCase("request")) {
            String email = args[1];
            recovery.requestRecovery(player, email);
            return true;
        }

        // === REDEFINIR SENHA ===
        // /recovery <codigo> <nova_senha>
        if (args.length == 2) {
            String code = args[0];
            String newPassword = args[1];
            recovery.completeRecovery(player, code, newPassword);
            return true;
        }

        // === MENSAGEM DE AJUDA ===
        player.sendMessage("");
        player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("");
        player.sendMessage("  §e§lRECUPERAÇÃO DE SENHA");
        player.sendMessage("");
        player.sendMessage("  §f1. Solicite um código:");
        player.sendMessage("     §e/recovery request <email>");
        player.sendMessage("");
        player.sendMessage("  §f2. Use o código para redefinir:");
        player.sendMessage("     §e/recovery <codigo> <nova_senha>");
        player.sendMessage("");
        player.sendMessage("  §7O código expira em 5 minutos.");
        player.sendMessage("");
        player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("");
        return true;
    }
}