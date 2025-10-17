package com.iflytek.aiui.demo.chat.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Created by hj at 2025/5/9 09:54
 * <p>
 * 声音指示器，用柱状图表示音量变化。
 */
public class VoiceIndicator extends View {
    private static final String TAG = "VoiceIndicator";

    private int mMaxVol = 30;
    private int mBarInterval = 20;
    private int mBarWidth = 0;
    private int mBarMinHeight = 10;
    private int mBarMaxHeight = 0;
    private int mBarNum = 5;
    private int mBarColor = Color.WHITE;

    private LinearGradient mBarColorGradient;

    private final int[] mVolArray = new int[10];

    // 每个条的X坐标
    private final int[] mBarXArray = new int[10];

    private int mCenterY = 0;

    private int mUpdateCount = 0;
    private int mUpdateInterval = 3;

    private final Paint mPaint = new Paint();

    public VoiceIndicator(Context context) {
        super(context);
    }

    public VoiceIndicator(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public VoiceIndicator(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public VoiceIndicator(Context context, @Nullable AttributeSet attrs, int defStyleAttr,
                          int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setMaxVol(int vol) {
        mMaxVol = vol;
    }

    /**
     * 设置柱的条数。
     *
     * @param num 正整数，最大为10
     */
    public void setBarNum(int num) {
        if (num < 1) {
            throw new IllegalArgumentException("num must be greater than 0");
        }

        mBarNum = Math.min(10, num);
    }

    public void setBarColor(int color) {
        mBarColor = color;
    }

    public void setBarColor(LinearGradient linearGradient) {
        mBarColorGradient = linearGradient;
    }

    public void setBarMinHeight(int minHeight) {
        mBarMinHeight = minHeight;
    }

    public void updateVol(int vol) {
        for (int i = 0; i < mBarNum - 1; i++) {
            mVolArray[i] = mVolArray[i + 1];
        }

        mVolArray[mBarNum - 1] = vol;

        if (mUpdateCount++ % mUpdateInterval == 0) {
            invalidate();
        }
    }

    /**
     * 设置更新间隔，即每调用多少次updateVol才更新一次显示。
     *
     * @param interval
     */
    public void setUpdateInterval(int interval) {
        mUpdateInterval = interval;
    }

    /**
     * 清空数据。
     */
    public void clear() {
        for (int i = 0; i < mBarNum; i++) {
            mVolArray[i] = 0;
        }

        invalidate();
    }

    /**
     * 将音量变成柱子的高度。
     *
     * @param vol
     * @return
     */
    private int volToHeight(int vol) {
        int height = (int) ((vol / (float) mMaxVol) * mBarMaxHeight);

        return Math.max(mBarMinHeight, height);
    }

    /**
     * 将柱子的高度变成上边缘的纵坐标。
     *
     * @param height
     * @return
     */
    private int heightToTopY(int height) {
        return mCenterY - height / 2;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mBarWidth == 0) {
            mBarMaxHeight = getHeight();
            mCenterY = mBarMaxHeight / 2;

            mBarWidth = (getWidth() - (mBarNum - 1) * mBarInterval) / mBarNum;
            mBarXArray[0] = mBarWidth / 2;

            for (int i = 1; i < mBarNum; i++) {
                mBarXArray[i] = i * (mBarWidth + mBarInterval) + mBarWidth / 2;
            }

            if (mBarColorGradient != null) {
                mPaint.setShader(mBarColorGradient);
            } else {
                mPaint.setColor(mBarColor);
            }
            mPaint.setStrokeWidth(mBarWidth);
            mPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        // 画各个柱子
        for (int i = 0; i < mBarNum; i++) {
            int barHeight = volToHeight(mVolArray[i]);
            int topY = heightToTopY(barHeight);
            int x = mBarXArray[i];

            canvas.drawLine(x, topY,
                    x, topY + barHeight, mPaint);
        }
    }
}
