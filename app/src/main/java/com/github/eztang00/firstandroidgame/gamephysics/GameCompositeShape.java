package com.github.eztang00.firstandroidgame.gamephysics;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.MaskFilter;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * For now not used.
 *
 * Meant to represent a number of shapes.
 * The potential advantage of using a composite shape
 * rather than a single GamePolyarcgon made out of different
 * contours, is that a composite shape can have individual
 * colours and densities etc. for each sub-shape.
 *
 * It also only tests for collisions for each sub-shape individually.
 * This means if another shape is outside the bounding radius of its sub-shapes
 * but still within the total bounding radius, we could save computing time.
 */
public class GameCompositeShape implements GameShape {
    private ArrayList<GameShape> allShapes;
    private HashSet<GameShape> negativeShapes;

    double x;
    double y;
    double mass;
    double area;
    double momentOfInertia;
    double boundingRadius;
    double rotationRadians;

    private double xWhenLastUpdatedShapes = 0;
    private double yWhenLastUpdatedShapes = 0;
    private double rotationWhenLastUpdatedShapes;
    private ArrayList<DoublePoint> allShapesTemplatePositions;

    public GameCompositeShape(ArrayList<GameShape> shapes) {
        this(shapes, new HashSet<GameShape>());
    }

    public GameCompositeShape(ArrayList<GameShape> allShapes, HashSet<GameShape> negativeShapes) {
        this.allShapes = allShapes;
        this.negativeShapes = negativeShapes;
        for (GameShape shape : allShapes) {
            if (negativeShapes.contains(shape)) {
                mass -= shape.getMass();
                x -= shape.getX() * shape.getMass();
                y -= shape.getY() * shape.getMass();
                area -= shape.getArea();
            } else {
                mass += shape.getMass();
                x += shape.getX() * shape.getMass();
                y += shape.getY() * shape.getMass();
                area += shape.getArea();
            }
        }
        x /= mass;
        y /= mass;


        rotationRadians = 0;

        xWhenLastUpdatedShapes = x;
        yWhenLastUpdatedShapes = y;
        rotationWhenLastUpdatedShapes = 0;


        allShapesTemplatePositions = new ArrayList<>();
        boundingRadius = 0;
        momentOfInertia = 0;

        for (GameShape shape : allShapes) {
            DoublePoint shapeRelativePos = new DoublePoint(shape.getX() - x, shape.getY() - y);
            allShapesTemplatePositions.add(shapeRelativePos);
            double distanceSq = shapeRelativePos.x * shapeRelativePos.x + shapeRelativePos.y * shapeRelativePos.y;
            double radius = Math.sqrt(distanceSq) + shape.getBoundingRadius();
            if (radius > boundingRadius) {
                boundingRadius = radius;
            }
            if (negativeShapes.contains(shape)) {
                momentOfInertia -= shape.getMomentOfInertia() + shape.getMass() * distanceSq;
            } else {
                momentOfInertia += shape.getMomentOfInertia() + shape.getMass() * distanceSq;
            }
        }

//        Log.i("me", String.format("mass: %.2f area: %.2f moment of inertia: %.2f x: %.2f y: %.2f bounding radius: %.2f", mass, area, momentOfInertia, x, y, boundingRadius));
    }

    public void collision(GameShape otherShape, boolean isThisMovable, boolean isOtherShapeMovable, boolean thisIsFirstShape, OverlapCalculator overlapCalculator) {
        if (Math.sqrt((otherShape.getX() - x) * (otherShape.getX() - x) + (otherShape.getY() - y) * (otherShape.getY() - y)) < boundingRadius + otherShape.getBoundingRadius()) {
            for (GameShape shape : getAllShapes()) {
                // note if the other shape is also a composite shape, this will end up calling the same function
                // from the other shape, colliding against part of this shape
                shape.collision(otherShape, isThisMovable, isOtherShapeMovable, thisIsFirstShape, overlapCalculator);
            }
        }
    }


    @Override
    public void draw(Canvas canvas) {
//        for (GameShape shape : getAllShapes()) {
//            shape.draw(canvas); //ahh screw negative shapes for now
//        }
        drawWithBorder(canvas);
    }

    public void drawWithBorder(Canvas canvas) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            double blurRadius = 20;
//            Bitmap imageToDraw = Bitmap.createBitmap((int) (2*boundingRadius+1), (int) (2*boundingRadius+1), Bitmap.Config.ARGB_8888, true);
//            Canvas imageCanvas = new Canvas(imageToDraw);
//            imageCanvas.translate((int) (boundingRadius-x), (int) (boundingRadius-y));
            Paint paint = new Paint();
            paint.setColor(Color.rgb(0, 255, 255));
            paint.setStyle(Paint.Style.FILL);
            MaskFilter filter = paint.getMaskFilter();
            paint.setMaskFilter(new BlurMaskFilter((float) blurRadius, BlurMaskFilter.Blur.OUTER));
            for (GameShape shape : getAllShapes()) {
//                if (shape instanceof GameConvexPolygon) {
//
//                    Path path = new Path();
//                    DoublePoint[] corners = ((GameConvexPolygon) shape).getCorners();
//                    path.moveTo((float) corners[corners.length-1].x, (float) corners[corners.length-1].y); // used for first point
//                    for (int i=0; i<corners.length; i++) {
//                        path.lineTo((float) corners[i].x, (float) corners[i].y);
//                    }
//                    canvas.drawPath(path, paint);
//                } else {
////                    shape.draw(imageCanvas);
//                    shape.draw(canvas);
//                }
                shape.draw(canvas);
            }
            paint.setMaskFilter(filter);
            paint.setColor(Color.rgb(0, 128, 128));
            for (GameShape shape : getAllShapes()) {
//                if (shape instanceof GameConvexPolygon) {
//                    Path path = new Path();
//                    DoublePoint[] corners = ((GameConvexPolygon) shape).getCorners();
//                    path.moveTo((float) corners[corners.length-1].x, (float) corners[corners.length-1].y); // used for first point
//                    for (int i=0; i<corners.length; i++) {
//                        path.lineTo((float) corners[i].x, (float) corners[i].y);
//                    }
//                    canvas.drawPath(path, paint);
//                } else {
//                    shape.draw(canvas);
//                }
                shape.draw(canvas);

            }
//            Paint paint = new Paint();
//            canvas.drawBitmap(imageToDraw, (int) (x-boundingRadius), (int) (y-boundingRadius), paint);
        } else {
            for (GameShape shape : getAllShapes()) {
                shape.draw(canvas); //ahh screw negative shapes for now
            }
        }
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
        return area;
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

    public ArrayList<GameShape> getAllShapes() {
        if (xWhenLastUpdatedShapes != x || yWhenLastUpdatedShapes != y || rotationWhenLastUpdatedShapes != rotationRadians) {
            double cos = Math.cos(rotationRadians);
            double sin = Math.sin(rotationRadians);
            for (int i = 0; i < allShapes.size(); i++) {
                GameShape shape = allShapes.get(i);
                DoublePoint templatePoint = allShapesTemplatePositions.get(i);
                shape.setPos(x + templatePoint.x * cos + templatePoint.y * (-sin), y + templatePoint.x * sin + templatePoint.y * cos);
                shape.setRotation(rotationRadians);
            }
            xWhenLastUpdatedShapes = x;
            yWhenLastUpdatedShapes = y;
            rotationWhenLastUpdatedShapes = rotationRadians;
        }

        return allShapes;
    }

    public HashSet<GameShape> getNegativeShapes() {
        getAllShapes();
        return negativeShapes;
    }
}
