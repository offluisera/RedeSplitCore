package org.redesplit.github.offluisera.redesplitcore.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.redesplit.github.offluisera.redesplitcore.system.PasswordRecovery;

/**
 * Comando /recovery para recuperar senha perdida
 *
 * Fluxo:
 * 1. /recovery request - Solicita recuperação
 * 2. /recovery confirmar - Envia código via Discord
 * 3. /recovery <codigo> <nova_senha> - Define nova senha
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

        // === PASSO 1: SOLICITAR RECUPERAÇÃO ===
        // /recovery request
        if (args.length >= 1 && args[0].equalsIgnoreCase("request")) {
            recovery.requestRecovery(player);
            return true;
        }

        // === PASSO 2: CONFIRMAR E ENVIAR CÓDIGO ===
        // /recovery confirmar
        if (args.length >= 1 && args[0].equalsIgnoreCase("confirmar")) {
            recovery.confirmRecovery(player);
            return true;
        }

        // === PASSO 3: REDEFINIR SENHA ===
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
        player.sendMessage("  §f1. Solicite recuperação:");
        player.sendMessage("     §e/recovery request");
        player.sendMessage("");
        player.sendMessage("  §f2. Confirme para receber o código:");
        player.sendMessage("     §e/recovery confirmar");
        player.sendMessage("");
        player.sendMessage("  §f3. Use o código do Discord:");
        player.sendMessage("     §e/recovery <codigo> <nova_senha>");
        player.sendMessage("");
        player.sendMessage("  §7O código expira em 5 minutos.");
        player.sendMessage("");
        player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("");
        return true;
    }
}