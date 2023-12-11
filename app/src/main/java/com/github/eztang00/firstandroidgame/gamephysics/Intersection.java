package com.github.eztang00.firstandroidgame.gamephysics;

/**
 * Intersection between two lines or arcs
 */
public class Intersection extends DoublePoint {
    /**
     * The winding number is -1 or 1
     * depending on whether the first line/arc crosses the
     * second line/arc from the left or right
     */
    public int windingNumber;

    public Intersection(double x, double y, int windingNumber) {
        super(x, y);
        this.windingNumber = windingNumber;
    }
}
