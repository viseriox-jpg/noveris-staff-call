package com.noveris.staffcall.client;

import com.noveris.staffcall.NoveLiveBookPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class NoveLiveBookScreen extends Screen {
    private final NoveLiveBookPayload payload;

    NoveLiveBookScreen(NoveLiveBookPayload payload) {
        super(Component.literal("Destino da Alma"));
        this.payload = payload;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Este Screen usa um fundo próprio: o passe padrão aplica o blur de menus do 1.21.1.
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int panelWidth = Math.min(360, width - 28);
        int panelHeight = Math.min(220, height - 24);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int right = left + panelWidth;
        int bottom = top + panelHeight;

        // Vidro negro translúcido, moldura vinho e pequenos cantos arcanos.
        graphics.fill(left, top, right, bottom, 0xDD100E12);
        graphics.fill(left + 2, top + 2, right - 2, bottom - 2, 0xB3151318);
        graphics.fill(left, top, right, top + 2, 0xFF652733);
        graphics.fill(left, bottom - 2, right, bottom, 0xFF652733);
        graphics.fill(left, top, left + 2, bottom, 0xFF652733);
        graphics.fill(right - 2, top, right, bottom, 0xFF652733);
        drawCorner(graphics, left + 7, top + 7);
        drawCorner(graphics, right - 12, top + 7);
        drawCorner(graphics, left + 7, bottom - 12);
        drawCorner(graphics, right - 12, bottom - 12);

        int color = stateColor(payload.fragments());
        graphics.drawString(font, "DESTINO DA ALMA", left + 24, top + 18, 0xFFE1DCE2, false);
        graphics.drawString(font, payload.name(), left + 24, top + 43, 0xFFB69AC2, false);

        String seal = sealText();
        int sealWidth = font.width(seal) + 14;
        graphics.fill(right - sealWidth - 20, top + 14, right - 20, top + 32,
                payload.canonicalMode() && payload.marked() ? 0xAA4B1822 : 0x88402E48);
        graphics.drawCenteredString(font, seal, right - sealWidth / 2 - 20, top + 19,
                payload.canonicalMode() && payload.marked() ? 0xFFFF6876 : 0xFFC0A5C8);

        int shardGap = Math.min(62, (panelWidth - 90) / 4);
        int rowWidth = shardGap * 2 + 18;
        int firstX = width / 2 - rowWidth / 2;
        long time = System.currentTimeMillis() / 350L;
        for (int index = 0; index < 3; index++) {
            int floatOffset = payload.fragments() == 1 && index == 0 ? (int) (time % 2) : (int) ((time + index) % 2);
            drawShard(graphics, firstX + index * shardGap, top + 76 - floatOffset,
                    index < payload.fragments(), color, payload.fragments() == 0);
        }

        graphics.drawCenteredString(font, payload.fragments() + " / 3 Fragmentos", width / 2, top + 119, 0xFFC3AEC8);
        graphics.drawCenteredString(font, payload.state(), width / 2, top + 143, color);
        graphics.fill(width / 2 - 105, top + 161, width / 2 - 8, top + 162, 0x775D475F);
        graphics.fill(width / 2 + 8, top + 161, width / 2 + 105, top + 162, 0x775D475F);
        graphics.fill(width / 2 - 2, top + 159, width / 2 + 2, top + 163, 0xAA806386);
        graphics.drawCenteredString(font, statePhrase(payload.fragments()), width / 2, top + 174, 0xFFB7A6BC);
        graphics.drawCenteredString(font, "[ESC] Retornar ao mundo dos vivos", width / 2, bottom - 20, 0xFF756B78);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Não chama Screen#renderBackground: ele pode ativar o post-processamento de blur.
        // O sombreamento translúcido mantém o mundo visível sem desfocar a interface.
        graphics.fill(0, 0, width, height, 0x66000000);
    }

    private String sealText() {
        if (payload.pendingRuptures() > 0) return "DIANTE DO JULGAMENTO";
        if (!payload.canonicalMode()) return "O LIMIAR ESTÁ SELADO";
        if (payload.marked()) return "O OLHAR ESTÁ SOBRE VOCÊ";
        return "ALÉM DO ALCANCE";
    }

    private String statePhrase(int fragments) {
        return switch (fragments) {
            case 3 -> "Sua essência permanece intocada.";
            case 2 -> "O Véu reconhece as primeiras fissuras.";
            case 1 -> "A Presença conhece o som da sua alma.";
            default -> "Nenhuma resposta atravessa o Véu.";
        };
    }

    private int stateColor(int fragments) {
        return switch (fragments) {
            case 3 -> 0xFFC5A4D2;
            case 2 -> 0xFFC83B4D;
            case 1 -> 0xFFFF3549;
            default -> 0xFF716B76;
        };
    }

    private void drawShard(GuiGraphics graphics, int x, int y, boolean filled, int color, boolean desolate) {
        int outline = desolate ? 0xFF49454D : 0xFF8B828E;
        graphics.fill(x + 7, y, x + 12, y + 2, outline);
        graphics.fill(x + 4, y + 2, x + 15, y + 5, outline);
        graphics.fill(x + 2, y + 5, x + 17, y + 18, outline);
        graphics.fill(x + 4, y + 18, x + 15, y + 22, outline);
        graphics.fill(x + 7, y + 22, x + 12, y + 25, outline);
        graphics.fill(x + 4, y + 5, x + 15, y + 18, filled ? color : 0x55151318);
        graphics.fill(x + 6, y + 3, x + 12, y + 7, filled ? brighten(color) : 0x44151318);
        if (filled) graphics.fill(x + 5, y + 8, x + 7, y + 15, 0x88FFFFFF);
    }

    private int brighten(int color) {
        int red = Math.min(255, ((color >> 16) & 255) + 28);
        int green = Math.min(255, ((color >> 8) & 255) + 18);
        int blue = Math.min(255, (color & 255) + 24);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private void drawCorner(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y + 2, x + 5, y + 3, 0xAA7C3040);
        graphics.fill(x + 2, y, x + 3, y + 5, 0xAA7C3040);
    }

    @Override public boolean isPauseScreen() { return false; }
}
