package com.sneva.spng.assist;


import com.sneva.spng.ApngDrawable;

public abstract class ApngListener {
    public void onAnimationStart(ApngDrawable apngDrawable) {}
    public void onAnimationRepeat(ApngDrawable apngDrawable) {}
    public void onAnimationEnd(ApngDrawable apngDrawable) {}
}
