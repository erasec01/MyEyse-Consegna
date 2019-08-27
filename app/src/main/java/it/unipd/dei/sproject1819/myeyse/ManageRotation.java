package it.unipd.dei.sproject1819.myeyse;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.nfc.Tag;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ManageRotation
{
    private static String TAG = "ManageRotation";

    private enum AlgorithmForPosition
    {
        ANDROID,
        LOW_PASS,
        FUSED
    }

    //Default algorithm
    private final static AlgorithmForPosition ALGORITHM_FOR_POSITION = AlgorithmForPosition.FUSED;

    private static Context mContext;

    /**
     * Defines the state in which the user reverses the orientation of the tour with respect
     * to that initially chosen
     */
    private boolean mIsRotationChange;

    /**
     * Defines the maximum angular speed allowed to the user to perform the rotation.
     */
    private final static float MAX_ANGULAR_SPEED = 0.5f;

    /**
     * The user can approximately perform  oscillations around the point where he has activated the
     * panoramic mode to the right and left of 12°
     */
    private final static float RANGE_INTERVAL_START = 0.2f;

    /**
     * Defines the distance, in radians, from when we start to compare histograms
     */
    private float rangeIntervalStop;

    /**
     * Since the position value is between [0,6[, the user has made half a turn
     * when that value is equal to 3.
     */
    private final static int POS_HALF_TOUR = 3;

    //Minimum Interval in which two alerts are sent to the user
    private final static int MIN_TIME_INTERVAL_WARNING_USER = 3000;

    //Maximum number of warnings that can be sent to the user. After that he is stopped.
    private final static int NUM_MAX_WARNING_SEND_TO_USER = 2;

    /**
     * The choice in which direction to move is chosen at runtime, so in this value we store
     */
    private DirectOrientation mVerseRotation;

    /**
     * This reference basically contains three value: currentPosition(between [0,6[), the position
     * in which the tour start anc the position in which
     */
    private Positions mPositions;

    /**
     * Define possible state of the orientation
     */
    public enum DirectOrientation
    {
        /**
         * Defines the state in which it isn't yet defined if the user wishes to make the turn
         * clockwise or counterclockwise
         */
        UNDEFINED_ROTATION,

        /**
         * Defines the state in which the user makes a clockwise tour
         */
        CLOCKWISE_ROTATION,

        /**
         * Defines the state in which the user makes a counter clockwise tour
         */
        COUNTERCLOCKWISE_ROTATION
    }

    /**
     * Enumeration that defines the possible states during the rotation of the user
     */
    public enum StateRotation
    {
        /**
         * Defines the state in which
         */
        MAXIMUM_ANGULAR_SPEED_EXCEEDED,

        /**
         * Defines the state in which the user has exceeded the maximum speed allowed for more than
         * NUM_MAX_WARNING_SEND_TO_USER
         */
        INCORRECT_ROTATION,

        /**
         * The user can exceed a maximum of twice the maximum allowed speed
         */
        SEND_WARNING_TO_USER,

        /**
         * Defines the state in which the user
         */
        FOR_NOW_CORRECT_ROTATION
    }

    /**
     * Reference to the class that is used to register / de-register the sensors and given position
     * value
     */
    ManageSensors manageSensor;

    /**
     * Once the direction of the turn is established, there is the risk that the user is in the
     * stopping range for which he is immediately blocked. We therefore conditioned the arrest to
     * the fact that the user has traveled half a tour
     */
    private boolean half_tour_complete;

    //Defines whether the user is around the initial position
    private boolean userWithinInitialPositionRange;

    /**
     * Defines the state in which the user has exceeded the maximum angular speed to make the turn
     */
    private boolean mMaximumAngularSpeedExceeded;

    /**
     * Number of notices already sent to the user concerning the fact that it has exceeded the
     * maximum rotation speed
     */
    private int mNumWarningSendToUser;

    //Time point in which the last warning was sent to the user
    private float mInstantLastWarning;

    //In practice not used
    private static float POS_NOT_AVAILABLE = -10;

    //As we shall see, the position value can take values between [0, 6 [
    private static float MAX_POSITION = 6;

    //Constructor
    public ManageRotation(Context context, float focalLenth)
    {
        mContext = context;

        rangeIntervalStop = focalLenth/2;
        //Set the default value to the variable previously described
        mIsRotationChange = false;
        mVerseRotation = DirectOrientation.UNDEFINED_ROTATION;
        mNumWarningSendToUser = 0;
        mInstantLastWarning = 0;
        half_tour_complete = false;
        manageSensor = null;
        userWithinInitialPositionRange = false;
        mMaximumAngularSpeedExceeded = false;
    }

    /**
     * @param direction in number
     * @return direction in string
     */
    public String mapDirectionToString(int direction)
    {
        if (direction == 1)
            return mContext.getString(R.string.hour_1);
        if (direction == 2)
            return mContext.getString(R.string.hour_2);
        if (direction == 3)
            return mContext.getString(R.string.hour_3);
        if (direction == 4)
            return mContext.getString(R.string.hour_4);
        if (direction == 5)
            return mContext.getString(R.string.hour_5);
        if (direction == 6)
            return mContext.getString(R.string.hour_6);
        if (direction == 7)
            return mContext.getString(R.string.hour_7);
        if (direction == 8)
            return mContext.getString(R.string.hour_8);
        if (direction == 9)
            return mContext.getString(R.string.hour_9);
        if (direction == 10)
            return mContext.getString(R.string.hour_10);
        if (direction == 11)
            return mContext.getString(R.string.hour_11);
        else
            return mContext.getString(R.string.hour_12);
    }

    /**
     * At the end of the tour, the position attributed to each identified object must be converted
     * to a string that makes up part of the audio message(together of course the name of the
     * identified object)
     *
     * @param position attributed to a tracked object
     * @return part of the string that compose message audio
     */
    public int mapPositionToDirection(float position)
    {
        double firstExtreme = 0.25;
        double secondExtreme = 0.75;
        for (int i = 1; i <= 11; i++)
        {
            if ((position > firstExtreme) && (position <= secondExtreme))
                return i;
            else
            {
                firstExtreme += 0.5;
                secondExtreme += 0.5;
            }
        }
        return 12;
    }

    /**
     * @param position
     * @param inc
     * @return
     */
    public static float increment(float position, float inc)
    {
        float temp = position + inc;

        if (temp >= MAX_POSITION)
            return temp - MAX_POSITION;

        else if (temp < 0)
            return temp + MAX_POSITION;

        else
            return temp;
    }

    /**
     * @return if the direction of orientation has been established
     */
    public boolean isEstablishedOrientation()
    {
        return (mVerseRotation != DirectOrientation.UNDEFINED_ROTATION);
    }

    /**
     * It defines whether the sensors (accelerometers, magneometer, gyroscope) are registered
     */
    public boolean isActive()
    {
        return !isInactive();
    }

    /**
     * It defines whether the sensors (accelerometers, magneometer, gyroscope) are registered
     *
     * @return
     */
    public boolean isInactive()
    {
        return (manageSensor == null);
    }

    /**
     * Activating the sensors doesn't mean having the position value immediately.
     *
     * @return true
     */
    public boolean isSensorReady()
    {
        return !(mPositions == null);
    }

    /**
     * We check if the user is near the initial position
     *
     * @return True if user is near the start tour position, false otherwise
     */
    public boolean UserInsideNearInitialPosition()
    {
        /**
         *We remember that this value is always between [0.6[and also takes into account the
         * initial position of the tour
         */

        float current_position = mPositions.getCurrentPosition();

        if ((userWithinInitialPositionRange == false) && (half_tour_complete == true))
        {
            if ((mVerseRotation == DirectOrientation.CLOCKWISE_ROTATION) &&
                    (current_position >= MAX_POSITION - rangeIntervalStop))
            {
                userWithinInitialPositionRange = true;
                return true;

            }
            else if ((mVerseRotation == DirectOrientation.COUNTERCLOCKWISE_ROTATION) &&
                    (current_position <= rangeIntervalStop))
            {
                //The user has reached the initial position
                userWithinInitialPositionRange = true;
                return true;
            }
        }
        return false;
    }

    /**
     * @return
     */
    public boolean stopUser()
    {
        float current_position = mPositions.getCurrentPosition();

        if (userWithinInitialPositionRange)
        {
            if (mVerseRotation == DirectOrientation.CLOCKWISE_ROTATION)
            {
                //
                if ((current_position > 0.5) && (current_position < 1))
                    return true;
                else
                    return false;
            }
            else
            {
                if ((current_position > 4) && (current_position < 5.5))
                    return true;
                else
                    return false;
            }
        }
        return false;
    }

    /**
     * We check if the user is making a wrong move
     *
     * @return Value representing the status of the rotation
     */
    public StateRotation isUserMakeWrongMove(boolean isReturn)
    {
        if ((mIsRotationChange == true) &&
                (mVerseRotation != DirectOrientation.UNDEFINED_ROTATION))
            return StateRotation.INCORRECT_ROTATION;

        if (isReturn)
            return StateRotation.FOR_NOW_CORRECT_ROTATION;

        else if ((mMaximumAngularSpeedExceeded == true) &&
                (mVerseRotation != DirectOrientation.UNDEFINED_ROTATION))
        {
            mMaximumAngularSpeedExceeded = false;
            float currTime = SystemClock.uptimeMillis();
            /**
             * If from the last warning has passed less than MIN_TIME_INTERVAL_WARNING_USER,
             * no action is taken
             */
            if (currTime - mInstantLastWarning < MIN_TIME_INTERVAL_WARNING_USER)
                return StateRotation.FOR_NOW_CORRECT_ROTATION;

            /**
             * If I have not exceeded the maximum number of predetermined warnings, we inform the
             * user to slow down
             */
            else if (mNumWarningSendToUser < NUM_MAX_WARNING_SEND_TO_USER)
            {
                mNumWarningSendToUser++;
                mInstantLastWarning = currTime;
                return StateRotation.SEND_WARNING_TO_USER;
            }
            else
            {
                //We must block the user
                return StateRotation.MAXIMUM_ANGULAR_SPEED_EXCEEDED;
            }
        }
        else
            return StateRotation.FOR_NOW_CORRECT_ROTATION;
    }

    /**
     * Getter method for private variable azimuthValue
     *
     * @return Current azimuth value
     */
    public float getCurrentPosition()
    {
        if (mPositions != null)
            return mPositions.getCurrentPosition();
        else
            return POS_NOT_AVAILABLE;
    }

    /**
     * Create an instance of ManageSensors and initialize the sensors
     */
    public void active()
    {
        mVerseRotation = DirectOrientation.UNDEFINED_ROTATION;
        mIsRotationChange = false;
        manageSensor = new ManageSensors(mContext);
        manageSensor.registerSensors();
    }

    /**
     * Method used to unregister sensor(accelerometers, magnetometer, gyroscope) and set state
     * variables to default value.
     * <p>
     * The order of actions cannot be reversed.
     */
    public void close()
    {
        if (manageSensor != null)
            manageSensor.unRegisterSensors();

        manageSensor = null;
        mPositions = null;

        //Set state variable to default state
        mVerseRotation = DirectOrientation.UNDEFINED_ROTATION;
        mIsRotationChange = false;
        mMaximumAngularSpeedExceeded = false;
        userWithinInitialPositionRange = false;
        half_tour_complete = false;
        mNumWarningSendToUser = 0;
        mInstantLastWarning = 0;
    }

    /**
     * If the user wishes to return to the starting point, an estimate of the degrees he must take
     * is provided
     */
    public InfoBack approximatelyDistanceInDegrees()
    {
        return calculateDegreeAndOrientation(mPositions.mCurrentPosition);
    }

    /**
     * To understand this method it is necessary to remember that the initial position of the tour
     * is always 0
     *
     * @param pos
     * @return
     */
    private InfoBack calculateDegreeAndOrientation(float pos)
    {
        float degreeCurrentPos = convertPositionToDegree(pos);

        if (degreeCurrentPos > 180)
        {
            int degreeInt = ((int) (360 - degreeCurrentPos));
            int offset = (degreeInt % 10);
            return new InfoBack(degreeInt - offset, DirectOrientation.CLOCKWISE_ROTATION);
        }
        else
        {
            int degreeInt = ((int) (degreeCurrentPos));
            int offset = (degreeInt % 10);
            return new InfoBack(degreeInt - offset,
                    DirectOrientation.COUNTERCLOCKWISE_ROTATION);
        }
    }

    ////We used the proportion PI:180 = pos:x
    private float convertPositionToDegree(float pos)
    {
        return ((pos * 180) / (float) Math.PI);
    }

    /**
     * Convert to string enum direction
     *
     * @param directOrientation enum direction
     * @return string direction
     */
    public String convertEnumDirectToString(DirectOrientation directOrientation)
    {
        switch (directOrientation)
        {
            case COUNTERCLOCKWISE_ROTATION:
                return "counterclockwise";
            case UNDEFINED_ROTATION:
                return "undefined";
            case CLOCKWISE_ROTATION:
                return "clockwise";
        }
        return null;
    }

    /**
     * @param orientation
     */
    public void setOrientation(DirectOrientation orientation)
    {
        half_tour_complete = true;
        mVerseRotation = orientation;
        mIsRotationChange = false;
        mNumWarningSendToUser = 0;
        mInstantLastWarning = 0;
        mMaximumAngularSpeedExceeded = false;
    }

    /**
     * In this method we check if it's possible to establish in which direction the user has decided
     * to take the tour or if it's consistent with the choice previously made
     *
     * @param gyValue
     */
    private void updateInfoOrientation(float[] gyValue)
    {
        /**
         * If the sensors have just been registered, I may not immediately have the position
         * v available
         */
        if (mPositions == null)
            return;

        /**
         * Check to see if the user has made a wrong move
         * case the variable mIsRotationChange goes to true
         */
        checkConsistencyUser(gyValue);

        float current_position = mPositions.getCurrentPosition();

        if (!mIsRotationChange)
        {
            /**
             * N.B. For reasons of greater legibility the code was written in this way
             */
            if (mVerseRotation != DirectOrientation.UNDEFINED_ROTATION)
            {
                //We check if the user has traveled half tour
                if (half_tour_complete == false)
                {
                    /**
                     * We observe that for the problem of variance, the condition
                     * current_position > POS_HALF_TOUR is not sufficient when tour is clockwise
                     */
                    if ((current_position > POS_HALF_TOUR - 0.5) &&
                            (current_position < POS_HALF_TOUR + 0.5) &&
                            (mVerseRotation == DirectOrientation.CLOCKWISE_ROTATION))
                        half_tour_complete = true;
                    /**
                     * The extremes are inverted since, current_position, goes from 6 (excluded)
                     * to 0
                     */
                    else if ((current_position < POS_HALF_TOUR + 0.5) &&
                            (current_position > POS_HALF_TOUR - 0.5) &&
                            (mVerseRotation == DirectOrientation.COUNTERCLOCKWISE_ROTATION))
                        half_tour_complete = true;
                }
            }
            else
                checkIfOrientationDirectionIsEstablished(gyValue);
        }
    }

    public String getCurrentOrientation()
    {
        switch (mVerseRotation)
        {
            case CLOCKWISE_ROTATION:
                return "clockwise";
            case UNDEFINED_ROTATION:
                return "undefined";
            case COUNTERCLOCKWISE_ROTATION:
                return "counterclockwise";
        }
        return null;
    }

    /**
     * We check if the user is making the turn in the same direction with which he started it and
     * its angular velocity is lower than MAX_ANGULAR_SPEED
     *
     * @param value
     */
    private void checkConsistencyUser(float[] value)
    {
        //If the rotation is not defined, I have no checks to make
        if (mVerseRotation == DirectOrientation.UNDEFINED_ROTATION)
            return;

        //First we check if the user has exceeded the maximum angular speed
        if (((value[1] > MAX_ANGULAR_SPEED) &&
                (mVerseRotation == DirectOrientation.COUNTERCLOCKWISE_ROTATION)) ||
                ((value[1] < -MAX_ANGULAR_SPEED) &&
                        (mVerseRotation == DirectOrientation.CLOCKWISE_ROTATION)))
        {
            mMaximumAngularSpeedExceeded = true;
        }
        //Next, we check if the user has reversed the direction of the tour
        else if (((mVerseRotation == DirectOrientation.CLOCKWISE_ROTATION) &&
                (value[1] > 0.3f)) ||
                ((mVerseRotation == DirectOrientation.COUNTERCLOCKWISE_ROTATION) &&
                        (value[1] < -0.3f)))
        {
            //storeCriticVal = value[1];
            mIsRotationChange = true;
        }
    }

    /**
     * We verify if the user has decided in which direction to complete the tour
     */
    private void checkIfOrientationDirectionIsEstablished(float[] gyValue)
    {
        float currentPosition = mPositions.getCurrentPosition();
        float statPosition = mPositions.getStartPositionPanoramicMode();

        //This case must be manage separately
        if ((statPosition >= MAX_POSITION - RANGE_INTERVAL_START) ||
                (statPosition <= RANGE_INTERVAL_START))
        {
            if (statPosition >= MAX_POSITION - RANGE_INTERVAL_START)
            {
                if ((currentPosition < 1) && (currentPosition >
                        increment(mPositions.getStartPositionPanoramicMode(),
                                RANGE_INTERVAL_START)) && (gyValue[1] < -0.1))
                {
                    mVerseRotation = DirectOrientation.CLOCKWISE_ROTATION;
                    mPositions.updateStartTourPosition();
                }
                else if (currentPosition < increment(mPositions.getStartPositionPanoramicMode(),
                        -RANGE_INTERVAL_START) && (gyValue[1] > 0.1))
                {
                    mVerseRotation = DirectOrientation.COUNTERCLOCKWISE_ROTATION;
                    mPositions.updateStartTourPosition();
                }
            }
            else
            {
                if ((currentPosition > increment(mPositions.getStartPositionPanoramicMode(),
                        RANGE_INTERVAL_START) && (currentPosition > 0)) && (gyValue[1] < -0.1))
                {
                    mVerseRotation = DirectOrientation.CLOCKWISE_ROTATION;
                    mPositions.updateStartTourPosition();
                }
                else if ((currentPosition > MAX_POSITION - 1) && (currentPosition <
                        increment(mPositions.getStartPositionPanoramicMode(),
                                -RANGE_INTERVAL_START)) && (gyValue[1] > 0.1))
                {
                    mVerseRotation = DirectOrientation.COUNTERCLOCKWISE_ROTATION;
                    mPositions.updateStartTourPosition();
                }
            }
        }
        else
        {
            if (currentPosition > increment(mPositions.getStartPositionPanoramicMode(),
                    RANGE_INTERVAL_START) && (gyValue[1] < -0.1))
            {
                mVerseRotation = DirectOrientation.CLOCKWISE_ROTATION;
                mPositions.updateStartTourPosition();
            }
            else if ((currentPosition < increment(mPositions.getStartPositionPanoramicMode(),
                    -RANGE_INTERVAL_START)) && (gyValue[1] > 0.1))
            {
                mVerseRotation = DirectOrientation.COUNTERCLOCKWISE_ROTATION;
                mPositions.updateStartTourPosition();
            }
        }
    }

    /**
     * This class calculate device position from accelerometer, magnetometer and gyroscope using
     * sensor fusion and complementary filter approach
     * The algorithm steps are as follows:
     * <p>
     * Calculate device's position using magnetometer and accelerometer data using android's
     * SensorManager method getOrientation()
     * <p>
     * Calculate Quaternion from gyro data as reported on:
     * https://developer.android.com/reference/android/hardware/SensorEvent
     * <p>
     * Converts quaternion to rotation matrix using android's method getRotationMatrixFromVector()
     * <p>
     * Align gyro data to inertial reference system (same as magAcc data), to do so generate magAcc
     * rotation matrix and multiply with gyroRotationMatrix. This is done only first time gyro
     * position is computed.
     * Calculate rotation matrix corresponding to rotation calculated by gyroscope data.
     * This done by multiplying gyroRotationMatrix with rotation matrix calculated from quaternion
     * Calculate gyro position using android's method getOrientation()
     * Calculate fused position adding magAcc position data with gyro position data and apply
     * complementary filter
     * Calculate rotation matrix corresponding to actual fused position and multiply with
     * gyroRotationMatrix
     * in order to eliminate Drift from gyro position.
     */

    private class ManageSensors implements SensorEventListener
    {
        public int sampleToDiscard;

        /**
         * This constant is used to decide when Rotation vector is big enough to start computing
         * quaternion
         */
        public static final float EPSILON = 0.000000001f;

        // System sensor manager instance.
        private SensorManager mSensorManager;

        private Sensor mSensorGyro;
        private Sensor mSensorAcc;
        private Sensor mSensorMag;

        //Constant to perform conversion from nanosecond to second
        private static final float NANOTOSEC = 1.0f / 1000000000.0f;

        //This timestamp is used to calculate integration time
        private float timestamp;

        //Raw data from sensors
        //Raw data from sensors
        private float[] mGyroscopeData = new float[3];
        private float[] mAccelerometerData = new float[3];
        private float[] mMagnetometerData = new float[3];

        //Raw data from sensor low pass filtered
        private float[] mAccDataFiltered = new float[3];
        private float[] mMagDataFiltered = new float[3];

        //position computed from accelerometer and magnetometer
        private float[] magAccPosition = new float[3];
        //position computed from accelerometer and magnetometer with data filtered
        private float[] magAccPositionFiltered = new float[3];

        //position computed from gyroscope
        private float[] gyroPosition = new float[3];
        private float[] fusedPosition = new float[3];

        //Orientation vector normalized
        private float[] normGyroVector = new float[3];

        //quaternion vector
        private float[] deltaRotationVector = new float[4];

        //Rotation matrix calculated from quaternion
        private float[] deltaRotationMatrix = new float[9];

        //Actual Rotation matrix for gyro contains drift corrections
        private float[] gyroRotationMatrix = new float[9];

        // Rotation matrix for magnetometer and accelerometer data
        private float[] magAccRotationMatrix = new float[9];

        //Rotation matrix for magnetometer and accelerometer data with data filtered
        private float[] magAccRotationMatrixFiltered = new float[9];

        /**
         * initState is used to access if statement only during first gyro sensor event
         * magAccPosOk is used to access if statement only if there is a valid position obtained
         * from acc and mag
         */
        private boolean initState = true;
        private boolean magAccPosOk = false;

        /**
         * Filter coeff is used in complementary filter to weight gyro data or magAcc data on final
         * result
         */
        private float balanceFilterCoeff = 0.9f;
        private float lowPassFilterCoeff = 0.3f;

        //Constructor
        private ManageSensors(Context mContext)
        {
            //
            /**
             * Initialize gyroRotationMatrix to identity matrix in order to permits matrix
             * multiplications
             */
            getIdentityMatrix(gyroRotationMatrix);
            sampleToDiscard = 200;
            mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
            mSensorAcc = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mSensorMag = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            mSensorGyro = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }

        /**
         * The following methods registerSensors() and unregisterSensor() needs to be used in order
         * to stop sensors and avoid the battery to be discharged
         */
        private void registerSensors()
        {
            if (mSensorAcc != null)
            {
                mSensorManager.registerListener(this, mSensorAcc,
                        SensorManager.SENSOR_DELAY_GAME);
            }
            if (mSensorMag != null)
            {
                mSensorManager.registerListener(this, mSensorMag,
                        SensorManager.SENSOR_DELAY_GAME);
            }
            if (mSensorGyro != null)
            {
                mSensorManager.registerListener(this, mSensorGyro,
                        SensorManager.SENSOR_DELAY_GAME);
            }
        }

        private void unRegisterSensors()
        {
            mSensorManager.unregisterListener(this);
        }

        @Override
        public void onSensorChanged(SensorEvent event)
        {
            /**
             * Define sensor type variable and read from sensor copying data in the right variable
             * using switch-case construct
             */
            int sensorType = event.sensor.getType();

            switch (sensorType)
            {
                case Sensor.TYPE_GYROSCOPE:
                    mGyroscopeData = event.values.clone();
                    updateInfoOrientation(mGyroscopeData);
                    break;
                case Sensor.TYPE_ACCELEROMETER:
                    mAccelerometerData = event.values.clone();
                    mAccDataFiltered = lowPassFilter(mAccelerometerData, mAccDataFiltered);
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    mMagnetometerData = event.values.clone();
                    mMagDataFiltered = lowPassFilter(mMagnetometerData, mMagDataFiltered);
                    break;
                default:
                    return;
            }

            /**
             * Calculate magAccRotationMatrix and magAccPosition, set magAccPosOk == true
             * this position is magnetometer-Accelerometer part of final fused position
             */
            boolean rotationOK = SensorManager.getRotationMatrix(magAccRotationMatrix,
                    null, mAccelerometerData, mMagnetometerData);

            if (rotationOK)
            {
                magAccPosition = SensorManager.getOrientation(magAccRotationMatrix, magAccPosition);
                magAccPosOk = true;
                if ((mPositions == null) && (ALGORITHM_FOR_POSITION == AlgorithmForPosition.ANDROID))
                {
                    mPositions = new Positions(magAccPosition[0]);
                    return;
                }
                else if (ALGORITHM_FOR_POSITION == AlgorithmForPosition.ANDROID)
                {
                    mPositions.setPositionValue(magAccPosition[0]);
                    return;
                }

            }

            boolean rotationOk2 = SensorManager.getRotationMatrix(magAccRotationMatrixFiltered,
                    null, mAccDataFiltered, mMagDataFiltered);

            if (rotationOk2)
            {
                magAccPositionFiltered = SensorManager.getOrientation(magAccRotationMatrixFiltered,
                        magAccPositionFiltered);

                if (sampleToDiscard > 0)
                {
                    sampleToDiscard--;
                    return;
                }
                else if ((mPositions == null) && (ALGORITHM_FOR_POSITION ==
                        AlgorithmForPosition.LOW_PASS))
                {
                    mPositions = new Positions(magAccPositionFiltered[0]);
                    return;
                }
                else if (ALGORITHM_FOR_POSITION == AlgorithmForPosition.LOW_PASS)
                {
                    mPositions.setPositionValue(magAccPositionFiltered[0]);
                    return;
                }
            }
            //azimuthDataLowPassFiltered = magAccPositionFiltered[0];

            /**
             * The following code is accessed only if sensor is gyro.
             * Basically this check is needed for timestamp consistency in order to calculate
             * dT only between gyro events
             */
            if (sensorType == Sensor.TYPE_GYROSCOPE)
            {
                /**
                 * Following code is taken and adapted from android documentation:
                 * https://developer.android.com/reference/android/hardware/SensorEvent
                 */
                if (timestamp != 0)
                {
                    /**
                     * Calculate time lapsed from consecutive sampling and convert from nanosecond
                     * to second
                     */
                    final float dT = (event.timestamp - timestamp) * NANOTOSEC;

                    /**
                     * To create quaternion we need:
                     * -Magnitude of rotation vector normalized
                     * -Angle of Rotation vector
                     */

                    //Calculate the resultant magnitude vector in 3d space
                    float resultantGyroVector = (float) Math.sqrt(mGyroscopeData[0]
                            * mGyroscopeData[0] + mGyroscopeData[1] * mGyroscopeData[1] +
                            mGyroscopeData[2] * mGyroscopeData[2]);

                    //Normalization of rotation vector
                    if (resultantGyroVector > EPSILON)
                    {
                        normGyroVector[0] = mGyroscopeData[0] / resultantGyroVector;
                        normGyroVector[1] = mGyroscopeData[1] / resultantGyroVector;
                        normGyroVector[2] = mGyroscopeData[2] / resultantGyroVector;
                    }
                    //Calculate rotation angle
                    float angleComputed = resultantGyroVector * dT / 2.0f;
                    float sinAngleComputed = (float) Math.sin(angleComputed);
                    float cosAngleComputed = (float) Math.cos(angleComputed);

                    /**
                     * Create quaternion. By definition a quaternion is composed of 4 elements:
                     * -Rotation along X axis
                     * -Rotation along Y axis
                     * -Rotation along Z axis
                     * -Magnitude of rotation
                     */
                    deltaRotationVector[0] = sinAngleComputed * normGyroVector[0];
                    deltaRotationVector[1] = sinAngleComputed * normGyroVector[1];
                    deltaRotationVector[2] = sinAngleComputed * normGyroVector[2];
                    deltaRotationVector[3] = cosAngleComputed;


                    //From quaternion above calculate the corresponding rotation matrix
                    SensorManager.getRotationMatrixFromVector(deltaRotationMatrix,
                            deltaRotationVector);

                    /**
                     * At this point the gyro data are referenced to sensor body system  that is
                     * different from inertial reference system in which magAccPosition data is
                     * computed.
                     * We need to align gyro data with magAccData. To do so we multiply
                     * gyroRotationMatrix with magAccRotationMatrix that is already aligned with
                     * inertial reference system.
                     * In this way gyroRotationMatrix it will be aligned with inertial reference
                     * system as well. This step is done only the first time gyro event occurs and
                     * only if magAccPosition is available
                     */
                    if (initState && magAccPosOk)
                    {

                        gyroRotationMatrix = matrixMultiplication(magAccRotationMatrix,
                                gyroRotationMatrix);
                        initState = false;
                    }

                    /**
                     * Update gyroRotationMatrix with actual gyro value rotation. Here is where
                     * integration is performed. gyroRotationMatrix is updated with current rotation
                     * values and aligned with inertial reference system
                     */
                    gyroRotationMatrix = matrixMultiplication(deltaRotationMatrix,
                            gyroRotationMatrix);

                    /**
                     * Get position from gyro rotation matrix using android's method getOrientation()
                     * (Here gyro position is affected by drift). The calculated position is used in the
                     * final fused position
                     */
                    SensorManager.getOrientation(gyroRotationMatrix, gyroPosition);
                }

                //update timestamp
                timestamp = event.timestamp;

                /**
                 * Check if the signs of gyroPosition and magAccPositionFiltered are different,
                 * in the case change the signs in order to manage "transition to zero" problem
                 */

                if ((gyroPosition[0] < 0.0f && magAccPositionFiltered[0] > 0.0f)
                        || (gyroPosition[0] > 0.0f && magAccPositionFiltered[0] < 0.0f))
                    gyroPosition[0] = gyroPosition[0] * (-1.0f);

                if ((gyroPosition[1] < 0.0f && magAccPositionFiltered[1] > 0.0f)
                        || (gyroPosition[1] > 0.0f && magAccPositionFiltered[1] < 0.0f))
                    gyroPosition[1] = gyroPosition[1] * (-1.0f);

                if ((gyroPosition[2] < 0.0f && magAccPositionFiltered[2] > 0.0f)
                        || (gyroPosition[2] > 0.0f && magAccPositionFiltered[2] < 0.0f))
                    gyroPosition[2] = gyroPosition[2] * (-1.0f);

                /**
                 * Calculate fused position and apply complementary filter
                 * --> if filter_coeff is near 1 Gyro values wins, if is near 0 magAcc wins
                 */
                fusedPosition[0] = balanceFilterCoeff * gyroPosition[0] +
                        (1.0f - balanceFilterCoeff) * magAccPositionFiltered[0];
                fusedPosition[1] = balanceFilterCoeff * gyroPosition[1] +
                        (1.0f - balanceFilterCoeff) * magAccPositionFiltered[1];
                fusedPosition[2] = balanceFilterCoeff * gyroPosition[2] +
                        (1.0f - balanceFilterCoeff) * magAccPositionFiltered[2];

                /**
                 * Until here fused position is affected by drift generated by gyro.
                 * To eliminate drift, gyro should continue to follow the value provided by magAcc
                 * that is without drift.
                 * To do so we update gyroRotationMatrix with new rotation matrix calculated by
                 * actual fused position. In this way the resultant gyroRotationMatrix
                 * contains "rotation instructions" in order to move new gyro position affected by
                 * drift versus magAcc position that is without drift.
                 * Method getRotationMatrixFromAngle is used to calculate
                 * rotation matrix from euler angles (actual position) passed by arguments.
                 * The vector's values passed are swapped since the method is expected to receive X
                 * Y Z angle in this order, but fusedPosition vector has Y Z X order instead
                 */
                gyroRotationMatrix = getRotationMatrixFromAngle(fusedPosition[1], fusedPosition[2],
                        fusedPosition[0]);

                if (sampleToDiscard > 0)
                {
                    sampleToDiscard--;
                    return;
                }
                else if ((mPositions == null) && (ALGORITHM_FOR_POSITION == AlgorithmForPosition.FUSED))
                    mPositions = new Positions(fusedPosition[0]);
                else if (ALGORITHM_FOR_POSITION == AlgorithmForPosition.FUSED)
                    mPositions.setPositionValue(fusedPosition[0]);
            }
        }

        /**
         * This method performs low pass filtering of data passed by argument.
         * @param last is the data related to last sensor event
         * @param current Is the new data provided by sensor event
         * @return
         */
        private float[] lowPassFilter(float[] last, float[] current)
        {
            for (int i = 0; i < last.length; i++)
                current[i] = current[i] + lowPassFilterCoeff * (last[i] - current[i]);
            return current;
        }

        /**
         * This method calculate the Rotation Matrix corresponding to a given position passed by
         * argument
         * The matrix calculated is the combination of the 3 rotation matrix corresponding to
         * rotation
         * of X Y Z vectors. The convention used for calculation is ENU, right hand rule. Angles X Y
         * are considered positive with counterclockwise rotation, angle Z is considered negative
         * with counterclockwise rotation.
         *
         * @param angleX rotation along X axis in rad
         * @param angleY rotation along Y axis in rad
         * @param angleZ rotation along Z axis in rad
         * @return <code>float[] rm</code> The rotation matrix corresponding to position passed to
         * the method
         */

        private float[] getRotationMatrixFromAngle(float angleX, float angleY, float angleZ)
        {
            float[] rm = new float[9];

            float sinX = (float) Math.sin(angleX);
            float cosX = (float) Math.cos(angleX);

            float sinY = (float) Math.sin(angleY);
            float cosY = (float) Math.cos(angleY);

            float sinZ = (float) Math.sin(angleZ);
            float cosZ = (float) Math.cos(angleZ);

            // rotation about x-axis (pitch)
            rm[0] = (cosZ * cosY) - (sinX * sinY * sinZ);
            rm[1] = cosX * sinZ;
            rm[2] = (cosZ * sinY) + (sinZ * sinX * cosY);
            rm[3] = 0.0f - (sinZ * cosY) - (sinX * sinY * cosZ);
            rm[4] = cosZ * cosX;
            rm[5] = (cosZ * sinX * cosY) - (sinZ * sinY);
            rm[6] = 0.0f - (sinY * cosX);
            rm[7] = 0.0f - sinX;
            rm[8] = cosX * cosY;

            return rm;
        }

        /**
         * This method performs matrix multiplication, it should be re-emplemented using
         * Strassen algorithm in order to get better performance
         *
         * @param A Matrix to be multiplied with B
         * @param B Matrix to be multiplied with A
         * @return <code>float[]</code> The resulting matrix from A*B multiplication
         */
        private float[] matrixMultiplication(float[] A, float[] B)
        {
            float[] result = new float[9];

            result[0] = A[0] * B[0] + A[1] * B[3] + A[2] * B[6];
            result[1] = A[0] * B[1] + A[1] * B[4] + A[2] * B[7];
            result[2] = A[0] * B[2] + A[1] * B[5] + A[2] * B[8];

            result[3] = A[3] * B[0] + A[4] * B[3] + A[5] * B[6];
            result[4] = A[3] * B[1] + A[4] * B[4] + A[5] * B[7];
            result[5] = A[3] * B[2] + A[4] * B[5] + A[5] * B[8];

            result[6] = A[6] * B[0] + A[7] * B[3] + A[8] * B[6];
            result[7] = A[6] * B[1] + A[7] * B[4] + A[8] * B[7];
            result[8] = A[6] * B[2] + A[7] * B[5] + A[8] * B[8];

            return result;
        }

        /**
         * This method calculates 3x3 identity matrix
         *
         * @param m The matrix to be converted into identity matrix
         */
        private void getIdentityMatrix(float[] m)
        {
            for (int i = 0; i < m.length; i++)
            {
                if (i == 0 || i == 4 || i == 8)
                    m[i] = 1;
                else
                    m[i] = 0;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy)
        {
            Log.d(TAG, "onAccuracyChanged: " + sensor + ", accuracy: " + accuracy);
        }
    }

    /**
     * This class contains the initial position in which the user has switched to panoramic mode
     * (mStartPositionPanoramicMode),the position in which the direction of the turn was established
     * (mStartTourPosition), and the current position
     */
    class Positions
    {
        //Initial position in which the user starts panoramic mode
        private float mStartPositionPanoramicMode;

        //Initial position in which we began to analyze the frames in panoramic mode
        private float mStartTourPosition;

        /**
         * Position value(between [0,6[) to be attributed to the frame.This value already takes into
         * account the mStartTourPosition value. So, when this value is equal to 3,
         * means that the user has done half tour
         */
        private float mCurrentPosition;

        //Constructor
        private Positions(float firstAzimut)
        {
            float normAz = convertAzimToPosition(firstAzimut);
            mStartPositionPanoramicMode = normAz;
            mCurrentPosition = normAz;
        }

        private void updateStartTourPosition()
        {
            this.mStartTourPosition = mCurrentPosition;
        }

        private float getStartPositionPanoramicMode()
        {
            return mStartPositionPanoramicMode;
        }

        private float getCurrentPosition()
        {
            return mCurrentPosition;
        }

        private void setPositionValue(float mAzimuthValue)
        {
            this.mCurrentPosition = convertAzimToPosition(mAzimuthValue);
        }

        /**
         * The value of azimuth takes values between [0, pi] and [-pi, 0].
         * <p>
         * For simplicity of calculation, we added 6 if negative so that, now, the value we have a
         * position value is included in the range[0,6[.
         *
         * @return
         */
        private float convertAzimToPosition(float valueAz)
        {
            //In this way valueAz is always a value between [0,6[
            if (valueAz < 0)
                valueAz = valueAz + 6;

            if (mVerseRotation != DirectOrientation.UNDEFINED_ROTATION)
            {
                //This means that mStartTourPosition contains the value of start tour.
                // Subtract it
                valueAz = increment(valueAz, -mPositions.mStartTourPosition);
            }
            return valueAz;
        }
    }

}
