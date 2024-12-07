/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.commands.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.arguments.FriendArgumentType;
import meteordevelopment.meteorclient.commands.arguments.PlayerListEntryArgumentType;
import meteordevelopment.meteorclient.systems.friends.Friend;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.friends.Friend.FriendType;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;
import net.minecraft.util.Formatting;

public class EnemyCommand extends Command {
    public EnemyCommand() {
        super("enemy", "Manages enemies.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("add")
            .then(argument("player", PlayerListEntryArgumentType.create())
                .executes(context -> {
                    GameProfile profile = PlayerListEntryArgumentType.get(context).getProfile();
                    Friend friend = new Friend(profile.getName(), profile.getId(), FriendType.Enemy);

                    if (Friends.get().add(friend)) {
                        ChatUtils.sendMsg(friend.hashCode(), Formatting.GRAY, "Added (highlight)%s (default)to enemies.".formatted(friend.getName()));
                    }
                    else error("Already enemies with that player.");

                    return SINGLE_SUCCESS;
                })
            )
        );

        builder.then(literal("remove")
            .then(argument("friend", FriendArgumentType.create())
                .executes(context -> {
                    Friend friend = FriendArgumentType.get(context);
                    if (friend == null) {
                        error("Not friends with that enemy.");
                        return SINGLE_SUCCESS;
                    }

                    if (Friends.get().remove(friend)) {
                        ChatUtils.sendMsg(friend.hashCode(), Formatting.GRAY, "Removed (highlight)%s (default)from enemies.".formatted(friend.getName()));
                    }
                    else error("Failed to remove that enemy.");

                    return SINGLE_SUCCESS;
                })
            )
        );

        builder.then(literal("list").executes(context -> {
                info("--- Enemies ((highlight)%s(default)) ---", Friends.get().count());
                Friends.get().friendStream().forEach(friend -> ChatUtils.info("(highlight)%s".formatted(friend.getName())));
                return SINGLE_SUCCESS;
            })
        );
    }
}
