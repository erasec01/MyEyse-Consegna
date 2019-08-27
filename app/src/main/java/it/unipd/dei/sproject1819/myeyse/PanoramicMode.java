package it.unipd.dei.sproject1819.myeyse;

import android.content.Context;
import android.graphics.RectF;
import android.support.v4.content.Loader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import it.unipd.dei.sproject1819.myeyse.tracking.TrackedRecognition;

public class PanoramicMode
{
    //The audio message of the lap result is produced at this speed
    public final static float VELOCITY_TO_SPEECH_RESULTS = 0.9f;

    //Context
    private Context mContext;

    /**
     * Defines whether the user is listening to the result of the rotation.
     * <p>
     * Basically, it helps us know this since it allows us to switch to object detection mode only
     * when the user has finished listening to the results.
     * <p>
     * In this way, if the user performs the double tap while the audio message corresponding
     * to the results is in progress, we can return to object detection mode
     */
    boolean mIsSpeechResult = false;

    //Estimated focal length of the camera
    private float mFocalLength;

    public enum DefineState
    {
        MODE_NORMAL,
        /**
         * Mode in which the user wants to return to the starting point
         * This mode is completely transparent to the end user
         */
        MODE_RETURN_STARTING_POINT,
        MODE_NO_ACTIVE
    }

    /**
     * Defines if the user must be stopped and, if so, gives an indication as to why the user was
     * stopped.
     */
    public enum StateStop
    {
        UNDEFINDED,
        STOP_FOR_SIMILARITY_IMAGE,
        STOP_FOR_EXCEEDED_RANGE_STOP

    }

    //Establishes the reason for the shutdown if the overview mode is successful
    StateStop reasonStopForSuccess;

    private DefineState mode;

    //On the i-th iteration, it contains all the objects identified by the iteration [0, i-1]
    private HashMap<Integer, TrackedRecognition> mMotionVector;

    /**
     * Reference to a class, designed entirely by us, to support the panoramic mode. In particular,
     * inside it the sensors are registered and the calculation of position to be attributed to a
     * generic frame executed.
     * <p>
     * It is important to note that the position returned is already subtracted from the starting
     * position of the lap.
     */
    private ManageRotation mManageRotation;

    //Statistical information on the number of frames analyzed during the tour in panoramic mode
    private int mNumberAnalyzedFrames;

    private float mCurrentPosition;

    /**
     * This object allows the comparison between frames: in particular it allows to establish
     * whether two frames can be considered similar or not
     */
    private ImageSimilarity imageSimilarity;

    /**
     * When the user approaches the initial position, I start comparing the histogram of the
     * initially stored frame with the histogram of the current frames
     */
    private boolean mIsUserNearStartPosition = false;

    /**
     * If the user wants to return to the starting point, store in this variable (the eventual) new
     * direction of orientation
     */
    ManageRotation.DirectOrientation newOrientation;

    //Constructor
    public PanoramicMode(final Context context, final int realFrameWidth, final int realFrameHeight,
                         final int orientation, final float focalLength)
    {
        mContext = context;
        mFocalLength = focalLength;
        mMotionVector = new HashMap<>();
        mManageRotation = new ManageRotation(context, focalLength);
        imageSimilarity = new ImageSimilarity(realFrameWidth, realFrameHeight, orientation);
        mode = DefineState.MODE_NO_ACTIVE;
    }

    /**
     * Determines if the user is near the start position.
     *
     * @return true user is near start position, false otherwise
     */
    public boolean isUserNearStartPosition()
    {
        return mIsUserNearStartPosition;
    }

    /**
     * Activate the overview mode. In particular, enable the sensors
     */
    public void active()
    {
        mode = DefineState.MODE_NORMAL;
        mManageRotation.active();
    }

    /**
     * Method getter relative of motion vector containing object before tracked and then lost
     * (since they are no longer present on the screen) during the tour
     *
     * @return
     */
    public HashMap<Integer, TrackedRecognition> getMotionVector()
    {
        return mMotionVector;
    }


    public boolean isInactive()
    {
        return mManageRotation.isInactive();
    }

    public boolean isActive()
    {
        return !isInactive();
    }

    /**
     * @return if we are in return mode
     */
    public boolean isReturnMode()
    {
        return (mode == DefineState.MODE_RETURN_STARTING_POINT);
    }

    /**
     * @return Defines whether the user has performed an incorrect maneuver such as a lap change
     * or has exceeded the maximum speed allowed several times
     */
    public ManageRotation.StateRotation isWrongMove()
    {
        ManageRotation.StateRotation s = null;

        //If we are in return mode, I only check the inversion of the turn, not the speed
        if (isReturnMode())
            s = mManageRotation.isUserMakeWrongMove(true);
        else
            s = mManageRotation.isUserMakeWrongMove(false);

        //The user has not performed incorrect maneuvers
        if (s == ManageRotation.StateRotation.FOR_NOW_CORRECT_ROTATION)
        {
            /**
             * In this case we have to create and memorize the histogram of
             * the current frame to then quantify the similarity with the
             * frames coming at the end of the round
             */
            if ((mIsUserNearStartPosition == false) &&
                    mManageRotation.UserInsideNearInitialPosition())
            {
                mIsUserNearStartPosition = true;
            }
        }
        return s;
    }

    public boolean isSensorReady()
    {
        return mManageRotation.isSensorReady();
    }

    /**
     * @param rgbBytes
     * @return
     */
    public boolean stopUser(int[] rgbBytes)
    {
        if (mode == DefineState.MODE_NORMAL)
            ++mNumberAnalyzedFrames;

        /**
         * In this case I have to create and memorize the histogram of
         * the current frame to then quantify the similarity with the
         * frames coming at the end of the round
         */
        if ((mNumberAnalyzedFrames == 1) && (mode == DefineState.MODE_NORMAL))
        {
            imageSimilarity.setFrameReference(rgbBytes);
            return false;
        }

        /**
         * We stop the user either because we found a frame similar to the reference frame, or
         * because the user has exceeded the range of stopping positions
         */
        if (mIsUserNearStartPosition == false)
            return false;
        else
        {
            //We store why we stopped the user.
            if (imageSimilarity.IsSimilarWhitReferenceFrame(rgbBytes))
            {
                reasonStopForSuccess = StateStop.STOP_FOR_SIMILARITY_IMAGE;
                return true;
            }
            else if (mManageRotation.stopUser())
            {
                reasonStopForSuccess = StateStop.STOP_FOR_EXCEEDED_RANGE_STOP;
                return true;
            }
            else
                return false;
        }
    }

    /**
     * In the event that the user wants to return to the starting point, it may be necessary to set
     * the orientation direction as the current one is incorrect.
     */
    public void setNewOrientations()
    {
        mManageRotation.setOrientation(newOrientation);
        newOrientation = ManageRotation.DirectOrientation.UNDEFINED_ROTATION;
    }

    /**
     * @return
     */
    public String goStartingPoint()
    {
        mode = DefineState.MODE_RETURN_STARTING_POINT;
        mIsUserNearStartPosition = false;
        InfoBack infoBack = mManageRotation.approximatelyDistanceInDegrees();
        newOrientation = infoBack.getOrientation();
        /**
         *  We can tell the user that, if the movement he has to perform is less than 10 degrees,
         *  he can be considered already in the initial position.
         */
        if (infoBack.getDegrees() <= 10)
            return null;
        return mContext.getString(R.string.turn) + infoBack.getDegrees()
                + mManageRotation.convertEnumDirectToString(newOrientation);
    }

    public float getCurrentPositionFrame()
    {
        return mCurrentPosition;
    }

    //Memorize the current position returned by the sensors
    public void storeCurrentPosition()
    {
        mCurrentPosition = mManageRotation.getCurrentPosition();
    }

    /**
     * @return true if the orientation of the lap has been set
     */
    public boolean isEstablishedOrientation()
    {
        return mManageRotation.isEstablishedOrientation();
    }

    /**
     * Method that produce, in the form of a string, the sequence of audio messages, one for each
     * recognized object, which will be produced by the speaker
     *
     * @return List of strings containing the messages to be communicated to the user
     */
    public List<String> getResultsTour()
    {
        int differentObject = 0;
        //List of string return by method
        List<String> results = new ArrayList<>();

        List<String> objectNearStartPosition = new ArrayList<>();

        HashMap<Integer, ArrayList<ObjectRecognized>> orderResult = new HashMap<>();

        if (reasonStopForSuccess == StateStop.STOP_FOR_SIMILARITY_IMAGE)
            results.add(mContext.getString(R.string.panoramic_mode_end_for_image_similarity));
        else
            results.add(mContext.getString(R.string.panoramic_mode_end_for_exceeding_the_stopping_range));

        //Depending on the number of objects identified, message is different
        if (mMotionVector.size() == 0)
        {
            results.add(mContext.getString(R.string.panoramic_mode_obj_not_found));
            close(true);
            return results;
        }

        /**
         * Each identified object is assigned a unique id that starts from 1. So the n-th object
         * tracked have id n.
         */
        for (int i = 1; i <= mMotionVector.size(); i++)
        {
            //This control should be always true..
            if (mMotionVector.containsKey(i))
            {
                float realPosition = objectPositionProcessing(i);

                if ((realPosition > 6 - mFocalLength / 2) || (realPosition < mFocalLength / 2))
                {
                    //Alleged duplicate object
                    if (objectNearStartPosition.contains(mMotionVector.get(i).getTitle()))
                        continue;
                    else
                        objectNearStartPosition.add(mMotionVector.get(i).getTitle());
                }

                int dir = mManageRotation.mapPositionToDirection(realPosition);

                //I get the list of all the objects that are in the dir direction
                ArrayList<ObjectRecognized> objectDir = orderResult.get(dir);

                /**
                 * Surely if the list is null it means that previously it is not
                 * objects are detected in that direction
                 */
                if (objectDir == null)
                {
                    objectDir = new ArrayList<>();
                    objectDir.add(new ObjectRecognized(mMotionVector.get(i).getTitle(),
                            realPosition));
                    ++differentObject;
                    orderResult.put(dir, objectDir);
                }
                else
                {
                    /**
                     * If different from null, I verify if inside the objects identified in
                     * that direction, is present by chance one with the same name as the current
                     * one
                     */
                    boolean found = false;

                    for (int j = 0; j < objectDir.size(); j++)
                    {
                        if (objectDir.get(j).name == mMotionVector.get(i).getTitle())
                        {
                            found = true;
                            objectDir.get(j).num++;
                            break;
                        }
                    }
                    if (found == false)
                    {
                        objectDir.add(new ObjectRecognized(mMotionVector.get(i).getTitle(),
                                realPosition));
                        differentObject++;
                    }
                }
            }
        }
        if (differentObject > 1)
            results.add(mContext.getString(R.string.panoramic_mode_found_more_obj) +
                    differentObject + " different object");
        else
            results.add(mContext.getString(R.string.panoramic_mode_found_1_obj));

        //I cycle for all the hours looking for objects
        for (int i = 1; i <= 12; i++)
        {
            //We check if at least one object is present in the i-th direction
            if (orderResult.containsKey(i))
            {
                //For each object identified at the i-th we now create a string
                List<ObjectRecognized> or = orderResult.get(i);
                for (int k = 0; k < or.size(); k++)
                {
                    results.add("I found " + or.get(k).num + " " + or.get(k).name + " " +
                            mManageRotation.mapDirectionToString(i));
                }
            }
        }
        close(true);
        return results;
    }

    public int getNumFrameAnalyzed()
    {
        return mNumberAnalyzedFrames;
    }

    /**
     * @return If the speaker is emitting the audio message containing, for each object identified
     * along the tour, its location
     */
    public boolean iSspeechResult()
    {
        return mIsSpeechResult;
    }

    /**
     * The speaker has finished issuing the audio message related to the objects identified along
     * the tour
     */
    public void setIsSpeechResultToFalse()
    {
        mIsSpeechResult = false;
    }

    /**
     * Report the state variables to the default value and deregister the sensors
     *
     * @param isSpeechResult
     */
    public void close(boolean isSpeechResult)
    {
        if (isSpeechResult)
            mIsSpeechResult = true;

        //Basically, deregister the sensor
        if (mManageRotation.isActive())
            mManageRotation.close();

        //Set state variable to default value
        mIsUserNearStartPosition = false;
        imageSimilarity.close();
        mNumberAnalyzedFrames = 0;
        mode = DefineState.MODE_NO_ACTIVE;
        mMotionVector.clear();
        reasonStopForSuccess = StateStop.UNDEFINDED;
    }

    public String getOrientation()
    {
        return mManageRotation.getCurrentOrientation();
    }

    /**
     * Struct used to order clockwise recognized objects and count objects that appear two or
     * more times at the same hour
     */
    class ObjectRecognized
    {
        int num;
        String name;
        float position;

        private ObjectRecognized(String name, float realPosition)
        {
            this.name = name;
            num = 1;
            position = realPosition;
        }
    }

    /**
     * Method that calculates, for each object, the position of each object taking into account the
     * actual stopping position, the position in which this object was recognized the first time and
     * the position, inside frame, occupied.
     */
    private float objectPositionProcessing(int i)
    {
        return realPosition(mMotionVector.get(i).getFirstPosition(),
                mMotionVector.get(i).getLastPosition());
    }

    /**
     * Calculate the position to assign to the object
     *
     * @param firstPosition position first frame in which object appears
     * @param lastPosition position last frame in which object appears
     * @return real position attributed to the object
     */
    private float realPosition(float firstPosition, float lastPosition)
    {
        if (Math.abs(lastPosition-firstPosition) < 1)
        {
            float pos = (lastPosition + firstPosition) / 2;
            if (pos > 6)
                pos -= 6;
            return pos;
        }
        return firstPosition;
    }
}
