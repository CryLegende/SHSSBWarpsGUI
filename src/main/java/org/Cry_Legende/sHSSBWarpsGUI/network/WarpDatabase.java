package org.Cry_Legende.sHSSBWarpsGUI.network;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Favoris, compteurs de visites et warps mis en avant en MariaDB, partagés par tous les serveurs. Chargée seulement
 * avec SHNetwork (HikariCP et le pilote MariaDB viennent de {@code libraries:} dans plugin.yml).
 *
 * <p>Toutes les méthodes bloquent : à appeler hors du fil principal.</p>
 */
public final class WarpDatabase {

    /** Warp mis en avant tel qu'il est écrit en base. */
    public record FeaturedRow(UUID owner, String warpName, long expiry) {
    }

    private final HikariDataSource pool;

    public WarpDatabase(String url, String user, String password, int timeoutMs) {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("SHSSBWarpsGUI");
        hikari.setJdbcUrl(url);
        hikari.setUsername(user);
        hikari.setPassword(password);
        hikari.setDriverClassName("org.mariadb.jdbc.Driver");
        hikari.addDataSourceProperty("connectTimeout", String.valueOf(timeoutMs));
        hikari.addDataSourceProperty("socketTimeout", String.valueOf(timeoutMs));
        hikari.setMaximumPoolSize(2);
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(timeoutMs);
        // le serveur démarre même si la base est absente : le plugin garde alors ce qu'il a en mémoire
        hikari.setInitializationFailTimeout(-1);
        hikari.setKeepaliveTime(60_000);
        this.pool = new HikariDataSource(hikari);
    }

    /** Clé d'un warp en base : son nom en minuscules, comme les compteurs de visites de WarpVisitManager. */
    public static String key(String warpName) {
        return warpName.toLowerCase(Locale.ROOT);
    }

    /** Crée les tables si elles manquent. Des tables déjà là ne demandent aucun droit CREATE à l'utilisateur. */
    public void createTables() throws SQLException {
        try (Connection c = pool.getConnection(); Statement st = c.createStatement()) {
            createIfMissing(st, "shssbwarps_favorites", "CREATE TABLE IF NOT EXISTS shssbwarps_favorites ("
                + "viewer CHAR(36) NOT NULL, "
                + "target CHAR(36) NOT NULL, "
                + "PRIMARY KEY (viewer, target)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
            createIfMissing(st, "shssbwarps_visits", "CREATE TABLE IF NOT EXISTS shssbwarps_visits ("
                + "owner CHAR(36) NOT NULL, "
                + "warp VARCHAR(100) NOT NULL, "
                + "visits INT NOT NULL DEFAULT 0, "
                + "PRIMARY KEY (owner, warp)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
            createIfMissing(st, "shssbwarps_featured", "CREATE TABLE IF NOT EXISTS shssbwarps_featured ("
                + "owner CHAR(36) NOT NULL, "
                + "warp VARCHAR(100) NOT NULL, "
                + "warp_name VARCHAR(100) NOT NULL, "
                + "expiry BIGINT NOT NULL, "
                + "PRIMARY KEY (owner, warp), "
                + "KEY idx_expiry (expiry)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
        }
    }

    private static void createIfMissing(Statement st, String table, String create) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT 1 FROM " + table + " LIMIT 1")) {
            return;
        } catch (SQLException missing) {
            // la table n'existe pas encore : création ci-dessous
        }
        st.executeUpdate(create);
    }

    // ------------------------------------------------------------------ favoris

    public List<UUID> loadFavorites(UUID viewer) throws SQLException {
        List<UUID> targets = new ArrayList<>();
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT target FROM shssbwarps_favorites WHERE viewer = ?")) {
            ps.setString(1, viewer.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    targets.add(UUID.fromString(rs.getString(1)));
                }
            }
        }
        return targets;
    }

    public void addFavorite(UUID viewer, UUID target) throws SQLException {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT IGNORE INTO shssbwarps_favorites (viewer, target) VALUES (?, ?)")) {
            ps.setString(1, viewer.toString());
            ps.setString(2, target.toString());
            ps.executeUpdate();
        }
    }

    public void removeFavorite(UUID viewer, UUID target) throws SQLException {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM shssbwarps_favorites WHERE viewer = ? AND target = ?")) {
            ps.setString(1, viewer.toString());
            ps.setString(2, target.toString());
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------ visites

    /** Ajoute une visite par incrément atomique et rend le total qui en résulte (celui de tous les serveurs). */
    public int incrementVisit(UUID owner, String warpKey) throws SQLException {
        try (Connection c = pool.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO shssbwarps_visits (owner, warp, visits) VALUES (?, ?, 1) "
                + "ON DUPLICATE KEY UPDATE visits = visits + 1")) {
                ps.setString(1, owner.toString());
                ps.setString(2, warpKey);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT visits FROM shssbwarps_visits WHERE owner = ? AND warp = ?")) {
                ps.setString(1, owner.toString());
                ps.setString(2, warpKey);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 1;
                }
            }
        }
    }

    /** Tous les compteurs : propriétaire -> (warp en minuscules -> visites). */
    public Map<UUID, Map<String, Integer>> loadVisits() throws SQLException {
        Map<UUID, Map<String, Integer>> all = new HashMap<>();
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT owner, warp, visits FROM shssbwarps_visits");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID owner;
                try {
                    owner = UUID.fromString(rs.getString(1));
                } catch (IllegalArgumentException invalid) {
                    continue;
                }
                all.computeIfAbsent(owner, k -> new HashMap<>()).put(rs.getString(2), rs.getInt(3));
            }
        }
        return all;
    }

    // ------------------------------------------------------------------ warps mis en avant

    public List<FeaturedRow> loadFeatured() throws SQLException {
        List<FeaturedRow> rows = new ArrayList<>();
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT owner, warp_name, expiry FROM shssbwarps_featured");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    rows.add(new FeaturedRow(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getLong(3)));
                } catch (IllegalArgumentException invalid) {
                    // ligne illisible : ignorée
                }
            }
        }
        return rows;
    }

    /** Met en avant un warp, ou prolonge sa mise en avant. */
    public void upsertFeatured(UUID owner, String warpName, long expiry) throws SQLException {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO shssbwarps_featured (owner, warp, warp_name, expiry) VALUES (?, ?, ?, ?) "
                 + "ON DUPLICATE KEY UPDATE warp_name = VALUES(warp_name), expiry = VALUES(expiry)")) {
            ps.setString(1, owner.toString());
            ps.setString(2, key(warpName));
            ps.setString(3, warpName);
            ps.setLong(4, expiry);
            ps.executeUpdate();
        }
    }

    public void removeFeatured(UUID owner, String warpName) throws SQLException {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM shssbwarps_featured WHERE owner = ? AND warp = ?")) {
            ps.setString(1, owner.toString());
            ps.setString(2, key(warpName));
            ps.executeUpdate();
        }
    }

    /**
     * Retire une mise en avant échue. Vrai pour un seul serveur : celui dont la suppression a touché la ligne (une
     * prolongation entre-temps a changé l'échéance et la ligne n'est plus retirée).
     */
    public boolean claimExpired(UUID owner, String warpName, long expiry) throws SQLException {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM shssbwarps_featured WHERE owner = ? AND warp = ? AND expiry = ?")) {
            ps.setString(1, owner.toString());
            ps.setString(2, key(warpName));
            ps.setLong(3, expiry);
            return ps.executeUpdate() == 1;
        }
    }

    // ------------------------------------------------------------------ reprise des fichiers

    /** Copie des favoris d'un fichier : une ligne déjà en base n'est pas touchée. */
    public int importFavorites(Map<UUID, List<UUID>> favorites) throws SQLException {
        int rows = 0;
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT IGNORE INTO shssbwarps_favorites (viewer, target) VALUES (?, ?)")) {
            for (Map.Entry<UUID, List<UUID>> entry : favorites.entrySet()) {
                for (UUID target : entry.getValue()) {
                    ps.setString(1, entry.getKey().toString());
                    ps.setString(2, target.toString());
                    rows += ps.executeUpdate();
                }
            }
        }
        return rows;
    }

    /** Copie des compteurs d'un fichier : le plus grand des deux totaux est gardé, jamais une somme (l'import peut être relancé). */
    public int importVisits(Map<UUID, Map<String, Integer>> visits) throws SQLException {
        int rows = 0;
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO shssbwarps_visits (owner, warp, visits) VALUES (?, ?, ?) "
                 + "ON DUPLICATE KEY UPDATE visits = GREATEST(visits, VALUES(visits))")) {
            for (Map.Entry<UUID, Map<String, Integer>> owner : visits.entrySet()) {
                for (Map.Entry<String, Integer> warp : owner.getValue().entrySet()) {
                    ps.setString(1, owner.getKey().toString());
                    ps.setString(2, key(warp.getKey()));
                    ps.setInt(3, warp.getValue());
                    ps.executeUpdate();
                    rows++;
                }
            }
        }
        return rows;
    }

    /** Copie des mises en avant d'un fichier : l'échéance la plus tardive est gardée. */
    public int importFeatured(List<FeaturedRow> featured) throws SQLException {
        int rows = 0;
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO shssbwarps_featured (owner, warp, warp_name, expiry) VALUES (?, ?, ?, ?) "
                 + "ON DUPLICATE KEY UPDATE expiry = GREATEST(expiry, VALUES(expiry))")) {
            for (FeaturedRow row : featured) {
                ps.setString(1, row.owner().toString());
                ps.setString(2, key(row.warpName()));
                ps.setString(3, row.warpName());
                ps.setLong(4, row.expiry());
                ps.executeUpdate();
                rows++;
            }
        }
        return rows;
    }

    // ------------------------------------------------------------------ îles hébergées (table de SHSkyBridge)

    /** Hôte inscrit pour une île dans island_host (SHSkyBridge), ou null si l'île n'y a pas de ligne. */
    public String islandHost(UUID island) throws SQLException {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT server FROM island_host WHERE island_uuid = ?")) {
            ps.setString(1, island.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1).toLowerCase(Locale.ROOT) : null;
            }
        }
    }

    /** Toutes les lignes de island_host : île -> serveur. */
    public Map<UUID, String> loadIslandHosts() throws SQLException {
        Map<UUID, String> hosts = new HashMap<>();
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT island_uuid, server FROM island_host");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    hosts.put(UUID.fromString(rs.getString(1)), rs.getString(2).toLowerCase(Locale.ROOT));
                } catch (IllegalArgumentException invalid) {
                    // ligne illisible : ignorée
                }
            }
        }
        return hosts;
    }

    public void close() {
        pool.close();
    }
}
