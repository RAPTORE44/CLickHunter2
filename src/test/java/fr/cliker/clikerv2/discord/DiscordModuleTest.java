package fr.cliker.clikerv2.discord;

import fr.cliker.clikerv2.npc.CitizensNpc;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DiscordModuleTest {
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private org.mockbukkit.mockbukkit.entity.PlayerMock player;
    private DiscordModule module;
    private static final String LINK = "https://discord.gg/WSwBwpnQTa";
    private final Command command = new Command("discord") { @Override public boolean execute(CommandSender sender, String label, String[] args) { return false; } };
    @BeforeEach void setup() throws Exception {
        var server = MockBukkit.mock(); plugin = MockBukkit.createMockPlugin(); player = server.addPlayer();
        player.addAttachment(plugin, DiscordModule.USE, true);
        var path = plugin.getDataFolder().toPath().resolve("discord.yml"); Files.createDirectories(path.getParent());
        try (var input = getClass().getResourceAsStream("/discord.yml")) { Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING); }
        module = new DiscordModule(plugin);
    }
    @AfterEach void cleanup() { try { module.shutdown(); } finally { MockBukkit.unmock(); } }
    @Test void exactInviteIsClickableAndCommandWorksWithoutCitizens() {
        assertEquals(ClickEvent.Action.OPEN_URL, module.clickableLink().clickEvent().action());
        assertEquals(LINK, module.clickableLink().clickEvent().value());
        module.onCommand(player, command, "discord", new String[0]);
        assertTrue(PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage()).contains("ClickHunter"));
        assertEquals(LINK, PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage()));
    }
    @Test void managementIsAdminOnlyAndMissingCitizensIsReported() {
        player.addAttachment(plugin, DiscordModule.ADMIN, false);
        module.onCommand(player, command, "discord", new String[]{"pnj", "spawn"});
        assertTrue(player.nextMessage().contains("permission"));
        player.addAttachment(plugin, DiscordModule.ADMIN, true);
        module.onCommand(player, command, "discord", new String[]{"pnj", "spawn"});
        assertTrue(player.nextMessage().contains("Citizens"));
        assertFalse(YamlConfiguration.loadConfiguration(new java.io.File(plugin.getDataFolder(), "discord.yml")).getBoolean("pnj.actif"));
    }
    @Test void linkPermissionIsEnforced() {
        player.addAttachment(plugin, DiscordModule.USE, false);
        module.sendLink(player);
        assertTrue(player.nextMessage().contains("permission"));
        assertNull(player.nextMessage());
    }
    @Test void discordNpcRemovalNeverRemovesDailyNpcAndPersistsDeletion() throws Exception {
        var config = new YamlConfiguration(); config.set("pnj.actif", true); config.set("pnj.id", 12);
        var file = new java.io.File(plugin.getDataFolder(), "test-npc.yml");
        FakeNpc discord = new FakeNpc("clikerv2-discord-npc"), daily = new FakeNpc("clikerv2-daily-npc");
        FakeRegistry registry = new FakeRegistry(List.of(discord, daily));
        var npc = new CitizensNpc(plugin, config, () -> { try { config.save(file); } catch (Exception e) { throw new RuntimeException(e); } }, p -> {}, "clikerv2-discord-npc", "ClickHunterDiscord");
        var field = CitizensNpc.class.getDeclaredField("registry"); field.setAccessible(true); field.set(npc, registry);
        assertTrue(npc.remove()); assertTrue(discord.destroyed); assertFalse(daily.destroyed);
        assertFalse(YamlConfiguration.loadConfiguration(file).getBoolean("pnj.actif"));
        assertFalse(YamlConfiguration.loadConfiguration(file).contains("pnj.id"));
        assertEquals(1, registry.saves);
    }
    @Test void savedNpcPreventsDuplicateCreation() throws Exception {
        var config = new YamlConfiguration(); config.set("pnj.actif", true);
        var npc = new CitizensNpc(plugin, config, () -> {}, p -> {}, "clikerv2-discord-npc", "ClickHunterDiscord");
        assertFalse(npc.spawn(player.getLocation()));
    }
    // Doubles limités à la frontière d'API Citizens ; pas un test d'apparition réelle en jeu.
    public static class FakeNpc {
        private final Metadata data;
        boolean destroyed;
        FakeNpc(String owner) { data = new Metadata(owner); }
        public Metadata data() { return data; }
        public void destroy() { destroyed = true; }
    }
    public static class Metadata {
        private final String owner;
        Metadata(String owner) { this.owner = owner; }
        public Object get(String key, Object fallback) { return owner.equals(key) ? true : fallback; }
    }
    public static class FakeRegistry implements Iterable<FakeNpc> {
        private final List<FakeNpc> npcs;
        int saves;
        FakeRegistry(List<FakeNpc> npcs) { this.npcs = npcs; }
        @Override public Iterator<FakeNpc> iterator() { return npcs.iterator(); }
        public void saveToStore() { saves++; }
    }
}
