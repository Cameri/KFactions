/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.kmcguire.KFactions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.util.LongHash;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

class DataDumper implements Runnable {
    P           p;
    DataDumper(P p) {
        this.p = p;
    }

    @Override
    public void run() {
        synchronized(p) {
            try {
                SLAPI.save(p.factions, "plugin.data.factions");
                p.smsg("saved data to disk");
            } catch (Exception e) {
                p.smsg("error when trying to save data to disk");
            }
            /// SCHEDULE OURSELVES TO RUN AGAIN ONCE WE ARE DONE
            p.getServer().getScheduler().scheduleAsyncDelayedTask(p, this, 20 * 60 * 10);
        }
    }
}

public class P extends JavaPlugin implements IFactionsProtection {
    public Map<String, Faction>        factions;
    private boolean                    saveToDisk;
    public static final File           fdata;
    
    static final int    NOPVP =        0x01;
    static final int    NOBOOM =       0x02;
    static final int    NODECAY =      0x04;
    
    public HashMap<Long, Integer>      emcMap;
    
    // configuration
    public double       landPowerCostPerHour;
    
    public Location    gspawn = null;
    
    public static P     __ehook;
    
    static {
        fdata = new File("kfactions.data.yml");
    }
    
    public P() {        
        __ehook = this;
    }
    
    public void LoadHumanReadableData() throws InvalidConfigurationException {
        YamlConfiguration                   cfg;
        ConfigurationSection                cfg_root;
        ConfigurationSection                cfg_chunks;
        ConfigurationSection                cfg_friends;
        ConfigurationSection                cfg_invites;
        ConfigurationSection                cfg_players;
        ConfigurationSection                cfg_walocs;
        ConfigurationSection                cfg_zapin;
        ConfigurationSection                cfg_zapout;
        Map<String, Object>                 m;
        Faction                             f;
        FactionChunk                        fc;
        
        cfg = new YamlConfiguration();
        
        try {
            cfg.load(fdata);
        } catch (FileNotFoundException ex) {
            return;
        } catch (IOException ex) {
            return;
        }
        
        m = cfg.getValues(false);
        
        for (Entry<String, Object> e : m.entrySet()) {
            getLogger().info(String.format("%s:%s", e.getKey(), e.getValue().getClass().getName()));
            cfg_root = (ConfigurationSection)e.getValue();
            
            f = new Faction();
            
            // access all of the list/array/map type stuff
            cfg_chunks = cfg_root.getConfigurationSection("chunks");
            for (String key : cfg_chunks.getKeys(false)) {
                ConfigurationSection        ccs;
                ConfigurationSection        _ccs;
                fc = new FactionChunk();
                
                fc.x = Integer.parseInt(key.substring(1, key.indexOf('_')));
                fc.z = Integer.parseInt(key.substring(key.indexOf('_') + 1));
                
                fc.builders = null;
                fc.users = null;
                fc.faction = f;
                
                ccs = cfg_chunks.getConfigurationSection(key);
                fc.mru = ccs.getInt("mru");
                fc.mrb = ccs.getInt("mrb");
                
                _ccs = ccs.getConfigurationSection("tid");
                fc.tid = new HashMap<TypeDataID, Integer>();
                
                for (Entry<String, Object> en : _ccs.getValues(false).entrySet()) {
                    TypeDataID          tid;
                    
                    tid = new TypeDataID();
                    tid.typeId = Integer.parseInt(en.getKey());
                    tid.dataId = 0;
                    fc.tid.put(tid, (Integer)en.getValue());
                }
                
                _ccs = ccs.getConfigurationSection("tidu");
                fc.tidu = new HashMap<TypeDataID, Integer>();
                
                for (Entry<String, Object> en : _ccs.getValues(false).entrySet()) {
                    TypeDataID          tid;
                    
                    tid = new TypeDataID();
                    tid.typeId = Integer.parseInt(en.getKey());
                    tid.dataId = 0;
                    fc.tid.put(tid, (Integer)en.getValue());
                }
            }
            cfg_friends = cfg_root.getConfigurationSection("friends");
            
            f.friends = new HashMap<String, Integer>();
            for (Entry<String, Object> en : cfg_friends.getValues(false).entrySet()) {
                f.friends.put(en.getKey(), (Integer)en.getValue());
            }
            
            cfg_invites = cfg_root.getConfigurationSection("invites");
            f.invites = new HashSet<String>();
            
            cfg_players = cfg_root.getConfigurationSection("players");
            f.players = new HashMap<String, FactionPlayer>();
            
            for (Entry<String, Object> en : cfg_players.getValues(false).entrySet()) {
                FactionPlayer               fp;
                
                fp = new FactionPlayer();
                fp.faction = f;
                fp.name = en.getKey();
                fp.rank = (Integer)en.getValue();
                f.players.put(en.getKey(), fp);
            }
            
            cfg_walocs = cfg_root.getConfigurationSection("walocs");
            f.walocs = new HashSet<WorldAnchorLocation>();
            
            for (String key : cfg_walocs.getKeys(false)) {
                ConfigurationSection        _cs;
                WorldAnchorLocation         waloc;
                
                _cs = cfg_walocs.getConfigurationSection(key);
                waloc = new WorldAnchorLocation();
                waloc.x = _cs.getInt("x");
                waloc.y = _cs.getInt("y");
                waloc.z = _cs.getInt("z");
                waloc.byWho = _cs.getString("byWho");
                waloc.timePlaced = _cs.getLong("timePlaced");
                f.walocs.add(waloc);
            }
            
            
            cfg_zapin = cfg_root.getConfigurationSection("zappersIncoming");
            for (String key : cfg_zapin.getKeys(false)) {
                ConfigurationSection        _cs;
                ZapEntry                    ze;
                
                _cs = cfg_walocs.getConfigurationSection(key);
                ze = new ZapEntry();
            }
            
            cfg_zapout = cfg_root.getConfigurationSection("zappersOutgoing");
            
            // access all the primitive value fields
            f.desc = cfg_root.getString("desc");
            f.flags = cfg_root.getInt("flags");
            f.hw = cfg_root.getString("hw");
            f.hx = cfg_root.getDouble("hx");
            f.hy = cfg_root.getDouble("hy");
            f.hz = cfg_root.getDouble("hz");
            f.lpud = cfg_root.getLong("lpud");
            f.mrc = cfg_root.getInt("mrc");
            f.mri = cfg_root.getInt("mri");
            f.mrsh = cfg_root.getInt("mrsh");
            f.mrtp = cfg_root.getInt("mrtp");
            f.mrz = cfg_root.getInt("mrz");
            f.name = e.getKey();
            f.peaceful = false;
            f.power = cfg_root.getDouble("power");
            f.worthEMC = cfg_root.getLong("worthEMC");
        }
    }
    
    public String sanitizeString(String in) {
        char[]                          cb;
        String                          ac;
        int                             y;
        int                             z;
        
        ac = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890_";
        z = 0;
        
        cb = new char[in.length()];
        
        for (int x = 0; x < in.length(); ++x) {
            for (y = 0; y < ac.length(); ++y) {
                if (in.charAt(x) == ac.charAt(y)) {
                    break;
                }
            }
            if (y < ac.length()) {
                cb[z++] = in.charAt(x);
            }
        }
        
        return new String(cb, 0, z);
    }
    
    public void DumpHumanReadableData() throws FileNotFoundException, IOException {
        // .factions
        //      .FireClan
        //          .land
        //              3:4
        //              2:1
        //              5:6
        //            .
        RandomAccessFile                raf;
        Faction                         f;
        String                          fname;
        int                             j;
        
        raf = new RandomAccessFile(fdata, "rw");
            
        for (Entry<String, Faction> ef : factions.entrySet()) {
            // TestFaction:
            f = ef.getValue();
            fname = sanitizeString(f.name);
            if (fname.length() == 0) {
                continue;
            }
            raf.writeBytes(String.format("%s:\n", fname));
            // members/players
            raf.writeBytes(" players:\n");
            for (Entry<String, FactionPlayer> p : f.players.entrySet()) {
                raf.writeBytes(String.format("  %s: %d\n", p.getKey(), p.getValue().rank));                
            }
            raf.writeBytes(" friends:\n");
            if (f.friends != null) {
                for (Entry<String, Integer> fr : f.friends.entrySet()) {
                    raf.writeBytes(String.format("  %s: %d\n", fr.getKey(), fr.getValue()));
                }
            }
            raf.writeBytes(" chunks:\n");
            for (Entry<Long, FactionChunk> fc : f.chunks.entrySet()) {
                FactionChunk        chk;
                
                chk = fc.getValue();
                
                // mru (done)
                // mrb (done)
                // tid (loop)
                // tidu (loop)
                raf.writeBytes(String.format("  c%d_%d:\n", chk.x, chk.z));
                raf.writeBytes(String.format("   mru: %d\n", chk.mru));
                raf.writeBytes(String.format("   mrb: %d\n", chk.mrb));
                raf.writeBytes("   tid:\n");
                if (chk.tid != null) {
                    for (Entry<TypeDataID, Integer> e : chk.tid.entrySet()) {
                        raf.writeBytes(String.format("    %d: %d\n", e.getKey().typeId, e.getValue()));
                    }
                }
                raf.writeBytes("   tidu:\n");
                if (chk.tidu != null) {
                    for (Entry<TypeDataID, Integer> e : chk.tidu.entrySet()) {
                        raf.writeBytes(String.format("    %d: %d\n", e.getKey().typeId, e.getValue()));
                    }
                }
            }
            // desc
            raf.writeBytes(String.format(" desc: %s\n", f.desc));
            // flags
            raf.writeBytes(String.format(" flags: %d\n", f.flags));
            // hw, hx, hy, hz
            raf.writeBytes(String.format(" hw: %s\n", f.hw));
            raf.writeBytes(String.format(" hx: %f\n", f.hx));
            raf.writeBytes(String.format(" hy: %f\n", f.hy));
            raf.writeBytes(String.format(" hz: %f\n", f.hz));
            // invitations
            raf.writeBytes(String.format(" invites:\n"));
            if (f.invites != null) {
                for (String inv : f.invites) {
                    inv = sanitizeString(inv);
                    if (inv.length() == 0) {
                        continue;
                    }
                    raf.writeBytes(String.format("  - %s\n", inv));
                }
            }
            // lpud
            raf.writeBytes(String.format(" lpud: %d\n", f.lpud));
            // mrc
            raf.writeBytes(String.format(" mrc: %d\n", f.mrc));
            // mri
            raf.writeBytes(String.format(" mri: %d\n", f.mri));
            // mrsh
            raf.writeBytes(String.format(" mrsh: %d\n", f.mrsh));
            // mrtp
            raf.writeBytes(String.format(" mrtp: %d\n", (int)f.mrtp));
            // mrz
            raf.writeBytes(String.format(" mrz: %d\n", (int)f.mrz));
            // name (already used for root key name)
            // power
            raf.writeBytes(String.format(" power: %f\n", f.power));
            
            raf.writeBytes(" walocs:\n");
            j = 0;
            for (WorldAnchorLocation wal : f.walocs) {
                raf.writeBytes(String.format("  %d:", j++));
                raf.writeBytes(String.format("   byWho: %s\n", wal.byWho));
                raf.writeBytes(String.format("   timePlaced: %d\n", wal.timePlaced));
                raf.writeBytes(String.format("   world: %s\n", wal.w));
                raf.writeBytes(String.format("   x: %d\n", wal.x));
                raf.writeBytes(String.format("   y: %d\n", wal.y));
                raf.writeBytes(String.format("   z: %d\n", wal.z));
            }
            // worthEMC
            raf.writeBytes(String.format(" worthEMC: %d\n", f.worthEMC));
            
            
            HashSet<ZapEntry>[]             zez;
            String                          f_from;
            String                          f_to;
            
            zez = new HashSet[2];
            
            zez[0] = f.zappersIncoming;
            zez[1] = f.zappersOutgoing;
            
            for (HashSet<ZapEntry> hsze : zez) {
                if (hsze == zez[0]) {
                    raf.writeBytes(" zappersIncoming:\n");
                } else {
                    raf.writeBytes(" zappersOutgoing:\n");
                }
                
                j = 0;
                for (ZapEntry ze : f.zappersIncoming) {
                    raf.writeBytes(String.format("  %d:", j++));
                    // amount double
                    raf.writeBytes(String.format("   amount: %f\n", ze.amount));
                    // from
                    raf.writeBytes(String.format("   from: %s\n", ze.from.name));
                    // isFake boolean
                    raf.writeBytes(String.format("   isFake: %d:\n", ze.isFake));
                    // perTick boolean
                    raf.writeBytes(String.format("   perTick: %f:\n", ze.perTick));
                    // timeStart long 
                    raf.writeBytes(String.format("   timeStart: %d\n", ze.timeStart));
                    // timeTick long
                    raf.writeBytes(String.format("   timeTick: %d\n", ze.timeTick));
                    // to Faction
                    raf.writeBytes(String.format("   to: %s\n", ze.to.name));
                }
            }
            // <end of loop>
        }
        
        return;
    }
    
    public int getEMC(int tid, int did) {
        if (!emcMap.containsKey(LongHash.toLong(tid, did)))
            return 0;
        return emcMap.get(LongHash.toLong(tid, did));
    }
    
    @Override
    public void onEnable() {
        File                            file;
        File                            femcvals;
        RandomAccessFile                raf;
        Iterator<Entry<Long, Integer>>  i;
        Entry<Long, Integer>            e;
        
        // ensure that emcvals.txt exists
        femcvals = new File("kfactions.emcvals.txt");
        if (!femcvals.exists()) {
            try {
                raf = new RandomAccessFile(femcvals, "rw");
                i = EMCMap.emcMap.entrySet().iterator();
                while (i.hasNext()) {
                    e = i.next();
                    raf.writeBytes(String.format("%d:%d=%d\n", LongHash.msw(e.getKey()), LongHash.lsw(e.getKey()), e.getValue()));
                }
                raf.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        
        // load from emcvals.txt
        emcMap = new HashMap<Long, Integer>();
        
        try {
            String      line;
            int         epos;
            int         cpos;
            int         tid;
            int         did;
            int         emc;
            
            raf = new RandomAccessFile(femcvals, "rw");
            while ((line = raf.readLine()) != null) {
                epos = line.indexOf("=");
                if (epos > -1) {
                    cpos = line.indexOf(":");
                    tid = Integer.parseInt(line.substring(0, cpos));
                    did = Integer.parseInt(line.substring(cpos + 1, epos));
                    emc = Integer.parseInt(line.substring(epos + 1));
                    emcMap.put(LongHash.toLong(tid, did), emc);
                }
            }
            raf.close();
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        
        
        // configuration
        landPowerCostPerHour = 8192.0 / 24.0 / 4.0;

        file = new File("plugin.gspawn.factions");
        gspawn = null;
        if (file.exists()) {
            RandomAccessFile     fis;
            double               x, y, z;
            String               wname;
            
            try {
                fis = new RandomAccessFile(file, "rw");
                wname = fis.readUTF();
                x = fis.readDouble();
                y = fis.readDouble();
                z = fis.readDouble();
                fis.close();
            } catch (FileNotFoundException ex) {
                wname = "world";
                x = 0.0d;
                y = 0.0d;
                z = 0.0d;
            } catch (IOException ex) {
                wname = "world";
                x = 0.0d;
                y = 0.0d;
                z = 0.0d;
            }
            
            gspawn = new Location(getServer().getWorld(wname), x, y, z);
            getServer().getWorld(wname).setSpawnLocation((int)x, (int)y, (int)z);
        }
        // IF DATA ON DISK LOAD FROM THAT OTHERWISE CREATE
        // A NEW DATA STRUCTURE FOR STORAGE 
        file = new File("plugin.data.factions");
        saveToDisk = true;
        if (file.exists()) {
            try {
                factions = (HashMap<String, Faction>)SLAPI.load("plugin.data.factions");
                Iterator<Entry<String, Faction>>        fi;
                Faction                                 f;
                
                fi = factions.entrySet().iterator();
                smsg("loaded data from disk");
                //while (fi.hasNext()) {
                //    f = fi.next().getValue();
                //    smsg(String.format("object:%x int:%d", f.test, f.test2));
                //}
                
            } catch (Exception ex) {
                factions = new HashMap<String, Faction>();
                saveToDisk = false;
                smsg("error when trying to load data from disk (SAVE TO DISK DISABLED)");
                ex.printStackTrace();
            }
        } else {
            factions = new HashMap<String, Faction>();
            smsg("found no data on disk creating new faction data");
        }
        
        // EVERYTHING WENT OKAY WE PREP THE DISK COMMIT THREAD WHICH WILL RUN LATER ON
        //getServer().getScheduler().scheduleAsyncDelayedTask(this, new DataDumper(this), 20 * 60 * 10);
        
        this.getServer().getPluginManager().registerEvents(new BlockHook(this), this);
        this.getServer().getPluginManager().registerEvents(new EntityHook(this), this);
        this.getServer().getPluginManager().registerEvents(new PlayerHook(this), this);

        try {
            DumpHumanReadableData();
            LoadHumanReadableData();
        } catch (Exception ex) {
            ex.printStackTrace();
        } 
        
        // let faction objects initialize anything <new> .. LOL like new fields
        Iterator<Entry<String, Faction>>            fe;
        Faction                                     f;
        
        fe = factions.entrySet().iterator();
        while (fe.hasNext()) {
            f = fe.next().getValue();
            //getServer().getLogger().info(String.format("§7[f] initFromStorage(%s)", f.name));
            f.initFromStorage();
            // remove world anchors (temp)
            //getServer().getLogger().info("removing world anchors");
            //for (WorldAnchorLocation wal : f.walocs) {
            //    getServer().getWorld(wal.w).getBlockAt(wal.x, wal.y, wal.z).setTypeId(0);
            //    getServer().getWorld(wal.w).getBlockAt(wal.x, wal.y, wal.z).setData((byte)0);
            //}
        }
        
        final P       ___p;
        
        ___p = this;
        getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            public P            p;
            public boolean      isAsyncDone = true;
            
            @Override
            public void run() {
                // copy factions array then schedule async task to work them
                final Entry<String, Faction>[]      flist;
                
                if (!isAsyncDone)
                    return;
                
                isAsyncDone = false;
                
                p = ___p;
                
                getServer().getLogger().info("running flist dumper");
                
                synchronized (p.factions) {
                    flist = new Entry[factions.size()];
                    factions.entrySet().toArray(flist);
                }
                
                getServer().getLogger().info("§7[f] running ticker for flist");
                
                getServer().getScheduler().scheduleAsyncDelayedTask(p, new Runnable() {
                    @Override
                    public void run() {
                        for (Entry<String, Faction> e : flist) {
                            calcZappers(e.getValue());
                        }
                        
                        isAsyncDone = true;
                    }
                });
            }
        }
        , 20 * 10 * 60, 20 * 10 * 60); // every 10 minutes seems decent
        // make job to go through and calculate zappers for factions
    }
    
    public void calcZappers(Faction f) {
        long        ct, tdelta;
        double      toTake;
        
        ct = System.currentTimeMillis();
        // stops modification of power field and of zappers 
        synchronized (f) {
            Iterator<ZapEntry>          i;
            ZapEntry                    z;
            
            i = f.zappersIncoming.iterator();
            
            while (i.hasNext()) {
                z = i.next();
                
                tdelta = ct - z.timeTick;
                z.timeTick = ct;
                toTake = z.perTick * (double)tdelta;
                z.amount -= toTake;
                if (!z.isFake)
                    f.power -= toTake;
                // do not make them go negative that is rather
                // too harsh and could essentially lock out a
                // faction from ever playing again cause they
                // would be unable to ever claim any land with
                // a large negative power
                if (f.power < 0)
                    f.power = 0;
                // if zapper is zero-ed then remove it
                if (z.amount < 1) {
                    i.remove();
                    synchronized (z.from) {
                        z.from.zappersOutgoing.remove(z);
                    }
                }
            }
        }
    }
    
    @Override
    public void onDisable() {
        try {
            if (saveToDisk)
                SLAPI.save(factions, "plugin.data.factions");
            else
                getServer().getLogger().info("§7[f] save to disk was disabled..");
        } catch (Exception e) {
            getServer().getLogger().info("§7[f] unable to save data on disable");
            return;
        }
        getServer().getLogger().info("§7[f] saved data on disable");
    }
    
    public Faction getFactionByName(String factionName) {
        if (factions.containsKey(factionName.toLowerCase()))
            return factions.get(factionName.toLowerCase());
        return null;
    }
    
    public void sendFactionMessage(Faction f, String m) {
        Iterator<Entry<String, FactionPlayer>>      i;
        Player                                      p;
        
        i = f.players.entrySet().iterator();
        while (i.hasNext()) {
            p = getServer().getPlayer(i.next().getValue().name);
            if (p != null) {
                p.sendMessage(m);
            }
        }
    }
    
    public void handlePlayerLogin(PlayerLoginEvent event) {
        FactionPlayer           fp;
        Faction                 f;
        Player                  p;
        
        p = event.getPlayer();

        fp = getFactionPlayer(p.getName());
        if (fp == null)
            return;
        
        f = fp.faction;
        p.sendMessage(String.format("Faction [%s] Hours Until Depletion Is %d/hours.",
            f.name,
            (int)(getFactionPower(f) / ((8192.0 / 24.0) * f.chunks.size()))
        ));
    }
    
    public void handlePlayerLogout(Player p) {
        FactionPlayer           fp;
        
        //fp = getFactionPlayer(p.getName());     
    }
    
    public void handlePlayerInteract(PlayerInteractEvent event) {
        int             x, z;
        World           w;
        FactionChunk    fchunk;
        FactionPlayer   fplayer;
        Player          player;
        int             rank;
        
        player = event.getPlayer();
        //x = player.getLocation().getBlockX() >> 4;
        //z = player.getLocation().getBlockZ() >> 4;
        w = player.getWorld();
        //getServer().getLogger().info(String.format("x:%d z:%d", x, z));
        
        if (event.getClickedBlock() == null)
            return;
        
        x = event.getClickedBlock().getX() >> 4;
        z = event.getClickedBlock().getZ() >> 4;
        //getServer().getLogger().info(String.format("x:%d z:%d", x, z));
        
        fchunk = getFactionChunk(w, x, z);
        if (fchunk == null)
            return;
        
        fplayer = getFactionPlayer(player.getName());        
        
        rank = getPlayerRankOnLand(player, fchunk, fplayer);

        if (fchunk.tidu != null) {
            Block       block;
            TypeDataID  tid;
            
            if (!fchunk.tidudefreject)
                event.setCancelled(false);
            else 
                event.setCancelled(true);
            
            block = event.getClickedBlock();
            tid = new TypeDataID();
            tid.typeId = block.getTypeId();
            tid.dataId = block.getData();
            getServer().getLogger().info(String.format("int:%d:%d", tid.typeId, tid.dataId));
            if (fchunk.tidu.containsKey(tid)) {
                if (rank < fchunk.tidu.get(tid)) {
                    player.sendMessage(String.format("§7[f] Land block rank for block is %d and your rank is %d.", fchunk.tid.get(tid), rank));
                    if (!fchunk.tidudefreject)
                        event.setCancelled(true);
                    else
                        event.setCancelled(false);
                    return;
                }
            }
            //
        }

        if (rank < fchunk.mru) {
            event.setCancelled(true);
            player.sendMessage(String.format("§7[f] Your rank is too low in faction %s.", fchunk.faction.name));
            return;
        }
        
        return;
    }
    
    public boolean isWorldAnchor(int typeId) {
        if ((typeId == 214) || (typeId == 179) || (typeId == 4095))
            return true;
        return false;
    }
    
    public int getPlayerRankOnLand(Player player, FactionChunk fchunk, FactionPlayer fplayer) {
        int         rank;
        
        rank = -1;
        
        if (fchunk == null)
            return -1;
        
        if (fchunk.faction.friends != null) {
            if (fchunk.faction.friends.containsKey(player.getName())) {
                rank = fchunk.faction.friends.get(player.getName());
            }
            if (fplayer != null) {
                if (fchunk.faction.friends.containsKey(fplayer.faction.name)) {
                    rank = fchunk.faction.friends.get(fplayer.faction.name);
                }
            }
        }

        if (rank == -1) {
            if (fplayer != null)
                if (fplayer.faction == fchunk.faction)
                    rank = fplayer.rank;
        }
        
        return rank;
    }
    
    public void handleBlockPlace(BlockPlaceEvent event) {
        int             x, z;
        World           w;
        FactionChunk    fchunk;
        FactionPlayer   fplayer;
        Player          player;
        int             rank;
        Block           block;
        
        if (event.isCancelled())
            return;
        
        block = event.getBlockPlaced();
        player = event.getPlayer();
        
        //        27592, 130
        //if ((block.getTypeId() == 27592) || (block.getTypeId() == 130)) {
        //    event.setCancelled(true);
        //    player.sendMessage("This block is disabled until I can fix it tomorrow.");
        //    return;
        //}
        
        
        x = event.getBlock().getX() >> 4;
        z = event.getBlock().getZ() >> 4;
        w = player.getWorld();
        
        fchunk = getFactionChunk(w, x, z);
        if (fchunk == null) {
            if (isWorldAnchor(block.getTypeId())) {
                player.sendMessage("§7[f] You can only place world anchors if you are on faction land.");
                event.setCancelled(true);
                return;
            }
            return;
        }
        
        fplayer = getFactionPlayer(player.getName());

        rank = getPlayerRankOnLand(player, fchunk, fplayer);
        
        if (fchunk.tid != null) {
            TypeDataID  tid;
            
            if (!fchunk.tiddefreject)
                event.setCancelled(false);
            else 
                event.setCancelled(true);
            
            tid = new TypeDataID();
            tid.typeId = block.getTypeId();
            tid.dataId = block.getData();
            if (fchunk.tid.containsKey(tid)) {
                if (rank < fchunk.tid.get(tid)) {
                    player.sendMessage(String.format("§7[f] Land block rank for block is %d and your rank is %d.", fchunk.tid.get(tid), rank));
                    if (!fchunk.tiddefreject)
                        event.setCancelled(true);
                    else
                        event.setCancelled(false);
                    return;
                }
            }
        }        
        
        if (rank < fchunk.mrb) {
            player.sendMessage(String.format("§7[f] Your rank is too low in faction %s.", fchunk.faction.name));
            event.setCancelled(true);
            return;
        }

        if (isWorldAnchor(block.getTypeId())) {
            if (fchunk.faction.walocs.size() > 1) {
                player.sendMessage(String.format("§7[f] You already have %d/2 world anchors placed. Remove one and replace it.", fchunk.faction.walocs.size()));
                event.setCancelled(true);
                return;
            }
            fchunk.faction.walocs.add(new WorldAnchorLocation(
                    block.getX(), block.getY(), block.getZ(),
                    block.getWorld().getName(),
                    player.getName()
            ));
        }
        
        return;       
    }
    
    public HashSet<Item>        peacefulItems;
    
    public void handleItemSpawn(ItemSpawnEvent event) {
        // check if item spawns on peaceful faction
        // ground and if so add item to special peaceful
        // item set
        //event.getEntity();
    }
    
    public void handlePlayerPickupItem(PlayerPickupItemEvent event) {
        // is this item in the peaceful item set? if so then
        // is this player in a peaceful faction? if so then
        // let them pick it up and if not then forbid them to
        // pick this item up
    }
    
    public void handleBlockBreak(BlockBreakEvent event) {
        int             x, z;
        World           w;
        FactionChunk    fchunk;
        FactionPlayer   fplayer;
        Player          player;
        int             rank;
        Block           block;
        
        if (event.isCancelled())
            return;
        
        block = event.getBlock();
        player = event.getPlayer();
        x = event.getBlock().getX() >> 4;
        z = event.getBlock().getZ() >> 4;
        w = player.getWorld();
        
        fchunk = getFactionChunk(w, x, z);
        if (fchunk == null)
            return;
        
        fplayer = getFactionPlayer(player.getName());
        
        rank = getPlayerRankOnLand(player, fchunk, fplayer);
        
        if (fchunk.tid != null) {
            TypeDataID  tid;
            
            if (!fchunk.tiddefreject)
                event.setCancelled(false);
            else 
                event.setCancelled(true);
            
            tid = new TypeDataID();
            tid.typeId = block.getTypeId();
            tid.dataId = block.getData();
            if (fchunk.tid.containsKey(tid)) {
                if (rank < fchunk.tid.get(tid)) {
                    player.sendMessage(String.format("§7[f] Land block rank for block is %d and your rank is %d.", fchunk.tid.get(tid), rank));
                    if (!fchunk.tiddefreject)
                        event.setCancelled(true);
                    else
                        event.setCancelled(false);
                    return;
                }
            }
        }
        
        // FINAL RANK CHECK DO OR DIE TIME
        if (rank < fchunk.mrb) {
            player.sendMessage(String.format("§7[f] Your rank is too low in faction %s.", fchunk.faction.name));
            event.setCancelled(true);
            return;
        }
        
        // WORLD ANCHOR CONTROL
        if (isWorldAnchor(block.getTypeId())) {
            Iterator<WorldAnchorLocation>       i;
            WorldAnchorLocation                 wal;
            
            i = fchunk.faction.walocs.iterator();
            while (i.hasNext()) {
                wal = i.next();
                if ((wal.x == block.getX()) && 
                    (wal.y == block.getY()) && 
                    (wal.z == block.getZ()) && 
                    (wal.w.equals(block.getWorld().getName()))) {
                    player.sendMessage("§7[f] World anchor removed from faction control. You may now place one more.");
                    i.remove();
                }
            }            
        }
        
        return;
    }
    
    public void handleEntityExplodeEvent(EntityExplodeEvent event) {
        List<Block>     blocks;
        Iterator<Block> iter;
        Block           b;
        int             x, z;
        World           w;
        FactionChunk    fchunk;
        double          pcost;
        
        w = event.getLocation().getWorld();
       
        blocks = event.blockList();
        iter = blocks.iterator();
        while (iter.hasNext()) {
            b = iter.next();
            
            x = b.getX() >> 4;
            z = b.getZ() >> 4;
            
            fchunk = getFactionChunk(w, x, z);
            if (fchunk != null) {
                synchronized (fchunk.faction) {
                    if ((fchunk.faction.flags & NOBOOM) == NOBOOM) {
                        // remove explosion effecting 
                        // this block since it is protected
                        // by the NOBOOM flag
                        iter.remove();
                        continue;
                    }
                    // check if faction can pay for protection
                    // 8192 / 24 is cost per block (equal one hour faction power)
                    pcost = Math.random() * (8192.0 / 24.0 / 2.0 / 5.7 / 4.0);
                    if (getFactionPower(fchunk.faction) >= pcost) {
                        fchunk.faction.power -= pcost;
                        iter.remove();
                        continue;
                    }
                }
            }
        }
    }
    
    public boolean canPlayerBeDamaged(Player p) {
        Location                pl;
        FactionChunk            fc;
        int                     cx, cz;
        
        pl = p.getLocation();
        
        cx = pl.getChunk().getX();
        cz = pl.getChunk().getZ();
        
        fc = getFactionChunk(p.getWorld(), cx, cz);
        if (fc == null)
            return true;
        
        if ((fc.faction.flags & NOPVP) == NOPVP) {
            return false;
        }        
        
        return true;
    }
    
    public void handleEntityDamageEntity(EntityDamageByEntityEvent event) {
        Entity          e;
        Player          p;
        Location        pl;
        int             cx, cz;
        FactionChunk    fc;
        
        e = event.getEntity();
        
        if (!(e instanceof Player))
            return;
        
        p = (Player)e;
        
        pl = p.getLocation();
        
        cx = pl.getChunk().getX();
        cz = pl.getChunk().getZ();
        
        fc = getFactionChunk(p.getWorld(), cx, cz);
        if (fc == null)
            return;
        
        if ((fc.faction.flags & NOPVP) == NOPVP) {
            e = event.getDamager();
            if (e instanceof Player) {
                p = (Player)e;                
                p.sendMessage("§7[f] You can not attack someone who is standing on a NOPVP faction zone!");
            }
            event.setCancelled(true);
            return;
        }
        return;
    }
    
    public void handlePlayerRespawnEvent(PlayerRespawnEvent event) {
        if (gspawn == null)
            return;
        event.setRespawnLocation(gspawn);
    }
    
    public void handlePlayerMove(PlayerMoveEvent event) {
        int             fx, fz;
        int             tx, tz;
        Faction         fc, tc;
        FactionChunk    _fc, _tc;
        World           world;
        Player          player;
        
        fx = event.getFrom().getBlockX() >> 4;
        fz = event.getFrom().getBlockZ() >> 4;
        tx = event.getTo().getBlockX() >> 4;
        tz = event.getTo().getBlockZ() >> 4;
        
        player = event.getPlayer();
        
        // KEEPS US FROM EATING TONS OF CPU CYCLES WHEN ALL WE NEED TO DO
        // IS CHECK ON CHUNK TRANSITIONS
        if ((fx != tx) || (fz != tz)) {
            fc = null;
            tc = null;
            
            world = player.getWorld();
            _fc = getFactionChunk(world, fx, fz);
            if (_fc != null)
                fc = _fc.faction;
            _tc = getFactionChunk(world, tx, tz);
            if (_tc != null)
                tc = _tc.faction;
            // IF WALKING FROM SAME TO SAME SAY NOTHING
            if (fc == tc) {
                return;
            }
            // HANDLES walking from one faction chunk to another or walking from wilderness (fc can be null or not)
            if (tc != null) {
                player.sendMessage(String.format("§7[f] You entered faction %s.", tc.name));
                return;
            }
            // HANDLES walking into wilderness
            if (fc != null) {
                player.sendMessage("§7[f] You entered wilderness.");
                return;
            }
        }
        
    }
    
    public double getFactionPower(Faction f) {
        FactionPlayer                                   fp;
        Iterator<Entry<String, FactionPlayer>>          i;
        float                                           pow;
        long                                            ctime;
        double                                          delta;
        double                                          powcon;                
        
        ctime =  System.currentTimeMillis();
        
        delta = (double)(ctime - f.lpud) / 1000.0d / 60.0d / 60.0d;
        
        // BASED OFF ONE DIAMOND PROVIDING 24HRS OF POWER FOR ONE
        // LAND CLAIM
        //powcon = delta * (double)f.chunks.size() * (8192.0 / 24.0);
        powcon = delta * (double)f.chunks.size() * landPowerCostPerHour;
        
        synchronized (f) {
            f.power = f.power - powcon;
            if (f.power < 0.0)
                f.power = 0.0;
            
            f.lpud = ctime;

            if ((f.flags & NODECAY) == NODECAY) {
                return (f.chunks.size() + 1) * 8192.0;
            }
        }
        return f.power;
    }
    
    @Override
    public boolean isFactionChunk(World world, int x, int z) { 
        return getFactionChunk(world, x, z) != null;
    }
    
    public FactionChunk getFactionChunk(World world, int x, int z) {
        Iterator<Entry<String, Faction>>               i;
        Entry<String, Faction>                         e;
        Faction                                        f;
        Long                                           l;
        
        l = getChunkLong(world, x, z);
        i = factions.entrySet().iterator();
        while (i.hasNext()) {
            e = i.next();
            f = e.getValue();
            
            if (f.chunks.containsKey(l)) {
                return f.chunks.get(l);
            }
        }
        return null;
    }
    
    public FactionPlayer getFactionPlayer(String playerName) {
        Iterator<Entry<String, Faction>>               i;
        Entry<String, Faction>                         e;
        Faction                                        f;
        
        i = factions.entrySet().iterator();
        while (i.hasNext()) {
            e = i.next();
            f = e.getValue();
            if (f.players.containsKey(playerName)) {
                return f.players.get(playerName);
            }
        }
        return null;
    }
    
    public Long getChunkLong(World world, int x, int z) {
        return new Long((world.getUID().getMostSignificantBits() << 32) | (z & 0xffff) | ((x & 0xffff) << 16));
        
    }
    
    public void displayHelp(Player player) {
        player.sendMessage("Faction Command Interface Help");
        player.sendMessage("§6charge§r - charge faction power from item");
        player.sendMessage("§6chkcharge§r - check how much charge from item");
        player.sendMessage("§6unclaimall§r - unclaim all land");
        player.sendMessage("§6unclaim§r - unclaim land claimed");
        player.sendMessage("§6claim§r - claim land standing on");
        player.sendMessage("§6disband§r - disband faction");
        player.sendMessage("§6leave§r - leave current faction");
        player.sendMessage("§6join§r <faction> - join faction after invite");
        player.sendMessage("§6kick§r <player> - kick out of faction");
        player.sendMessage("§6invite§r <player> - invite to faction");
        player.sendMessage("§6create§r <name> - make new faction");
        player.sendMessage("§6setrank§r <player> <rank> - set new rank");
        player.sendMessage("§6cbrank§r <rank> - set chunk build rank");
        player.sendMessage("§6curank§r <rank> - set chunk use rank");
        player.sendMessage("§6inspect§r - inspect chunk you are standing on");
        player.sendMessage("§6addfriend§r <name> <rank> - add friend to faction");
        player.sendMessage("§6remfriend§r <name> - remove faction friend");
        player.sendMessage("§6listfriends§r - inspect chunk you are standing on");
        player.sendMessage("§7[f] cbr, lbr, and br are for block place/break ---");
        player.sendMessage("§7[f] cbru, lbru, and bru are for block interact ---");
        player.sendMessage("§6cbr or cbrus§r - clear block ranks for current claim");
        player.sendMessage("§6lbr or lbru§r - list block ranks for current claim");
        player.sendMessage("§6br or bru§r <rank> <typeID> <dataId(optional)> - or hold item in hand and just give <rank>");
        player.sendMessage("§6setzaprank§r - set rank needed to issue /zap commands");
        player.sendMessage("§6showzaps§r - shows incoming and outgoing zaps");
        player.sendMessage("§6zap§r <faction> <amount> - zap faction's power using your own power");
        player.sendMessage("§6setmri§r <rank> - set minimum rank to invite");
        player.sendMessage("§6setmrc§r <rank> - set minimum rank to claim");
        player.sendMessage("§6setmrz§r <rank> - set minimum rank to zap");        
        player.sendMessage("§6sethome§r - set home for faction for teleport cmds");
        player.sendMessage("§6setmrtp§r <rank> - minimum rank to do teleport cmds and set home");
        player.sendMessage("§6tptp§r <player> <player|home> - teleport player to player ");
        player.sendMessage("§6home§r - short for tptp <yourname> home");
        player.sendMessage("§6spawn§r - short for tptp <yourname> spawn");
        player.sendMessage("§6showanchors§r - shows world anchors your faction has placed");
        player.sendMessage("§6setmrsh§r - sets minimum rank to use /f sethome");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String      cmd;
        Player      player;
        
        // MUST BE TYPED INTO THE CONSOLE
        if (!(sender instanceof Player)) {
            if (args.length < 1) {
                return true;
            }
            
            cmd = args[0];
            
            if (cmd.equals("glitch1")) {
                Faction             f;
                
                f = getFactionByName(args[1]);
                
                f.mrc = 700;
                f.mri = 600;
                return true;
            }
            
            if (command.getName().equals("resetwa")) {
                int                                 x, y, z;
                Iterator<WorldAnchorLocation>       i;
                WorldAnchorLocation                 wal;
                Faction                             f;
                String                              fn;
                String                              w;
                
                fn = args[0];
                w = args[1];
                x = Integer.parseInt(args[2]);
                y = Integer.parseInt(args[3]);
                z = Integer.parseInt(args[4]);

                f = getFactionByName(fn);
                if (f == null) {
                    getServer().getLogger().info("[f] Could not find faction");
                    return true;
                }
                
                i = f.walocs.iterator();
                while (i.hasNext()) {
                    wal = i.next();
                    if ((wal.x == x) && 
                        (wal.y == y) && 
                        (wal.z == z) && 
                        (wal.w.equals(w))) {
                        getServer().getLogger().info("§7[f] World anchor removed from faction control. You may now place one more.");
                        i.remove();
                        return true;
                    }
                }
                getServer().getLogger().info("[f] Could not find world anchor.");
                return true;
            }
            
            if (cmd.equals("setgspawn")) {
                if (args.length < 1) {
                    getServer().getLogger().info("§7[f] setgspawn needs one argument /f setgspawn <playername>");
                    return true;
                }
                Location            l;
                File                file;
                RandomAccessFile    raf;
                
                if (getServer().getPlayer(args[1]) == null) {
                    getServer().getLogger().info("§7[f] player does not exist");
                    return true;
                }
                
                l = getServer().getPlayer(args[1]).getLocation();
                gspawn = l;
                getServer().getLogger().info("§7[f] set global respawn to location of player");
                // ALSO WRITE OUT TO DISK FILE
                file = new File("plugin.gspawn.factions");
                try {
                    raf = new RandomAccessFile(file, "rw");
                    raf.writeUTF(l.getWorld().getName());
                    raf.writeDouble(l.getX());
                    raf.writeDouble(l.getY());
                    raf.writeDouble(l.getZ());
                    raf.close();
                } catch (FileNotFoundException e) {
                    return true;
                } catch (IOException e) {
                    getServer().getLogger().info("§7[f] could not write gspawn to disk!");
                    return true;
                }
                return true;
            }
            if (cmd.equals("noboom")) {
                // f noboom <faction-name>
                Faction                 f;
                
                if (args.length < 1) {
                    getServer().getLogger().info("§7[f] noboom needs one argument /f noboom <faction>");
                    return true;
                }                              
                
                f = getFactionByName(args[1]);
                if (f == null) {
                    getServer().getLogger().info(String.format("§7[f] faction %s can not be found", args[1]));
                    return true;
                }
                
                if ((f.flags & NOBOOM) == NOBOOM) { 
                    f.flags = f.flags & ~NOBOOM;
                    getServer().getLogger().info(String.format("§7[f] NOBOOM toggled OFF on %s", args[1]));
                    return true;
                }
                
                f.flags = f.flags | NOBOOM;
                getServer().getLogger().info(String.format("§7[f] NOBOOM toggled ON on %s", args[1]));
                return true;
            }
            
            if (cmd.equals("nopvp")) {
                // f nopvp <faction-name>
                Faction                 f;

                if (args.length < 1) {
                    getServer().getLogger().info("§7[f] nopvp needs one argument /f nopvp <faction>");
                    return true;
                }                              
                
                f = getFactionByName(args[1]);
                if (f == null) {
                    getServer().getLogger().info(String.format("§7[f] faction %s can not be found", args[1]));
                    return true;
                }
                
                if ((f.flags & NOPVP) == NOPVP) { 
                    f.flags = f.flags & ~NOPVP;
                    getServer().getLogger().info(String.format("§7[f] NOPVP toggled OFF on %s", args[1]));
                    return true;
                }
                
                f.flags = f.flags | NOPVP;
                getServer().getLogger().info(String.format("§7[f] NOPVP toggled ON on %s", args[1]));
                return true;
            }

            if (cmd.equals("nodecay")) {
                // f nopvp <faction-name>
                Faction                 f;
                
                if (args.length < 1) {
                    getServer().getLogger().info("§7[f] nodecay needs one argument /f nodecay <faction>");
                    return true;
                }                
                
                f = getFactionByName(args[1]);
                if (f == null) {
                    getServer().getLogger().info(String.format("§7[f] faction %s can not be found", args[1]));
                    return true;
                }
                
                if ((f.flags & NODECAY) == NODECAY) { 
                    f.flags = f.flags & ~NODECAY;
                    getServer().getLogger().info(String.format("§7[f] NODECAY toggled OFF on %s", args[1]));
                    return true;
                }
                
                f.flags = f.flags | NODECAY;
                getServer().getLogger().info(String.format("§7[f] NODECAY toggled ON on %s", args[1]));
                return true;
            }
            
            if (cmd.equals("yank")) {
                // f yank <faction> <player>
                Faction                 f;
                
                if (args.length < 2) {
                    getServer().getLogger().info("§7[f] yank needs two arguments /f yank <faction> <player>");
                    return true;
                }
                
                f = getFactionByName(args[1]);
                
                if (f == null) {
                    getServer().getLogger().info(String.format("§7[f] faction %s can not be found", args[1]));
                    return true;
                }
                
                if (!f.players.containsKey(args[2])) {
                    getServer().getLogger().info(String.format("§7[f] faction %s has no player %s", args[1], args[2]));
                    return true;
                }
                
                f.players.remove(args[2]);
                getServer().getLogger().info(String.format("§7[f] player %s was yanked from faction %s", args[2], args[1]));
                return true;
            }
            
            if (cmd.equals("stick")) {
                // f stick <faction> <player>
                FactionPlayer           fp;
                Faction                 f;

                if (args.length < 2) {
                    getServer().getLogger().info("§7[f] stick needs two arguments /stick <faction> <player>");
                    return true;
                }                
                
                fp = getFactionPlayer(args[2]);
                if (fp != null) {
                    fp.faction.players.remove(fp.name);
                }
                
                f = getFactionByName(args[1]);
                if (f == null) {
                    getServer().getLogger().info(String.format("§7[f] faction %s can not be found", args[1]));
                    return true;
                }
                
                fp = new FactionPlayer();
                fp.rank = 1000;
                fp.name = args[2];
                fp.faction = f;
                
                f.players.put(args[2], fp);
                
                getServer().getLogger().info(String.format("§7[f] player %s was stick-ed in faction %s", args[2], args[1]));
                return true;
            }
        }
        
        if (!(sender instanceof Player))
            return false;
        
        player = (Player)sender;
        
        if (command.getName().equals("home")) {
            player.sendMessage("§7[f] Use /f home (requires a faction)");
            return true;
        }
        
        if (command.getName().equals("spawn")) {
            player.sendMessage("§7[f] Use /f spawn (requires a faction)");
            return true;
        }
        
        if (args.length < 1) {
            if (sender instanceof Player)
                displayHelp((Player)sender);
            return false;
        }
        
        cmd = args[0];
        
        //this.getServer().getPluginManager().getPlugins()[0].
        //setmri, setmrc, setmrsp, setmrtp, 
        //sethome, tptp
        
        if (cmd.equals("showanchors")) {
            Iterator<WorldAnchorLocation>       i;
            WorldAnchorLocation                 wal;
            FactionPlayer                       fp;

            fp = getFactionPlayer(player.getName());
            
            if (fp == null) {
                player.sendMessage("§7[f] §7You are not in a faction.");
                return true;
            }
            
            player.sendMessage("§7[f] Showing Placed World Anchors");
            i = fp.faction.walocs.iterator();
            while (i.hasNext()) {
                wal = i.next();
                player.sendMessage(String.format(
                    "  x:%d y:%d z:%d w:%s by:%s age:%d/days", 
                    wal.x, wal.y, wal.z, wal.w, wal.byWho,
                    (System.currentTimeMillis() - wal.timePlaced) / 1000 / 60 / 60 / 24
                ));
            }
            return true;
        }
        
        if (cmd.equals("curank") || cmd.equals("cbrank")) {
            FactionPlayer           fp;
            FactionChunk            fc;
            Location                loc;
            int                     bx, bz;
            int                     irank;
            
            loc = player.getLocation();
            fp = getFactionPlayer(player.getName());
            
            if (fp == null) {
                player.sendMessage("§7[f] §7You are not in a faction.");
                return true;
            }
            
            bx = loc.getBlockX() >> 4;
            bz = loc.getBlockZ() >> 4;
            
            fc = getFactionChunk(player.getWorld(), bx, bz);
            
            if (fc == null) {
                player.sendMessage("§7[f] §7Land not claimed by anyone.");
                return true;
            }
            
            if ((fc.faction != fp.faction) && (!player.isOp())) {
                player.sendMessage("§7[f] §7Land not claimed by your faction.");
                return true;
            }

            if ((fc.mrb >= fp.rank) && (!player.isOp())) {
                player.sendMessage("§7[f] §7Land rank is equal or greater than yours.");
                return true;
            }
            
            if (args.length < 2) {
                player.sendMessage("§7[f] §7The syntax is /f curank <value> OR /f cbrank <value>");
                return true;
            }
            
            irank = Integer.valueOf(args[1]);
            
            if ((irank >= fp.rank) && (!player.isOp())) {
                player.sendMessage("§7[f] §7Your rank is too low.");
                return true;
            }
            
            if (cmd.equals("curank")) {
                fc.mru = irank;
            } else {
                fc.mrb = irank;
            }
            player.sendMessage("§7[f] §7The rank was set. Use /f inspect to check.");
            return true;
        }
        
        if (cmd.equals("sethome")) {
            FactionPlayer           fp;
            FactionChunk            fc;
            
            fp = getFactionPlayer(player.getName());
            if (fp == null) {
                player.sendMessage("§7[f] You must be in a faction!");
                return true;                
            }
            
            fc = getFactionChunk(player.getWorld(), player.getLocation().getBlockX() >> 4, player.getLocation().getBlockZ() >> 4);
            
            if (fc == null) {
                player.sendMessage("§7[f] Must be on faction land.");
                return true;
            }
            
            if (fc.faction != fp.faction) {
               player.sendMessage("§7[f] Must be on your faction land.");
                return true;
            }
            
            if (fp.rank < fp.faction.mrsh) {
                player.sendMessage("§7[f] Your rank is too low.");
                return true;
            }
            
            fp.faction.hx = player.getLocation().getX();
            fp.faction.hy = player.getLocation().getY();
            fp.faction.hz = player.getLocation().getZ();
            fp.faction.hw = player.getLocation().getWorld().getName();
            
            player.sendMessage("§7[f] Faction home set.");
            return true;
        }
        if (cmd.equals("tptp") || cmd.equals("home") || cmd.equals("spawn")) {
            FactionPlayer           fp;
            
            if (cmd.equals("spawn")) {
                args = new String[3];
                args[1] = player.getName();
                args[2] = "spawn";
            }
            
            if (cmd.equals("home")) {
                args = new String[3];
                args[1] = player.getName();
                args[2] = "home";                
            }
            
            fp = getFactionPlayer(player.getName());
            if (fp == null) {
                player.sendMessage("§7[f] You must be in a faction!");
                return true;                
            }
            
            if (fp.rank < fp.faction.mrtp) {
                player.sendMessage("§7[f] Your rank is too low.");
                return true;
            }
            
            if (args.length < 3) {
                player.sendMessage("§7[f] Use syntax /f tptp <player> <player/home>");
                return true;
            }
            
            FactionPlayer       src;
            FactionPlayer       dst;
            
            src = getFactionPlayer(args[1]);
            dst = getFactionPlayer(args[2]);
            
            if (src == null) {
                player.sendMessage("§7[f] Source player does not exist.");
                return true;
            }
            
            if (src.rank > fp.rank) {
                player.sendMessage(String.format("§7[f] The player %s is higher in rank than you. Ask him.", args[2]));
                return true;
            }

            if (dst == null) {
                if (!args[2].equals("home") && !args[2].equals("spawn")) {
                    player.sendMessage("§7[f] Source player does not exist, or use word $home.");
                    return true;
                }
            } else {
                if (dst.rank > fp.rank) {
                    player.sendMessage(String.format("§7[f] The player %s is higher in rank than you. Ask him.", args[3]));
                    return true;
                }
            }
            
            Location        loc;
            
            if (dst != null) {
                if ((src.faction != dst.faction) || (src.faction != fp.faction)) {
                    player.sendMessage("§7[f] Everyone has to be in the same faction.");
                    return true;
                }
                
                loc = getServer().getPlayer(args[2]).getLocation();
                if (getServer().getPlayer(args[1]) != null) {
                    getServer().getPlayer(args[1]).teleport(loc);
                    synchronized (fp.faction) {
                        fp.faction.power = fp.faction.power - (fp.faction.power * 0.1);
                    }
                } else {
                    player.sendMessage(String.format("§7[f] The player %s was not found.", args[1]));
                }
                return true;
            }
            
            if ((src.faction != fp.faction)) {
                player.sendMessage("§7[f] Everyone has to be in the same faction.");
                return true;
            }
            
            // are we going home or to spawn?
            if (args[2].equals("spawn")) {
                loc = gspawn;
                if (gspawn == null) {
                    player.sendMessage("§7[f] The spawn has not been set for factions!");
                    return true;
                }
            } else {
                // teleport them to home
                if (fp.faction.hw == null) {
                    player.sendMessage("§7[f] The faction home is not set! Use /f sethome");
                    return true;
                }
                loc = new Location(getServer().getWorld(fp.faction.hw), fp.faction.hx, fp.faction.hy, fp.faction.hz);                
            }
            
            getServer().getPlayer(args[1]).teleport(loc);
            synchronized (fp.faction) {
                fp.faction.power = fp.faction.power - (fp.faction.power * 0.1);
            }
            return true;
        }
        if (cmd.equals("setmrsh")) {
            FactionPlayer           fp;
            int                     rank;
            
            fp = getFactionPlayer(player.getName());
            if (fp == null) {
                player.sendMessage("§7[f] You must be in a faction!");
                return true;                
            }
            
            if (fp.rank < fp.faction.mrsh) {
                player.sendMessage("§7[f] Your rank is too low.");
                return true;
            }
            
            if (args.length < 2) {
                player.sendMessage("§7[f] Use /f setmrsh <rank>");
                return true;
            }
            
            rank = Integer.parseInt(args[1]);
            
            if (rank > fp.rank) {
                player.sendMessage("§7[f] You can not set the rank higher than your own.");
                return true;
            }
            
            fp.faction.mrsh = rank;
            player.sendMessage("§7[f] The rank was changed.");
            return true;
        }
        if (cmd.equals("setmrtp")) {
            FactionPlayer           fp;
            int                     rank;
            
            fp = getFactionPlayer(player.getName());
            if (fp == null) {
                player.sendMessage("§7[f] You must be in a faction!");
                return true;                
            }
            
            if (fp.rank < fp.faction.mrtp) {
                player.sendMessage("§7[f] Your rank is too low.");
                return true;
            }
            
            if (args.length < 2) {
                player.sendMessage("§7[f] Use /f setmrtp <rank>");
                return true;
            }
            
            rank = Integer.parseInt(args[1]);
            
            if (rank > fp.rank) {
                player.sendMessage("§7[f] You can not set the rank higher than your own.");
                return true;
            }
            
            fp.faction.mrtp = rank;
            player.sendMessage("§7[f] The rank was changed.");
            return true;
        }
        if (cmd.equals("setmrz")) {
            FactionPlayer           fp;
            int                     rank;
            
            fp = getFactionPlayer(player.getName());
            if (fp == null) {
                player.sendMessage("§7[f] You must be in a faction!");
                return true;                
            }
            
            if (fp.rank < fp.faction.mrz) {
                player.sendMessage("§7[f] Your rank is too low.");
                return true;
            }
            
            if (args.length < 2) {
                player.sendMessage("§7[f] Use /f setmrz <rank>");
                return true;
            }
            
            rank = Integer.parseInt(args[1]);
            
            if (rank > fp.rank) {
                player.sendMessage("§7[f] You can not set the rank higher than your own.");
                return true;
            }
            
            fp.faction.mrz = rank;
            player.sendMessage("§7[f] The rank was changed.");
            return true;
        }
        if (cmd.equals("setmrc")) {
            FactionPlayer           fp;
            int                     rank;
            
            fp = getFactionPlayer(player.getName());
            if (fp == null) {
                player.sendMessage("§7[f] You must be in a faction!");
                return true;                
            }
            
            if (fp.rank < fp.faction.mrc) {
                player.sendMessage("§7[f] Your rank is too low.");
                return true;
            }
            
            if (args.length < 2) {
                player.sendMessage("§7[f] Use /f setmrc <rank>");
                return true;
            }
            
            rank = Integer.parseInt(args[1]);
            
            if (rank > fp.rank) {
                player.sendMessage("§7[f] You can not set the rank higher than your own.");
                return true;
            }
            
            fp.faction.mrc = rank;
            player.sendMessage("§7[f] The rank was changed.");
            return true;
        }
        if (cmd.equals("setmri")) {
            FactionPlayer           fp;
            int                     rank;
            
            fp = getFactionPlayer(player.getName());
            if (fp == null) {
                player.sendMessage("§7[f] You must be in a faction!");
                return true;                
            }
            
            if (fp.rank < fp.faction.mri) {
                player.sendMessage("§7[f] Your rank is too low.");
                return true;
            }
            
            if (args.length < 2) {
                player.sendMessage("§7[f] Use /f setmri <rank>");
                return true;
            }
            
            rank = Integer.parseInt(args[1]);
            
            if (rank > fp.rank) {
                player.sendMessage("§7[f] You can not set the rank higher than your own.");
                return true;
            }
            
            fp.faction.mri = rank;
            player.sendMessage("§7[f] The rank was changed.");
            return true;
        }
        
        if (cmd.equals("setzaprank")) {
            FactionPlayer           fp;
            
            fp = getFactionPlayer(player.getName());
            if (fp == null) {
                player.sendMessage("§7[f] You must be in a faction!");
                return true;
            }
            
            if (args.length < 2) {
                player.sendMessage("§7[f] Too few arguments. Use /f setzaprank <rank>");
                return true;
            }
            
            if (fp.rank < fp.faction.mrz) {
                player.sendMessage("§7[f] You are lower than the MRZ rank, so you can not change it.");
                return true;
            }
            
            fp.faction.mrz = Integer.parseInt(args[1]);
            return true;
        }
        
        if (cmd.equals("showzaps")) {
            FactionPlayer           fp;
            Faction                 f;

            fp = getFactionPlayer(player.getName());
            if (fp == null) {
                player.sendMessage("§7[f] You must be in a faction!");
                return true;
            }
            
            f = fp.faction;
            
            synchronized (f) {
                double     grandTotal;

                player.sendMessage("[§6f§r] [§6DIR§r ] [§6AMOUNT§r    ] [§6FACTION§r]");
                for (ZapEntry e : f.zappersOutgoing) {
                    player.sendMessage(String.format("    §6OUT§r (%11f) §6%s", e.amount, e.to.name));
                }
                
                grandTotal = 0;
                for (ZapEntry e : f.zappersIncoming) {
                    player.sendMessage(String.format("    §6IN§r   (%11f) §6%s", e.amount, e.from.name));
                    if (!e.isFake)
                        grandTotal += e.amount;
                }
                player.sendMessage(String.format("TOTAL ZAP INCOMING: %f", grandTotal));
            }
            
            return true;
        }
        
        if (cmd.equals("zap")) {
            FactionPlayer           fp;
            Faction                 tf;
            Faction                 f;
            int                     amount;
            String                  target;
            
            fp = getFactionPlayer(player.getName());
            if (fp == null) {
                player.sendMessage("§7[f] You must be in a faction!");
                return true;
            }
            
            if (fp.rank < fp.faction.mrz) {
                player.sendMessage("§7[f] You do not have enough rank to ZAP.");
                return true;
            }
            // <faction> <amount>
            if (args.length < 3) {
                player.sendMessage("§7[f] The syntax is /f zap <faction> <amount>");
                return true;
            }
            
            target = args[1];
            amount = Integer.parseInt(args[2]);
            
            if (amount <= 0) {
                player.sendMessage("The amount of zap must be greater then zero.");
                return true;
            }
            
            if (amount > fp.faction.power) {
                player.sendMessage(String.format("§7[f] You do not have %d in power to ZAP with.", amount));
                return true;
            }
            
            tf = getFactionByName(target);
            
            if (tf == null) {
                player.sendMessage(String.format("§7[f] Faction could not be found by the name '%s'", target));
                return true;
            }
            
            f = fp.faction;
            
            // make sure not more than 20 zaps are going this
            // is to prevent a DOS memory attack by flooding us with
            // thousands of entries
            Iterator<ZapEntry>      i;
            int                     zapCnt;
            
            i = tf.zappersIncoming.iterator();
            
            zapCnt = 0;
            while (i.hasNext()) {
                if (i.next().from == f)
                    zapCnt++;
            }
            
            if (zapCnt > 20) {
                player.sendMessage("§7[f] You reached maximum number of active zaps [20]");
                return true;
            }

            ZapEntry            ze;
            
            ze = new ZapEntry();
            
            if (tf.power < f.power) {
                // target faction had less than us
                ze.isFake = true;
            } else {
                // target faction has more than us (or equal)
                ze.isFake = false;
            }
            
            ze.amount = (double)amount;
            ze.timeStart = System.currentTimeMillis();
            ze.timeTick = ze.timeStart;
            ze.perTick = (double)amount / (1000.0d * 60.0d * 60.0d * 48.0d);
            
            if (ze.perTick <= 0.0d) {
                // ensure it will at least drain amount which
                // will result in the ZapEntry's removal
                ze.perTick = 1.0d;
            }
            
            ze.from = f;
            ze.to = tf;
            
            synchronized (f) {
                f.zappersOutgoing.add(ze);
                tf.zappersIncoming.add(ze);
            
            // go ahead and subtract what they spent
                f.power -= amount;
            }
            
            player.sendMessage("§7[f] Zap has commenced.");
            return true;
        }
        
        if (cmd.equals("create")) {
            FactionPlayer         fp;
            Faction               f;
            
            fp = getFactionPlayer(player.getName());
            
            args[1] = sanitizeString(args[1]);
            
            if (fp != null) {
                player.sendMessage("§7[f] You must leave your current faction to create a new faction.");
                return true;
            }
            
            if (args.length < 2) {
                player.sendMessage("§7[f] You must specify the new faction name. /f create <faction-name>");
                return true;
            }
            
            if (factions.containsKey(args[1].toLowerCase())) {
                player.sendMessage(String.format("§7[f] The faction name %s is already taken.", args[1]));
                return true;
            }
            
            f = new Faction();
            f.name = args[1];
            f.desc = "default description";
            f.mrc = 700;
            f.mri = 600;
            
            fp = new FactionPlayer();
            fp.faction = f;
            fp.name = player.getName();
            fp.rank = 1000;
            f.players.put(player.getName(), fp);
            
            getServer().broadcastMessage(String.format("§7[f] %s created new faction %s", player.getName(), args[1]));
            
            synchronized (factions) {
                factions.put(args[1].toLowerCase(), f);
            }
            return true;
        }
        
        if (cmd.startsWith("cbr")) {
            FactionPlayer       fp;
            FactionChunk        fc;
            int                 x, z;
            Map<TypeDataID, Integer>     m;
            
            fp = getFactionPlayer(player.getName());
            if (fp == null) {
                player.sendMessage("§7[f] You must be in a faction!");
                return true;
            }
            
            x = player.getLocation().getBlockX();
            z = player.getLocation().getBlockZ();
            
            fc = getFactionChunk(player.getWorld(), x >> 4, z >> 4);
            
            if (fc == null) {
                player.sendMessage("§7[f] This land is not owned");
                return true;
            }
            
            if (fc.faction != fp.faction) {
                player.sendMessage(String.format("§7[f] This land is owned by %s", fp.faction.name));
                return true;
            }
            
            if (cmd.equals("cbru")) {
                m = fc.tidu;
            } else {
                m = fc.tid;
            }
            
            player.sendMessage("§7[f] Clearing Block Ranks");
            if (m != null) {
                Iterator<Entry<TypeDataID, Integer>>         i;
                Entry<TypeDataID, Integer>                   e;
                TypeDataID                                   tid;
                
                i = m.entrySet().iterator();
                
                while (i.hasNext()) {
                    e = i.next();
                    tid = e.getKey();
                    if (e.getValue() >= fp.rank) {
                        player.sendMessage(String.format("  Rank too low for [%d:%d]:%d", tid.typeId, tid.dataId, e.getValue()));
                    } else {
                        i.remove();
                    }
                }
            }
            
            return true;
        }

        if (cmd.startsWith("lbr")) {
            FactionPlayer       fp;
            FactionChunk        fc;
            int                 x, z;
            Map<TypeDataID, Integer>     m;
            
            fp = getFactionPlayer(player.getName());
             if (fp == null) {
                player.sendMessage("§7[f] You must be in a faction!");
                return true;
            }
            
            x = player.getLocation().getBlockX();
            z = player.getLocation().getBlockZ();
            
            fc = getFactionChunk(player.getWorld(), x >> 4, z >> 4);
            
            if (fc == null) {
                player.sendMessage("§7[f] This land is not owned");
                return true;
            }
            
            if (cmd.equals("lbru")) {
                m = fc.tidu;
            } else {
                m = fc.tid;
            }
            
            player.sendMessage("§7[f] List Block Rank For Current Claim");
            if (m != null) {
                Iterator<Entry<TypeDataID, Integer>>         i;
                Entry<TypeDataID, Integer>                   e;
                TypeDataID                                   tid;
                
                i = m.entrySet().iterator();
                
                while (i.hasNext()) {
                    e = i.next();
                    tid = e.getKey();
                    
                    player.sendMessage(String.format("  [%2d:%2d]    %d", tid.typeId, tid.dataId, e.getValue()));
                }
            }
            return true;
        }
        
        if (cmd.startsWith("br")) {
            FactionPlayer       fp;
            FactionChunk        fc;
            int                 typeId;
            byte                typeData;
            TypeDataID      tdir;
            int                 rank;
            int                 x, z;
            Map<TypeDataID, Integer>     m;
            
            fp = getFactionPlayer(player.getName());
            if (fp == null) {
                player.sendMessage("§7[f] You must be in a faction!");
                return true;
            }
            
            x = player.getLocation().getBlockX();
            z = player.getLocation().getBlockZ();
            
            fc = getFactionChunk(player.getWorld(), x >> 4, z >> 4);
                       
            if (fc == null) {
                player.sendMessage("This land is not claimed.");
                return true;
            }
            
            if (fc.faction != fp.faction) {
                player.sendMessage(String.format("§7[f] This land is owned by %s.", fc.faction.name));
                return true;
            }
            
            if (args.length == 1) {
                player.sendMessage("§7[f] Either hold item in hand, or use /f blockrank <rank> <typeId> <dataId(optinal)>");
                return true;
            }
            
            rank = Integer.parseInt(args[1]);
            
            if (rank >= fp.rank) {
                player.sendMessage("§7[f] You can not set a block rank equal or greater than your rank.");
                return true;
            }

            if (args.length > 2) {
                typeId = Integer.parseInt(args[2]);
                if (args.length < 4) {
                    typeData = (byte)0;
                } else {
                    typeData = (byte)Integer.parseInt(args[3]);
                }
            } else {
                if (player.getItemInHand().getTypeId() == 0) {
                    player.sendMessage("§7[f] Either hold item in hand, or use /f blockrank <rank> <typeId> <dataId(optinal)>");
                    return true;
                }
                typeId = player.getItemInHand().getTypeId();
                typeData = player.getItemInHand().getData().getData();
            }
            
            tdir = new TypeDataID();
            tdir.typeId = typeId;
            tdir.dataId = typeData;
            
            if (cmd.equals("bru")) {
                if (fc.tidu == null)
                    fc.tidu = new HashMap<TypeDataID, Integer>();
                m = fc.tidu;
            } else {
                if (fc.tid == null)
                    fc.tid = new HashMap<TypeDataID, Integer>();
                m = fc.tid;
            }
            
            /// UPGRADE CODE BLOCK
            if (m.containsKey(tdir)) {
                if (m.get(tdir) >= fp.rank) {
                    player.sendMessage(String.format("§7[f] Block rank exists [%d] and is equal or higher than your rank [%d].", fc.tid.get(tdir), fp.rank));
                    return true;
                }
            }
            
            m.put(tdir, rank);
            player.sendMessage(String.format("§7[f] Block %d:%d at rank %d added to current claim.", typeId, typeData, rank));
            return true;
        }
            
        if (cmd.equals("invite")) {
            FactionPlayer       fp;
            Player              p;
            
            fp = getFactionPlayer(player.getName());
            if (fp == null) {
                player.sendMessage("§7[f] You must be in a faction!");
                return true;
            }
            
            if (args.length < 2) {
                player.sendMessage("§7[f] You must specify the name of whom to invite! /f invite <player-name>");
                return true;
            }
            
            if (fp.rank < fp.faction.mri) {
                player.sendMessage(String.format("§7[f] Your rank is %d but needs to be %d.", fp.rank, fp.faction.mri));
                return true;
            }
            
            p = getServer().getPlayer(args[1]);
            
            if (p == null) {
                player.sendMessage("§7[f] The player must be online to invite them.");
                return true;
            }
            
            fp.faction.invites.add(args[1].toLowerCase());
            sendFactionMessage(fp.faction, String.format("§7[f] %s has been invited to your faction", args[1]));
            if (p != null) {
                p.sendMessage(String.format("§7[f] You have been invited to %s by %s. Use /f join %s to join!", fp.faction.name, fp.name, fp.faction.name));
            } 
            return true;
        }
        
        if (cmd.equals("kick")) {
            FactionPlayer       fp;
            FactionPlayer       _fp;
            
            fp = getFactionPlayer(player.getName());
            
            if (fp.rank < fp.faction.mri) {
                player.sendMessage("§7[f] Your rank is too low to invite or kick.");
                return true;
            }
            
            if (args.length < 2) {
                player.sendMessage("§7[f] You must specify the player name. /f kick <player-name>");
                return true;
            }
            
            _fp = getFactionPlayer(args[1]);
            
            if (_fp == null) {
                player.sendMessage("§7[f] Player specified is not in a faction or does not exist.");
                return true;
            }
            
            if (_fp.faction != fp.faction) {
                player.sendMessage(String.format("§7[f] Player is in faction %s and you are in faction %s.", _fp.faction.name, fp.faction.name));
                return true;
            }
            
            if (_fp.rank >= fp.rank) {
                player.sendMessage(String.format("§7[f] Player %s at rank %d is equal or higher than your rank of %d", _fp.name, _fp.rank, fp.rank));
                return true;
            }
            
            fp.faction.players.remove(_fp.name);
            getServer().broadcastMessage(String.format("§7[f] %s was kicked from faction %s by %s", _fp.name, fp.faction.name, fp.name));
            
            return true;
        }
        
        if (cmd.equals("join")) {
            FactionPlayer       fp;
            Faction             f;
            
            fp = getFactionPlayer(player.getName());
            if (fp != null) {
                player.sendMessage("§7[f] You must leave you current faction to join another one.");
                return true;
            }
            
            if (args.length < 2) {
                player.sendMessage("§7[f] You must specify the faction to join. /f join <faction-name>");
                return true;
            }
            
            f = getFactionByName(args[1]);
            if (f == null) {
                player.sendMessage("§7[f] No faction found by that name.");
                return true;
            }
            
            // FIX FOR OLDER VER CLASS
            if (f.invites == null)
                f.invites = new HashSet<String>();
            
            Iterator<String>            i;
            
            i = f.invites.iterator();
            
            while (i.hasNext()) {
                if (i.next().toLowerCase().equals(player.getName().toLowerCase())) {
                    f.invites.remove(player.getName());

                    fp = new FactionPlayer();
                    fp.faction = f;
                    fp.name = player.getName();
                    fp.rank = 0;
                    f.players.put(player.getName(), fp);
                    getServer().broadcastMessage(String.format("§7[f] %s just joined the faction [%s].", fp.name, f.name));
                    return true;
                }
            }
            
            player.sendMessage("You have no invintation to join that faction!");
            return true;
            
        }        
        
        if (cmd.equals("leave")) {
            FactionPlayer           fp;
            FactionChunk            fchunk;
            
            fp = getFactionPlayer(player.getName());
            if (fp == null) {
                player.sendMessage("§7[f] You are not in a faction.");
                return true;
            }
            
            /// MAKE SURE WE ARE NOT STANDING ON FACTION CLAIMED LAND
            fchunk = getFactionChunk(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
            if (fchunk != null) {
                if (fchunk.faction == fp.faction) {
                    player.sendMessage("§7[f] You must not be on your faction land when leaving the faction.");
                    return true;
                }
            }
            /// MAKE SURE NOT EMPTY IF SO NEED TO USE DISBAND
            /// IF WE ARE OWNER WE NEED TO TRANSFER OWNERSHIP BEFORE WE LEAVE
            if (fp.faction.players.size() == 1) {
                // ENSURE THEY ARE HIGH ENOUGH FOR OWNER (CATCH BUGS KINDA)
                fp.rank = 1000;
                player.sendMessage("§7[f] You are the last player in faction use /f disband.");
                return true;
            }
            
            // IF THEY ARE THE OWNER HAND OWNERSHIP TO SOMEBODY ELSE KINDA AT RANDOM
            if (fp.rank == 1000) {
                Iterator<Entry<String, FactionPlayer>>      i;
                FactionPlayer                               _fp;
                
                i = fp.faction.players.entrySet().iterator();
                _fp = null;
                while (i.hasNext()) {
                    _fp = i.next().getValue();
                    if (!fp.name.equals(_fp.name))
                        break;
                }
                _fp.rank = 1000;
                getServer().broadcastMessage(String.format("§7[f] Ownership of %s was handed to %s at random.", fp.faction.name, fp.name));
            }
            
            fp.faction.players.remove(fp.name);
            sendFactionMessage(fp.faction, String.format("§7[f] %s has left the faction", fp.name));
            return true;
        }
        
        if (cmd.equals("disband")) {
            FactionPlayer               fp;
            
            fp = getFactionPlayer(player.getName());
            if (fp == null) {
                player.sendMessage("§7[f] You are not in a faction.");
                return true;
            }
            
            // MUST BE OWNER OF FACTION
            if (fp.rank < 1000) {
                player.sendMessage("§7[f] You are not owner of faction.");
                return true;
            }
            
            fp.faction.chunks = new HashMap<Long, FactionChunk>(); 
            
            getServer().broadcastMessage(String.format("§7[f] %s has been disbanded", fp.faction.name));
            factions.remove(fp.faction.name.toLowerCase());
            return true;
        }
        
        if (cmd.equals("listfriends")) {
            String                              friendName;
            Faction                             f;
            FactionPlayer                       fp;
            int                                 frank;
            
            Iterator<Entry<String, Integer>>    i;
            Entry<String, Integer>              e;
            
            fp = getFactionPlayer(player.getName());
            if (fp == null) {
                player.sendMessage("§7[f] You are not in a faction.");
                return true;
            }
            
            if (fp.faction.friends == null) {
                player.sendMessage("§7[f] Faction has no friends.");
                return true;
            }
            
            i = fp.faction.friends.entrySet().iterator();
            while (i.hasNext()) {
                e = i.next();
                player.sendMessage(String.format("§7[f] %s => %d", e.getKey(), e.getValue()));
            }
            player.sendMessage("§7[f] Done");
            return true;
        }
        
        if (cmd.equals("addfriend")) {
            String          friendName;
            Faction         f;
            FactionPlayer   fp;
            int             frank;

            fp = getFactionPlayer(player.getName());
            if (fp == null) {
                player.sendMessage("§7[f] You are not in a faction.");
                return true;
            }
            
            if (args.length < 3) {
                player.sendMessage("Syntax is /f addfriend <name> <rank>. Use again to change rank. Use /f remfriend to remove.");
                return true;
            }
            
            friendName = args[1];
            frank = Integer.parseInt(args[2]);

            if (frank >= fp.rank) {
                player.sendMessage(String.format("§7[f] You can not set friend rank of %d because it is higher than your rank of %d.", frank, fp.rank));
                return true;
            }
            
            if (fp.faction.friends == null)
                fp.faction.friends = new HashMap<String, Integer>();

            fp.faction.friends.put(friendName, frank);
            sendFactionMessage(fp.faction, String.format("§7[f] Added friend %s at rank %d\n", friendName, frank));
            return true;
        }
        
        if (cmd.equals("remfriend")) {
            String          friendName;
            Faction         f;
            FactionPlayer   fp;
            int             frank;

            fp = getFactionPlayer(player.getName());
            if (fp == null) {
                player.sendMessage("§7[f] You are not in a faction.");
                return true;
            }
            
            if (args.length < 2) {
                player.sendMessage("Syntax is /f remfriend <name>.");
                return true;
            }
            
            friendName = args[1];
            
            if (fp.faction.friends == null)
                fp.faction.friends = new HashMap<String, Integer>();
            
            if (fp.faction.friends.containsKey(friendName)) {
                frank = fp.faction.friends.get(friendName);
                if (frank >= fp.rank) {
                    player.sendMessage(String.format("§7[f] You can not remove friend with rank of %d because it is higher than your rank of %d.", frank, fp.rank));
                    return true;
                }
            }
            
            fp.faction.friends.remove(friendName);
            sendFactionMessage(fp.faction, String.format("§7[f] Removed friend %s\n", friendName));
            return true;            
        }
        
        if (cmd.equals("claim")) {
            FactionPlayer               fp;
            int                         x, z;
            FactionChunk                fchunk;
            double                      pow;
            
            if (player == null) {
                player.sendMessage("§7[f] uhoh plyer is null");
                return true;
            }
            
            fp = getFactionPlayer(player.getName());
            
            if (fp == null) {
                player.sendMessage("§7[f] You are not in a faction.");
                return true;
            }
            
            // IS OUR RANK GOOD ENOUGH?
            if (fp.rank < fp.faction.mrc) {
                player.sendMessage(String.format("§7[f] Your rank of %d is below the required rank of %d to claim/unclaim.", fp.rank, fp.faction.mrc));
                return true;
            }
            
            // DO WE HAVE ENOUGH POWER? 
            // NEED ENOUGH TO HOLD FOR 24 HOURS
            pow = getFactionPower(fp.faction);
            if (pow < ((fp.faction.chunks.size() + 1) * 24.0)) {
                player.sendMessage("§7[f] The faction lacks needed power to claim land");
                return true;
            }
            
            smsg(String.format("blockx:%d", player.getLocation().getBlockX()));
            
            x = player.getLocation().getBlockX();
            z = player.getLocation().getBlockZ();
            
            fchunk = getFactionChunk(player.getWorld(), x >> 4, z >> 4);
            if (fchunk != null) {
                if (fchunk.faction == fp.faction) {
                    player.sendMessage(String.format("§7[f] chunk already owned by your faction %s", fchunk.faction.name));
                    return true;
                }

                if (fchunk.faction != fp.faction) {
                    if (getFactionPower(fchunk.faction) >= fchunk.faction.chunks.size()) {
                        player.sendMessage(String.format("§7[f] faction %s has enough power to hold this claim", fchunk.faction.name));
                        return true;
                    }
                    getServer().broadcastMessage(String.format("§7[f] %s lost land claim to %s by %s", fchunk.faction.name, fp.faction.name, fp.name));
                    fchunk.faction.chunks.remove(getChunkLong(player.getWorld(), x >> 4, z >> 4));
                }
            }
            fchunk = new FactionChunk();
            fchunk.x = x >> 4;
            fchunk.z = z >> 4;
            fchunk.worldName = player.getWorld().getName();
            fchunk.faction = fp.faction;
            fchunk.mrb = 500;               // DEFAULT VALUES
            fchunk.mru = 250;
            
            fchunk.faction = fp.faction;
            
            fp.faction.chunks.put(getChunkLong(player.getWorld(), x >> 4, z >> 4), fchunk);
            getServer().broadcastMessage(String.format("§7[f] %s of faction %s claimed land", fp.name, fp.faction.name));
            return true;
        }
        
        if (cmd.equals("setrank")) {
            FactionPlayer           fp;
            FactionPlayer           mp;
            int                     nrank;
            
            if (args.length < 2) {
                player.sendMessage("§7[f] Not enough arguments /f rank <player> <rank>");
                return true;
            }
            
            fp = getFactionPlayer(args[1]);
            mp = getFactionPlayer(player.getName());
            
            if (fp == null) {
                player.sendMessage("§7[f] Player is not in a faction.");
                return true;
            }
            
            if (mp == null) {
                player.sendMessage("§7[f] You are not in a faction.");
                return true;
            }
            
            if (fp.faction != mp.faction) {
                player.sendMessage("§7[f] You and player are not in the same faction.");
            }
            
            if ((fp.rank >= mp.rank) && (!player.isOp())) {
                player.sendMessage("§7[f] Player is already at equal or greater rank than you.");
                return true;
            }
            
            nrank = Integer.valueOf(args[2]);
            
            if ((nrank >= mp.rank) && (!player.isOp())) {
                player.sendMessage("§7[f] Rank you wish to set is equal or greater than you rank. [rejected]");
                return true;
            }
            
            fp.rank = nrank;
            player.sendMessage(String.format("§7[f] rank for %s is now %d", args[1], nrank));
            return true;
        }
        
        if (cmd.equals("unclaim")) {
            FactionPlayer               fp;
            FactionChunk                fchunk;
            int                         x, z;
            
            fp = getFactionPlayer(player.getName());

            if (fp == null) {
                player.sendMessage("§7[f] You are not in a faction.");
                return true;
            }            

            // IS OUR RANK GOOD ENOUGH?
            if (fp.rank < fp.faction.mrc) {
                player.sendMessage(String.format("§7[f] Your rank of %d is below the required rank of %d to claim/unclaim.", fp.rank, fp.faction.mrc));
                return true;
            }
            
            x = player.getLocation().getBlockX() >> 4;
            z = player.getLocation().getBlockZ() >> 4;
            fchunk = getFactionChunk(player.getWorld(), x, z);
            
            if (fchunk == null) {
                player.sendMessage("§7[f] This land chunk is owned by no one.");
                return true;
            }
            
            if (fchunk.faction != fp.faction) {
                player.sendMessage(String.format("§7[f] Your faction is %s, but this land belongs to %s.", fp.faction.name, fchunk.faction.name));
                return true;
            }
            
            fp.faction.chunks.remove(getChunkLong(player.getWorld(), x, z));
            
            // UPDATE FACTION POWER
            getFactionPower(fp.faction);
            
            getServer().broadcastMessage(String.format("§7[f] %s of faction %s unclaimed land", fp.name, fp.faction.name));
            return true;
        }
        
        if (cmd.equals("unclaimall")) {
            FactionPlayer                       fp;
            Iterator<Entry<Long, FactionChunk>> i;
            
            fp = getFactionPlayer(player.getName());
            
            if (fp == null) {
                player.sendMessage("§7[f] You are not in a faction.");
                return true;
            }
            
            // IS OUR RANK GOOD ENOUGH?
            if (fp.rank < fp.faction.mrc) {
                player.sendMessage(String.format("§7[f] Your rank of %d is below the required rank of %d to claim/unclaim.", fp.rank, fp.faction.mrc));
                return true;
            }
            
            fp.faction.chunks = new HashMap<Long, FactionChunk>();
            
            getServer().broadcastMessage(String.format("§7[f] %s of faction %s declaimed all land", fp.name, fp.faction.name));
            return true;
        }
        
        if (cmd.equals("chkcharge")) {
            int                         icnt;
            int                         mid;
            byte                        dat;
            double                      pts;
            
            icnt = player.getItemInHand().getAmount();
            mid = player.getItemInHand().getTypeId();
            dat = player.getItemInHand().getData().getData();
            
            pts = (double)getEMC(mid, dat);
            
            player.sendMessage(String.format("§7[f] %f/item total:%f", pts, pts * icnt));
            
            
            
            return true;
        } 
        
        if (cmd.equals("charge")) {
            FactionPlayer               fp;
            int                         icnt;
            double                      pts;
            int                         mid;
            byte                        dat;
                    
            fp = getFactionPlayer(player.getName());
            if (fp == null) {
                player.sendMessage("§7[f] You are not in a faction.");
                return true;
            }
            
            icnt = player.getItemInHand().getAmount();
            mid = player.getItemInHand().getTypeId();
            dat = player.getItemInHand().getData().getData();
            
            //pts = (double)EEMaps.getEMC(mid, dat);
            pts = (double)getEMC(mid, dat);       
            
            player.sendMessage(String.format("§7[f] Item value is %f", pts));
            
            pts = pts * icnt;
            
            if (pts == 0.0d) {
                player.sendMessage("§7[f] Item(s) in hand yield no charge value.");
                return true;
            }
            
            player.setItemInHand(null);
            
            synchronized (fp.faction) {
                fp.faction.power += pts;
            }
            
            //getServer().broadcastMessage(String.format("§7[f] %s in %s charged faction power!", player.getDisplayName(), fp.faction.name));
            sendFactionMessage(fp.faction, String.format("§7[f] %s charged the faction power by %f to %f.", player.getDisplayName(), pts, getFactionPower(fp.faction)));
            return true;
        }
        
        // SHOW FACTION INFORMATION ABOUT OUR FACTION
        // OR ANOTHER FACTION SPECIFIED
        if (cmd.equalsIgnoreCase("who")) {
            FactionPlayer                           fp;
            Faction                                 f;
            Iterator<Entry<String, FactionPlayer>>  iter;
            Entry<String, FactionPlayer>            e;
            String                                  o;
            
            fp = getFactionPlayer(player.getName());
            
            if (args.length < 2 && fp == null) {
                player.sendMessage("§7[f] You are not in a faction, and you did not specify a faction (optional).");
                return true;
            }
            
            if (args.length == 2) {
                f = getFactionByName(args[1]);
                if (f == null) {
                    fp = getFactionPlayer(args[1]);
                    if (fp == null) {
                        player.sendMessage(String.format("§7[f] The player %s is not in a faction.", args[1]));
                        return true;
                    }
                    f = fp.faction;
                }
            } else {
                f = fp.faction;
            }
            
            if (f == null) {
                player.sendMessage(String.format("§7[f] The faction %s could not be found.", args[1]));
                return true;
            }
            
            // display name and description
            player.sendMessage(String.format("§6Faction: §7%s", f.name));
            //player.sendMessage(String.format("Description: %s", f.desc));
            // display land/power/maxpower
            if (fp != null) {
                player.sendMessage(String.format("§6Land: §7%d §6Power/Hour: §7%d §6Power: §7%d", 
                    f.chunks.size(), 
                    (int)(landPowerCostPerHour * f.chunks.size()), 
                    (int)getFactionPower(f)
                ));
                player.sendMessage(String.format("§6TimeLeft: §7%d/hr", 
                     (int)(getFactionPower(f) / (landPowerCostPerHour * f.chunks.size()))
                ));
            } else {
                player.sendMessage(String.format("§6Land: §7%d",
                        f.chunks.size()
                ));                
            }    
            
            // mri, mrc, flags, mrz, mrtp, mrsh
            player.sendMessage(String.format(
                "§6MRI:§7%d §6MRC:§7%d §6FLAGS:§7%d §6MRZ:§7%d §6MRTP:§7%d §6MRSH:§7%d", 
                f.mri, f.mrc, f.flags, (int)f.mrz, (int)f.mrtp, f.mrsh
            ));
            
            iter = f.players.entrySet().iterator();
            
            o = "§6Members:§r"; 
            while (iter.hasNext()) {
                e = iter.next();
                if (getServer().getPlayer(e.getKey()) != null) {
                    o = String.format("%s, §6%s§7[%d]", o, e.getKey(), e.getValue().rank);
                } else {
                    o = String.format("%s, §7%s§7[%d]", o, e.getKey(), e.getValue().rank);
                }
            }
            player.sendMessage(o);
            return true;
        }
        
        if (cmd.equals("inspect")) {
            Location            loc;
            FactionChunk        fc;
            int                 bx, bz;
            
            loc = player.getLocation();
            
            bx = loc.getBlockX() >> 4;
            bz = loc.getBlockZ() >> 4;
            
            fc = getFactionChunk(player.getWorld(), bx, bz);
            
            if (fc == null) {
                player.sendMessage("§7[f] §7Land not claimed by anyone.");
                return true;
            }
            
            player.sendMessage(String.format("§6Faction:§r%s §6MinimumBuildRank:§r%d §6MinimumUseRank:§r%d", fc.faction.name, fc.mrb, fc.mru));
            return true;
        }
        displayHelp(player);
        return false;
    }
    
    protected void smsg(String msg) {
        this.getLogger().info(msg);
    }
}
