package com.github.eztang00.firstandroidgame.gamelogic;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

/**
 * Text in the game that can fade in and out
 */
public class GameFadeableText {
    public final String text;
    public final long duration;
    public final double x;
    public final double y;
    public final int width;
    public final double textSize;
    public final int textColor;
    final long DEFAULT_TEXT_FADE_IN_DURATION = 1 * Game.SECOND_MS;
    final long DEFAULT_TEXT_FADE_OUT_DURATION = 1 * Game.SECOND_MS;

    public GameFadeableText(String text, long duration, double x, double y, double width, double textSize, int textColor) {
        this.text = text;
        this.duration = duration;
        this.x = x;
        this.y = y;
        this.width = (int) (width + 0.5);
        this.textSize = textSize;
        this.textColor = textColor;
    }

    public void draw(long timeSinceTextAppearedMs, Canvas canvas) {
        double visibility = Math.min(timeSinceTextAppearedMs / ((double) DEFAULT_TEXT_FADE_IN_DURATION), (duration - timeSinceTextAppearedMs) / ((double) DEFAULT_TEXT_FADE_OUT_DURATION));
        if (duration < DEFAULT_TEXT_FADE_IN_DURATION + DEFAULT_TEXT_FADE_OUT_DURATION) {
            visibility = visibility / ((double) duration / (DEFAULT_TEXT_FADE_IN_DURATION + DEFAULT_TEXT_FADE_OUT_DURATION));
        }
        int color;
        if (visibility < 1) {
            if (visibility <= 0) {
                return; //otherwise glitches for short times
            }
            color = Color.argb((int) (Color.alpha(textColor) * visibility), Color.red(textColor), Color.green(textColor), Color.blue(textColor));
        } else {
            color = textColor;
        }

        drawText(text, x, y, width, textSize, color, canvas);
    }

    //    public void draw(Canvas canvas) {
//        drawText(text, x, y, width, textSize, textColor, canvas);
//    }
    public static void drawText(String text, double x, double y, int width, double textSize, int color, Canvas canvas) {
        //thanks to https://medium.com/over-engineering/drawing-multiline-text-to-canvas-on-android-9b98f0bfa16a

        TextPaint textPaint = new TextPaint();
        textPaint.setTextSize((float) textSize);
        textPaint.setColor(color);
        StaticLayout staticLayout = StaticLayout.Builder.obtain(text, 0, text.length(), textPaint, width).setAlignment(Layout.Alignment.ALIGN_CENTER).build();
        Rect bounds = null;
        for (int i = 0; i < staticLayout.getLineCount(); i++) {
            Rect rect = new Rect();
            staticLayout.getLineBounds(i, rect);
            if (bounds == null) {
                bounds = rect;
            } else {
                bounds.union(rect);
            }
        }
        double translateX = x - (bounds.left + bounds.right) / 2.0;
        double translateY = y - (bounds.top + bounds.bottom) / 2.0;

        canvas.translate((float) translateX, (float) translateY);
        staticLayout.draw(canvas);
        canvas.translate(-(float) translateX, -(float) translateY);


        //this version doesn't support multi line text etc.
//        Paint paint = new Paint();
//        paint.setColor(color);
//        paint.setTextSize(textSize);
//        Rect bounds = new Rect();
//        paint.getTextBounds(text, 0, text.length(), bounds);
//        canvas.drawText(text, x-(bounds.left+bounds.right)/2, y-(bounds.top+bounds.bottom)/2, paint);


    }
}
