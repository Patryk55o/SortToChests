package com.chestsort.mod;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ChestSortClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("chestsort");
    public static KeyMapping blacklistKeyMapping;

    private static final Random RANDOM = new Random();

    private enum SortState { IDLE, OPENING, CLOSING, CHECKING }

    private static List<BlockPos> pendingChests = new ArrayList<>();
    private static int sortTickDelay = 0;
    private static SortState state = SortState.IDLE;

    private static int randomDelay() {
        return 15 + RANDOM.nextInt(10);
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("ChestSort client initialized");

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("chestsort", "general")
        );
        blacklistKeyMapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.chestsort.blacklist",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_X,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (blacklistKeyMapping.consumeClick()) {
                client.execute(() -> client.gui.setScreen(new ChestSortBlacklistScreen(client.gui.screen())));
            }

            if (state == SortState.IDLE) return;

            if (sortTickDelay > 0) {
                sortTickDelay--;
                return;
            }

            switch (state) {
                case OPENING -> {
                    if (client.gui.screen() instanceof AbstractContainerScreen<?>) {
                        dumpInventoryToContainer(client);
                        state = SortState.CLOSING;
                        sortTickDelay = 3;
                    } else {
                        LOGGER.info("Chest didn't open, skipping");
                        state = SortState.CHECKING;
                    }
                }
                case CLOSING -> {
                    if (client.player != null) client.player.closeContainer();
                    state = SortState.CHECKING;
                    sortTickDelay = 5;
                }
                case CHECKING -> {
                    if (!hasNonBlacklistedItems(client)) {
                        state = SortState.IDLE;
                        pendingChests.clear();
                        if (client.player != null) {
                            client.player.sendOverlayMessage(Component.literal("§aChestSort: Inventory empty, done!");
                        }
                        return;
                    }

                    if (!pendingChests.isEmpty()) {
                        BlockPos next = pendingChests.remove(0);
                        openChest(client, next);
                        state = SortState.OPENING;
                        sortTickDelay = randomDelay();
                    } else {
                        state = SortState.IDLE;
                        if (client.player != null) {
                            client.player.sendOverlayMessage(Component.literal("§eChestSort: No more chests nearby, done!");
                        }
                    }
                }
                default -> { }
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("cchestsort")
                        .executes(context -> {
                            Minecraft client = Minecraft.getInstance();
                            client.execute(() -> startNearbySorting(client));
                            return 1;
                        })));
    }

    private static void startNearbySorting(Minecraft client) {
        if (client.player == null || client.level == null) return;

        if (state != SortState.IDLE) {
            client.player.sendOverlayMessage(Component.literal("§eChestSort: Already sorting, please wait.");
            return;
        }

        if (!hasNonBlacklistedItems(client)) {
            client.player.sendOverlayMessage(Component.literal("§eChestSort: Nothing to sort!");
            return;
        }

        List<BlockPos> chests = findNearbyChests(client, 5);
        if (chests.isEmpty()) {
            client.player.sendOverlayMessage(Component.literal("§eChestSort: No chests found nearby.");
            return;
        }

        client.player.sendOverlayMessage(Component.literal("§aChestSort: Found " + chests.size() + " chest(s), sorting...");
        pendingChests = new ArrayList<>(chests);

        BlockPos first = pendingChests.remove(0);
        openChest(client, first);
        state = SortState.OPENING;
        sortTickDelay = 10;
    }

    private static List<BlockPos> findNearbyChests(Minecraft client, int radius) {
        List<BlockPos> found = new ArrayList<>();
        Level level = client.level;
        BlockPos playerPos = client.player.blockPosition();

        for (BlockPos pos : BlockPos.betweenClosed(
                playerPos.offset(-radius, -radius, -radius),
                playerPos.offset(radius, radius, radius))) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof ChestBlockEntity) {
                found.add(pos.immutable());
            }
        }
        return found;
    }

    private static void openChest(Minecraft client, BlockPos pos) {
        if (client.player == null || client.gameMode == null) return;
        Vec3 hitVec = Vec3.atCenterOf(pos);
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, pos, false);
        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hitResult);
    }

    private static boolean hasNonBlacklistedItems(Minecraft client) {
        if (client.player == null) return false;
        BlacklistManager blacklist = BlacklistManager.getInstance();
        Inventory inventory = client.player.getInventory();
        for (int i = 0; i <= 35; i++) {
            var stack = inventory.getItem(i);
            if (!stack.isEmpty() && !blacklist.isBlacklisted(stack.getItem())) {
                return true;
            }
        }
        return false;
    }

    public static void dumpInventoryToContainer(Minecraft client) {
        if (client.player == null || client.gameMode == null) return;

        AbstractContainerMenu menu = client.player.containerMenu;
        Player player = client.player;
        Inventory inventory = player.getInventory();
        BlacklistManager blacklist = BlacklistManager.getInstance();

        for (Slot slot : menu.slots) {
            if (slot.container == inventory) {
                int index = slot.getContainerSlot();
                if (index >= 0 && index <= 35 && slot.hasItem()) {
                    if (blacklist.isBlacklisted(slot.getItem().getItem())) continue;
                    client.gameMode.handleContainerInput(
                            menu.containerId,
                            slot.index,
                            0,
                            ContainerInput.QUICK_MOVE,
                            player
                    );
                }
            }
        }
    }
}
