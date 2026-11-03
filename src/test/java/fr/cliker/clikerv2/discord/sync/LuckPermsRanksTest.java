package fr.cliker.clikerv2.discord.sync;

import static org.junit.jupiter.api.Assertions.*;

import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.model.data.NodeMap;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.*;
import net.luckperms.api.node.types.InheritanceNode;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;

class LuckPermsRanksTest {
    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, java.util.function.Function<String, Object> values) {
        return (T)
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class[] {type},
                        (p, m, a) -> {
                            if (m.getName().equals("toString")) return type.getSimpleName();
                            if (m.getName().equals("equals")) return p == a[0];
                            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
                            return values.apply(m.getName());
                        });
    }

    static Node node(String group, boolean global, boolean value, boolean expired) {
        var contexts = proxy(ImmutableContextSet.class, m -> m.equals("isEmpty") ? global : null);
        return proxy(
                InheritanceNode.class,
                m ->
                        switch (m) {
                            case "getGroupName" -> group;
                            case "getKey" -> "group." + group;
                            case "getContexts" -> contexts;
                            case "getValue" -> value;
                            case "hasExpired", "hasExpiry" -> expired;
                            case "getExpiry" -> Instant.EPOCH;
                            default -> null;
                        });
    }

    static User user(List<Node> normal, List<Node> temporaryMemoryOnly) {
        var map = proxy(NodeMap.class, m -> m.equals("toCollection") ? normal : null);
        return proxy(
                User.class,
                m ->
                        switch (m) {
                            case "data" -> map;
                            case "getNodes" -> {
                                var all = new ArrayList<>(normal);
                                all.addAll(temporaryMemoryOnly);
                                yield all;
                            }
                            default -> null;
                        });
    }

    @Test
    void onlyActiveDirectGlobalPersistentParentCountsNotContextualTransientOrExpiredNodes() {
        var user =
                user(
                        List.of(
                                node("vip", true, true, false),
                                node("mvp", false, true, false),
                                node("legend", true, true, true),
                                node("vipplus", true, false, false)),
                        List.of(node("minivip", true, true, false)));
        assertTrue(LuckPermsRanks.membership(user, "vip").present());
        assertFalse(LuckPermsRanks.membership(user, "mvp").present());
        assertFalse(LuckPermsRanks.membership(user, "legend").present());
        assertFalse(LuckPermsRanks.membership(user, "vipplus").present());
        assertFalse(LuckPermsRanks.membership(user, "minivip").present());
        assertFalse(LuckPermsRanks.membership(user, "staff").present());
    }
}
