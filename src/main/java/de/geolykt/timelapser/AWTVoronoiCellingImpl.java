package de.geolykt.timelapser;

import java.awt.Color;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import be.humphreys.simplevoronoi.GraphEdge;
import be.humphreys.simplevoronoi.Voronoi;

public class AWTVoronoiCellingImpl extends AbstractCellingImpl {
    public static int DEBUG_POLYGON = -1;
    public static boolean ENABLE_DEBUG = false;

    /**
     * The distance between a polygon edge and the parent point of the polygon (in our case the star).
     * Distance in pixels.
     */
    private final float maxPolygonDistance;

    /**
     * The squared distance between a polygon edge and the parent point of the polygon (in our case the star).
     * Distance in pixels.
     */
    private final float maxPolygonDistanceSquared;

    private final boolean smoothPolygons;
    private final boolean trunctuatePolygons;

    public AWTVoronoiCellingImpl(CellingType type, float polySize) {
        super(type);
        switch (type) {
        case AWTVORONOI:
            this.trunctuatePolygons = false;
            this.smoothPolygons = false;
            break;
        case AWTVORONOI_TRUNCTUATED:
            this.trunctuatePolygons = true;
            this.smoothPolygons = false;
            break;
        case AWTVORONOI_TRUNCTUATED_SMOOTH:
            this.trunctuatePolygons = true;
            this.smoothPolygons = true;
            break;
        default:
            throw new IllegalArgumentException();
        }
        this.maxPolygonDistance = polySize;
        this.maxPolygonDistanceSquared = polySize * polySize;
    }

    @Override
    public List<Polygon> process(ArrayList<ImmutableQuadruple<Float, Float, Color, Paint>> markers,
            final float minW, final float maxW, final float minH, final float maxH,
            final float canvasW, final float canvasH) {
        Voronoi vor = new Voronoi(Math.min(minH/canvasH, minW/canvasW)); // I do not really know what the value means
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
        List<GraphEdge> edges = vor.generateVoronoi(xValues, yValues, minW, maxW, minH, maxH);
        for (GraphEdge e : edges) {
            List<Map.Entry<Point, Point>> l1 = siteEdges.get(e.site1);
            List<Map.Entry<Point, Point>> l2 = siteEdges.get(e.site2);
            Map.Entry<Point, Point> entry = Map.entry(new Point(normaliseX(e.x1, maxW, minW, canvasW), normaliseY(e.y1, maxH, minH, canvasH)), new Point(normaliseX(e.x2, maxW, minW, canvasW), normaliseY(e.y2, maxH, minH, canvasH)));
            l1.add(entry);
            l2.add(entry);
        }
        List<Polygon> polygons = new ArrayList<>(numStars);
        for (int i = 0; i < numStars; i++) {
            List<Map.Entry<Point, Point>> polygonPoints = siteEdges.get(i);
            int pointAmount = polygonPoints.size();
            if (pointAmount == 0) {
//                System.err.println("Cell " + i + " is empty! Generating empty polygon.");
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
        if (!trunctuatePolygons) {
            return polygons;
        }
        for (int i = 0; i < numStars; i++) {
            Polygon p = polygons.get(i);
            if (p == null) {
                continue;
            }
            boolean trunctuated = false;
            var marker = markers.get(i);
            int xPos = normaliseX(marker.a, maxW, minW, canvasW);
            int yPos = normaliseY(marker.b, maxH, minH, canvasH);
            for (int j = 0; j < p.npoints; j++) {
                int deltaXSq = (xPos - p.xpoints[j]) * (xPos - p.xpoints[j]);
                int deltaYSq = (yPos - p.ypoints[j]) * (yPos - p.ypoints[j]);
                double distSq = deltaXSq + deltaYSq;
                if (distSq > maxPolygonDistanceSquared) {
                    if (smoothPolygons && !trunctuated) {
                        multiplyVertices(p);
                        multiplyVertices(p);
                        multiplyVertices(p);
                        j = -1; // reset array (-1 because of the j++ that will be invoked later on)
                        trunctuated = true;
                        continue;
                    }
                    double ratio = maxPolygonDistance / Math.sqrt(distSq);
                    int newDeltaX = (int) ((xPos - p.xpoints[j]) * -ratio);
                    int newDeltaY = (int) ((yPos - p.ypoints[j]) * -ratio);
                    p.xpoints[j] = xPos + newDeltaX;
                    p.ypoints[j] = yPos + newDeltaY;
                }
            }
        }
        return polygons;
    }
}
