package me.SuperRonanCraft.BetterRTP.player.commands.types;

import me.SuperRonanCraft.BetterRTP.player.commands.RTPCommand;
import me.SuperRonanCraft.BetterRTP.references.PermissionNode;
import me.SuperRonanCraft.BetterRTP.references.rtpinfo.RandomLocation;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CmdDeveloper implements RTPCommand {

    public String getName() {
        return "dev";
    }

    public void execute(CommandSender sendi, String label, String[] args) {
        World world = sendi instanceof Player ? ((Player) sendi).getWorld() : Bukkit.getWorld("world");
        RandomLocation.runChunkTest(world);
    }

    @Override public List<String> tabComplete(CommandSender sendi, String[] args) {
        return null;
    }

    @NotNull public PermissionNode permission() {
        return PermissionNode.DEVELOPER;
        //sendi.getName().equalsIgnoreCase("SuperRonanCraft") || sendi.getName().equalsIgnoreCase("RonanCrafts");
    }

    public void usage(CommandSender sendi, String label) {
        sendi.sendMessage("This is for Developement use only!");
    }
}
