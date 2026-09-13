package me.SuperRonanCraft.BetterRTP.player.rtp.effects;

import me.SuperRonanCraft.BetterRTP.BetterRTP;
import me.SuperRonanCraft.BetterRTP.references.file.FileOther;
import me.SuperRonanCraft.BetterRTP.versions.AsyncHandler;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

//---
//Particles are spawned using the native Bukkit/Paper API.
//ParticleLib (xyz.xenondevs.particle) is no longer used as it stopped working on 1.19.4+.
//Find a list of supported particles with '/rtp info particles'.
//---

public class RTPEffect_Particles {

    private boolean enabled;
    private final List<Particle> effects = new ArrayList<>();
    private String shape;
    private final int precision = 16;
    private final Set<Particle> failedParticles = new HashSet<>();

    //Some particles act very differently and might not care how they are shaped before animating, ex: EXPLOSION
    public static String[] shapeTypes = {
            "SCAN", //Body scan
            "EXPLODE", //Make an explosive entrance
            "TELEPORT" //Startrek type of portal
            };

    void load() {
        FileOther.FILETYPE config = getPl().getFiles().getType(FileOther.FILETYPE.EFFECTS);
        enabled = config.getBoolean("Particles.Enabled");
        effects.clear();
        failedParticles.clear();
        if (!enabled) return;
        //Enabled? Load all this junk
        List<String> types;
        if (config.isList("Particles.Type"))
            types = config.getStringList("Particles.Type");
        else {
            types = new ArrayList<>();
            types.add(config.getString("Particles.Type"));
        }
        for (String type : types) {
            Particle particle = getParticle(type);
            if (particle == null) {
                effects.clear();
                effects.add(Particle.EXPLOSION);
                getPl().getLogger().severe("The particle '" + type + "' doesn't exist! Default particle enabled... " +
                        "Try using '/rtp info particles' to get a list of available particles");
                break;
            }
            effects.add(particle);
        }
        shape = config.getString("Particles.Shape").toUpperCase();
        if (!Arrays.asList(shapeTypes).contains(shape)) {
            getPl().getLogger().severe("The particle shape '" + shape + "' doesn't exist! Default particle shape enabled...");
            getPl().getLogger().severe("Try using '/rtp info shapes' to get a list of shapes, or: " + Arrays.asList(shapeTypes));
            shape = shapeTypes[0];
        }
    }

    public void display(Player p) {
        if (!enabled) return;
        AsyncHandler.sync(() -> {
            try {
                switch (shape) {
                    case "TELEPORT":
                        partTeleport(p);
                        break;
                    case "EXPLODE":
                        partExplosion(p);
                        break;
                    default: //Super redundant, but... just future proofing
                    case "SCAN":
                        partScan(p);
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void partScan(Player p) { //Particles with negative velocity
        Location loc = p.getLocation().add(new Vector(0, 1.75, 0));
        for (int index = 1; index < precision; index++) {
            Vector vec = getVecCircle(index);
            spawn(p, loc.clone().add(vec), new Vector(0, -0.125, 0), .15f);
        }
    }

    private void partTeleport(Player p) { //Static particles in a shape
        Location loc = p.getLocation();
        for (float y = 2.5f; y > 0; y -= .25f)
            for (int index = 1; index < precision; index++) {
                Vector vec = getVecCircle(index).add(new Vector(0, y, 0));
                spawn(p, loc.clone().add(vec), new Vector(0, 0, 0), 0f);
            }
    }

    private void partExplosion(Player p) { //Particles with a shape and forward velocity
        Location loc = p.getLocation().add(new Vector(0, 1, 0));
        for (int index = 1; index < precision; index++) {
            Vector vec = getVecCircle(index);
            spawn(p, loc.clone().add(vec), vec, 1.5f);
        }
    }

    private void spawn(Player p, Location loc, Vector offset, float speed) {
        World world = loc.getWorld();
        if (world == null) return;
        for (Particle effect : effects) {
            try {
                world.spawnParticle(effect, loc, 1, offset.getX(), offset.getY(), offset.getZ(), speed);
            } catch (IllegalArgumentException e) {
                if (failedParticles.add(effect)) //Only log once per particle
                    getPl().getLogger().severe("The particle '" + effect.name() + "' couldn't be spawned (may require data on this server version)! " +
                            "Remove it from effects.yml Particles.Type and try '/rtp info particles'");
            }
        }
    }

    private Vector getVecCircle(int index) {
        double p1 = (index * Math.PI) / (precision / 2);
        double p2 = (index - 1) * Math.PI / (precision / 2);
        //Positions
        int radius = 3;
        double x1 = Math.cos(p1) * radius;
        double x2 = Math.cos(p2) * radius;
        double z1 = Math.sin(p1) * radius;
        double z2 = Math.sin(p2) * radius;
        return new Vector(x2 - x1, 0, z2 - z1);
    }

    private Particle getParticle(String type) {
        try {
            return Particle.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    private BetterRTP getPl() {
        return BetterRTP.getInstance();
    }
}