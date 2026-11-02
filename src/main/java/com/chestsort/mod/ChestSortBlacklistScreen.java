package com.chestsort.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ChestSortBlacklistScreen extends Screen {
    private final Screen parent;
    private final BlacklistManager blacklist;

    private static final int SLOT_SIZE = 18;
    private static final int COLUMNS = 9;
    private static final int INV_ROWS = 4;

    protected ChestSortBlacklistScreen(Screen parent) {
        super(Component.translatable("screen.chestsort.blacklist"));
        this.parent = parent;
        this.blacklist = BlacklistManager.getInstance();
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 40, 200, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.text(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF, true);
        graphics.text(this.font, Component.translatable("screen.chestsort.blacklist.hint"),
                this.width / 2, 35, 0xFFAAAAAA, true);

        int gridWidth = COLUMNS * SLOT_SIZE;
        int gridStartX = (this.width - gridWidth) / 2;
        int gridStartY = this.height / 2 - (INV_ROWS * SLOT_SIZE) / 2 + 10;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        Inventory inventory = client.player.getInventory();

        for (int i = 0; i < 36; i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = gridStartX + col * SLOT_SIZE;
            int y = gridStartY + row * SLOT_SIZE;

            graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF373737);
            graphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0xFF8B8B8B);

            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                graphics.item(stack, x + 1, y + 1);
                graphics.itemDecorations(this.font, stack, x + 1, y + 1);

                if (blacklist.isBlacklisted(stack.getItem())) {
                    graphics.fill(x + 1, y + 1, x + 17, y + 17, 0x80FF0000);
                }
            }

            if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE
                    && !stack.isEmpty()) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(stack.getHoverName());
                if (blacklist.isBlacklisted(stack.getItem())) {
                    tooltip.add(Component.translatable("screen.chestsort.blacklisted").withStyle(ChatFormatting.RED));
                } else {
                    tooltip.add(Component.translatable("screen.chestsort.click_to_blacklist").withStyle(ChatFormatting.GRAY));
                }
                graphics.setTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }

        if (event.button() != 1) {
            return false;
        }

        double mouseX = event.x();
        double mouseY = event.y();

        int gridWidth = COLUMNS * SLOT_SIZE;
        int startX = (this.width - gridWidth) / 2;
        int startY = this.height / 2 - (INV_ROWS * SLOT_SIZE) / 2 + 10;

        for (int i = 0; i < 36; i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = startX + col * SLOT_SIZE;
            int y = startY + row * SLOT_SIZE;

            if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                Minecraft client = Minecraft.getInstance();
                if (client.player == null) return false;
                Inventory inventory = client.player.getInventory();
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty()) {
                    blacklist.toggle(stack.getItem());
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
