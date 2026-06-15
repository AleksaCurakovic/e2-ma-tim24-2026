package com.example.myapplication.presentation.fragments;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.example.myapplication.data.model.ChatMessage;
import com.example.myapplication.data.model.User;
import com.example.myapplication.presentation.viewModel.ChatViewModel;
import com.example.myapplication.presentation.viewModel.HomeViewModel;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatFragment extends Fragment {

    private ChatViewModel vm;
    private HomeViewModel homeVm;

    private LinearLayout container;
    private ScrollView scroll;
    private String myUid;
    private String region;

    private final SimpleDateFormat timeFmt = new SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault());

    public ChatFragment() {
        super(R.layout.fragment_chat);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity()).get(ChatViewModel.class);
        homeVm = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);

        container = view.findViewById(R.id.chatContainer);
        scroll = view.findViewById(R.id.chatScroll);
        TextView title = view.findViewById(R.id.tvChatTitle);
        EditText etMessage = view.findViewById(R.id.etMessage);
        MaterialButton btnSend = view.findViewById(R.id.btnSend);

        btnSend.setOnClickListener(v -> {
            String text = etMessage.getText().toString().trim();
            if (text.isEmpty()) return;
            User me = homeVm.currentUser.getValue();
            if (me == null || region == null) {
                Toast.makeText(requireContext(), "Ucitavanje korisnika...", Toast.LENGTH_SHORT).show();
                return;
            }
            vm.send(me.getUid(), me.getUsername(), text);
            etMessage.setText("");
        });

        vm.messages.observe(getViewLifecycleOwner(), this::render);

        User user = homeVm.currentUser.getValue();
        if (user != null) {
            bindUser(user, title);
        } else {
            homeVm.currentUser.observe(getViewLifecycleOwner(), u -> {
                if (u != null && region == null) bindUser(u, title);
            });
        }
    }

    private void bindUser(User user, TextView title) {
        myUid = user.getUid();
        region = user.getRegion();
        if (region == null || region.isEmpty()) {
            title.setText("Regionalni cet");
            Toast.makeText(requireContext(), "Nemas dodeljen region.", Toast.LENGTH_SHORT).show();
            return;
        }
        title.setText("Regionalni cet - " + region);
        vm.start(region);
    }

    private void render(List<ChatMessage> messages) {
        container.removeAllViews();
        if (messages == null) return;
        for (ChatMessage m : messages) container.addView(buildBubble(m));
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private View buildBubble(ChatMessage m) {
        boolean mine = myUid != null && myUid.equals(m.getSenderUid());

        LinearLayout wrapper = new LinearLayout(requireContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.bottomMargin = dp(8);
        wrapper.setLayoutParams(wlp);
        wrapper.setGravity(mine ? Gravity.END : Gravity.START);

        LinearLayout bubble = new LinearLayout(requireContext());
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(12), dp(8), dp(12), dp(8));
        bubble.setBackgroundColor(mine ? Color.parseColor("#DBEAFE") : Color.WHITE);
        bubble.setElevation(dp(1));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.leftMargin = mine ? dp(48) : 0;
        blp.rightMargin = mine ? 0 : dp(48);
        bubble.setLayoutParams(blp);

        TextView sender = new TextView(requireContext());
        sender.setText(mine ? "Ti" : (m.getSenderUsername() != null ? m.getSenderUsername() : "-"));
        sender.setTextSize(12);
        sender.setTypeface(null, Typeface.BOLD);
        sender.setTextColor(mine ? Color.parseColor("#1D4ED8") : Color.parseColor("#475569"));
        bubble.addView(sender);

        TextView text = new TextView(requireContext());
        text.setText(m.getText());
        text.setTextSize(15);
        text.setTextColor(Color.parseColor("#111827"));
        bubble.addView(text);

        TextView time = new TextView(requireContext());
        time.setText(m.getTimestamp() > 0 ? timeFmt.format(new Date(m.getTimestamp())) : "");
        time.setTextSize(10);
        time.setTextColor(Color.parseColor("#64748B"));
        time.setGravity(Gravity.END);
        bubble.addView(time);

        wrapper.addView(bubble);
        return wrapper;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
