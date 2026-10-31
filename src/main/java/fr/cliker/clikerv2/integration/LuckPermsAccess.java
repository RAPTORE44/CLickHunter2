package fr.cliker.clikerv2.integration;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Ne charger cet adaptateur qu'après détection de LuckPerms ; aucun listener ne porte ses types.
 */
public final class LuckPermsAccess {
    private LuckPermsAccess() {}

    public static CompletableFuture<Map<String, Boolean>> offline(
            UUID owner, Map<String, String> nodes) {
        var manager = net.luckperms.api.LuckPermsProvider.get().getUserManager();
        return manager.loadUser(owner)
                .thenApply(
                        user -> {
                            try {
                                var permissions = new HashMap<String, Boolean>();
                                var data = user.getCachedData().getPermissionData();
                                nodes.forEach(
                                        (id, node) ->
                                                permissions.put(
                                                        id,
                                                        data.checkPermission(node)
                                                                == net.luckperms.api.util.Tristate
                                                                        .TRUE));
                                return Map.copyOf(permissions);
                            } finally {
                                manager.cleanupUser(user);
                            }
                        });
    }
}
