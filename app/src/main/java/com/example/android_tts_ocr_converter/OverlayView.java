package com.example.android_tts_ocr_converter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class OverlayView extends View {
    private boolean textDetected = false; // 标记是否检测到文字
    private final Paint paint;

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
        updateBorderColor(); // 初始化边框颜色
    }

    public void setTextDetected(boolean detected) {
        this.textDetected = detected;
        updateBorderColor(); // 更新边框颜色
        invalidate(); // 刷新绘制
    }

    private void updateBorderColor() {
        paint.setColor(textDetected ? Color.GREEN : Color.RED);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // 绘制一个固定长方形边框，覆盖整个预览区域
        int width = getWidth();
        int height = getHeight();
        canvas.drawRect(10, 10, width - 10, height - 10, paint); // 留 10px 边距
    }

}
