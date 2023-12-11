package com.github.eztang00.firstandroidgame.gamephysics;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * A generalization of a polygon, which can have arcs instead of just straight edges.
 * It's more limited than Android's Path class, but has methods for collision physics,
 * center of mass, etc.
 */
public class GamePolyarcgon implements GameShape {
    public double x;
    public double y;
    public double boundingRadius;
    public double rotationRadians;

    public final Object additionalAttributes;
    private double density;
    private double mass;
    private double momentOfInertia;

    private final PolyarcgonPoint[] templatePoints;
    private final Path templatePathForDrawing;
    final Cache<PolyarcgonPointCache[]> pointsCache;
    private final Cache<Path> pathForDrawingCache;

    public GamePolyarcgon(GamePolyarcgonBuilder gamePolyarcgonBuilder) {

        this.additionalAttributes = gamePolyarcgonBuilder.additionalAttributes;
        density = gamePolyarcgonBuilder.density;
        templatePoints = gamePolyarcgonBuilder.generatePoints().toArray(new PolyarcgonPoint[0]);

        PolyarcgonPointCache[] pointsCachePoints = new PolyarcgonPointCache[templatePoints.length];
        pointsCache = new Cache<>(pointsCachePoints, ((newX, newY, newRotation) -> {
            double cosRotation = Math.cos(rotationRadians);
            double sinRotation = Math.sin(rotationRadians);
            for (PolyarcgonPointCache pointCache : pointsCachePoints) {
                pointCache.updatePositionCache(x, y, cosRotation, sinRotation);
            }
            PolyarcgonPointCache lastPointCache = pointsCachePoints[pointsCachePoints.length - 1];
            for (PolyarcgonPointCache pointCache : pointsCachePoints) {
                pointCache.updateLineOrArcCache(lastPointCache);
                lastPointCache = pointCache;
            }
        }));
        for (int i = 0; i < templatePoints.length; i++) {
            if (templatePoints[i].isAlmostStraight()) {
                pointsCachePoints[i] = new PolyarcgonStraightPointCache(templatePoints[i]);
            } else {
                pointsCachePoints[i] = new PolyarcgonArcedPointCache(templatePoints[i]);
            }
        }

        initiateXYMassMomentOfInertiaBoundingRadiusAndTemplatePoints(gamePolyarcgonBuilder.centerOfMassX, gamePolyarcgonBuilder.centerOfMassY, gamePolyarcgonBuilder.centerOfMassIsRelativeToDefaultCenterOfMass); //needs to be after pointsCache exists

        templatePathForDrawing = initiatePath(); //needs to be after pointsCache exists
        Path pathForDrawingCachePath = new Path();
        pathForDrawingCache = new Cache<>(pathForDrawingCachePath, ((newX, newY, newRotation) -> {
            Matrix matrix = new Matrix();
            matrix.setTranslate((float) newX, (float) newY);
            matrix.preRotate((float) (newRotation * 180.0 / Math.PI));
            pathForDrawingCachePath.set(templatePathForDrawing);
            pathForDrawingCachePath.transform(matrix);
        }));
    }

    private void initiateXYMassMomentOfInertiaBoundingRadiusAndTemplatePoints(double centerOfMassX, double centerOfMassY, boolean centerOfMassIsRelativeToDefaultCenterOfMass) {
        OverlapAreaIntegralCalculator selfOverlap = new OverlapAreaIntegralCalculator(this, this);

        PolyarcgonPointCache[] gotPointsCache = pointsCache.get();

        PolyarcgonPointCache lastPoint = gotPointsCache[gotPointsCache.length - 1];
        for (PolyarcgonPointCache nextPoint : gotPointsCache) {
            if (!nextPoint.getNonCachePoint().isMoveToWithoutLineEtc) {
                if (nextPoint instanceof PolyarcgonStraightPointCache) {
                    selfOverlap.addLineSegmentToOverlap(lastPoint.getX(), lastPoint.getY(), nextPoint.getX(), nextPoint.getY(), 1, false, nextPoint, true);
                } else if (nextPoint instanceof PolyarcgonArcedPointCache) {
                    PolyarcgonArcedPointCache nextPointArc = (PolyarcgonArcedPointCache) nextPoint;
                    selfOverlap.addArcToOverlap(nextPointArc.radiusOfCurvature, nextPointArc.arcCenterX, nextPointArc.arcCenterY, lastPoint.getX(), lastPoint.getY(), nextPointArc.x, nextPointArc.y, nextPointArc.nonCachePoint.arcAngleChange, 1, false, nextPoint, true);
                }
            }
            lastPoint = nextPoint;
        }

        double defaultCenterOfMassX = selfOverlap.overlapXAreaIntegral / selfOverlap.overlapArea;
        double defaultCenterOfMassY = selfOverlap.overlapYAreaIntegral / selfOverlap.overlapArea;

        if (centerOfMassIsRelativeToDefaultCenterOfMass) {
            x = defaultCenterOfMassX + centerOfMassX;
            y = defaultCenterOfMassY + centerOfMassY;
        } else {
            x = centerOfMassX;
            y = centerOfMassY;
        }

        mass = density * selfOverlap.overlapArea;

        // don't worry about whether x and y equal default center of mass when calculating moment of inertia
        momentOfInertia = density * (selfOverlap.overlapXSqPlusYSqAreaIntegral - (defaultCenterOfMassX * defaultCenterOfMassX + defaultCenterOfMassY * defaultCenterOfMassY) * selfOverlap.overlapArea);

        boundingRadius = 0;
        for (PolyarcgonPointCache point : gotPointsCache) {
            double distance = 0;
            if (point instanceof PolyarcgonStraightPointCache) {
                PolyarcgonStraightPointCache straightPoint = (PolyarcgonStraightPointCache) point;
                straightPoint.nonCachePoint.x = straightPoint.x - x;
                straightPoint.nonCachePoint.y = straightPoint.y - y;

                distance = Math.sqrt(straightPoint.nonCachePoint.x * straightPoint.nonCachePoint.x + straightPoint.nonCachePoint.y * straightPoint.nonCachePoint.y);
            } else if (point instanceof PolyarcgonArcedPointCache) {
                PolyarcgonArcedPointCache arcedPoint = (PolyarcgonArcedPointCache) point;
                arcedPoint.nonCachePoint.x = arcedPoint.x - x;
                arcedPoint.nonCachePoint.y = arcedPoint.y - y;
                if (arcedPoint.nonCachePoint.isMoveToWithoutLineEtc) {
                    distance = Math.sqrt(arcedPoint.nonCachePoint.x * arcedPoint.nonCachePoint.x + arcedPoint.nonCachePoint.y * arcedPoint.nonCachePoint.y);
                } else {
                    double relArcCenterX = arcedPoint.arcCenterX - x;
                    double relArcCenterY = arcedPoint.arcCenterY - y;
                    if (relArcCenterX == 0 && relArcCenterY == 0) { //i.e. this is a circle
                        distance = arcedPoint.radiusOfCurvature;
                    } else {
                        double potentialDistance = arcedPoint.radiusOfCurvature + Math.sqrt(relArcCenterX * relArcCenterX + relArcCenterY * relArcCenterY);
                        if (potentialDistance > boundingRadius) {
                            double potentialDistanceAngle = Math.atan2(arcedPoint.arcCenterY - y, arcedPoint.arcCenterX - x);
                            boolean arcPassesPotentialDistanceAngle = true;
                            if (arcedPoint.nonCachePoint.arcAngleChange > 0) {
                                if ((potentialDistanceAngle - arcedPoint.startAngle + 10 * Math.PI) % (2 * Math.PI) >= arcedPoint.nonCachePoint.arcAngleChange) {
                                    arcPassesPotentialDistanceAngle = false;
                                }
                            } else {
                                if ((potentialDistanceAngle - arcedPoint.startAngle - 10 * Math.PI) % (2 * Math.PI) <= arcedPoint.nonCachePoint.arcAngleChange) {
                                    arcPassesPotentialDistanceAngle = false;
                                }
                            }
                            if (arcPassesPotentialDistanceAngle) {
                                boundingRadius = potentialDistance;
                            } else {
                                distance = Math.sqrt(arcedPoint.nonCachePoint.x * arcedPoint.nonCachePoint.x + arcedPoint.nonCachePoint.y * arcedPoint.nonCachePoint.y);
                            }
                        }
                    }
                }
            }
            if (distance > boundingRadius) {
                boundingRadius = distance;
            }
        }
    }

    class Cache<T> {
        private double xWhenLastUpdatedCache = Double.NaN;
        private double yWhenLastUpdatedCache = Double.NaN;
        private double rotationWhenLastUpdatedCache = Double.NaN;
        private final T cache;
        private final CacheUpdateFunction cacheUpdateFunction;

        Cache(T cache, CacheUpdateFunction cacheUpdateFunction) {
            this.cache = cache;
            this.cacheUpdateFunction = cacheUpdateFunction;
        }

        public T get() {
            if (xWhenLastUpdatedCache != x || yWhenLastUpdatedCache != y || rotationWhenLastUpdatedCache != rotationRadians) {
                cacheUpdateFunction.updateCache(x, y, rotationRadians);
                xWhenLastUpdatedCache = x;
                yWhenLastUpdatedCache = y;
                rotationWhenLastUpdatedCache = rotationRadians;
            }
            return cache;
        }
    }

    interface CacheUpdateFunction {
        void updateCache(double newX, double newY, double newRotation);
    }

    public PolyarcgonPoint[] getTemplatePoints() {
        return templatePoints;
    }

    public void collision(GameShape otherShape, boolean isThisMovable, boolean isOtherShapeMovable, boolean thisIsFirstShape, OverlapCalculator overlapCalculator) {
        if (otherShape instanceof GamePolyarcgon) {
            if (thisIsFirstShape) {
                collision(this, (GamePolyarcgon) otherShape, overlapCalculator);
            } else {
                collision((GamePolyarcgon) otherShape, this, overlapCalculator);
            }
        } else {
            otherShape.collision(this, isOtherShapeMovable, isThisMovable, !thisIsFirstShape, overlapCalculator);
        }
    }

    public Path getPathForDrawing() {
        return pathForDrawingCache.get();
    }

    @Override
    public void draw(Canvas canvas) {
        Path path = pathForDrawingCache.get();
        path.setFillType(Path.FillType.EVEN_ODD);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(path, paint);
    }

    private Path initiatePath() {
        Path path = new Path();
        PolyarcgonPointCache[] gotPointsCache = pointsCache.get();
        for (int i = 0; i <= gotPointsCache.length; i++) {
            PolyarcgonPointCache pointI = gotPointsCache[i % gotPointsCache.length];
            if (i == 0 || pointI.getNonCachePoint().isMoveToWithoutLineEtc) {
                if (i != gotPointsCache.length) {
                    path.moveTo((float) pointI.getNonCachePoint().x, (float) pointI.getNonCachePoint().y);
                }
            } else {
                if (pointI instanceof PolyarcgonStraightPointCache) {
                    PolyarcgonPointCache pointI1 = gotPointsCache[(i + 1) % gotPointsCache.length];
                    if (!(pointI1 instanceof PolyarcgonArcedPointCache && i != gotPointsCache.length)) {
                        path.lineTo((float) pointI.getNonCachePoint().x, (float) pointI.getNonCachePoint().y);
                    }
                } else if (pointI instanceof PolyarcgonArcedPointCache) {
                    PolyarcgonArcedPointCache pointIArc = (PolyarcgonArcedPointCache) pointI;
                    path.arcTo((float) (pointIArc.arcCenterX - x - pointIArc.radiusOfCurvature), (float) (pointIArc.arcCenterY - y - pointIArc.radiusOfCurvature), (float) (pointIArc.arcCenterX - x + pointIArc.radiusOfCurvature), (float) (pointIArc.arcCenterY - y + pointIArc.radiusOfCurvature), (float) (pointIArc.startAngle * 180.0 / Math.PI), (float) (pointIArc.nonCachePoint.arcAngleChange * 180.0 / Math.PI), false);
                }
            }
        }
//        path.close(); not necessary
        return path;
    }

    @Override
    public void receiveForce(ForceAndTorque collision) {
//        if (this == collision.shape1) {
        //because inertia-less, don't have velocity or angular speed, just add acceleration to position instead of velocity and likewise for angular acceleration

        x += collision.forceActingOnShapeX / mass;
        y += collision.forceActingOnShapeY / mass;

        rotationRadians += collision.torqueActingOnShape / momentOfInertia;
//        }
    }
//    @Override
//    public void receiveForce(double forceX, double forceY, double torque) {
//        x += forceX / mass;
//        y += forceY / mass;
//        rotationRadians += torque / momentOfInertia;
//    }

    @Override
    public double getMass() {
        return mass;
    }

    @Override
    public double getArea() {
        return mass / density;
    }

    @Override
    public double getBoundingRadius() {
        return boundingRadius;
    }

    @Override
    public double getX() {
        return x;
    }

    @Override
    public double getY() {
        return y;
    }

    @Override
    public double getMomentOfInertia() {
        return momentOfInertia;
    }

    @Override
    public void setPos(double x, double y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public void setRotation(double rotationRadians) {
        this.rotationRadians = rotationRadians;
    }

    public static void collision(GamePolyarcgon firstShape, GamePolyarcgon otherShape, OverlapCalculator handler) {
        double distSq = (firstShape.x - otherShape.x) * (firstShape.x - otherShape.x) + (firstShape.y - otherShape.y) * (firstShape.y - otherShape.y);
        if (Math.sqrt(distSq) < firstShape.boundingRadius + otherShape.boundingRadius) {
            //maybe at some point add ability to check if bounding radius of one shape collides with another shape at all (mostly for a wall)?
            //though won't help much

            /*
            We use the shoelace formula (see https://en.wikipedia.org/wiki/Shoelace_formula)
            To calculate the area and area integrals
             */

            PolyarcgonPointCache[] points = firstShape.pointsCache.get();
            PolyarcgonPointCache[] otherPoints = otherShape.pointsCache.get();

            PolyarcgonPointCache lastPoint = points[points.length - 1];
            for (PolyarcgonPointCache nextPoint : points) {
                if (!nextPoint.getNonCachePoint().isMoveToWithoutLineEtc) { // make sure the line/arc from last point to next point exists
//                double pointToPointX = nextPoint.x - lastPoint.x;
//                double pointToPointY = nextPoint.y - lastPoint.y;
//                double pointToPointDistance = Math.sqrt(pointToPointX*pointToPointX + pointToPointY*pointToPointY);


                    PolyarcgonPointCache otherLastPoint = otherPoints[otherPoints.length - 1];
                    for (PolyarcgonPointCache otherNextPoint : otherPoints) {
                        if (!otherNextPoint.getNonCachePoint().isMoveToWithoutLineEtc) {
//                        double otherPointToPointX = otherNextPoint.x - otherLastPoint.x;
//                        double otherPointToPointY = otherNextPoint.y - otherLastPoint.y;
//                    double otherPointToPointDistance = Math.sqrt(otherPointToPointX*otherPointToPointX + otherPointToPointY*otherPointToPointY);


                            if (nextPoint instanceof PolyarcgonStraightPointCache) {
                                if (otherNextPoint instanceof PolyarcgonStraightPointCache) {
                                    addPotentialLineSegmentIntersectionWithLineSegmentToOverlap(handler, lastPoint, (PolyarcgonStraightPointCache) nextPoint, otherLastPoint, (PolyarcgonStraightPointCache) otherNextPoint, 0);
                                } else if (otherNextPoint instanceof PolyarcgonArcedPointCache) {
                                    addPotentialLineSegmentIntersectionWithArcToOverlap(handler, lastPoint, (PolyarcgonStraightPointCache) nextPoint, otherLastPoint, (PolyarcgonArcedPointCache) otherNextPoint, true, 0);
                                }
                            } else if (nextPoint instanceof PolyarcgonArcedPointCache) {
                                if (otherNextPoint instanceof PolyarcgonStraightPointCache) {
                                    addPotentialLineSegmentIntersectionWithArcToOverlap(handler, otherLastPoint, (PolyarcgonStraightPointCache) otherNextPoint, lastPoint, (PolyarcgonArcedPointCache) nextPoint, false, 0);
                                } else if (otherNextPoint instanceof PolyarcgonArcedPointCache) {
                                    addPotentialArcIntersectionWithArcToOverlap(handler, lastPoint, (PolyarcgonArcedPointCache) nextPoint, otherLastPoint, (PolyarcgonArcedPointCache) otherNextPoint, 0);
                                }
                            }
                        }
                        otherLastPoint = otherNextPoint;
                    }
                }
                lastPoint = nextPoint;
            }
        }
    }

    public static void addPotentialLineSegmentIntersectionWithLineSegmentToOverlap(OverlapCalculator overlap, PolyarcgonPointCache lastPoint, PolyarcgonStraightPointCache nextPoint, PolyarcgonPointCache otherLastPoint, PolyarcgonStraightPointCache otherNextPoint, double maxRoundingErrorForNearCollisions) {
        double edgeCrossProduct = nextPoint.pointToPointX * otherNextPoint.pointToPointY - nextPoint.pointToPointY * otherNextPoint.pointToPointX;
        double lastPointX = lastPoint.getX();
        double lastPointY = lastPoint.getY();
        double otherLastPointX = otherLastPoint.getX();
        double otherLastPointY = otherLastPoint.getY();
        double edgeDistanceCrossProduct = otherNextPoint.pointToPointX * (lastPointY - otherLastPointY) - otherNextPoint.pointToPointY * (lastPointX - otherLastPointX);
        double otherEdgeDistanceCrossProduct = nextPoint.pointToPointX * (lastPointY - otherLastPointY) - nextPoint.pointToPointY * (lastPointX - otherLastPointX);

        //check whether the two edges are parallel
        if ((Math.abs(edgeDistanceCrossProduct) + Math.abs(otherEdgeDistanceCrossProduct)) / 1000000 >= Math.abs(edgeCrossProduct)) {
            //almost parallel, cannot find intercept without dividing by zero
            //so assume neither line segment intersects the other line
        } else {
            double newInterceptRelativeToPointToPoint = edgeDistanceCrossProduct / edgeCrossProduct;

            boolean lineSegmentIntersectsOtherRay = true;
            boolean otherLineSegmentIntersectsRay = true;
            if (newInterceptRelativeToPointToPoint <= 0 - maxRoundingErrorForNearCollisions) {
//                                    newInterceptRelativeToPointToPoint = 0;
                lineSegmentIntersectsOtherRay = false;
                otherLineSegmentIntersectsRay = false;
            } else if (newInterceptRelativeToPointToPoint >= 1 + maxRoundingErrorForNearCollisions) {
                newInterceptRelativeToPointToPoint = 1;
                lineSegmentIntersectsOtherRay = false;
            }

            double newInterceptRelativeToOtherPointToPoint = otherEdgeDistanceCrossProduct / edgeCrossProduct;
            if (newInterceptRelativeToOtherPointToPoint <= 0 - maxRoundingErrorForNearCollisions) {
//                                    newInterceptRelativeToOtherPointToPoint = 0;
                otherLineSegmentIntersectsRay = false;
                lineSegmentIntersectsOtherRay = false;
            } else if (newInterceptRelativeToOtherPointToPoint >= 1 + maxRoundingErrorForNearCollisions) {
                newInterceptRelativeToOtherPointToPoint = 1;
                otherLineSegmentIntersectsRay = false;
            }

            if (lineSegmentIntersectsOtherRay || otherLineSegmentIntersectsRay) {
                double newInterceptX = 0;
                double newInterceptY = 0;
                if (lineSegmentIntersectsOtherRay && otherLineSegmentIntersectsRay) {
                    newInterceptX = lastPointX + nextPoint.pointToPointX * newInterceptRelativeToPointToPoint;
                    newInterceptY = lastPointY + nextPoint.pointToPointY * newInterceptRelativeToPointToPoint;
                } else if (lineSegmentIntersectsOtherRay) {
                    newInterceptX = otherNextPoint.x;
                    newInterceptY = otherNextPoint.y;
                } else if (otherLineSegmentIntersectsRay) {
                    newInterceptX = nextPoint.x;
                    newInterceptY = nextPoint.y;
                }
                int windingFactor;
                if (edgeCrossProduct > 0) {
                    windingFactor = 1;
                } else {
                    windingFactor = -1;
                }
                if (otherLineSegmentIntersectsRay) {
                    overlap.addLineSegmentToOverlap(lastPointX, lastPointY, newInterceptX, newInterceptY, windingFactor, true, nextPoint, lineSegmentIntersectsOtherRay);
                }
                if (lineSegmentIntersectsOtherRay) {
                    overlap.addLineSegmentToOverlap(otherLastPointX, otherLastPointY, newInterceptX, newInterceptY, -windingFactor, false, otherNextPoint, otherLineSegmentIntersectsRay);
                }
            }
        }
    }

    public static void addPotentialLineSegmentIntersectionWithArcToOverlap(OverlapCalculator overlap, PolyarcgonPointCache lineSegmentLastPoint, PolyarcgonStraightPointCache lineSegmentNextPoint, PolyarcgonPointCache arcLastPoint, PolyarcgonArcedPointCache arcNextPoint, boolean lineSegmentIsFirstShape, double maxRoundingErrorForNearCollisions) {
        double pointToPointDistance = Math.sqrt(lineSegmentNextPoint.pointToPointX * lineSegmentNextPoint.pointToPointX + lineSegmentNextPoint.pointToPointY * lineSegmentNextPoint.pointToPointY);

        double lineSegmentLastPointX = lineSegmentLastPoint.getX();
        double lineSegmentLastPointY = lineSegmentLastPoint.getY();
        double arcLastPointX = arcLastPoint.getX();
        double arcLastPointY = arcLastPoint.getY();

        double pointToArcCenterX = arcNextPoint.arcCenterX - lineSegmentLastPointX;
        double pointToArcCenterY = arcNextPoint.arcCenterY - lineSegmentLastPointY;

        double lastPointAngleFromArcCenter = Math.atan2(lineSegmentLastPointY - arcNextPoint.arcCenterY, lineSegmentLastPointX - arcNextPoint.arcCenterX);
        double nextPointAngleFromArcCenter = Math.atan2(lineSegmentNextPoint.y - arcNextPoint.arcCenterY, lineSegmentNextPoint.x - arcNextPoint.arcCenterX);

        double crossProduct = lineSegmentNextPoint.pointToPointX * pointToArcCenterY - lineSegmentNextPoint.pointToPointY * pointToArcCenterX;
        double centerPerpendicularSignedDistance = crossProduct / pointToPointDistance;

        boolean lineSegmentIntersectsArcRay = false; //this boolean, unlike others, is false until proven true because there are multiple ways for it to be true
        boolean lineSegmentIntersectsCircle1 = true;
        boolean lineSegmentIntersectsCircle2 = true;
        boolean lineSegmentFullyInsideCircle = true;

        if (maxRoundingErrorForNearCollisions > 0) {
            if (Math.abs(centerPerpendicularSignedDistance) >= arcNextPoint.radiusOfCurvature && Math.abs(centerPerpendicularSignedDistance) < arcNextPoint.radiusOfCurvature + maxRoundingErrorForNearCollisions) {
                centerPerpendicularSignedDistance -= Math.copySign(maxRoundingErrorForNearCollisions, centerPerpendicularSignedDistance);
            }
        }

        if (Math.abs(centerPerpendicularSignedDistance) >= arcNextPoint.radiusOfCurvature) {
            //line does not go through circle at all
            lineSegmentIntersectsCircle1 = false;
            lineSegmentIntersectsCircle2 = false;
            lineSegmentFullyInsideCircle = false;
        } else {
            double dotProduct = pointToArcCenterX * lineSegmentNextPoint.pointToPointX + pointToArcCenterY * lineSegmentNextPoint.pointToPointY;
            double centerParallelDistance = dotProduct / pointToPointDistance;
            double interceptParallelDistance = Math.sqrt(arcNextPoint.radiusOfCurvature * arcNextPoint.radiusOfCurvature - centerPerpendicularSignedDistance * centerPerpendicularSignedDistance);

            boolean lineSegmentIntersectsArc1 = true;
            boolean lineSegmentIntersectsArc2 = true;
            boolean arc1IntersectsRay = true;
            boolean arc2IntersectsRay = true;

            double intercept1Position = centerParallelDistance - interceptParallelDistance;
            double cappedIntercept1Position;
            if (intercept1Position <= 0 - maxRoundingErrorForNearCollisions) {
                lineSegmentIntersectsArc1 = false;
                lineSegmentIntersectsCircle1 = false;
                arc1IntersectsRay = false;
                cappedIntercept1Position = 0;
            } else if (intercept1Position >= pointToPointDistance + maxRoundingErrorForNearCollisions) {
                lineSegmentIntersectsArc1 = false;
                lineSegmentIntersectsArc2 = false;
                lineSegmentIntersectsCircle1 = false;
                lineSegmentIntersectsCircle2 = false;
                lineSegmentFullyInsideCircle = false;
                cappedIntercept1Position = pointToPointDistance;
            } else {
                lineSegmentFullyInsideCircle = false;
                cappedIntercept1Position = intercept1Position;
            }

            double intercept2Position = centerParallelDistance + interceptParallelDistance;
            double cappedIntercept2Position;
            if (intercept2Position <= 0 - maxRoundingErrorForNearCollisions) {
                lineSegmentIntersectsArc1 = false;
                lineSegmentIntersectsArc2 = false;
                lineSegmentIntersectsCircle1 = false;
                lineSegmentIntersectsCircle2 = false;
                lineSegmentFullyInsideCircle = false;
                arc1IntersectsRay = false;
                arc2IntersectsRay = false;
                cappedIntercept2Position = 0;
            } else if (intercept2Position >= pointToPointDistance + maxRoundingErrorForNearCollisions) {
                lineSegmentIntersectsArc2 = false;
                lineSegmentIntersectsCircle2 = false;
                cappedIntercept2Position = pointToPointDistance;
            } else {
                lineSegmentFullyInsideCircle = false;
                cappedIntercept2Position = intercept2Position;
            }

            double cosLineAngle = lineSegmentNextPoint.pointToPointX / pointToPointDistance;
            double sinLineAngle = lineSegmentNextPoint.pointToPointY / pointToPointDistance;

            double intercept1AngleFromArcCenter = 0;
            double intercept1AngleFromArcCenterRelativeToArcStart = 0;

            double cappedIntercept1X = 0;
            double cappedIntercept1Y = 0;
            if (lineSegmentIntersectsArc1 || arc1IntersectsRay) {
                double intercept1X = lineSegmentLastPointX + cosLineAngle * intercept1Position;
                double intercept1Y = lineSegmentLastPointY + sinLineAngle * intercept1Position;

                intercept1AngleFromArcCenter = Math.atan2(intercept1Y - arcNextPoint.arcCenterY, intercept1X - arcNextPoint.arcCenterX);
                if (arcNextPoint.nonCachePoint.arcAngleChange > 0) {
                    intercept1AngleFromArcCenterRelativeToArcStart = (intercept1AngleFromArcCenter - arcNextPoint.startAngle + maxRoundingErrorForNearCollisions + 10 * Math.PI) % (2 * Math.PI) - maxRoundingErrorForNearCollisions;
                    if (intercept1AngleFromArcCenterRelativeToArcStart >= arcNextPoint.nonCachePoint.arcAngleChange + maxRoundingErrorForNearCollisions) {
                        lineSegmentIntersectsArc1 = false;
                        arc1IntersectsRay = false;
                    }
                } else {
                    intercept1AngleFromArcCenterRelativeToArcStart = (intercept1AngleFromArcCenter - arcNextPoint.startAngle - maxRoundingErrorForNearCollisions - 10 * Math.PI) % (2 * Math.PI) + maxRoundingErrorForNearCollisions;
                    if (intercept1AngleFromArcCenterRelativeToArcStart <= arcNextPoint.nonCachePoint.arcAngleChange - maxRoundingErrorForNearCollisions) {
                        lineSegmentIntersectsArc1 = false;
                        arc1IntersectsRay = false;
                    }
                }
                if (lineSegmentIntersectsArc1) {
                    cappedIntercept1X = intercept1X;
                    cappedIntercept1Y = intercept1Y;
                } else if (arc1IntersectsRay) {
                    cappedIntercept1X = lineSegmentLastPointX + cosLineAngle * cappedIntercept1Position;
                    cappedIntercept1Y = lineSegmentLastPointY + sinLineAngle * cappedIntercept1Position;
                }
            }

            double intercept2AngleFromArcCenter = 0;
            double intercept2AngleFromArcCenterRelativeToArcStart = 0;

            double cappedIntercept2X = 0;
            double cappedIntercept2Y = 0;
            if (lineSegmentIntersectsArc2 || arc2IntersectsRay) {
                double intercept2X = lineSegmentLastPointX + cosLineAngle * intercept2Position;
                double intercept2Y = lineSegmentLastPointY + sinLineAngle * intercept2Position;

                intercept2AngleFromArcCenter = Math.atan2(intercept2Y - arcNextPoint.arcCenterY, intercept2X - arcNextPoint.arcCenterX);
                if (arcNextPoint.nonCachePoint.arcAngleChange > 0) {
                    intercept2AngleFromArcCenterRelativeToArcStart = (intercept2AngleFromArcCenter - arcNextPoint.startAngle + maxRoundingErrorForNearCollisions + 10 * Math.PI) % (2 * Math.PI) - maxRoundingErrorForNearCollisions;
                    if (intercept2AngleFromArcCenterRelativeToArcStart >= arcNextPoint.nonCachePoint.arcAngleChange + maxRoundingErrorForNearCollisions) {
                        lineSegmentIntersectsArc2 = false;
                        arc2IntersectsRay = false;
                    }
                } else {
                    intercept2AngleFromArcCenterRelativeToArcStart = (intercept2AngleFromArcCenter - arcNextPoint.startAngle - maxRoundingErrorForNearCollisions - 10 * Math.PI) % (2 * Math.PI) + maxRoundingErrorForNearCollisions;
                    if (intercept2AngleFromArcCenterRelativeToArcStart <= arcNextPoint.nonCachePoint.arcAngleChange - maxRoundingErrorForNearCollisions) {
                        lineSegmentIntersectsArc2 = false;
                        arc2IntersectsRay = false;
                    }
                }
                if (lineSegmentIntersectsArc2) {
                    cappedIntercept2X = intercept2X;
                    cappedIntercept2Y = intercept2Y;
                } else if (arc2IntersectsRay) {
                    cappedIntercept2X = lineSegmentLastPointX + cosLineAngle * cappedIntercept2Position;
                    cappedIntercept2Y = lineSegmentLastPointY + sinLineAngle * cappedIntercept2Position;
                }
            }

            if (lineSegmentIntersectsCircle1) {
                if (centerPerpendicularSignedDistance > 0) {
                    if ((intercept1AngleFromArcCenter - lastPointAngleFromArcCenter + 10 * Math.PI) % (2 * Math.PI) > (arcNextPoint.endAngle - lastPointAngleFromArcCenter + 10 * Math.PI) % (2 * Math.PI)) {
                        lineSegmentIntersectsArcRay = true;
                    }
                } else {
                    if ((-(intercept1AngleFromArcCenter - lastPointAngleFromArcCenter) + 10 * Math.PI) % (2 * Math.PI) > (-(arcNextPoint.endAngle - lastPointAngleFromArcCenter) + 10 * Math.PI) % (2 * Math.PI)) {
                        lineSegmentIntersectsArcRay = true;
                    }
                }
            }
            if (lineSegmentIntersectsCircle2) {
                if (centerPerpendicularSignedDistance > 0) {
                    if ((nextPointAngleFromArcCenter - intercept2AngleFromArcCenter + 10 * Math.PI) % (2 * Math.PI) > (arcNextPoint.endAngle - intercept2AngleFromArcCenter + 10 * Math.PI) % (2 * Math.PI)) {
                        lineSegmentIntersectsArcRay = true;
                    }
                } else {
                    if ((-(nextPointAngleFromArcCenter - intercept2AngleFromArcCenter) + 10 * Math.PI) % (2 * Math.PI) > (-(arcNextPoint.endAngle - intercept2AngleFromArcCenter) + 10 * Math.PI) % (2 * Math.PI)) {
                        lineSegmentIntersectsArcRay = true;
                    }
                }
            }

            int windingFactor;
            if (arcNextPoint.nonCachePoint.arcAngleChange > 0) {
                windingFactor = 1;
            } else {
                windingFactor = -1;
            }

            if (arc1IntersectsRay) {
                overlap.addLineSegmentToOverlap(lineSegmentLastPointX, lineSegmentLastPointY, cappedIntercept1X, cappedIntercept1Y, -windingFactor, lineSegmentIsFirstShape, lineSegmentNextPoint, lineSegmentIntersectsArc1);
            }
            if (arc2IntersectsRay) {
                overlap.addLineSegmentToOverlap(lineSegmentLastPointX, lineSegmentLastPointY, cappedIntercept2X, cappedIntercept2Y, windingFactor, lineSegmentIsFirstShape, lineSegmentNextPoint, lineSegmentIntersectsArc2);
            }
            if (lineSegmentIntersectsArc1) {
                overlap.addArcToOverlap(arcNextPoint.radiusOfCurvature, arcNextPoint.arcCenterX, arcNextPoint.arcCenterY, arcLastPointX, arcLastPointY, cappedIntercept1X, cappedIntercept1Y, intercept1AngleFromArcCenterRelativeToArcStart, windingFactor, !lineSegmentIsFirstShape, arcNextPoint, true);
            }
            if (lineSegmentIntersectsArc2) {
                overlap.addArcToOverlap(arcNextPoint.radiusOfCurvature, arcNextPoint.arcCenterX, arcNextPoint.arcCenterY, arcLastPointX, arcLastPointY, cappedIntercept2X, cappedIntercept2Y, intercept2AngleFromArcCenterRelativeToArcStart, -windingFactor, !lineSegmentIsFirstShape, arcNextPoint, true);
            }
        }

        if (!lineSegmentIntersectsCircle1 && !lineSegmentIntersectsCircle2 && !lineSegmentFullyInsideCircle) {
            if (centerPerpendicularSignedDistance > 0) {
                if ((nextPointAngleFromArcCenter - lastPointAngleFromArcCenter + 10 * Math.PI) % (2 * Math.PI) > (arcNextPoint.endAngle - lastPointAngleFromArcCenter + 10 * Math.PI) % (2 * Math.PI)) {
                    lineSegmentIntersectsArcRay = true;
                }
            } else {
                if ((-(nextPointAngleFromArcCenter - lastPointAngleFromArcCenter) + 10 * Math.PI) % (2 * Math.PI) > (-(arcNextPoint.endAngle - lastPointAngleFromArcCenter) + 10 * Math.PI) % (2 * Math.PI)) {
                    lineSegmentIntersectsArcRay = true;
                }
            }
        }
        if (lineSegmentIntersectsArcRay) {
            int rayWindingFactor = (centerPerpendicularSignedDistance > 0) ? 1 : -1;
            overlap.addArcToOverlap(arcNextPoint.radiusOfCurvature, arcNextPoint.arcCenterX, arcNextPoint.arcCenterY, arcLastPointX, arcLastPointY, arcNextPoint.x, arcNextPoint.y, arcNextPoint.nonCachePoint.arcAngleChange, rayWindingFactor, !lineSegmentIsFirstShape, arcNextPoint, false);
        }
    }

    public static void addPotentialArcIntersectionWithArcToOverlap(OverlapCalculator overlap, PolyarcgonPointCache lastPoint, PolyarcgonArcedPointCache nextPoint, PolyarcgonPointCache otherLastPoint, PolyarcgonArcedPointCache otherNextPoint, double maxRoundingErrorForNearCollisions) {
        double lastPointX = lastPoint.getX();
        double lastPointY = lastPoint.getY();
        double otherLastPointX = otherLastPoint.getX();
        double otherLastPointY = otherLastPoint.getY();

        double distanceSq = (otherNextPoint.arcCenterX - nextPoint.arcCenterX) * (otherNextPoint.arcCenterX - nextPoint.arcCenterX) + (otherNextPoint.arcCenterY - nextPoint.arcCenterY) * (otherNextPoint.arcCenterY - nextPoint.arcCenterY);
        double distance = Math.sqrt(distanceSq);

        double angleOfArcStartFromOtherArcCenter = Math.atan2(lastPointY - otherNextPoint.arcCenterY, lastPointX - otherNextPoint.arcCenterX);
        double angleOfArcEndFromOtherArcCenter = Math.atan2(nextPoint.y - otherNextPoint.arcCenterY, nextPoint.x - otherNextPoint.arcCenterX);

        double angleOfOtherArcStartFromArcCenter = Math.atan2(otherLastPointY - nextPoint.arcCenterY, otherLastPointX - nextPoint.arcCenterX);
        double angleOfOtherArcEndFromArcCenter = Math.atan2(otherNextPoint.y - nextPoint.arcCenterY, otherNextPoint.x - nextPoint.arcCenterX);

        int windingFactor = (nextPoint.nonCachePoint.arcAngleChange > 0) ? 1 : -1;
        int otherWindingFactor = (otherNextPoint.nonCachePoint.arcAngleChange > 0) ? 1 : -1;

        boolean arcIntersectsOtherCircle1 = true;
        boolean arcIntersectsOtherCircle2 = true;
        boolean otherArcIntersectsCircle1 = true;
        boolean otherArcIntersectsCircle2 = true;
        boolean circleContainsOtherArcStart = true;
        boolean otherCircleContainsArcStart = true;
        boolean circleContainsOtherArcCenter = true;
        boolean otherCircleContainsArcCenter = true;
        int numberOfTimesArcIntersectsOtherRayClockwise = 0; //really is counterclockwise from the y is up point of view
        int numberOfTimesOtherArcIntersectsRayClockwise = 0;

        if (maxRoundingErrorForNearCollisions > 0) {
            if (distance >= nextPoint.radiusOfCurvature + otherNextPoint.radiusOfCurvature && distance - maxRoundingErrorForNearCollisions < nextPoint.radiusOfCurvature + otherNextPoint.radiusOfCurvature) {
                distance -= maxRoundingErrorForNearCollisions;
                distanceSq = distance * distance;
            } else if ((nextPoint.radiusOfCurvature >= distance + otherNextPoint.radiusOfCurvature && nextPoint.radiusOfCurvature < distance + maxRoundingErrorForNearCollisions + otherNextPoint.radiusOfCurvature)
                    || (otherNextPoint.radiusOfCurvature >= distance + nextPoint.radiusOfCurvature) && (otherNextPoint.radiusOfCurvature < distance + maxRoundingErrorForNearCollisions + nextPoint.radiusOfCurvature)) {
                distance += maxRoundingErrorForNearCollisions;
                distanceSq = distance * distance;
            }
        }

        if (distance >= nextPoint.radiusOfCurvature + otherNextPoint.radiusOfCurvature) {
            arcIntersectsOtherCircle1 = false;
            arcIntersectsOtherCircle2 = false;
            otherArcIntersectsCircle1 = false;
            otherArcIntersectsCircle2 = false;
            circleContainsOtherArcStart = false;
            otherCircleContainsArcStart = false;
            circleContainsOtherArcCenter = false;
            otherCircleContainsArcCenter = false;
        } else if (nextPoint.radiusOfCurvature >= distance + otherNextPoint.radiusOfCurvature) {
            arcIntersectsOtherCircle1 = false;
            arcIntersectsOtherCircle2 = false;
            otherArcIntersectsCircle1 = false;
            otherArcIntersectsCircle2 = false;
            otherCircleContainsArcStart = false;
            otherCircleContainsArcCenter = false;
        } else if (otherNextPoint.radiusOfCurvature >= distance + nextPoint.radiusOfCurvature) {
            arcIntersectsOtherCircle1 = false;
            arcIntersectsOtherCircle2 = false;
            otherArcIntersectsCircle1 = false;
            otherArcIntersectsCircle2 = false;
            circleContainsOtherArcStart = false;
            circleContainsOtherArcCenter = false;
        } else {
            circleContainsOtherArcCenter = nextPoint.radiusOfCurvature > distance;
            otherCircleContainsArcCenter = otherNextPoint.radiusOfCurvature > distance;

            double radiusSq = nextPoint.radiusOfCurvature * nextPoint.radiusOfCurvature;
            double otherRadiusSq = otherNextPoint.radiusOfCurvature * otherNextPoint.radiusOfCurvature;

            //let the point P refer to one of the two points where the two circles intersect
            //let point A be the center of this circle, and point B be the center of the other circle

            //the angle PAB
            double angleAtArcCenterBetweenOtherArcCenterAndIntersections = Math.acos((radiusSq - otherRadiusSq + distanceSq) / (2.0 * nextPoint.radiusOfCurvature * distance));

            //the angle PBA
            double angleAtOtherArcCenterBetweenArcCenterAndIntersections = Math.acos((otherRadiusSq - radiusSq + distanceSq) / (2.0 * otherNextPoint.radiusOfCurvature * distance));

            //the angle from this circle to the other circle
            double angleFromArcCenterToOtherArcCenter = Math.atan2(otherNextPoint.arcCenterY - nextPoint.arcCenterY, otherNextPoint.arcCenterX - nextPoint.arcCenterX);
            double angleFromOtherArcCenterToArcCenter = angleFromArcCenterToOtherArcCenter + Math.PI;

            double intersection1AngleFromArcCenter = angleFromArcCenterToOtherArcCenter - angleAtArcCenterBetweenOtherArcCenterAndIntersections;
            double intersection2AngleFromArcCenter = angleFromArcCenterToOtherArcCenter + angleAtArcCenterBetweenOtherArcCenterAndIntersections;

            double intersection1AngleFromOtherArcCenter = angleFromOtherArcCenterToArcCenter + angleAtOtherArcCenterBetweenArcCenterAndIntersections;
            double intersection2AngleFromOtherArcCenter = angleFromOtherArcCenterToArcCenter - angleAtOtherArcCenterBetweenArcCenterAndIntersections;

            double intersection1X = nextPoint.arcCenterX + nextPoint.radiusOfCurvature * Math.cos(intersection1AngleFromArcCenter);
            double intersection1Y = nextPoint.arcCenterY + nextPoint.radiusOfCurvature * Math.sin(intersection1AngleFromArcCenter);

            double intersection2X = nextPoint.arcCenterX + nextPoint.radiusOfCurvature * Math.cos(intersection2AngleFromArcCenter);
            double intersection2Y = nextPoint.arcCenterY + nextPoint.radiusOfCurvature * Math.sin(intersection2AngleFromArcCenter);

            double angleBetweenIntercept1AndArcStartFromArcCenter;
            double angleBetweenIntercept2AndArcStartFromArcCenter;
            if (nextPoint.nonCachePoint.arcAngleChange > 0) {
                angleBetweenIntercept1AndArcStartFromArcCenter = (intersection1AngleFromArcCenter - nextPoint.startAngle + maxRoundingErrorForNearCollisions + 10 * Math.PI) % (2 * Math.PI) - maxRoundingErrorForNearCollisions;
                if (angleBetweenIntercept1AndArcStartFromArcCenter >= nextPoint.nonCachePoint.arcAngleChange + maxRoundingErrorForNearCollisions) {
                    arcIntersectsOtherCircle1 = false;
                }
                angleBetweenIntercept2AndArcStartFromArcCenter = (intersection2AngleFromArcCenter - nextPoint.startAngle + maxRoundingErrorForNearCollisions + 10 * Math.PI) % (2 * Math.PI) - maxRoundingErrorForNearCollisions;
                if (angleBetweenIntercept2AndArcStartFromArcCenter >= nextPoint.nonCachePoint.arcAngleChange + maxRoundingErrorForNearCollisions) {
                    arcIntersectsOtherCircle2 = false;
                }
            } else {
                angleBetweenIntercept1AndArcStartFromArcCenter = (intersection1AngleFromArcCenter - nextPoint.startAngle - maxRoundingErrorForNearCollisions - 10 * Math.PI) % (2 * Math.PI) + maxRoundingErrorForNearCollisions;
                if (angleBetweenIntercept1AndArcStartFromArcCenter <= nextPoint.nonCachePoint.arcAngleChange - maxRoundingErrorForNearCollisions) {
                    arcIntersectsOtherCircle1 = false;
                }
                angleBetweenIntercept2AndArcStartFromArcCenter = (intersection2AngleFromArcCenter - nextPoint.startAngle - maxRoundingErrorForNearCollisions - 10 * Math.PI) % (2 * Math.PI) + maxRoundingErrorForNearCollisions;
                if (angleBetweenIntercept2AndArcStartFromArcCenter <= nextPoint.nonCachePoint.arcAngleChange - maxRoundingErrorForNearCollisions) {
                    arcIntersectsOtherCircle2 = false;
                }
            }
            if (angleBetweenIntercept2AndArcStartFromArcCenter >= angleBetweenIntercept1AndArcStartFromArcCenter) {
                otherCircleContainsArcStart = false; //because the arc starts outside the other circle
            }
            double angleBetweenIntercept1AndOtherArcStartFromOtherArcCenter;
            double angleBetweenIntercept2AndOtherArcStartFromOtherArcCenter;
            if (otherNextPoint.nonCachePoint.arcAngleChange > 0) {
                angleBetweenIntercept1AndOtherArcStartFromOtherArcCenter = (intersection1AngleFromOtherArcCenter - otherNextPoint.startAngle + maxRoundingErrorForNearCollisions + 10 * Math.PI) % (2 * Math.PI) - maxRoundingErrorForNearCollisions;
                if (angleBetweenIntercept1AndOtherArcStartFromOtherArcCenter >= otherNextPoint.nonCachePoint.arcAngleChange + maxRoundingErrorForNearCollisions) {
                    otherArcIntersectsCircle1 = false;
                }
                angleBetweenIntercept2AndOtherArcStartFromOtherArcCenter = (intersection2AngleFromOtherArcCenter - otherNextPoint.startAngle + maxRoundingErrorForNearCollisions + 10 * Math.PI) % (2 * Math.PI) - maxRoundingErrorForNearCollisions;
                if (angleBetweenIntercept2AndOtherArcStartFromOtherArcCenter >= otherNextPoint.nonCachePoint.arcAngleChange + maxRoundingErrorForNearCollisions) {
                    otherArcIntersectsCircle2 = false;
                }
            } else {
                angleBetweenIntercept1AndOtherArcStartFromOtherArcCenter = (intersection1AngleFromOtherArcCenter - otherNextPoint.startAngle - maxRoundingErrorForNearCollisions - 10 * Math.PI) % (2 * Math.PI) + maxRoundingErrorForNearCollisions;
                if (angleBetweenIntercept1AndOtherArcStartFromOtherArcCenter <= otherNextPoint.nonCachePoint.arcAngleChange - maxRoundingErrorForNearCollisions) {
                    otherArcIntersectsCircle1 = false;
                }
                angleBetweenIntercept2AndOtherArcStartFromOtherArcCenter = (intersection2AngleFromOtherArcCenter - otherNextPoint.startAngle - maxRoundingErrorForNearCollisions - 10 * Math.PI) % (2 * Math.PI) + maxRoundingErrorForNearCollisions;
                if (angleBetweenIntercept2AndOtherArcStartFromOtherArcCenter <= otherNextPoint.nonCachePoint.arcAngleChange - maxRoundingErrorForNearCollisions) {
                    otherArcIntersectsCircle2 = false;
                }
            }
            if (angleBetweenIntercept1AndOtherArcStartFromOtherArcCenter >= angleBetweenIntercept2AndOtherArcStartFromOtherArcCenter) {
                circleContainsOtherArcStart = false; //because the other arc starts outside the circle
            }
            if (arcIntersectsOtherCircle1 && arcIntersectsOtherCircle2 && otherCircleContainsArcStart) {
                if ((intersection1AngleFromOtherArcCenter - intersection2AngleFromOtherArcCenter + 10 * Math.PI) % (2 * Math.PI) > (otherNextPoint.endAngle - intersection2AngleFromOtherArcCenter + 10 * Math.PI) % (2 * Math.PI)) {
                    numberOfTimesArcIntersectsOtherRayClockwise += windingFactor;
                }
            } else {
                double angleBetweenArcStartAndArcCenterFromOtherArcCenter = ((angleOfArcStartFromOtherArcCenter - angleFromOtherArcCenterToArcCenter + 11 * Math.PI) % (2 * Math.PI)) - Math.PI;
                double angleBetweenArcEndAndArcCenterFromOtherArcCenter = ((angleOfArcEndFromOtherArcCenter - angleFromOtherArcCenterToArcCenter + 11 * Math.PI) % (2 * Math.PI)) - Math.PI;
                if (arcIntersectsOtherCircle1) {
                    numberOfTimesArcIntersectsOtherRayClockwise += getSignedNumberOfTimesArcAComingOutOfIntersectionCIntersectsRayOfArcB(otherNextPoint.endAngle, intersection1AngleFromOtherArcCenter, angleAtOtherArcCenterBetweenArcCenterAndIntersections, angleBetweenArcStartAndArcCenterFromOtherArcCenter, angleBetweenArcEndAndArcCenterFromOtherArcCenter, windingFactor, 1);
                }
                if (arcIntersectsOtherCircle2) {
                    numberOfTimesArcIntersectsOtherRayClockwise += getSignedNumberOfTimesArcAComingOutOfIntersectionCIntersectsRayOfArcB(otherNextPoint.endAngle, intersection2AngleFromOtherArcCenter, angleAtOtherArcCenterBetweenArcCenterAndIntersections, angleBetweenArcStartAndArcCenterFromOtherArcCenter, angleBetweenArcEndAndArcCenterFromOtherArcCenter, windingFactor, -1);
                }
            }

            if (otherArcIntersectsCircle1 && otherArcIntersectsCircle2 && circleContainsOtherArcStart) {
                if ((intersection2AngleFromArcCenter - intersection1AngleFromArcCenter + 10 * Math.PI) % (2 * Math.PI) > (nextPoint.endAngle - intersection1AngleFromArcCenter + 10 * Math.PI) % (2 * Math.PI)) {
                    numberOfTimesOtherArcIntersectsRayClockwise += otherWindingFactor;
                }
            } else {
                double angleBetweenOtherArcStartAndOtherArcCenterFromArcCenter = ((angleOfOtherArcStartFromArcCenter - angleFromArcCenterToOtherArcCenter + 11 * Math.PI) % (2 * Math.PI)) - Math.PI;
                double angleBetweenOtherArcEndAndOtherArcCenterFromArcCenter = ((angleOfOtherArcEndFromArcCenter - angleFromArcCenterToOtherArcCenter + 11 * Math.PI) % (2 * Math.PI)) - Math.PI;
                if (otherArcIntersectsCircle1) {
                    numberOfTimesOtherArcIntersectsRayClockwise += getSignedNumberOfTimesArcAComingOutOfIntersectionCIntersectsRayOfArcB(nextPoint.endAngle, intersection1AngleFromArcCenter, angleAtArcCenterBetweenOtherArcCenterAndIntersections, angleBetweenOtherArcStartAndOtherArcCenterFromArcCenter, angleBetweenOtherArcEndAndOtherArcCenterFromArcCenter, otherWindingFactor, -1);
                }
                if (otherArcIntersectsCircle2) {
                    numberOfTimesOtherArcIntersectsRayClockwise += getSignedNumberOfTimesArcAComingOutOfIntersectionCIntersectsRayOfArcB(nextPoint.endAngle, intersection2AngleFromArcCenter, angleAtArcCenterBetweenOtherArcCenterAndIntersections, angleBetweenOtherArcStartAndOtherArcCenterFromArcCenter, angleBetweenOtherArcEndAndOtherArcCenterFromArcCenter, otherWindingFactor, 1);
                }
            }
            if (arcIntersectsOtherCircle1 && otherArcIntersectsCircle1) {
                overlap.addArcToOverlap(nextPoint.radiusOfCurvature, nextPoint.arcCenterX, nextPoint.arcCenterY, lastPointX, lastPointY, intersection1X, intersection1Y, angleBetweenIntercept1AndArcStartFromArcCenter, -windingFactor * otherWindingFactor, true, nextPoint, true);
                overlap.addArcToOverlap(otherNextPoint.radiusOfCurvature, otherNextPoint.arcCenterX, otherNextPoint.arcCenterY, otherLastPointX, otherLastPointY, intersection1X, intersection1Y, angleBetweenIntercept1AndOtherArcStartFromOtherArcCenter, windingFactor * otherWindingFactor, false, otherNextPoint, true);
            }
            if (arcIntersectsOtherCircle2 && otherArcIntersectsCircle2) {
                overlap.addArcToOverlap(nextPoint.radiusOfCurvature, nextPoint.arcCenterX, nextPoint.arcCenterY, lastPointX, lastPointY, intersection2X, intersection2Y, angleBetweenIntercept2AndArcStartFromArcCenter, windingFactor * otherWindingFactor, true, nextPoint, true);
                overlap.addArcToOverlap(otherNextPoint.radiusOfCurvature, otherNextPoint.arcCenterX, otherNextPoint.arcCenterY, otherLastPointX, otherLastPointY, intersection2X, intersection2Y, angleBetweenIntercept2AndOtherArcStartFromOtherArcCenter, -windingFactor * otherWindingFactor, false, otherNextPoint, true);
            }
        }
        if (!otherArcIntersectsCircle1 && !otherArcIntersectsCircle2 && !circleContainsOtherArcStart) {
            numberOfTimesOtherArcIntersectsRayClockwise += getSignedNumberOfTimesArcAThatIsOutsideCircleBIntersectsRayOfArcB(nextPoint.endAngle, otherNextPoint.nonCachePoint.arcAngleChange, angleOfOtherArcStartFromArcCenter, angleOfOtherArcEndFromArcCenter, otherCircleContainsArcCenter);
        }
        if (!arcIntersectsOtherCircle1 && !arcIntersectsOtherCircle2 && !otherCircleContainsArcStart) {
            numberOfTimesArcIntersectsOtherRayClockwise += getSignedNumberOfTimesArcAThatIsOutsideCircleBIntersectsRayOfArcB(otherNextPoint.endAngle, nextPoint.nonCachePoint.arcAngleChange, angleOfArcStartFromOtherArcCenter, angleOfArcEndFromOtherArcCenter, circleContainsOtherArcCenter);
        }
        // Note it is logically impossible for these to be different than -1, 0, or 1
        if (numberOfTimesOtherArcIntersectsRayClockwise != 0) {
            overlap.addArcToOverlap(nextPoint.radiusOfCurvature, nextPoint.arcCenterX, nextPoint.arcCenterY, lastPointX, lastPointY, nextPoint.x, nextPoint.y, nextPoint.nonCachePoint.arcAngleChange, numberOfTimesOtherArcIntersectsRayClockwise, true, nextPoint, false);
        }
        if (numberOfTimesArcIntersectsOtherRayClockwise != 0) {
            overlap.addArcToOverlap(otherNextPoint.radiusOfCurvature, otherNextPoint.arcCenterX, otherNextPoint.arcCenterY, otherLastPointX, otherLastPointY, otherNextPoint.x, otherNextPoint.y, otherNextPoint.nonCachePoint.arcAngleChange, numberOfTimesArcIntersectsOtherRayClockwise, false, otherNextPoint, false);
        }
    }

    private static int getSignedNumberOfTimesArcAThatIsOutsideCircleBIntersectsRayOfArcB(double arcBEndAngle, double arcAAngleChange, double angleOfArcAStartFromArcBCenter, double angleOfArcAEndFromArcBCenter, boolean circleAContainsArcBCenter) {
        if (circleAContainsArcBCenter) { //note cannot replace this with circleContainsOtherArcStart
            //it can only move clockwise or counterclockwise depending on whether windingFactor is positive
            if (arcAAngleChange > 0) {
                if ((angleOfArcAEndFromArcBCenter - angleOfArcAStartFromArcBCenter + 10 * Math.PI) % (2 * Math.PI) > (arcBEndAngle - angleOfArcAStartFromArcBCenter + 10 * Math.PI) % (2 * Math.PI)) {
                    return 1;
                }
            } else {
                if ((angleOfArcAEndFromArcBCenter - angleOfArcAStartFromArcBCenter - 10 * Math.PI) % (2 * Math.PI) < (arcBEndAngle - angleOfArcAStartFromArcBCenter - 10 * Math.PI) % (2 * Math.PI)) {
                    return -1;
                }
            }
        } else {
            //it can move either clockwise or counterclockwise regardless of winding factor, but cannot rotate more than pi in either direction

            double angleBetweenArcStartAndArcEndFromOtherArcCenter = ((angleOfArcAEndFromArcBCenter - angleOfArcAStartFromArcBCenter + 11 * Math.PI) % (2 * Math.PI)) - Math.PI;
            if (angleBetweenArcStartAndArcEndFromOtherArcCenter > 0) {
                if (angleBetweenArcStartAndArcEndFromOtherArcCenter > (arcBEndAngle - angleOfArcAStartFromArcBCenter + 10 * Math.PI) % (2 * Math.PI)) {
                    return 1;
                }
            } else {
                if (angleBetweenArcStartAndArcEndFromOtherArcCenter < (arcBEndAngle - angleOfArcAStartFromArcBCenter - 10 * Math.PI) % (2 * Math.PI)) {
                    return -1;
                }
            }
        }
        return 0;
    }

    private static int getSignedNumberOfTimesArcAComingOutOfIntersectionCIntersectsRayOfArcB(double arcBEndAngle, double intersectionCAngleFromArcBCenter, double angleAtArcBCenterBetweenArcACenterAndIntersections, double angleBetweenArcAStartAndArcACenterFromArcBCenter, double angleBetweenArcAEndAndArcACenterFromArcBCenter, int arcAWindingFactor, int signOfAngleFromArcACenterToIntersectionCRelativeToArcBCenter) {
        // To calculate the angle between intercept 1 and the arc start/end (from the point of view of the other center),
        // we can't just look at the two angles because we don't know which way the arc turns from one angle to the other.
        // Instead we have to break the angle into two pieces: one between intercept C and the center (which we know the direction of),
        // and one between the center and the arc start/end (which is less than pi).
//        wrong: double angleBetweenArcStartOrEndAndInterceptCFromOtherArcCenter = (angleOfArcAStartOrEndFromArcBCenter - intersectionCAngleFromArcBCenter + 10 * Math.PI) % (2 * Math.PI)
        double angleBetweenArcStartOrEndAndInterceptCFromOtherArcCenter;
        if (arcAWindingFactor * signOfAngleFromArcACenterToIntersectionCRelativeToArcBCenter > 0) {
            angleBetweenArcStartOrEndAndInterceptCFromOtherArcCenter = angleBetweenArcAStartAndArcACenterFromArcBCenter + angleAtArcBCenterBetweenArcACenterAndIntersections * (-signOfAngleFromArcACenterToIntersectionCRelativeToArcBCenter);
        } else {
            angleBetweenArcStartOrEndAndInterceptCFromOtherArcCenter = angleBetweenArcAEndAndArcACenterFromArcBCenter + angleAtArcBCenterBetweenArcACenterAndIntersections * (-signOfAngleFromArcACenterToIntersectionCRelativeToArcBCenter);
        }
        if (angleBetweenArcStartOrEndAndInterceptCFromOtherArcCenter > 0) {
            if (angleBetweenArcStartOrEndAndInterceptCFromOtherArcCenter > (arcBEndAngle - intersectionCAngleFromArcBCenter + 10 * Math.PI) % (2 * Math.PI)) {
                return (-signOfAngleFromArcACenterToIntersectionCRelativeToArcBCenter) * arcAWindingFactor;
            }
        }
        if (angleBetweenArcStartOrEndAndInterceptCFromOtherArcCenter < 0) {
            if (angleBetweenArcStartOrEndAndInterceptCFromOtherArcCenter < (arcBEndAngle - intersectionCAngleFromArcBCenter - 10 * Math.PI) % (2 * Math.PI)) {
                return signOfAngleFromArcACenterToIntersectionCRelativeToArcBCenter * arcAWindingFactor;
            }
        }
        return 0;
    }

    public static interface PolyarcgonPointCache {

        double getX();

        double getY();

        PolyarcgonPoint getNonCachePoint();

        void updatePositionCache(double xTranslation, double yTranslation, double cosRotation, double sinRotation);

        void updateLineOrArcCache(PolyarcgonPointCache lastPoint);
    }

    public static class PolyarcgonStraightPointCache implements PolyarcgonPointCache {
        double x;
        double y;
        public double pointToPointX;
        public double pointToPointY;
        final PolyarcgonPoint nonCachePoint;

        public PolyarcgonStraightPointCache(PolyarcgonPoint nonCachePoint) {
            this.nonCachePoint = nonCachePoint;
        }

        @Override
        public double getX() {
            return x;
        }

        @Override
        public double getY() {
            return y;
        }

        @Override
        public PolyarcgonPoint getNonCachePoint() {
            return nonCachePoint;
        }

        @Override
        public void updatePositionCache(double xTranslation, double yTranslation, double cosRotation, double sinRotation) {
            x = nonCachePoint.x * cosRotation - nonCachePoint.y * sinRotation + xTranslation;
            y = nonCachePoint.y * cosRotation + nonCachePoint.x * sinRotation + yTranslation;
        }

        @Override
        public void updateLineOrArcCache(PolyarcgonPointCache lastPoint) {
            pointToPointX = x - lastPoint.getX();
            pointToPointY = y - lastPoint.getY();
        }
    }

    public static class PolyarcgonArcedPointCache implements PolyarcgonPointCache {
        double x;
        double y;
        double radiusOfCurvature = 0;
        private double signedDistanceBetweenArcCenterAndPointToPointCenterRelativeToPointToPointDistance = 0;
        double arcCenterX;
        double arcCenterY;
        double startAngle;
        double endAngle;
        final PolyarcgonPoint nonCachePoint;

        public PolyarcgonArcedPointCache(PolyarcgonPoint nonCachePoint) {
            this.nonCachePoint = nonCachePoint;
        }

        @Override
        public double getX() {
            return x;
        }

        @Override
        public double getY() {
            return y;
        }

        @Override
        public PolyarcgonPoint getNonCachePoint() {
            return nonCachePoint;
        }

        @Override
        public void updatePositionCache(double xTranslation, double yTranslation, double cosRotation, double sinRotation) {
            x = nonCachePoint.x * cosRotation - nonCachePoint.y * sinRotation + xTranslation;
            y = nonCachePoint.y * cosRotation + nonCachePoint.x * sinRotation + yTranslation;
        }

        @Override
        public void updateLineOrArcCache(PolyarcgonPointCache lastPoint) {
            double lastPointX = lastPoint.getX();
            double lastPointY = lastPoint.getY();
            if (radiusOfCurvature == 0 || signedDistanceBetweenArcCenterAndPointToPointCenterRelativeToPointToPointDistance == 0) {
                double arcPointToPointX = x - lastPointX;
                double arcPointToPointY = y - lastPointY;
                double arcPointToPointDistance = Math.sqrt(arcPointToPointX * arcPointToPointX + arcPointToPointY * arcPointToPointY);
                double arcSignedRadiusOfCurvature = arcPointToPointDistance / (2.0 * Math.sin(nonCachePoint.arcAngleChange / 2.0));
                double arcSignedDistanceBetweenArcCenterAndStraightEdge = arcSignedRadiusOfCurvature * Math.cos(nonCachePoint.arcAngleChange / 2.0);
                signedDistanceBetweenArcCenterAndPointToPointCenterRelativeToPointToPointDistance = arcSignedDistanceBetweenArcCenterAndStraightEdge / arcPointToPointDistance;
                radiusOfCurvature = Math.abs(arcSignedRadiusOfCurvature);
            }
            arcCenterX = (lastPointX + x) / 2.0 + (-(y - lastPointY)) * signedDistanceBetweenArcCenterAndPointToPointCenterRelativeToPointToPointDistance;
            arcCenterY = (lastPointY + y) / 2.0 + (x - lastPointX) * signedDistanceBetweenArcCenterAndPointToPointCenterRelativeToPointToPointDistance;
            startAngle = Math.atan2(lastPointY - arcCenterY, lastPointX - arcCenterX);
            endAngle = startAngle + nonCachePoint.arcAngleChange;
        }
    }

    public static double[] getArcCenterAndSignedRadius(double lastPointX, double lastPointY, double nextPointX, double nextPointY, double arcAngleChange) {
        double arcPointToPointX = nextPointX - lastPointX;
        double arcPointToPointY = nextPointY - lastPointY;
        double arcPointToPointDistance = Math.sqrt(arcPointToPointX * arcPointToPointX + arcPointToPointY * arcPointToPointY);
        double arcSignedRadiusOfCurvature = arcPointToPointDistance / (2.0 * Math.sin(arcAngleChange / 2.0));
        double arcSignedDistanceBetweenArcCenterAndStraightEdge = arcSignedRadiusOfCurvature * Math.cos(arcAngleChange / 2.0);
        double signedDistanceBetweenArcCenterAndPointToPointCenterRelativeToPointToPointDistance = arcSignedDistanceBetweenArcCenterAndStraightEdge / arcPointToPointDistance;

        double[] arcCenterAndSignedRadius = new double[3];
        arcCenterAndSignedRadius[0] = (lastPointX + nextPointX) / 2.0 + (-(nextPointY - lastPointY)) * signedDistanceBetweenArcCenterAndPointToPointCenterRelativeToPointToPointDistance;
        arcCenterAndSignedRadius[1] = (lastPointY + nextPointY) / 2.0 + (nextPointX - lastPointX) * signedDistanceBetweenArcCenterAndPointToPointCenterRelativeToPointToPointDistance;
        arcCenterAndSignedRadius[2] = arcSignedRadiusOfCurvature;
        return arcCenterAndSignedRadius;
    }
}
