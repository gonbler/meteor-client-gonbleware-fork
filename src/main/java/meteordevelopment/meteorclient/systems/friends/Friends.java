/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.friends;

import com.mojang.util.UndashedUuid;
import meteordevelopment.meteorclient.systems.System;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.systems.friends.Friend.FriendType;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class Friends extends System<Friends> {
    private final List<Friend> friends = new ArrayList<>();

    public Friends() {
        super("friends");
    }

    public static Friends get() {
        return Systems.get(Friends.class);
    }

    public boolean add(Friend friend) {
        if (friend.name.isEmpty() || friend.name.contains(" ")) return false;

        if (!friends.contains(friend)) {
            friends.add(friend);
            save();

            return true;
        } else {
            Friend friendListFriend = friends.get(friends.indexOf(friend));

            if (friendListFriend.getFriendType() != friend.getFriendType()) {
                friendListFriend.setfFriendType(friend.getFriendType());
                
                return true;
            }
        }

        return false;
    }

    public boolean remove(Friend friend) {
        if (friends.remove(friend)) {
            save();
            return true;
        }

        return false;
    }

    public Friend get(String name) {
        for (Friend friend : friends) {
            if (friend.name.equalsIgnoreCase(name)) {
                return friend;
            }
        }

        return null;
    }

    public Friend get(PlayerEntity player) {
        return get(player.getName().getString());
    }

    public Friend get(PlayerListEntry player) {
        return get(player.getProfile().getName());
    }

    public boolean isFriend(PlayerEntity player) {
        return player != null && get(player) != null && get(player).getFriendType() == FriendType.Friend;
    }

    public boolean isFriend(PlayerListEntry player) {
        return get(player) != null && get(player).getFriendType() == FriendType.Friend;
    }

    public boolean isEnemy(PlayerEntity player) {
        return player != null && get(player) != null && get(player).getFriendType() == FriendType.Enemy;
    }

    public boolean isEnemy(PlayerListEntry player) {
        return get(player) != null && get(player).getFriendType() == FriendType.Enemy;
    }

    public boolean shouldAttack(PlayerEntity player) {
        return !isFriend(player) || isEnemy(player);
    }

    public int count() {
        return friends.size();
    } 

    public boolean isEmpty() {
        return friends.isEmpty();
    }

    public @NotNull Stream<Friend> friendStream() {
        return friends.stream().filter(x -> x.getFriendType() == FriendType.Friend);
    }

    public @NotNull Stream<Friend> enemyStream() {
        return friends.stream().filter(x -> x.getFriendType() == FriendType.Enemy);
    }

    public @NotNull Stream<Friend> stream() {
        return friends.stream();
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();

        tag.put("friends", NbtUtils.listToTag(friends));

        return tag;
    }

    @Override
    public Friends fromTag(NbtCompound tag) {
        friends.clear();

        for (NbtElement itemTag : tag.getList("friends", 10)) {
            NbtCompound friendTag = (NbtCompound) itemTag;
            if (!friendTag.contains("name")) continue;

            String name = friendTag.getString("name");
            if (get(name) != null) continue;

            String s_friendType = friendTag.getString("friendType");
            FriendType type = FriendType.Friend;
            if (s_friendType != null) {
                if (s_friendType.equals("Friend")) {
                    type = FriendType.Friend;
                } else if (s_friendType.equals("Enemy")) {
                    type = FriendType.Enemy;
                }
            }
            
            String uuid = friendTag.getString("id");
            Friend friend = !uuid.isBlank()
                ? new Friend(name, UndashedUuid.fromStringLenient(uuid), type)
                : new Friend(name, type);

            friends.add(friend);
        }

        Collections.sort(friends);

        MeteorExecutor.execute(() -> friends.forEach(Friend::updateInfo));

        return this;
    }
}
