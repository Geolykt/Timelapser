package de.geolykt.timelapser;

import java.awt.Color;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import be.humphreys.simplevoronoi.GraphEdge;
import be.humphreys.simplevoronoi.Voronoi;

public class AWTVoronoiCellingImpl extends AbstractCellingImpl {

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
    public List<? extends Shape> process(ArrayList<ImmutableQuadruple<Float, Float, Color, Paint>> markers,
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
        List<Polygon> polygons = processEdges(siteEdges, canvasW, canvasH);
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
