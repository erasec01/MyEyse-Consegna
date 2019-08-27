package it.unipd.dei.sproject1819.myeyse;

/**
 * Support class
 */
public class InfoBack
{
    private int mDegrees;
    private ManageRotation.DirectOrientation mOrientation;

    public InfoBack(int degrees, ManageRotation.DirectOrientation orientation)
    {
        mDegrees = degrees;
        mOrientation = orientation;
    }

    public int getDegrees()
    {
        return mDegrees;
    }

    public ManageRotation.DirectOrientation getOrientation()
    {
        return mOrientation;
    }
}
