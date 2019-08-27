package it.unipd.dei.sproject1819.myeyse;

import it.unipd.dei.sproject1819.myeyse.tracking.MultiBoxTracker;

/**
 * Support class
 */
public class InfoSpeech
{
    private String mMessToSpeech;
    private String speechMessage;

    public InfoSpeech(String label, MultiBoxTracker.Direction pos)
    {
        if (pos == MultiBoxTracker.Direction.LEFT)
            speechMessage = " at eleven o\'clock";
        else if (pos == MultiBoxTracker.Direction.CENTER)
            speechMessage = " at twelve o\'clock";
        else
            speechMessage = " at one o\'clock";

        mMessToSpeech = label + speechMessage;
    }
    public String getmMessToSpeech()
    {
        return mMessToSpeech;
    }

}
