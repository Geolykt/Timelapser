package de.geolykt.timelapser;

import java.awt.Color;
import java.awt.Paint;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractCellingImpl implements MathHelper {

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
}
