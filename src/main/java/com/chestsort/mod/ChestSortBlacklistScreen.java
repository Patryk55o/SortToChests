package com.chestsort.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Component;
import net.minecraft.util.Formatting;
import net.minecraft.util.ResourceLocation;

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
        this.addDrawableChild(ButtonWidget.builder(Component.translatable("gui.done"), button -> this.close())
                .dimensions(this.width / 2 - 100, this.height - 40, 200, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredComponentWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
        context.drawCenteredComponentWithShadow(this.textRenderer, Component.translatable("screen.chestsort.blacklist.hint"), this.width / 2, 35, 0xAAAAAA);

        int gridWidth = COLUMNS * SLOT_SIZE;
        int gridStartX = (this.width - gridWidth) / 2;
        int gridStartY = this.height / 2 - (INV_ROWS * SLOT_SIZE) / 2 + 10;

        Inventory inv = Minecraft.getInstance().player.getInventory();
        for (int i = 0; i < 36; i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = gridStartX + col * SLOT_SIZE;
            int y = gridStartY + row * SLOT_SIZE;

            context.drawGuiComponenture(RenderPipelines.GUI_TEXTURED, ResourceLocation.of("container/slot"), x, y, 18, 18);

            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty()) {
                context.drawItem(stack, x + 1, y + 1);
                context.drawStackOverlay(this.textRenderer, stack, x + 1, y + 1);

                if (blacklist.isBlacklisted(stack.getItem())) {
                    context.fill(x + 1, y + 1, x + 17, y + 17, 0x80FF0000);
                }
            }
        }

        for (int i = 0; i < 36; i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = gridStartX + col * SLOT_SIZE;
            int y = gridStartY + row * SLOT_SIZE;

            if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                ItemStack stack = inv.getStack(i);
                if (!stack.isEmpty()) {
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(stack.getName());
                    if (blacklist.isBlacklisted(stack.getItem())) {
                        tooltip.add(Component.translatable("screen.chestsort.blacklisted").formatted(Formatting.RED));
                    } else {
                        tooltip.add(Component.translatable("screen.chestsort.click_to_blacklist").formatted(Formatting.GRAY));
                    }
                    context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean isUsed) {
        if (super.mouseClicked(click, isUsed)) {
            return true;
        }

        double mouseX = click.x();
        double mouseY = click.y();

        int gridWidth = COLUMNS * SLOT_SIZE;
        int startX = (this.width - gridWidth) / 2;
        int startY = this.height / 2 - (INV_ROWS * SLOT_SIZE) / 2 + 10;

        for (int i = 0; i < 36; i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = startX + col * SLOT_SIZE;
            int y = startY + row * SLOT_SIZE;

            if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                Inventory inv = Minecraft.getInstance().player.getInventory();
                ItemStack stack = inv.getStack(i);
                if (!stack.isEmpty()) {
                    blacklist.toggle(stack.getItem());
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
