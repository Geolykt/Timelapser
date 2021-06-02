package de.geolykt.timelapser;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.TexturePaint;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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

import be.humphreys.simplevoronoi.GraphEdge;
import be.humphreys.simplevoronoi.Voronoi;

public final class TimelapserListener implements Listener {

    private final Timelapser extension;
    private final Map<Integer, Color> colors = new HashMap<>();
    private final Map<Integer, Color> colorsLessAlpha = new HashMap<>();
    private final Map<Integer, Paint> vassalPaint = new HashMap<>();
    private final Map<String, BufferedImage> actorCache = new HashMap<>();
    private final Map<Map.Entry<String, Color>, BufferedImage> coloredActorCache = new HashMap<>();

    private static final double DYN_WIDTH = Timelapser.WIDTH - 30.0F;
    private static final double DYN_HEIGHT = Timelapser.HEIGHT - 30.0F;
    private long lastDrawMilli = 0;
    private long lastDrawYear = 0;

    private HashMap<Integer, Font> FONT_CACHE = new HashMap<>();
    public static final Color DARK_ORAGE = Color.ORANGE.darker();

    public static final int OVAL_RADIUS = 3;

    public boolean trunctuatePolygon = true;
    public boolean displayDrawTime = true;
    public boolean drawActors = true;

    /**
     * The squared distance between a polygon edge and the parent point of the polygon (in our case the star).
     * Distance in pixels.
     */
    public int maxPolygonDistanceSquared = 50 * 50;

    /**
     * The distance between a polygon edge and the parent point of the polygon (in our case the star).
     * Distance in pixels.
     */
    public int maxPolygonDistance = 50;

    public TimelapserListener(Timelapser extension) {
        this.extension = extension;
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
                colorsLessAlpha.put(empire.getUID(), new Color(col.getRed(), col.getGreen(), col.getBlue(), 191));
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

    public static int DEBUG_POLYGON = -1;
    public static boolean ENABLE_DEBUG = false;

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
                Voronoi vor = new Voronoi(Math.min(extension.minHeight/DYN_HEIGHT, extension.minWidth/DYN_WIDTH)); // I do not really know what the value means
                final int numStars = markers.size();
                double[] xValues = new double[numStars];
                double[] yValues = new double[numStars];
                for (int i = 0; i < numStars; i++) {
                    var marker = markers.get(i);
                    xValues[i] = marker.a;
                    yValues[i] = marker.b;
                }
                List<List<Map.Entry<Point, Point>>> siteEdges = new ArrayList<>(numStars);
                for (int i = 0; i < numStars; i++) {
                    siteEdges.add(new ArrayList<>());
                }
                List<GraphEdge> edges = vor.generateVoronoi(xValues, yValues, extension.minWidth, extension.maxWidth, extension.minHeight, extension.maxHeight);
                for (GraphEdge e : edges) {
                    List<Map.Entry<Point, Point>> l1 = siteEdges.get(e.site1);
                    List<Map.Entry<Point, Point>> l2 = siteEdges.get(e.site2);
                    Map.Entry<Point, Point> entry = Map.entry(new Point(normaliseX(e.x1), normaliseY(e.y1)), new Point(normaliseX(e.x2), normaliseY(e.y2)));
                    l1.add(entry);
                    l2.add(entry);
                }
                List<Polygon> polygons = new ArrayList<>(numStars);
                for (int i = 0; i < numStars; i++) {
                    List<Map.Entry<Point, Point>> polygonPoints = siteEdges.get(i);
                    int pointAmount = polygonPoints.size();
                    if (pointAmount == 0) {
//                        System.err.println("Cell " + i + " is empty! Generating empty polygon.");
                        polygons.add(null);
                        continue;
                    }
                    // TODO this section creates hard to debug issues, but would be a nice optimisation
//                    if (pointAmount < 4) { // For triangles and simple lines, there isn't anything to reorder
//                        Polygon p = new Polygon();
//                        boolean requireOverthinking = false;
//                        for (int j = 0; j < pointAmount; j++) {
//                            Map.Entry<Point, Point> entry = polygonPoints.get(j);
//                            if (entry.getKey().x == 0 || entry.getKey().x == DYN_WIDTH
//                                    || entry.getKey().y == 0 || entry.getKey().y == DYN_HEIGHT
//                                    || entry.getValue().x == 0 || entry.getValue().x == DYN_WIDTH
//                                    || entry.getValue().y == 0 || entry.getValue().y == DYN_HEIGHT) {
//                                requireOverthinking = true;
//                                break;
//                            }
//                            p.addPoint(entry.getKey().x, entry.getKey().y);
//                        }
//                        if (!requireOverthinking) {
//                            polygons.add(p);
//                            continue;
//                        }
//                    }
                    int[] xPoly = new int[pointAmount];
                    int[] yPoly = new int[pointAmount];
                    if (ENABLE_DEBUG && i == DEBUG_POLYGON) {
                        int count = 0;
                        for (var indexed : polygonPoints) {
                            System.err.printf("%02d, %s\n", count++, indexed.toString());
                        }
                    }
                    Map<Point, Point> pointMap;
                    try {
                        pointMap = toMap(polygonPoints);
                    } catch (IllegalStateException e) {
                        if (ENABLE_DEBUG) {
                            e.printStackTrace();
                            System.err.println("Cell " + i + " has invalid data! Generating empty polygon.");
                            int count = 0;
                            for (var indexed : polygonPoints) {
                                System.err.printf("%02d, %s\n", count++, indexed.toString());
                            }
                        }
                        polygons.add(new Polygon());
                        continue;
                    }
                    if (ENABLE_DEBUG && i == DEBUG_POLYGON) {
                        int count = 0;
                        for (var indexed : pointMap.entrySet()) {
                            System.err.printf("%02d, %s\n", count++, indexed.toString());
                        }
                    }

                    Point point = polygonPoints.get(0).getKey();
                    if (pointMap.get(point) == null) {
                        if (pointMap.isEmpty()) {
                            if (ENABLE_DEBUG) {
                                System.err.println("Unable to chain data for polygon " + i + ".");
                                int count = 0;
                                for (var indexed : polygonPoints) {
                                    System.err.printf("%02d, %s\n", count++, indexed.toString());
                                }
                                System.err.println("End of report. Generating empty polygon.");
                            }
                            polygons.add(new Polygon());
                            continue;
                        }
                        // Invalid key (this is often a result of 0-width edges)
                        point = pointMap.keySet().iterator().next();
                    }
                    int j = 0;
                    boolean unnaturalAbort = false;
                    boolean fixedChains = false;
                    while (true) {
                        xPoly[j] = point.x;
                        yPoly[j] = point.y;
                        if (++j == pointAmount && point.x != 0 && point.y != 0 && point.x != DYN_WIDTH && point.y != DYN_HEIGHT) {
                            break;
                        }
                        Point newPoint = pointMap.get(point);
                        if ((point.x == 0 || point.y == 0 || point.x == DYN_WIDTH || point.y == DYN_HEIGHT) || newPoint == null) {
                            if (!fixedChains) {
                                pointMap = fixChain(pointMap);
                            }
                            ArrayList<Point> indexedPoints = new ArrayList<>();
                            Point begin = polygonPoints.get(0).getKey();
                            if (pointMap.get(begin) == null) {
                                // Invalid key (this is often a result of 0-width edges)
                                begin = pointMap.keySet().iterator().next();
                            }
                            indexChain(indexedPoints, pointMap, begin);
                            if (pointMap.size() > indexedPoints.size() && ENABLE_DEBUG) {
                                System.err.println("Unable to resolve chaining errors!");
                                System.err.println("Indexed points:");
                                int count = 0;
                                for (Point indexed : indexedPoints) {
                                    System.err.printf("%02d, %s\n", count++, indexed.toString());
                                }
                                System.err.println("Mapped points:");
                                count = 0;
                                for (var indexed : pointMap.entrySet()) {
                                    System.err.printf("%02d, %s\n", count++, indexed.toString());
                                }
                                System.err.println("Affected polygon: " + i);
                                System.err.println("End of report.");
                            }
                            Polygon polygon = new Polygon();
                            int count = 0;
                            for (Point indexed : indexedPoints) {
                                polygon.addPoint(indexed.x, indexed.y);
                                if (ENABLE_DEBUG && i == DEBUG_POLYGON) {
                                    System.err.printf("%02d - %s\n", count++, indexed.toString());
                                }
                            }
                            polygons.add(polygon);
                            unnaturalAbort = true;
                            break;
                        } else {
                            point = newPoint;
                        }
                    }
                    if (!unnaturalAbort) {
                        if (ENABLE_DEBUG && i == DEBUG_POLYGON) {
                            System.err.printf("%s, %s, %02d\n", Arrays.toString(xPoly), Arrays.toString(yPoly), j);
                        }
                        polygons.add(new Polygon(xPoly, yPoly, j));
                    }
                }
                for (int i = 0; i < numStars; i++) {
                    Color col = markers.get(i).c.darker();
                    Polygon p = polygons.get(i);
                    if (p == null) {
                        continue;
                    }
                    boolean trunctuated = false;
                    var marker = markers.get(i);
                    if (trunctuatePolygon) {
                        int xPos = normaliseX(marker.a);
                        int yPos = normaliseY(marker.b);
                        for (int j = 0; j < p.npoints; j++) {
                            int deltaXSq = (xPos - p.xpoints[j]) * (xPos - p.xpoints[j]);
                            int deltaYSq = (yPos - p.ypoints[j]) * (yPos - p.ypoints[j]);
                            double distSq = deltaXSq + deltaYSq;
                            if (distSq > maxPolygonDistanceSquared) {
                                if (!trunctuated) {
                                    multiplyVertices(p);
                                    multiplyVertices(p);
                                    multiplyVertices(p);
                                    j = -1; // reset array (-1 because of the j++ that will be invoked later on)
                                    trunctuated = true;
                                    continue;
                                }
//                                double ratio = deltaXSq / deltaYSq;
//                                double trunctuatedX = maxPolygonDistanceSquared * ratio;
                                double ratio = maxPolygonDistance / Math.sqrt(distSq);
                                int newDeltaX = (int) ((xPos - p.xpoints[j]) * -ratio);
                                int newDeltaY = (int) ((yPos - p.ypoints[j]) * -ratio);
                                p.xpoints[j] = xPos + newDeltaX;
                                p.ypoints[j] = yPos + newDeltaY;
                            }
                        }
                    }
                    if (trunctuated) {
                        Stroke defaultStroke = g2d.getStroke();
                        g2d.setStroke(new BasicStroke(10.0F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2d.setColor(col);
                        if (marker.d != null) {
                            Paint oldPaint = g2d.getPaint();
                            g2d.setPaint(marker.d);
                            g2d.fillPolygon(p);
                            g2d.setPaint(oldPaint);
                        } else {
                            g2d.fillPolygon(p);
                        }
                        g2d.setColor(Color.WHITE);
                        g2d.setStroke(defaultStroke);
//                        g2d.drawPolygon(p);
                    } else {
                        g2d.setColor(col);
                        if (marker.d != null) {
                            Paint oldPaint = g2d.getPaint();
                            g2d.setPaint(marker.d);
                            g2d.fillPolygon(p);
                            g2d.setPaint(oldPaint);
                        } else {
                            g2d.fillPolygon(p);
                        }
                        g2d.setColor(Color.WHITE);
//                        g2d.setStroke(new Basi)
//                        g2d.drawPolygon(p);
                    }
                }
                if (ENABLE_DEBUG) {
                    for (int i = 0; i < numStars; i++) {
                        g2d.setColor(Color.CYAN);
                        g2d.drawString(Integer.toString(i), polygons.get(i).xpoints[0], polygons.get(i).ypoints[0]);
                    }
                }
//                for (GraphEdge e : edges) {
//                    g2d.setColor(Color.WHITE);
//                    g2d.drawLine(normaliseX(e.x1), normaliseY(e.y1), normaliseX(e.x2), normaliseY(e.y2));
//                }
            });
            BufferedImage bi = Galimulator.getImplementation().getMap().getAWTBackground();
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

    /**
     * Increases the amount of vertices the polygon has.
     * The shape of the polygon is not altered.
     *
     * @param p The polygon to alter
     */
    private void multiplyVertices(Polygon p) {
        int[] xPosO = p.xpoints;
        int[] yPosO = p.ypoints;
        int points = p.npoints;
        int[] xPosN = new int[points * 2];
        int[] yPosN = new int[points * 2];
        p.npoints = points * 2;
        p.xpoints = xPosN;
        p.ypoints = yPosN;
        for (int i = 0, j = 0; i < points;) {
            int x = xPosO[i];
            int y = yPosO[i++];
            xPosN[j] = x;
            yPosN[j++] = y;
            xPosN[j] = x + ((xPosO[i % points] - x) / 2);
            yPosN[j++] = y + ((yPosO[i % points] - y) / 2);
        }
        p.invalidate();
    }

    public static final <T> T indexChain(ArrayList<T> valueDump, Map<T, T> map, T start) {
        T last = null;
        HashSet<T> alreadyIndexed = new HashSet<>(valueDump);
        while (start != null) {
            if (last != null && !alreadyIndexed.add(start)) {
                return start;
            }
            last = start;
            valueDump.add(start);
            start = map.get(start);
        }
        return last;
    }

    public static final Map<Point, Point> fixChain(Map<Point, Point> chain) {
        List<Point> pointsH = new ArrayList<>();
        List<Point> pointsV = new ArrayList<>();
        List<Map.Entry<Point, Point>> fixedEntries = new ArrayList<>();
        for (Map.Entry<Point, Point> entry : chain.entrySet()) {
            if (entry.getKey().x == 0 || entry.getKey().x == DYN_WIDTH) {
                pointsH.add(entry.getKey());
            }
            if (entry.getKey().y == 0 || entry.getKey().y == DYN_HEIGHT) {
                pointsV.add(entry.getKey());
            }
            if (entry.getValue().x == 0 || entry.getValue().x == DYN_WIDTH) {
                pointsH.add(entry.getValue());
            }
            if (entry.getValue().y == 0 || entry.getValue().y == DYN_HEIGHT) {
                pointsV.add(entry.getValue());
            }
            fixedEntries.add(entry);
        }
        if (pointsH.size() == 2) {
            fixedEntries.add(Map.entry(pointsH.get(0), pointsH.get(1)));
        } else if (pointsH.size() == 1 && pointsV.size() == 1) {
            Point edge = new Point(pointsH.get(0).x, pointsV.get(0).y);
            fixedEntries.add(Map.entry(pointsH.get(0), edge));
            fixedEntries.add(Map.entry(edge, pointsV.get(0)));
        } else if (pointsV.size() == 2) {
            fixedEntries.add(Map.entry(pointsV.get(0), pointsV.get(1)));
        }
        return toMap(fixedEntries);
    }

    public final int normaliseX(float x) {
        return normaliseX((double) x);
    }

    public final int normaliseY(float y) {
        return normaliseY((double) y);
    }

    public final int normaliseX(double x) {
        int temp = (int) ((x - extension.minWidth) / (extension.maxWidth - extension.minWidth) * DYN_WIDTH);
        if (Math.abs(DYN_WIDTH - temp) < 3) { // Floating point issues may produce this
            return (int) DYN_WIDTH;
        }
        return temp;
    }

    public final int normaliseY(double y) {
        int temp = (int) ((y - extension.minHeight) / (extension.maxHeight - extension.minHeight) * DYN_HEIGHT);
        temp -= (temp - DYN_HEIGHT/2) * 2;
        if (Math.abs(DYN_HEIGHT - temp) < 3) { // Floating point issues may produce this
            return (int) DYN_HEIGHT;
        }
        return temp;
    }

    public static final <T> void putConflict(Map<T, T> map, T conflictKey, T conflictValue, int recursionGuard) {
        if (recursionGuard == -1) {
            throw new IllegalStateException("Recursion guard hit!");
        }
        T oldVal = map.put(conflictValue, conflictKey);
        if (oldVal != null) {
            putConflict(map, conflictValue, oldVal, recursionGuard - 1);
        }
    }

    public static final <T> Map<T, T> toMap(List<Map.Entry<T, T>> entries) {
        HashMap<T, T> m = new HashMap<>(entries.size());
        for (Map.Entry<T, T> entry : entries) {
            if (entry.getKey().equals(entry.getValue())) {
                continue; // This tends to result in trouble
            }
            if (m.containsKey(entry.getKey())) {
                T oldValue = m.put(entry.getValue(), entry.getKey());
                if (oldValue != null) {
                    try {
                        putConflict(m, entry.getValue(), oldValue, m.size() * 3);
                    } catch (IllegalStateException e) {
                        if (!ENABLE_DEBUG) { // rethrow exception
                            throw new IllegalStateException(e);
                        }
                        int i = 0;
                        for (Map.Entry<T, T> e2 : entries) {
                            System.err.printf("%02d, %s - %s\n", i++, e2.getKey().toString(), e2.getValue().toString());
                        }
                        throw new IllegalStateException("An exception occoured.", e);
                    }
                }
            } else {
                m.put(entry.getKey(), entry.getValue());
            }
        }
        return m;
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
