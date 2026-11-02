package com.chestsort.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.ResourceLocation;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class BlacklistManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Set<String>>() {}.getType();
    private static final BlacklistManager INSTANCE = new BlacklistManager();

    private final Set<ResourceLocation> blacklistedItems = new HashSet<>();
    private boolean loaded = false;

    public static BlacklistManager getInstance() {
        INSTANCE.load();
        return INSTANCE;
    }

    private Path getFile() {
        return Minecraft.getInstance().runDirectory.toPath()
                .resolve("config").resolve("chestsort-blacklist.json");
    }

    public void load() {
        if (loaded) return;
        File file = getFile().toFile();
        if (file.exists()) {
            try (Reader reader = new FileReader(file)) {
                Set<String> ids = GSON.fromJson(reader, TYPE);
                if (ids != null) {
                    for (String id : ids) {
                        ResourceLocation parsed = ResourceLocation.tryParse(id);
                        if (parsed != null) {
                            blacklistedItems.add(parsed);
                        }
                    }
                }
            } catch (IOException e) {
                ChestSortClient.LOGGER.error("Failed to load blacklist", e);
            }
        }
        loaded = true;
    }

    public void save() {
        try {
            File file = getFile().toFile();
            file.getParentFile().mkdirs();
            Set<String> ids = new HashSet<>();
            for (ResourceLocation id : blacklistedItems) {
                ids.add(id.toString());
            }
            try (Writer writer = new FileWriter(file)) {
                GSON.toJson(ids, TYPE, writer);
            }
        } catch (IOException e) {
            ChestSortClient.LOGGER.error("Failed to save blacklist", e);
        }
    }

    public boolean isBlacklisted(Item item) {
        return blacklistedItems.contains(Registries.ITEM.getId(item));
    }

    public void toggle(Item item) {
        ResourceLocation id = Registries.ITEM.getId(item);
        if (blacklistedItems.contains(id)) {
            blacklistedItems.remove(id);
        } else {
            blacklistedItems.add(id);
        }
        save();
    }

    public Set<ResourceLocation> getBlacklisted() {
        return new HashSet<>(blacklistedItems);
    }
}
