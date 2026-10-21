package fr.cliker.clikerv2.lootbox;

import fr.cliker.clikerv2.EconomyService;
import fr.cliker.clikerv2.Numbers;
import fr.cliker.clikerv2.economy.CurrencyType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class LootboxManager implements Listener {
    private final EconomyService economy;

    public LootboxManager(EconomyService economy) {
        this.economy = economy;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        LootboxTier tier = LootboxItem.tierOf(item);
        if (tier == null) return;
        event.setCancelled(true);

        int amount = player.isSneaking() ? item.getAmount() : 1;
        for (int i = 0; i < amount; i++) open(player, tier);

        int remaining = item.getAmount() - amount;
        if (remaining <= 0) player.getInventory().setItemInMainHand(null);
        else item.setAmount(remaining);
    }

    private void open(Player player, LootboxTier tier) {
        economy.change(player.getUniqueId(), player.getName(), CurrencyType.ECUS, tier.ecus());
        economy.change(player.getUniqueId(), player.getName(), CurrencyType.GEMMES_SOMBRES, tier.gemmesSombresTenths());
        economy.change(player.getUniqueId(), player.getName(), CurrencyType.CENDRES, tier.cendres());
        player.giveExp((int) Math.min(Integer.MAX_VALUE, tier.xp()));

        player.sendMessage(Component.text(tier.boxName() + " ouvert ! ", tier.color())
                .append(Component.text("+" + Numbers.compact(tier.ecus()) + " Écus, +" + CurrencyType.GEMMES_SOMBRES.format(tier.gemmesSombresTenths()) + " Gemmes, +" + Numbers.compact(tier.xp()) + " XP, +" + Numbers.compact(tier.cendres()) + " Cendres", NamedTextColor.GRAY)));
    }
}
