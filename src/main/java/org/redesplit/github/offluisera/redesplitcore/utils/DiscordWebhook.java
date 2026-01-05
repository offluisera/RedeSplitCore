package org.redesplit.github.offluisera.redesplitcore.utils;

import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DiscordWebhook {

    // COLOQUE SEU LINK DO WEBHOOK AQUI
    private static final String WEBHOOK_URL = "https://discord.com/api/webhooks/1455458097723346955/nDfep7OM3W8Fi7GceSgQ4ynNpU4SfzX5h-YRMftLQC0mEqJJPJ6iNx5dcuoqplETYyIW";

    public static void send(String username, String content) {
        // Roda assincronamente para não travar o servidor
        RedeSplitCore.getInstance().getServer().getScheduler().runTaskAsynchronously(RedeSplitCore.getInstance(), () -> {
            try {
                URL url = new URL(WEBHOOK_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");

                // Monta o JSON simples
                String json = "{\"username\": \"" + username + "\", \"content\": \"" + content + "\"}";

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                conn.getInputStream().close(); // Dispara a requisição
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}