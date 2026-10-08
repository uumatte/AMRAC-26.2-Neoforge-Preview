package amrac.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import amrac.client.PlaneKeyBindings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class PlaneControlsScreen extends Screen {
    private static final int LIST_TOP = 40;
    private static final int LIST_BOTTOM_MARGIN = 40;
    private static final int ROW_HEIGHT = 22;
    private static final int ROW_WIDTH = 340;
    private static final int CONTROL_WIDTH = 90;
    private static final int RESET_WIDTH = 50;

    @Nullable
    private final Screen parent;
    private ControlsList list;
    private Button resetAllButton;
    @Nullable
    private ControlsList.BindingEntry awaitingKey;
    // Set by the language row; rebuilding inside its own click would pull the list out from under it.
    private boolean rebuildPending;

    public PlaneControlsScreen(@Nullable Screen parent) {
        super(Component.translatable("amrac.controls.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        list = addRenderableWidget(new ControlsList());

        resetAllButton = addRenderableWidget(Button.builder(
                Component.translatable("controls.resetAll"), button -> {
                    PlaneKeyBindings.resetAll();
                    amrac.client.SoundVolumes.resetAll();
                    rebuild();
                })
            .bounds(width / 2 - 155, height - 29, 150, 20).build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE,
                button -> onClose())
            .bounds(width / 2 + 5, height - 29, 150, 20).build());

        refreshResetAll();
    }

    private void rebuild() {
        awaitingKey = null;
        clearWidgets();
        init();
    }

    @Override
    public void tick() {
        super.tick();
        if (rebuildPending) {
            rebuildPending = false;
            rebuild();
        }
    }

    private static Component languageName() {
        return switch (amrac.client.ModLanguage.choice()) {
            case FOLLOW_GAME -> Component.translatable("amrac.controls.language.follow");
            case ENGLISH -> Component.literal("English");
            case CHINESE -> Component.literal("简体中文");
        };
    }

    private void refreshResetAll() {
        if (resetAllButton != null) {
            resetAllButton.active = PlaneKeyBindings.anyChanged()
                || amrac.client.SoundVolumes.anyChanged();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (awaitingKey != null) {
            awaitingKey.assign(InputConstants.Type.MOUSE.getOrCreate(event.button()));
            awaitingKey = null;
            refreshAll();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (awaitingKey != null) {
            awaitingKey.assign(event.key() == InputConstants.KEY_ESCAPE
                ? InputConstants.UNKNOWN
                : InputConstants.getKey(event));
            awaitingKey = null;
            refreshAll();
            return true;
        }
        return super.keyPressed(event);
    }

    private void refreshAll() {
        if (list != null) {
            list.refresh();
        }
        refreshResetAll();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX,
                                   int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 16, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        PlaneKeyBindings.releaseAll();
        if (minecraft != null) {
            minecraft.gui.setScreen(parent);
        }
    }

    private class ControlsList extends ContainerObjectSelectionList<ControlsList.Row> {
        ControlsList() {
            super(PlaneControlsScreen.this.minecraft, PlaneControlsScreen.this.width,
                PlaneControlsScreen.this.height - LIST_TOP - LIST_BOTTOM_MARGIN,
                LIST_TOP, ROW_HEIGHT);
            build();
        }

        private void build() {
            // Top row, so a player stuck in a language they cannot read finds it.
            addEntry(new CycleRow("amrac.controls.language",
                PlaneControlsScreen::languageName,
                () -> {
                    amrac.client.ModLanguage.set(amrac.client.ModLanguage.choice().next());
                    rebuildPending = true;
                },
                amrac.client.ModLanguage::isDefault,
                () -> {
                    amrac.client.ModLanguage.set(
                        amrac.client.ModLanguage.Choice.FOLLOW_GAME);
                    rebuildPending = true;
                }));

            String section = null;
            for (PlaneKeyBindings.Entry entry : PlaneKeyBindings.entries()) {
                if (!entry.section().equals(section)) {
                    section = entry.section();
                    addEntry(new SectionRow(Component.translatable(section)));
                }
                addEntry(new BindingEntry(entry));
            }

            addEntry(new SectionRow(
                Component.translatable(PlaneKeyBindings.SECTION_MOUSE)));
            addEntry(new ToggleRow("amrac.controls.mouse_stick",
                PlaneKeyBindings::mouseStick, PlaneKeyBindings::setMouseStick,
                PlaneKeyBindings::isMouseStickDefault,
                PlaneKeyBindings::resetMouseStick));
            addEntry(new ToggleRow("amrac.controls.invert_vertical_mouse",
                PlaneKeyBindings::invertVerticalMouse,
                PlaneKeyBindings::setInvertVerticalMouse,
                PlaneKeyBindings::isInvertVerticalMouseDefault,
                PlaneKeyBindings::resetInvertVerticalMouse));
            addEntry(new SliderRow("amrac.controls.mouse_sensitivity",
                PlaneKeyBindings.MIN_MOUSE_SENSITIVITY,
                PlaneKeyBindings.MAX_MOUSE_SENSITIVITY,
                PlaneKeyBindings::mouseSensitivity,
                PlaneKeyBindings::previewMouseSensitivity,
                PlaneKeyBindings::setMouseSensitivity,
                PlaneKeyBindings::isMouseSensitivityDefault,
                PlaneKeyBindings::resetMouseSensitivity));
            addEntry(new SliderRow(
                "amrac.controls.mouse_sensitivity_vertical",
                PlaneKeyBindings.MIN_MOUSE_SENSITIVITY,
                PlaneKeyBindings.MAX_MOUSE_SENSITIVITY,
                PlaneKeyBindings::mouseVerticalSensitivity,
                PlaneKeyBindings::previewMouseVerticalSensitivity,
                PlaneKeyBindings::setMouseVerticalSensitivity,
                PlaneKeyBindings::isMouseVerticalSensitivityDefault,
                PlaneKeyBindings::resetMouseVerticalSensitivity));
            addEntry(new CycleRow("amrac.controls.mouse_sensitivity_rule",
                () -> Component.translatable(
                    PlaneKeyBindings.mouseSensitivityRule().translationKey()),
                () -> PlaneKeyBindings.setMouseSensitivityRule(
                    PlaneKeyBindings.mouseSensitivityRule().next()),
                PlaneKeyBindings::isMouseSensitivityRuleDefault,
                PlaneKeyBindings::resetMouseSensitivityRule));
            addEntry(new CycleRow("amrac.controls.horizontal_mouse_stick",
                () -> Component.translatable(
                    PlaneKeyBindings.horizontalMouseAxis().translationKey()),
                () -> PlaneKeyBindings.setHorizontalMouseAxis(
                    PlaneKeyBindings.horizontalMouseAxis().next()),
                PlaneKeyBindings::isHorizontalMouseAxisDefault,
                PlaneKeyBindings::resetHorizontalMouseAxis));

            addEntry(new SectionRow(
                Component.translatable(PlaneKeyBindings.SECTION_KEYBOARD)));
            addEntry(new SliderRow("amrac.controls.keyboard_sensitivity",
                PlaneKeyBindings.MIN_KEYBOARD_SENSITIVITY,
                PlaneKeyBindings.MAX_KEYBOARD_SENSITIVITY,
                PlaneKeyBindings::keyboardSensitivity,
                PlaneKeyBindings::previewKeyboardSensitivity,
                PlaneKeyBindings::setKeyboardSensitivity,
                PlaneKeyBindings::isKeyboardSensitivityDefault,
                PlaneKeyBindings::resetKeyboardSensitivity));
            addEntry(new SliderRow("amrac.controls.pitch_authority",
                PlaneKeyBindings.MIN_PITCH_AUTHORITY,
                PlaneKeyBindings.MAX_PITCH_AUTHORITY,
                PlaneKeyBindings::pitchAuthority,
                PlaneKeyBindings::previewPitchAuthority,
                PlaneKeyBindings::setPitchAuthority,
                PlaneKeyBindings::isPitchAuthorityDefault,
                PlaneKeyBindings::resetPitchAuthority));
            addEntry(new SliderRow("amrac.controls.pitch_authority_step",
                PlaneKeyBindings.MIN_PITCH_AUTHORITY_STEP,
                PlaneKeyBindings.MAX_PITCH_AUTHORITY_STEP,
                PlaneKeyBindings::pitchAuthorityStep,
                PlaneKeyBindings::previewPitchAuthorityStep,
                PlaneKeyBindings::setPitchAuthorityStep,
                PlaneKeyBindings::isPitchAuthorityStepDefault,
                PlaneKeyBindings::resetPitchAuthorityStep));

            addEntry(new SectionRow(
                Component.translatable(PlaneKeyBindings.SECTION_CAMERA)));
            addEntry(new SliderRow("amrac.controls.third_person_distance",
                PlaneKeyBindings.MIN_THIRD_PERSON_DISTANCE,
                PlaneKeyBindings.MAX_THIRD_PERSON_DISTANCE,
                PlaneKeyBindings::thirdPersonDistance,
                PlaneKeyBindings::previewThirdPersonDistance,
                PlaneKeyBindings::setThirdPersonDistance,
                PlaneKeyBindings::isThirdPersonDistanceDefault,
                PlaneKeyBindings::resetThirdPersonDistance));
            addEntry(new SliderRow(
                "amrac.controls.third_person_vertical_angle",
                PlaneKeyBindings.MIN_THIRD_PERSON_VERTICAL_ANGLE,
                PlaneKeyBindings.MAX_THIRD_PERSON_VERTICAL_ANGLE,
                PlaneKeyBindings::thirdPersonVerticalAngle,
                PlaneKeyBindings::previewThirdPersonVerticalAngle,
                PlaneKeyBindings::setThirdPersonVerticalAngle,
                PlaneKeyBindings::isThirdPersonVerticalAngleDefault,
                PlaneKeyBindings::resetThirdPersonVerticalAngle));
            addEntry(new ToggleRow("amrac.controls.camera_follows_roll",
                PlaneKeyBindings::cameraFollowsPlaneRoll,
                PlaneKeyBindings::setCameraFollowsPlaneRoll,
                PlaneKeyBindings::isCameraFollowsPlaneRollDefault,
                PlaneKeyBindings::resetCameraFollowsPlaneRoll));

            addEntry(new SectionRow(
                Component.translatable(PlaneKeyBindings.SECTION_ASSISTANCE)));
            addEntry(new ToggleRow("amrac.controls.auto_throttle",
                PlaneKeyBindings::autoThrottle, PlaneKeyBindings::setAutoThrottle,
                PlaneKeyBindings::isAutoThrottleDefault,
                PlaneKeyBindings::resetAutoThrottle));

            addEntry(new SectionRow(
                Component.translatable("amrac.controls.section.sound")));
            for (amrac.client.SoundVolumes.Channel channel
                    : amrac.client.SoundVolumes.Channel.values()) {
                addEntry(new SliderRow(channel.translationKey(),
                    amrac.client.SoundVolumes.MIN, amrac.client.SoundVolumes.MAX,
                    () -> amrac.client.SoundVolumes.get(channel),
                    value -> amrac.client.SoundVolumes.preview(channel, value),
                    value -> amrac.client.SoundVolumes.set(channel, value),
                    () -> amrac.client.SoundVolumes.isDefault(channel),
                    () -> amrac.client.SoundVolumes.reset(channel)));
            }
        }

        void refresh() {
            for (Row row : children()) {
                row.refresh();
            }
        }

        @Override
        public int getRowWidth() {
            return ROW_WIDTH;
        }

        @Override
        protected int scrollBarX() {
            return width / 2 + ROW_WIDTH / 2 + 8;
        }

        abstract class Row extends ContainerObjectSelectionList.Entry<Row> {
            void refresh() {
            }
        }

        class SectionRow extends Row {
            private final Component label;

            SectionRow(Component label) {
                this.label = label;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX,
                                       int mouseY, boolean hovering,
                                       float partialTick) {
                graphics.text(font, label.copy().withStyle(ChatFormatting.YELLOW),
                    getContentX(), getContentBottom() - 11, 0xFFFFFFFF, false);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of();
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of();
            }
        }

        class BindingEntry extends Row {
            private final PlaneKeyBindings.Entry entry;
            private final Button keyButton;
            private final Button resetButton;

            BindingEntry(PlaneKeyBindings.Entry entry) {
                this.entry = entry;
                this.keyButton = Button.builder(Component.empty(),
                        button -> awaitingKey = this)
                    .bounds(0, 0, CONTROL_WIDTH, 20).build();
                this.resetButton = Button.builder(
                        Component.translatable("controls.reset"), button -> {
                            PlaneKeyBindings.reset(entry);
                            refreshAll();
                        })
                    .bounds(0, 0, RESET_WIDTH, 20).build();
                refresh();
            }

            void assign(InputConstants.Key key) {
                PlaneKeyBindings.set(entry, key);
            }

            @Override
            void refresh() {
                KeyMapping binding = entry.binding();
                Component label = binding.isUnbound()
                    ? Component.translatable("key.keyboard.unknown")
                    : binding.getTranslatedKeyMessage().copy();
                Component shared = PlaneKeyBindings.sharedVanillaAction(entry);
                if (awaitingKey == this) {
                    label = Component.literal("> ")
                        .append(label.copy().withStyle(ChatFormatting.YELLOW))
                        .append(" <").withStyle(ChatFormatting.YELLOW);
                    keyButton.setTooltip(null);
                } else if (PlaneKeyBindings.hasConflict(entry)) {
                    label = label.copy().withStyle(ChatFormatting.RED);
                    keyButton.setTooltip(Tooltip.create(Component.translatable(
                        "amrac.controls.conflict")));
                } else if (shared != null) {
                    keyButton.setTooltip(Tooltip.create(Component.translatable(
                        "amrac.controls.shared_with", shared)));
                } else {
                    keyButton.setTooltip(null);
                }
                keyButton.setMessage(label);
                resetButton.active = !binding.isDefault();
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX,
                                       int mouseY, boolean hovering,
                                       float partialTick) {
                refresh();
                int left = getContentX();
                int top = getContentY();
                int rowWidth = getContentWidth();
                graphics.text(font,
                    Component.translatable(entry.binding().getName()),
                    left, getContentYMiddle() - 4, 0xFFFFFFFF, false);
                layout(keyButton, resetButton, left, rowWidth, top);
                keyButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
                resetButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of(keyButton, resetButton);
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of(keyButton, resetButton);
            }
        }

        class ToggleRow extends Row {
            private final String translationKey;
            private final BooleanGetter getter;
            private final BooleanSetter setter;
            private final BooleanGetter isDefault;
            private final Button toggleButton;
            private final Button resetButton;

            ToggleRow(String translationKey, BooleanGetter getter,
                      BooleanSetter setter, BooleanGetter isDefault,
                      Runnable reset) {
                this.translationKey = translationKey;
                this.getter = getter;
                this.setter = setter;
                this.isDefault = isDefault;
                this.toggleButton = Button.builder(Component.empty(), button -> {
                        setter.set(!getter.get());
                        refreshAll();
                    })
                    .bounds(0, 0, CONTROL_WIDTH, 20).build();
                this.resetButton = Button.builder(
                        Component.translatable("controls.reset"), button -> {
                            reset.run();
                            refreshAll();
                        })
                    .bounds(0, 0, RESET_WIDTH, 20).build();
                refresh();
            }

            @Override
            void refresh() {
                toggleButton.setMessage(getter.get()
                    ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
                resetButton.active = !isDefault.get();
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX,
                                       int mouseY, boolean hovering,
                                       float partialTick) {
                int left = getContentX();
                int top = getContentY();
                int rowWidth = getContentWidth();
                graphics.text(font, Component.translatable(translationKey),
                    left, getContentYMiddle() - 4, 0xFFFFFFFF, false);
                layout(toggleButton, resetButton, left, rowWidth, top);
                toggleButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
                resetButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of(toggleButton, resetButton);
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of(toggleButton, resetButton);
            }
        }

        class SliderRow extends Row {
            private final IntSlider slider;
            private final Button resetButton;
            private final BooleanGetter isDefault;

            SliderRow(String translationKey, int minimum, int maximum,
                      IntGetter getter, IntSetter preview, IntSetter commit,
                      BooleanGetter isDefault, Runnable reset) {
                this.isDefault = isDefault;
                this.slider = new IntSlider(translationKey, minimum, maximum,
                    getter, preview, commit);
                this.resetButton = Button.builder(
                        Component.translatable("controls.reset"), button -> {
                            reset.run();
                            refreshAll();
                        })
                    .bounds(0, 0, RESET_WIDTH, 20).build();
                refresh();
            }

            @Override
            void refresh() {
                slider.syncFromOption();
                resetButton.active = !isDefault.get();
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX,
                                       int mouseY, boolean hovering,
                                       float partialTick) {
                int left = getContentX();
                int top = getContentY();
                int rowWidth = getContentWidth();
                slider.setX(left);
                slider.setY(top);
                slider.setWidth(rowWidth - RESET_WIDTH - 4);
                resetButton.setX(left + rowWidth - RESET_WIDTH);
                resetButton.setY(top);
                slider.extractRenderState(graphics, mouseX, mouseY, partialTick);
                resetButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of(slider, resetButton);
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of(slider, resetButton);
            }
        }

        class CycleRow extends Row {
            private final String label;
            private final Supplier<Component> state;
            private final Runnable cycle;
            private final BooleanSupplier isDefault;
            private final Runnable reset;
            private final Button cycleButton;
            private final Button resetButton;

            CycleRow(String label, Supplier<Component> state, Runnable cycle,
                     BooleanSupplier isDefault, Runnable reset) {
                this.label = label;
                this.state = state;
                this.cycle = cycle;
                this.isDefault = isDefault;
                this.reset = reset;
                this.cycleButton = Button.builder(Component.empty(), button -> {
                        cycle.run();
                        refreshAll();
                    })
                    .bounds(0, 0, CONTROL_WIDTH, 20).build();
                this.resetButton = Button.builder(
                        Component.translatable("controls.reset"), button -> {
                            reset.run();
                            refreshAll();
                        })
                    .bounds(0, 0, RESET_WIDTH, 20).build();
                refresh();
            }

            @Override
            void refresh() {
                cycleButton.setMessage(state.get());
                resetButton.active = !isDefault.getAsBoolean();
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX,
                                       int mouseY, boolean hovering,
                                       float partialTick) {
                int left = getContentX();
                int top = getContentY();
                int rowWidth = getContentWidth();
                graphics.text(font, Component.translatable(label),
                    left, getContentYMiddle() - 4, 0xFFFFFFFF, false);
                layout(cycleButton, resetButton, left, rowWidth, top);
                cycleButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
                resetButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of(cycleButton, resetButton);
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of(cycleButton, resetButton);
            }
        }
    }

    private static void layout(Button primary, Button reset, int left, int rowWidth,
                               int top) {
        reset.setX(left + rowWidth - RESET_WIDTH);
        reset.setY(top);
        primary.setX(left + rowWidth - RESET_WIDTH - CONTROL_WIDTH - 4);
        primary.setY(top);
    }

    private class IntSlider extends AbstractSliderButton {
        private final String translationKey;
        private final int minimum;
        private final int maximum;
        private final IntGetter getter;
        private final IntSetter preview;
        private final IntSetter commit;

        IntSlider(String translationKey, int minimum, int maximum, IntGetter getter,
                  IntSetter preview, IntSetter commit) {
            super(0, 0, ROW_WIDTH - RESET_WIDTH - 4, 20, Component.empty(), 0.0D);
            this.translationKey = translationKey;
            this.minimum = minimum;
            this.maximum = maximum;
            this.getter = getter;
            this.preview = preview;
            this.commit = commit;
            syncFromOption();
        }

        void syncFromOption() {
            value = (double) (getter.get() - minimum) / (maximum - minimum);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(translationKey, currentValue()));
        }

        @Override
        protected void applyValue() {
            preview.set(currentValue());
        }

        @Override
        public void onRelease(MouseButtonEvent event) {
            super.onRelease(event);
            commit.set(currentValue());
            refreshAll();
        }

        private int currentValue() {
            return minimum + (int) Math.round(Mth.clamp(value, 0.0D, 1.0D) *
                (maximum - minimum));
        }
    }

    @FunctionalInterface
    private interface BooleanGetter {
        boolean get();
    }

    @FunctionalInterface
    private interface BooleanSetter {
        void set(boolean value);
    }

    @FunctionalInterface
    private interface IntGetter {
        int get();
    }

    @FunctionalInterface
    private interface IntSetter {
        void set(int value);
    }
}
