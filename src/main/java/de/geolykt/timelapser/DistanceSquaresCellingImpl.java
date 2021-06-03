package de.geolykt.timelapser;

import java.awt.Color;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

/**
 * An BFS-based cell division implementation that does stuff.
 */
public class DistanceSquaresCellingImpl extends AbstractCellingImpl {

    protected final int cutoffSq;
    protected final int isolineGridSize;
    protected final int markerGridSize;

    public DistanceSquaresCellingImpl(int isolineGridSize, int markerGridSize, int cutoff) {
        super(CellingType.DISTANCE_SQARES);
        this.isolineGridSize = isolineGridSize;
        this.markerGridSize = markerGridSize;
        this.cutoffSq = cutoff * cutoff;
    }

    protected @Nullable Marker nearest(int x, int y, MarkerList[][] raster) {
        int rasterX = x / markerGridSize;
        int rasterY = y / markerGridSize;
        Marker nearest = null;
        int nearestDist = Integer.MAX_VALUE;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                Marker current = nearest0(x, y, dx + rasterX, dy + rasterY, raster);
                if (current == null) {
                    continue;
                }
                int currentDist = current.distanceSq(x, y);
                if (currentDist < nearestDist) {
                    nearestDist = currentDist;
                    nearest = current;
                }
            }
        }
        return nearest;
    }

    /**
     * IOOBE resistant form of raster[y][x].getNearest(actualX, actualY);
     *
     * @param actualX The X value used for the nearest operation
     * @param actualY The Y value used for the nearest operation
     * @param x The X value of the raster within the raster grid
     * @param y The Y value of the raster within the raster grid
     * @param raster The raster grid
     * @return The nearest Marker to the given point, or null if not applicable
     */
    private final @Nullable Marker nearest0(int actualX, int actualY, int x, int y, MarkerList[][] raster) {
        if (x < 0 || y < 0 || y >= raster.length || x >= raster[y].length)
            return null;
        if (raster[y][x] == null) {
            raster[y][x] = new MarkerList();
            return null;
        }
        return raster[y][x].getNearest(actualX, actualY);
    }

    @Override
    public List<? extends Shape> process(ArrayList<ImmutableQuadruple<Float, Float, Color, Paint>> markers, float minW,
            float maxW, float minH, float maxH, float canvasW, float canvasH) {

        /* === Initial Setup code === */
        // Initialise requires data holders
        Point[] normalisedPoints = new Point[markers.size()];
        int mRasterWidth = (int) Math.ceil(canvasW / markerGridSize);
        int mRasterHeight = (int) Math.ceil(canvasH / markerGridSize);
        int cRasterWidth = (int) Math.ceil(canvasW / isolineGridSize);
        int cRasterHeight = (int) Math.ceil(canvasH / isolineGridSize);
        MarkerList[][] mRaster = new MarkerList[mRasterHeight][mRasterWidth];

        // Index markers
        for (int i = 0; i < normalisedPoints.length; i++) {
            var marker = markers.get(i);
            int x = normaliseX(marker.a, maxW, minW, canvasW);
            int y = normaliseY(marker.b, maxH, minH, canvasH);
            normalisedPoints[i] = new Point(x, y);
            int rasterX = x / markerGridSize;
            int rasterY = y / markerGridSize;
            if (mRaster[rasterY][rasterX] == null) {
                mRaster[rasterY][rasterX] = new MarkerList();
            }
            mRaster[rasterY][rasterX].add(new Marker(x, y, i));
        }

        // Init area holders
        List<Area> areas = new ArrayList<>(normalisedPoints.length);
        for (int i = 0; i < normalisedPoints.length; i++) {
            areas.add(new Area());
        }

        /* === Actual logic === */
        for (int y = 0; y < cRasterHeight; y++) {
            for (int x = 0; x < cRasterWidth; x++) {
                int mX = x * isolineGridSize;
                int mY = y * isolineGridSize;
                Marker marker = nearest(mX, mY, mRaster);
                if (marker == null) {
                    continue;
                }
                if (marker.distanceSq(mX, mY) < cutoffSq) {
                    // You can do funky stuff if you alter the width and height of the rectangles to values that were not intended
                    // perhaps I should allow configurabillity for that
                    areas.get(marker.index).add(new Area(new Rectangle(mX, mY, isolineGridSize, isolineGridSize)));
                }
            }
        }
        return areas;
    }
}
