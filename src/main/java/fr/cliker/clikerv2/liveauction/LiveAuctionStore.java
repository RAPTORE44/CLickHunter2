package fr.cliker.clikerv2.liveauction;

import fr.cliker.clikerv2.DatabaseManager;
import fr.cliker.clikerv2.rewards.MailStore;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Inscriptions, offres, attribution et débit final sont persistants et transactionnels. */
public final class LiveAuctionStore {
    private final DatabaseManager db;

    public record State(
            String id,
            String phase,
            int lot,
            long deadline,
            long price,
            UUID bidder,
            String name,
            long revision,
            int members,
            List<String> items) {}

    public enum Bid {
        ACCEPTED,
        TOO_LOW,
        FUNDS,
        NOT_REGISTERED,
        CLOSED
    }

    public record Offer(Bid result, State state) {}

    public record Sale(int lot, UUID winner, String name, long price, String payload) {}

    public record Advance(State state, Sale sale) {}

    public LiveAuctionStore(DatabaseManager db) {
        this.db = db;
    }

    public CompletableFuture<State> initialize() {
        return db.submit(
                c -> {
                    try (var s = c.createStatement()) {
                        s.executeUpdate(
                                "CREATE TABLE IF NOT EXISTS live_auctions(id TEXT PRIMARY KEY,phase"
                                    + " TEXT NOT NULL,lot INTEGER NOT NULL DEFAULT 0,deadline"
                                    + " INTEGER NOT NULL,price INTEGER NOT NULL DEFAULT 0,bidder"
                                    + " TEXT,name TEXT,revision INTEGER NOT NULL DEFAULT 0)");
                        s.executeUpdate(
                                "CREATE UNIQUE INDEX IF NOT EXISTS live_one_active ON"
                                        + " live_auctions((1)) WHERE phase<>'DONE'");
                        s.executeUpdate(
                                "CREATE TABLE IF NOT EXISTS live_auction_lots(event TEXT NOT"
                                    + " NULL,slot INTEGER NOT NULL,payload TEXT NOT NULL,state TEXT"
                                    + " NOT NULL DEFAULT 'PENDING',winner TEXT,price INTEGER NOT"
                                    + " NULL DEFAULT 0,PRIMARY KEY(event,slot))");
                        s.executeUpdate(
                                "CREATE TABLE IF NOT EXISTS live_auction_members(event TEXT NOT"
                                        + " NULL,owner TEXT NOT NULL,PRIMARY KEY(event,owner))");
                        s.executeUpdate(
                                "CREATE TABLE IF NOT EXISTS live_auction_bids(event TEXT NOT"
                                    + " NULL,lot INTEGER NOT NULL,owner TEXT NOT NULL,name TEXT NOT"
                                    + " NULL,price INTEGER NOT NULL,PRIMARY KEY(event,lot,price))");
                        s.executeUpdate(
                                "CREATE TABLE IF NOT EXISTS live_auction_entries(event TEXT NOT"
                                    + " NULL,owner TEXT NOT NULL,fee INTEGER NOT NULL,PRIMARY"
                                    + " KEY(event,owner))");
                        try (var r =
                                s.executeQuery(
                                        "SELECT id FROM live_auctions WHERE phase<>'DONE' LIMIT"
                                                + " 1")) {
                            return r.next() ? read(c, r.getString(1)) : null;
                        }
                    } catch (SQLException e) {
                        throw failure(e);
                    }
                });
    }

    private static DatabaseManager.DatabaseException failure(SQLException e) {
        return new DatabaseManager.DatabaseException(e);
    }

    private static State read(Connection c, String id) throws SQLException {
        try (var s =
                c.prepareStatement(
                        "SELECT a.*, (SELECT COUNT(*) FROM live_auction_members m WHERE"
                                + " m.event=a.id) members FROM live_auctions a WHERE a.id=?")) {
            s.setString(1, id);
            try (var r = s.executeQuery()) {
                if (!r.next()) return null;
                var items = new ArrayList<String>();
                try (var l =
                        c.prepareStatement(
                                "SELECT payload FROM live_auction_lots WHERE event=? ORDER BY"
                                        + " slot")) {
                    l.setString(1, id);
                    try (var rows = l.executeQuery()) {
                        while (rows.next()) items.add(rows.getString(1));
                    }
                }
                String bidder = r.getString("bidder");
                return new State(
                        id,
                        r.getString("phase"),
                        r.getInt("lot"),
                        r.getLong("deadline"),
                        r.getLong("price"),
                        bidder == null ? null : UUID.fromString(bidder),
                        r.getString("name"),
                        r.getLong("revision"),
                        r.getInt("members"),
                        List.copyOf(items));
            }
        }
    }

    public CompletableFuture<State> create(String id, List<String> items, long registrationMs) {
        if (items.size() != 3 || registrationMs < 1) throw new IllegalArgumentException();
        var copy = List.copyOf(items);
        return db.submit(
                c -> {
                    try {
                        c.setAutoCommit(false);
                        try (var s =
                                c.prepareStatement(
                                        "INSERT INTO live_auctions(id,phase,deadline)"
                                                + " VALUES(?,'REGISTERING',?)")) {
                            s.setString(1, id);
                            s.setLong(2, System.currentTimeMillis() + registrationMs);
                            s.executeUpdate();
                        }
                        for (int i = 0; i < 3; i++)
                            try (var s =
                                    c.prepareStatement(
                                            "INSERT INTO live_auction_lots(event,slot,payload)"
                                                    + " VALUES(?,?,?)")) {
                                s.setString(1, id);
                                s.setInt(2, i);
                                s.setString(3, copy.get(i));
                                s.executeUpdate();
                            }
                        State state = read(c, id);
                        c.commit();
                        return state;
                    } catch (SQLException e) {
                        rollback(c, e);
                        throw failure(e);
                    }
                });
    }

    public CompletableFuture<Boolean> member(String id, UUID owner) {
        return db.submit(
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    "SELECT 1 FROM live_auction_members WHERE event=? AND"
                                        + " owner=?")) {
                        q.setString(1, id);
                        q.setString(2, owner.toString());
                        try (var r = q.executeQuery()) {
                            return r.next();
                        }
                    } catch (SQLException e) {
                        throw failure(e);
                    }
                });
    }

    public CompletableFuture<Boolean> join(String id, UUID owner) {
        return join(id, owner, 0);
    }

    public CompletableFuture<Boolean> join(String id, UUID owner, long entryFee) {
        if (entryFee < 0 || entryFee > 1_000_000_000_000_000L)
            throw new IllegalArgumentException("Frais invalides");
        return db.submit(
                c -> {
                    try {
                        c.setAutoCommit(false);
                        State state = read(c, id);
                        if (state == null || state.phase.equals("DONE")) {
                            c.rollback();
                            return false;
                        }
                        if (state.phase.equals("REGISTERING")
                                && state.deadline > System.currentTimeMillis()) {
                            boolean paid;
                            try (var q =
                                    c.prepareStatement(
                                            "SELECT 1 FROM live_auction_entries WHERE event=? AND"
                                                + " owner=?")) {
                                q.setString(1, id);
                                q.setString(2, owner.toString());
                                try (var r = q.executeQuery()) {
                                    paid = r.next();
                                }
                            }
                            if (!paid) {
                                if (entryFee > 0)
                                    try (var debit =
                                            c.prepareStatement(
                                                    "UPDATE player_balances SET ecus=ecus-? WHERE"
                                                        + " uuid=? AND ecus>=?")) {
                                        debit.setLong(1, entryFee);
                                        debit.setString(2, owner.toString());
                                        debit.setLong(3, entryFee);
                                        if (debit.executeUpdate() != 1) {
                                            c.rollback();
                                            return false;
                                        }
                                    }
                                try (var receipt =
                                        c.prepareStatement(
                                                "INSERT INTO live_auction_entries(event,owner,fee)"
                                                    + " VALUES(?,?,?)")) {
                                    receipt.setString(1, id);
                                    receipt.setString(2, owner.toString());
                                    receipt.setLong(3, entryFee);
                                    receipt.executeUpdate();
                                }
                            }

                            try (var s =
                                    c.prepareStatement(
                                            "INSERT OR IGNORE INTO"
                                                    + " live_auction_members(event,owner)"
                                                    + " VALUES(?,?)")) {
                                s.setString(1, id);
                                s.setString(2, owner.toString());
                                s.executeUpdate();
                            }
                            c.commit();
                            return true;
                        }
                        try (var s =
                                c.prepareStatement(
                                        "SELECT 1 FROM live_auction_members WHERE event=? AND"
                                                + " owner=?")) {
                            s.setString(1, id);
                            s.setString(2, owner.toString());
                            try (var r = s.executeQuery()) {
                                boolean registered = r.next();
                                c.commit();
                                return registered;
                            }
                        }
                    } catch (SQLException e) {
                        rollback(c, e);
                        throw failure(e);
                    }
                });
    }

    public CompletableFuture<Void> leaveRegistration(String id, UUID owner) {
        return db.submit(
                c -> {
                    try (var s =
                            c.prepareStatement(
                                    "DELETE FROM live_auction_members WHERE event=? AND owner=?")) {
                        s.setString(1, id);
                        s.setString(2, owner.toString());
                        s.executeUpdate();
                        return null;
                    } catch (SQLException e) {
                        throw failure(e);
                    }
                });
    }

    public CompletableFuture<Offer> bid(
            String id, int lot, UUID owner, String name, long price, long bidMs) {
        if (price <= 0 || price > 1_000_000_000_000_000L || bidMs < 1)
            throw new IllegalArgumentException();
        return db.submit(
                c -> {
                    try {
                        c.setAutoCommit(false);
                        State state = read(c, id);
                        Bid result;
                        if (state == null
                                || !state.phase.equals("ACTIVE")
                                || state.lot != lot
                                || state.deadline <= System.currentTimeMillis())
                            result = Bid.CLOSED;
                        else if (price <= state.price) result = Bid.TOO_LOW;
                        else {
                            boolean member;
                            try (var s =
                                    c.prepareStatement(
                                            "SELECT 1 FROM live_auction_members WHERE event=? AND"
                                                    + " owner=?")) {
                                s.setString(1, id);
                                s.setString(2, owner.toString());
                                try (var r = s.executeQuery()) {
                                    member = r.next();
                                }
                            }
                            long balance = 0;
                            try (var s =
                                    c.prepareStatement(
                                            "SELECT ecus FROM player_balances WHERE uuid=?")) {
                                s.setString(1, owner.toString());
                                try (var r = s.executeQuery()) {
                                    if (r.next()) balance = r.getLong(1);
                                }
                            }
                            if (!member) result = Bid.NOT_REGISTERED;
                            else if (balance < price) result = Bid.FUNDS;
                            else {
                                try (var s =
                                        c.prepareStatement(
                                                "INSERT INTO"
                                                    + " live_auction_bids(event,lot,owner,name,price)"
                                                    + " VALUES(?,?,?,?,?)")) {
                                    s.setString(1, id);
                                    s.setInt(2, lot);
                                    s.setString(3, owner.toString());
                                    s.setString(4, name);
                                    s.setLong(5, price);
                                    s.executeUpdate();
                                }
                                try (var s =
                                        c.prepareStatement(
                                                "UPDATE live_auctions SET"
                                                    + " price=?,bidder=?,name=?,deadline=?,revision=revision+1"
                                                    + " WHERE id=?")) {
                                    s.setLong(1, price);
                                    s.setString(2, owner.toString());
                                    s.setString(3, name);
                                    s.setLong(4, System.currentTimeMillis() + bidMs);
                                    s.setString(5, id);
                                    s.executeUpdate();
                                }
                                result = Bid.ACCEPTED;
                            }
                        }
                        State after = read(c, id);
                        c.commit();
                        return new Offer(result, after);
                    } catch (SQLException e) {
                        rollback(c, e);
                        throw failure(e);
                    }
                });
    }

    public CompletableFuture<Advance> advance(String id, long bidMs, boolean forceStart) {
        return db.submit(
                c -> {
                    try {
                        c.setAutoCommit(false);
                        State state = read(c, id);
                        if (state == null
                                || state.phase.equals("DONE")
                                || (!forceStart && state.deadline > System.currentTimeMillis())) {
                            c.rollback();
                            return new Advance(state, null);
                        }
                        Sale sale = null;
                        if (state.phase.equals("REGISTERING")) {
                            try (var s =
                                    c.prepareStatement(
                                            "UPDATE live_auctions SET"
                                                + " phase=?,deadline=?,revision=revision+1 WHERE"
                                                + " id=?")) {
                                s.setString(1, state.members == 0 ? "DONE" : "ACTIVE");
                                s.setLong(2, System.currentTimeMillis() + bidMs);
                                s.setString(3, id);
                                s.executeUpdate();
                            }
                            if (state.members == 0)
                                try (var s =
                                        c.prepareStatement(
                                                "UPDATE live_auction_lots SET state='UNSOLD' WHERE"
                                                        + " event=?")) {
                                    s.setString(1, id);
                                    s.executeUpdate();
                                }
                        } else if (!forceStart) {
                            UUID winner = null;
                            String name = null;
                            long price = 0;
                            try (var s =
                                    c.prepareStatement(
                                            "SELECT owner,name,MAX(price) AS price FROM"
                                                + " live_auction_bids WHERE event=? AND lot=? GROUP"
                                                + " BY owner ORDER BY price DESC")) {
                                s.setString(1, id);
                                s.setInt(2, state.lot);
                                try (var r = s.executeQuery()) {
                                    while (r.next()) {
                                        try (var debit =
                                                c.prepareStatement(
                                                        "UPDATE player_balances SET ecus=ecus-?"
                                                                + " WHERE uuid=? AND ecus>=?")) {
                                            long offered = r.getLong(3);
                                            debit.setLong(1, offered);
                                            debit.setString(2, r.getString(1));
                                            debit.setLong(3, offered);
                                            if (debit.executeUpdate() == 1) {
                                                winner = UUID.fromString(r.getString(1));
                                                name = r.getString(2);
                                                price = offered;
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                            String payload = state.items.get(state.lot);
                            if (winner != null)
                                MailStore.insert(
                                        c, winner, "live-auction:" + id + ":" + state.lot, payload);
                            try (var s =
                                    c.prepareStatement(
                                            "UPDATE live_auction_lots SET state=?,winner=?,price=?"
                                                    + " WHERE event=? AND slot=? AND"
                                                    + " state='PENDING'")) {
                                s.setString(1, winner == null ? "UNSOLD" : "SOLD");
                                s.setString(2, winner == null ? null : winner.toString());
                                s.setLong(3, price);
                                s.setString(4, id);
                                s.setInt(5, state.lot);
                                if (s.executeUpdate() != 1)
                                    throw new SQLException("Lot déjà attribué");
                            }
                            sale = new Sale(state.lot, winner, name, price, payload);
                            try (var s =
                                    c.prepareStatement(
                                            "UPDATE live_auctions SET"
                                                + " phase=?,lot=?,deadline=?,price=0,bidder=NULL,name=NULL,revision=revision+1"
                                                + " WHERE id=?")) {
                                s.setString(1, state.lot == 2 ? "DONE" : "ACTIVE");
                                s.setInt(2, state.lot == 2 ? 2 : state.lot + 1);
                                s.setLong(3, System.currentTimeMillis() + bidMs);
                                s.setString(4, id);
                                s.executeUpdate();
                            }
                        }
                        State after = read(c, id);
                        c.commit();
                        return new Advance(after, sale);
                    } catch (SQLException e) {
                        rollback(c, e);
                        throw failure(e);
                    }
                });
    }

    public CompletableFuture<State> state(String id) {
        return db.submit(
                c -> {
                    try {
                        return read(c, id);
                    } catch (SQLException e) {
                        throw failure(e);
                    }
                });
    }

    private static void rollback(Connection c, SQLException error) {
        try {
            c.rollback();
        } catch (SQLException e) {
            error.addSuppressed(e);
        }
    }
}
