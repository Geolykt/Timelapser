package de.geolykt.timelapser;

import java.awt.Point;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Helper interface that attempts to make the process of some math-related issues easier.
 */
public interface MathHelper {

    public static final boolean ENABLE_MH_DEBUG = false;

    public default Map<Point, Point> fixChain(Map<Point, Point> chain, double canvasW, double canvasH) {
        List<Point> pointsH = new ArrayList<>();
        List<Point> pointsV = new ArrayList<>();
        List<Map.Entry<Point, Point>> fixedEntries = new ArrayList<>();
        for (Map.Entry<Point, Point> entry : chain.entrySet()) {
            if (entry.getKey().x == 0 || entry.getKey().x == canvasW) {
                pointsH.add(entry.getKey());
            }
            if (entry.getKey().y == 0 || entry.getKey().y == canvasH) {
                pointsV.add(entry.getKey());
            }
            if (entry.getValue().x == 0 || entry.getValue().x == canvasW) {
                pointsH.add(entry.getValue());
            }
            if (entry.getValue().y == 0 || entry.getValue().y == canvasH) {
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

    public default <T> T indexChain(ArrayList<T> valueDump, Map<T, T> map, T start) {
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

    /**
     * Increases the amount of vertices the polygon has.
     * The shape of the polygon is not altered.
     *
     * @param p The polygon to alter
     */
    public default void multiplyVertices(Polygon p) {
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

    public default int normaliseX(double x, double maxW, double minW, double canvasW) {
        int temp = (int) ((x - minW) / (maxW - minW) * canvasW);
        if (Math.abs(canvasW - temp) < 3) { // Floating point issues may produce this
            return (int) canvasW;
        }
        return temp;
    }

    public default int normaliseY(double y, double maxH, double minH, double canvasH) {
        int temp = (int) ((y - minH) / (maxH - minH) * canvasH);
        temp -= (temp - canvasH/2) * 2;
        if (Math.abs(canvasH - temp) < 3) { // Floating point issues may produce this
            return (int) canvasH;
        }
        return temp;
    }

    public default <T> void putConflict(Map<T, T> map, T conflictKey, T conflictValue, int recursionGuard) {
        if (recursionGuard == -1) {
            throw new IllegalStateException("Recursion guard hit!");
        }
        T oldVal = map.put(conflictValue, conflictKey);
        if (oldVal != null) {
            putConflict(map, conflictValue, oldVal, recursionGuard - 1);
        }
    }

    public default <T> Map<T, T> toMap(List<Map.Entry<T, T>> entries) {
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
                        if (!ENABLE_MH_DEBUG) { // rethrow exception
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
}
