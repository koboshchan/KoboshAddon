package com.kobosh.wurstaddon.client.hack;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.util.ChatUtils;

@SearchTags({"example", "items", "writable_book"})
public final class ExampleHack extends Hack {

    // Net-added items in ../Wurst7-1.21.1 from
    // 9459157791e06b93730e68a11fe37e31daea3133..HEAD (inclusive).
        private static final String[] NET_ADDED_ITEMS = {
            "minecraft:writable_book"
    };

    public ExampleHack() {
        super("ExampleHack");
        setCategory(Category.FUN);
    }

    @Override
    protected void onEnable() {
        ChatUtils.message("Net-added items in range: " + NET_ADDED_ITEMS.length);

        for (String itemId : NET_ADDED_ITEMS) {
            ChatUtils.message("- " + itemId);
        }

        setEnabled(false);
    }

    @Override
    protected void onDisable() {
    }
}
