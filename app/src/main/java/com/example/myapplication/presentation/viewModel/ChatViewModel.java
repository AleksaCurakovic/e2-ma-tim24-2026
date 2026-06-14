package com.example.myapplication.presentation.viewModel;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.myapplication.data.model.ChatMessage;
import com.example.myapplication.data.repository.ChatRepository;

import java.util.List;

public class ChatViewModel extends ViewModel {

    private final ChatRepository repository = new ChatRepository();

    public final MutableLiveData<List<ChatMessage>> messages = new MutableLiveData<>();
    public final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    private String region;

    public void start(String region) {
        if (region == null || region.equals(this.region)) return;
        this.region = region;
        repository.listen(region,
                messages::postValue,
                e -> errorMessage.postValue(e.getMessage()));
    }

    public void send(String senderUid, String senderUsername, String text) {
        if (region == null || text == null || text.trim().isEmpty()) return;
        ChatMessage message = new ChatMessage(senderUid, senderUsername, text.trim());
        repository.send(region, message, unused -> {}, e -> errorMessage.postValue(e.getMessage()));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        repository.detach();
    }
}
