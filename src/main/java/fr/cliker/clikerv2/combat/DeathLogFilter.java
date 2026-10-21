package fr.cliker.clikerv2.combat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

import java.util.regex.Pattern;

/**
 * Supprime le log console vanilla "Named entity X died: ..." pour nos mobs custom (toujours nommés à cause de la
 * plaque HP), qui spamme la console à chaque kill. Filtre global au logger racine, installé une seule fois par JVM
 * (un reload à chaud rappelle install() sans effet grâce au flag, pas besoin de retrait à onDisable).
 */
public final class DeathLogFilter extends AbstractFilter {
    private static final Pattern NAMEPLATE_DEATH = Pattern.compile(".*\\[[^\\[\\]]*/[^\\[\\]]*].*died:.*");
    private static boolean installed = false;

    public static void install() {
        if (installed) return;
        ((Logger) LogManager.getRootLogger()).addFilter(new DeathLogFilter());
        installed = true;
    }

    @Override
    public Result filter(LogEvent event) {
        return test(event.getMessage());
    }

    private Result test(Message message) {
        if (message == null) return Result.NEUTRAL;
        String formatted = message.getFormattedMessage();
        if (formatted != null && NAMEPLATE_DEATH.matcher(formatted).matches()) return Result.DENY;
        return Result.NEUTRAL;
    }
}
