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
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.example.myapplication.data.model.GameRoom;
import com.example.myapplication.presentation.viewModel.GameViewModel;
import com.google.android.material.button.MaterialButton;

import java.util.HashMap;
import java.util.Map;

public class AsocijacijeFragment extends Fragment {

    private GameViewModel vm;
    private String gameId;
    private String myUsername;
    private CountDownTimer roundTimer;
    private boolean roundEnded = false;

    public AsocijacijeFragment() { super(R.layout.fragment_asocijacije); }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_asocijacije, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);
        gameId = getArguments() != null ? getArguments().getString("gameId") : null;
        myUsername = getArguments() != null ? getArguments().getString("myUsername") : null;
        int roundNumber = getArguments() != null ? getArguments().getInt("roundNumber", 1) : 1;

        TextView tvTimer  = view.findViewById(R.id.tvAsocTimer);
        TextView tvStatus = view.findViewById(R.id.tvAsocStatus);
        GridLayout grid   = view.findViewById(R.id.gridAsoc);

        tvStatus.setText("Asocijacije  •  Runda " + roundNumber + "/2");

        grid.setColumnCount(4);
        for (int i = 0; i < 16; i++) {
            MaterialButton btn = new MaterialButton(requireContext());
            btn.setText("?");
            btn.setOnClickListener(v -> btn.setText("hint"));
            GridLayout.LayoutParams p = new GridLayout.LayoutParams();
            p.width = 0; p.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            p.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            p.setMargins(6, 6, 6, 6);
            btn.setLayoutParams(p);
            grid.addView(btn);
        }

        tvTimer.setText("30s");
        roundTimer = new CountDownTimer(30_000L, 1_000L) {
            @Override public void onTick(long ms) { tvTimer.setText((ms / 1000) + "s"); }
            @Override public void onFinish() { tvTimer.setText("0s"); endRound(); }
        };
        roundTimer.start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (roundTimer != null) { roundTimer.cancel(); roundTimer = null; }
    }

    private void endRound() {
        if (roundEnded) return;
        roundEnded = true;
        if (roundTimer != null) { roundTimer.cancel(); roundTimer = null; }
        GameRoom room = vm.gameRoom.getValue();
        if (gameId != null && room != null && myUsername != null
                && myUsername.equals(room.getPlayerOne())) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("roundPhase", "MINIGAME_DONE");
            vm.advancePhase(gameId, updates);
        }
    }
}
