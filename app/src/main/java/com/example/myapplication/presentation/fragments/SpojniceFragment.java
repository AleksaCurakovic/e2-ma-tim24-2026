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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.myapplication.R;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpojniceFragment extends Fragment {

    private final List<String> left  = new ArrayList<>();
    private final List<String> right = new ArrayList<>();
    private final Map<String,String> pairs = new HashMap<>();

    private String pendingLeft = null;

    public SpojniceFragment() { super(R.layout.fragment_spojnice); }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_spojnice, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        TextView tvTimer = view.findViewById(R.id.tvSpojniceTimer);
        GridLayout gridLeft = view.findViewById(R.id.gridLeft);
        GridLayout gridRight = view.findViewById(R.id.gridRight);
        TextView tvResult = view.findViewById(R.id.tvSpojniceResult);

        left.clear(); right.clear(); pairs.clear();
        left.add("Queen"); left.add("Metallica"); left.add("ABBA"); left.add("The Beatles"); left.add("Nirvana");
        pairs.put("Queen", "Bohemian Rhapsody");
        pairs.put("Metallica", "Nothing Else Matters");
        pairs.put("ABBA", "Dancing Queen");
        pairs.put("The Beatles", "Hey Jude");
        pairs.put("Nirvana", "Smells Like Teen Spirit");
        right.addAll(pairs.values());
        Collections.shuffle(right);

        setupColumn(gridLeft, left, true, tvResult);
        setupColumn(gridRight, right, false, tvResult);

        new CountDownTimer(30000, 500) {
            @Override public void onTick(long ms) { tvTimer.setText((ms/1000)+"s"); }
            @Override public void onFinish() { tvTimer.setText("0s"); }
        }.start();
    }

    private void setupColumn(GridLayout grid, List<String> items, boolean isLeft, TextView tvResult) {
        grid.removeAllViews();
        grid.setColumnCount(1);
        for (String text : items) {
            MaterialCardView card = (MaterialCardView) View.inflate(requireContext(), R.layout.item_spojnice_card, null);
            TextView tv = card.findViewById(R.id.tvText);
            tv.setText(text);
            card.setOnClickListener(v -> onCardClicked(text, isLeft, card, tvResult));
            GridLayout.LayoutParams p = new GridLayout.LayoutParams();
            p.width = ViewGroup.LayoutParams.MATCH_PARENT;
            p.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            p.setMargins(dp(4), dp(4), dp(4), dp(4));
            card.setLayoutParams(p);
            grid.addView(card);
        }
    }

    private void onCardClicked(String text, boolean isLeft, MaterialCardView card, TextView tvResult) {
        if (isLeft) {
            pendingLeft = text;
            highlight(card, true);
        } else {
            if (pendingLeft == null) return;
            boolean correct = pairs.get(pendingLeft).equals(text);
            tvResult.setText(correct ? "Tačno +2" : "Pogrešno");
            highlight(card, correct);
            pendingLeft = null;
        }
    }

    private void highlight(MaterialCardView card, boolean success) {
        int color = ContextCompat.getColor(requireContext(), success ? R.color.skocko_cell_filled : R.color.skocko_absent);
        card.setCardBackgroundColor(color);
    }

    private int dp(int dp) { return Math.round(dp * requireContext().getResources().getDisplayMetrics().density); }
}
