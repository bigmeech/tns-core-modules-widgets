/**
 * 
 */
package org.nativescript.widgets;

import android.content.Context;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

/**
 * @author hhristov
 *
 */
public class HorizontalScrollView extends android.widget.HorizontalScrollView {

	private final Rect mTempRect = new Rect();
	
    private int contentMeasuredWidth = 0;
    private int contentMeasuredHeight = 0;
    private int scrollableLength = 0;    
    private SavedState mSavedState;
    private boolean isFirstLayout = true;

    /**
     * True when the layout has changed but the traversal has not come through yet.
     * Ideally the view hierarchy would keep track of this for us.
     */
    private boolean mIsLayoutDirty = true;
    
    /**
     * The child to give focus to in the event that a child has requested focus while the
     * layout is dirty. This prevents the scroll from being wrong if the child has not been
     * laid out before requesting focus.
     */
    private View mChildToScrollTo = null;
    
    public HorizontalScrollView(Context context) {
		super(context);
	}
   
    public int getScrollableLength() {
    	return this.scrollableLength;
    }
    
    @Override
    public void requestLayout() {
        this.mIsLayoutDirty = true;
        super.requestLayout();
    }
    
    @Override
    public void requestChildFocus(View child, View focused) {
        if (!mIsLayoutDirty) {
            this.scrollToChild(focused);
        } else {
            // The child may not be laid out yet, we can't compute the scroll yet
            mChildToScrollTo = focused;
        }
        super.requestChildFocus(child, focused);
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        CommonLayoutParams.adjustChildrenLayoutParams(this, widthMeasureSpec, heightMeasureSpec);

        // Don't call measure because it will measure content twice.
    	// ScrollView is expected to have single child so we measure only the first child.
        View child = this.getChildCount() > 0 ? this.getChildAt(0) : null;
        if (child == null) {
            this.scrollableLength = 0;
            this.contentMeasuredWidth = 0;
            this.contentMeasuredHeight = 0;
        }
        else {
        	CommonLayoutParams.measureChild(child, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), heightMeasureSpec);
        	this.contentMeasuredWidth = CommonLayoutParams.getDesiredWidth(child);
        	this.contentMeasuredHeight = CommonLayoutParams.getDesiredHeight(child);
	    	
        	// Android ScrollView does not account to child margins so we set them as paddings. Otherwise you can never scroll to bottom. 
	    	CommonLayoutParams lp = (CommonLayoutParams)child.getLayoutParams();
	    	this.setPadding(lp.leftMargin, lp.topMargin, lp.rightMargin, lp.bottomMargin);
	    }
	
	    // Don't add in our paddings because they are already added as child margins. (we will include them twice if we add them).
        // Check the previous line - this.setPadding(lp.leftMargin, lp.topMargin, lp.rightMargin, lp.bottomMargin);
        //this.contentMeasuredWidth += this.getPaddingLeft() + this.getPaddingRight();
        //this.contentMeasuredHeight += this.getPaddingTop() + this.getPaddingBottom();

        // Check against our minimum height
        this.contentMeasuredWidth = Math.max(this.contentMeasuredWidth, this.getSuggestedMinimumWidth());
        this.contentMeasuredHeight = Math.max(this.contentMeasuredHeight, this.getSuggestedMinimumHeight());

        int widthSizeAndState = resolveSizeAndState(this.contentMeasuredWidth, widthMeasureSpec, 0);
        int heightSizeAndState = resolveSizeAndState(this.contentMeasuredHeight, heightMeasureSpec, 0);

        this.setMeasuredDimension(widthSizeAndState, heightSizeAndState);
    }
    
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    	int childWidth = 0;
    	if (this.getChildCount() > 0) {
	    	View child = this.getChildAt(0);
	    	childWidth = child.getMeasuredWidth();
	    	
	    	int width = right - left;
	    	int height = bottom - top;
	
	    	this.scrollableLength = this.contentMeasuredWidth - width;
	    	CommonLayoutParams.layoutChild(child, 0, 0, Math.max(this.contentMeasuredWidth, width), height);
	    	this.scrollableLength = Math.max(0, this.scrollableLength);
    	}
    	
    	this.mIsLayoutDirty = false;
        // Give a child focus if it needs it
        if (this.mChildToScrollTo != null && isViewDescendantOf(this.mChildToScrollTo, this)) {
        	this.scrollToChild(this.mChildToScrollTo);
        }
        
        this.mChildToScrollTo = null;
        
        int scrollX = this.getScrollX();
        int scrollY = this.getScrollY();
        
        if (this.isFirstLayout) {
        	this.isFirstLayout = false;
        	
            final int scrollRange = Math.max(0, childWidth - (right - left - this.getPaddingLeft() - this.getPaddingRight()));
            if (this.mSavedState != null) {
            	scrollX = (this.isLayoutRtl() == mSavedState.isLayoutRtl) ? mSavedState.scrollPosition : (scrollRange - this.mSavedState.scrollPosition);
                mSavedState = null;
            } else {
                if (this.isLayoutRtl()) {
                	scrollX = scrollRange - scrollX;
                } // mScrollX default value is "0" for LTR
            }
            // Don't forget to clamp
            if (scrollX > scrollRange) {
            	scrollX = scrollRange;
            } else if (scrollX < 0) {
            	scrollX = 0;
            }
        }

        // Calling this with the present values causes it to re-claim them
        this.scrollTo(scrollX, scrollY);
        CommonLayoutParams.restoreOriginalParams(this);
    }
    
    @Override
    protected void onAttachedToWindow() {
    	super.onAttachedToWindow();
    	this.isFirstLayout = true;
    }
    
    @Override
    protected void onDetachedFromWindow() {
    	super.onDetachedFromWindow();
    	this.isFirstLayout = true;
    }
    
    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        this.mSavedState = ss;
        this.requestLayout();
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        ss.scrollPosition = this.getScrollX();
        ss.isLayoutRtl = this.isLayoutRtl();
        return ss;
    }
    
    private void scrollToChild(View child) {
        child.getDrawingRect(mTempRect);

        /* Offset from child's local coordinates to ScrollView coordinates */
        offsetDescendantRectToMyCoords(child, mTempRect);

        int scrollDelta = computeScrollDeltaToGetChildRectOnScreen(mTempRect);
        if (scrollDelta != 0) {
            this.scrollBy(scrollDelta, 0);
        }
    }
    
    private boolean isLayoutRtl() {
        return (this.getLayoutDirection() == LAYOUT_DIRECTION_RTL);
    }
    
    /**
     * Return true if child is a descendant of parent, (or equal to the parent).
     */
    static boolean isViewDescendantOf(View child, View parent) {
        if (child == parent) {
            return true;
        }

        final ViewParent theParent = child.getParent();
        return (theParent instanceof ViewGroup) && isViewDescendantOf((View) theParent, parent);
    }
    
    static class SavedState extends BaseSavedState {
        public int scrollPosition;
        public boolean isLayoutRtl;

        SavedState(Parcelable superState) {
            super(superState);
        }

        public SavedState(Parcel source) {
            super(source);
            scrollPosition = source.readInt();
            isLayoutRtl = (source.readInt() == 0) ? true : false;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(scrollPosition);
            dest.writeInt(isLayoutRtl ? 1 : 0);
        }

        @Override
        public String toString() {
            return "HorizontalScrollView.SavedState{"
                    + Integer.toHexString(System.identityHashCode(this))
                    + " scrollPosition=" + scrollPosition
                    + " isLayoutRtl=" + isLayoutRtl + "}";
        }

        public static final Parcelable.Creator<SavedState> CREATOR
                = new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}
