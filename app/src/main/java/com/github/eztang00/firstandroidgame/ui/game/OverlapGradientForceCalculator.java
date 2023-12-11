package com.github.eztang00.firstandroidgame.ui.game;

class OverlapGradientForceCalculator implements OverlapHandler {
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
        this.overlapPerimeter += Math.sqrt(forceX*forceX + forceY*forceY);
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
        this.overlapPerimeter += radiusOfCurvature*Math.abs(arcAngleChange);
    }

    public boolean isAlmostZero() {
        double area = firstShape.getArea() + otherShape.getArea();
        return (overlapGradientForceX * overlapGradientForceX + overlapGradientForceY * overlapGradientForceY) / area + (overlapGradientTorqueOnFirstShape * overlapGradientTorqueOnFirstShape + overlapGradientTorqueOnOtherShape * overlapGradientTorqueOnOtherShape) / area / area < 0.000001 * 0.000001;
    }
}
