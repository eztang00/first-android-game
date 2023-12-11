package com.github.eztang00.firstandroidgame.gamephysics;

import android.graphics.Canvas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * This 2D physics engine has no inertia so objects stop where they stand as soon as forces stop.
 */
public class GamePhysicsEngine {
    // 1 does not push things out of walls (thanks to more shape collisions than ripple collision simulations
    static final double RIPPLE_COLLISION_FACTOR = 1;
//    static final double RIPPLE_COLLISION_FACTOR = 0.000001;

    static final double PREFERRED_MAX_ANGLE_BETWEEN_NET_FORCE_AND_FORCE_FIELDS_FORCE = Math.PI * 3 / 4;
    // try 0.5 since not that much risk pushing into walls?
    // no 0.5 too much because long rectangle can stab into walls being pushed by ripple alone
    static final double RIPPLE_WEAKENING_FACTOR_WHEN_MOVING = 0.2;

    // want 30 because otherwise movement resolution too low things don't move smoothly
    static final int SHAPE_COLLISION_SIMULATIONS_PER_FRAME = 30;

    // 0.499 sometimes pushes things sorta deep into walls though not through
//    static final int SHAPE_RIPPLE_COLLISION_SIMULATIONS_PER_FRAME = (int) (0.499 * SHAPE_COLLISION_SIMULATIONS_PER_FRAME);
    // 0.3 I feel is the best because avoid ever pushing things too deep into walls
    // now 0.8 because reduced force
    static final int SHAPE_COLLISION_SIMULATIONS_WITH_FORCE_FIELDS_PER_FRAME = (int) (0.8 * SHAPE_COLLISION_SIMULATIONS_PER_FRAME);

    // want 100 otherwise things don't move far enough stop before hitting corner
    static final double SHAPE_COLLISION_SIMULATION_MOVEMENT_SPEED = ((double) 100) / SHAPE_COLLISION_SIMULATIONS_PER_FRAME;
    final ArrayList<GameShape> unmovableShapes;
    final ArrayList<GameShape> movableShapes;
    public final ArrayList<GameForceField> forceFields;
    final ArrayList<BiPredicate<GameShape, GameShape>> collisionRules;

    public GamePhysicsEngine() {
        unmovableShapes = new ArrayList<>();
        movableShapes = new ArrayList<>();
        forceFields = new ArrayList<>();
        collisionRules = new ArrayList<>();
    }

    public void addWall(GameShape shape) {
        unmovableShapes.add(shape);
    }

    public void addMovableShape(GameShape shape) {
        movableShapes.add(shape);
    }

    public void update() {

        //collision
        for (int collisionSimulations = 0; collisionSimulations < SHAPE_COLLISION_SIMULATIONS_PER_FRAME; collisionSimulations++) {
            boolean includeForceFieldsInSimulation = (collisionSimulations < SHAPE_COLLISION_SIMULATIONS_WITH_FORCE_FIELDS_PER_FRAME);
            boolean collided = updateCollisionSimulationAndReturnWhetherCollided(includeForceFieldsInSimulation);
            if (!collided) {
                break;
            }
        }
    }

    private boolean updateCollisionSimulationAndReturnWhetherCollided(boolean applyForceFields) {
        /*
        TODO: replace collision algorithm with one that enforces objects pushing away never move towards each other
         */
        HashMap<GameShape, ArrayList<OverlapGradientForceCalculator>> temporaryCollisionData = new HashMap<>();
        for (GameShape shape : movableShapes) {
            otherShapesLoop:
            for (GameShape otherShape : unmovableShapes) {
                for (BiPredicate<GameShape, GameShape> collisionRule : collisionRules) {
                    if (!collisionRule.test(shape, otherShape)) {
                        continue otherShapesLoop;
                    }
                }
                OverlapGradientForceCalculator collision = new OverlapGradientForceCalculator(shape, otherShape);
                shape.collision(otherShape, true, false, true, collision);
                if (!collision.isAlmostZero()) {
//                    Log.i("me", String.format("%.2f, %.2f", collision.repulsionForceAwayFromShape1x, collision.repulsionForceAwayFromShape1y));
                    addCollisionToTemporaryCollisionData(collision, shape, temporaryCollisionData);
                }
            }
            otherShapesLoop:
            for (GameShape otherShape : movableShapes) {
                if (otherShape == shape) {
                    continue;
                } else {
                    for (BiPredicate<GameShape, GameShape> collisionRule : collisionRules) {
                        if (!collisionRule.test(shape, otherShape)) {
                            continue otherShapesLoop;
                        }
                    }
                    OverlapGradientForceCalculator collision = new OverlapGradientForceCalculator(shape, otherShape);
                    shape.collision(otherShape, true, true, true, collision);
                    if (!collision.isAlmostZero()) {
                        addCollisionToTemporaryCollisionData(collision, shape, temporaryCollisionData);
                        addCollisionToTemporaryCollisionData(collision, otherShape, temporaryCollisionData);
                    }
                }
            }
            if (applyForceFields) {
                collisionsOnShapeFromForceFields(shape, temporaryCollisionData);
            }
        }
        boolean collided = !temporaryCollisionData.isEmpty();
        while (!temporaryCollisionData.isEmpty()) {
            Iterator<Map.Entry<GameShape, ArrayList<OverlapGradientForceCalculator>>> iterator = temporaryCollisionData.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<GameShape, ArrayList<OverlapGradientForceCalculator>> e = iterator.next();
                HashMap<GameShape, ForceAndTorque> relatedCollisions = new HashMap<>();
                double maxMovement = recursivelyCalculateRelatedCollisionsAndFindMaxMovement(relatedCollisions, e.getKey(), e.getValue(), 0.001, temporaryCollisionData);
//                Log.i("me", String.format("max movement: %.2f", maxMovement));
                for (Map.Entry<GameShape, ForceAndTorque> push : relatedCollisions.entrySet()) {
                    push.getValue().multiplyIntensity(SHAPE_COLLISION_SIMULATION_MOVEMENT_SPEED / maxMovement);
                    push.getKey().receiveForce(push.getValue());
                }
                if (relatedCollisions.size() == 1) {
                    iterator.remove();
                } else {
                    for (GameShape shape : relatedCollisions.keySet()) {
                        temporaryCollisionData.remove(shape);
                    }
                    break;
                }
            }
        }
        return collided;
    }

    private double recursivelyCalculateRelatedCollisionsAndFindMaxMovement(HashMap<GameShape, ForceAndTorque> relatedCollisions, GameShape shape, ArrayList<OverlapGradientForceCalculator> collisions, double maxMovement, HashMap<GameShape, ArrayList<OverlapGradientForceCalculator>> temporaryCollisionData) {
        ForceAndTorque totalCollision = new ForceAndTorque(0, 0, 0, 0, shape);
        relatedCollisions.put(shape, totalCollision);
        for (OverlapGradientForceCalculator collision : collisions) {
            totalCollision.addForceAndTorque(collision);
            GameShape other;
            if (shape == collision.firstShape) {
                other = collision.otherShape;
            } else {
                other = collision.firstShape;
            }
            if (other != null && movableShapes.contains(other) && !relatedCollisions.containsKey(other)) {
                maxMovement = recursivelyCalculateRelatedCollisionsAndFindMaxMovement(relatedCollisions, other, temporaryCollisionData.get(other), maxMovement, temporaryCollisionData);
            }
        }
        double translationMovement = Math.sqrt(totalCollision.forceActingOnShapeX * totalCollision.forceActingOnShapeX + totalCollision.forceActingOnShapeY * totalCollision.forceActingOnShapeY) / shape.getMass();
        double rotationMovement = Math.abs(totalCollision.torqueActingOnShape / shape.getMomentOfInertia() * shape.getBoundingRadius());
        double movement = translationMovement + rotationMovement;
        if (movement > maxMovement) {
            return movement;
        } else {
            return maxMovement;
        }
    }

    public void draw(Canvas canvas) {
        if (canvas != null) {
            for (GameShape s : unmovableShapes) {
                s.draw(canvas);
            }
            for (GameShape s : movableShapes) {
                s.draw(canvas);
            }
            for (GameForceField f : forceFields) {
                f.affectedArea.draw(canvas);
            }
        }
    }

    //TODO: make force strong enough the object doesn't move towards ripple at too direct an angle
    //this ensures either there is always something moving rather than just back and forth, or
    //the ripple continuously applies force without doing anything
    //avoids cases where the ball is against the wall at too direct an angle and refuses to move,
    //or the ball is against a heavy shape that's able to move but refuses to push it
    //maybe angle can be 180 degrees?

    //also how much it pushes an object should depend on whether other things
    //are pushing that object in the same direction. This prevents the ripple
    //from piling a bunch of objects and exerting a small force on each one of them
    //adding up to a huge force that shoves the last object through the wall
    private void collisionsOnShapeFromForceFields(GameShape shape, HashMap<GameShape, ArrayList<OverlapGradientForceCalculator>> temporaryCollisionData) {
        ForceAndTorque forceFromFields = null;
        forceFieldsLoop:
        for (GameForceField forceField : forceFields) {
            for (BiPredicate<GameShape, GameShape> collisionRule : collisionRules) {
                if (!collisionRule.test(shape, forceField.affectedArea)) {
                    continue forceFieldsLoop;
                }
            }
            if (forceFromFields == null) {
                forceFromFields = forceField.apply(shape);
            } else {
                forceFromFields.addForceAndTorque(forceField.apply(shape));
            }
        }

        if (forceFromFields != null && !forceFromFields.isAlmostZero()) {
            ArrayList<OverlapGradientForceCalculator> otherCollisions = temporaryCollisionData.get(shape);
            if (otherCollisions == null) {
                addCollisionToTemporaryCollisionData(forceFromFields.asOverlapGradientForceCalculator(), shape, temporaryCollisionData);
            } else {
                double repulsionCollisionMagnitude = Math.sqrt(forceFromFields.forceActingOnShapeX * forceFromFields.forceActingOnShapeX + forceFromFields.forceActingOnShapeY * forceFromFields.forceActingOnShapeY);
                double netForceX = 0;
                double netForceY = 0;
                double grossForceParallelToRepulsionCollision = 0;
                for (OverlapGradientForceCalculator otherCollision : otherCollisions) {
                    double forceX;
                    double forceY;
                    if (otherCollision.firstShape == shape) {
                        forceX = otherCollision.overlapGradientForceX;
                        forceY = otherCollision.overlapGradientForceY;
                    } else {
                        forceX = -otherCollision.overlapGradientForceX;
                        forceY = -otherCollision.overlapGradientForceY;
                    }
                    netForceX += forceX;
                    netForceY += forceY;
                    double dotProduct = forceX * forceFromFields.forceActingOnShapeX + forceY * forceFromFields.forceActingOnShapeY;
                    if (dotProduct >= 0) {
                        grossForceParallelToRepulsionCollision += dotProduct / repulsionCollisionMagnitude;
                    }
                }
                double netForceCrossProduct = Math.abs(netForceX * forceFromFields.forceActingOnShapeY + netForceY * forceFromFields.forceActingOnShapeX);
                double netForceDotProduct = netForceX * forceFromFields.forceActingOnShapeX + netForceY * forceFromFields.forceActingOnShapeY;
                double preferredRepulsionForce = (netForceCrossProduct / Math.tan(PREFERRED_MAX_ANGLE_BETWEEN_NET_FORCE_AND_FORCE_FIELDS_FORCE) + netForceDotProduct) / repulsionCollisionMagnitude;
                if (preferredRepulsionForce < repulsionCollisionMagnitude) {
                    if (preferredRepulsionForce > repulsionCollisionMagnitude * RIPPLE_WEAKENING_FACTOR_WHEN_MOVING) {
                        forceFromFields.multiplyIntensity(preferredRepulsionForce / repulsionCollisionMagnitude);
                        repulsionCollisionMagnitude *= preferredRepulsionForce / repulsionCollisionMagnitude;
                    } else {
                        forceFromFields.multiplyIntensity(RIPPLE_WEAKENING_FACTOR_WHEN_MOVING);
                        repulsionCollisionMagnitude *= RIPPLE_WEAKENING_FACTOR_WHEN_MOVING;
                    }
                }
                if (grossForceParallelToRepulsionCollision < repulsionCollisionMagnitude) {
                    if (grossForceParallelToRepulsionCollision > 0) {
                        forceFromFields.multiplyIntensity(1 - grossForceParallelToRepulsionCollision / repulsionCollisionMagnitude);
                    }
                    addCollisionToTemporaryCollisionData(forceFromFields.asOverlapGradientForceCalculator(), shape, temporaryCollisionData);
                }
            }
        }
    }

    private static void addCollisionToTemporaryCollisionData(OverlapGradientForceCalculator collision, GameShape shape, HashMap<GameShape, ArrayList<OverlapGradientForceCalculator>> temporaryCollisionData) {
        ArrayList<OverlapGradientForceCalculator> list = temporaryCollisionData.get(shape);
        if (list == null) {
            list = new ArrayList<>();
            temporaryCollisionData.put(shape, list);
        }
        list.add(collision);
    }

    public double distanceBetween(GameShape shape1, GameShape shape2) {
        double xDifference = shape2.getX() - shape1.getX();
        double yDifference = shape2.getY() - shape1.getY();
        return Math.sqrt(xDifference * xDifference + yDifference * yDifference);
    }

    public void removeAllShapes() {
        unmovableShapes.clear();
        movableShapes.clear();
        forceFields.clear();
        collisionRules.clear();
    }

    public void addCollisionRule(BiPredicate<GameShape, GameShape> rule) {
        collisionRules.add(rule);
    }

    public ArrayList<GameShape> getAllShapes() {
        ArrayList<GameShape> list = new ArrayList<>();
        list.addAll(unmovableShapes);
        list.addAll(movableShapes);
        return list;
    }

}
