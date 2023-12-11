package com.github.eztang00.firstandroidgame.ui.game;

interface OverlapHandler {
    void addLineSegmentToOverlap(double startX, double startY, double endX, double endY, int windingFactor, boolean lineSegmentIsFirstShape, GamePolyarcgon.PolyarcgonPointCache nextPoint, boolean isRealIntersection);

    void addArcToOverlap(double radiusOfCurvature, double arcCenterX, double arcCenterY, double arcStartX, double arcStartY, double arcEndX, double arcEndY, double arcAngleChange, int windingFactor, boolean arcIsFirstShape, GamePolyarcgon.PolyarcgonPointCache nextPoint, boolean isRealIntersection);

}
