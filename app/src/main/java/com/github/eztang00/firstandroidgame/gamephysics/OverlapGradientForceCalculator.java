package com.github.eztang00.firstandroidgame.gamephysics;

/**
 * Similar to the OverlapAreaIntegralCalculator, but calculates the force/direction
 * two overlapping objects should push against each other with.
 *
 * What direction should the two overlapping objects push against each other?
 * They should push against each other in the direction that decreases the
 * overlap area the fastest.
 *
 * This means if the overlap area is a function of the position of one shape
 * (relative to the other), the gradient of this function tells us the direction
 * to push the shapes to minimize their overlap.
 *
 * And the gradient of this function is surprisingly simple: it's just the vector
 * from one end of the overlap to the other, turned 90 degrees.
 *
 * So each line segment or arc given to the OverlapGradientForceCalculator,
 * the OverlapGradientForceCalculator adds its contribution to the vector from
 * one end of the overlap to the other, turned 90 degrees.
 *
 * This sort of resembles the <a href="https://en.wikipedia.org/wiki/Shoelace_formula">shoestring formula</a>.
 *
 * It also calculates the area (just like OverlapAreaIntegralCalculator) and
 * the perimeter, in order to estimate the depth of the overlap.
 * This can be used to give deeper overlaps a stronger force, discouraging
 * objects from being shoved deep into each other by strong forces.
 *
 */
class OverlapGradientForceCalculator implements OverlapCalculator {
    public final GameShape firstShape;
    public final GameShape otherShape;
    double overlapGradientForceX = 0;
    double overlapGradientForceY = 0;
    double overlapGradientTorqueOnFirstShape = 0;
    double overlapGradientTorqueOnOtherShape = 0;
    double overlapPerimeter = 0;
    double overlapArea = 0;

    public OverlapGradientForceCalculator(GameShape firstShape, GameShape otherShape) {
        this.firstShape = firstShape;
        this.otherShape = otherShape;
    }

    public void addLineSegmentToOverlap(double startX, double startY, double endX, double endY, int windingFactor, boolean lineSegmentIsFirstShape, GamePolyarcgon.PolyarcgonPointCache nextPoint, boolean isRealIntersection) {
        double forceX = -(endY - startY);
        double forceY = endX - startX;
        if (lineSegmentIsFirstShape) {
            this.overlapGradientForceX += windingFactor * forceX;
            this.overlapGradientForceY += windingFactor * forceY;

            double forceLocationX = (startX + endX) / 2;
            double forceLocationY = (startY + endY) / 2;
            this.overlapGradientTorqueOnFirstShape += windingFactor * ((forceLocationX - this.firstShape.getX()) * forceY - (forceLocationY - this.firstShape.getY()) * forceX);
            this.overlapGradientTorqueOnOtherShape += windingFactor * (-((forceLocationX - this.otherShape.getX()) * forceY - (forceLocationY - this.otherShape.getY()) * forceX));
        }
        double overlapAreaContribution = (startX * endY - startY * endX) / 2.0;
        this.overlapArea += windingFactor * overlapAreaContribution;
        this.overlapPerimeter += windingFactor * Math.sqrt(forceX*forceX + forceY*forceY);
    }

    public void addArcToOverlap(double radiusOfCurvature, double arcCenterX, double arcCenterY, double arcStartX, double arcStartY, double arcEndX, double arcEndY, double arcAngleChange, int windingFactor, boolean arcIsFirstShape, GamePolyarcgon.PolyarcgonPointCache nextPoint, boolean isRealIntersection) {
        if (arcIsFirstShape) {
            double forceX = -(arcEndY - arcStartY);
            double forceY = arcEndX - arcStartX;
            this.overlapGradientForceX += (windingFactor) * forceX;
            this.overlapGradientForceY += (windingFactor) * forceY;

            double forceLocationX = (arcStartX + arcEndX) / 2;
            double forceLocationY = (arcStartY + arcEndY) / 2;
            this.overlapGradientTorqueOnFirstShape += (windingFactor) * ((forceLocationX - this.firstShape.getX()) * forceY - (forceLocationY - this.firstShape.getY()) * forceX);
            this.overlapGradientTorqueOnOtherShape += (windingFactor) * (-((forceLocationX - this.otherShape.getX()) * forceY - (forceLocationY - this.otherShape.getY()) * forceX));
        }
        double overlapAreaContribution1 = (arcStartX * arcCenterY - arcStartY * arcCenterX) / 2.0;
        double overlapAreaContribution2 = (arcCenterX * arcEndY - arcCenterY * arcEndX) / 2.0;
        double overlapAreaContributionWedge = arcAngleChange * radiusOfCurvature * radiusOfCurvature / 2.0;
        this.overlapArea += windingFactor * (overlapAreaContribution1 + overlapAreaContribution2 + overlapAreaContributionWedge);
        this.overlapPerimeter += windingFactor * radiusOfCurvature*Math.abs(arcAngleChange);
    }

    public boolean isAlmostZero() {
        double area = firstShape.getArea() + otherShape.getArea();
        return (overlapGradientForceX * overlapGradientForceX + overlapGradientForceY * overlapGradientForceY) / area + (overlapGradientTorqueOnFirstShape * overlapGradientTorqueOnFirstShape + overlapGradientTorqueOnOtherShape * overlapGradientTorqueOnOtherShape) / area / area < 0.000001 * 0.000001;
    }
}
