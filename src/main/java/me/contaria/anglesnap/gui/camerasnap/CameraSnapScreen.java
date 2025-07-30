package me.contaria.anglesnap.gui.camerasnap;

import me.contaria.anglesnap.AngleSnap;
import me.contaria.anglesnap.gui.config.AngleSnapConfigScreen;
import me.contaria.anglesnap.gui.screen.IconButtonWidget;
import me.contaria.anglesnap.gui.warning.AngleSnapWarningScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class CameraSnapScreen extends Screen {
    private static final Text CONFIGURE_TEXT = Text.translatable("anglesnap.gui.screen.configure");
    private static final Identifier CONFIGURE_TEXTURE = Identifier.of("anglesnap", "textures/gui/configure.png");

    private final Screen parent;

    protected CameraSnapScreen(Screen parent) {
        super(Text.translatable("anglesnap.gui.screen.title.camerasnap"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.addDrawableChild(new TextWidget(0, 10, this.width, 15, this.title, this.textRenderer).alignCenter());
        this.addDrawableChild(new IconButtonWidget(this.width - 26, 10, CONFIGURE_TEXT, button -> MinecraftClient.getInstance().setScreen(new AngleSnapConfigScreen(this)), CONFIGURE_TEXTURE));
        this.addDrawableChild(new CameraSnapListWidget(this.client, this.width, this.height - 70, 35));
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> this.close()).dimensions(this.width / 2 - 100, this.height - 27, 200, 20).build());
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(this.parent);
    }

    @Override
    public void removed() {
        AngleSnap.CONFIG.saveAngles();
    }

    public static Screen create(Screen parent) {
        if (AngleSnap.CONFIG.hasCameraPositions()) {
            if (AngleSnap.isInMultiplayer() && !AngleSnap.CONFIG.disableMultiplayerWarning.getValue()) {
                return AngleSnapWarningScreen.create(
                        disableMultiplayerWarning -> {
                            if (disableMultiplayerWarning) {
                                AngleSnap.CONFIG.disableMultiplayerWarning.setValue(true);
                                AngleSnap.CONFIG.save();
                            }
                            MinecraftClient.getInstance().setScreen(new CameraSnapScreen(parent));
                        },
                        () -> MinecraftClient.getInstance().setScreen(parent)
                );
            }
            return new CameraSnapScreen(parent);
        }
        return new AngleSnapConfigScreen(parent);
    }
}
