package com.noveris.staffcall.client;

import com.noveris.staffcall.SubmitPlayerCallPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

final class PlayerCallScreen extends Screen {
    private EditBox reason;
    private Button send;
    private String type = "rp";

    PlayerCallScreen() {
        super(Component.literal("Chamar a staff"));
    }

    @Override
    protected void init() {
        int center = width / 2;
        addRenderableWidget(Button.builder(Component.literal("RP — interpretação"),
                button -> select("rp")).bounds(center - 154, height / 2 - 55, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("OFF-RP — ajuda técnica"),
                button -> select("offrp")).bounds(center + 4, height / 2 - 55, 150, 20).build());

        reason = new EditBox(font, center - 154, height / 2 - 20, 308, 20,
                Component.literal("Motivo do chamado"));
        reason.setMaxLength(120);
        reason.setHint(Component.literal("Explique o motivo em 10 a 120 caracteres"));
        reason.setResponder(value -> updateSendButton());
        addRenderableWidget(reason);

        send = addRenderableWidget(Button.builder(Component.literal("Enviar chamado RP"), button -> submit())
                .bounds(center - 75, height / 2 + 20, 150, 20).build());
        updateSendButton();
        setInitialFocus(reason);
    }

    private void select(String selected) {
        type = selected;
        send.setMessage(Component.literal("Enviar chamado " + ("rp".equals(type) ? "RP" : "OFF-RP")));
    }

    private void updateSendButton() {
        if (send != null) send.active = reason.getValue().trim().length() >= 10;
    }

    private void submit() {
        String text = reason.getValue().trim();
        if (text.length() < 10 || text.length() > 120) return;
        PacketDistributor.sendToServer(new SubmitPlayerCallPayload(type, text));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 88, 0xFFFFFF);
        graphics.drawCenteredString(font,
                "RP usa uma apresentação mística; OFF-RP usa um alerta técnico.",
                width / 2, height / 2 - 73, 0xB0B0B0);
        graphics.drawString(font, "Motivo", width / 2 - 154, height / 2 - 31, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
