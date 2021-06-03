package de.geolykt.timelapser;

/**
 * The implementation of the algorithm that is chosen for the cell division between different stars.
 */
public enum CellingType {
    /**
     * The more faster algorithm that comes at a huge cost of niceness.
     * Does rarely make use of curves.
     */
    AWTVORONOI(Family.VORONOI),

    /**
     * A variant of the {@link #AWTVORONOI} implementation that limits the size of the individual cells.
     */
    AWTVORONOI_TRUNCTUATED(Family.VORONOI),

    /**
     * A smoothed out variant of the {@link #AWTVORONOI_TRUNCTUATED} implementation. Edges are less radical if they
     * are trunctuated.
     */
    AWTVORONOI_TRUNCTUATED_SMOOTH(Family.VORONOI),

    /**
     * Built-in implementation of the marching squares algorithm.
     * Breaks the markers into rasters for easy computation.
     */
    TIMELAPSER_ISOLINE(Family.ISOLINE);

    /**
     * The algorithm family that is used, the exact implementation is left out however,
     * however even then the implementations can radically differ between each other or look very much the same.
     */
    public static enum Family {

        /**
         * The algorithm produces isoline-like shapes.
         * Very curvy but very expensive in a computation power sense.
         */
        ISOLINE,

        /**
         * The algorithm is based on Voronoi tesselation.
         */
        VORONOI;
    }

    private final Family family;

    private CellingType(Family family) {
        this.family = family;
    }

    public Family getFamily() {
        return family;
    }
}
