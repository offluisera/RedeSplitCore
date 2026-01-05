package org.redesplit.github.offluisera.redesplitcore.tasks;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.scheduler.BukkitRunnable;
import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;

public class RestartTask extends BukkitRunnable {

    private int seconds;

    public RestartTask(int minutes) {
        this.seconds = minutes * 60;
    }

    @Override
    public void run() {
        if (!RedeSplitCore.getInstance().isRestarting()) {
            RedeSplitCore.getInstance().setRestarting(true);
        }

        boolean isMinute = (seconds > 0 && seconds % 60 == 0);
        boolean isFinalCountdown = (seconds > 0 && seconds <= 5);

        if (isMinute || isFinalCountdown) {
            String timeMsg = formatTime(seconds);

            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage("§c§l⚠ AVISO DE REINICIALIZAÇÃO ⚠");
            Bukkit.broadcastMessage("§cO servidor irá reiniciar em: §f" + timeMsg);
            Bukkit.broadcastMessage("§cA entrada de novos jogadores foi bloqueada.");
            Bukkit.broadcastMessage("");

            // --- O TRUQUE DE SOM (CONTORNO) ---
            // Usamos playSoundSafe para tentar tocar o som antigo.
            // Se não existir, ele toca um som padrão e não trava o plugin.
            Bukkit.getOnlinePlayers().forEach(p ->
                    playSoundSafe(p, "ANVIL_LAND", "BLOCK_ANVIL_LAND") // Tenta nome antigo, depois o novo
            );
        }

        if (seconds <= 0) {
            this.cancel();
            Bukkit.broadcastMessage("§c§lReiniciando agora...");
            Bukkit.getOnlinePlayers().forEach(p ->
                    p.kickPlayer("§cServidor Reiniciando...\n§eVolte em 1 minuto!"));
            Bukkit.shutdown();
        }

        seconds--;
    }

    // Método auxiliar para evitar o erro "NoSuchFieldError"
    private void playSoundSafe(org.bukkit.entity.Player p, String oldSound, String newSound) {
        try {
            // Tenta forçar o som da 1.8 pelo nome em texto
            p.playSound(p.getLocation(), Sound.valueOf(oldSound), 1f, 1f);
        } catch (IllegalArgumentException | NoSuchFieldError e1) {
            try {
                // Se falhar, tenta o som novo (caso você mude de versão no futuro)
                p.playSound(p.getLocation(), Sound.valueOf(newSound), 1f, 1f);
            } catch (Exception e2) {
                // Se tudo falhar, toca um som que existe desde o Alpha (Level Up)
                // Assim o código NUNCA quebra.
                try {
                    p.playSound(p.getLocation(), Sound.valueOf("LEVEL_UP"), 1f, 1f);
                } catch (Exception ignored) {}
            }
        }
    }

    private String formatTime(int s) {
        if (s >= 60) {
            int minutes = s / 60;
            return minutes + (minutes == 1 ? " minuto" : " minutos");
        }
        return s + " segundos";
    }
}