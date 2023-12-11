package com.github.eztang00.firstandroidgame.gamephysics;

import java.util.function.BiFunction;

/**
 * An area (represented by a GameShape) that can push other shapes inside it.
 * In ripple golf this would be the ripple that pushes the ball (or other shapes)
 */
public class GameForceField {
    /**
     * For the strength attribute of pushAwayForceField() etc.
     */
    public static final double PREFERRED_STRENGTH = 0.01;

    public GameShape affectedArea;
    BiFunction<GameShape, OverlapAreaIntegralCalculator, ForceAndTorque> forceFunction;

    public GameForceField(GameShape affectedArea, BiFunction<GameShape, OverlapAreaIntegralCalculator, ForceAndTorque> forceFunction) {
        this.affectedArea = affectedArea;
        this.forceFunction = forceFunction;
    }

    public ForceAndTorque apply(GameShape shape) {
        if (affectedArea != null) {
            OverlapAreaIntegralCalculator calculator = new OverlapAreaIntegralCalculator(shape, affectedArea);
            shape.collision(affectedArea, true, false, true, calculator);
            if (!calculator.isAlmostZero()) {
                return forceFunction.apply(shape, calculator);
            }
        }
        return new ForceAndTorque(0, 0, 0, 0, shape);
    }

    public static GameForceField pushAwayForceField(GameShape affectedArea, double centerX, double centerY, double strength) {
        return new GameForceField(affectedArea, (shape, overlapAreaIntegralCalculator) -> {
            double xDisplacement = overlapAreaIntegralCalculator.overlapXAreaIntegral / overlapAreaIntegralCalculator.overlapArea - centerX;
            double yDisplacement = overlapAreaIntegralCalculator.overlapYAreaIntegral / overlapAreaIntegralCalculator.overlapArea - centerY;
            double resultingForce = strength * overlapAreaIntegralCalculator.overlapArea / shape.getArea();
            double distance = Math.sqrt(xDisplacement * xDisplacement + yDisplacement * yDisplacement);
            if (distance != 0) { // distance might equal zero if the force field is inside
                return new ForceAndTorque(centerX, centerY, resultingForce * xDisplacement / distance, resultingForce * yDisplacement / distance, shape);
            } else {
                return new ForceAndTorque(0, 0, 0, 0, shape);
            }
        });
    }

    public static GameForceField simplePushAwayForceField(GameShape affectedArea, double strength) {
        GameForceField newForceField = new GameForceField(affectedArea, null);
        //we have to have the force function reference the force field so have to make it after
        newForceField.forceFunction = (shape, overlapAreaIntegralCalculator) -> {
            double xDisplacement = overlapAreaIntegralCalculator.overlapXAreaIntegral / overlapAreaIntegralCalculator.overlapArea - newForceField.affectedArea.getX();
            double yDisplacement = overlapAreaIntegralCalculator.overlapYAreaIntegral / overlapAreaIntegralCalculator.overlapArea - newForceField.affectedArea.getY();
            double resultingForce = strength * overlapAreaIntegralCalculator.overlapArea;
            double distance = Math.sqrt(xDisplacement * xDisplacement + yDisplacement * yDisplacement);
            if (distance != 0) { // distance might equal zero if the force field is inside
                return new ForceAndTorque(newForceField.affectedArea.getX(), newForceField.affectedArea.getY(), resultingForce * xDisplacement / distance, resultingForce * yDisplacement / distance, shape);
            } else {
                return new ForceAndTorque(0, 0, 0, 0, shape);
            }
        };
        return newForceField;
    }
}
