package it.unipd.dei.sproject1819.myeyse;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;

/**
 * A {@link TextureView} that can be adjusted to a specified aspect ratio.
 * TextureView is used to show the preview of the camera. Different camera have
 * different resolution, so we need to choose the most similar regard to the resolution
 * of the screen in order to have a full-screen preview.
 */
public class AutoFitTextureView extends TextureView
{
    private int ratioWidth = 0;
    private int ratioHeight = 0;

    public AutoFitTextureView(final Context context)
    {
        this(context, null);
    }

    public AutoFitTextureView(final Context context, final AttributeSet attrs)
    {
        this(context, attrs, 0);
    }

    public AutoFitTextureView(final Context context, final AttributeSet attrs, final int defStyle)
    {
        super(context, attrs, defStyle);
    }

    /**
     * Simply sets variable that indicate the aspect ratio for this view.
     * We want to set the aspect ratio of the view at the same aspect ratio of the display.
     *
     * @param width  Relative horizontal size
     * @param height Relative vertical size
     */
    public void setAspectRatio(final int width, final int height)
    {
        if (width < 0 || height < 0)
        {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        ratioWidth = width;
        ratioHeight = height;
        requestLayout();
    }

    /**
     * Real set the aspect ratio of the TextView.
     *
     * @param widthMeasureSpec
     * @param heightMeasureSpec
     */
    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec)
    {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = MeasureSpec.getSize(heightMeasureSpec);
        if (0 == ratioWidth || 0 == ratioHeight)
        {
            setMeasuredDimension(width, height);
        }
        else
        {
            if (width < height * ratioWidth / ratioHeight)
            {
                setMeasuredDimension(width, width * ratioHeight / ratioWidth);
            }
            else
            {
                setMeasuredDimension(height * ratioWidth / ratioHeight, height);
            }
        }
    }
}
