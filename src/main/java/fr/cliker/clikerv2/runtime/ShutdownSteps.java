package fr.cliker.clikerv2.runtime;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Une erreur d'un module ne doit pas empêcher les sauvegardes et nettoyages suivants. */
public final class ShutdownSteps {
    private ShutdownSteps() {}

    public static boolean run(Logger logger, String name, Runnable action) {
        try {
            action.run();
            return true;
        } catch (RuntimeException | LinkageError error) {
            logger.log(
                    Level.SEVERE,
                    "Arrêt incomplet de « "
                            + name
                            + " » ; poursuite des autres sauvegardes et nettoyages.",
                    error);
            return false;
        }
    }
}
