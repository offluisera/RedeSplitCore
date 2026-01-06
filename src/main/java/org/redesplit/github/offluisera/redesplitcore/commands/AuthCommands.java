package org.redesplit.github.offluisera.redesplitcore.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.redesplit.github.offluisera.redesplitcore.system.AuthSystem;

/**
 * Comandos do sistema de autenticação
 */
public class AuthCommands implements CommandExecutor {

    private final AuthSystem authSystem;

    public AuthCommands(AuthSystem authSystem) {
        this.authSystem = authSystem;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // Apenas jogadores podem usar
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cApenas jogadores podem usar este comando!");
            return true;
        }

        Player player = (Player) sender;
        String cmd = command.getName().toLowerCase();

        // ========================================
        // COMANDO: /LOGIN <senha>
        // ========================================
        if (cmd.equals("login") || cmd.equals("logar")) {

            // Já está autenticado
            if (authSystem.isAuthenticated(player.getUniqueId())) {
                player.sendMessage("§c§lERRO: §cVocê já está autenticado!");
                return true;
            }

            // Não passou a senha
            if (args.length != 1) {
                player.sendMessage("§c§lERRO: §cUse: /login <senha>");
                return true;
            }

            // Não está registrado
            if (!authSystem.isRegistered(player.getUniqueId())) {
                player.sendMessage("§c§lERRO: §cVocê não está registrado!");
                player.sendMessage("§eUse: §f/register <senha> [email]");
                return true;
            }

            // Processa o login
            String password = args[0];
            authSystem.login(player, password);
            return true;
        }

        // ========================================
        // COMANDO: /REGISTER <senha> [email]
        // ========================================
        if (cmd.equals("register") || cmd.equals("registrar")) {

            // Já está autenticado
            if (authSystem.isAuthenticated(player.getUniqueId())) {
                player.sendMessage("§c§lERRO: §cVocê já está autenticado!");
                return true;
            }

            // Já está registrado
            if (authSystem.isRegistered(player.getUniqueId())) {
                player.sendMessage("§c§lERRO: §cVocê já está registrado!");
                player.sendMessage("§eUse: §f/login <senha>");
                return true;
            }

            // Sintaxe incorreta
            if (args.length < 1 || args.length > 2) {
                player.sendMessage("§c§lERRO: §cUse: /register <senha> [email]");
                player.sendMessage("§7Exemplo: §f/register MinhaSenh@123");
                player.sendMessage("§7Com email: §f/register MinhaSenh@123 [email protected]");
                return true;
            }

            String password = args[0];
            String email = (args.length == 2) ? args[1] : null;

            // Valida email se fornecido
            if (email != null && !isValidEmail(email)) {
                player.sendMessage("§c§lERRO: §cEmail inválido!");
                player.sendMessage("§eUse um formato válido, como: §f[email protected]");
                return true;
            }

            // Processa o registro
            authSystem.register(player, password, email);
            return true;
        }

        // ========================================
        // COMANDO: /CHANGEPASSWORD <senha_atual> <senha_nova>
        // ========================================
        if (cmd.equals("changepassword") || cmd.equals("trocarsenha")) {

            // Precisa estar autenticado para trocar senha
            if (!authSystem.isAuthenticated(player.getUniqueId())) {
                player.sendMessage("§c§lERRO: §cVocê precisa estar autenticado!");
                return true;
            }

            // Sintaxe incorreta
            if (args.length != 2) {
                player.sendMessage("§c§lERRO: §cUse: /changepassword <senha_atual> <senha_nova>");
                player.sendMessage("§7Exemplo: §f/changepassword SenhaAntiga123 SenhaNova456");
                return true;
            }

            String oldPassword = args[0];
            String newPassword = args[1];

            // Senhas iguais
            if (oldPassword.equals(newPassword)) {
                player.sendMessage("§c§lERRO: §cA nova senha deve ser diferente da atual!");
                return true;
            }

            // Processa a mudança
            authSystem.changePassword(player, oldPassword, newPassword);
            return true;
        }

        return false;
    }

    /**
     * Valida se um email está em formato correto
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) return false;
        // Regex básico para validar email
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email.matches(emailRegex);
    }
}