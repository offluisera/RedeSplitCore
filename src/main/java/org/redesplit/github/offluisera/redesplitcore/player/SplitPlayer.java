package org.redesplit.github.offluisera.redesplitcore.player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SplitPlayer {

    private final UUID uuid;
    private final String name;

    private String rankId;
    private double coins;
    private double cash;

    private String muteReason = null;   // Novo
    private String muteOperator = null;

    // Lista para guardar as permissões do banco
    private final Set<String> permissions = new HashSet<>();

    // Guarda o momento exato do login para calcular o tempo jogado
    private final long loginTimestamp;

    // NOVO CAMPO: Timestamp de quando acaba o mute (0 = sem mute)
    private long muteExpires = 0;

    private long xp;
    private int level;

    public SplitPlayer(UUID uuid, String name, String rankId, double coins, double cash) {
        this.uuid = uuid;
        this.name = name;
        this.rankId = rankId;
        this.coins = coins;
        this.cash = cash;

        // Inicializa o contador de tempo no momento da criação do objeto
        this.loginTimestamp = System.currentTimeMillis();

        // Por padrão inicia sem mute (será carregado depois pelo PlayerManager)
        this.muteExpires = 0;

        this.xp = 0;
        this.level = 1;
    }

    // --- Getters e Setters ---

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }

    public String getRankId() { return rankId; }
    public void setRankId(String rankId) { this.rankId = rankId; }

    public double getCoins() { return coins; }
    public void setCoins(double coins) { this.coins = coins; }

    // Métodos úteis para facilitar manipulação de economia
    public void addCoins(double amount) { this.coins += amount; }
    public void removeCoins(double amount) { this.coins -= amount; }

    public double getCash() { return cash; }
    public void setCash(double cash) { this.cash = cash; }

    public Set<String> getPermissions() { return permissions; }

    /**
     * Retorna quantos milissegundos o jogador ficou online nesta sessão.
     * Usado pelo PlayerManager para somar ao 'playtime' do banco de dados.
     */
    public long getSessionPlaytime() {
        return System.currentTimeMillis() - this.loginTimestamp;
    }

    // --- LÓGICA DE MUTE (NOVO) ---

    public long getMuteExpires() {
        return muteExpires;
    }

    public void setMuteExpires(long muteExpires) {
        this.muteExpires = muteExpires;
    }

    public String getMuteReason() { return muteReason; }
    public void setMuteReason(String muteReason) { this.muteReason = muteReason; }

    public String getMuteOperator() { return muteOperator; }
    public void setMuteOperator(String muteOperator) { this.muteOperator = muteOperator; }

    /**
     * Verifica se o jogador está mutado neste exato momento.
     * Retorna true se o tempo de expiração for maior que a hora atual.
     */
    public boolean isMuted() {
        return muteExpires > System.currentTimeMillis();
    }

    // GETTERS E SETTERS DE XP

    public long getXp() {return xp;}
    public void setXp(long xp) {this.xp = xp;updateLevel();}
    public void addXp(long amount) {this.xp += amount;updateLevel();}
    public void removeXp(long amount) {this.xp = Math.max(0, this.xp - amount);updateLevel();}
    public int getLevel() {return level;}
    public void setLevel(int level) {this.level = Math.max(1, level);}
    private void updateLevel() {int newLevel = calculateLevel(this.xp);if (newLevel != this.level) {this.level = newLevel;}}
    public static int calculateLevel(long xp) {return (int) Math.max(1, (xp / 1000) + 1);}
    public long getXpToNextLevel() {long nextLevelXp = level * 1000L;return Math.max(0, nextLevelXp - xp);}
    public double getProgressToNextLevel() {
        long currentLevelXp = (level - 1) * 1000L;
        long nextLevelXp = level * 1000L;
        long progressXp = xp - currentLevelXp;
        long requiredXp = nextLevelXp - currentLevelXp;
        return Math.min(100.0, (progressXp * 100.0) / requiredXp);
    }
}