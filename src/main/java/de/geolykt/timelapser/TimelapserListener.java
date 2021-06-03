package de.geolykt.timelapser;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.TexturePaint;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.imageio.ImageIO;

import de.geolykt.starloader.api.Galimulator;
import de.geolykt.starloader.api.actor.ActorSpec;
import de.geolykt.starloader.api.empire.ActiveEmpire;
import de.geolykt.starloader.api.empire.Star;
import de.geolykt.starloader.api.event.EventHandler;
import de.geolykt.starloader.api.event.EventPriority;
import de.geolykt.starloader.api.event.Listener;
import de.geolykt.starloader.api.event.TickEvent;
import de.geolykt.starloader.api.event.lifecycle.ApplicationStartEvent;

public final class TimelapserListener implements Listener, MathHelper {

    public static final Color DARK_ORAGE = Color.ORANGE.darker();
    private static final double DYN_HEIGHT = Timelapser.HEIGHT - 30.0F;
    private static final double DYN_WIDTH = Timelapser.WIDTH - 30.0F;
    public static final int OVAL_RADIUS = 3;
    private final Map<String, BufferedImage> actorCache = new HashMap<>();
    private final Map<Map.Entry<String, Color>, BufferedImage> coloredActorCache = new HashMap<>();

    private final Map<Integer, Color> colors = new HashMap<>();
    private final Map<Integer, Color> colorsLessAlpha = new HashMap<>();
    public boolean displayDrawTime = true;
    public boolean drawActors = false;

    private final Timelapser extension;
    private HashMap<Integer, Font> FONT_CACHE = new HashMap<>();

    private long lastDrawMilli = 0;
    private long lastDrawYear = 0;

    /**
     * The distance between a polygon edge and the parent point of the polygon (in our case the star).
     * Distance in pixels.
     */
    public int maxPolygonDistance = 50;

    private final Map<Integer, Paint> vassalPaint = new HashMap<>();

    protected final AbstractCellingImpl cellingImpl = new DistanceSquaresCellingImpl(10, 200, 60);

    public TimelapserListener(Timelapser extension) {
        this.extension = extension;
    }

    public BufferedImage fetchColTexture(String name, Color awtCol) {
        if (coloredActorCache.containsKey(Map.entry(name, awtCol))) {
            return coloredActorCache.get(Map.entry(name, awtCol));
        }
        BufferedImage base = fetchNocolTexture(name); // Just avoid doing things twice
        BufferedImage bi = new BufferedImage(base.getWidth(), base.getHeight(), base.getType());
        int replaceColor = awtCol.getRGB() & 0x00FFFFFF;
        for (int y = 0; y < base.getHeight(); y++) {
            for (int x = 0; x < base.getWidth(); x++) {
                int argb = base.getRGB(x, y);
                if ((argb & 0x00FFFFFF) == 0x00FFFFFF) {
                    bi.setRGB(x, y, replaceColor | (argb & 0xFF000000));
                }
            }
        }
        coloredActorCache.put(Map.entry(name, awtCol), bi);
        return bi;
    }

    public BufferedImage fetchNocolTexture(String name) {
        // TODO eventually honour the SL 2.0 data folder
        if (actorCache.containsKey(name)) {
            return actorCache.get(name);
        }
        File loc = new File(new File("data", "sprites"), name);
        if (!loc.exists()) {
            extension.getLogger().error(String.format(Locale.ROOT, "Unable to load in sprite: %s (expected it to be in %s)", name, loc.getAbsolutePath()));
            actorCache.put(name, new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB));
            return actorCache.get(name);
        }
        BufferedImage bi;
        try {
            bi = ImageIO.read(loc);
        } catch (IOException e) {
            e.printStackTrace();
            extension.getLogger().error(String.format(Locale.ROOT, "Unable to load in sprite: %s (expected it to be in %s)", name, loc.getAbsolutePath()));
            actorCache.put(name, new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB));
            return actorCache.get(name);
        }
        actorCache.put(name, bi);
        return actorCache.get(name);
    }

    /**
     * Event handler that does late init stuff like registering keybinds.
     *
     * @param evt Unused. Just required for SLAPI to find this method.
     */
    @EventHandler
    public final void lateInit(ApplicationStartEvent evt) {
        Galimulator.getImplementation().registerKeybind(new TimelapserHaltKeybind(extension));
    }

    public final int normaliseX(double x) {
        return normaliseX(x, extension.maxWidth, extension.minWidth, DYN_WIDTH);
    }

    public final int normaliseY(double y) {
        return normaliseY(y, extension.maxHeight, extension.minHeight, DYN_HEIGHT);
    }

    /**
     * Event handler that is run every tick.
     * It is responsible of transferring the render data to the render thread whenever applicable
     *
     * @param evt Unused.
     */
    @EventHandler(EventPriority.MONITOR)
    public final void onTick(TickEvent evt) {
        if (extension.awaitInput.get()) {
            extension.renderData.clear();
            double mapH = Galimulator.getMap().getHeight();
            double mapW = Galimulator.getMap().getWidth();
            extension.minHeight = -mapH;
            extension.maxHeight =  mapH;
            extension.minWidth  = -mapW;
            extension.maxWidth  =  mapW;
            colors.put(Galimulator.getNeutralEmpire().getUID(), Color.LIGHT_GRAY);
            colorsLessAlpha.put(Galimulator.getNeutralEmpire().getUID(), new Color(192, 192, 192, 63));
            HashSet<Star> stars = new HashSet<>();
            ArrayList<Map.Entry<Map.Entry<Float, Float>, Map.Entry<Float, Float>>> starlanes = new ArrayList<>();
            ArrayList<ImmutableQuadruple<Float, Float, Color, Paint>> markers = new ArrayList<>();
            for (Star s : Galimulator.getStars()) {
                renderStar(new HashSet<>(), stars, s, extension.renderData, starlanes, markers);
            }
            ArrayList<ImmutableQuadruple<String, Float, Float, Integer>> empireNames = new ArrayList<>();
            int totalStarCount = Galimulator.getStars().size();
            for (ActiveEmpire empire : Galimulator.getEmpires()) {
                if (totalStarCount/50 < empire.getStarCount() && ((empire.getAge() > 20 && empire.getStarCount() > 100) || (empire.getStarCount() > 200))) {
                    empireNames.add(new ImmutableQuadruple<>(empire.getEmpireName(), empire.getCapitalX(), empire.getCapitalY(), (int) (((double) empire.getStarCount())/totalStarCount * 30) + 10));
                }
            }
            extension.renderData.add(g2d -> {
//                Font f = g2d.getFont();
                Font f = null;
                int lastSize = -2000;
                FontRenderContext frc = g2d.getFontRenderContext();
                for (var name : empireNames) {
                    if (name.d != lastSize || f == null) {
                        lastSize = name.d;
                        f = FONT_CACHE.get(name.d);
                        if (f == null) {
                            f = new Font(Font.MONOSPACED, Font.BOLD, name.d);
                            FONT_CACHE.put(name.d, f);
                        }
                    }
                    GlyphVector gVector = f.createGlyphVector(frc, name.a);
                    Rectangle2D background = gVector.getVisualBounds();
                    g2d.setColor(Color.ORANGE);
                    int x = normaliseX(name.b) - (int) (background.getWidth()/2);
                    int y = normaliseY(name.c);
                    g2d.fillRect(x - 2, y - (int) background.getHeight() - 2, (int) background.getWidth() + 4, (int) background.getHeight() + 6);
                    g2d.setColor(DARK_ORAGE);
                    g2d.drawRect(x - 2, y - (int) background.getHeight() - 2, (int) background.getWidth() + 4, (int) background.getHeight() + 6);
                    g2d.setColor(Color.BLACK);
                    g2d.drawGlyphVector(gVector, x, y);
                }
            });
            if (drawActors) {
                List<ImmutableQuadruple<Float, Float, String, Color>> actors = new ArrayList<>();
                List<ImmutableQuadruple<Float, Float, String, Color>> actorsNocol = new ArrayList<>();
                coloredActorCache.clear();
                for (ActiveEmpire e : Galimulator.getEmpires()) {
                    Color awtColor = e.getAWTColor();
                    Vector<ActorSpec> empireActors = e.getSLActors();
                    for (ActorSpec aSpec : empireActors) {
                        actors.add(new ImmutableQuadruple<>(aSpec.getX(), aSpec.getY(), aSpec.getTextureName(), awtColor));
                        if (aSpec.getColorlessTextureName() != null) {
                            // I know that the docs say that it is not null, but that is a lie. :/
                            actorsNocol.add(new ImmutableQuadruple<>(aSpec.getX(), aSpec.getY(), aSpec.getColorlessTextureName(), awtColor));
                        }
                    }
                }
                extension.renderData.add(g2d -> {
                    // TODO do that but yea
                });
            }
            extension.renderData.add(0, g2d -> {
                g2d.setColor(Color.WHITE);
                for (Map.Entry<Map.Entry<Float, Float>, Map.Entry<Float, Float>> e : starlanes) {
                    int x1 = normaliseX(e.getKey().getKey());
                    int y1 = normaliseY(e.getKey().getValue());
                    int x2 = normaliseX(e.getValue().getKey());
                    int y2 = normaliseY(e.getValue().getValue());
                    g2d.drawLine(x1, y1, x2, y2);
                }
            });
            extension.renderData.add(0, g2d -> {
                var polygons = cellingImpl.process(markers,
                        (float) extension.minWidth, (float) extension.maxWidth,
                        (float) extension.minHeight, (float) extension.maxHeight,
                        (float) DYN_WIDTH, (float) DYN_HEIGHT);
                if (polygons.size() != markers.size()) {
                    throw new IllegalStateException("Unexpected polygon amount");
                }
                for (int i = 0; i < markers.size(); i++) {
                    if (polygons.get(i) == null) {
                        continue;
                    }
                    g2d.setColor(markers.get(i).c);
                    Shape polygon = polygons.get(i);
                    Stroke defaultStroke = g2d.getStroke();
                    g2d.setStroke(new BasicStroke(10.0F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2d.setColor(markers.get(i).c.darker());
                    if (markers.get(i).d != null) {
                        Paint oldPaint = g2d.getPaint();
                        g2d.setPaint(markers.get(i).d);
                        if (polygon instanceof Polygon) {
                            g2d.fillPolygon((Polygon) polygon);
                        } else {
                            g2d.fill(polygon);
                        }
                        g2d.setPaint(oldPaint);
                    } else {
                        if (polygon instanceof Polygon) {
                            g2d.fillPolygon((Polygon) polygon);
                        } else {
                            g2d.fill(polygon);
                        }
                    }
                    g2d.setColor(Color.WHITE);
                    g2d.setStroke(defaultStroke);
                }
            });
            BufferedImage bi = Galimulator.getMap().getAWTBackground();
            if (bi != null) {
                extension.renderData.add(0, g2d -> {
                    g2d.drawImage(bi, 0, 0, (int) DYN_WIDTH, (int) DYN_HEIGHT, 0, 0, bi.getWidth(), bi.getHeight(), null);
                });
            }

            if (displayDrawTime) {
                long thisDrawMilli = System.currentTimeMillis();
                long currentYear = Galimulator.getGameYear();
                extension.renderData.add(g2d -> {
                    g2d.setColor(Color.WHITE);
                    g2d.drawString(String.format("FPS: %.02f", 1000.0f / (thisDrawMilli - lastDrawMilli)), 10, 10);
                    if (currentYear > 9_999_999) {
                        g2d.drawString(String.format("Year: %d", currentYear), 10, 30);
                    } else {
                        g2d.drawString(String.format("Year: %08d", currentYear), 10, 30);
                    }
                    g2d.drawString(String.format("Time taken: %03d years", currentYear - lastDrawYear), 10, 50);
                    lastDrawYear = currentYear;
                    lastDrawMilli = thisDrawMilli;
                });
            }
            extension.awaitInput.set(false);
            extension.lock.release();
        }
    }

    public final void renderStar(Set<ActiveEmpire> empires, Set<Star> stars, Star s, ArrayList<Renderable> renderData, ArrayList<Map.Entry<Map.Entry<Float, Float>, Map.Entry<Float, Float>>> starlanes, ArrayList<ImmutableQuadruple<Float, Float, Color, Paint>> markers) {
        if (stars.contains(s)) {
            return;
        }
        stars.add(s);
        float x = s.getX();
        float y = s.getY();
        ActiveEmpire empire = s.getAssignedEmpire();
        Color col = colors.get(empire.getUID());
        Paint paint = vassalPaint.get(empire.getUID());
        if (empires.add(empire)) {
            if (col == null) {
                col = empire.getAWTColor();
                colors.put(empire.getUID(), col);
                colorsLessAlpha.put(empire.getUID(), new Color(col.getRed(), col.getGreen(), col.getBlue(), 127));
            }
            boolean alwaysFalse = false;
            if (alwaysFalse) { // Alternative paints are only going to be good for vassals, but are completely unusable for alliances as initially intended
                BufferedImage tex = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                Graphics2D texG2d = tex.createGraphics();
                texG2d.setColor(colorsLessAlpha.get(empire.getUID()));
                texG2d.fillRect(0, 0, 16, 16);
                texG2d.setColor(empire.getAlliance().getAWTColor());
                texG2d.drawLine(0, 0, 16, 16);
                texG2d.drawLine(16, 0, 0, 16);
                texG2d.drawLine(0, 16, 16, 0);
                paint = new TexturePaint(tex, new Rectangle(16, 16));
                vassalPaint.put(s.getAssignedEmpire().getUID(), paint);
            }
        }
        markers.add(new ImmutableQuadruple<>(x, y, colorsLessAlpha.get(empire.getUID()), paint));
        ArrayList<Map.Entry<Float, Float>> neighbours = new ArrayList<>();
        for (Star s2 : s.getNeighbours()) {
            neighbours.add(Map.entry(s2.getX(), s2.getY()));
        }
        Color finalCol = col; // Workaround through java's lambda logic
        float wealth = s.getWealth();
        renderData.add(g2d -> {
            int xPos = normaliseX(x);
            int yPos = normaliseY(y);
            g2d.setColor(finalCol);
            // \sqrt{\log\left(x+1\right)}\cdot1.5
            int starDiameter = (int) (OVAL_RADIUS * 3 * Math.sqrt(Math.log(wealth + 1)));
            int starRadius = starDiameter/2;
            g2d.fillOval(xPos - starRadius, yPos - starRadius, starDiameter, starDiameter);
        });
        Map.Entry<Float, Float> pos = Map.entry(x, y);
        for (Star s2 : s.getNeighbours()) {
            renderStar(empires, stars, s2, renderData, starlanes, markers);
            starlanes.add(Map.entry(pos, Map.entry(s2.getX(), s2.getY())));
        }
        s = null; // To prevent accidental use in lambdas
    }
} final class ImmutableQuadruple<J, K, L, M> {

    public final J a;
    public final K b;
    public final L c;
    public final M d;

    public ImmutableQuadruple(J a, K b, L c, M d) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
    }
}
