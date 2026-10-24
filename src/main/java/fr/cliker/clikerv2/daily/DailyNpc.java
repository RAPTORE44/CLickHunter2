package fr.cliker.clikerv2.daily;

import fr.cliker.clikerv2.npc.CitizensNpc;

/** PNJ du calendrier : conserve la balise historique et les données déjà enregistrées. */
public final class DailyNpc extends CitizensNpc {
    public DailyNpc(DailyModule module) {
        super(module.plugin(), module.settings().config(), module.settings()::save,
                module::open, "clikerv2-daily-npc", "NorthBlueDaily");
    }
}
