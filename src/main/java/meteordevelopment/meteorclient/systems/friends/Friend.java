/*
 * This file is part of the Meteor Client distribution
 * (https://github.com/MeteorDevelopment/meteor-client). Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.friends;

import com.mojang.util.UndashedUuid;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import meteordevelopment.meteorclient.utils.network.Http;
import meteordevelopment.meteorclient.utils.render.PlayerHeadTexture;
import meteordevelopment.meteorclient.utils.render.PlayerHeadUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

public class Friend implements ISerializable<Friend>, Comparable<Friend> {
    public volatile String name;
    private volatile @Nullable UUID id;
    private volatile @Nullable PlayerHeadTexture headTexture;
    private volatile boolean updating;
    private volatile FriendType type = FriendType.Friend;

    public Friend(String name, @Nullable UUID id, FriendType type) {
        this.name = name;
        this.id = id;
        this.headTexture = null;
        this.type = type;
    }

    public Friend(PlayerEntity player, FriendType type) {
        this(player.getName().getString(), player.getUuid(), type);
    }

    public Friend(String name, FriendType type) {
        this(name, null, type);
    }

    public String getName() {
        return name;
    }

    public PlayerHeadTexture getHead() {
        return headTexture != null ? headTexture : PlayerHeadUtils.STEVE_HEAD;
    }

    public void updateInfo() {
        updating = true;
        APIResponse res = Http.get("https://api.mojang.com/users/profiles/minecraft/" + name)
                .sendJson(APIResponse.class);
        if (res == null || res.name == null || res.id == null)
            return;
        name = res.name;
        id = UndashedUuid.fromStringLenient(res.id);
        headTexture = PlayerHeadUtils.fetchHead(id);
        updating = false;
    }

    public boolean headTextureNeedsUpdate() {
        return !this.updating && headTexture == null;
    }

    public FriendType getFriendType() {
        return type;
    }

    public void setfFriendType(FriendType type) {
        this.type = type;
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();

        tag.putString("name", name);

        if (id != null)
            tag.putString("id", UndashedUuid.toString(id));

        switch (type) {
            case Friend:
                tag.putString("friendType", "Friend");
                break;
            case Enemy:
                tag.putString("friendType", "Enemy");
                break;
        }

        return tag;
    }

    @Override
    public Friend fromTag(NbtCompound tag) {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Friend friend = (Friend) o;
        return Objects.equals(name, friend.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public int compareTo(@NotNull Friend friend) {
        return name.compareTo(friend.name);
    }

    private static class APIResponse {
        String name, id;
    }

    public enum FriendType {
        Friend, Enemy
    }
}
