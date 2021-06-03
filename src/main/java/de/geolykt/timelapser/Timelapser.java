package de.geolykt.timelapser;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import de.geolykt.starloader.api.event.EventManager;
import de.geolykt.starloader.mod.Extension;

public class Timelapser extends Extension {

    public static final double HEIGHT = (1080.0F);

    public static final double WIDTH = (1920.0F);

    public final AtomicBoolean awaitInput = new AtomicBoolean(true);
    private boolean halted = false;

    public final Semaphore lock = new Semaphore(0);
    public TimelapserListener ls = new TimelapserListener(this);
    public double maxHeight = 0.0F;
    public double maxWidth = 0.0F;

    public double minHeight = 0.0F;
    public double minWidth = 0.0F;

//    public static final double WIDTH = (3840.0F);
//    public static final double HEIGHT = (2160.0F);

    public final ArrayList<Renderable> renderData = new ArrayList<>();

    public void haltRendering() {
        halted = true;
    }

    public boolean isHaltingRendering() {
        return halted;
    }

    @Override
    public void postInitialize() {
        new Thread(new TimelapserRender(this), "Timelapser-Render-Thread").start();
        EventManager.registerListener(ls);
    }

    public void resumeRendering() {
        halted = false;
    }
}
