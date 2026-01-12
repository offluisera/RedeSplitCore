package org.redesplit.github.offluisera.redesplitcore.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;

/**
 * Comando /vinculardc
 * Gera código para vincular Discord
 */
public class DiscordLinkCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage("§cApenas jogadores podem usar este comando!");
            return true;
        }

        Player player = (Player) sender;

        // Verifica se já está vinculado
        if (RedeSplitCore.getInstance().getDiscordLinkManager().isLinked(player.getUniqueId())) {
            String discordTag = RedeSplitCore.getInstance().getDiscordLinkManager().getDiscordTag(player.getUniqueId());
            player.sendMessage("");
            player.sendMessage("§a§l✔ DISCORD JÁ VINCULADO!");
            player.sendMessage("§aSua conta está vinculada a: §f" + discordTag);
            player.sendMessage("");
            player.sendMessage("§7Para desvincular, contate um administrador.");
            player.sendMessage("");
            return true;
        }

        // Gera novo código
        String code = RedeSplitCore.getInstance().getDiscordLinkManager().generateCode(player);

        // Mensagem bonita
        player.sendMessage("");
        player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("");
        player.sendMessage("       §e§lVINCULAÇÃO DISCORD");
        player.sendMessage("");
        player.sendMessage("     §fPara vincular sua conta, siga os passos:");
        player.sendMessage("");
        player.sendMessage("     §71. Abra o Discord");
        player.sendMessage("     §72. Envie uma mensagem privada para o bot");
        player.sendMessage("     §73. Use o comando:");
        player.sendMessage("");
        player.sendMessage("        §e/vinculardc §f" + code);
        player.sendMessage("");
        player.sendMessage("     §c§l⚠ §cEste código expira em 10 minutos!");
        player.sendMessage("");
        player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("");

        return true;
    }
}