package it.unipd.dei.sproject1819.myeyse;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;

public class ImageSimilarity
{
    //Calculated empirical value: below this threshold two frames can be considered similar
    private static final int SIMILARITY_THRESHOLD = 95000;

    //In according to https://pdfs.semanticscholar.org/b539/791e55d550b493701c82f76c4867b3b88a3a.pdf
    private static final int WRESOLUTION = 256;
    private static final int HRESOLUTION = 256;

    //Input resolution frame
    private int resolutionFrameW;
    private int resolutionFrameH;

    //Matrix needed to cropped the frame
    private Matrix frameToCropTransform;

    //Bitmap where we store
    private Bitmap frameBitmap;

    //Bitmap where we stored cropped image
    private Bitmap croppedBitmap;

    //Histograms for the primary colors of the first frame
    private HistogramsImage firstFrameHist = null;

    /**
     * Constructor
     *
     * @param h frame height
     * @param w width frame
     * @param sensorOrientation properly of smartphone
     */
    public ImageSimilarity(int w, int h, int sensorOrientation)
    {
        //Memorize the resolution of the frames captured by the camera
        resolutionFrameW = w;
        resolutionFrameH = h;

        /**
         * We get the matrix needed to do the scaling from the frame to a WRESOLUTION x HRESOLUTION
         * resolution.
         *
         * Obviously this is calculated a single time when calculating the histogram of the first
         * frame, then it is reused
         */
        frameToCropTransform = ImageUtils.getTransformationMatrix(resolutionFrameW, resolutionFrameH,
                WRESOLUTION, HRESOLUTION, sensorOrientation, false);

        frameBitmap = Bitmap.createBitmap(resolutionFrameW, resolutionFrameH,
                Bitmap.Config.ARGB_8888);

        //Empty bitmap where we store frame
        croppedBitmap = Bitmap.createBitmap(WRESOLUTION, HRESOLUTION,
                Bitmap.Config.ARGB_8888);
    }

    public void setFrameReference(int[]rgb)
    {
        //Cropping the input frame to WRESOLUTION x HRESOLUTION resolution
        Bitmap firs = cropFrame(rgb);

        /**
         * Calculate and store the histogram and then compare it with the frames next to the
         * initial position
         */
        firstFrameHist = new HistogramsImage(firs);
    }
    /**
     * Determines whether the starting frame and the current frame can be considered similar
     *
     * @param rgb_frame of current frame analyzed.
     * @return true if these frames can be considered similar, false otherwise
     */
    public boolean IsSimilarWhitReferenceFrame(int[] rgb_frame)
    {
        if (compareHistogramImages(createHistogram(rgb_frame)) < SIMILARITY_THRESHOLD)
            return true;
        else
            return false;
    }

    public void close()
    {
        firstFrameHist = null;
    }

    /**
     * Method used to calculate a measure of similarity between current and starting frames based on
     * the analysis of their histograms
     *
     * @param histogramsImage Histograms related to a frame that you want to compare with the
     *                        histograms of the starting frame
     * @return Similarity measure
     */
    private int compareHistogramImages(HistogramsImage histogramsImage)
    {
        int sum = 0;

        //We remember that WRESOLUTION = HRESOLUTION = 256
        for (int i = 0; i < WRESOLUTION; i++)
        {
            sum += Math.abs(histogramsImage.mHistGreen[i] - firstFrameHist.mHistGreen[i]) +
                    Math.abs(histogramsImage.mHistBlu[i] - firstFrameHist.mHistBlu[i]) +
                    Math.abs(histogramsImage.mHistRed[i] - firstFrameHist.mHistRed[i]);
        }
        return sum;
    }

    /**
     * @param rgb that you want to cut out
     * @return Bitmap relative to the crop frame
     */
    private Bitmap cropFrame(int[] rgb)
    {
        //Give for each pixel a color to the empty bitmap previously create
        frameBitmap.setPixels(rgb, 0, resolutionFrameW, 0, 0,
                resolutionFrameW, resolutionFrameH);

        //Crop and return frame
        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(frameBitmap, frameToCropTransform, null);
        return croppedBitmap;
    }

    private HistogramsImage createHistogram(int[] rgb_frame)
    {
        Bitmap cur_frame_bitmap = cropFrame(rgb_frame);
        return new HistogramsImage(cur_frame_bitmap);
    }

    /**
     * Inner class which contains the red, blue and green histograms if a image provided in the
     * constructor
     */
    private class HistogramsImage
    {
        private final int NUM_BUCKETS = 256;

        //At the i-th position, it contains the number of green pixels having value i
        private int[] mHistGreen = new int[NUM_BUCKETS];

        //At the i-th position, it contains the number of blu pixels having value i
        private int[] mHistBlu = new int[NUM_BUCKETS];

        //At the i-th position, it contains the number of red pixels having value i
        private int[] mHistRed = new int[NUM_BUCKETS];

        /**
         * Constructor
         *
         * @param frame on which to calculate the red, blue and green histograms
         */
        private HistogramsImage(Bitmap frame)
        {
            for (int i = 0; i < frame.getWidth(); i++)
            {
                for (int j = 0; j < frame.getHeight(); j++)
                {
                    int pixel = frame.getPixel(i, j);
                    mHistRed[Color.red(pixel)]++;
                    mHistGreen[Color.green(pixel)]++;
                    mHistBlu[Color.blue(pixel)]++;
                }
            }
        }
    }
}
