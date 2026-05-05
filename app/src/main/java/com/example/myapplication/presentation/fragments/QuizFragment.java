package com.example.myapplication.presentation.fragments;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.myapplication.R;
import com.google.android.material.button.MaterialButton;

import java.util.Arrays;
import java.util.List;

public class QuizFragment extends Fragment {

    public QuizFragment() { super(R.layout.fragment_quiz); }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_quiz, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        TextView tvTimer = view.findViewById(R.id.tvQuizTimer);
        TextView tvQuestion = view.findViewById(R.id.tvQuizQuestion);
        LinearLayout layoutAnswers = view.findViewById(R.id.layoutQuizAnswers);
        TextView tvResult = view.findViewById(R.id.tvQuizResult);

        tvQuestion.setText("Najviši vrh na Balkanu je?");
        List<String> answers = Arrays.asList("Rila - Musala", "Durmitor - Bobotov kuk", "Prokletije - Jezerca", "Šar-planina - Titov vrh");

        for (int i = 0; i < answers.size(); i++) {
            MaterialButton btn = new MaterialButton(requireContext());
            btn.setText(answers.get(i));
            btn.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            final int idx = i;
            btn.setOnClickListener(v -> {
                tvResult.setVisibility(View.VISIBLE);
                tvResult.setText(idx == 0 ? "Tačno! +10" : "Netačno -5");
            });
            layoutAnswers.addView(btn);
        }

        new CountDownTimer(25000, 500) {
            @Override public void onTick(long ms) { tvTimer.setText((ms/1000)+"s"); }
            @Override public void onFinish() { tvTimer.setText("0s"); }
        }.start();
    }
}
