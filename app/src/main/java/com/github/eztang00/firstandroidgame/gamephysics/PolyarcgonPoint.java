package com.github.eztang00.firstandroidgame.gamephysics;

/**
 * A point representing a corner or inflection point in a "polyarcgon".
 * x and y represents where the point is.
 * arcAngleChange represents how curved the line segment or arc before the point is.
 */
public class PolyarcgonPoint {
    public double x;
    public double y;
    public double arcAngleChange; // positive if shape curves outwards negative if shape curves inwards
    boolean isMoveToWithoutLineEtc; //no line or arc just move to this point

    public PolyarcgonPoint(double x, double y, double arcAngleChange, boolean isMoveToWithoutLineEtc) {
        this.x = x;
        this.y = y;
        this.arcAngleChange = arcAngleChange;
        this.isMoveToWithoutLineEtc = isMoveToWithoutLineEtc;
    }

    public boolean isAlmostStraight() {
        return isAlmostStraight(arcAngleChange);
    }

    public static boolean isAlmostStraight(double arcAngleChange) {
        return Math.abs(arcAngleChange) <= Math.PI / 1000000.0;
    }
    // note we use "arcAngleChange" rather than curvature
    // because there are 4 possible arcs between every two points
    // with the same curvature, so even a signed curvature still needs
    // complicated conditions
}
