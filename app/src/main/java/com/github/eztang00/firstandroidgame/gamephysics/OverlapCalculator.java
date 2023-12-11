package com.github.eztang00.firstandroidgame.gamephysics;

/**
 * Calculates the area etc. of the overlap between two shapes (or just one shape),
 * by adding the contribution of each line or arc to the area etc.
 *
 * The <a href="https://en.wikipedia.org/wiki/Shoelace_formula">shoelace formula</a> is one example.
 */
public interface OverlapCalculator {
    /**
     *
     * @param isRealIntersection a "real" intersection is a line segment/arc in one shape
     *                           actually crossing a line segment/arc in the other.
     *                           a "fake" intersection is a line segment/arc in one shape
     *                           theoretically crossing a line segment/arc in the other
     *                           if it was extended. This still means the shapes intersect
     *                           since this line segment/arc is inside the other shape.
     *                           This line segment/arc is also part of the overlap (union)
     *                           between the two shapes, just like a line segment/arc that
     *                           actually intersects the other shape.
     */
    void addLineSegmentToOverlap(double startX, double startY, double endX, double endY, int windingFactor, boolean lineSegmentIsFirstShape, GamePolyarcgon.PolyarcgonPointCache nextPoint, boolean isRealIntersection);

    /**
     *
     * @param isRealIntersection see addLineSegmentToOverlap
     */
    void addArcToOverlap(double radiusOfCurvature, double arcCenterX, double arcCenterY, double arcStartX, double arcStartY, double arcEndX, double arcEndY, double arcAngleChange, int windingFactor, boolean arcIsFirstShape, GamePolyarcgon.PolyarcgonPointCache nextPoint, boolean isRealIntersection);

}
