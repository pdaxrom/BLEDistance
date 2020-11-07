package com.pdaxrom.bledistance;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private static final int NUM_PAGES = 3;
    private ViewPager2 mPager;

    private final BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            if (item.getItemId() == R.id.connection_page) {
                mPager.setCurrentItem(0);
                return true;
            } else if (item.getItemId() == R.id.settings_page) {
                mPager.setCurrentItem(1);
                return true;
            } else if (item.getItemId() == R.id.about_page) {
                mPager.setCurrentItem(2);
                return true;
            }
            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getSupportActionBar().hide();

        setContentView(R.layout.activity_main);

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

        // Instantiate a ViewPager and a PagerAdapter.
        mPager = findViewById(R.id.pager);
        ScreenSlidePagerAdapter pagerAdapter = new ScreenSlidePagerAdapter(getSupportFragmentManager(), getLifecycle());
        mPager.setAdapter(pagerAdapter);

        final BottomNavigationView navigation = findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        mPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                Log.i(TAG, "new position " + position);
                int navPage;
                switch (position) {
                    case 1: navPage = R.id.settings_page; break;
                    case 2: navPage = R.id.about_page; break;
                    default: navPage = R.id.connection_page; break;
                }
                navigation.setSelectedItemId(navPage);
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
    }

    private static class ScreenSlidePagerAdapter extends FragmentStateAdapter {
        public ScreenSlidePagerAdapter(@NonNull FragmentManager fragmentManager, @NonNull Lifecycle lifecycle) {
            super(fragmentManager, lifecycle);
        }

        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 1: return new SettingsFragment();
                case 2: return new AboutFragment();
                default: return new ConnectFragment();
            }
        }

        @Override
        public int getItemCount() {
            return NUM_PAGES;
        }
    }

}
