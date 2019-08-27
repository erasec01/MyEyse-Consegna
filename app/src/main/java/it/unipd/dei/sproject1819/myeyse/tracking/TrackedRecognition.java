package it.unipd.dei.sproject1819.myeyse.tracking;

import android.graphics.RectF;

public class TrackedRecognition
{
    //Defines the number of objects currently tracked
    private static int numberObjectTrack = 0;

    //Id of this current object
    private int mId;

    //Name of object
    private String mTitle;

    private RectF mLocation;

    private float mDetectionConfidence;

    //Di default non ho posizione
    private MultiBoxTracker.Direction mLastPosSpeech = MultiBoxTracker.Direction.UNDEFINED;

    //First position in which it was recognized
    private float mFirstPosition;

    //First position in which it was recognized
    private float mLastPosition;

    //Define if object is localized near start position
    private boolean isNearStartPosition = false;

    /**
     * This constructor is useful if you start tracing a new object.
     * The id attributed is unique obviously.
     *
     * @param trackedObject
     * @param _detectionConfidence
     * @param title
     * @param location
     */
    public TrackedRecognition(ObjectTracker.TrackedObject trackedObject, float _detectionConfidence,
                              String title, RectF location)
    {
        numberObjectTrack++;
        mId = numberObjectTrack;
        this.trackedObject = trackedObject;
        mLocation = location;
        mDetectionConfidence = _detectionConfidence;
        mTitle = title;
    }

    /**
     * @param id
     * @param trackedObject
     * @param _detectionConfidence
     * @param title
     * @param location
     */
    public TrackedRecognition(int id, ObjectTracker.TrackedObject trackedObject,
                              float _detectionConfidence, String title, RectF location)
    {
        mId = id;
        this.trackedObject = trackedObject;
        mLocation = location;
        mDetectionConfidence = _detectionConfidence;
        mTitle = title;
    }

    /**
     * Reset number of object tracked. This is invoked in the transition between object detection
     * and panoramic mode. In this way, we recognize n objects, these have an id ranging from 1..n
     */
    public static void resetNumObjectTrack()
    {
        numberObjectTrack = 0;
    }

    //Object that contains all real tracked information(used as a black box)
    public ObjectTracker.TrackedObject trackedObject;

    public RectF getLocation()
    {
        return mLocation;
    }

    public float getDetectionConfidence()
    {
        return mDetectionConfidence;
    }

    public String getTitle()
    {
        return mTitle;
    }

    public MultiBoxTracker.Direction getLastPosSpeech()
    {
        return mLastPosSpeech;
    }

    public void setLastPosSpeech(MultiBoxTracker.Direction direction)
    {
        mLastPosSpeech = direction;
    }

    public float getFirstPosition()
    {
        return mFirstPosition;
    }

    public float getLastPosition()
    {
        return mLastPosition;
    }

    public void setFirstPosition(float position)
    {
        mFirstPosition = position;
    }

    public void setLastPosition(float position)
    {
        mLastPosition = position;
    }

    public int getId()
    {
        return mId;
    }
}
