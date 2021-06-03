package de.geolykt.timelapser;

import java.awt.Color;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public abstract class AbstractCellingImpl implements MathHelper {

    public static int DEBUG_POLYGON = -1;
    public static boolean ENABLE_DEBUG = true;

    protected final CellingType type;

    public AbstractCellingImpl(CellingType type) {
        this.type = type;
    }

    public CellingType getType() {
        return type;
    }

    public abstract List<Polygon> process(ArrayList<ImmutableQuadruple<Float, Float, Color, Paint>> markers,
            final float minW, final float maxW, final float minH, final float maxH,
            final float canvasW, final float canvasH);

    public List<Polygon> processEdges(List<List<Map.Entry<Point, Point>>> siteEdges, float canvasW, float canvasH) {
        int numStars = siteEdges.size();
        List<Polygon> polygons = new ArrayList<>(numStars);
        for (int i = 0; i < numStars; i++) {
            List<Map.Entry<Point, Point>> polygonPoints = siteEdges.get(i);
            int pointAmount = polygonPoints.size();
            if (pointAmount == 0) {
                System.err.println("Cell " + i + " is empty! Generating empty polygon.");
                polygons.add(null);
                continue;
            }
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
                if (++j == pointAmount && point.x != 0 && point.y != 0 && point.x != canvasW && point.y != canvasH) {
                    break;
                }
                Point newPoint = pointMap.get(point);
                if ((point.x == 0 || point.y == 0 || point.x == canvasW || point.y == canvasH) || newPoint == null) {
                    if (!fixedChains) {
                        pointMap = fixChain(pointMap, canvasW, canvasH);
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
        return polygons;
    }
}
