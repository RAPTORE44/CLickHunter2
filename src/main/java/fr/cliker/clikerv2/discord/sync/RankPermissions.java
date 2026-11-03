package fr.cliker.clikerv2.discord.sync;

import java.util.*;
import java.util.function.Consumer;

public interface RankPermissions extends AutoCloseable {
    record Membership(boolean present, String stamp) {}

    Map<String, Membership> read(UUID owner, Set<String> groups) throws Exception;

    /** Compare de nouveau les parents avant toute modification ; false => réessayer plus tard. */
    boolean apply(UUID owner, String group, Membership expected, boolean present) throws Exception;

    void validate(Set<String> groups) throws Exception;

    default void subscribe(Consumer<UUID> changed) {}

    default void close() {}
}
