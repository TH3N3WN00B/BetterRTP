package me.SuperRonanCraft.BetterRTP.references.messages;

import com.google.common.collect.ImmutableCollection;
import lombok.NonNull;
import me.SuperRonanCraft.BetterRTP.references.file.FileData;
import me.SuperRonanCraft.BetterRTP.references.messages.placeholder.PlaceholderAnalyzer;
import me.SuperRonanCraft.BetterRTP.versions.AsyncHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface Message {

    FileData lang();

    static void sms(Message messenger, CommandSender sendi, String msg) {
        if (!msg.isEmpty())
            AsyncHandler.sync(() ->
                    sendComponent(sendi, parseComponent(sendi, getPrefix(messenger) + msg)));
    }

    static void sms(Message messenger, CommandSender sendi, String msg, Object placeholderInfo) {
        if (!msg.isEmpty())
            AsyncHandler.sync(() ->
                    sendComponent(sendi, parseComponent(sendi, getPrefix(messenger) + msg, placeholderInfo)));
    }

    static void sms(Message messenger, CommandSender sendi, String msg, List<Object> placeholderInfo) {
        if (!msg.isEmpty())
            AsyncHandler.sync(() ->
                    sendComponent(sendi, parseComponent(sendi, getPrefix(messenger) + msg, placeholderInfo)));
    }

    static void sms(CommandSender sendi, List<String> msg, Object placeholderInfo) {
        if (msg != null && !msg.isEmpty()) {
            AsyncHandler.sync(() -> {
                List<String> copy = new ArrayList<>(msg);
                for (int i = 0; i < copy.size(); i++)
                    copy.set(i, placeholder(sendi, copy.get(i), placeholderInfo));
                for (String line : copy) {
                    if (line != null && !line.isEmpty())
                        sendComponent(sendi, toComponent(line));
                }
            });
        }
    }

    /*static void smsActionBar(Player sendi, List<String> msg) {
        if (msg == null || msg.isEmpty()) return;
        String str = msg.get(new Random().nextInt(msg.size()));
        smsActionBar(sendi, str);
    }

    static void smsActionBar(Player sendi, String msg) {
        if (msg == null || msg.isEmpty()) return;
        Audience audience = BetterRTP.getInstance().getAdventure().player(sendi);
        audience.sendActionBar(Component.text(msg));
        //sendi.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
    }*/

    /*static void smsTitle(Player sendi, List<String> msg) {
        if (msg == null || msg.isEmpty()) return;
        Audience audience = BetterRTP.getInstance().getAdventure().player(sendi);
        if (msg.size() == 1)
            audience.showTitle(Title.title(Component.text(" "), Component.text(msg.get(0))));
        else
            audience.showTitle(Title.title(Component.text(msg.get(0)), Component.text(msg.get(1))));
    }*/

    static String getPrefix(Message messenger) {
        return messenger.lang().getString("Messages.Prefix");
    }

    /**
     * @param info: Accepts String, PersistentDataContainer
     * **/
    static List<String> placeholder(@Nullable CommandSender p, List<String> str, Object info) {
        if (str instanceof ImmutableCollection)
            return str;
        for (int i = 0; i < str.size(); i++) {
            String s = placeholder(p, str.get(i), info);
            if (s != null)
                str.set(i, s);
            else {
                str.remove(i);
                i--;
            }
        }
        return str;
    }

    @Nullable
    static String placeholder(@Nullable CommandSender p, String str, @Nullable Object info) {
        if (info instanceof Collection<?>)
            str = placeholder(p, str, Collections.unmodifiableList((List<?>) info));
        else if (str != null)
            str = PlaceholderAnalyzer.applyPlaceholders(p, str, info);
        if (str != null)
            return color(str);
        return null;
    }

    static String placeholder(@Nullable CommandSender p, String str, @NonNull List<Object> info) {
        for (Object obj : info)
            str = placeholder(p, str, obj);
        return str;
    }

    static String placeholder(@Nullable CommandSender p, String str) {
        if (str != null)
            str = PlaceholderAnalyzer.applyPlaceholders(p, str, null);
        if (str != null)
            return color(str);
        return null;
    }

    /**
     * Turns any legacy string (after placeholders & colors were applied) into a parsed component.
     * Won't throw - falls back to the legacy (ampersand) parser on failure.
     */
    static Component toComponent(String legacyColoredStr) {
        if (legacyColoredStr == null || legacyColoredStr.isEmpty())
            return Component.empty();
        try {
            return MiniMessage.miniMessage().deserialize(convertLegacyToMini(legacyColoredStr));
        } catch (Throwable e) {
            try {
                return LegacyComponentSerializer.legacyAmpersand().deserialize(legacyColoredStr);
            } catch (Throwable ignored) {
                return Component.text(legacyColoredStr);
            }
        }
    }

    static Component parseComponent(@Nullable CommandSender p, String msg) {
        return toComponent(placeholder(p, msg));
    }

    static Component parseComponent(@Nullable CommandSender p, String msg, @Nullable Object placeholderInfo) {
        return toComponent(placeholder(p, msg, placeholderInfo));
    }

    static Component parseComponent(@Nullable CommandSender p, String msg, @NonNull List<Object> placeholderInfo) {
        return toComponent(placeholder(p, msg, placeholderInfo));
    }

    static void sendComponent(CommandSender sendi, Component component) {
        try {
            sendi.sendMessage(component);
        } catch (Throwable ignored) {
        }
    }

    static String color(String str) {
        return translateHexColorCodes(str);
    }

    //Converts the & codes used by legacy color() into MiniMessage tags so both formats can be mixed.
    static String convertLegacyToMini(String original) {
        String str = original;
        //Hex: &x&a&b&c&d&e&f (14 characters)
        Pattern hexPattern = Pattern.compile("&x(&[0-9a-fA-F]){6}");
        Matcher hm = hexPattern.matcher(str);
        while (hm.find()) {
            StringBuilder code = new StringBuilder();
            for (String part : hm.group(0).substring(2).split("&"))
                if (!part.isEmpty())
                    code.append(part.substring(0, 1));
            str = str.substring(0, hm.start()) + "<color:#" + code + ">" + str.substring(hm.end());
            hm = hexPattern.matcher(str);
        }
        //Formatting codes
        str = str.replace("&k", "<obfuscated>");
        str = str.replace("&l", "<bold>");
        str = str.replace("&m", "<strikethrough>");
        str = str.replace("&n", "<underlined>");
        str = str.replace("&o", "<italic>");
        while (str.contains("&&")) //Literal ampersand (already un-translated by translateAlternateColorCodes)
            str = str.replace("&&", "&amp;");
        //Color codes
        StringBuilder sb = new StringBuilder();
        Matcher cm = Pattern.compile("(&)([0-9a-fA-F])").matcher(str);
        int last = 0;
        while (cm.find()) {
            sb.append(str, last, cm.start()).append(legacyColorToName(cm.group(2).toLowerCase().charAt(0)));
            last = cm.end();
        }
        sb.append(str.substring(last));
        return sb.toString();
    }

    private static String legacyColorToName(char code) {
        switch (code) {
            case '0': return "<black>";
            case '1': return "<dark_blue>";
            case '2': return "<dark_green>";
            case '3': return "<dark_aqua>";
            case '4': return "<dark_red>";
            case '5': return "<dark_purple>";
            case '6': return "<gold>";
            case '7': return "<gray>";
            case '8': return "<dark_gray>";
            case '9': return "<blue>";
            case 'a': return "<green>";
            case 'b': return "<aqua>";
            case 'c': return "<red>";
            case 'd': return "<light_purple>";
            case 'e': return "<yellow>";
            case 'f': return "<white>";
            default: return "";
        }
    }

    //Thank you to zwrumpy on Spigot! (https://www.spigotmc.org/threads/hex-color-code-translate.449748/#post-4270781)
    //Supports 1.8 to 1.18
    static String translateHexColorCodes(String message) {
        Pattern pattern = Pattern.compile("#[a-fA-F0-9]{6}");
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            String hexCode = message.substring(matcher.start(), matcher.end());
            String replaceSharp = hexCode.replace('#', 'x');

            char[] ch = replaceSharp.toCharArray();
            StringBuilder builder = new StringBuilder("");
            for (char c : ch) {
                builder.append("&").append(c);
            }

            message = message.replace(hexCode, builder.toString());
            matcher = pattern.matcher(message);
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}