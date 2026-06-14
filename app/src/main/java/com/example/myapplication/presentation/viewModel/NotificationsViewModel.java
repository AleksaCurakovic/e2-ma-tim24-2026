package com.example.myapplication.presentation.viewModel;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.myapplication.data.model.AppNotification;
import com.example.myapplication.data.repository.NotificationRepository;

import java.util.ArrayList;
import java.util.List;

public class NotificationsViewModel extends ViewModel {

    public enum Filter { ALL, UNREAD, READ }

    private final NotificationRepository repository = new NotificationRepository();

    private final List<AppNotification> all = new ArrayList<>();
    public final MutableLiveData<List<AppNotification>> visible = new MutableLiveData<>(new ArrayList<>());
    public final MutableLiveData<Filter> filter = new MutableLiveData<>(Filter.ALL);
    public final MutableLiveData<Integer> unreadCount = new MutableLiveData<>(0);

    public void start(String uid) {
        if (uid == null) return;
        repository.listen(uid, list -> {
            all.clear();
            all.addAll(list);
            int unread = 0;
            for (AppNotification n : list) if (!n.isRead()) unread++;
            unreadCount.postValue(unread);
            applyFilter();
        }, e -> { /* tiho */ });
    }

    public void setFilter(Filter f) {
        filter.setValue(f);
        applyFilter();
    }

    private void applyFilter() {
        Filter f = filter.getValue() != null ? filter.getValue() : Filter.ALL;
        List<AppNotification> out = new ArrayList<>();
        for (AppNotification n : all) {
            if (f == Filter.UNREAD && n.isRead()) continue;
            if (f == Filter.READ && !n.isRead()) continue;
            out.add(n);
        }
        visible.postValue(out);
    }

    public void markRead(String uid, String id) {
        repository.markRead(uid, id);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        repository.detach();
    }
}
