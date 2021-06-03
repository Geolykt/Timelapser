package de.geolykt.timelapser;

import java.awt.Color;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

/**
 * A partially functional implementation of the marching squares algorithm.
 * It is not fully functional which is why it is deprecated, though it is assumed that with the
 * right parameters a semi-functional output can be achieved, though let it be with a few issues.
 * That being said, if someone wishes to fix it, then they are free to do so.
 *
 * @author Geolykt
 * @deprecated This class is unfinished and will likely produce ugly output.
 *
 */
@Deprecated
public class IsolineCellingImpl extends AbstractCellingImpl {

    protected final int isolineGridSize;
    protected final int markerGridSize;
    protected final int cutoffSq;

    public IsolineCellingImpl(int isolineGridSize, int markerGridSize) {
        super(CellingType.TIMELAPSER_ISOLINE);
        this.isolineGridSize = isolineGridSize;
        this.markerGridSize = markerGridSize;
        this.cutoffSq = Integer.MAX_VALUE;
    }

    private Marker nearest(int x, int y, MarkerList[][] raster) {
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
    public List<Polygon> process(ArrayList<ImmutableQuadruple<Float, Float, Color, Paint>> markers, float minW,
            float maxW, float minH, float maxH, float canvasW, float canvasH) {

        // Initialise requires data holders
        Point[] normalisedPoints = new Point[markers.size()];
        int mRasterWidth = (int) Math.ceil(canvasW / markerGridSize);
        int mRasterHeight = (int) Math.ceil(canvasH / markerGridSize);
        int cRasterWidth = (int) Math.ceil(canvasW / isolineGridSize);
        int cRasterHeight = (int) Math.ceil(canvasH / isolineGridSize);
        MarkerList[][] mRaster = new MarkerList[mRasterHeight][mRasterWidth];
        int[][] colorRaster = new int[cRasterHeight][cRasterWidth];

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

        // Index colors
        for (int y = 0; y < cRasterHeight; y++) {
            for (int x = 0; x < cRasterWidth; x++) {
                int mX = x * isolineGridSize;
                int mY = y * isolineGridSize;
                Marker marker = nearest(mX, mY, mRaster);
                if (marker.distanceSq(mX, mY) > cutoffSq) {
                    colorRaster[y][x] = -1;
                } else {
                    colorRaster[y][x] = marker.index;
                }
            }
        }

        @SuppressWarnings("unchecked")
        List<Map.Entry<Point, Point>>[] lines = new List[normalisedPoints.length];

        for (int i = 0; i < lines.length; i++) {
            lines[i] = new ArrayList<>();
        }

        int gridOffset = isolineGridSize / 2;
        for (int y = 0; y < cRasterHeight - 1; y++) {
            for (int x = 0; x < cRasterWidth - 1; x++) {
                // First character = u (upper) or l (lower)
                // Second character = l (left) or r (right)
                // Note on the upper / lower cases: Due to how I am, I say that the lower the y value is, the higher
                // the point is, this might be a bit confusing which is why I am writing this comment
                int ul = colorRaster[y][x];
                int ur = colorRaster[y][x + 1];
                int ll = colorRaster[y + 1][x];
                int lr = colorRaster[y + 1][x + 1];
//                System.out.printf("%X, %X, %X, %X\n", ul, ur, ll, lr);
                // The edges of the square and whether there is a line passing through them
                // This is a bitfield to allow the use of the switch table
                byte edges = 0;
                if (ul != ur) {
                    // upper
                    edges |= 0b0001;
                }
                if (lr != ur) {
                    // right
                    edges |= 0b0010;
                }
                if (ll != lr) {
                    // lower
                    edges |= 0b0100;
                }
                if (ul != ll) {
                    // left
                    edges |= 0b1000;
                }

                if (edges == 0) {
                    continue; // Prematurely abort as there is no need to create lines
                }

                int x1 = x * isolineGridSize;
                int x2 = x * isolineGridSize + gridOffset;
                int x3 = (x + 1) * isolineGridSize;
                int y1 = y * isolineGridSize;
                int y2 = y * isolineGridSize + gridOffset;
                int y3 = (y + 1) * isolineGridSize;

                switch (edges) {
                case 0b0001:
                case 0b0010:
                case 0b0100:
                case 0b1000:
                    break; // rarely possible, but usually does not happen
                case 0b1001: // left + upper
                    pushLn(new Point(x1, y2), new Point(x2, y1), lines, ul, lr);
                    break;
                case 0b1010: // left + right
                    pushLn(new Point(x1, y2), new Point(x3, y2), lines, ul, lr);
                    break;
                case 0b1100: // left + lower
                    pushLn(new Point(x1, y2), new Point(x2, y3), lines, ll, ur);
                    break;
                case 0b0101: // lower + upper
                    pushLn(new Point(x2, y1), new Point(x2, y3), lines, ll, ur);
                    break;
                case 0b0110: // lower + right
                    pushLn(new Point(x2, y1), new Point(x3, y2), lines, ul, lr);
                    break;
                case 0b0011: // upper + right
                    pushLn(new Point(x2, y1), new Point(x1, y2), lines, ul, lr);
                    break;
                case 0b1011: // left + upper + right
                    pushLn(new Point(x2, y1), new Point(x2, y2), lines, ul, ur);
                    pushLn(new Point(x1, y2), new Point(x3, y2), lines, ul, ur, ll);
                    break;
                case 0b1111: // cross
                    pushLn(new Point(x2, y1), new Point(x2, y2), lines, ul, ur);
                    pushLn(new Point(x3, y2), new Point(x2, y2), lines, ur, lr);
                    pushLn(new Point(x2, y3), new Point(x2, y2), lines, ll, lr);
                    pushLn(new Point(x1, y2), new Point(x2, y2), lines, ul, ll);
                    break;
                case 0b0111: // lower + right + upper
                    pushLn(new Point(x2, y1), new Point(x2, y3), lines, ul, ur, lr);
                    pushLn(new Point(x3, y2), new Point(x2, y2), lines, ur, lr);
                    break;
                case 0b1110: // right + lower + left
                    pushLn(new Point(x2, y3), new Point(x2, y2), lines, ll, lr);
                    pushLn(new Point(x1, y2), new Point(x3, y2), lines, ul, lr, ll);
                    break;
                case 0b1101: // upper + lower + left
                    pushLn(new Point(x2, y1), new Point(x2, y3), lines, ul, ur, ll);
                    pushLn(new Point(x1, y2), new Point(x2, y2), lines, ul, ll);
                    break;
                default:
                    System.out.printf("IsolineCellingImpl forgot edge case: %X\n", edges);
                }
            }
        }

        return processEdges(Arrays.asList(lines), canvasW, canvasH);
    }

    private void pushLn(Point a, Point b, List<Map.Entry<Point, Point>>[] lines, int... indices) {
        Map.Entry<Point, Point> ln = Map.entry(a, b);
        for (int index : indices) {
            if (index == -1) {
                continue;
            }
            lines[index].add(ln);
        }
    }

} class Marker {
    final int index;
    final int x;
    final int y;

    Marker(int x, int y, int index) {
        this.x = x;
        this.y = y;
        this.index = index;
    }

    int distanceSq(int x, int y) {
        x -= this.x;
        y -= this.y;
        return (x * x + y * y);
    }

    int distanceSq(Marker other) {
        if (other == null) {
            return Integer.MAX_VALUE;
        }
        int px = other.x - x;
        int py = other.y - y;
        return (px * px + py * py);
    }

} class MarkerList extends ArrayList<Marker> {

    /**
     * serialVersionUID.
     */
    private static final long serialVersionUID = 7992958861845439232L;

    Marker getNearest(int x, int y) {
        if (isEmpty()) {
            return null;
        }
        int minDistSq = Integer.MAX_VALUE;
        Marker nearestMarker = null;
        for (int i = 0; i < size(); i++) {
            Marker current = get(i);
            int dist = current.distanceSq(x, y);
            if (dist < minDistSq) {
                nearestMarker = current;
                minDistSq = dist;
            }
        }
        return nearestMarker;
    }
}
