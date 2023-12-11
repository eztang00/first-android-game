package com.github.eztang00.firstandroidgame.ui.game;

class OverlapAreaIntegralCalculator implements OverlapHandler {
    public final GameShape firstShape;
    public final GameShape otherShape;
    double overlapArea = 0;
    double overlapXAreaIntegral = 0;
    double overlapYAreaIntegral = 0;

    double overlapXSqPlusYSqAreaIntegral = 0;

    public OverlapAreaIntegralCalculator(GameShape firstShape, GameShape otherShape) {
        this.firstShape = firstShape;
        this.otherShape = otherShape;
    }

    public void addLineSegmentToOverlap(double startX, double startY, double endX, double endY, int windingFactor, boolean lineSegmentIsFirstShape, GamePolyarcgon.PolyarcgonPointCache nextPoint, boolean isRealIntersection) {
        double overlapAreaContribution = (startX * endY - startY * endX) / 2.0;
        this.overlapArea += windingFactor * overlapAreaContribution;
        this.overlapXAreaIntegral += windingFactor * (startX + endX) * overlapAreaContribution / 3.0;
        this.overlapYAreaIntegral += windingFactor * (startY + endY) * overlapAreaContribution / 3.0;
        this.overlapXSqPlusYSqAreaIntegral += windingFactor * (startX * startX + startX * endX + endX * endX) * overlapAreaContribution / 6.0;
        this.overlapXSqPlusYSqAreaIntegral += windingFactor * (startY * startY + startY * endY + endY * endY) * overlapAreaContribution / 6.0;
    }

    public void addArcToOverlap(double radiusOfCurvature, double arcCenterX, double arcCenterY, double arcStartX, double arcStartY, double arcEndX, double arcEndY, double arcAngleChange, int windingFactor, boolean arcIsFirstShape, GamePolyarcgon.PolyarcgonPointCache nextPoint, boolean isRealIntersection) {
        double overlapAreaContribution1 = (arcStartX * arcCenterY - arcStartY * arcCenterX) / 2.0;
        double overlapAreaContribution2 = (arcCenterX * arcEndY - arcCenterY * arcEndX) / 2.0;
        double overlapAreaContributionWedge = arcAngleChange * radiusOfCurvature * radiusOfCurvature / 2.0;
        this.overlapArea += windingFactor * (overlapAreaContribution1 + overlapAreaContribution2 + overlapAreaContributionWedge);

        this.overlapXAreaIntegral += windingFactor * (arcStartX + arcCenterX) * overlapAreaContribution1 / 3.0;
        this.overlapXAreaIntegral += windingFactor * (arcCenterX + arcEndX) * overlapAreaContribution2 / 3.0;
        double overlapXAreaIntegralContributionWedge = (arcEndY - arcStartY) * radiusOfCurvature * radiusOfCurvature / 3.0;
        this.overlapXAreaIntegral += windingFactor * (overlapXAreaIntegralContributionWedge + arcCenterX * overlapAreaContributionWedge);

        this.overlapYAreaIntegral += windingFactor * (arcStartY + arcCenterY) * overlapAreaContribution1 / 3.0;
        this.overlapYAreaIntegral += windingFactor * (arcCenterY + arcEndY) * overlapAreaContribution2 / 3.0;
        double overlapYAreaIntegralContributionWedge = -(arcEndX - arcStartX) * radiusOfCurvature * radiusOfCurvature / 3.0;
        this.overlapYAreaIntegral += windingFactor * (overlapYAreaIntegralContributionWedge + arcCenterY * overlapAreaContributionWedge);

        double overlapWedgeCenterOfAreaRelativeToCenterX = overlapXAreaIntegralContributionWedge / overlapAreaContributionWedge;
        double overlapWedgeCenterOfAreaRelativeToCenterY = overlapYAreaIntegralContributionWedge / overlapAreaContributionWedge;
        double overlapWedgeCenterOfAreaX = overlapWedgeCenterOfAreaRelativeToCenterX + arcCenterX;
        double overlapWedgeCenterOfAreaY = overlapWedgeCenterOfAreaRelativeToCenterY + arcCenterY;

        this.overlapXSqPlusYSqAreaIntegral += windingFactor * (arcStartX * arcStartX + arcStartX * arcCenterX + arcCenterX * arcCenterX) * overlapAreaContribution1 / 6.0;
        this.overlapXSqPlusYSqAreaIntegral += windingFactor * (arcCenterX * arcCenterX + arcCenterX * arcEndX + arcEndX * arcEndX) * overlapAreaContribution2 / 6.0;
        this.overlapXSqPlusYSqAreaIntegral += windingFactor * (arcStartY * arcStartY + arcStartY * arcCenterY + arcCenterY * arcCenterY) * overlapAreaContribution1 / 6.0;
        this.overlapXSqPlusYSqAreaIntegral += windingFactor * (arcCenterY * arcCenterY + arcCenterY * arcEndY + arcEndY * arcEndY) * overlapAreaContribution2 / 6.0;
        //use parallel axis theorem for second moment of area, see https://en.wikipedia.org/wiki/Parallel_axis_theorem
        this.overlapXSqPlusYSqAreaIntegral += windingFactor * overlapAreaContributionWedge
                * (radiusOfCurvature * radiusOfCurvature / 2.0
                + (overlapWedgeCenterOfAreaX * overlapWedgeCenterOfAreaX + overlapWedgeCenterOfAreaY * overlapWedgeCenterOfAreaY)
                - (overlapWedgeCenterOfAreaRelativeToCenterX * overlapWedgeCenterOfAreaRelativeToCenterX + overlapWedgeCenterOfAreaRelativeToCenterY * overlapWedgeCenterOfAreaRelativeToCenterY));
    }

    public boolean isAlmostZero() {
        return overlapArea / (firstShape.getArea() + otherShape.getArea()) < 0.000001;
    }
}
