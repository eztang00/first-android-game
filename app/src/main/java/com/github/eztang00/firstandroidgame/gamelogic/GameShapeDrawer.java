package com.github.eztang00.firstandroidgame.gamelogic;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.MaskFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import com.github.eztang00.firstandroidgame.gamelogic.ripplegolflogic.RippleGolfGame;
import com.github.eztang00.firstandroidgame.gamephysics.GamePolyarcgon;
import com.github.eztang00.firstandroidgame.gamephysics.GameShape;

import java.util.ArrayList;

/**
 * A class for static methods for drawing shapes
 */
public class GameShapeDrawer {
    public static int weightedAverage(int int1, int int2, double weightTowardsInt2) {
        return int1 + (int) ((int2 - int1) * weightTowardsInt2 + 0.499);
    }

    public static void draw(Canvas canvas, GameShape shape, RippleGolfGame game) {
        if (shape instanceof GamePolyarcgon) {
            GamePolyarcgon shape1 = (GamePolyarcgon) shape;
            if (!(shape1.additionalAttributes instanceof GameShapeAdditionalAttributesForDrawingEtc)) { // also checks for null
                shape1.draw(canvas);
                return;
            }
            GameShapeAdditionalAttributesForDrawingEtc attributes = (GameShapeAdditionalAttributesForDrawingEtc) shape1.additionalAttributes;
            if (attributes.specialness == GameShapeAdditionalAttributesForDrawingEtc.Specialness.RIPPLE) {
                int[] colorsGradient = new int[]{
                        Color.argb(0, 255, 255, 0),
                        Color.argb(255, 255, 255, 0),
                        Color.argb(0, 255, 255, 0)};
                float[] stopsGradient = new float[]{0.8f, 0.9f, 1};
                RadialGradient radialGradient = new RadialGradient((float) shape1.x, (float) shape1.y, (float) shape1.boundingRadius, colorsGradient, stopsGradient, Shader.TileMode.CLAMP);

                //based on https://kodintent.wordpress.com/2015/06/29/android-using-radial-gradients-in-canvas-glowing-dot-example/
                Paint paint = new Paint();
                paint.setDither(true);
                paint.setAntiAlias(true);
                paint.setShader(radialGradient);

                canvas.drawCircle((float) shape1.x, (float) shape1.y, (float) shape1.boundingRadius, paint);

            } else if (attributes.specialness == GameShapeAdditionalAttributesForDrawingEtc.Specialness.BALL) {
                double edgeBrightness = 0.5;
                int[] colorsGradient = new int[]{
                        attributes.color,
                        attributes.color,
                        Color.rgb((int) (Color.red(attributes.color) * edgeBrightness), (int) (Color.blue(attributes.color) * edgeBrightness), (int) (Color.green(attributes.color) * edgeBrightness))};
                float[] stopsGradient = new float[]{0, 0.5f, 1};
                RadialGradient radialGradient = new RadialGradient((float) shape1.x, (float) shape1.y, (float) shape1.boundingRadius, colorsGradient, stopsGradient, Shader.TileMode.CLAMP);

                //based on https://kodintent.wordpress.com/2015/06/29/android-using-radial-gradients-in-canvas-glowing-dot-example/
                Paint paint = new Paint();
                paint.setDither(true);
                paint.setAntiAlias(true);
                paint.setShader(radialGradient);

                canvas.drawCircle((float) shape1.x, (float) shape1.y, (float) shape1.boundingRadius, paint);
            } else {
                Path path = shape1.getPathForDrawing();

                Paint paint = new Paint();
                if (Color.alpha(attributes.color) > 0) {
                    paint.setColor(attributes.color);
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawPath(path, paint);
                }

                if (attributes.edgeThickness > 0 && Color.alpha(attributes.edgeColor) > 0) {
                    paint.setColor(attributes.edgeColor);
                    paint.setDither(true);
                    paint.setAntiAlias(true);
                    int multiplier = Math.min(game.width, game.height);
                    MaskFilter filter = paint.getMaskFilter();
                    paint.setMaskFilter(new BlurMaskFilter((float) attributes.edgeThickness * multiplier, BlurMaskFilter.Blur.INNER));
                    canvas.drawPath(path, paint);
                    paint.setMaskFilter(filter);
                }
            }
        } else {
            shape.draw(canvas);
            //do this later
        }
    }

    public static void drawPotentialShadow(Canvas canvas, GameShape shape, RippleGolfGame game) {
        if (shape instanceof GamePolyarcgon) {
            GamePolyarcgon shape1 = (GamePolyarcgon) shape;
            if (!(shape1.additionalAttributes instanceof GameShapeAdditionalAttributesForDrawingEtc)) { // also checks for null
                return;
            }
            GameShapeAdditionalAttributesForDrawingEtc attributes = (GameShapeAdditionalAttributesForDrawingEtc) shape1.additionalAttributes;
            if (attributes.shadowThickness > 0 && Color.alpha(attributes.shadowColor) > 0) {
                Path path = shape1.getPathForDrawing();
                Paint paint = new Paint();
                paint.setColor(attributes.shadowColor);
                paint.setDither(true);
                paint.setAntiAlias(true);
                int multiplier = Math.min(game.width, game.height);
                MaskFilter filter = paint.getMaskFilter();
                paint.setMaskFilter(new BlurMaskFilter((float) attributes.shadowThickness * multiplier, BlurMaskFilter.Blur.OUTER));
                canvas.drawPath(path, paint);
                paint.setMaskFilter(filter);
            }
        }
    }

    public static void drawPotentialShadow(Canvas canvas, ArrayList<GameShape> shapes, RippleGolfGame game) {
        for (GameShape s : shapes) {
            drawPotentialShadow(canvas, s, game);
        }
    }

    public static void draw(Canvas canvas, ArrayList<GameShape> shapes, RippleGolfGame game) {
        for (GameShape s : shapes) {
            draw(canvas, s, game);
        }
    }
}
