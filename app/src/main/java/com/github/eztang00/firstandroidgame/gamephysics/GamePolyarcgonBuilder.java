package com.github.eztang00.firstandroidgame.gamephysics;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * A class for conveniently building "polyarcgon" shapes
 */
public class GamePolyarcgonBuilder {
    static class GamePolyarcgonBuilderPoint implements Cloneable {
        double x;
        double y;
        double arcAngleChange;
        double signedRoundedTurnRadius; // is positive if clockwise (or counterclockwise from the y is up frame of reference), negative otherwise
        double signedVirtualRoundedTurnRadius;
        // virtual rounded turn radius is useful for creating an offset
        // e.g. if signedVirtualRoundedTurnRadius == signedRoundedTurnRadius, then x and y are in the middle of the rounded turn circle
        // e.g. if signedVirtualRoundedTurnRadius == 0, then x and y are at where the "corner" would be if the rounded turn was sharpened
        // e.g. if signedVirtualRoundedTurnRadius == -signedRoundedTurnRadius, x and y would be even further towards the corner than the corner
        // e.g. twice as far assuming we have straight lines.
        // The negative version is useful for creating walls with corners which are both rounded on the outside and rounded on the inside.
        //  ______
        // (____  \
        //      \ |
        //      | |
        //      \_/
        // The inside rounded turn and the outside rounded turn can have the same x and y.
        // The outside rounded turn has signedVirtualRoundedTurnRadius == signedRoundedTurnRadius,
        // The inside rounded turn has signedVirtualRoundedTurnRadius == -signedRoundedTurnRadius.

        // These use the signedVirtualRoundedTurnRadius, not signedRoundedTurnRadius, though in the end these two should be equal
        // Beware these angles are offset by 180 degrees when we're using a negative signed radius.
        // This ensures that (cos(angle), sin(angle)) * signedRadius is correct.
        double roundedTurnStartAngle;
        double roundedTurnEndAngle;

        double roundedTurnStartX;
        double roundedTurnStartY;

        double roundedTurnEndX;
        double roundedTurnEndY;

        @NonNull
        public GamePolyarcgonBuilderPoint clone() {
            try {
                return (GamePolyarcgonBuilderPoint) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }

        public GamePolyarcgonBuilderPoint(double x, double y, double arcAngleChange) {
            this(x, y, arcAngleChange, 0, 0);
        }

        public GamePolyarcgonBuilderPoint(double x, double y, double arcAngleChange, double signedRoundedTurnRadius) {
            this(x, y, arcAngleChange, signedRoundedTurnRadius, signedRoundedTurnRadius);
        }

        public GamePolyarcgonBuilderPoint(double x, double y, double arcAngleChange, double signedRoundedTurnRadius, double signedVirtualRoundedTurnRadius) {
            this.x = x;
            this.y = y;
            this.roundedTurnStartX = x; // by default these are the same so that if the signedRoundedTurnRadius/signedVirtualRoundedTurnRadius are 0, things still work
            this.roundedTurnStartY = y;
            this.roundedTurnEndX = x;
            this.roundedTurnEndY = y;
            this.arcAngleChange = arcAngleChange;
            this.signedRoundedTurnRadius = signedRoundedTurnRadius;
            this.signedVirtualRoundedTurnRadius = signedVirtualRoundedTurnRadius;
        }
    }

    static final double MAX_ROUNDING_ERROR = 1000000 * Double.MIN_VALUE / Double.MIN_NORMAL;
    public ArrayList<ArrayList<GamePolyarcgonBuilderPoint>> contoursOfPoints;

    public double density;
    public Object additionalAttributes;

    public double centerOfMassX;
    public double centerOfMassY;
    public boolean centerOfMassIsRelativeToDefaultCenterOfMass;

    public GamePolyarcgonBuilder() {
        reset();
    }

    ArrayList<PolyarcgonPoint> generatePoints() {

        ArrayList<PolyarcgonPoint> generatedPoints = new ArrayList<>();

        for (ArrayList<GamePolyarcgonBuilderPoint> contour : contoursOfPoints) {
            if (!contour.isEmpty()) {
                ArrayList<GamePolyarcgonBuilderPoint> contourClone = new ArrayList<>(contour);
                contourClone.replaceAll(GamePolyarcgonBuilderPoint::clone);

                for (int i = 0; i < contour.size(); i++) {
                    GamePolyarcgonBuilderPoint prevPoint = contour.get((i + contour.size() - 1) % contour.size());
                    GamePolyarcgonBuilderPoint nextPoint = contour.get(i);
                    if (prevPoint.signedVirtualRoundedTurnRadius != 0 || nextPoint.signedVirtualRoundedTurnRadius != 0) {
                        processLineOrArcBetweenOneOrTwoRoundedTurns(prevPoint, nextPoint);
                    }
                }
                for (int i = 0; i < contour.size(); i++) {
                    GamePolyarcgonBuilderPoint point = contour.get(i);
                    if (point.signedVirtualRoundedTurnRadius != point.signedRoundedTurnRadius) {
                        GamePolyarcgonBuilderPoint prevPoint = contour.get((i + contour.size() - 1) % contour.size());
                        GamePolyarcgonBuilderPoint nextPoint = contour.get((i + 1) % contour.size());
                        movePointToMakeVirtualRoundedTurnEqualRoundedTurn(point, prevPoint, nextPoint);
                    }
                }

                if (contoursOfPoints.size() >= 2) {
                    GamePolyarcgonBuilderPoint lastPoint = contour.get(contour.size() - 1);
                    generatedPoints.add(new PolyarcgonPoint(lastPoint.roundedTurnEndX, lastPoint.roundedTurnEndY, 0, true));
                }
                for (int i = 0; i < contour.size(); i++) {
                    GamePolyarcgonBuilderPoint point = contour.get(i);
                    generatedPoints.add(new PolyarcgonPoint(point.roundedTurnStartX, point.roundedTurnStartY, point.arcAngleChange, false));
                    if (point.signedRoundedTurnRadius != 0) {
                        generatedPoints.add(new PolyarcgonPoint(point.roundedTurnEndX, point.roundedTurnEndY, (point.roundedTurnEndAngle - point.roundedTurnStartAngle + Math.copySign(10 * Math.PI, point.signedRoundedTurnRadius)) % (2 * Math.PI), false));
                    }
                }
            }
        }
        return generatedPoints;
    }

    public GamePolyarcgonBuilder addRectangleContour(double x1, double y1, double x2, double y2, boolean isClockwise) {
        newContour();
        lineTo(x1, y1);
        if (isClockwise) {
            lineTo(x2, y1);
            lineTo(x2, y2);
            lineTo(x1, y2);
        } else {
            lineTo(x1, y2);
            lineTo(x2, y2);
            lineTo(x2, y1);
        }
        newContour();
        return this;
    }

    public GamePolyarcgonBuilder addRoundedRectangleContour(double x1, double y1, double x2, double y2, double radius, boolean isClockwise) {
        newContour();
        lineToRoundedTurn(x1 + radius, y1 + radius, radius, isClockwise);
        if (isClockwise) {
            lineToRoundedTurn(x2 - radius, y1 + radius, radius, true);
            lineToRoundedTurn(x2 - radius, y2 - radius, radius, true);
            lineToRoundedTurn(x1 + radius, y2 - radius, radius, true);
        } else {
            lineToRoundedTurn(x1 + radius, y2 - radius, radius, false);
            lineToRoundedTurn(x2 - radius, y2 - radius, radius, false);
            lineToRoundedTurn(x2 - radius, y1 + radius, radius, false);
        }
        newContour();
        return this;
    }

    public GamePolyarcgonBuilder withCenterOfMass(double x, double y, boolean isRelativeToDefaultCenterOfMass) {
        this.centerOfMassX = x;
        this.centerOfMassY = y;
        this.centerOfMassIsRelativeToDefaultCenterOfMass = isRelativeToDefaultCenterOfMass;
        return this;
    }

    public GamePolyarcgonBuilder withDensity(double density) {
        this.density = density;
        return this;
    }

    public GamePolyarcgonBuilder withAdditionalAttributes(Object additionalAttributes) {
        this.additionalAttributes = additionalAttributes;
        return this;
    }

    public GamePolyarcgonBuilder newContour() {
        if (!contoursOfPoints.get(contoursOfPoints.size() - 1).isEmpty()) {
            contoursOfPoints.add(new ArrayList<>());
        }
        return this;
    }

    public GamePolyarcgonBuilder lineTo(double x, double y) {
        return arcTo(x, y, 0);
    }

    public GamePolyarcgonBuilder arcTo(double x, double y, double arcAngleChange) {
        contoursOfPoints.get(contoursOfPoints.size() - 1).add(new GamePolyarcgonBuilderPoint(x, y, arcAngleChange));
        return this;
    }

    public GamePolyarcgonBuilder lineToRoundedTurn(double x, double y, double roundedTurnRadius, boolean isClockwise) {
        return arcToRoundedTurn(x, y, 0, roundedTurnRadius, isClockwise);
    }

    public GamePolyarcgonBuilder arcToRoundedTurn(double x, double y, double arcAngleChange, double roundedTurnRadius, boolean isClockwise) {
        contoursOfPoints.get(contoursOfPoints.size() - 1).add(new GamePolyarcgonBuilderPoint(x, y, arcAngleChange, isClockwise ? roundedTurnRadius : -roundedTurnRadius));
        return this;
    }

    public GamePolyarcgonBuilder lineToVirtualRoundedTurn(double x, double y, double roundedTurnRadius, boolean isClockwise, double virtualRoundedTurnRadius, boolean isVirtualTurnClockwise) {
        return arcToVirtualRoundedTurn(x, y, 0, roundedTurnRadius, isClockwise, virtualRoundedTurnRadius, isVirtualTurnClockwise);
    }

    public GamePolyarcgonBuilder arcToVirtualRoundedTurn(double x, double y, double arcAngleChange, double roundedTurnRadius, boolean isClockwise, double virtualRoundedTurnRadius, boolean isVirtualTurnClockwise) {
        contoursOfPoints.get(contoursOfPoints.size() - 1).add(new GamePolyarcgonBuilderPoint(x, y, arcAngleChange, isClockwise ? roundedTurnRadius : -roundedTurnRadius, isVirtualTurnClockwise ? virtualRoundedTurnRadius : -virtualRoundedTurnRadius));
        return this;
    }

    private void movePointToMakeVirtualRoundedTurnEqualRoundedTurn(GamePolyarcgonBuilderPoint pointToMove, GamePolyarcgonBuilderPoint prevPoint, GamePolyarcgonBuilderPoint nextPoint) {
        boolean firstIsStraight = PolyarcgonPoint.isAlmostStraight(pointToMove.arcAngleChange);
        boolean secondIsStraight = PolyarcgonPoint.isAlmostStraight(nextPoint.arcAngleChange);
        if (firstIsStraight && secondIsStraight) {
            movePointToMakeVirtualRoundedTurnEqualRoundedTurnBothStraight(pointToMove, prevPoint, nextPoint);
        } else if (firstIsStraight && !secondIsStraight) {
            movePointToMakeVirtualRoundedTurnEqualRoundedTurnOneStraightOneArced(pointToMove, prevPoint, nextPoint, true);
        } else if (!firstIsStraight && secondIsStraight) {
            movePointToMakeVirtualRoundedTurnEqualRoundedTurnOneStraightOneArced(pointToMove, nextPoint, prevPoint, false);
        } else {
            movePointToMakeVirtualRoundedTurnEqualRoundedTurnBothArced(pointToMove, prevPoint, nextPoint);
        }
        // we have to update each rounded turn after moving it, otherwise the formulas for moving rounded turns won't work
        processLineOrArcBetweenOneOrTwoRoundedTurns(prevPoint, pointToMove);
        processLineOrArcBetweenOneOrTwoRoundedTurns(pointToMove, nextPoint);
    }

    private void movePointToMakeVirtualRoundedTurnEqualRoundedTurnBothStraight(GamePolyarcgonBuilderPoint pointToMove, GamePolyarcgonBuilderPoint prevPoint, GamePolyarcgonBuilderPoint nextPoint) {
        double firstUnitVectorX = pointToMove.roundedTurnStartX - prevPoint.roundedTurnEndX;
        double firstUnitVectorY = pointToMove.roundedTurnStartY - prevPoint.roundedTurnEndY;
        double secondUnitVectorX = pointToMove.roundedTurnEndX - nextPoint.roundedTurnStartX;
        double secondUnitVectorY = pointToMove.roundedTurnEndY - nextPoint.roundedTurnStartY;
        double firstVectorLength = Math.sqrt(firstUnitVectorX * firstUnitVectorX + firstUnitVectorY * firstUnitVectorY);
        double secondVectorLength = Math.sqrt(secondUnitVectorX * secondUnitVectorX + secondUnitVectorY * secondUnitVectorY);

        // normalize
        firstUnitVectorX /= firstVectorLength;
        firstUnitVectorY /= firstVectorLength;
        secondUnitVectorX /= secondVectorLength;
        secondUnitVectorY /= secondVectorLength;

        double averageUnitVectorX = firstUnitVectorX + secondUnitVectorX;
        double averageUnitVectorY = firstUnitVectorY + secondUnitVectorY;
        double averageVectorLength = Math.sqrt(averageUnitVectorX * averageUnitVectorX + averageUnitVectorY * averageUnitVectorY);

        averageUnitVectorX /= averageVectorLength;
        averageUnitVectorY /= averageVectorLength;

        double sinHalfAngleBetweenUnitVectors = -(firstUnitVectorX * averageUnitVectorY - firstUnitVectorY * averageUnitVectorX);
        double signedDistanceToMove = -(pointToMove.signedRoundedTurnRadius - pointToMove.signedVirtualRoundedTurnRadius) / sinHalfAngleBetweenUnitVectors;

        pointToMove.x = pointToMove.x + averageUnitVectorX * signedDistanceToMove;
        pointToMove.y = pointToMove.y + averageUnitVectorY * signedDistanceToMove;
        pointToMove.signedVirtualRoundedTurnRadius = pointToMove.signedRoundedTurnRadius;
    }

    private void movePointToMakeVirtualRoundedTurnEqualRoundedTurnOneStraightOneArced(GamePolyarcgonBuilderPoint pointToMove, GamePolyarcgonBuilderPoint straightNeighbour, GamePolyarcgonBuilderPoint arcedNeighbour, boolean straightNeighbourIsPrevArcedNeighbourIsNext) {
        double straightNeighbourX;
        double straightNeighbourY;
        double pointToMoveRoundedTurnToStraightNeighbourX;
        double pointToMoveRoundedTurnToStraightNeighbourY;
        double arcAngleChange;
        double arcCenterX;
        double arcCenterY;
        double arcSignedRadius;
        if (straightNeighbourIsPrevArcedNeighbourIsNext) {
            straightNeighbourX = straightNeighbour.roundedTurnEndX;
            straightNeighbourY = straightNeighbour.roundedTurnEndY;
            pointToMoveRoundedTurnToStraightNeighbourX = pointToMove.roundedTurnStartX;
            pointToMoveRoundedTurnToStraightNeighbourY = pointToMove.roundedTurnStartY;
            arcAngleChange = arcedNeighbour.arcAngleChange;
            double[] arcCenterAndSignedRadius = GamePolyarcgon.getArcCenterAndSignedRadius(pointToMove.roundedTurnEndX, pointToMove.roundedTurnEndY, arcedNeighbour.roundedTurnStartX, arcedNeighbour.roundedTurnStartY, arcAngleChange);
            arcCenterX = arcCenterAndSignedRadius[0];
            arcCenterY = arcCenterAndSignedRadius[1];
            arcSignedRadius = arcCenterAndSignedRadius[2];
        } else {
            straightNeighbourX = straightNeighbour.roundedTurnStartX;
            straightNeighbourY = straightNeighbour.roundedTurnStartY;
            pointToMoveRoundedTurnToStraightNeighbourX = pointToMove.roundedTurnEndX;
            pointToMoveRoundedTurnToStraightNeighbourY = pointToMove.roundedTurnEndY;
            arcAngleChange = pointToMove.arcAngleChange;
            double[] arcCenterAndSignedRadius = GamePolyarcgon.getArcCenterAndSignedRadius(arcedNeighbour.roundedTurnEndX, arcedNeighbour.roundedTurnEndY, pointToMove.roundedTurnStartX, pointToMove.roundedTurnStartY, arcAngleChange);
            arcCenterX = arcCenterAndSignedRadius[0];
            arcCenterY = arcCenterAndSignedRadius[1];
            arcSignedRadius = arcCenterAndSignedRadius[2];
        }
        double straightSegmentUnitVectorX = pointToMoveRoundedTurnToStraightNeighbourX - straightNeighbourX;
        double straightSegmentUnitVectorY = pointToMoveRoundedTurnToStraightNeighbourY - straightNeighbourY;
        double straightSegmentVectorLength = Math.sqrt(straightSegmentUnitVectorX * straightSegmentUnitVectorX + straightSegmentUnitVectorY * straightSegmentUnitVectorY);

        straightSegmentUnitVectorX /= straightSegmentVectorLength;
        straightSegmentUnitVectorY /= straightSegmentVectorLength;

        double orthogonalDistanceBetweenLineAndArcCenter = straightSegmentUnitVectorX * (arcCenterY - straightNeighbourY) - straightSegmentUnitVectorY * (arcCenterX - straightNeighbourX);
        double orthogonalDistanceBetweenArcCenterAndNewPoint = (straightNeighbourIsPrevArcedNeighbourIsNext ? 1 : -1) * pointToMove.signedRoundedTurnRadius - orthogonalDistanceBetweenLineAndArcCenter;
        double parallelDistanceBetweenArcCenterAndOldPoint = (pointToMove.x - arcCenterX) * straightSegmentUnitVectorX + (pointToMove.y - arcCenterY) * straightSegmentUnitVectorY;
        double parallelDistanceBetweenArcCenterAndNewPoint = Math.copySign(Math.sqrt((arcSignedRadius - pointToMove.signedRoundedTurnRadius) * (arcSignedRadius - pointToMove.signedRoundedTurnRadius) - orthogonalDistanceBetweenArcCenterAndNewPoint * orthogonalDistanceBetweenArcCenterAndNewPoint), parallelDistanceBetweenArcCenterAndOldPoint);

        pointToMove.x = arcCenterX + parallelDistanceBetweenArcCenterAndNewPoint * straightSegmentUnitVectorX - orthogonalDistanceBetweenArcCenterAndNewPoint * straightSegmentUnitVectorY;
        pointToMove.y = arcCenterY + parallelDistanceBetweenArcCenterAndNewPoint * straightSegmentUnitVectorY + orthogonalDistanceBetweenArcCenterAndNewPoint * straightSegmentUnitVectorX;

        double newArcAngleChange = Math.atan2(arcedNeighbour.y - arcCenterY, arcedNeighbour.x - arcCenterX) - Math.atan2(pointToMove.y - arcCenterY, pointToMove.x - arcCenterX);
        if (straightNeighbourIsPrevArcedNeighbourIsNext) {
            arcedNeighbour.arcAngleChange = (newArcAngleChange + Math.copySign(10 * Math.PI, arcedNeighbour.arcAngleChange)) % (2 * Math.PI);
        } else {
            pointToMove.arcAngleChange = (-newArcAngleChange + Math.copySign(10 * Math.PI, pointToMove.arcAngleChange)) % (2 * Math.PI);
        }

        pointToMove.signedVirtualRoundedTurnRadius = pointToMove.signedRoundedTurnRadius;
    }

    private void movePointToMakeVirtualRoundedTurnEqualRoundedTurnBothArced(GamePolyarcgonBuilderPoint pointToMove, GamePolyarcgonBuilderPoint prevPoint, GamePolyarcgonBuilderPoint nextPoint) {
        double[] firstArcCenterAndSignedRadius = GamePolyarcgon.getArcCenterAndSignedRadius(prevPoint.roundedTurnEndX, prevPoint.roundedTurnEndY, pointToMove.roundedTurnStartX, pointToMove.roundedTurnStartY, pointToMove.arcAngleChange);
        double firstArcCenterX = firstArcCenterAndSignedRadius[0];
        double firstArcCenterY = firstArcCenterAndSignedRadius[1];
        double firstSignedRadius = firstArcCenterAndSignedRadius[2];
        double[] secondArcCenterAndSignedRadius = GamePolyarcgon.getArcCenterAndSignedRadius(pointToMove.roundedTurnEndX, pointToMove.roundedTurnEndY, nextPoint.roundedTurnStartX, nextPoint.roundedTurnStartY, nextPoint.arcAngleChange);
        double secondArcCenterX = secondArcCenterAndSignedRadius[0];
        double secondArcCenterY = secondArcCenterAndSignedRadius[1];
        double secondSignedRadius = secondArcCenterAndSignedRadius[2];

        double firstCenterToSecondCenterX = secondArcCenterX - firstArcCenterX;
        double firstCenterToSecondCenterY = secondArcCenterY - firstArcCenterY;

        double distanceBetweenCentersSq = firstCenterToSecondCenterX * firstCenterToSecondCenterX + firstCenterToSecondCenterY * firstCenterToSecondCenterY;
//        double distanceBetweenCenters = Math.sqrt(distanceBetweenCentersSq);
        double distanceFromFirstCenterToNewRoundedTurnSq = (firstSignedRadius - pointToMove.signedRoundedTurnRadius) * (firstSignedRadius - pointToMove.signedRoundedTurnRadius);
        double distanceFromSecondCenterToNewRoundedTurnSq = (secondSignedRadius - pointToMove.signedRoundedTurnRadius) * (secondSignedRadius - pointToMove.signedRoundedTurnRadius);

        double oldRoundedTurnCrossProduct = firstCenterToSecondCenterX * (pointToMove.y - firstArcCenterY) - firstCenterToSecondCenterY * (pointToMove.x - firstArcCenterX);

        double relativeParallelDistanceBetweenFirstArcCenterAndNewRoundedTurn = (distanceBetweenCentersSq + distanceFromFirstCenterToNewRoundedTurnSq - distanceFromSecondCenterToNewRoundedTurnSq) / (2 * distanceBetweenCentersSq);
        double relativeOrthogonalDistanceBetweenFirstArcCenterAndNewRoundedTurn = Math.copySign(Math.sqrt(distanceFromFirstCenterToNewRoundedTurnSq / distanceBetweenCentersSq - relativeParallelDistanceBetweenFirstArcCenterAndNewRoundedTurn * relativeParallelDistanceBetweenFirstArcCenterAndNewRoundedTurn), oldRoundedTurnCrossProduct);

        pointToMove.x = firstArcCenterX + relativeParallelDistanceBetweenFirstArcCenterAndNewRoundedTurn * firstCenterToSecondCenterX - relativeOrthogonalDistanceBetweenFirstArcCenterAndNewRoundedTurn * firstCenterToSecondCenterY;
        pointToMove.y = firstArcCenterY + relativeParallelDistanceBetweenFirstArcCenterAndNewRoundedTurn * firstCenterToSecondCenterY + relativeOrthogonalDistanceBetweenFirstArcCenterAndNewRoundedTurn * firstCenterToSecondCenterX;

        pointToMove.arcAngleChange = (Math.atan2(pointToMove.y - firstArcCenterY, pointToMove.x - firstArcCenterX) - Math.atan2(prevPoint.y - firstArcCenterY, prevPoint.x - firstArcCenterX) + Math.copySign(10 * Math.PI, pointToMove.arcAngleChange)) % (2 * Math.PI);
        nextPoint.arcAngleChange = (Math.atan2(nextPoint.y - secondArcCenterY, nextPoint.x - secondArcCenterX) - Math.atan2(pointToMove.y - secondArcCenterY, pointToMove.x - secondArcCenterX) + Math.copySign(10 * Math.PI, nextPoint.arcAngleChange)) % (2 * Math.PI);

        pointToMove.signedVirtualRoundedTurnRadius = pointToMove.signedRoundedTurnRadius;
    }

    private void processLineOrArcBetweenOneOrTwoRoundedTurns(GamePolyarcgonBuilderPoint prevPoint, GamePolyarcgonBuilderPoint nextPoint) {
        double angleFromPrevRoundedTurnCenterToNextRoundedTurnCenter = Math.atan2(nextPoint.y - prevPoint.y, nextPoint.x - prevPoint.x);
        double distanceFromPrevRoundedTurnCenterToNextRoundedTurnCenter = Math.sqrt((nextPoint.x - prevPoint.x) * (nextPoint.x - prevPoint.x) + (nextPoint.y - prevPoint.y) * (nextPoint.y - prevPoint.y));
        // note the arc angle change between the two points is nextPoint.arcAngleChange, prevPoint.arcAngleChange is irrelevant
        double angleFromPrevRoundedTurnCenterToNextRoundedTurnCenterRelativeToAngleFromArcStartToArcEnd = Math.asin(Math.cos(nextPoint.arcAngleChange / 2.0) * (nextPoint.signedVirtualRoundedTurnRadius - prevPoint.signedVirtualRoundedTurnRadius) / distanceFromPrevRoundedTurnCenterToNextRoundedTurnCenter);
        if (prevPoint.signedVirtualRoundedTurnRadius != 0) {
            double angleAtPrevRoundedTurnCenterBetweenNextRoundedTurnCenterAndPrevRoundedTurnEnd = -nextPoint.arcAngleChange / 2.0 - Math.PI / 2.0 - angleFromPrevRoundedTurnCenterToNextRoundedTurnCenterRelativeToAngleFromArcStartToArcEnd;
            prevPoint.roundedTurnEndAngle = angleFromPrevRoundedTurnCenterToNextRoundedTurnCenter + angleAtPrevRoundedTurnCenterBetweenNextRoundedTurnCenterAndPrevRoundedTurnEnd;

            prevPoint.roundedTurnEndX = prevPoint.x + prevPoint.signedVirtualRoundedTurnRadius * Math.cos(prevPoint.roundedTurnEndAngle);
            prevPoint.roundedTurnEndY = prevPoint.y + prevPoint.signedVirtualRoundedTurnRadius * Math.sin(prevPoint.roundedTurnEndAngle);
        }
        if (nextPoint.signedVirtualRoundedTurnRadius != 0) {
            double angleAtNextRoundedTurnCenterBetweenPrevRoundedTurnCenterAndNextRoundedTurnStart = nextPoint.arcAngleChange / 2.0 + Math.PI / 2.0 - angleFromPrevRoundedTurnCenterToNextRoundedTurnCenterRelativeToAngleFromArcStartToArcEnd;
            nextPoint.roundedTurnStartAngle = (angleFromPrevRoundedTurnCenterToNextRoundedTurnCenter + Math.PI) + angleAtNextRoundedTurnCenterBetweenPrevRoundedTurnCenterAndNextRoundedTurnStart;

            nextPoint.roundedTurnStartX = nextPoint.x + nextPoint.signedVirtualRoundedTurnRadius * Math.cos(nextPoint.roundedTurnStartAngle);
            nextPoint.roundedTurnStartY = nextPoint.y + nextPoint.signedVirtualRoundedTurnRadius * Math.sin(nextPoint.roundedTurnStartAngle);
        }
    }

    public GamePolyarcgonBuilder addCircleContour(double x, double y, double radius, boolean isClockwise) {
        newContour();
        arcTo(x - radius, y, isClockwise ? Math.PI : -Math.PI);
        arcTo(x + radius, y, isClockwise ? Math.PI : -Math.PI);
        newContour();
        return this;
    }

    public GamePolyarcgonBuilder rotate(double clockwiseRotationAmount, double rotationCenterX, double rotationCenterY) {
        double cosClockwiseRotationAmount = Math.cos(clockwiseRotationAmount);
        double sinClockwiseRotationAmount = Math.sin(clockwiseRotationAmount);
        for (ArrayList<GamePolyarcgonBuilderPoint> contour : contoursOfPoints) {
            for (GamePolyarcgonBuilderPoint point : contour) {
                double newX = rotationCenterX + (point.x - rotationCenterX) * cosClockwiseRotationAmount - (point.y - rotationCenterY) * sinClockwiseRotationAmount;
                double newY = rotationCenterY + (point.y - rotationCenterY) * cosClockwiseRotationAmount + (point.x - rotationCenterX) * sinClockwiseRotationAmount;
                point.x = point.roundedTurnStartX = point.roundedTurnEndX = newX;
                point.y = point.roundedTurnStartY = point.roundedTurnEndY = newY;
            }
        }
        double rotationCenterXOr0 = centerOfMassIsRelativeToDefaultCenterOfMass ? 0 : rotationCenterX;
        double rotationCenterYOr0 = centerOfMassIsRelativeToDefaultCenterOfMass ? 0 : rotationCenterY;
        double newCenterOfMassX = rotationCenterXOr0 + (centerOfMassX - rotationCenterXOr0) * cosClockwiseRotationAmount - (centerOfMassY - rotationCenterYOr0) * sinClockwiseRotationAmount;
        double newCenterOfMassY = rotationCenterYOr0 + (centerOfMassY - rotationCenterYOr0) * cosClockwiseRotationAmount + (centerOfMassX - rotationCenterXOr0) * sinClockwiseRotationAmount;
        centerOfMassX = newCenterOfMassX;
        centerOfMassY = newCenterOfMassY;
        return this;
    }

    public GamePolyarcgonBuilder mirror(double mirrorLineAngle, double mirrorLinePosition) {
        double cosMirrorLineAngle = Math.cos(mirrorLineAngle);
        double sinMirrorLineAngle = Math.sin(mirrorLineAngle);
        double cos2MirrorLineAngle = Math.cos(2 * mirrorLineAngle);
        double sin2MirrorLineAngle = Math.sin(2 * mirrorLineAngle);
        for (ArrayList<GamePolyarcgonBuilderPoint> contour : contoursOfPoints) {
            for (GamePolyarcgonBuilderPoint point : contour) {
                double newX = point.x * cos2MirrorLineAngle + point.y * sin2MirrorLineAngle - 2 * mirrorLinePosition * sinMirrorLineAngle;
                double newY = point.x * sin2MirrorLineAngle - point.y * cos2MirrorLineAngle + 2 * mirrorLinePosition * cosMirrorLineAngle;
                point.x = point.roundedTurnStartX = point.roundedTurnEndX = newX;
                point.y = point.roundedTurnStartY = point.roundedTurnEndY = newY;
            }
        }
        double mirrorLinePositionOr0 = centerOfMassIsRelativeToDefaultCenterOfMass ? 0 : mirrorLinePosition;
        double newCenterOfMassX = centerOfMassX * cos2MirrorLineAngle + centerOfMassY * sin2MirrorLineAngle - 2 * mirrorLinePositionOr0 * sinMirrorLineAngle;
        double newCenterOfMassY = centerOfMassX * sin2MirrorLineAngle - centerOfMassY * cos2MirrorLineAngle + 2 * mirrorLinePositionOr0 * cosMirrorLineAngle;
        centerOfMassX = newCenterOfMassX;
        centerOfMassY = newCenterOfMassY;
        invert(true);
        return this;
    }

    public GamePolyarcgonBuilder invert(boolean justReverse) {
        for (ArrayList<GamePolyarcgonBuilderPoint> contour : contoursOfPoints) {
            GamePolyarcgonBuilderPoint pointToSwap;
            int size = contour.size();
            for (int i = 0; i < size / 2; i++) {
                pointToSwap = contour.get(i);
                contour.set(i, contour.get(size - 1 - i));
                contour.set(size - 1 - i, pointToSwap);
            }

            double firstX = contour.get(0).x;
            double firstY = contour.get(0).y;
            for (int i = 0; i < size; i++) {
                GamePolyarcgonBuilderPoint point = contour.get(i);
                if (!justReverse) {
                    point.arcAngleChange *= -1;
                }
                if (i + 1 < size) {
                    GamePolyarcgonBuilderPoint nextPoint = contour.get(i + 1);
                    point.x = point.roundedTurnStartX = point.roundedTurnEndX = nextPoint.x;
                    point.y = point.roundedTurnStartY = point.roundedTurnEndY = nextPoint.y;
                } else {
                    point.x = point.roundedTurnStartX = point.roundedTurnEndX = firstX;
                    point.y = point.roundedTurnStartY = point.roundedTurnEndY = firstY;
                }
            }
        }
        return this;
    }

    //    static class ContourlessSegment {
//        GamePolyarcgonBuilderPoint thisPoint;
//        ContourlessSegment nextPoint;
//        TreeMap<Double, Boolean> intersections; //key is fraction along the curve, value is whether it's a start or an end
//
//        public ContourlessSegment(GamePolyarcgonBuilderPoint thisPoint, ContourlessSegment nextPoint) {
//            this.thisPoint = thisPoint;
//            this.nextPoint = nextPoint;
//        }
//    }
    public GamePolyarcgonBuilder unionLastContour() {
        if (contoursOfPoints.get(contoursOfPoints.size() - 1).isEmpty()) {
            contoursOfPoints.remove(contoursOfPoints.size() - 1);
        }
        ArrayList<ArrayList<GamePolyarcgonBuilderPoint>> lastContour = new ArrayList<>();
        lastContour.add(contoursOfPoints.remove(contoursOfPoints.size() - 1));
        contoursOfPoints = unionOrIntersection(contoursOfPoints, lastContour, true);
        newContour();
        return this;
    }

    public GamePolyarcgonBuilder intersectionLastContour() {
        if (contoursOfPoints.get(contoursOfPoints.size() - 1).isEmpty()) {
            contoursOfPoints.remove(contoursOfPoints.size() - 1);
        }
        ArrayList<ArrayList<GamePolyarcgonBuilderPoint>> lastContour = new ArrayList<>();
        lastContour.add(contoursOfPoints.remove(contoursOfPoints.size() - 1));
        contoursOfPoints = unionOrIntersection(contoursOfPoints, lastContour, false);
        newContour();
        return this;
    }

    private static ArrayList<ArrayList<GamePolyarcgonBuilderPoint>> unionOrIntersection(ArrayList<ArrayList<GamePolyarcgonBuilderPoint>> shape1, ArrayList<ArrayList<GamePolyarcgonBuilderPoint>> shape2, boolean isUnion) {
        /*
        Algorithm for union and intersections:
        Don't create a turn each crossing between two edges, which can be inaccurate due to
        an edge crossing the other shape exactly at one of its corners (or a corner of each shape being at the same point),
        causing the edge to intersect two edges in the other shape (or no edges in the other shape).

        Instead break each edge into pieces inside the other shape and pieces outside. Only keep the pieces outside (or vice versa depending on union/intersection)
        and then try to create a path which goes from the end of one piece to the start of another piece as close as possible.

        Though this might still have pieces "doubly inside" or not inside... I guess having pieces "doubly inside" is okay,
        so maybe make the collision algorithm more greedily seek collisions?

        Each time a piece's winding number is greater than 1 it is reduced to 1, vice versa for -1. This way +1 +1 -1 adds to 0 instead of +1.

         */

//        ArrayList<ContourlessSegment> shape1Segments = convertToContourlessSegments(shape1);
//        ArrayList<ContourlessSegment> shape2Segments = convertToContourlessSegments(shape2);
//        ArrayList<ContourlessSegment> unionSegments = new ArrayList<>();
//        for (ContourlessSegment shape1Segment : shape1Segments) {
//            for (ContourlessSegment shape2Segment : shape2Segments) {
//            }
//        }
//        return convertToContoursOfPoints(unionSegments);

        ArrayList<ArrayList<GamePolyarcgonBuilderPoint>> result = new ArrayList<>();

        GamePolyarcgonBuilder builder1 = new GamePolyarcgonBuilder();
        builder1.contoursOfPoints = shape1;
        GamePolyarcgonBuilder builder2 = new GamePolyarcgonBuilder();
        builder2.contoursOfPoints = shape2;
        GamePolyarcgon newShape1 = builder1.buildAndReset();
        GamePolyarcgon newShape2 = builder2.buildAndReset();
        IntersectionsCalculator unionCalculator = new IntersectionsCalculator(isUnion, newShape1, newShape2);
        GamePolyarcgon.collision(newShape1, newShape2, unionCalculator);
        GamePolyarcgon.PolyarcgonPointCache[] shape1Points = newShape1.pointsCache.get();
        GamePolyarcgon.PolyarcgonPointCache[] shape2Points = newShape2.pointsCache.get();
        GamePolyarcgon.PolyarcgonPointCache[][] allPoints = new GamePolyarcgon.PolyarcgonPointCache[][]{shape1Points, shape2Points};

        // first we fix the intersection list in each point so it's made up of
        // start end pairs
        // also delete start/end pairs too close together (but not end/start pairs)
        for (GamePolyarcgon.PolyarcgonPointCache[] points : allPoints) {
            GamePolyarcgon.PolyarcgonPointCache lastPoint = points[points.length - 1];
            for (GamePolyarcgon.PolyarcgonPointCache nextPoint : points) {
                if (!nextPoint.getNonCachePoint().isMoveToWithoutLineEtc) {
                    IntersectionsCalculator.UnionIntersectionData data = unionCalculator.intersections.get(nextPoint);
                    assert data != null;
                    //greedy assume each segment is included because can exclude later if it doesn't form a loop
                    double intersectionFraction;
                    double lastIntersectionFraction = 0;
                    boolean lastIntersectionWasStart = false; // has to be false by default to avoid triggering adding an end if the loop is empty
                    double lastLastIntersectionFraction = 0;
                    // don't iterate entry set because modifying list as we go
                    for (Map.Entry<Double, Intersection> intersectionEntry = data.intersectionList.firstEntry(); intersectionEntry != null; intersectionEntry = data.intersectionList.higherEntry(intersectionFraction)) {
                        intersectionFraction = intersectionEntry.getKey();
                        Intersection intersection = intersectionEntry.getValue();
                        boolean intersectionWasStart = intersection.windingNumber < 0;
                        if (intersectionWasStart) {
                            //i.e. this intersection is a start
                            intersection.windingNumber = -1;
                            if (lastIntersectionWasStart) {
                                data.intersectionList.remove(intersectionFraction);
//                                intersectionWasStart = lastIntersectionWasStart;
                                intersectionFraction = lastIntersectionFraction;
                            }
                        } else {
                            //i.e. this intersection is an end
                            intersection.windingNumber = 1;
                            if (lastIntersectionWasStart) {
                                // check if the last intersection was too short
                                if (intersectionFraction - lastIntersectionFraction <= MAX_ROUNDING_ERROR) {
                                    data.intersectionList.remove(intersectionFraction);
                                    data.intersectionList.remove(lastIntersectionFraction);
                                    intersectionFraction = lastLastIntersectionFraction;
                                }
                            } else {
                                if (lastIntersectionFraction == 0) {
                                    // add start point at very start if needed
                                    data.intersectionList.put(0.0, new Intersection(lastPoint.getX(), lastPoint.getY(), -1));
                                } else {
                                    data.intersectionList.remove(lastIntersectionFraction); //remove the last end because there's an end after
                                    lastIntersectionFraction = lastLastIntersectionFraction;
                                }
                            }
                        }

                        lastLastIntersectionFraction = lastIntersectionFraction;
                        lastIntersectionWasStart = intersectionWasStart;
                        lastIntersectionFraction = intersectionFraction;
                    }
                    // add ending at very end if needed
                    if (lastIntersectionWasStart) {
                        if (lastIntersectionFraction >= 1 - MAX_ROUNDING_ERROR) {
                            data.intersectionList.remove(lastIntersectionFraction);
                        } else {
                            data.intersectionList.put(1.0, new Intersection(nextPoint.getX(), nextPoint.getY(), 1));
                        }
                    }
                }
                lastPoint = nextPoint;
            }
        }

        // next, we iterate over every pair of start and end between every pair of points, trying to form a contour loop out of it,
        // until there are none left
        for (GamePolyarcgon.PolyarcgonPointCache[] points : allPoints) {
            for (GamePolyarcgon.PolyarcgonPointCache nextPoint : points) {
                if (!nextPoint.getNonCachePoint().isMoveToWithoutLineEtc) {
                    IntersectionsCalculator.UnionIntersectionData data = unionCalculator.intersections.get(nextPoint);
                    assert data != null;

                    // this loop keeps trying to build a new contour by using up intersections,
                    // starting with intersections in this point,
                    // until this point has no more intersections left
                    while (!data.intersectionList.isEmpty()) {

                        ArrayList<GamePolyarcgon.PolyarcgonPointCache> newContourPoints = new ArrayList<>();
                        ArrayList<Map.Entry<Double, Intersection>> newContourStarts = new ArrayList<>();
                        ArrayList<Map.Entry<Double, Intersection>> newContourEnds = new ArrayList<>();


                        GamePolyarcgon.PolyarcgonPointCache newContourNextPoint = nextPoint;
                        Map.Entry<Double, Intersection> newContourNextStart = data.intersectionList.firstEntry();
                        assert newContourNextStart != null;
                        Map.Entry<Double, Intersection> newContourNextEnd = data.intersectionList.higherEntry(newContourNextStart.getKey());

                        // this loop keeps trying to add a segment to the new contour, until the new contour forms a loop
                        // or fails to do so (in which case we delete the last segment and start over)
                        int loopsAt = -1;
                        while (true) {
                            assert newContourNextEnd != null;

                            newContourPoints.add(newContourNextPoint);
                            newContourStarts.add(newContourNextStart);
                            newContourEnds.add(newContourNextEnd);

                            Intersection endIntersection = newContourNextEnd.getValue();

                            double closestDistanceSq = Double.MAX_VALUE;

                            // search all points again for what segment to add to the new contour
                            for (GamePolyarcgon.PolyarcgonPointCache[] otherPoints : allPoints) {
                                for (GamePolyarcgon.PolyarcgonPointCache otherNextPoint : otherPoints) {
                                    if (!otherNextPoint.getNonCachePoint().isMoveToWithoutLineEtc) {
                                        IntersectionsCalculator.UnionIntersectionData otherData = unionCalculator.intersections.get(otherNextPoint);
                                        assert otherData != null;
                                        for (Map.Entry<Double, Intersection> otherIntersectionEntry : otherData.intersectionList.entrySet()) {
                                            Intersection otherIntersection = otherIntersectionEntry.getValue();
                                            if (otherIntersection.windingNumber < 0) { // i.e. a start not an end
                                                double distanceSq = (otherIntersection.x - endIntersection.x) * (otherIntersection.x - endIntersection.x) + (otherIntersection.y - endIntersection.y) * (otherIntersection.y - endIntersection.y);
                                                if (distanceSq < 1000 * 1000 * MAX_ROUNDING_ERROR * MAX_ROUNDING_ERROR && distanceSq < closestDistanceSq) {
                                                    closestDistanceSq = distanceSq;
                                                    newContourNextPoint = otherNextPoint;
                                                    newContourNextStart = otherIntersectionEntry;
                                                    newContourNextEnd = otherData.intersectionList.higherEntry(otherIntersectionEntry.getKey());
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (closestDistanceSq == Double.MAX_VALUE) {
                                break;
                            }

                            // check for a finished loop, not necessarily connected to the first point in the new contour
                            for (int i = 0; i < newContourPoints.size(); i++) {
                                if (newContourPoints.get(i).equals(newContourNextPoint) && newContourStarts.get(i).equals(newContourNextStart)) {
                                    // we have a loop, so create a new contour
                                    loopsAt = i;
                                    break;
                                }
                            }

                            if (loopsAt != -1) {
                                break;
                            }
                        }
                        ArrayList<GamePolyarcgonBuilderPoint> newContour;
                        int removeFrom;
                        if (loopsAt != -1) {
                            // we have a loop, so create a new contour and remove the segments from the set
                            newContour = new ArrayList<>();
                            result.add(newContour);
                            removeFrom = loopsAt;
                        } else {
                            // this never loops and just ends, so no new contour but still remove the last segment from the set
                            newContour = null;
                            removeFrom = newContourPoints.size() - 1;
                        }

                        for (int i = removeFrom; i < newContourPoints.size(); i++) {
                            double start = newContourStarts.get(i).getKey();
                            Map.Entry<Double, Intersection> newEndEntry = newContourEnds.get(i);
                            double end = newEndEntry.getKey();

                            GamePolyarcgon.PolyarcgonPointCache newContourPoint = newContourPoints.get(i);
                            IntersectionsCalculator.UnionIntersectionData currentData = unionCalculator.intersections.get(newContourPoint);
                            assert currentData != null;

                            currentData.intersectionList.remove(start);
                            currentData.intersectionList.remove(end);

                            if (newContour != null) {
                                Intersection intersection = newEndEntry.getValue();
                                newContour.add(new GamePolyarcgonBuilderPoint(intersection.x, intersection.y, (end - start) * newContourPoint.getNonCachePoint().arcAngleChange));
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * Lists the each intersection between the two shapes without calculating things like area
     */
    static class IntersectionsCalculator implements OverlapCalculator {
        final boolean isUnion;
        final int shape1DefaultWindingNumber;
        final int shape2DefaultWindingNumber;
        final HashMap<GamePolyarcgon.PolyarcgonPointCache, UnionIntersectionData> intersections;

        public IntersectionsCalculator(boolean isUnion, GamePolyarcgon shape1, GamePolyarcgon shape2) {
            this.isUnion = isUnion;
            shape1DefaultWindingNumber = ((shape2.getMass() > 0) == !isUnion) ? 0 : 1;
            shape2DefaultWindingNumber = ((shape1.getMass() > 0) == !isUnion) ? 0 : 1;
            intersections = new HashMap<>();

            //if union:
            //If both shapes positive, only keep parts of each shape outside the other shape. Each segment starts as 1, is reduced to 0 when circled clockwise
            //If second shape negative, only keep parts of each shape "outside" the other shape.
            //Each segment in first shape starts as 0, is increased to 1 when circled counterclockwise
            //Each segment in second shape starts as 1, is decreased to 0 when circled clockwise
            //If both shapes negative, only keep parts of each shape "outside" the other shape. Each segment starts as 0, is increased to 0 when circled counterclockwise
            //if intersection:
            //If both shapes positive, only keep parts of each shape inside the other shape. Each segment starts as 0, is increased to 1 when circled clockwise
            //If second shape negative, only keep parts of each shape "inside" the other shape.
            //Each segment in first shape starts as 1, is decreased to 0 when circled counterclockwise
            //Each segment in second shape starts as 0, is increased to 1 when circled clockwise
            //If both shapes negative, only keep parts of each shape "inside" the other shape. Each segment starts as 1, is decreased to 0 when circled counterclockwise
            for (GamePolyarcgon.PolyarcgonPointCache point : shape1.pointsCache.get()) {
                UnionIntersectionData data = new UnionIntersectionData();
                if (shape1DefaultWindingNumber != 0) {
                    data.intersectionList.put(1.0, new Intersection(point.getX(), point.getY(), shape1DefaultWindingNumber));
                }
                intersections.put(point, data);
            }
            for (GamePolyarcgon.PolyarcgonPointCache point : shape2.pointsCache.get()) {
                UnionIntersectionData data = new UnionIntersectionData();
                if (shape2DefaultWindingNumber != 0) {
                    data.intersectionList.put(1.0, new Intersection(point.getX(), point.getY(), shape2DefaultWindingNumber));
                }
                intersections.put(point, data);
            }
        }

        static class UnionIntersectionData {
            TreeMap<Double, Intersection> intersectionList = new TreeMap<>(); //key is contour, value's key is fraction along the curve
        }

        @Override
        public void addLineSegmentToOverlap(double startX, double startY, double endX, double endY, int windingFactor, boolean lineSegmentIsFirstShape, GamePolyarcgon.PolyarcgonPointCache nextPoint, boolean isRealIntersection) {

            final double nextX = nextPoint.getX();
            final double nextY = nextPoint.getY();
            final double distance = Math.sqrt((endX - startX) * (endX - startX) + (endY - startY) * (endY - startY));
            final double fullDistance = Math.sqrt((nextX - startX) * (nextX - startX) + (nextY - startY) * (nextY - startY));

            addLineSegmentOrArc(endX, endY, windingFactor, nextPoint, lineSegmentIsFirstShape, distance / fullDistance);

        }

        @Override
        public void addArcToOverlap(double radiusOfCurvature, double arcCenterX, double arcCenterY, double arcStartX, double arcStartY, double arcEndX, double arcEndY, double arcAngleChange, int windingFactor, boolean arcIsFirstShape, GamePolyarcgon.PolyarcgonPointCache nextPoint, boolean isRealIntersection) {

            addLineSegmentOrArc(arcEndX, arcEndY, windingFactor, nextPoint, arcIsFirstShape, arcAngleChange / nextPoint.getNonCachePoint().arcAngleChange);

        }

        private void addLineSegmentOrArc(double endX, double endY, int windingFactor, GamePolyarcgon.PolyarcgonPointCache nextPoint, boolean lineSegmentIsFirstShape, double fraction) {

                /*
                Assuming we are iterating over the edges in order, the only reason we should have containing intersections at any time
                is if it's a glitch where a corner of one shape intersects with a segment of another shape twice
                (the segment before the corner and after the corner both intersects with the other segment)

                 */

            UnionIntersectionData data = intersections.get(nextPoint);
            assert data != null;

            if (isUnion) {
                windingFactor = -windingFactor;
            }

            //need to ensure it's between -1 and 1
            //or no even stricter, it needs to be no two adds or subtracts in a row. So it can go 0 1 0 1 0 1? Or no
            //it can have two adds or subtracts in a row actually.
            //Ahh maybe give up on policing the partially complete list
            //instead you have to just allow anything in the partially complete list, but after it is complete you
            //remove redundant intersections

            //but then actually the intersections on the ray need to be ordered rather than all being seen at the same point at the end
            //and the closest one should have the final say

            //or maybe intersections on the ray of a line segment can be like that,
            //while for the intersections on the ray of an arc, you avoid two in a row in the same direction
            //if the start and end angles aren't far enough

            //or maybe just ignore errors for intersecting a ray too many times. It'll lead to extra segments
            //but maybe we can just delete all segments which don't form a loop.
            Intersection currentIntersection = data.intersectionList.get(fraction);
            if (currentIntersection == null) {
                data.intersectionList.put(fraction, new Intersection(endX, endY, windingFactor));
            } else {
                currentIntersection.windingNumber += windingFactor;
                if (currentIntersection.windingNumber == 0) {
                    data.intersectionList.remove(fraction);
                }
            }
        }
    }

    public GamePolyarcgon build() {
        return new GamePolyarcgon(this);
    }

    /**
     * Builds the "polyarcgon"
     * Also deletes everything in this builder and allows it to be used again.
     *
     * @return the polyarcgon
     */
    public GamePolyarcgon buildAndReset() {
        GamePolyarcgon gamePolyarcgon = new GamePolyarcgon(this);
        reset();
        return gamePolyarcgon;
    }

    /**
     * Deletes everything in this builder and allows it to be used again
     *
     * @return this
     */
    public GamePolyarcgonBuilder reset() {
        if (contoursOfPoints == null) {
            contoursOfPoints = new ArrayList<>();
        } else {
            contoursOfPoints.clear();
        }
        contoursOfPoints.add(new ArrayList<>());
        centerOfMassX = 0;
        centerOfMassY = 0;
        centerOfMassIsRelativeToDefaultCenterOfMass = true;
        density = 1;
        additionalAttributes = null;
        return this;
    }

    /**
     * Unimportant function that removes redundant points.
     */
    private void removeRedundantPoints() {
        // Redundant points may happen for circles, which technically have 0 points,
        // but need 2 points in order to define their position and size.
        // If a circle is intersected with another shape and gains more
        // points, those points can be removed.
        // Or if a circle is actually not a full circle, but acts as
        // a circle initially due to the start point and end point being
        // the same point (with a signedVirtualRoundedTurnRadius that
        // will later move them apart).
        //TODO not done
    }
}
