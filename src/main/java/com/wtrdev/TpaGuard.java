package com.wtrdev;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.lang.StringBuffer;

import java.util.*;

public class TpaGuard extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private final Set<UUID> tpabekleyenoyuncu = new HashSet<>();
    private final Set<UUID> korumadakioyuncu = new HashSet<>();
    private final Map<UUID, Location> oncekikonum = new HashMap<>();
    private final Map<UUID, Long> korumasayacbaslangic = new HashMap<>();
    private final Map<UUID, Long> tekrarkullanimsuresi = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        Bukkit.getPluginManager().registerEvents(this, this);

        if (getCommand("tpaguard") != null) {
            getCommand("tpaguard").setExecutor(this::tpkorumakomutu);
        }
    }

    private boolean tpkorumakomutu(CommandSender gonderen, Command komut, String label, String[] args) {
        if (!(gonderen instanceof Player)) return true;

        Player oyuncu = (Player) gonderen;
        UUID uuid = oyuncu.getUniqueId();

        long beklemesuresi = config.getLong("settings.cooldown", 30000);
        long simdi = System.currentTimeMillis();

        if (tekrarkullanimsuresi.containsKey(uuid) && simdi - tekrarkullanimsuresi.get(uuid) < beklemesuresi) {
            oyuncu.sendMessage(renkli(config.getString("messages.cooldown")));
            return true;
        }

        tpabekleyenoyuncu.add(uuid);
        tekrarkullanimsuresi.put(uuid, simdi);
        oyuncu.sendMessage(renkli(config.getString("messages.waiting")));

        new BukkitRunnable() {
            @Override
            public void run() {
                if (tpabekleyenoyuncu.contains(uuid)) {
                    tpabekleyenoyuncu.remove(uuid);
                    oyuncu.sendMessage(renkli(config.getString("messages.timeout")));
                }
            }
        }.runTaskLater(this, 20 * 30);

        return true;
    }

    @EventHandler
    public void teleport(PlayerTeleportEvent e) {
        Player oyuncu = e.getPlayer();
        UUID uuid = oyuncu.getUniqueId();

        if (tpabekleyenoyuncu.remove(uuid)) {
            oncekikonum.put(uuid, e.getFrom());
            korumasayacbaslangic.put(uuid, System.currentTimeMillis());
            korumadakioyuncu.add(uuid);
            oyuncu.sendMessage(renkli(config.getString("messages.teleported")));

            new BukkitRunnable() {
                @Override
                public void run() {
                    korumasayacbaslangic.remove(uuid);
                    korumadakioyuncu.remove(uuid);
                }
            }.runTaskLater(this, 20 * 30);
        }
    }

    @EventHandler
    public void hasargorme(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;

        Player hedef = (Player) e.getEntity();
        UUID hedefID = hedef.getUniqueId();

        if (korumadakioyuncu.contains(hedefID) && System.currentTimeMillis() - korumasayacbaslangic.getOrDefault(hedefID, 0L) <= 15000) {
            gerigonder(hedefID, hedef);
            e.setCancelled(true);
            return;
        }

        if (e.getDamager() instanceof Player) {
            Player saldirgan = (Player) e.getDamager();
            UUID saldirganID = saldirgan.getUniqueId();

            if (korumadakioyuncu.contains(saldirganID) && System.currentTimeMillis() - korumasayacbaslangic.getOrDefault(saldirganID, 0L) <= 15000) {
                gerigonder(saldirganID, saldirgan);
                e.setCancelled(true);
            }
        }
    }

    private void gerigonder(UUID uuid, Player oyuncu) {
        Location oncekiKonum = oncekikonum.remove(uuid);
        if (oncekiKonum != null) {
            oyuncu.teleport(oncekiKonum);
            oyuncu.sendMessage(renkli(config.getString("messages.returned")));
        }
        korumasayacbaslangic.remove(uuid);
        korumadakioyuncu.remove(uuid);
    }

    private String renkli(String yazi) {
        if (yazi == null) return "";

        Pattern hexPattern = Pattern.compile("#([A-Fa-f0-9]{6})");
        Matcher matcher = hexPattern.matcher(yazi);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replace = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replace.append('§').append(c);
            }
            matcher.appendReplacement(buffer, replace.toString());
        }

        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}
