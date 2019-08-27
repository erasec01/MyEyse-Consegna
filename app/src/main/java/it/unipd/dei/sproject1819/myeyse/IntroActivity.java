package it.unipd.dei.sproject1819.myeyse;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.support.v4.content.ContextCompat;
import android.support.v4.text.HtmlCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class IntroActivity extends AppCompatActivity
{
    private static String TAG = "IntroActivity";

    //Variables that describe themselves
    ViewPager mSlideViewPager;
    LinearLayout mDotLayout;
    SliderAdapter sliderAdapter;
    private Button btnNext;
    private Button btnPrev;
    List<ScreenElements> mList;
    private TextView[] mDots;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_intro);

        //Check if it is the first start of the app
        if (!isFirstTimeStartApp())
        {
            startMainActivity();
            finish();
        }
        mList = new ArrayList<>();

        mSlideViewPager = findViewById(R.id.viewPager);
        mDotLayout = (LinearLayout) findViewById(R.id.dots_layout);

        btnNext = findViewById(R.id.btn_next);
        btnPrev = findViewById(R.id.btn_prev);

        btnNext.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                int currentPage = (mSlideViewPager.getCurrentItem() + 1);
                if (currentPage == mList.size() + 2)
                    startMainActivity();
                else
                    mSlideViewPager.setCurrentItem(currentPage);
            }
        });
        btnPrev.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                int currentPage = mSlideViewPager.getCurrentItem() - 1;
                mSlideViewPager.setCurrentItem(currentPage);
            }
        });
        mList.add(new ScreenElements(R.string.title_mode_object_detection_slide,
                R.string.description_mode_object_detection_slide, R.drawable.obj));

        mList.add(new ScreenElements(R.string.title_mode_panoramic_slide,
                R.string.description_mode_panoramic_slide, R.drawable.panoramic));

        mList.add(new ScreenElements(R.string.title_panoramic_mode_slide_2,
                R.string.panoramic_mode_slide_2, R.drawable.panoramic_result));

        mList.add(new ScreenElements(R.string.title_panoramic_mode_slide_3,
                R.string.panoramic_mode_slide_3, R.drawable.sad));

        sliderAdapter = new SliderAdapter(this, mList);
        mSlideViewPager.setAdapter(sliderAdapter);
        addDotsStatus(0);
        mSlideViewPager.addOnPageChangeListener(viewListener);
    }

    /**
     * Update color dots relative to the new position
     * @param position new
     */
    public void addDotsStatus(int position)
    {
        mDotLayout.removeAllViews();
        mDots = new TextView[mList.size() + 2];

        for (int i = 0; i < mDots.length; i++)
        {
            mDots[i] = new TextView(this);
            mDots[i].setText(HtmlCompat.fromHtml("&#8226",HtmlCompat.FROM_HTML_MODE_LEGACY));
            mDots[i].setTextSize(35);
            mDots[i].setTextColor(ContextCompat.getColor(this, R.color.inactive_dots));
            mDotLayout.addView(mDots[i]);
        }
        //Set current dot active
        if (mDots.length > 0)
            mDots[position].setTextColor(ContextCompat.getColor(this, R.color.active_dots));

    }

    ViewPager.OnPageChangeListener viewListener = new ViewPager.OnPageChangeListener()
    {
        @Override
        public void onPageScrolled(int i, float v, int i1)
        {
            Log.d(TAG, "OnPageScrolled");
        }

        @Override
        public void onPageSelected(int i)
        {
            if (i == mList.size() + 1)

                btnNext.setText("START");
            else
                btnNext.setText("Next");
            addDotsStatus(i);
        }

        @Override
        public void onPageScrollStateChanged(int i)
        {
            Log.d(TAG, "OnPageScrolled");
        }
    };

    private boolean isFirstTimeStartApp()
    {
        SharedPreferences ref = getApplicationContext().getSharedPreferences("IntroSlide", Context.MODE_PRIVATE);
        return ref.getBoolean("FirstTimeStartFlag", true);
    }

    private void setFirstTimeStartStatus(boolean stt)
    {
        SharedPreferences ref = getApplicationContext().getSharedPreferences(
                "IntroSlide", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = ref.edit();
        editor.putBoolean("FirstTimeStartFlag", stt);
        editor.commit();
    }

    //go to main Activity
    private void startMainActivity()
    {
        setFirstTimeStartStatus(false);
        startActivity(new Intent(IntroActivity.this, DetectorActivity.class));
        finish();
    }
}
