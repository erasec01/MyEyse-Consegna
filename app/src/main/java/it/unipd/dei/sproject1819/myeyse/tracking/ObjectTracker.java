package it.unipd.dei.sproject1819.myeyse.tracking;
import android.graphics.PointF;
import android.graphics.RectF;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Vector;

public class ObjectTracker
{
    //private static final Logger LOGGER = new Logger();

    static
    {
        try
        {
            System.loadLibrary("tensorflow_demo");
        }
        catch (UnsatisfiedLinkError e)
        {
            int x = 0;
            //LOGGER.e("libtensorflow_demo.so not found, tracking unavailable");
        }
    }

    private static final boolean DRAW_TEXT = false;

    /**
     * How many history points to keep track of and draw in the red history line.
     */
    private static final int MAX_DEBUG_HISTORY_SIZE = 30;

    /**
     * How many frames of optical flow deltas to record.
     * TODO(andrewharp): Push this down to the native level so it can be polled
     * efficiently into a an array for upload, instead of keeping a duplicate
     * copy in Java.
     */
    private static final int MAX_FRAME_HISTORY_SIZE = 200;

    private static final int DOWNSAMPLE_FACTOR = 2;

    private final byte[] downsampledFrame;

    protected static ObjectTracker instance;

    private final Map<String, TrackedObject> trackedObjects;

    private long lastTimestamp;

    private FrameChange lastKeypoints;

    private final Vector<PointF> debugHistory;

    private final LinkedList<TimestampedDeltas> timestampedDeltas;

    protected final int frameWidth;
    protected final int frameHeight;
    private final int rowStride;

    protected final boolean alwaysTrack;

    private static class TimestampedDeltas
    {
        final long timestamp;
        final byte[] deltas;

        public TimestampedDeltas(final long timestamp, final byte[] deltas)
        {
            this.timestamp = timestamp;
            this.deltas = deltas;
        }
    }

    /**
     * A simple class that records keypoint information, which includes
     * local location, score and type. This will be used in calculating
     * FrameChange.
     */
    public static class Keypoint
    {
        public final float x;
        public final float y;
        public final float score;
        public final int type;

        public Keypoint(final float x, final float y)
        {
            this.x = x;
            this.y = y;
            this.score = 0;
            this.type = -1;
        }

        public Keypoint(final float x, final float y, final float score, final int type)
        {
            this.x = x;
            this.y = y;
            this.score = score;
            this.type = type;
        }

        Keypoint delta(final Keypoint other)
        {
            return new Keypoint(this.x - other.x, this.y - other.y);
        }
    }

    /**
     * A simple class that could calculate Keypoint delta.
     * This class will be used in calculating frame translation delta
     * for optical flow.
     */
    public static class PointChange
    {
        public final Keypoint keypointA;
        public final Keypoint keypointB;
        Keypoint pointDelta;
        private final boolean wasFound;

        public PointChange(final float x1, final float y1,
                           final float x2, final float y2,
                           final float score, final int type,
                           final boolean wasFound)
        {
            this.wasFound = wasFound;

            keypointA = new Keypoint(x1, y1, score, type);
            keypointB = new Keypoint(x2, y2);
        }
    }

    /**
     * A class that records a timestamped frame translation delta for optical flow.
     */
    public static class FrameChange
    {
        public static final int KEYPOINT_STEP = 7;

        public final Vector<PointChange> pointDeltas;

        private final float minScore;
        private final float maxScore;

        public FrameChange(final float[] framePoints)
        {
            float minScore = 100.0f;
            float maxScore = -100.0f;

            pointDeltas = new Vector<PointChange>(framePoints.length / KEYPOINT_STEP);

            for (int i = 0; i < framePoints.length; i += KEYPOINT_STEP)
            {
                final float x1 = framePoints[i + 0] * DOWNSAMPLE_FACTOR;
                final float y1 = framePoints[i + 1] * DOWNSAMPLE_FACTOR;

                final boolean wasFound = framePoints[i + 2] > 0.0f;

                final float x2 = framePoints[i + 3] * DOWNSAMPLE_FACTOR;
                final float y2 = framePoints[i + 4] * DOWNSAMPLE_FACTOR;
                final float score = framePoints[i + 5];
                final int type = (int) framePoints[i + 6];

                minScore = Math.min(minScore, score);
                maxScore = Math.max(maxScore, score);

                pointDeltas.add(new PointChange(x1, y1, x2, y2, score, type, wasFound));
            }

            this.minScore = minScore;
            this.maxScore = maxScore;
        }
    }

    public static synchronized ObjectTracker getInstance(
            final int frameWidth, final int frameHeight, final int rowStride, final boolean alwaysTrack)
    {

        if (instance == null)
        {
            instance = new ObjectTracker(frameWidth, frameHeight, rowStride, alwaysTrack);
            instance.init();
        }
        else
        {
            throw new RuntimeException(
                    "Tried to create a new objectracker before releasing the old one!");
        }
        return instance;
    }

    public static synchronized void clearInstance()
    {
        if (instance != null)
        {
            instance.release();
        }
    }

    protected ObjectTracker(final int frameWidth, final int frameHeight, final int rowStride, final boolean alwaysTrack)
    {
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.rowStride = rowStride;
        this.alwaysTrack = alwaysTrack;
        this.timestampedDeltas = new LinkedList<TimestampedDeltas>();

        trackedObjects = new HashMap<String, TrackedObject>();

        debugHistory = new Vector<PointF>(MAX_DEBUG_HISTORY_SIZE);

        downsampledFrame =
                new byte
                        [(frameWidth + DOWNSAMPLE_FACTOR - 1)
                        / DOWNSAMPLE_FACTOR
                        * (frameWidth + DOWNSAMPLE_FACTOR - 1)
                        / DOWNSAMPLE_FACTOR];
    }

    protected void init()
    {
        // The native tracker never sees the full frame, so pre-scale dimensions
        // by the downsample factor.
        initNative(frameWidth / DOWNSAMPLE_FACTOR, frameHeight / DOWNSAMPLE_FACTOR, alwaysTrack);
    }


    private long downsampledTimestamp;


    public synchronized void nextFrame(final byte[] frameData, final byte[] uvData, final long timestamp, final float[] transformationMatrix, final boolean updateDebugInfo)
    {
        if (downsampledTimestamp != timestamp)
        {
            ObjectTracker.downsampleImageNative(frameWidth, frameHeight, rowStride, frameData, DOWNSAMPLE_FACTOR, downsampledFrame);//downsampledFrame
            downsampledTimestamp = timestamp;
        }

        // Do Lucas Kanade using the fullframe initializer.
        nextFrameNative(downsampledFrame, uvData, timestamp, transformationMatrix);//downsampledFrame

        timestampedDeltas.add(new TimestampedDeltas(timestamp, getKeypointsPacked(DOWNSAMPLE_FACTOR)));

        while (timestampedDeltas.size() > MAX_FRAME_HISTORY_SIZE)
        {
            timestampedDeltas.removeFirst();
        }

        //Map<String, TrackedObject> trackedObjects;
        for (final TrackedObject trackedObject : trackedObjects.values())
        {
            trackedObject.updateTrackedPosition();
        }

        if (updateDebugInfo)
        {
            updateDebugHistory();
        }

        lastTimestamp = timestamp;
    }

    public synchronized void release()
    {
        releaseMemoryNative();
        synchronized (ObjectTracker.class)
        {
            instance = null;
        }
    }


    private synchronized PointF getAccumulatedDelta(final long timestamp, final float positionX,
                                                    final float positionY, final float radius)
    {
        final RectF currPosition = getCurrentPosition(timestamp,
                new RectF(positionX - radius, positionY - radius, positionX + radius, positionY + radius));
        return new PointF(currPosition.centerX() - positionX, currPosition.centerY() - positionY);
    }

    private synchronized RectF getCurrentPosition(final long timestamp, final RectF
            oldPosition)
    {
        final RectF downscaledFrameRect = downscaleRect(oldPosition);

        final float[] delta = new float[4];
        getCurrentPositionNative(timestamp, downscaledFrameRect.left, downscaledFrameRect.top,
                downscaledFrameRect.right, downscaledFrameRect.bottom, delta);

        final RectF newPosition = new RectF(delta[0], delta[1], delta[2], delta[3]);

        return upscaleRect(newPosition);
    }

    private void updateDebugHistory()
    {
        lastKeypoints = new FrameChange(getKeypointsNative(false));

        if (lastTimestamp == 0)
        {
            return;
        }

        final PointF delta =
                getAccumulatedDelta(
                        lastTimestamp, frameWidth / DOWNSAMPLE_FACTOR, frameHeight / DOWNSAMPLE_FACTOR, 100);

        synchronized (debugHistory)
        {
            debugHistory.add(delta);

            while (debugHistory.size() > MAX_DEBUG_HISTORY_SIZE)
            {
                debugHistory.remove(0);
            }
        }
    }

    private RectF downscaleRect(final RectF fullFrameRect)
    {
        return new RectF(
                fullFrameRect.left / DOWNSAMPLE_FACTOR,
                fullFrameRect.top / DOWNSAMPLE_FACTOR,
                fullFrameRect.right / DOWNSAMPLE_FACTOR,
                fullFrameRect.bottom / DOWNSAMPLE_FACTOR);
    }

    private RectF upscaleRect(final RectF downsampledFrameRect)
    {
        return new RectF(
                downsampledFrameRect.left * DOWNSAMPLE_FACTOR,
                downsampledFrameRect.top * DOWNSAMPLE_FACTOR,
                downsampledFrameRect.right * DOWNSAMPLE_FACTOR,
                downsampledFrameRect.bottom * DOWNSAMPLE_FACTOR);
    }
    static int cnt = 1;
    /**
     * A TrackedObject represents a native TrackedObject, and provides access to the
     * relevant native tracking information available after every frame update. They may
     * be safely passed around and accessed externally, but will become invalid after
     * stopTracking() is called or the related creating ObjectTracker is deactivated.
     *
     * @author andrewharp@google.com (Andrew Harp)
     */
    public class TrackedObject
    {
        //Non è final in quanto l'id viene ceduto quando si cambia colore
        final private String id;

        private long lastExternalPositionTime;

        private RectF lastTrackedPosition;

        private boolean visibleInLastFrame;

        private boolean isDead;

        //Costruttore
        TrackedObject(final RectF position, final long timestamp, final byte[] data)
        {
            isDead = false;

            //hashCode() return distinct integers for distinct object
            id = Integer.toString(cnt);

            cnt++;
            lastExternalPositionTime = timestamp;

            synchronized (ObjectTracker.this)
            {
                registerInitialAppearance(position, data);
                setPreviousPosition(position, timestamp);
                trackedObjects.put(id, this);
            }
        }

        public void stopTracking()
        {
            checkValidObject();

            synchronized (ObjectTracker.this)
            {
                isDead = true;
                forgetNative(id);
                trackedObjects.remove(id);
            }
        }

        public float getCurrentCorrelation()
        {
            checkValidObject();
            return ObjectTracker.this.getCurrentCorrelation(id);
        }

        void registerInitialAppearance(final RectF position, final byte[] data)
        {
            final RectF externalPosition = downscaleRect(position);
            registerNewObjectWithAppearanceNative(id, externalPosition.left, externalPosition.top, externalPosition.right, externalPosition.bottom, data);
        }

        synchronized void setPreviousPosition(final RectF position, final long timestamp)
        {
            checkValidObject();
            synchronized (ObjectTracker.this)
            {
                if (lastExternalPositionTime > timestamp)
                {
                    //LOGGER.w("Tried to use older position time!");
                    return;
                }
                final RectF externalPosition = downscaleRect(position);
                lastExternalPositionTime = timestamp;

                setPreviousPositionNative(id,
                        externalPosition.left, externalPosition.top,
                        externalPosition.right, externalPosition.bottom,
                        lastExternalPositionTime);

                updateTrackedPosition();
            }
        }

        private synchronized void updateTrackedPosition()
        {
            checkValidObject();

            final float[] delta = new float[4];
            getTrackedPositionNative(id, delta);
            lastTrackedPosition = new RectF(delta[0], delta[1], delta[2], delta[3]);
            visibleInLastFrame = isObjectVisible(id);
        }

        public synchronized RectF getTrackedPositionInPreviewFrame()
        {
            checkValidObject();

            if (lastTrackedPosition == null)
            {
                return null;
            }
            return upscaleRect(lastTrackedPosition);
        }

        private void checkValidObject()
        {
            if (isDead)
            {
                throw new RuntimeException("TrackedObject already removed from tracking!");
            }
            else if (ObjectTracker.this != instance)
            {
                throw new RuntimeException("TrackedObject created with another ObjectTracker!");
            }
        }
    }

    public synchronized TrackedObject trackObject(final RectF position, final long timestamp, final byte[] frameData)
    {
        if (downsampledTimestamp != timestamp)
        {
            ObjectTracker.downsampleImageNative(frameWidth, frameHeight, rowStride, frameData, DOWNSAMPLE_FACTOR, downsampledFrame);
            downsampledTimestamp = timestamp;
        }
        return new TrackedObject(position, timestamp, downsampledFrame);
    }

    /** ********************* NATIVE CODE ************************************ */

    /**
     * This will contain an opaque pointer to the native ObjectTracker
     */
    private long nativeObjectTracker;

    private native void initNative(int imageWidth, int imageHeight, boolean alwaysTrack);

    protected native void registerNewObjectWithAppearanceNative(
            String objectId, float x1, float y1, float x2, float y2, byte[] data);

    protected native void setPreviousPositionNative(
            String objectId, float x1, float y1, float x2, float y2, long timestamp);

    protected native void setCurrentPositionNative(
            String objectId, float x1, float y1, float x2, float y2);

    protected native void forgetNative(String key);

    protected native String getModelIdNative(String key);

    protected native boolean haveObject(String key);

    protected native boolean isObjectVisible(String key);

    protected native float getCurrentCorrelation(String key);

    protected native float getMatchScore(String key);

    protected native void getTrackedPositionNative(String key, float[] points);

    protected native void nextFrameNative(
            byte[] frameData, byte[] uvData, long timestamp, float[] frameAlignMatrix);

    protected native void releaseMemoryNative();

    protected native void getCurrentPositionNative(long timestamp,
                                                   final float positionX1, final float positionY1,
                                                   final float positionX2, final float positionY2,
                                                   final float[] delta);

    protected native byte[] getKeypointsPacked(float scaleFactor);

    protected native float[] getKeypointsNative(boolean onlyReturnCorrespondingKeypoints);

    protected native void drawNative(int viewWidth, int viewHeight, float[] frameToCanvas);

    protected static native void downsampleImageNative(
            int width, int height, int rowStride, byte[] input, int factor, byte[] output);
}
