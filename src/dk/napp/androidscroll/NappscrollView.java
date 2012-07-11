/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package dk.napp.androidscroll;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiConfig;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiAnimationBuilder.TiMatrixAnimation;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.Ti2DMatrix;
import org.appcelerator.titanium.view.TiCompositeLayout;
import org.appcelerator.titanium.view.TiCompositeLayout.LayoutArrangement;
import org.appcelerator.titanium.view.TiUIView;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.util.FloatMath;
import android.view.FocusFinder;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Scroller;

/**
 * The class that Titanium uses to link TiUIView with Androids View
 * orginal files on Github: 
 */
public class NappscrollView extends TiUIView {
	
	TDSV scrollView;
	private float maxZoom = 5.f;
	private float minZoom = 0.1f;
	private float curZoom = 1.f;
	
	private static final String SHOW_VERTICAL_SCROLL_INDICATOR = "showVerticalScrollIndicator";
	private static final String SHOW_HORIZONTAL_SCROLL_INDICATOR = "showHorizontalScrollIndicator";
	private static final String LCAT = "NappscrollView";
	private static final boolean DBG = TiConfig.LOGD;
	public static final float MAX_SCROLL_FACTOR = 0.5f;
	private int offsetX = 0, offsetY = 0;
	private boolean setInitialOffset = false;

	/**
	 * The class used by Titanium instead of the regular Android layout classes
	 */
	private class TiScrollViewLayout extends TiCompositeLayout {
		
		private static final int AUTO = Integer.MAX_VALUE;
		protected int measuredWidth = 0, measuredHeight = 0;
		private int parentWidth = 0, parentHeight = 0;

		public TiScrollViewLayout(Context context, LayoutArrangement arrangement) {
			super(context, arrangement);
		}

		private LayoutParams getParams(View child) {
			return (LayoutParams) child.getLayoutParams();
		}

		@Override
		protected void onLayout(boolean changed, int l, int t, int r, int b) {
			super.onLayout(changed, l, t, r, b);
			measuredHeight = measuredWidth = 0;
		}

		public void setParentWidth(int width) {
			parentWidth = width;
		}

		public void setParentHeight(int height) {
			parentHeight = height;
		}

		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		}

		private int getContentProperty(String property) {
			Object value = getProxy().getProperty(property);
			if (value != null) {
				if (value.equals(TiC.SIZE_AUTO)) {
					return AUTO;
				} else if (value instanceof Number) {
					return ((Number) value).intValue();
				} else {
					int type = 0;
					TiDimension dimension;
					if (TiC.PROPERTY_CONTENT_HEIGHT.equals(property)) {
						type = TiDimension.TYPE_HEIGHT;
					} else if (TiC.PROPERTY_CONTENT_WIDTH.equals(property)) {
						type = TiDimension.TYPE_WIDTH;
					}
					dimension = new TiDimension(value.toString(), type);
					return dimension.getUnits() == TiDimension.COMPLEX_UNIT_AUTO ? AUTO
							: dimension.getIntValue();
				}
			}
			return AUTO;
		}

		private int calculateAbsoluteRight(View child) {
			LayoutParams p = getParams(child);
			int contentWidth = getContentProperty(TiC.PROPERTY_CONTENT_WIDTH);
			if (contentWidth == AUTO) {
				int childMeasuredWidth = child.getMeasuredWidth();
				
				if (!p.autoFillsWidth && p.optionWidth != null) {
					childMeasuredWidth = getDimensionValue(p.optionWidth,
							parentWidth);
				}
				if (p.optionLeft != null) {
					childMeasuredWidth += getDimensionValue(p.optionLeft,
							parentWidth);
				}
				if (p.optionRight != null) {
					childMeasuredWidth += getDimensionValue(p.optionRight,
							parentWidth);
				}

				measuredWidth = Math.max(childMeasuredWidth, measuredWidth);

				measuredWidth = Math.max(parentWidth, measuredWidth);
			} else {
				measuredWidth = contentWidth;
			}

			return measuredWidth;
		}

		private int calculateAbsoluteBottom(View child) {
			LayoutParams p = (LayoutParams) child.getLayoutParams();
			int contentHeight = getContentProperty(TiC.PROPERTY_CONTENT_HEIGHT);

			if (contentHeight == AUTO) {
				int childMeasuredHeight = child.getMeasuredHeight();

				if (!p.autoFillsHeight && p.optionHeight != null) {
					childMeasuredHeight = getDimensionValue(p.optionHeight,
							parentHeight);
				}
				if (p.optionTop != null) {
					childMeasuredHeight += getDimensionValue(p.optionTop,
							parentHeight);
				}
				if (p.optionBottom != null) {
					childMeasuredHeight += getDimensionValue(p.optionBottom,
							parentHeight);
				}

				measuredHeight = Math.max(childMeasuredHeight, measuredHeight);
				
				measuredHeight = Math.max(parentHeight, measuredHeight);
			} else {
				measuredHeight = contentHeight;
			}
			return measuredHeight;
		}

		private int getDimensionValue(TiDimension dimension, int parentValue) {
			if (dimension.isUnitPercent()) {
				return (int) ((dimension.getValue() / 100.0) * parentValue);
			}
			return dimension.getAsPixels(this);
		}

		@Override
		protected void constrainChild(View child, int width, int wMode, int height, int hMode) {
			super.constrainChild(child, width, wMode, height, hMode);
			@SuppressWarnings("unused")
			int absWidth = calculateAbsoluteRight(child);
			@SuppressWarnings("unused")
			int absHeight = calculateAbsoluteBottom(child);
		}

		@Override
		protected int getWidthMeasureSpec(View child) {
			int contentWidth = getContentProperty(TiC.PROPERTY_CONTENT_WIDTH);
			if (contentWidth == AUTO) {
				return MeasureSpec.UNSPECIFIED;
			} else
				return super.getWidthMeasureSpec(child);
		}

		@Override
		protected int getHeightMeasureSpec(View child) {
			int contentHeight = getContentProperty(TiC.PROPERTY_CONTENT_HEIGHT);
			if (contentHeight == AUTO) {
				return MeasureSpec.UNSPECIFIED;
			} else
				return super.getHeightMeasureSpec(child);
		}

		@Override
		protected int getMeasuredWidth(int maxWidth, int widthSpec) {
			int contentWidth = getContentProperty(TiC.PROPERTY_CONTENT_WIDTH);
			if (contentWidth == AUTO) {
				return maxWidth;
			} else
				return contentWidth;
		}

		@Override
		protected int getMeasuredHeight(int maxHeight, int heightSpec) {
			int contentHeight = getContentProperty(TiC.PROPERTY_CONTENT_HEIGHT);
			if (contentHeight == AUTO) {
				return maxHeight;
			} else
				return contentHeight;
		}
	}

	/**
	 * The home made scroll-class, extending the class that the regular scrollviews extend
	 * Two Direction ScrollView = TDSV
	 */
	private class TDSV extends FrameLayout {
		private ScaleGestureDetector pinchDetector;
		private float lastX,lastY;
		static final int NONE = 0;
		static final int DRAG = 1;
		static final int PINCH = 2;
		private int mode = NONE;
		private static final int INVALID_POINTER_ID = -1;
        private int activePointerId;
		static final int ANIMATED_SCROLL_GAP = 250;

		private KrollDict lastUpEvent = new KrollDict(2);

		private long mLastScroll;

		private final Rect mTempRect = new Rect();
		private Scroller mScroller;

		private boolean mTwoDScrollViewMovedFocus;

		private float mLastMotionY;
		private float mLastMotionX;

		private boolean mIsLayoutDirty = true;

		private View mChildToScrollTo = null;

		private boolean mIsBeingDragged = false;

		private VelocityTracker mVelocityTracker;
		private boolean mFillViewport;

		private int mTouchSlop;
		private int mMinimumVelocity;
		private int mMaximumVelocity;

		private TiScrollViewLayout layout;
		
		public TDSV(Context context, LayoutArrangement arrangement) {
			super(context);
			if(context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH)) {
                pinchDetector = new ScaleGestureDetector(context, new ScaleListener());
			} else
                Log.i("UIPinchView","Multitouch not supported on this device!");
			setScrollBarStyle(SCROLLBARS_INSIDE_OVERLAY);
			setFillViewport(true);
			setScrollContainer(true);
			mScroller = new Scroller(context);
			setFocusable(true);
			setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
			setWillNotDraw(false);
			final ViewConfiguration configuration = ViewConfiguration
					.get(getContext());
			mTouchSlop = configuration.getScaledTouchSlop();
			mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
			mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();

			layout = new TiScrollViewLayout(context, arrangement);
			FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
					ViewGroup.LayoutParams.FILL_PARENT,
					ViewGroup.LayoutParams.FILL_PARENT);
			layout.setLayoutParams(params);
			super.addView(layout, params);
		}

		public void setFillViewport(boolean fillViewport) {
			if (fillViewport != mFillViewport) {
				mFillViewport = fillViewport;
				requestLayout();
			}
		}

		@Override
		public void addView(View child, android.view.ViewGroup.LayoutParams params) {
			layout.addView(child, params);
		}

		@Override
		protected float getTopFadingEdgeStrength() {
			if (getChildCount() == 0) {
				return 0.0f;
			}
			final int length = getVerticalFadingEdgeLength();
			if (getScrollY() < length) {
				return getScrollY() / (float) length;
			}
			return 1.0f;
		}

		@Override
		protected float getBottomFadingEdgeStrength() {
			if (getChildCount() == 0) {
				return 0.0f;
			}
			final int length = getVerticalFadingEdgeLength();
			final int bottomEdge = getHeight() - getPaddingBottom();
			final int span = getChildAt(0).getBottom() - getScrollY()
					- bottomEdge;
			if (span < length) {
				return span / (float) length;
			}
			return 1.0f;
		}

		@Override
		protected float getLeftFadingEdgeStrength() {
			if (getChildCount() == 0) {
				return 0.0f;
			}
			final int length = getHorizontalFadingEdgeLength();
			if (getScrollX() < length) {
				return getScrollX() / (float) length;
			}
			return 1.0f;
		}

		@Override
		protected float getRightFadingEdgeStrength() {
			if (getChildCount() == 0) {
				return 0.0f;
			}
			final int length = getHorizontalFadingEdgeLength();
			final int rightEdge = getWidth() - getPaddingRight();
			final int span = getChildAt(0).getRight() - getScrollX()
					- rightEdge;
			if (span < length) {
				return span / (float) length;
			}
			return 1.0f;
		}

		/**
		 * Determines whether or not there is room for scrolling - that is if the scrollview is smaller than its childview
		 * @return A boolean that states whether or not you can scroll
		 */
		private boolean canScroll() {
			View child = getChildAt(0);
			if (child != null) {
				int childHeight = child.getHeight();
				int childWidth = child.getWidth();
				return (getHeight() < childHeight + getPaddingTop()
						+ getPaddingBottom())
						|| (getWidth() < childWidth + getPaddingLeft()
								+ getPaddingRight());
			}
			return false;
		}

		/**
		 * Determines if the touch event should even be processed
		 * @return Whether or not the touch event should be processed
		 */
		@Override
		public boolean onInterceptTouchEvent(MotionEvent ev) {
			final int action = ev.getAction();
			if ((action == MotionEvent.ACTION_MOVE) && (mIsBeingDragged)) {
				mode=DRAG;
				return true;
			}
			if (!canScroll()) {
				mode=NONE;
				mIsBeingDragged = false;
				return false;
			}
			final float y = ev.getY();
			final float x = ev.getX();
			switch (action) {
			case MotionEvent.ACTION_MOVE:
				mode=DRAG;
				final int yDiff = (int) Math.abs(y - mLastMotionY);
				final int xDiff = (int) Math.abs(x - mLastMotionX);
				if (yDiff > mTouchSlop || xDiff > mTouchSlop) {
					mIsBeingDragged = true;
				}
				break;

			case MotionEvent.ACTION_DOWN:
				mLastMotionY = y;
				mLastMotionX = x;
				
				if (!mScroller.isFinished()) {
					mIsBeingDragged = true;
					mode=DRAG;
				} else {
					mode=NONE;
					mIsBeingDragged = false;
					return false;
				}
				break;
			
			case MotionEvent.ACTION_POINTER_DOWN:
				mIsBeingDragged = false;
				return true;
				
			case MotionEvent.ACTION_CANCEL:
			case MotionEvent.ACTION_UP:
				mode=NONE;
				mIsBeingDragged = false;
				break;
			}	

			return mIsBeingDragged;
		}
		
		/**
		 * A method for calculating the distance between two points/fingers based on coordinates on the screen
		 * @param event The event where the fingers are placed, containing the coordinates for each finger
		 * @return The distance in pixels
		 */
		private float distance(MotionEvent event) {
			float x = event.getX(0) - event.getX(1);
			float y = event.getY(0) - event.getY(1);
			return FloatMath.sqrt(x * x + y * y);
		}
		
		@Override
		public boolean onTouchEvent(MotionEvent ev) {
			//If mode has been set to PINCH, then two-finger movement should be processed by the pinchDetector-class
			if(mode == PINCH)
				if(pinchDetector != null) {
	                if(ev.getAction() != MotionEvent.ACTION_POINTER_UP || ev.getAction() != MotionEvent.ACTION_UP){
	                	return pinchDetector.onTouchEvent(ev);
	                }
	                	
				}
			if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getEdgeFlags() != 0) {
				return false;
			}

			if (!canScroll()) {
				return false;
			}

			if (mVelocityTracker == null) {
				mVelocityTracker = VelocityTracker.obtain();
			}
			mVelocityTracker.addMovement(ev);

			final int action = ev.getAction();
			float y = ev.getY();
			float x = ev.getX();
			
			float oldDist;
			
			switch (action & MotionEvent.ACTION_MASK) {
				//One finger down
				case MotionEvent.ACTION_DOWN:
					mode = DRAG;
					if (!mScroller.isFinished()) {
						mScroller.abortAnimation();
					}
					activePointerId = ev.getPointerId(0);
					mLastMotionY = y;
					mLastMotionX = x;
					lastX = x;
					lastY = y;
					break;
				//Second finger down. Only activated if the fingers are more than 10 pixel apart (sometimes one finger is registered as two)
				case MotionEvent.ACTION_POINTER_DOWN:
					mScroller.abortAnimation();
					oldDist = distance(ev);
					if(oldDist > 10f) {
						mode = PINCH;
						activePointerId = ev.getPointerId(1);
						lastX = x;
						lastY = y;
						return pinchDetector.onTouchEvent(ev);
					}
					break;
				//Movement with either one or two fingers
				case MotionEvent.ACTION_MOVE:
					if(mode == DRAG) {
						int deltaX = (int) (mLastMotionX - x);
						int deltaY = (int) (mLastMotionY - y);
						mLastMotionX = x;
						mLastMotionY = y;
						if (deltaX < 0) {
							if (getScrollX() < 0) {
								deltaX = 0;
							}
						} else if (deltaX > 0) {
							final int rightEdge = getWidth() - getPaddingRight();
							final int availableToScroll = getChildAt(0).getRight()
									- getScrollX() - rightEdge;
							if (availableToScroll > 0) {
								deltaX = Math.min(availableToScroll, deltaX);
							} else {
								deltaX = 0;
							}
						}
						if (deltaY < 0) {
							if (getScrollY() < 0) {
								deltaY = 0;
							}
						} else if (deltaY > 0) {
							final int bottomEdge = getHeight() - getPaddingBottom();
							final int availableToScroll = getChildAt(0).getBottom()
									- getScrollY() - bottomEdge;
							if (availableToScroll > 0) {
								deltaY = Math.min(availableToScroll, deltaY);
							} else {
								deltaY = 0;
							}
						}
						if (deltaY != 0 || deltaX != 0)
							scrollBy(deltaX, deltaY);
					} else if (mode == PINCH) {
						final int pointerIndex = ev.findPointerIndex(activePointerId);
	                    if(pointerIndex == INVALID_POINTER_ID) {
	                    	break;
	                    }
	                    x = ev.getX(pointerIndex);
	                    y = ev.getY(pointerIndex);
	                    lastX = x;
	                    lastY = y;
					} else {
						//Log.e(LCAT, " ACTION_MOVE unknown");
					}
					break;
				//Final finger lifted from screen
				case MotionEvent.ACTION_UP:
					 activePointerId = INVALID_POINTER_ID;
					final VelocityTracker velocityTracker = mVelocityTracker;
					velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
					int initialXVelocity = (int) velocityTracker.getXVelocity();
					int initialYVelocity = (int) velocityTracker.getYVelocity();
					if ((Math.abs(initialXVelocity) + Math.abs(initialYVelocity) > mMinimumVelocity)
							&& getChildCount() > 0) {
						fling(-initialXVelocity, -initialYVelocity);
					}
					if (mVelocityTracker != null) {
						mVelocityTracker.recycle();
						mVelocityTracker = null;
					}
					mode = NONE;
					break;
				//A finger is lifted from the screen, but one remains
				case MotionEvent.ACTION_POINTER_UP:
					if(mode == PINCH) {
						final int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
		                final int pointerId = ev.getPointerId(pointerIndex);
		                if(pointerId == activePointerId) {
		                        final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
		                        activePointerId = ev.getPointerId(newPointerIndex);
		                        lastX = ev.getX(newPointerIndex);
		                        lastY = ev.getY(newPointerIndex);
		                }
					}
					mode = DRAG;
					break;
				default:
					break;
			}
			
				
			return true;
		}
		
		private View findFocusableViewInMyBounds(final boolean topFocus,
				final int top, final boolean leftFocus, final int left,
				View preferredFocusable) {
			final int verticalFadingEdgeLength = getVerticalFadingEdgeLength() / 2;
			final int topWithoutFadingEdge = top + verticalFadingEdgeLength;
			final int bottomWithoutFadingEdge = top + getHeight()
					- verticalFadingEdgeLength;
			final int horizontalFadingEdgeLength = getHorizontalFadingEdgeLength() / 2;
			final int leftWithoutFadingEdge = left + horizontalFadingEdgeLength;
			final int rightWithoutFadingEdge = left + getWidth()
					- horizontalFadingEdgeLength;

			if ((preferredFocusable != null)
					&& (preferredFocusable.getTop() < bottomWithoutFadingEdge)
					&& (preferredFocusable.getBottom() > topWithoutFadingEdge)
					&& (preferredFocusable.getLeft() < rightWithoutFadingEdge)
					&& (preferredFocusable.getRight() > leftWithoutFadingEdge)) {
				return preferredFocusable;
			}
			return findFocusableViewInBounds(topFocus, topWithoutFadingEdge,
					bottomWithoutFadingEdge, leftFocus, leftWithoutFadingEdge,
					rightWithoutFadingEdge);
		}

		private View findFocusableViewInBounds(boolean topFocus, int top,
				int bottom, boolean leftFocus, int left, int right) {
			List<View> focusables = getFocusables(View.FOCUS_FORWARD);
			View focusCandidate = null;
			boolean foundFullyContainedFocusable = false;

			int count = focusables.size();
			for (int i = 0; i < count; i++) {
				View view = focusables.get(i);
				int viewTop = view.getTop();
				int viewBottom = view.getBottom();
				int viewLeft = view.getLeft();
				int viewRight = view.getRight();

				if (top < viewBottom && viewTop < bottom && left < viewRight
						&& viewLeft < right) {
					final boolean viewIsFullyContained = (top < viewTop)
							&& (viewBottom < bottom) && (left < viewLeft)
							&& (viewRight < right);
					if (focusCandidate == null) {
						focusCandidate = view;
						foundFullyContainedFocusable = viewIsFullyContained;
					} else {
						final boolean viewIsCloserToVerticalBoundary = (topFocus && viewTop < focusCandidate
								.getTop())
								|| (!topFocus && viewBottom > focusCandidate
										.getBottom());
						final boolean viewIsCloserToHorizontalBoundary = (leftFocus && viewLeft < focusCandidate
								.getLeft())
								|| (!leftFocus && viewRight > focusCandidate
										.getRight());
						if (foundFullyContainedFocusable) {
							if (viewIsFullyContained
									&& viewIsCloserToVerticalBoundary
									&& viewIsCloserToHorizontalBoundary) {
								focusCandidate = view;
							}
						} else {
							if (viewIsFullyContained) {
								focusCandidate = view;
								foundFullyContainedFocusable = true;
							} else if (viewIsCloserToVerticalBoundary
									&& viewIsCloserToHorizontalBoundary) {
								focusCandidate = view;
							}
						}
					}
				}
			}
			return focusCandidate;
		}
		/**
		 * A method for scrolling to the bottom or to the right of a view, in case a certain button is pressed
		 * @param direction The direction
		 * @param horizontal If the view is horizontal
		 * @return 
		 */
		public boolean fullScroll(int direction, boolean horizontal) {
			if (!horizontal) {
				boolean down = direction == View.FOCUS_DOWN;
				int height = getHeight();
				mTempRect.top = 0;
				mTempRect.bottom = height;
				if (down) {
					int count = getChildCount();
					if (count > 0) {
						View view = getChildAt(count - 1);
						mTempRect.bottom = view.getBottom();
						mTempRect.top = mTempRect.bottom - height;
					}
				}
				return scrollAndFocus(direction, mTempRect.top,
						mTempRect.bottom, 0, 0, 0);
			} else {
				boolean right = direction == View.FOCUS_DOWN;
				int width = getWidth();
				mTempRect.left = 0;
				mTempRect.right = width;
				if (right) {
					int count = getChildCount();
					if (count > 0) {
						View view = getChildAt(count - 1);
						mTempRect.right = view.getBottom();
						mTempRect.left = mTempRect.right - width;
					}
				}
				return scrollAndFocus(0, 0, 0, direction, mTempRect.top,
						mTempRect.bottom);
			}
		}

		private boolean scrollAndFocus(int directionY, int top, int bottom,
				int directionX, int left, int right) {
			boolean handled = true;
			int height = getHeight();
			int containerTop = getScrollY();
			int containerBottom = containerTop + height;
			boolean up = directionY == View.FOCUS_UP;
			int width = getWidth();
			int containerLeft = getScrollX();
			int containerRight = containerLeft + width;
			boolean leftwards = directionX == View.FOCUS_UP;
			View newFocused = findFocusableViewInBounds(up, top, bottom,
					leftwards, left, right);
			if (newFocused == null) {
				newFocused = this;
			}
			if ((top >= containerTop && bottom <= containerBottom)
					|| (left >= containerLeft && right <= containerRight)) {
				handled = false;
			} else {
				int deltaY = up ? (top - containerTop)
						: (bottom - containerBottom);
				int deltaX = leftwards ? (left - containerLeft)
						: (right - containerRight);
				doScroll(deltaX, deltaY);
			}
			if (newFocused != findFocus()
					&& newFocused.requestFocus(directionY)) {
				mTwoDScrollViewMovedFocus = true;
				mTwoDScrollViewMovedFocus = false;
			}
			return handled;
		}

		private void doScroll(int deltaX, int deltaY) {
			if (deltaX != 0 || deltaY != 0) {
				smoothScrollBy(deltaX, deltaY);
			}
		}

		public final void smoothScrollBy(int dx, int dy) {
			long duration = AnimationUtils.currentAnimationTimeMillis()
					- mLastScroll;
			if (duration > ANIMATED_SCROLL_GAP) {
				mScroller.startScroll(getScrollX(), getScrollY(), dx, dy);
				awakenScrollBars(mScroller.getDuration());
				invalidate();
			} else {
				if (!mScroller.isFinished()) {
					mScroller.abortAnimation();
				}
				scrollBy(dx, dy);
			}
			mLastScroll = AnimationUtils.currentAnimationTimeMillis();
		}

		public final void smoothScrollTo(int x, int y) {
			smoothScrollBy(x - getScrollX(), y - getScrollY());
		}
		
		@Override
		protected int computeVerticalScrollRange() {
			int count = getChildCount();
			return count == 0 ? getHeight() : (getChildAt(0)).getBottom();
		}

		@Override
		protected int computeHorizontalScrollRange() {
			int count = getChildCount();
			return count == 0 ? getWidth() : (getChildAt(0)).getRight();
		}

		@Override
		protected void measureChild(View child, int parentWidthMeasureSpec,
				int parentHeightMeasureSpec) {
			ViewGroup.LayoutParams lp = child.getLayoutParams();
			int childWidthMeasureSpec;
			int childHeightMeasureSpec;

			childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec,
					getPaddingLeft() + getPaddingRight(), lp.width);
			childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(0,
					MeasureSpec.UNSPECIFIED);

			child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
		}

		@Override
		protected void measureChildWithMargins(View child,
				int parentWidthMeasureSpec, int widthUsed,
				int parentHeightMeasureSpec, int heightUsed) {
			final MarginLayoutParams lp = (MarginLayoutParams) child
					.getLayoutParams();
			final int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
					lp.leftMargin + lp.rightMargin, MeasureSpec.UNSPECIFIED);
			final int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
					lp.topMargin + lp.bottomMargin, MeasureSpec.UNSPECIFIED);

			child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
		}
		/**
		 * The method that executes the scroll
		 */
		@Override
		public void computeScroll() {
			if (mScroller.computeScrollOffset()) {
				int oldX = getScrollX();
				int oldY = getScrollY();
				int x = mScroller.getCurrX();
				int y = mScroller.getCurrY();
				if (getChildCount() > 0) {
					View child = getChildAt(0);
					scrollTo(
							clamp(x, getWidth() - getPaddingRight()
									- getPaddingLeft(), child.getWidth()),
							clamp(y, getHeight() - getPaddingBottom()
									- getPaddingTop(), child.getHeight()));
				} else {
					scrollTo(x, y);
				}
				if (oldX != getScrollX() || oldY != getScrollY()) {
					onScrollChanged(getScrollX(), getScrollY(), oldX, oldY);
				}
				postInvalidate();
			}
		}

		private void scrollToChild(View child) {
			child.getDrawingRect(mTempRect);
			offsetDescendantRectToMyCoords(child, mTempRect);
			int scrollDelta = computeScrollDeltaToGetChildRectOnScreen(mTempRect);
			if (scrollDelta != 0) {
				scrollBy(0, scrollDelta);
			}
		}

		private boolean scrollToChildRect(Rect rect, boolean immediate) {
			final int delta = computeScrollDeltaToGetChildRectOnScreen(rect);
			final boolean scroll = delta != 0;
			if (scroll) {
				if (immediate) {
					scrollBy(0, delta);
				} else {
					smoothScrollBy(0, delta);
				}
			}
			return scroll;
		}

		protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
			if (getChildCount() == 0)
				return 0;
			int height = getHeight();
			int screenTop = getScrollY();
			int screenBottom = screenTop + height;
			int fadingEdge = getVerticalFadingEdgeLength();
			if (rect.top > 0) {
				screenTop += fadingEdge;
			}

			if (rect.bottom < getChildAt(0).getHeight()) {
				screenBottom -= fadingEdge;
			}
			int scrollYDelta = 0;
			if (rect.bottom > screenBottom && rect.top > screenTop) {
				if (rect.height() > height) {
					scrollYDelta += (rect.top - screenTop);
				} else {
					scrollYDelta += (rect.bottom - screenBottom);
				}

				int bottom = getChildAt(0).getBottom();
				int distanceToBottom = bottom - screenBottom;
				scrollYDelta = Math.min(scrollYDelta, distanceToBottom);

			} else if (rect.top < screenTop && rect.bottom < screenBottom) {

				if (rect.height() > height) {
					scrollYDelta -= (screenBottom - rect.bottom);
				} else {
					scrollYDelta -= (screenTop - rect.top);
				}

				scrollYDelta = Math.max(scrollYDelta, -getScrollY());
			}
			return scrollYDelta;
		}

		@Override
		public void requestChildFocus(View child, View focused) {
			if (!mTwoDScrollViewMovedFocus) {
				if (!mIsLayoutDirty) {
					scrollToChild(focused);
				} else {
					mChildToScrollTo = focused;
				}
			}
			super.requestChildFocus(child, focused);
		}

		@Override
		protected boolean onRequestFocusInDescendants(int direction,
				Rect previouslyFocusedRect) {
			if (direction == View.FOCUS_FORWARD) {
				direction = View.FOCUS_DOWN;
			} else if (direction == View.FOCUS_BACKWARD) {
				direction = View.FOCUS_UP;
			}

			final View nextFocus = previouslyFocusedRect == null ? FocusFinder
					.getInstance().findNextFocus(this, null, direction)
					: FocusFinder.getInstance().findNextFocusFromRect(this,
							previouslyFocusedRect, direction);

			if (nextFocus == null) {
				return false;
			}

			return nextFocus.requestFocus(direction, previouslyFocusedRect);
		}

		@Override
		public boolean requestChildRectangleOnScreen(View child,
				Rect rectangle, boolean immediate) {
			rectangle.offset(child.getLeft() - child.getScrollX(),
					child.getTop() - child.getScrollY());
			return scrollToChildRect(rectangle, immediate);
		}

		@Override
		public void requestLayout() {
			mIsLayoutDirty = true;
			super.requestLayout();
		}

		@Override
		protected void onLayout(boolean changed, int l, int t, int r, int b) {
			super.onLayout(changed, l, t, r, b);
			mIsLayoutDirty = false;
			if (mChildToScrollTo != null
					&& isViewDescendantOf(mChildToScrollTo, this)) {
				scrollToChild(mChildToScrollTo);
			}
			mChildToScrollTo = null;
			
			scrollTo(getScrollX(), getScrollY());
		}

		@Override
		protected void onSizeChanged(int w, int h, int oldw, int oldh) {
			super.onSizeChanged(w, h, oldw, oldh);

			View currentFocused = findFocus();
			if (null == currentFocused || this == currentFocused)
				return;

			currentFocused.getDrawingRect(mTempRect);
			offsetDescendantRectToMyCoords(currentFocused, mTempRect);
			int scrollDeltaX = computeScrollDeltaToGetChildRectOnScreen(mTempRect);
			int scrollDeltaY = computeScrollDeltaToGetChildRectOnScreen(mTempRect);
			doScroll(scrollDeltaX, scrollDeltaY);
		}

		private boolean isViewDescendantOf(View child, View parent) {
			if (child == parent) {
				return true;
			}

			final ViewParent theParent = child.getParent();
			return (theParent instanceof ViewGroup)
					&& isViewDescendantOf((View) theParent, parent);
		}
		
		/**
		 * The method that executes the flinging gesture, using velocity calculated when the finger is lifted from the screen
		 * @param velocityX The velocity in the x-dimension
		 * @param velocityY The velocity in the y-dimension
		 */
		public void fling(int velocityX, int velocityY) {
			if (getChildCount() > 0) {
				int height = getHeight() - getPaddingBottom() - getPaddingTop();
				int bottom = getChildAt(0).getHeight();
				int width = getWidth() - getPaddingRight() - getPaddingLeft();
				int right = getChildAt(0).getWidth();

				mScroller.fling(getScrollX(), getScrollY(), velocityX,
						velocityY, 0, right - width, 0, bottom - height);

				final boolean movingDown = velocityY > 0;
				final boolean movingRight = velocityX > 0;

				View newFocused = findFocusableViewInMyBounds(movingRight,
						mScroller.getFinalX(), movingDown,
						mScroller.getFinalY(), findFocus());
				if (newFocused == null) {
					newFocused = this;
				}

				if (newFocused != findFocus()
						&& newFocused.requestFocus(movingDown ? View.FOCUS_DOWN
								: View.FOCUS_UP)) {
					mTwoDScrollViewMovedFocus = true;
					mTwoDScrollViewMovedFocus = false;
				}

				awakenScrollBars(mScroller.getDuration());
				invalidate();
			}
		}

		public void scrollTo(int x, int y) {
			if (getChildCount() > 0) {
				View child = getChildAt(0);
				x = clamp(x, getWidth() - getPaddingRight() - getPaddingLeft(),
						child.getWidth());
				y = clamp(y,
						getHeight() - getPaddingBottom() - getPaddingTop(),
						child.getHeight());
				if (x != getScrollX() || y != getScrollY()) {
					super.scrollTo(x, y);
				}
			}
		}

		private int clamp(int n, int my, int child) {
			if (my >= child || n < 0) {
				return 0;
			}
			if ((my + n) > child) {
				return child - my;
			}
			return n;
		}
		
		/**
		 * A class to capture the pinching of the scrollview
		 */
		private class ScaleListener extends SimpleOnScaleGestureListener {

			@Override
			public boolean onScaleBegin(ScaleGestureDetector detector) {
				KrollDict event = new KrollDict();
				event.put("scale", curZoom);
				proxy.fireEvent("pinchStart", event);
				return true;
			}
			/**
			 * Calculates the current zoom value, so it can be used to scale views
			 */
			@Override
			public boolean onScale(ScaleGestureDetector detector) {
				
				curZoom *= detector.getScaleFactor();
				curZoom = Math.max(minZoom, Math.min(curZoom, maxZoom));
				
				//TODO: I need help here. I cannot figure out how to scale the scrollView's content
				
				/*
				//get child view
				View javascriptView = layout.getChildAt(0);
				int newWidth = (int)((int)javascriptView.getWidth()*curZoom);
				int newHeight = (int)((int)javascriptView.getHeight()*curZoom);
				*/
				
				KrollDict event = new KrollDict();
				event.put("scale", curZoom);
				proxy.fireEvent("pinch", event);
				return true;
			}
			
			@Override
			public void onScaleEnd(ScaleGestureDetector detector) {
				mode = NONE;
				KrollDict event = new KrollDict();
				event.put("scale", curZoom);
				proxy.fireEvent("pinchEnd", event);
			}
			
		}

	}
	
	/**
	 * The view that's called by Titanium
	 * @param proxy The current proxy
	 */
	public NappscrollView(TiViewProxy proxy) {
		super(proxy);
		getLayoutParams().autoFillsHeight = true;
		getLayoutParams().autoFillsWidth = true;
	}

	public void setContentOffset(int x, int y) {
		KrollDict offset = new KrollDict();
		offsetX = x;
		offsetY = y;
		offset.put(TiC.EVENT_PROPERTY_X, offsetX);
		offset.put(TiC.EVENT_PROPERTY_Y, offsetY);
		getProxy().setProperty(TiC.PROPERTY_CONTENT_OFFSET, offset);
	}

	public void setContentOffset(Object hashMap) {
		if (hashMap instanceof HashMap) {
			HashMap contentOffset = (HashMap) hashMap;
			offsetX = TiConvert.toInt(contentOffset, TiC.PROPERTY_X);
			offsetY = TiConvert.toInt(contentOffset, TiC.PROPERTY_Y);
		} else {
			Log.e(LCAT, "contentOffset must be an instance of HashMap");
		}
	}

	@Override
	public void propertyChanged(String key, Object oldValue, Object newValue,
			KrollProxy proxy) {
		if (DBG) {
			Log.d(LCAT, "Property: " + key + " old: " + oldValue + " new: "+ newValue);
		}
		if (key.equals(TiC.PROPERTY_CONTENT_OFFSET)) {
			setContentOffset(newValue);
			scrollTo(offsetX, offsetY);
		}
		if (key.equals(TiC.PROPERTY_HEIGHT)) {
			getNativeView().getLayoutParams().height = TiConvert.toInt(newValue);
		}
		if (key.equals(TiC.PROPERTY_WIDTH)) {
			getNativeView().getLayoutParams().width = TiConvert.toInt(newValue);
		}
		super.propertyChanged(key, oldValue, newValue, proxy);
	}

	/**
	 * The method that actually creates the scrollview, using information given in javascript
	 */
	@Override
	public void processProperties(KrollDict d) {
		if (d.containsKey(TiC.PROPERTY_CONTENT_OFFSET)) {
			Object offset = d.get(TiC.PROPERTY_CONTENT_OFFSET);
			setContentOffset(offset);
		}
		
		//custom properties for the scale
		if(d.containsKeyAndNotNull("maxZoomValue"))
            maxZoom = d.getDouble("maxZoomValue").floatValue();
		if(d.containsKeyAndNotNull("minZoomValue"))
            minZoom = d.getDouble("minZoomValue").floatValue();
		
		//We do not want to use Horizontal or Vertical - due to the Bidirectional (2D) scrolling
		LayoutArrangement arrangement = LayoutArrangement.DEFAULT;
		scrollView = new TDSV(getProxy().getActivity(), arrangement);
		setNativeView(scrollView);
		
		
		
		/*
		//TODO: NOT WORKING - Need to implement scroll indicators 
		boolean showHorizontalScrollBar = false;
		boolean showVerticalScrollBar = false;
		if (d.containsKey(TiC.PROPERTY_SHOW_HORIZONTAL_SCROLL_INDICATOR)) {
			showHorizontalScrollBar = TiConvert.toBoolean(d, TiC.PROPERTY_SHOW_HORIZONTAL_SCROLL_INDICATOR);
		}
		if (d.containsKey(TiC.PROPERTY_SHOW_VERTICAL_SCROLL_INDICATOR)) {
			showVerticalScrollBar = TiConvert.toBoolean(d, TiC.PROPERTY_SHOW_VERTICAL_SCROLL_INDICATOR);
		}
		nativeView.setHorizontalScrollBarEnabled(showHorizontalScrollBar);
		nativeView.setVerticalScrollBarEnabled(showVerticalScrollBar);
		*/
		
		super.processProperties(d);
	}
	

    
    public void setMaxZoomValue(float maxZoom)
    {
    	this.maxZoom = maxZoom;
    }
    
    public void setMinZoomValue(float minZoom)
    {
    	this.minZoom = minZoom;
    }
    
    public void setCurZoomValue(float curZoom)
    {
    	this.curZoom = curZoom;
    }

	public TiScrollViewLayout getLayout() {
		View nativeView = getNativeView();
		return ((TDSV) nativeView).layout;
	}
	
	
	public void scrollTo(int x, int y) {
		getNativeView().scrollTo(x, y);
		getNativeView().computeScroll();
	}

	public void scrollToBottom() {
		View view = getNativeView();
		TDSV scrollView = (TDSV) view;
		scrollView.fullScroll(View.FOCUS_DOWN, false);
	}

	@Override
	public void add(TiUIView child) {
		super.add(child);

		if (getNativeView() != null) {
			getLayout().requestLayout();
			if (child.getNativeView() != null) {
				child.getNativeView().requestLayout();
			}
		}
	}

	@Override
	public void remove(TiUIView child) {
		if (child != null) {
			View cv = child.getNativeView();
			if (cv != null) {
				View nv = getLayout();
				if (nv instanceof ViewGroup) {
					((ViewGroup) nv).removeView(cv);
					children.remove(child);
					child.setParent(null);
				}
			}
		}
	}

}