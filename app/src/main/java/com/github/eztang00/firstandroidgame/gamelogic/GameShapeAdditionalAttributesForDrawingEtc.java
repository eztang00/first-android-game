package com.github.eztang00.firstandroidgame.gamelogic;

import android.graphics.Color;

/**
 * Attributes of a shape that helps with drawing
 */
public class GameShapeAdditionalAttributesForDrawingEtc {
    public static final GameShapeAdditionalAttributesForDrawingEtc WHITE_BALL_MATERIAL = new GameShapeAdditionalAttributesForDrawingEtc(Color.WHITE, Color.TRANSPARENT, 0, Color.TRANSPARENT, 0, Specialness.BALL);
    public static final GameShapeAdditionalAttributesForDrawingEtc DARK_GREEN_MATERIAL = new GameShapeAdditionalAttributesForDrawingEtc(Color.rgb(0x00, 0x80, 0x00), Color.TRANSPARENT, 0, Color.TRANSPARENT, 0, Specialness.NONE);
    public static final GameShapeAdditionalAttributesForDrawingEtc SCI_FI_MATERIAL = new GameShapeAdditionalAttributesForDrawingEtc(Color.TRANSPARENT, Color.TRANSPARENT, 0, Color.CYAN, 0.05, Specialness.NONE);
    public static final GameShapeAdditionalAttributesForDrawingEtc RIPPLE_MATERIAL = new GameShapeAdditionalAttributesForDrawingEtc(Color.TRANSPARENT, Color.TRANSPARENT, 0, Color.TRANSPARENT, 0, Specialness.RIPPLE);
    public final int color;
    public final int edgeColor;
    public final double edgeThickness;
    public final int shadowColor;
    public final double shadowThickness;
    public final Specialness specialness;

    public enum Specialness {
        RIPPLE,
        BALL,
        NONE;
    }

    public GameShapeAdditionalAttributesForDrawingEtc(int color, int edgeColor, double edgeThickness, int shadowColor, double shadowThickness, Specialness specialness) {
        this.color = color;
        this.edgeColor = edgeColor;
        this.edgeThickness = edgeThickness;
        this.shadowColor = shadowColor;
        this.shadowThickness = shadowThickness;
        this.specialness = (specialness != null) ? specialness : Specialness.NONE;
    }
}
