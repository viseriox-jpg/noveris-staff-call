package com.noveris.staffcall.client;

import com.noveris.staffcall.NoveLiveAdminActionPayload;
import com.noveris.staffcall.NoveLiveAdminData;
import com.noveris.staffcall.NoveLiveAdminPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

final class NoveLiveAdminScreen extends Screen {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM • HH:mm")
            .withZone(ZoneId.systemDefault());
    private NoveLiveAdminData data;
    private boolean soulsTab;
    private int rupturePage;
    private int soulPage;
    private long selectedRupture = -1;
    private String selectedSoul = "";

    NoveLiveAdminScreen(NoveLiveAdminPayload payload) {
        super(Component.literal("Tribunal das Almas"));
        update(payload);
    }

    void update(NoveLiveAdminPayload payload) {
        data = payload.data();
        if (!data.selectedPlayerId().isBlank()) {
            selectedSoul = data.selectedPlayerId();
            soulsTab = true;
        }
        if (selectedRupture < 0 && !data.ruptures().isEmpty()) selectedRupture = data.ruptures().getFirst().id();
        if (!data.ruptures().isEmpty() && data.ruptures().stream().noneMatch(value -> value.id() == selectedRupture))
            selectedRupture = data.ruptures().getFirst().id();
        if (data.ruptures().isEmpty()) selectedRupture = -1;
        if (!selectedSoul.isBlank() && data.souls().stream().noneMatch(value -> value.id().equals(selectedSoul))) selectedSoul = "";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int panelWidth = Math.min(610, width - 24);
        int panelHeight = Math.min(330, height - 20);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int right = left + panelWidth;
        int bottom = top + panelHeight;
        panel(graphics, left, top, right, bottom);

        graphics.drawString(font, "TRIBUNAL DAS ALMAS", left + 22, top + 17, 0xFFE1DCE2, false);
        graphics.drawString(font, data.canonicalMode() ? "VÉU EXPOSTO" : "VÉU FECHADO", right - 91, top + 17,
                data.canonicalMode() ? 0xFFFF5365 : 0xFF9A849F, false);
        drawTab(graphics, left + 20, top + 40, 126, "RUPTURAS  " + data.ruptures().size(), !soulsTab, mouseX, mouseY);
        drawTab(graphics, left + 151, top + 40, 126, "ALMAS  " + data.souls().size(), soulsTab, mouseX, mouseY);
        graphics.fill(left + 20, top + 66, right - 20, top + 67, 0x885D475F);

        if (soulsTab) renderSouls(graphics, mouseX, mouseY, left, top, right, bottom);
        else renderRuptures(graphics, mouseX, mouseY, left, top, right, bottom);
        if (!data.feedback().isBlank()) graphics.drawCenteredString(font, data.feedback(), width / 2, bottom - 17, 0xFFD8B5CA);
    }

    private void renderRuptures(GuiGraphics graphics, int mouseX, int mouseY, int left, int top, int right, int bottom) {
        int split = left + Math.min(220, (right - left) / 2 - 30);
        graphics.drawString(font, "AGUARDANDO VEREDITO", left + 22, top + 78, 0xFFC899A7, false);
        int perPage = 6;
        int pages = Math.max(1, (data.ruptures().size() + perPage - 1) / perPage);
        rupturePage = Math.min(rupturePage, pages - 1);
        for (int row = 0; row < perPage; row++) {
            int index = rupturePage * perPage + row;
            if (index >= data.ruptures().size()) break;
            NoveLiveAdminData.RuptureEntry value = data.ruptures().get(index);
            int y = top + 96 + row * 29;
            boolean selected = value.id() == selectedRupture;
            boolean hover = inside(mouseX, mouseY, left + 20, y, split - 8, y + 24);
            graphics.fill(left + 20, y, split - 8, y + 24, selected ? 0xAA53202B : hover ? 0x88432A36 : 0x66302A32);
            graphics.drawString(font, "#" + value.id() + "  " + value.playerName(), left + 28, y + 5,
                    selected ? 0xFFFFD3DC : 0xFFD3C5D0, false);
            graphics.drawString(font, TIME.format(Instant.ofEpochMilli(value.timestamp())), left + 28, y + 15, 0xFF877B89, false);
        }
        pageControls(graphics, mouseX, mouseY, left + 20, bottom - 43, split - 8, rupturePage, pages);
        NoveLiveAdminData.RuptureEntry selected = data.ruptures().stream()
                .filter(value -> value.id() == selectedRupture).findFirst().orElse(null);
        if (selected == null) {
            graphics.drawCenteredString(font, "Nenhuma ruptura aguarda julgamento.", (split + right) / 2, top + 155, 0xFF8E818F);
            return;
        }
        int x = split + 14;
        graphics.drawString(font, selected.playerName(), x, top + 80, 0xFFFFC1CC, false);
        graphics.drawString(font, "Ruptura #" + selected.id() + " • " + TIME.format(Instant.ofEpochMilli(selected.timestamp())), x, top + 95, 0xFF968895, false);
        detail(graphics, "Causa", selected.cause(), x, top + 119, right - 22);
        detail(graphics, "Local", shortDimension(selected.dimension()) + "  " + selected.x() + ", " + selected.y() + ", " + selected.z(), x, top + 151, right - 22);
        detail(graphics, "Assassino", selected.killer(), x, top + 183, right - 22);
        detail(graphics, "Arma", selected.weapon(), x, top + 215, right - 22);
        actionButton(graphics, mouseX, mouseY, x, bottom - 48, 126, "CONFIRMAR", true);
        actionButton(graphics, mouseX, mouseY, x + 134, bottom - 48, 112, "REJEITAR", false);
    }

    private void renderSouls(GuiGraphics graphics, int mouseX, int mouseY, int left, int top, int right, int bottom) {
        int split = left + Math.min(220, (right - left) / 2 - 30);
        graphics.drawString(font, "JOGADORES PRESENTES", left + 22, top + 78, 0xFFC1A6C9, false);
        int perPage = 7;
        int pages = Math.max(1, (data.souls().size() + perPage - 1) / perPage);
        soulPage = Math.min(soulPage, pages - 1);
        for (int row = 0; row < perPage; row++) {
            int index = soulPage * perPage + row;
            if (index >= data.souls().size()) break;
            NoveLiveAdminData.SoulEntry value = data.souls().get(index);
            int y = top + 96 + row * 25;
            boolean selected = value.id().equals(selectedSoul);
            boolean hover = inside(mouseX, mouseY, left + 20, y, split - 8, y + 21);
            graphics.fill(left + 20, y, split - 8, y + 21, selected ? 0xAA4A274F : hover ? 0x88402E48 : 0x66302A32);
            graphics.drawString(font, value.name(), left + 28, y + 7, selected ? 0xFFF0D9F4 : 0xFFD3C5D0, false);
            graphics.drawString(font, symbols(value.fragments()), split - 67, y + 7, color(value.fragments()), false);
        }
        pageControls(graphics, mouseX, mouseY, left + 20, bottom - 43, split - 8, soulPage, pages);
        NoveLiveAdminData.SoulEntry selected = data.souls().stream().filter(value -> value.id().equals(selectedSoul)).findFirst().orElse(null);
        if (selected == null) {
            graphics.drawCenteredString(font, "Selecione uma alma para administrá-la.", (split + right) / 2, top + 155, 0xFF8E818F);
            return;
        }
        int center = (split + right) / 2;
        graphics.drawCenteredString(font, selected.name(), center, top + 86, 0xFFE8D9EC);
        graphics.drawCenteredString(font, symbols(selected.fragments()), center, top + 115, color(selected.fragments()));
        graphics.drawCenteredString(font, selected.fragments() + "/4 • " + selected.state(), center, top + 137, color(selected.fragments()));
        graphics.drawCenteredString(font, selected.marked() ? "SOB JULGAMENTO" : "ALMA RESGUARDADA", center, top + 158,
                selected.marked() ? 0xFFFF596B : 0xFF998A9D);
        if (selected.pendingRuptures() > 0) graphics.drawCenteredString(font,
                selected.pendingRuptures() + " ruptura(s) pendente(s)", center, top + 176, 0xFFE06A78);

        int controlsX = center - 128;
        actionButton(graphics, mouseX, mouseY, controlsX, top + 202, 80, "− REMOVER", false);
        actionButton(graphics, mouseX, mouseY, controlsX + 176, top + 202, 80, "+ ADICIONAR", true);
        graphics.drawCenteredString(font, "DEFINIR", center, top + 239, 0xFF897D8C);
        for (int value = 0; value <= 4; value++) {
            int x = center - 72 + value * 36;
            boolean hover = inside(mouseX, mouseY, x, top + 251, x + 28, top + 271);
            graphics.fill(x, top + 251, x + 28, top + 271,
                    value == selected.fragments() ? 0xAA623044 : hover ? 0x99513A50 : 0x77302A32);
            graphics.drawCenteredString(font, Integer.toString(value), x + 14, top + 257,
                    value == selected.fragments() ? 0xFFFFD9E0 : 0xFFC4B5C7);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int panelWidth = Math.min(610, width - 24), panelHeight = Math.min(330, height - 20);
        int left = (width - panelWidth) / 2, top = (height - panelHeight) / 2;
        int right = left + panelWidth, bottom = top + panelHeight;
        int split = left + Math.min(220, panelWidth / 2 - 30);
        if (inside(mouseX, mouseY, left + 20, top + 40, left + 146, top + 60)) { soulsTab = false; return true; }
        if (inside(mouseX, mouseY, left + 151, top + 40, left + 277, top + 60)) { soulsTab = true; return true; }
        if (soulsTab) {
            int perPage = 7, pages = Math.max(1, (data.souls().size() + perPage - 1) / perPage);
            for (int row = 0; row < perPage; row++) {
                int index = soulPage * perPage + row, y = top + 96 + row * 25;
                if (index < data.souls().size() && inside(mouseX, mouseY, left + 20, y, split - 8, y + 21)) {
                    selectedSoul = data.souls().get(index).id(); return true;
                }
            }
            if (pageClick(mouseX, mouseY, left + 20, bottom - 43, split - 8, soulPage, pages, true)) return true;
            NoveLiveAdminData.SoulEntry soul = selectedSoul();
            if (soul == null) return false;
            int center = (split + right) / 2, controlsX = center - 128;
            if (inside(mouseX, mouseY, controlsX, top + 202, controlsX + 80, top + 222)) return soulAction("REMOVE", 1);
            if (inside(mouseX, mouseY, controlsX + 176, top + 202, controlsX + 256, top + 222)) return soulAction("ADD", 1);
            for (int value = 0; value <= 4; value++) {
                int x = center - 72 + value * 36;
                if (inside(mouseX, mouseY, x, top + 251, x + 28, top + 271)) return soulAction("SET", value);
            }
        } else {
            int perPage = 6, pages = Math.max(1, (data.ruptures().size() + perPage - 1) / perPage);
            for (int row = 0; row < perPage; row++) {
                int index = rupturePage * perPage + row, y = top + 96 + row * 29;
                if (index < data.ruptures().size() && inside(mouseX, mouseY, left + 20, y, split - 8, y + 24)) {
                    selectedRupture = data.ruptures().get(index).id(); return true;
                }
            }
            if (pageClick(mouseX, mouseY, left + 20, bottom - 43, split - 8, rupturePage, pages, false)) return true;
            int x = split + 14;
            if (selectedRupture >= 0 && inside(mouseX, mouseY, x, bottom - 48, x + 126, bottom - 28))
                return ruptureAction("CONFIRM");
            if (selectedRupture >= 0 && inside(mouseX, mouseY, x + 134, bottom - 48, x + 246, bottom - 28))
                return ruptureAction("REJECT");
        }
        return false;
    }

    private boolean soulAction(String action, int amount) {
        PacketDistributor.sendToServer(new NoveLiveAdminActionPayload(action, selectedSoul, amount, 0));
        return true;
    }

    private boolean ruptureAction(String action) {
        NoveLiveAdminData.RuptureEntry rupture = selectedRupture();
        PacketDistributor.sendToServer(new NoveLiveAdminActionPayload(action,
                rupture == null ? "" : rupture.playerId(), 0, selectedRupture));
        return true;
    }

    private boolean pageClick(double mouseX, double mouseY, int left, int y, int right, int page, int pages, boolean souls) {
        if (inside(mouseX, mouseY, left, y, left + 28, y + 20) && page > 0) {
            if (souls) soulPage--; else rupturePage--; return true;
        }
        if (inside(mouseX, mouseY, right - 28, y, right, y + 20) && page + 1 < pages) {
            if (souls) soulPage++; else rupturePage++; return true;
        }
        return false;
    }

    private NoveLiveAdminData.SoulEntry selectedSoul() {
        return data.souls().stream().filter(value -> value.id().equals(selectedSoul)).findFirst().orElse(null);
    }

    private NoveLiveAdminData.RuptureEntry selectedRupture() {
        return data.ruptures().stream().filter(value -> value.id() == selectedRupture).findFirst().orElse(null);
    }

    private void panel(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, bottom, 0xED100E12);
        graphics.fill(left + 2, top + 2, right - 2, bottom - 2, 0xC3151318);
        graphics.fill(left, top, right, top + 2, 0xFF652733); graphics.fill(left, bottom - 2, right, bottom, 0xFF652733);
        graphics.fill(left, top, left + 2, bottom, 0xFF652733); graphics.fill(right - 2, top, right, bottom, 0xFF652733);
    }

    private void drawTab(GuiGraphics graphics, int x, int y, int w, String text, boolean active, int mouseX, int mouseY) {
        graphics.fill(x, y, x + w, y + 20, active ? 0xAA53202B : inside(mouseX, mouseY, x, y, x + w, y + 20) ? 0x88432A36 : 0x55302A32);
        graphics.drawCenteredString(font, text, x + w / 2, y + 6, active ? 0xFFFFD3DC : 0xFFAA9DAB);
    }

    private void actionButton(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, String text, boolean positive) {
        boolean hover = inside(mouseX, mouseY, x, y, x + w, y + 20);
        graphics.fill(x, y, x + w, y + 20, positive ? (hover ? 0xCC7C283B : 0xAA5E1E2D) : (hover ? 0xCC493A4A : 0xAA332C35));
        graphics.drawCenteredString(font, text, x + w / 2, y + 6, positive ? 0xFFFFD5DC : 0xFFC7BAC9);
    }

    private void pageControls(GuiGraphics graphics, int mouseX, int mouseY, int left, int y, int right, int page, int pages) {
        actionButton(graphics, mouseX, mouseY, left, y, 28, "‹", false);
        actionButton(graphics, mouseX, mouseY, right - 28, y, 28, "›", false);
        graphics.drawCenteredString(font, (page + 1) + "/" + pages, (left + right) / 2, y + 6, 0xFF857987);
    }

    private void detail(GuiGraphics graphics, String label, String value, int x, int y, int right) {
        graphics.drawString(font, label.toUpperCase(), x, y, 0xFF806F82, false);
        graphics.drawString(font, trim(value, right - x), x, y + 12, 0xFFD3C7D5, false);
    }

    private String trim(String value, int maxWidth) { return font.plainSubstrByWidth(value == null ? "—" : value, maxWidth); }
    private String shortDimension(String value) { int colon = value.indexOf(':'); return colon >= 0 ? value.substring(colon + 1) : value; }
    private String symbols(int fragments) { return "◆ ".repeat(Math.max(0, fragments)) + "◇ ".repeat(Math.max(0, 4 - fragments)); }
    private int color(int fragments) { return switch (fragments) { case 4 -> 0xFFC5A4D2; case 3 -> 0xFFE85A68; case 2 -> 0xFFC83B4D; case 1 -> 0xFFFF3549; default -> 0xFF716B76; }; }
    private boolean inside(double x, double y, int left, int top, int right, int bottom) { return x >= left && x < right && y >= top && y < bottom; }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { graphics.fill(0, 0, width, height, 0x66000000); }
    @Override public boolean isPauseScreen() { return false; }
}
