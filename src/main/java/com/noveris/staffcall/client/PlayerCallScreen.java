package com.noveris.staffcall.client;

import com.noveris.staffcall.SubmitPlayerCallPayload;
import com.noveris.staffcall.PlayerCallStatusPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

final class PlayerCallScreen extends Screen {
    private EditBox reason;
    private Button send;
    private Button rpButton;
    private Button offRpButton;
    private Button cancelButton;
    private String type = "rp";
    private boolean statusMode;
    private String status = "";
    private String statusReason = "";
    private String statusStaff = "";
    private int remainingSeconds;
    private final int minLength;
    private final int maxLength;

    PlayerCallScreen(int minLength, int maxLength) {
        super(Component.literal("Chamar a staff"));
        this.minLength = minLength;
        this.maxLength = maxLength;
    }

    @Override
    protected void init() {
        int center = width / 2;
        rpButton = addRenderableWidget(Button.builder(Component.literal("RP — interpretação"),
                button -> select("rp")).bounds(center - 154, height / 2 - 55, 150, 20).build());
        offRpButton = addRenderableWidget(Button.builder(Component.literal("OFF-RP — ajuda técnica"),
                button -> select("offrp")).bounds(center + 4, height / 2 - 55, 150, 20).build());

        reason = new EditBox(font, center - 154, height / 2 - 20, 308, 20,
                Component.literal("Motivo do chamado"));
        reason.setMaxLength(maxLength);
        reason.setHint(Component.literal("Explique o motivo em " + minLength + " a " + maxLength + " caracteres"));
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
        if (send != null) send.active = reason.getValue().trim().length() >= minLength;
    }

    private void submit() {
        String text = reason.getValue().trim();
        if (text.length() < minLength || text.length() > maxLength) return;
        PacketDistributor.sendToServer(new SubmitPlayerCallPayload(type, text));
        send.active = false;
        reason.setEditable(false);
        status = "ENVIANDO...";
    }

    public void updateStatus(PlayerCallStatusPayload payload) {
        statusMode = !"NONE".equals(payload.state());
        type = payload.callType();
        status = payload.state();
        statusReason = payload.reason();
        statusStaff = payload.staff();
        remainingSeconds = payload.remainingSeconds();
        if (reason != null) reason.visible = !statusMode;
        if (send != null) send.visible = !statusMode;
        if (rpButton != null) rpButton.visible = !statusMode;
        if (offRpButton != null) offRpButton.visible = !statusMode;
        if (statusMode && payload.canCancel() && cancelButton == null) {
            cancelButton = addRenderableWidget(Button.builder(Component.literal("Cancelar chamado"), button -> {
                if (minecraft != null && minecraft.player != null) {
                    minecraft.player.connection.sendCommand("novecall cancelar");
                    button.active = false;
                }
            }).bounds(width / 2 - 75, height / 2 + 45, 150, 20).build());
        }
        if (cancelButton != null) cancelButton.visible = statusMode && payload.canCancel();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 88, 0xFFFFFF);
        graphics.drawCenteredString(font,
                statusMode ? ("offrp".equals(type) ? "Atendimento OFF-RP" : "Atendimento RP")
                        : "RP usa uma apresentação mística; OFF-RP usa um alerta técnico.",
                width / 2, height / 2 - 73, 0xB0B0B0);
        if (statusMode) {
            graphics.drawCenteredString(font, status.replace('_', ' '), width / 2, height / 2 - 35, 0xFFD43B);
            graphics.drawCenteredString(font, "Motivo: " + statusReason, width / 2, height / 2 - 15, 0xFFFFFF);
            if (!statusStaff.isBlank()) graphics.drawCenteredString(font, "Staff: " + statusStaff, width / 2, height / 2 + 3, 0xFFFFFF);
            if (remainingSeconds > 0) graphics.drawCenteredString(font,
                    String.format("Tempo restante: %02d:%02d", remainingSeconds / 60, remainingSeconds % 60),
                    width / 2, height / 2 + 21, 0xFFFF55);
        } else {
            graphics.drawString(font, "Motivo", width / 2 - 154, height / 2 - 31, 0xFFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
