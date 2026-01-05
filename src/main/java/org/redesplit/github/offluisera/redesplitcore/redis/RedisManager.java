package org.redesplit.github.offluisera.redesplitcore.redis;

import org.redesplit.github.offluisera.redesplitcore.RedeSplitCore;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisManager {

    private JedisPool pool;

    // Configurações (Idealmente deveriam vir do config.yml, mas mantive as suas para facilitar)
    private final String HOST = "82.39.107.62";
    private final int PORT = 6379;
    private final String PASSWORD = "UHAFDjbnakfye@@jouiayhfiqwer903";

    private Thread subscriberThread;
    private RedisSubscriber currentSubscriber;

    public void connect() {
        try {
            // 1. Configuração do Pool de Conexões
            JedisPoolConfig config = new JedisPoolConfig();
            config.setMaxTotal(16);
            config.setMaxIdle(8);
            config.setTestOnBorrow(true); // Testa se a conexão está viva antes de usar

            // 2. Criação do Pool
            this.pool = new JedisPool(config, HOST, PORT, 5000, PASSWORD);

            // 3. Teste de Conexão (Ping)
            try (Jedis jedis = pool.getResource()) {
                String resposta = jedis.ping();
                RedeSplitCore.getInstance().getLogger().info("§a[Redis] Conectado com sucesso! Ping: " + resposta);
            }

            // 4. Inicia a escuta (Subscriber) em uma Thread separada
            startSubscriber();

        } catch (Exception e) {
            RedeSplitCore.getInstance().getLogger().severe("§c[Redis] FALHA AO CONECTAR: " + e.getMessage());
            // Não damos printStackTrace aqui para não poluir o console se o Redis estiver off
        }
    }

    public void startSubscriber() {
        // Proteção: Se já existe uma thread rodando, não cria outra.
        if (subscriberThread != null && subscriberThread.isAlive()) {
            return;
        }

        this.subscriberThread = new Thread(() -> {
            // Loop de reconexão (opcional, mas bom para estabilidade)
            try (Jedis jedis = pool.getResource()) {
                // Instancia sua classe de lógica (RedisSubscriber)
                this.currentSubscriber = new RedisSubscriber();

                RedeSplitCore.getInstance().getLogger().info("§e[Redis] Iniciando escuta no canal 'redesplit:channel'...");

                // ESTA LINHA TRAVA A THREAD (Fica escutando infinitamente)
                jedis.subscribe(currentSubscriber, "redesplit:channel");

            } catch (Exception e) {
                // Se o plugin estiver desativando, erros de conexão são normais
                if (pool != null && !pool.isClosed()) {
                    RedeSplitCore.getInstance().getLogger().warning("§c[Redis] Conexão do Subscriber caiu: " + e.getMessage());
                }
            }
        }, "RedeSplit-RedisSubscriber");

        subscriberThread.start();
    }

    public void stopSubscriber() {
        // 1. Desinscreve do canal (Libera a thread do Jedis)
        if (currentSubscriber != null && currentSubscriber.isSubscribed()) {
            try {
                currentSubscriber.unsubscribe();
            } catch (Exception e) {
                // Ignora erro ao desinscrever
            }
        }

        // 2. Mata a thread se ela ainda estiver viva
        if (subscriberThread != null && subscriberThread.isAlive()) {
            subscriberThread.interrupt();
        }
    }

    public void disconnect() {
        // Para o ouvinte primeiro
        stopSubscriber();

        // Fecha o pool de conexões
        if (pool != null && !pool.isClosed()) {
            pool.close();
            RedeSplitCore.getInstance().getLogger().info("§c[Redis] Desconectado.");
        }
    }

    // Método útil para enviar mensagens (Java -> Site ou Java -> Java)
    public void publish(String channel, String message) {
        if (pool == null || pool.isClosed()) return;

        // Usa 'try-with-resources' para fechar a conexão automaticamente após o uso
        try (Jedis jedis = pool.getResource()) {
            jedis.publish(channel, message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}