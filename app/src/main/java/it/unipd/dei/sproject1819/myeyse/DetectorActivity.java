package it.unipd.dei.sproject1819.myeyse;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.ImageReader;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import it.unipd.dei.sproject1819.myeyse.tracking.MultiBoxTracker;

public class DetectorActivity extends CameraActivity implements ImageReader.OnImageAvailableListener
{
    private static String TAG = "DetectorActivity";
    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final boolean TF_OD_API_IS_QUANTIZED = true;

    //Paths in which the network and the list of identified objects are located
    private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt";

    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE_OBJECT_RECOGNIZED = 0.7f;

    /**
     * Reference on TensorFlowLite framework which we start the inference on the cropped bitmap of
     * the current frame
     */
    private TensorFlowObjectDetectionAPIModel detector = null;

    //private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    /**
     * Bitmap representing the current frame after resizing it to
     * TF_OD_API_INPUT_SIZE x TF_OD_API_INPUT_SIZE
     */
    private Bitmap croppedBitmap = null;

    private byte[] luminanceCopy;

    //Defines if the network is inference of a frame
    private boolean bgThreadRun = false;

    private long timestamp = 0;

    //Matrices used to perform the resize
    private Matrix frameToCropTransform;
    /**
     * Matrix used to perform the resize. This is used to resize image
     */
    private Matrix cropToFrameTransform;
    private Mode newMode = Mode.UNDEFINED;
    //Object that manages the tracking of all recognized objects.
    private MultiBoxTracker tracker;

    @Override
    protected void onPreviewSizeChosen(final Size size, final int rotation, final float focalLength)
    {
        try
        {
            detector = TensorFlowObjectDetectionAPIModel.create(
                    getAssets(),
                    TF_OD_API_MODEL_FILE,
                    TF_OD_API_LABELS_FILE,
                    TF_OD_API_INPUT_SIZE,
                    TF_OD_API_IS_QUANTIZED);

        }
        catch (final IOException e)
        {
            Log.e(TAG, "TF-Lite error");
        }

        sensorOrientation = rotation - getScreenOrientation();

        /**
         * We create an empty bitmap and then, using the setPixels method, add a color to each pixel.
         */
        rgbFrameBitmap = Bitmap.createBitmap(mRealFrameWidth, mRealFrameHeight,
                Bitmap.Config.ARGB_8888);

        //We create an empty bitmap and then store the clipped frame
        croppedBitmap = Bitmap.createBitmap(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE,
                Bitmap.Config.ARGB_8888);

        //We get the matrix needed to do the scaling from the frame to a 300x300 resolution
        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        mRealFrameWidth, mRealFrameHeight,
                        TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE,
                        sensorOrientation, false);

        cropToFrameTransform = new Matrix();

        /**
         *  We get the matrix needed to do the scaling from the frame to a
         *  TF_OD_API_INPUT_SIZE x TF_OD_API_INPUT_SIZE resolution
         */
        frameToCropTransform.invert(cropToFrameTransform);

        //Object for the manage of panoramic mode
        pm = new PanoramicMode(this, mRealFrameWidth, mRealFrameHeight, sensorOrientation,
                focalLength);
    }

    //Method that manages the return to the initial position
    @Override
    protected void processReturnTour()
    {
        final Context context = this;

        /**
         * If the frame in bg is calculating the histogram of an older frame, it does not process
         * it.
         */
        if (bgThreadRun == true)
            readyForNextImage();

        /**
         * New mode can only Object detection
         */
        if (newMode != Mode.UNDEFINED)
        {
            mode = Mode.MODE_OBJECT_DETECTION;
            newMode = Mode.UNDEFINED;
            isModeChange = false;
            if (pm.isActive())
                pm.close(false);
            t1.speechMessage(getString(R.string.object_detection_mode_activated));
            tracker = new MultiBoxTracker(context, mode);
            readyForNextImage();
            return;
        }

        bgThreadRun = true;

        /**
         * I am interested in making the comparison only if the user is around the starting point
         */
        runInBackground(new Runnable()
        {
            @Override
            public void run()
            {
                getRgbBytes();
                if (pm.stopUser(rgbBytes))
                {
                    pm.close(false);
                    t1.speechMessage(getString(R.string.panoramic_mode_arrived_start_position));
                    t1.speechMessage(getString(R.string.object_detection_mode_activated));
                    mode = Mode.MODE_OBJECT_DETECTION;
                    tracker = new MultiBoxTracker(context, mode);
                }
                bgThreadRun = false;
            }
        });
        //UI thread prepare the next frame
        readyForNextImage();
    }

    /**
     * Method within which inference occurs.
     */
    @Override
    protected void processImage()
    {
        final Context cont = this;
        ++timestamp;
        final long currTimestamp = timestamp;
        final byte[] originalLuminance = getLuminance();

        /**
         * If the mode has changed and the bg thread has finished, I change the mode safely and
         * without the UI thread having to wait
         */
        if ((newMode != Mode.UNDEFINED) && (bgThreadRun == false))
        {
            if (newMode == Mode.MODE_OBJECT_DETECTION)
            {
                if (pm.isReturnMode() && bgThreadRun == true)
                {
                    //I can't change mode
                    readyForNextImage();
                    return;
                }
                //Change mode
                mode = Mode.MODE_OBJECT_DETECTION;
                if (pm.isActive())
                    pm.close(false);

                t1.speechMessage(getString(R.string.object_detection_mode_activated));
                tracker = new MultiBoxTracker(cont, mode);
            }
            else if (newMode == Mode.MODE_PANORAMIC)
            {
                mode = Mode.MODE_PANORAMIC;
                t1.speechMessage(getString(R.string.panoramic_mode_activated));
                tracker = new MultiBoxTracker(cont, mode, pm);
            }
            newMode = Mode.UNDEFINED;
            isModeChange = false;
            readyForNextImage();
            return;
        }
        else if (newMode != Mode.UNDEFINED)
        {
            //This means that the bg thread has not finished
            readyForNextImage();
            return;
        }

        /**
         * We first update the tracking information regard tracking object(this used only current
         * frame. This control is always true as if in return mode, you never pass this way
         */
        if (!pm.isReturnMode())
        {
            tracker.onFrame(
                    mRealFrameWidth,
                    mRealFrameHeight,
                    getLuminanceStride(),
                    sensorOrientation,
                    originalLuminance,
                    timestamp);
        }

        if (bgThreadRun)
        {
            readyForNextImage();
            return;
        }

        //We use a so-called "worker thread" to perform frame analysis
        runInBackground(new Runnable()
        {
            @Override
            public void run()
            {
                bgThreadRun = true;

                int[] rgb = getRgbBytes();

                if (mode == Mode.MODE_PANORAMIC)
                {
                    if (pm.getNumFrameAnalyzed() == 0)
                        t1.speechMessage(pm.getOrientation());
                    /**
                     * If the mode is panoramic, we may have to use the rgb vector just
                     * calculate
                     */

                    /**
                     * In the case in which the user has arrived in a neighborhood of
                     * the initial position, we calculate the frame histogram.
                     * If current frame is considered similar at the
                     * starting frame, we can conclude that the panoramic mode is
                     * complete
                     *
                     * It is possible to note that, with this code organization,
                     * whatever the strategy adopted to estimate the similarity between
                     * frames, the code here is independent to it
                     *
                     * However, if the neighborhood of the initial position is exceeded
                     * without the recognition of a frame similar to the stop one,
                     * however we block the user.
                     *
                     */
                    if (pm.stopUser(rgbBytes))
                    {
                        /**
                         * Even if we stopped the user, we cannot immediately switch to object
                         * detection mode because the user is still listening to the results of
                         * the rotation.
                         *
                         * For this reason he expects, by pressing double tap, to go into object
                         * detection mode.
                         *
                         * For this reason, we switch to object detection mode only when the
                         * reproduction of the results ends
                         *
                         */
                        mode = Mode.UNDEFINED;
                        endModePanoramic();
                        bgThreadRun = false;
                        readyForNextImage();
                        return;
                    }

                }

                rgbFrameBitmap.setPixels(rgb, 0, mRealFrameWidth, 0, 0, mRealFrameWidth,
                        mRealFrameHeight);

                if (luminanceCopy == null)
                {
                    luminanceCopy = new byte[originalLuminance.length];
                }
                System.arraycopy(originalLuminance, 0, luminanceCopy, 0,
                        originalLuminance.length);

                readyForNextImage();

                /**
                 * An empty bitmap is passed to the canvas. We create the cropped bitmap using
                 * drawBitmpa
                 */
                Canvas canvas = new Canvas(croppedBitmap);
                canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

                //Return list of message that may send to user
                List<InfoSpeech> s = procImage(currTimestamp);

                //We reproduce the audio messages
                if ((s != null) && (s.size() > 0) && mode == Mode.MODE_OBJECT_DETECTION)
                {
                    for (int i = 0; i < s.size(); i++)
                        t1.speechMessage(s.get(i).getmMessToSpeech());
                }

                //Processing of the current frame is finished, sdd can inference a new frame
                bgThreadRun = false;
            }

        });
    }

    /**
     * Method in which inference is made
     *
     * @param currTimestamp Current time stamp
     * @return List of audio messages
     */
    private List<InfoSpeech> procImage(long currTimestamp)
    {
        /**
         * SSD model makes the inference on the current clipped frame and returns a list
         * of recognized objects
         */
        final List<Recognition> results = detector.recognizeImage(croppedBitmap);
        /**
         * Store only object with confidence a >= MINIMUM_CONFIDENCE_TF_OD_API
         * and localized
         */
        final List<Recognition> mappedRecognitions = new LinkedList<Recognition>();

        for (final Recognition result : results)
        {
            /**
             * Get reference of the four coordinate of the rectangle that surrounds
             * the (current) object recognized
             */
            final RectF location = result.getLocation();

            /**
             * Check if location is defined and if have confidence greater than
             * the minimum threshold
             */
            if (location != null && result.getConfidence() >= MINIMUM_CONFIDENCE_OBJECT_RECOGNIZED)
            {
                /**
                 * Position coordinates are relative to the clipped frame, so we must
                 * change them for the original frame
                 */
                cropToFrameTransform.mapRect(location);

                //Set coordinate modify
                result.setLocation(location);

                //Add object recognize to list
                mappedRecognitions.add(result);
            }
        }

        /**
         * Pass all the objects that we think are likely to be present in the photo, current frame
         * and the timestamp
         */
        List<InfoSpeech> s = tracker.trackResults(mappedRecognitions, luminanceCopy, currTimestamp);
        return s;
    }

    /**
     * This method provides, in the form of audio messages, information on the position of objects
     * recognized by the app when the user return to the start position of tour
     */
    @Override
    protected void endModePanoramic()
    {
        String res = "";
        t1.setSpeechRate(PanoramicMode.VELOCITY_TO_SPEECH_RESULTS);

        List<String> result = pm.getResultsTour();

        for (int i = 0; i < result.size(); i++)
            res += result.get(i) + '\n';

        t1.speechMessage(res, "endModPreview");
        t1.resetSpeechRate();
    }

    @Override
    protected void initializationObjectDetectionMode()
    {
        Context cont = this;
        t1.speechMessage("Object detection mode activated!");
        tracker = new MultiBoxTracker(cont, mode);

    }

    @Override
    protected void setNewMode(Mode newMode)
    {
        this.newMode = newMode;
        isModeChange = true;
    }
}
