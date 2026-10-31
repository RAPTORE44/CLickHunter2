package fr.cliker.clikerv2.npc.fixture;

public final class HiddenApiFactory {
    private HiddenApiFactory() {}

    public static java.util.function.Supplier<String> create() {
        return new java.util.function.Supplier<>() {
            public String get() {
                return "manager";
            }
        };
    }
}
