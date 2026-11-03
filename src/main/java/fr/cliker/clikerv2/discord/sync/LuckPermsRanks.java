package fr.cliker.clikerv2.discord.sync;

import net.luckperms.api.*;
import net.luckperms.api.event.node.NodeMutateEvent;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Parents directs globaux uniquement : ni OP, ni permissions wildcard, ni groupes hérités. */
public final class LuckPermsRanks implements RankPermissions {
    private final LuckPerms api;
    private final Object owner;
    private net.luckperms.api.event.EventSubscription<NodeMutateEvent> subscription;

    public LuckPermsRanks(Object owner) {
        this.owner = owner;
        this.api = LuckPermsProvider.get();
    }

    private static boolean parent(Node node, String group) {
        return node instanceof InheritanceNode inherited
                && inherited.getGroupName().equals(group)
                && node.getContexts().isEmpty();
    }

    static Membership membership(User user, String group) {
        var nodes = user.data().toCollection().stream().filter(n -> parent(n, group)).toList();
        boolean present = nodes.stream().anyMatch(n -> n.getValue() && !n.hasExpired());
        String stamp =
                nodes.stream()
                        .map(
                                n ->
                                        n.getKey()
                                                + ":"
                                                + n.getValue()
                                                + ":"
                                                + (n.hasExpiry()
                                                        ? n.getExpiry().toEpochMilli()
                                                        : "permanent"))
                        .sorted()
                        .reduce("", (a, b) -> a + "|" + b);
        return new Membership(present, stamp);
    }

    public Map<String, Membership> read(UUID owner, Set<String> groups) throws Exception {
        boolean loaded = api.getUserManager().isLoaded(owner);
        User user = api.getUserManager().loadUser(owner).get(10, TimeUnit.SECONDS);
        try {
            Map<String, Membership> result = new HashMap<>();
            for (String group : groups) result.put(group, membership(user, group));
            return Map.copyOf(result);
        } finally {
            if (!loaded) api.getUserManager().cleanupUser(user);
        }
    }

    public boolean apply(UUID id, String group, Membership expected, boolean present)
            throws Exception {
        boolean loaded = api.getUserManager().isLoaded(id);
        User user = api.getUserManager().loadUser(id).get(10, TimeUnit.SECONDS);
        try {
            if (!membership(user, group).equals(expected)) return false;
            if (expected.present() != present) {
                user.data().clear(n -> parent(n, group));
                if (present) user.data().add(InheritanceNode.builder(group).build());
            }
            // Même sans nouveau node : réessaie une sauvegarde qui aurait échoué auparavant.
            api.getUserManager().saveUser(user).get(10, TimeUnit.SECONDS);
            return membership(user, group).present() == present;
        } finally {
            if (!loaded) api.getUserManager().cleanupUser(user);
        }
    }

    public void validate(Set<String> groups) throws Exception {
        for (String group : groups)
            if (api.getGroupManager().loadGroup(group).get(10, TimeUnit.SECONDS).isEmpty())
                throw new IllegalArgumentException(
                        "Un groupe LuckPerms configuré n'existe pas : " + group);
    }

    public void subscribe(Consumer<UUID> changed) {
        subscription =
                api.getEventBus()
                        .subscribe(
                                owner,
                                NodeMutateEvent.class,
                                event -> {
                                    if (event.isUser()
                                            && event.getDataType()
                                                    == net.luckperms.api.model.data.DataType.NORMAL)
                                        changed.accept(((User) event.getTarget()).getUniqueId());
                                });
    }

    public void close() {
        if (subscription != null) subscription.close();
    }
}
