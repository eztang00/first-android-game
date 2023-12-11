package com.github.eztang00.firstandroidgame.gamelogic.gameobstacles;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import com.github.eztang00.firstandroidgame.gamelogic.GameFadeableText;
import com.github.eztang00.firstandroidgame.gamephysics.GamePolyarcgon;
import com.github.eztang00.firstandroidgame.gamephysics.GamePolyarcgonBuilder;

/**
 * Teleports the ball between two points
 */
public class GameWormhole {
    static final double SIZE_FACTOR = 1.5;
    GamePolyarcgon circle1;
    GamePolyarcgon circle2;
    String text;
    private boolean ballJustTeleported = false;

    public GameWormhole(double x1, double y1, double radius1, double x2, double y2, double radius2, String text) {
        GamePolyarcgonBuilder builder = new GamePolyarcgonBuilder();
        circle1 = builder.addCircleContour(x1, y1, radius1, true).buildAndReset();
        circle2 = builder.addCircleContour(x2, y2, radius2, true).buildAndReset();
        this.text = text;
    }

    public void draw(Canvas canvas) {
        for (int i = 0; i < 2; i++) {
            GamePolyarcgon circle = (i == 0) ? circle1 : circle2;
            int[] colorsGradient = new int[]{
                    Color.argb(0, 0, 0, 0),
                    Color.argb(255, 0, 0, 0),
                    Color.argb(0, 0, 0, 0)};
            float[] stopsGradient = new float[]{0.8f, 0.9f, 1};
            RadialGradient radialGradient = new RadialGradient((float) circle.x, (float) circle.y, (float) circle.boundingRadius, colorsGradient, stopsGradient, Shader.TileMode.CLAMP);

            //based on https://kodintent.wordpress.com/2015/06/29/android-using-radial-gradients-in-canvas-glowing-dot-example/
            Paint paint = new Paint();
            paint.setDither(true);
            paint.setAntiAlias(true);
            paint.setShader(radialGradient);

            canvas.drawCircle((float) circle.x, (float) circle.y, (float) circle.boundingRadius, paint);
            GameFadeableText.drawText(text, circle.x, circle.y, (int) (1.6 * circle.boundingRadius), circle.boundingRadius, Color.rgb(0, 0, 0), canvas);
        }
    }

    public void update(GamePolyarcgon ball) {
        double distance1 = Math.sqrt((ball.x - circle1.x) * (ball.x - circle1.x) + (ball.y - circle1.y) * (ball.y - circle1.y));
        double distance2 = Math.sqrt((ball.x - circle2.x) * (ball.x - circle2.x) + (ball.y - circle2.y) * (ball.y - circle2.y));
        if (ballJustTeleported) {
            if (distance1 > ball.boundingRadius + circle1.boundingRadius * SIZE_FACTOR && distance2 > ball.boundingRadius + circle2.boundingRadius * SIZE_FACTOR) {
                ballJustTeleported = false;
            }
        } else {
            if (distance1 - circle1.boundingRadius * SIZE_FACTOR < distance2 - circle2.boundingRadius * SIZE_FACTOR && distance1 < circle1.boundingRadius * SIZE_FACTOR) {
                ball.x = circle2.x;
                ball.y = circle2.y;
                ballJustTeleported = true;
            } else if (distance2 < circle2.boundingRadius * SIZE_FACTOR) {
                ball.x = circle1.x;
                ball.y = circle1.y;
                ballJustTeleported = true;
            }
        }
    }
}
