package me.contaria.anglesnap;

import com.mojang.logging.LogUtils;
import me.contaria.anglesnap.config.AngleSnapConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.StickyKeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.util.Objects;

public class AngleSnap implements ClientModInitializer {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final AngleSnapConfig CONFIG = new AngleSnapConfig();

    public static KeyBinding openMenu;
    public static KeyBinding openOverlay;
    public static KeyBinding cameraPositions;

    @Nullable
    public static CameraPosEntry currentCameraPos;

    @Override
    public void onInitializeClient() {
        openMenu = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "anglesnap.key.openmenu",
                GLFW.GLFW_KEY_F6,
                "anglesnap.key"
        ));
        openOverlay = KeyBindingHelper.registerKeyBinding(new StickyKeyBinding(
                "anglesnap.key.openoverlay",
                GLFW.GLFW_KEY_F7,
                "anglesnap.key",
                () -> true
        ));
        cameraPositions = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "anglesnap.key.camerapositions",
                GLFW.GLFW_KEY_F8,
                "anglesnap.key"
        ));

        WorldRenderEvents.LAST.register(AngleSnap::renderOverlay);
        HudElementRegistry.addFirst(Identifier.of("anglesnap", "overlay"), AngleSnap::renderHud);

        ClientPlayConnectionEvents.JOIN.register((networkHandler, packetSender, client) -> {
            if (client.isIntegratedServerRunning()) {
                AngleSnap.CONFIG.loadAnglesAndCameraPositions(Objects.requireNonNull(client.getServer()).getSavePath(WorldSavePath.ROOT).getParent().getFileName().toString(), false);
            } else {
                AngleSnap.CONFIG.loadAnglesAndCameraPositions(Objects.requireNonNull(networkHandler.getServerInfo()).address, true);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((networkHandler, client) -> AngleSnap.CONFIG.unloadAnglesAndCameraPositions());
    }

    public static boolean shouldRenderOverlay() {
        return openOverlay.isPressed();
    }

    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        if (shouldRenderOverlay()) {
            if (AngleSnap.CONFIG.angleHud.getValue()) {
                renderAngleHud(context);
            }
        }
    }

    private static void renderOverlay(WorldRenderContext context) {
        if (shouldRenderOverlay()) {
            for (AngleEntry angle : AngleSnap.CONFIG.getAngles()) {
                renderMarker(context, angle, AngleSnap.CONFIG.markerScale.getValue(), AngleSnap.CONFIG.textScale.getValue());
            }
        }
    }

    private static void renderMarker(WorldRenderContext context, AngleEntry angle, float markerScale, float textScale) {
        markerScale = markerScale / 10.0f;
        textScale = textScale / 50.0f;

        Vector3f pos = Vec3d.fromPolar(
                MathHelper.wrapDegrees(angle.pitch),
                MathHelper.wrapDegrees(angle.yaw + 180.0f)
        ).multiply(-1.0, 1.0, -1.0).toVector3f();
        Quaternionf rotation = context.camera().getRotation();

        drawIcon(context, pos, rotation, angle, markerScale);
        if (!angle.name.isEmpty()) {
            drawName(context, pos, rotation, angle, textScale);
        }
    }

    private static void drawIcon(WorldRenderContext context, Vector3f pos, Quaternionf rotation, AngleEntry angle, float scale) {
        if (scale == 0.0f) {
            return;
        }

        MatrixStack matrices = Objects.requireNonNull(context.matrixStack());
        matrices.push();
        matrices.translate(pos.x(), pos.y(), pos.z());
        matrices.multiply(rotation);
        matrices.scale(scale, -scale, scale);

        Matrix4f matrix4f = matrices.peek().getPositionMatrix();
        MinecraftClient client = MinecraftClient.getInstance();
        RenderLayer layer = RenderLayer.getCelestial(angle.getIcon());
        VertexConsumer consumer = client.getBufferBuilders().getEffectVertexConsumers().getBuffer(layer);
        consumer.vertex(matrix4f, -1.0f, -1.0f, 0.0f).color(angle.color).texture(0.0f, 0.0f);
        consumer.vertex(matrix4f, -1.0f, 1.0f, 0.0f).color(angle.color).texture(0.0f, 1.0f);
        consumer.vertex(matrix4f, 1.0f, 1.0f, 0.0f).color(angle.color).texture(1.0f, 1.0f);
        consumer.vertex(matrix4f, 1.0f, -1.0f, 0.0f).color(angle.color).texture(1.0f, 0.0f);

        matrices.scale(1.0f / scale, 1.0f / -scale, 1.0f / scale);
        matrices.pop();
    }

    private static void drawName(WorldRenderContext context, Vector3f pos, Quaternionf rotation, AngleEntry angle, float scale) {
        if (scale == 0.0f || angle.name.isEmpty()) {
            return;
        }

        MatrixStack matrices = Objects.requireNonNull(context.matrixStack());
        matrices.push();
        matrices.translate(pos.x(), pos.y(), pos.z());
        matrices.multiply(rotation);
        matrices.scale(scale, -scale, scale);

        Matrix4f matrix4f = matrices.peek().getPositionMatrix();
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        float x = -textRenderer.getWidth(angle.name) / 2.0f;
        int backgroundColor = (int) (client.options.getTextBackgroundOpacity(0.25f) * 255.0f) << 24;
        textRenderer.draw(
                angle.name, x, -15.0f, Colors.WHITE, false, matrix4f, client.getBufferBuilders().getEffectVertexConsumers(), TextRenderer.TextLayerType.SEE_THROUGH, backgroundColor, 15
        );

        matrices.scale(1.0f / scale, 1.0f / -scale, 1.0f / scale);
        matrices.pop();
    }

    private static void renderAngleHud(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getDebugHud().shouldShowDebugHud() || client.player == null) {
            return;
        }

        TextRenderer textRenderer = client.textRenderer;
        String text = String.format("%.3f / %.3f", MathHelper.wrapDegrees(client.player.getYaw()), MathHelper.wrapDegrees(client.player.getPitch()));
        context.fill(5, 5, 5 + 2 + textRenderer.getWidth(text) + 2, 5 + 2 + textRenderer.fontHeight + 2, -1873784752);
        context.drawText(textRenderer, text, 5 + 2 + 1, 5 + 2 + 1, -2039584, false);
    }

    public static boolean isInMultiplayer() {
        return MinecraftClient.getInstance().world != null && !MinecraftClient.getInstance().isInSingleplayer();
    }
}
