package com.je.dejpeg.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

public class ProcessingAnimation extends View {
    private Paint backgroundPaint;
    private Paint animationPaint;
    private RectF backgroundRect;
    private RectF animationRect;

    private float animationPosition = 0f;
    private float barWidth;
    private float cornerRadius;
    private boolean isMovingRight = true;

    private ValueAnimator activeAnimator;

    private static final int DARK_GREY = 0xFF424242;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BOUNCE_COUNT = 3;
    private static final float[] BOUNCE_AMPLITUDES = {0.15f, 0.10f, 0.05f};
    private static final long MOVE_DURATION = 1000;
    private static final long BOUNCE_DURATION = 200;

    public ProcessingAnimation(Context context) {
        super(context);
        init();
    }

    public ProcessingAnimation(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ProcessingAnimation(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(DARK_GREY);
        backgroundPaint.setStyle(Paint.Style.FILL);

        animationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        animationPaint.setColor(WHITE);
        animationPaint.setStyle(Paint.Style.FILL);

        backgroundRect = new RectF();
        animationRect = new RectF();

        post(this::startMoveAnimation);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float padding = h * 0.1f;
        backgroundRect.set(0, padding, w, h - padding);
        barWidth = w * 0.2f;
        cornerRadius = (h - 2 * padding) / 2f;
        updateAnimationRect();
    }

    private void updateAnimationRect() {
        float height = backgroundRect.height();
        float top = backgroundRect.top;
        animationRect.set(animationPosition, top, animationPosition + barWidth, top + height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, backgroundPaint);
        canvas.drawRoundRect(animationRect, cornerRadius, cornerRadius, animationPaint);
    }

    private void startMoveAnimation() {
        if (activeAnimator != null) activeAnimator.cancel();

        float start = isMovingRight ? 0 : getWidth() - barWidth;
        float end = isMovingRight ? getWidth() - barWidth : 0;

        activeAnimator = ValueAnimator.ofFloat(start, end);
        activeAnimator.setDuration(MOVE_DURATION);
        activeAnimator.setInterpolator(new AccelerateDecelerateInterpolator());

        activeAnimator.addUpdateListener(anim -> {
            animationPosition = (float) anim.getAnimatedValue();
            updateAnimationRect();
            invalidate();
        });

        activeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                performBounce(0);
            }
        });

        activeAnimator.start();
    }

    private void performBounce(final int bounceIndex) {
        if (bounceIndex >= BOUNCE_COUNT - 1) {
            throwToOtherSide();
            return;
        }

        float edge = isMovingRight ? getWidth() - barWidth : 0;
        float amplitude = BOUNCE_AMPLITUDES[bounceIndex] * (getWidth() - barWidth);
        float bounceTarget = isMovingRight ? edge - amplitude : edge + amplitude;

        ValueAnimator bounceOut = ValueAnimator.ofFloat(edge, bounceTarget);
        bounceOut.setDuration(BOUNCE_DURATION / 2);
        bounceOut.setInterpolator(new AccelerateInterpolator());

        bounceOut.addUpdateListener(anim -> {
            animationPosition = (float) anim.getAnimatedValue();
            updateAnimationRect();
            invalidate();
        });

        bounceOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                ValueAnimator bounceBack = ValueAnimator.ofFloat(animationPosition, edge);
                bounceBack.setDuration(BOUNCE_DURATION / 2);
                bounceBack.setInterpolator(new DecelerateInterpolator());

                bounceBack.addUpdateListener(anim -> {
                    animationPosition = (float) anim.getAnimatedValue();
                    updateAnimationRect();
                    invalidate();
                });

                bounceBack.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        performBounce(bounceIndex + 1);
                    }
                });

                bounceBack.start();
            }
        });

        bounceOut.start();
    }

    private void throwToOtherSide() {
        float start = isMovingRight ? getWidth() - barWidth : 0;
        float end = isMovingRight ? 0 : getWidth() - barWidth;

        ValueAnimator throwAnimator = ValueAnimator.ofFloat(start, end);
        throwAnimator.setDuration(MOVE_DURATION / 2);
        throwAnimator.setInterpolator(new AccelerateInterpolator());

        throwAnimator.addUpdateListener(anim -> {
            animationPosition = (float) anim.getAnimatedValue();
            updateAnimationRect();
            invalidate();
        });

        throwAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isMovingRight = !isMovingRight;
                performBounce(0);
            }
        });

        throwAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (activeAnimator != null) activeAnimator.cancel();
    }
}