/*
 *  Copyright (C) 2004-2021 Savoir-faire Linux Inc.
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package cx.ring.views;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RotateDrawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.CompoundButton;

import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import cx.ring.R;

public class SwitchButton extends CompoundButton {

    public static final float DEFAULT_THUMB_RANGE_RATIO = 3f;
    public static final int DEFAULT_THUMB_SIZE_DP = 20;
    public static final int DEFAULT_THUMB_MARGIN_DP = 2;
    public static final int DEFAULT_ANIMATION_DURATION = 250;
    public static final int DEFAULT_EXTRA_MARGIN = 10;
    public static final int DEFAULT_SWITCH_WIDTH = 150;

    private int mBackColor;
    private int mThumbWidth = 0;
    private int mThumbHeight = 0;
    private int mBackWidth;
    private int mBackHeight;
    private final int mTouchSlop;
    private final int mClickTimeout;
    private float mThumbRadius, mBackRadius;
    private float mThumbRangeRatio;
    private float mTextWidth;
    private float mTextHeight;
    private float mProgress;
    private float mStartX, mStartY, mLastX;
    private boolean mReady = false;
    private boolean mCatch = false;
    private boolean mShowImage = false;
    private final RectF mThumbRectF = new RectF(),
            mBackRectF = new RectF(),
            mSafeRectF = new RectF(),
            mTextOnRectF= new RectF(),
            mTextOffRectF = new RectF(),
            mThumbMargin = new RectF(),
            mPresentThumbRectF = new RectF();
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ValueAnimator mProgressAnimator = ValueAnimator.ofFloat(0, 0);
    private CharSequence mStatus;
    private Layout mOnLayout;
    private Layout mOffLayout;
    private final RotateDrawable mImageDrawable;

    private boolean mChangingState = false;
    private CompoundButton.OnCheckedChangeListener mChildOnCheckedChangeListener = null;

    public SwitchButton(Context context) {
        this(context, null);
    }

    public SwitchButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwitchButton(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.SwitchButton);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveAttributeDataForStyleable(context, R.styleable.SwitchButton, attrs, ta, defStyle, 0);
        }

        int backColor = ta.getColor(R.styleable.SwitchButton_backColor, getResources().getColor(R.color.grey_400));
        String status = ta.getString(R.styleable.SwitchButton_status);
        ta.recycle();

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mClickTimeout = ViewConfiguration.getPressedStateDuration() + ViewConfiguration.getTapTimeout();

        mProgressAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        mProgressAnimator.addUpdateListener(valueAnimator -> setProgress((float) valueAnimator.getAnimatedValue()));
        mProgressAnimator.setDuration(DEFAULT_ANIMATION_DURATION);

        LayerDrawable layerDrawable = (LayerDrawable) ResourcesCompat.getDrawable(getResources(), R.drawable.rotate, context.getTheme());
        mImageDrawable = (RotateDrawable) layerDrawable.findDrawableByLayerId(R.id.progress);

        setFocusable(true);
        setClickable(true);

        mStatus = status;

        mBackColor = backColor;

        float margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DEFAULT_THUMB_MARGIN_DP, getResources().getDisplayMetrics());
        mThumbMargin.set(margin, margin, margin, margin);

        // size & measure params must larger than 1
        float thumbRangeRatio = DEFAULT_THUMB_RANGE_RATIO;
        mThumbRangeRatio = mThumbMargin.width() >= 0 ? Math.max(thumbRangeRatio, 1) : thumbRangeRatio;

        // sync checked status
        setProgress(isChecked() ? 1.f : 0.f);

        super.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mChangingState)
                return;
            if (mChildOnCheckedChangeListener != null)
                mChildOnCheckedChangeListener.onCheckedChanged(buttonView, isChecked);
        });
    }

    private Layout makeLayout(CharSequence text) {
        return new StaticLayout(text, getPaint(), (int) Math.ceil(Layout.getDesiredWidth(text, getPaint())), Layout.Alignment.ALIGN_CENTER, 1.f, 0, false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mOnLayout == null && !TextUtils.isEmpty(mStatus)) {
            mOnLayout = makeLayout(mStatus);
        }
        if (mOffLayout == null && !TextUtils.isEmpty(mStatus)) {
            mOffLayout = makeLayout(mStatus);
        }

        float onWidth = DEFAULT_SWITCH_WIDTH;
//                mOnLayout != null ? mOnLayout.getWidth() : 0;
        float offWidth = DEFAULT_SWITCH_WIDTH;
//                mOffLayout != null ? mOffLayout.getWidth() : 0;
        if (onWidth != 0 || offWidth != 0) {
            mTextWidth = Math.max(onWidth, offWidth);
        } else {
            mTextWidth = 0;
        }

        float onHeight = mOnLayout != null ? mOnLayout.getHeight() : 0;
        float offHeight = mOffLayout != null ? mOffLayout.getHeight() : 0;
        if (onHeight != 0 || offHeight != 0) {
            mTextHeight = Math.max(onHeight, offHeight);
        } else {
            mTextHeight = 0;
        }

        setMeasuredDimension(measureWidth(widthMeasureSpec), measureHeight(heightMeasureSpec));
    }

    /**
     * SwitchButton use this formula to determine the final size of thumb, background and itself.
     * <p>
     * textWidth = max(onWidth, offWidth)
     * thumbRange = thumbWidth * rangeRatio
     * textExtraSpace = textWidth + textExtra - (moveRange - thumbWidth + max(thumbMargin.left, thumbMargin.right) + textThumbInset)
     * backWidth = thumbRange + thumbMargin.left + thumbMargin.right + max(textExtraSpace, 0)
     * contentSize = thumbRange + max(thumbMargin.left, 0) + max(thumbMargin.right, 0) + max(textExtraSpace, 0)
     *
     * @param widthMeasureSpec widthMeasureSpec
     * @return measuredWidth
     */
    private int measureWidth(int widthMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int measuredWidth = widthSize;

        int moveRange;
        int textWidth = ceil(mTextWidth);
        // how much the background should extend to fit text.
        int textExtraSpace;
        int contentSize;

        if (mThumbRangeRatio == 0) {
            mThumbRangeRatio = DEFAULT_THUMB_RANGE_RATIO;
        }

        if (widthMode == MeasureSpec.EXACTLY) {
            contentSize = widthSize - getPaddingLeft() - getPaddingRight();
            moveRange = ceil(contentSize - Math.max(mThumbMargin.left, 0) - Math.max(mThumbMargin.right, 0));
            if (moveRange < 0) {
                mThumbWidth = 0;
                mBackWidth = 0;
                return measuredWidth;
            }
            mThumbWidth = ceil(moveRange / mThumbRangeRatio);
            mBackWidth = ceil(moveRange + mThumbMargin.left + mThumbMargin.right) + DEFAULT_EXTRA_MARGIN;
            if (mBackWidth < 0) {
                mThumbWidth = 0;
                mBackWidth = 0;
                return measuredWidth;
            }
            textExtraSpace = textWidth - (moveRange - mThumbWidth + ceil(Math.max(mThumbMargin.left, mThumbMargin.right)));
            if (textExtraSpace > 0) {
                // since backWidth is determined by view width, so we can only reduce thumbSize.
                mThumbWidth = mThumbWidth - textExtraSpace;
            }
            if (mThumbWidth < 0) {
                mThumbWidth = 0;
                mBackWidth = 0;
                return measuredWidth;
            }
        } else {
            /*
            If parent view want SwitchButton to determine it's size itself, we calculate the minimal
            size of it's content. Further more, we ignore the limitation of widthSize since we want
            to display SwitchButton in its actual size rather than compress the shape.
             */

            mThumbWidth = ceil(getResources().getDisplayMetrics().density * DEFAULT_THUMB_SIZE_DP);

            if (mThumbRangeRatio == 0) {
                mThumbRangeRatio = DEFAULT_THUMB_RANGE_RATIO;
            }

            moveRange = ceil(mThumbWidth * mThumbRangeRatio);
            textExtraSpace = ceil(textWidth - (moveRange - mThumbWidth + Math.max(mThumbMargin.left, mThumbMargin.right)));
            mBackWidth = ceil(moveRange + mThumbMargin.left + mThumbMargin.right + Math.max(0, textExtraSpace)) + DEFAULT_EXTRA_MARGIN;
            if (mBackWidth < 0) {
                mThumbWidth = 0;
                mBackWidth = 0;
                return measuredWidth;
            }
            contentSize = ceil(moveRange + Math.max(0, mThumbMargin.left) + Math.max(0, mThumbMargin.right) + Math.max(0, textExtraSpace));

            measuredWidth = Math.max(contentSize, contentSize + getPaddingLeft() + getPaddingRight());
        }
        return measuredWidth;
    }

    private int measureHeight(int heightMeasureSpec) {
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int measuredHeight = heightSize;

        int contentSize;
        int textExtraSpace;
        if (heightMode == MeasureSpec.EXACTLY) {
            if (mThumbHeight != 0) {
                /*
                If thumbHeight has been set, we calculate backHeight and check if there is enough room.
                 */
                mBackHeight = ceil(mThumbHeight + mThumbMargin.top + mThumbMargin.bottom);
                mBackHeight = ceil(Math.max(mBackHeight, mTextHeight));
                if (mBackHeight + getPaddingTop() + getPaddingBottom() - Math.min(0, mThumbMargin.top) - Math.min(0, mThumbMargin.bottom) > heightSize) {
                    // No enough room, we set thumbHeight to zero to calculate these value again.
                    mThumbHeight = 0;
                }
            }

            if (mThumbHeight == 0) {
                mBackHeight = ceil(heightSize - getPaddingTop() - getPaddingBottom() + Math.min(0, mThumbMargin.top) + Math.min(0, mThumbMargin.bottom));
                if (mBackHeight < 0) {
                    mBackHeight = 0;
                    mThumbHeight = 0;
                    return measuredHeight;
                }
                mThumbHeight = ceil(mBackHeight - mThumbMargin.top - mThumbMargin.bottom);
            }
            if (mThumbHeight < 0) {
                mBackHeight = 0;
                mThumbHeight = 0;
                return measuredHeight;
            }
        } else {
            if (mThumbHeight == 0) {
                mThumbHeight = ceil(getResources().getDisplayMetrics().density * DEFAULT_THUMB_SIZE_DP);
            }
            mBackHeight = ceil(mThumbHeight + mThumbMargin.top + mThumbMargin.bottom);
            if (mBackHeight < 0) {
                mBackHeight = 0;
                mThumbHeight = 0;
                return measuredHeight;
            }
            textExtraSpace = ceil(mTextHeight - mBackHeight);
            if (textExtraSpace > 0) {
                mBackHeight += textExtraSpace;
                mThumbHeight += textExtraSpace;
            }
            contentSize = Math.max(mThumbHeight, mBackHeight);

            measuredHeight = Math.max(contentSize, contentSize + getPaddingTop() + getPaddingBottom());
            measuredHeight = Math.max(measuredHeight, getSuggestedMinimumHeight());
        }

        return measuredHeight;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw || h != oldh) {
            setup();
        }
    }

    static private int ceil(double dimen) {
        return (int) Math.ceil(dimen);
    }

    private void setup() {
        if (mThumbWidth == 0 || mThumbHeight == 0 || mBackWidth == 0 || mBackHeight == 0) {
            return;
        }

        mThumbRadius = Math.min(mThumbWidth, mThumbHeight) / 2f;
        mBackRadius = Math.min(mBackWidth, mBackHeight) / 2f;

        int contentWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight() + DEFAULT_EXTRA_MARGIN;
        int contentHeight = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();

        // max range of drawing content, when thumbMargin is negative, drawing range is larger than backWidth
        int drawingWidth = ceil(mBackWidth - Math.min(0, mThumbMargin.left) - Math.min(0, mThumbMargin.right));
        int drawingHeight = ceil(mBackHeight - Math.min(0, mThumbMargin.top) - Math.min(0, mThumbMargin.bottom));

        float thumbTop;
        if (contentHeight <= drawingHeight) {
            thumbTop = getPaddingTop() + Math.max(0, mThumbMargin.top);
        } else {
            // center vertical in content area
            thumbTop = getPaddingTop() + Math.max(0, mThumbMargin.top) + (contentHeight - drawingHeight + 1) / 2f;
        }

        float thumbLeft;
        if (contentWidth <= mBackWidth) {
            thumbLeft = getPaddingLeft() + Math.max(0, mThumbMargin.left) - DEFAULT_EXTRA_MARGIN;
        } else {
            thumbLeft = getPaddingLeft() + Math.max(0, mThumbMargin.left) + (contentWidth - drawingWidth + 1) / 2f - DEFAULT_EXTRA_MARGIN;
        }

        mThumbRectF.set(thumbLeft, thumbTop, thumbLeft + mThumbWidth, thumbTop + mThumbHeight);

        float backLeft = mThumbRectF.left - mThumbMargin.left;
        mBackRectF.set(backLeft,
                mThumbRectF.top - mThumbMargin.top,
                backLeft + mBackWidth,
                mThumbRectF.top - mThumbMargin.top + mBackHeight);

        mSafeRectF.set(mThumbRectF.left, 0, mBackRectF.right - mThumbMargin.right - mThumbRectF.width(), 0);

        float minBackRadius = Math.min(mBackRectF.width(), mBackRectF.height()) / 2.f;
        mBackRadius = Math.min(minBackRadius, mBackRadius);

        if (mOnLayout != null) {
            float onLeft = mBackRectF.left + (mBackRectF.width() - mThumbWidth - mThumbMargin.right - mOnLayout.getWidth()) / 2f + DEFAULT_EXTRA_MARGIN / 2;
            float onTop = mBackRectF.top + (mBackRectF.height() - mOnLayout.getHeight()) / 2;
            mTextOnRectF.set(onLeft, onTop, onLeft + mOnLayout.getWidth(), onTop + mOnLayout.getHeight());
        }

        if (mOffLayout != null) {
            float offLeft = mBackRectF.right - (mBackRectF.width() - mThumbWidth - mThumbMargin.left - mOffLayout.getWidth()) / 2f - mOffLayout.getWidth() - DEFAULT_EXTRA_MARGIN / 2;
            float offTop = mBackRectF.top + (mBackRectF.height() - mOffLayout.getHeight()) / 2;
            mTextOffRectF.set(offLeft, offTop, offLeft + mOffLayout.getWidth(), offTop + mOffLayout.getHeight());
        }

        int dWidth = mImageDrawable.getIntrinsicWidth();
        int dHeight = mImageDrawable.getIntrinsicHeight();
        mImageDrawable.setBounds((int) ((mBackWidth - mThumbWidth) / 2 - dWidth / 2), 0, (int) ((mBackWidth - mThumbWidth) / 2 + dWidth / 2), dHeight);

        mReady = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!mReady) {
            setup();
        }
        if (!mReady) {
            return;
        }

        mPaint.setColor(mBackColor);
        canvas.drawRoundRect(mBackRectF, mBackRadius, mBackRadius, mPaint);

        // thumb
        mPresentThumbRectF.set(mThumbRectF);
        mPresentThumbRectF.offset(mProgress * mSafeRectF.width(), 0);
        mPaint.setColor(Color.WHITE);
        canvas.drawRoundRect(mPresentThumbRectF, mThumbRadius, mThumbRadius, mPaint);

        // image
        if (mShowImage) {
            mImageDrawable.draw(canvas);
        } else {
            // text
            Layout switchText = getProgress() > 0.5 ? mOnLayout : mOffLayout;
            RectF textRectF = getProgress() > 0.5 ? mTextOnRectF : mTextOffRectF;
            if (switchText != null && textRectF != null) {
                float alpha = getProgress() >= 0.75 ? getProgress() * 4 - 3 : (getProgress() < 0.25 ? 1 - getProgress() * 4 : 0);
                switchText.getPaint().setAlpha((int) (Color.alpha(getCurrentTextColor()) * alpha));
                canvas.save();
                canvas.translate(textRectF.left, textRectF.top);
                switchText.draw(canvas);
                canvas.restore();
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || !isClickable() || !isFocusable() || !mReady) {
            return false;
        }

        int action = event.getAction();

        float deltaX = event.getX() - mStartX;
        float deltaY = event.getY() - mStartY;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mStartX = event.getX();
                mStartY = event.getY();
                mLastX = mStartX;
                setPressed(true);
                break;

            case MotionEvent.ACTION_MOVE:
                float x = event.getX();
                setProgress(getProgress() + (x - mLastX) / mSafeRectF.width());
                mLastX = x;
                if (!mCatch && (Math.abs(deltaX) > mTouchSlop / 2f || Math.abs(deltaY) > mTouchSlop / 2f)) {
                    if (deltaY == 0 || Math.abs(deltaX) > Math.abs(deltaY)) {
                        catchView();
                    } else if (Math.abs(deltaY) > Math.abs(deltaX)) {
                        return false;
                    }
                }
                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                mCatch = false;
                setPressed(false);
                float time = event.getEventTime() - event.getDownTime();
                if (Math.abs(deltaX) < mTouchSlop && Math.abs(deltaY) < mTouchSlop && time < mClickTimeout) {
                    performClick();
                } else {
                    boolean nextStatus = getStatusBasedOnPos();
                    if (nextStatus != isChecked()) {
                        playSoundEffect(SoundEffectConstants.CLICK);
                        setChecked(nextStatus);
                    } else {
                        animateToState(nextStatus);
                    }
                }
                break;

            default:
                break;
        }
        return true;
    }


    /**
     * return the status based on position of thumb
     *
     * @return whether checked or not
     */
    private boolean getStatusBasedOnPos() {
        return getProgress() > 0.5f;
    }

    private float getProgress() {
        return mProgress;
    }

    private void setProgress(final float progress) {
        float tempProgress = progress;
        if (tempProgress > 1) {
            tempProgress = 1;
        } else if (tempProgress < 0) {
            tempProgress = 0;
        }
        mProgress = tempProgress;
        invalidate();
    }

    @Override
    public void setOnCheckedChangeListener(@Nullable OnCheckedChangeListener listener) {
        mChildOnCheckedChangeListener = listener;
    }

    public void animateToState(boolean checked) {
        if (mProgressAnimator == null) {
            return;
        }
        if (mProgressAnimator.isRunning()) {
            mProgressAnimator.cancel();
        }
        if (checked) {
            mProgressAnimator.setFloatValues(mProgress, 1f);
        } else {
            mProgressAnimator.setFloatValues(mProgress, 0);
        }
        mProgressAnimator.start();
    }

    private void catchView() {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
        }
        mCatch = true;
    }

    @Override
    public void setChecked(boolean checked) {
        if (isChecked() == checked) {
            return;
        }
        animateToState(checked);
        super.setChecked(checked);
    }

    public void setCheckedSilent(boolean checked) {
        mChangingState = true;
        super.setChecked(checked);
        setProgress(checked ? 1 : 0);
        mChangingState = false;
    }

    public int getBackColor() {
        return mBackColor;
    }

    public void setBackColor(int backColor) {
        mBackColor = backColor;
        invalidate();
    }

    public void setStatus(CharSequence status) {
        mStatus = status;

        mOnLayout = null;
        mOffLayout = null;

        mReady = false;
        requestLayout();
        invalidate();
    }

    public CharSequence getStatus() {
        return mStatus;
    }

    public void showImage(boolean show) {
        mShowImage = show;
        invalidate();
    }

    public void startImageAnimation() {
        ObjectAnimator anim = ObjectAnimator.ofInt(mImageDrawable, "level", 0, 10000);
        anim.setDuration(500);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.start();
    }

}
