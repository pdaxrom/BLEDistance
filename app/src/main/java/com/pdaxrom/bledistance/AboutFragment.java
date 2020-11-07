package com.pdaxrom.bledistance;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.View;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;
import android.text.Html;
import android.text.method.LinkMovementMethod;

public class AboutFragment extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(
                R.layout.fragment_about, container, false);

        TextView version = rootView.findViewById(R.id.about_version);
        try {
            version.setText(getString(R.string.app_version) + " " +
                    getActivity().getPackageManager().getPackageInfo(
                            getActivity().getPackageName(), 0).versionName);
        } catch (Exception e) {
            //ignore
        }

        TextView textView = rootView.findViewById(R.id.about_info);
        textView.setText(Html.fromHtml(getString(R.string.about_text)));
        textView.setMovementMethod(LinkMovementMethod.getInstance());

        return rootView;
    }
}