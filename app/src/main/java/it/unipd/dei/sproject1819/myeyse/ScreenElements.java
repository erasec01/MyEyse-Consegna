package it.unipd.dei.sproject1819.myeyse;


/**
 * This class contains the elements of which a typical is composed a
 * slide, ie image, title, description.
 */

public class ScreenElements
{
    int mTitle;
    int mDescription;
    int mScreenImg;

    public int getTitle()
    {
        return mTitle;
    }

    public int getDescription()
    {
        return mDescription;
    }

    public int getScreenImg()
    {
        return mScreenImg;
    }

    public ScreenElements(int title, int description, int screenImg)
    {
        mTitle = title;
        mDescription = description;
        mScreenImg = screenImg;
    }
}
