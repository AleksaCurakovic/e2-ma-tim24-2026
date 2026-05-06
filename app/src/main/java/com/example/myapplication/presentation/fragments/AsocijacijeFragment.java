package com.example.myapplication.presentation.fragments;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.myapplication.R;
import com.google.android.material.button.MaterialButton;

public class AsocijacijeFragment extends Fragment {

    public AsocijacijeFragment() { super(R.layout.fragment_asocijacije); }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_asocijacije, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        TextView tvTimer = view.findViewById(R.id.tvAsocTimer);
        GridLayout grid = view.findViewById(R.id.gridAsoc);
        TextView tvStatus = view.findViewById(R.id.tvAsocStatus);

        grid.setColumnCount(4);
        for (int i = 0; i < 16; i++) {
            MaterialButton btn = new MaterialButton(requireContext());
            btn.setText("?");
            btn.setOnClickListener(v -> btn.setText("hint"));
            GridLayout.LayoutParams p = new GridLayout.LayoutParams();
            p.width = 0; p.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            p.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            p.setMargins(6,6,6,6);
            btn.setLayoutParams(p);
            grid.addView(btn);
        }

        new CountDownTimer(120000, 500) {
            @Override public void onTick(long ms) { tvTimer.setText((ms/1000)+"s"); }
            @Override public void onFinish() { tvTimer.setText("0s"); tvStatus.setText("Vreme!"); }
        }.start();
    }
}
