package com.iflytek.aiui.demo.chat.ui;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

/**
 * Created by hj at 2025/5/8 16:56
 */
public class ToggleImageButton extends androidx.appcompat.widget.AppCompatImageView {
    public interface OnToggleListener {
        boolean onClick(boolean isChecked);
    }

    private OnToggleListener mListener;

    private int mCheckedDrawable;
    private int mUnCheckedDrawable;

    private boolean mIsChecked;

    public ToggleImageButton(Context context) {
        super(context);
        initUI();
    }

    public ToggleImageButton(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initUI();
    }

    public ToggleImageButton(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initUI();
    }

    private void initUI() {
        setOnClickListener(v -> {
            if (mListener != null) {
                if (mListener.onClick(mIsChecked)) {
                    mIsChecked = !mIsChecked;
                    setImageResource(mIsChecked ? mCheckedDrawable : mUnCheckedDrawable);
                }
            } else {
                mIsChecked = !mIsChecked;
                setImageResource(mIsChecked ? mCheckedDrawable : mUnCheckedDrawable);
            }
        });
    }

    public void setDrawables(int checkedDrawable, int uncheckedDrawable) {
        mCheckedDrawable = checkedDrawable;
        mUnCheckedDrawable = uncheckedDrawable;
    }

    public void setOnToggleListener(OnToggleListener listener) {
        mListener = listener;
    }

    public void setIsChecked(boolean isChecked) {
        mIsChecked = isChecked;
        setImageResource(mIsChecked ? mCheckedDrawable : mUnCheckedDrawable);
    }

    public boolean isChecked() {
        return mIsChecked;
    }
}
