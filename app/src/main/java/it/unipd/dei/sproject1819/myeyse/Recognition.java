package it.unipd.dei.sproject1819.myeyse;

import android.graphics.RectF;

public class Recognition
{
    /**
     * Display name for the recognition.
     */
    private final String title;

    /**
     * A sortable score for how good the recognition is relative to others. Higher should be better.
     */
    private final Float confidence;

    /**
     * Optional location within the source image for the location of the recognized object.
     */
    private RectF location;


    public Recognition(final String title, final Float confidence, final RectF location)
    {
        this.title = title;
        this.confidence = confidence;
        this.location = location;
    }

    public String getTitle()
    {
        return title;
    }

    public Float getConfidence()
    {
        return confidence;
    }

    public RectF getLocation()
    {
        return new RectF(location);
    }

    public void setLocation(RectF location)
    {
        this.location = location;
    }
}
