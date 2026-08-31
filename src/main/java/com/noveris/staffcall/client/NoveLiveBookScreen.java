package com.noveris.staffcall.client;

import com.noveris.staffcall.NoveLiveBookPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class NoveLiveBookScreen extends Screen {
    private static final int PAGE_SIZE = 8;
    private final NoveLiveBookPayload payload;
    private final List<NoveLiveBookPayload.Entry> allEntries;
    private List<NoveLiveBookPayload.Entry> filtered;
    private EditBox search;
    private Button previous;
    private Button next;
    private int page;

    NoveLiveBookScreen(NoveLiveBookPayload payload) {
        super(Component.literal("Livro das Almas"));
        this.payload = payload;
        this.allEntries = new ArrayList<>(payload.entries());
        this.filtered = new ArrayList<>(allEntries);
    }

    @Override
    protected void init() {
        int left = width / 2 - 150;
        search = addRenderableWidget(new EditBox(font, left + 25, 42, 250, 20,
                Component.literal("Pesquisar por nome")));
        search.setHint(Component.literal("Pesquisar uma alma..."));
        search.setResponder(this::filter);
        previous = addRenderableWidget(Button.builder(Component.literal("‹ Página anterior"), button -> {
            if (page > 0) page--;
            updateButtons();
        }).bounds(left + 25, height - 50, 120, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal("Próxima página ›"), button -> {
            if ((page + 1) * PAGE_SIZE < filtered.size()) page++;
            updateButtons();
        }).bounds(left + 155, height - 50, 120, 20).build());
        updateButtons();
    }

    private void filter(String query) {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        filtered = allEntries.stream().filter(value -> value.name().toLowerCase(Locale.ROOT).contains(normalized)).toList();
        page = 0;
        updateButtons();
    }

    private void updateButtons() {
        if (previous != null) previous.active = page > 0;
        if (next != null) next.active = (page + 1) * PAGE_SIZE < filtered.size();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = width / 2 - 150;
        int right = width / 2 + 150;
        graphics.fill(left, 18, right, height - 18, 0xEE2A1B13);
        graphics.fill(left + 5, 23, right - 5, height - 23, 0xFFE1C48A);
        graphics.fill(width / 2 - 1, 68, width / 2 + 1, height - 62, 0x553A2417);
        graphics.drawCenteredString(font, title, width / 2, 27, 0x4A2412);
        graphics.drawCenteredString(font, payload.canonicalMode() ? "O Véu está enfraquecido" : "O Véu permanece fechado",
                width / 2, 66, payload.canonicalMode() ? 0x8A1010 : 0x513A63);

        int start = page * PAGE_SIZE;
        int end = Math.min(filtered.size(), start + PAGE_SIZE);
        for (int index = start; index < end; index++) {
            NoveLiveBookPayload.Entry entry = filtered.get(index);
            int local = index - start;
            int column = local % 2;
            int row = local / 2;
            int x = left + 20 + column * 148;
            int y = 88 + row * 55;
            graphics.drawString(font, entry.name(), x, y, entry.fragments() == 0 ? 0x6B2020 : 0x3B2012, false);
            graphics.drawString(font, symbols(entry.fragments()), x, y + 14, entry.fragments() <= 1 ? 0x9B1515 : 0x6A3C18, false);
            graphics.drawString(font, entry.state(), x, y + 28, 0x554130, false);
            if (entry.marked() && payload.viewAll()) graphics.drawString(font, "⚠ Exposta", x + 78, y, 0x9B1515, false);
        }
        int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        graphics.drawCenteredString(font, "Página " + (page + 1) + "/" + pages, width / 2, height - 64, 0x4A2412);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private String symbols(int fragments) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < 4; index++) {
            if (index > 0) text.append(' ');
            text.append(index < fragments ? '◆' : '◇');
        }
        return text.toString();
    }
}
