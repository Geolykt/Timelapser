package de.geolykt.timelapser;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import de.geolykt.starloader.api.gui.Drawing;
import de.geolykt.starloader.api.gui.Keybind;

public class TimelapserHaltKeybind implements Keybind {

    private final Timelapser tl;

    public TimelapserHaltKeybind(Timelapser extension) {
        tl = extension;
    }

    @Override
    public char getCharacter() {
        return 'h';
    }

    @Override
    public @NotNull String getDescription() {
        return "Pause Timelapse rendering";
    }

    @Override
    public int getKeycode() {
        return 0;
    }

    @Override
    public @Nullable String getKeycodeDescription() {
        return null;
    }

    @Override
    public void performAction() {
        if (tl.isHaltingRendering()) {
            tl.resumeRendering();
            Drawing.toast("Resumed timelapse rendering");
        } else {
            tl.haltRendering();
            Drawing.toast("Paused timelapse rendering, press '" + getCharacter() + "' again to resume.");
        }
    }
}
