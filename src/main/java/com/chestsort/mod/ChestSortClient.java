package com.chestsort.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyMappingHelper;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ingame.AbstractContainerScreen;
import net.minecraft.client.option.KeyMapping;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.Player;
import net.minecraft.entity.player.Inventory;
import net.minecraft.screen.AbstractContainerMenu;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.ClickType;
import net.minecraft.text.Component;
import net.minecraft.util.InteractionHand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3;
import net.minecraft.world.Level;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ChestSortClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("chestsort");
    public static KeyMapping blacklistKeyMapping;

    private static final Random RANDOM = new Random();

    // States
    private enum SortState { IDLE, OPENING, DUMPING, CLOSING, CHECKING }

    private static List<BlockPos> pendingChests = new ArrayList<>();
    private static int sortTickDelay = 0;
    private static SortState state = SortState.IDLE;

    // 15-25 ticks (0.75-1.25s) between chests
    private static int randomDelay() {
        return 15 + RANDOM.nextInt(10);
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("ChestSort client initialized");

        blacklistKeyMapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.chestsort.blacklist",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_X,
                KeyMapping.Category.create(net.minecraft.util.ResourceLocation.of("chestsort", "general"))
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (blacklistKeyMapping.wasPressed()) {
                client.execute(() ->
                    client.setScreen(new ChestSortBlacklistScreen(client.currentScreen))
                );
            }

            if (state == SortState.IDLE) return;

            if (sortTickDelay > 0) {
                sortTickDelay--;
                return;
            }

            switch (state) {
                case OPENING -> {
                    // Waiting for chest screen to appear
                    if (client.currentScreen instanceof AbstractContainerScreen<?>) {
                        // Screen is open — dump items
                        dumpInventoryToContainer(client);
                        state = SortState.CLOSING;
                        sortTickDelay = 3; // short wait before closing
                    } else {
                        // Chest didn't open, skip
                        LOGGER.info("Chest didn't open, skipping");
                        state = SortState.CHECKING;
                        sortTickDelay = 0;
                    }
                }
                case CLOSING -> {
                    // Close the chest
                    if (client.player != null) client.player.closeAbstractContainerScreen();
                    state = SortState.CHECKING;
                    sortTickDelay = 5; // wait 5 ticks for server to confirm inventory state
                }
                case CHECKING -> {
                    // Now the inventory is settled — check if we still have items
                    if (!hasNonBlacklistedItems(client)) {
                        state = SortState.IDLE;
                        pendingChests.clear();
                        if (client.player != null)
                            client.player.sendMessage(Component.literal("§aChestSort: Inventory empty, done!"), true);
                        return;
                    }
                    // Still has items — open next chest
                    if (!pendingChests.isEmpty()) {
                        BlockPos next = pendingChests.remove(0);
                        openChest(client, next);
                        state = SortState.OPENING;
                        sortTickDelay = randomDelay();
                    } else {
                        state = SortState.IDLE;
                        if (client.player != null)
                            client.player.sendMessage(Component.literal("§eChestSort: No more chests nearby, done!"), true);
                    }
                }
                default -> {}
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("cchestsort")
                    .executes(context -> {
                        Minecraft client = Minecraft.getInstance();
                        client.execute(() -> startNearbySorting(client));
                        return 1;
                    }));
        });
    }

    private static void startNearbySorting(Minecraft client) {
        if (client.player == null || client.world == null) return;

        if (state != SortState.IDLE) {
            client.player.sendMessage(Component.literal("§eChestSort: Already sorting, please wait."), true);
            return;
        }

        if (!hasNonBlacklistedItems(client)) {
            client.player.sendMessage(Component.literal("§eChestSort: Nothing to sort!"), true);
            return;
        }

        List<BlockPos> chests = findNearbyChests(client, 5);
        if (chests.isEmpty()) {
            client.player.sendMessage(Component.literal("§eChestSort: No chests found nearby."), true);
            return;
        }

        client.player.sendMessage(Component.literal("§aChestSort: Found " + chests.size() + " chest(s), sorting..."), true);

        pendingChests = new ArrayList<>(chests);

        // Open first chest immediately
        BlockPos first = pendingChests.remove(0);
        openChest(client, first);
        state = SortState.OPENING;
        sortTickDelay = 10; // wait for screen to load
    }

    private static List<BlockPos> findNearbyChests(Minecraft client, int radius) {
        List<BlockPos> found = new ArrayList<>();
        Level world = client.world;
        BlockPos playerPos = client.player.getBlockPos();

        for (BlockPos pos : BlockPos.iterate(
                playerPos.add(-radius, -radius, -radius),
                playerPos.add(radius, radius, radius))) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof ChestBlockEntity) {
                found.add(pos.toImmutable());
            }
        }
        return found;
    }

    private static void openChest(Minecraft client, BlockPos pos) {
        if (client.player == null || client.interactionManager == null) return;
        Vec3 hitVec = Vec3.ofCenter(pos);
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, pos, false);
        client.interactionManager.interactBlock(client.player, InteractionHand.MAIN_HAND, hitResult);
    }

    private static boolean hasNonBlacklistedItems(Minecraft client) {
        if (client.player == null) return false;
        BlacklistManager blacklist = BlacklistManager.getInstance();
        Inventory inv = client.player.getInventory();
        for (int i = 0; i <= 35; i++) {
            var stack = inv.getStack(i);
            if (!stack.isEmpty() && !blacklist.isBlacklisted(stack.getItem())) {
                return true;
            }
        }
        return false;
    }

    public static void dumpInventoryToContainer(Minecraft client) {
        if (client.player == null || client.interactionManager == null) return;

        AbstractContainerMenu handler = client.player.currentAbstractContainerMenu;
        Player player = client.player;
        BlacklistManager blacklist = BlacklistManager.getInstance();

        for (Slot slot : handler.slots) {
            if (slot.inventory instanceof Inventory) {
                int index = slot.getIndex();
                if (index >= 0 && index <= 35 && slot.hasStack()) {
                    if (blacklist.isBlacklisted(slot.getStack().getItem())) continue;
                    client.interactionManager.clickSlot(
                            handler.syncId,
                            slot.id,
                            0,
                            ClickType.QUICK_MOVE,
                            player
                    );
                }
            }
        }
    }
}
