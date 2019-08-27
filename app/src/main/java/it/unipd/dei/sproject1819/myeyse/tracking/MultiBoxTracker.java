package it.unipd.dei.sproject1819.myeyse.tracking;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.Log;
import android.util.Pair;

import java.util.LinkedList;
import java.util.List;

import it.unipd.dei.sproject1819.myeyse.CameraActivity;
import it.unipd.dei.sproject1819.myeyse.ImageUtils;
import it.unipd.dei.sproject1819.myeyse.InfoSpeech;
import it.unipd.dei.sproject1819.myeyse.PanoramicMode;
import it.unipd.dei.sproject1819.myeyse.Recognition;

public class MultiBoxTracker
{
    private static String TAG = "MultiBoxTracker";

    Context mContext;
    /**
     * Consider object to be lost if correlation falls below this threshold.
     * This value has not been changed.
     */
    private static final float MIN_CORRELATION = 0.3f;

    //Useful enumeration for the areas in which an object can be found on the screen
    public enum Direction
    {
        LEFT,
        RIGHT,
        CENTER,
        UNDEFINED
    }

    /**
     * Real class that manage all regard tracking. This class can be seen as a highest level of
     * abstraction of the tracking algorithm implemented in C++.
     * <p>
     * This class has not been changed by us. It was used simply as a "black-box"
     * Initialized in the onFrame method at the first iteration(see next variable)
     */
    public ObjectTracker objectTracker = null;

    //This variable is equal to true when objectTracker is initialized
    private boolean initialized = false;

    //Contains information regard object currently traced
    private List<TrackedRecognition> trackedObjects;

    //Current operative mode
    CameraActivity.Mode mMode;

    //Panoramic mode object
    PanoramicMode pm;

    //Information regard frame
    private int frameWidth;
    private int frameHeight;
    private int sensorOrientation;

    //Constructor for object detection mode
    public MultiBoxTracker(final Context context, CameraActivity.Mode mode)
    {
        this(context, mode, null);
    }

    //Constructor for panoramic mode
    public MultiBoxTracker(final Context context, CameraActivity.Mode mode, PanoramicMode pm)
    {
        TrackedRecognition.resetNumObjectTrack();
        mContext = context;
        this.mMode = mode;
        trackedObjects = new LinkedList<>();
        this.pm = pm;
    }

    /**
     *
     * @param results List of all objects recognized in the current frame by the network
     * @param frame current frame
     * @param timestamp current timestamp
     * @return Possible list containing all the audio messages to be sent to the user, if
     *          object detection mode
     */
    public synchronized List<InfoSpeech> trackResults(final List<Recognition> results,
                                                      final byte[] frame, final long timestamp)
    {
        return processResults(timestamp, results, frame);
    }

    /**
     * Called by the processImage method to each new frame that we want
     * analyzed. This method updates the tracking information based on the
     * current frame
     *
     * @param w frame width
     * @param h frame height
     * @param rowStride
     * @param sensorOrientation
     * @param frame
     * @param timestamp
     */
    public synchronized void onFrame(
            final int w,
            final int h,
            final int rowStride,
            final int sensorOrientation,
            final byte[] frame,
            final long timestamp)
    {
        if (objectTracker == null && !initialized)
        {
            ObjectTracker.clearInstance();
            initialized = true;

            Log.d(TAG, "Initializing ObjectTracker");
            objectTracker = ObjectTracker.getInstance(w, h, rowStride, true);

            frameWidth = w;
            frameHeight = h;
            this.sensorOrientation = sensorOrientation;

            if (objectTracker == null)
            {
                Log.e(TAG, "Object tracking support not found");
            }
        }

        if (objectTracker == null)
            return;

        /**
         * Very important method. It is used to update the tracking information of objects currently
         * tracked using the current frame. This is done, regardless of whether the frame is then
         * passed to the network.
         *
         * In fact, as anticipated, in the case in which the network is committed to making the
         * inference of a previous frame, this frame is discarded(but but at least the information
         * regarding the tracking has been updated)
         */
        objectTracker.nextFrame(frame, null, timestamp, null,
                true);


        /**
         * Clean up any objects not worth tracking any more.
         * This code is copy by demo.
         */
        final LinkedList<TrackedRecognition> copyList =
                new LinkedList<TrackedRecognition>(trackedObjects);
        for (final TrackedRecognition recognition : copyList)
        {
            final ObjectTracker.TrackedObject trackedObject = recognition.trackedObject;
            final float correlation = trackedObject.getCurrentCorrelation();
            if (correlation < MIN_CORRELATION)
            {
                trackedObject.stopTracking();
                trackedObjects.remove(recognition);
            }
        }
    }

    /**
     *
     * @param timestamp
     * @param results
     * @param originalFrame
     * @return
     */
    private List<InfoSpeech> processResults(final long timestamp, final List<Recognition> results,
                                            final byte[] originalFrame)
    {
        List<InfoSpeech> infoSpeeches = new LinkedList<>();
        final List<Pair<Float, Recognition>> rectsToTrack = new LinkedList<Pair<Float, Recognition>>();

        //For each object recognized in the current frame
        for (final Recognition result : results)
            rectsToTrack.add(new Pair<>(result.getConfidence(), result));

        if (rectsToTrack.isEmpty())
            return null;

        //For the first identified object
        if (objectTracker == null)
        {
            trackedObjects.clear();

            for (final Pair<Float, Recognition> potential : rectsToTrack)
            {
                final TrackedRecognition trackedRecognition =
                        new TrackedRecognition(null, potential.first,
                                potential.second.getTitle(),
                                new RectF(potential.second.getLocation()));

                trackedObjects.add(trackedRecognition);
            }
            return null;
        }

        for (final Pair<Float, Recognition> potential : rectsToTrack)
        {
            InfoSpeech infoSpeech = handleDetection(originalFrame, timestamp, potential);
            if (infoSpeech != null)
                infoSpeeches.add(infoSpeech);
        }
        return infoSpeeches;
    }

    /**
     * One of the most important methods of the whole app.
     * This method is invoked for every single object, obj, recognized by ssd in the last frame.
     * <p>
     * Basically, a comparison is made between the figure, intended as a rectangle, of (obj) and
     * all the figure relative to the object currently tracked. If there is a overlap sufficiently
     * high with one of them, example obj2, this means that obj isn't a new object but is obj2 that
     * moved inside the screen.
     *
     * @param frameCopy current frame
     * @param timestamp timestamp
     * @param potential contains all the information of an object recognized by the network in the
     *                  current frame
     * @return possibly the audio information to be sent to the user if we are in object detection
     * mode, otherwise null
     */
    private InfoSpeech handleDetection(final byte[] frameCopy, final long timestamp,
                                       final Pair<Float, Recognition> potential)
    {
        /**
         * Maximum percentage of a box that can be overlapped by another box at detection time.
         * Otherwise the lower scored box (new or old) will be removed.
         * This value has not been changed.
         */
        final float MAX_OVERLAP = 0.2f;

        /**
         * Allow replacement of the tracked box with new results if correlation has dropped below
         * this level. This value has not been changed.
         */
        final float MARGINAL_CORRELATION = 0.75f;

        /**
         * First we create an object containing all the information regarding the tracking of
         * the current object analyzed (recognized in the current frame)
         */
        final ObjectTracker.TrackedObject potentialObject =
                objectTracker.trackObject(potential.second.getLocation(), timestamp, frameCopy);

        /**
         * Basically, a sort of comparison is made between the current frame and the frame analyzed
         * before. However, the granularity is on the single object.
         *
         * For example, if in one frame you have a laptop and a bottle, and in the next frame the
         * bottle moves a lot while you don't move the laptop, the correlation regarding the laptop
         * calculated from the two frames continues to be high ( basically the same), while the
         * correlation of the bottle decreases drastically.
         *
         * Attention: the correlation is not calculated only based on the position (always
         * understood as a rectangle surrounding the object).
         *
         * In fact, supposing that in a frame you have a bottle: if you don't move it, and of course
         * the smartphone, but you color its label, the correlation decreases drastically between
         * the frame with the white label and the one with the colored label.
         */
        final float potentialCorrelation = potentialObject.getCurrentCorrelation();


        if (potentialCorrelation < MARGINAL_CORRELATION)
        {
            //Correlation too low to begin tracking;
            potentialObject.stopTracking();
            return null;
        }

        final List<TrackedRecognition> removeList = new LinkedList<TrackedRecognition>();

        /**
         * The current identified object may overlap more than
         * MAX_OVERLAP with multiple objects tracked.
         *
         * We obviously want to take the one with which the overlap is greater.
         * In this variable we store the maximum overlap currently identified.
         */
        float maxIntersect = 0.0f;

        /**
         * If left at null it means that the object recognized now has not been
         * previously identified.
         *
         * Otherwise it inherits the information of the corresponding object
         */
        TrackedRecognition recogToReplace = null;

        /**
         * Look for intersections that will be overridden by this object or an intersection that
         * would prevent this one from being placed.
         *
         * Of course, to enter this cycle it is necessary that there is at least one object tracked.
         */
        for (final TrackedRecognition trackedRecognition : trackedObjects)
        {
            //This rectangle is relative to the i-th object currently tracked
            final RectF a = trackedRecognition.trackedObject.getTrackedPositionInPreviewFrame();

            //This rectangle is relative to the current object obj identified in the current frame
            final RectF b = potentialObject.getTrackedPositionInPreviewFrame();

            //Create an empty rectangle
            final RectF intersection = new RectF();

            /**
             * Check if the two rectangles intersect. If intersect, rectangle intersection
             * (previously create) is set to that intersection
             */
            final boolean intersects = intersection.setIntersect(a, b);

            //Intersect area calculation (intersectOverUnion).
            final float intersectArea = intersection.width() * intersection.height();
            final float totalArea = a.width() * a.height() + b.width() * b.height() - intersectArea;
            final float intersectOverUnion = intersectArea / totalArea;

            /**
             * If there is an intersection with this currently tracked box above the maximum overlap
             * percentage allowed, either the new recognition needs to be dismissed or the old
             * recognition needs to be removed and possibly replaced with the new one.
             *
             * This is extremely unintuitive.
             */
            if (intersects && intersectOverUnion > MAX_OVERLAP)
            {
                if (potential.first < trackedRecognition.getDetectionConfidence()
                        && trackedRecognition.trackedObject.getCurrentCorrelation() > MARGINAL_CORRELATION)
                {
                    /**
                     *  If track for the existing object is still going strong and the detection
                     *  core was good, reject this new object.
                     */
                    potentialObject.stopTracking();

                    RectF trackedPos =
                            trackedRecognition.trackedObject.getTrackedPositionInPreviewFrame();
                    adjustRectangle().mapRect(trackedPos);

                    Direction checkPos = checkPosition(trackedPos.centerX());

                    if (checkPos != trackedRecognition.getLastPosSpeech())
                    {
                        trackedRecognition.setLastPosSpeech(checkPos);
                        return new InfoSpeech(trackedRecognition.getTitle(), checkPos);
                    }
                    return null;
                }
                else
                {
                    removeList.add(trackedRecognition);

                    // Let the previously tracked object with max intersection amount donate its color to
                    // the new object.
                    if (intersectOverUnion > maxIntersect)
                    {
                        maxIntersect = intersectOverUnion;
                        recogToReplace = trackedRecognition;
                    }
                }
            }
        }

        // Remove everything that got intersected.
        for (final TrackedRecognition trackedRecognition : removeList)
        {
            //"Removing tracked object
            trackedRecognition.trackedObject.stopTracking();
            trackedObjects.remove(trackedRecognition);
        }

        final TrackedRecognition trackedRecognition;
        if (recogToReplace == null)
        {
            //The object is new and has never been traced before
            trackedRecognition = new TrackedRecognition(potentialObject, potential.first,
                    potential.second.getTitle(), null);
        }
        else
            trackedRecognition = new TrackedRecognition(recogToReplace.getId(), potentialObject,
                    potential.first, potential.second.getTitle(), null);


        if (mMode == CameraActivity.Mode.MODE_OBJECT_DETECTION)
        {
            RectF trackedPos = trackedRecognition.trackedObject.getTrackedPositionInPreviewFrame();
            adjustRectangle().mapRect(trackedPos);
            Direction currentSector = checkPosition(trackedPos.centerX());

            //It means that it is a tracked object
            if (recogToReplace != null)
            {
                Direction x = recogToReplace.getLastPosSpeech();

                /**
                 * Check if I need to send an audio message to the user to notify them that the
                 * object has changed position on the screen or not
                 */
                if (currentSector != recogToReplace.getLastPosSpeech())
                {
                    trackedRecognition.setLastPosSpeech(currentSector);
                    trackedObjects.add(trackedRecognition);
                    return new InfoSpeech(trackedRecognition.getTitle(), currentSector);
                }
                else
                {
                    trackedRecognition.setLastPosSpeech(x);
                    trackedObjects.add(trackedRecognition);
                    //I don't have to go back to audio mess ..
                    return null;
                }
            }
            else
            {
                //If the identified object is new
                trackedRecognition.setLastPosSpeech(currentSector);
                trackedObjects.add(trackedRecognition);
                /**
                 * We need to send an audio voice message that communicates that
                 * the traced object has changed quadrant
                 */
                return new InfoSpeech(trackedRecognition.getTitle(), currentSector);
            }
        }
        else if (mMode == CameraActivity.Mode.MODE_PANORAMIC)
        {
            RectF trackedPos =
                    trackedRecognition.trackedObject.getTrackedPositionInPreviewFrame();
            adjustRectangle().mapRect(trackedPos);

            if (pm.getMotionVector().containsKey(trackedRecognition.getId()) == false)
            {
                /**
                 * For the first object identified, we memorize the first position
                 */
                trackedRecognition.setFirstPosition(pm.getCurrentPositionFrame());
                pm.getMotionVector().put(trackedRecognition.getId(), trackedRecognition);
            }
            else
            {
                /**
                 * For each object already identified, I store the current position, which could be
                 * the last
                 */
                pm.getMotionVector().get(trackedRecognition.getId()).
                        setLastPosition(pm.getCurrentPositionFrame());
            }
            trackedObjects.add(trackedRecognition);
        }
        return null;
    }

    /**
     * The rectangle return by ssd is respect to a frame 320x320 and rotate respect the input
     * frame. We need a matrix to get a frame rotated to the opposite and of size equal to the
     * input one. Honestly it is not strictly necessary to do this, but for reasons of clarity it
     * is done.
     * @return Matrix to get what has been said
     */
    public Matrix adjustRectangle()
    {
        final boolean rotated = sensorOrientation % 180 == 90;
        return ImageUtils.getTransformationMatrix(frameWidth, frameHeight,
                (rotated ? frameHeight : frameWidth),
                (rotated ? frameWidth : frameHeight),
                sensorOrientation,
                false);
    }

    /**
     * Determines the position of the object in object detection mode
     * @param centerX Center of gravity of the rectangle that identifies the object within the frame
     * @return if the object is LEFT,CENTER of RIGHT
     */
    private Direction checkPosition(float centerX)
    {
        if (centerX >= 0 && centerX <= frameHeight / 3)
            return Direction.LEFT;
        else if (centerX > frameHeight / 3 && centerX <= frameHeight * 2 / 3)
            return Direction.CENTER;
        else
            return Direction.RIGHT;
    }
}
