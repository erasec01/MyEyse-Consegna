package it.unipd.dei.sproject1819.myeyse;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

public class TextToSpeechImp
{
    //Value between 0 to 1
    private float speechRate;

    //Object for speaker management
    private TextToSpeech t1;

    //Constructor
    public TextToSpeechImp(Context context,
                           final UtteranceProgressListener utteranceProgressListener)
    {
        speechRate = 1f;
        t1 = new TextToSpeech(context, new TextToSpeech.OnInitListener()
        {
            @Override
            public void onInit(int status)
            {
                if (status == TextToSpeech.SUCCESS)
                {
                    t1.setLanguage(Locale.UK);
                    t1.setOnUtteranceProgressListener(utteranceProgressListener);
                }
            }
        });
        t1.setSpeechRate(speechRate);
    }

    /**
     * Send an audio message to the speaker
     * @param text
     * @param utteranceld this parameter is useful if we needs processed some operation
     *                    after speaker has finished speaking
     */
    public void speechMessage(String text, String utteranceld)
    {
        t1.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceld);
    }

    /**
     * Send an audio message to the speaker,
     * @param text
     */
    public void speechMessage(String text)
    {
        speechMessage(text, null);
    }

    /**
     * Set the new speed at which the audio message is played.
     * Typically this is lower than the default one
     *
     * @param value new speed audio message
     */
    public void setSpeechRate(float value)
    {
        t1.setSpeechRate(value);
    }

    /**
     * Reset the speed to the default one
     */
    public void resetSpeechRate()
    {
        t1.setSpeechRate(speechRate);
    }

    //Release resource
    public void close()
    {
        if (t1 != null)
        {
            //Interrupts the current utterance (whether played or rendered to file)
            // and discards other utterances in the queue.
            t1.stop();
            //Releases the resources used by the TextToSpeech engine
            t1.shutdown();
        }
    }

    //Stop the current audio message produced by the speaker
    public void interruptSpeech()
    {
        if (t1 != null)
            t1.stop();
    }
}
