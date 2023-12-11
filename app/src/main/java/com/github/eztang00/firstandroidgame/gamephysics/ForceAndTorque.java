package com.github.eztang00.firstandroidgame.gamephysics;

public class ForceAndTorque {
    GameShape shape;
    double forceActingOnShapeX;
    double forceActingOnShapeY;
    double torqueActingOnShape;

    public ForceAndTorque(double x, double y, double forceActingOnShapeX, double forceActingOnShapeY, GameShape shape) {

        this.shape = shape;
        this.forceActingOnShapeX = forceActingOnShapeX;
        this.forceActingOnShapeY = forceActingOnShapeY;

        if (shape != null) {
            torqueActingOnShape = (x - shape.getX()) * forceActingOnShapeY - (y - shape.getY()) * forceActingOnShapeX;
        }
    }

    public ForceAndTorque(OverlapGradientForceCalculator overlap, boolean onFirstShape) {
        if (onFirstShape) {
            shape = overlap.firstShape;
        } else {
            shape = overlap.otherShape;

        }
        addForceAndTorque(overlap);
    }

    public void addForceAndTorque(OverlapGradientForceCalculator other) {
        // divide by overlap perimeter not force otherwise torque becomes near infinite if force zero
        if (other.overlapPerimeter != 0) {
            double depth = other.overlapArea / other.overlapPerimeter;
            int factor = 0;
            if (shape != null) {
                if (shape == other.firstShape) {
                    factor = 1;
                    torqueActingOnShape += other.overlapGradientTorqueOnFirstShape * depth;
                } else if (shape == other.otherShape) {
                    factor = -1;
                    torqueActingOnShape += other.overlapGradientTorqueOnOtherShape * depth;
                }
            }
            forceActingOnShapeX += factor * other.overlapGradientForceX * depth;
            forceActingOnShapeY += factor * other.overlapGradientForceY * depth;
        }
    }

    public void addForceAndTorque(ForceAndTorque other) {
        torqueActingOnShape += other.torqueActingOnShape;
        forceActingOnShapeX += other.forceActingOnShapeX;
        forceActingOnShapeY += other.forceActingOnShapeY;
    }


    public void multiplyIntensity(double factor) {
        forceActingOnShapeX *= factor;
        forceActingOnShapeY *= factor;
        torqueActingOnShape *= factor;
    }

    public boolean isAlmostZero() {
        double area = shape.getArea();
        return (forceActingOnShapeX * forceActingOnShapeX + forceActingOnShapeY * forceActingOnShapeY) / area + (torqueActingOnShape * torqueActingOnShape) / area / area < 0.000001 * 0.000001;
    }

    public OverlapGradientForceCalculator asOverlapGradientForceCalculator() {
        OverlapGradientForceCalculator force = new OverlapGradientForceCalculator(shape, null);
        force.overlapGradientForceX = forceActingOnShapeX;
        force.overlapGradientForceY = forceActingOnShapeY;
        force.overlapGradientTorqueOnFirstShape = torqueActingOnShape;
        // invert the formula for converting to force and torque
        force.overlapArea = 1;
        force.overlapPerimeter = 1;
        return force;
    }
}
