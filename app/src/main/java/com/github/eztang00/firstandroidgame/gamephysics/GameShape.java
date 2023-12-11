package com.github.eztang00.firstandroidgame.gamephysics;

import android.graphics.Canvas;

/**
 * These are shapes inside the game.
 * The main class that implements this is the GamePolyarcgon
 */
public interface GameShape {
    void collision(GameShape shape, boolean isThisMovable, boolean isOtherShapeMovable, boolean thisIsFirstShape, OverlapCalculator overlapCalculator);

    void draw(Canvas canvas);

    void receiveForce(ForceAndTorque collision);

//    void receiveForce(double forceX, double forceY, double torque);

    double getMass();

    double getArea();

    double getBoundingRadius();

    double getX();

    double getY();

    double getMomentOfInertia();

    void setPos(double x, double y);

    void setRotation(double rotationRadians);
}
