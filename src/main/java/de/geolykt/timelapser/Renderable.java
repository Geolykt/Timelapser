package de.geolykt.timelapser;

import java.awt.Graphics2D;

@FunctionalInterface
public interface Renderable {

    public void render(Graphics2D g2d);
}
