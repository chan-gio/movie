package com.example.movieapp.Activities;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.movieapp.Adapters.SearchAdapter;
import com.example.movieapp.Domain.Search.SearchMovie;
import com.example.movieapp.R;
import com.example.movieapp.Domain.WatchedMovie;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {
    private RecyclerView.Adapter adapterSearchMovies;
    private RecyclerView recyclerviewSearchMovies;
    private ProgressBar loading1;
    private RequestQueue mRequestQueue;
    private List<WatchedMovie> watchedMovieList = new ArrayList<>();

    private List<WatchedMovie> previousWatchedMovieList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        initView();
        getWatchedListFromFirebase();
    }

    private void getWatchedListFromFirebase() {
        loading1.setVisibility(View.VISIBLE);
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            DatabaseReference watchListRef = FirebaseDatabase.getInstance().getReference()
                    .child("users").child(currentUser.getUid()).child("watchedMovies");
            watchListRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    List<WatchedMovie> currentWatchedMovieList = new ArrayList<>();
                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        String slug = snapshot.child("slug").getValue(String.class);
                        Long addTime = snapshot.child("addTime").getValue(Long.class);
                        WatchedMovie watchedMovie = new WatchedMovie(slug, addTime);
                        currentWatchedMovieList.add(watchedMovie);
                    }
                    sortWatchedListByAddTime(currentWatchedMovieList);
                    if (!watchedMovieListsAreEqual(currentWatchedMovieList, previousWatchedMovieList)) {
                        watchedMovieList.clear();
                        watchedMovieList.addAll(currentWatchedMovieList);
                        sendRequestSearchMovies();
                        previousWatchedMovieList.clear();
                        previousWatchedMovieList.addAll(currentWatchedMovieList);
                    } else {
                        loading1.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    loading1.setVisibility(View.GONE);
                    Log.e("HistoryActivity", "Database Error: " + databaseError.getMessage());
                }
            });
        } else {
            loading1.setVisibility(View.GONE);
            Log.e("HistoryActivity", "Current user is null");
        }
    }

    private boolean watchedMovieListsAreEqual(List<WatchedMovie> list1, List<WatchedMovie> list2) {
        // Kiểm tra xem hai danh sách có bằng nhau không
        if (list1.size() != list2.size()) {
            return false;
        }
        for (int i = 0; i < list1.size(); i++) {
            if (!list1.get(i).equals(list2.get(i))) {
                return false;
            }
        }
        return true;
    }

    private void sortWatchedListByAddTime(List<WatchedMovie> watchedMovieList) {
        Collections.sort(watchedMovieList, new Comparator<WatchedMovie>() {
            @Override
            public int compare(WatchedMovie movie1, WatchedMovie movie2) {
                return movie2.getAddTime().compareTo(movie1.getAddTime());
            }
        });
    }

    private void sendRequestSearchMovies() {
        mRequestQueue = Volley.newRequestQueue(this);
        loading1.setVisibility(View.VISIBLE);
        for (WatchedMovie watchedMovie : watchedMovieList) {
            String url = "https://phimapi.com/v1/api/tim-kiem?keyword=" + watchedMovie.getSlug() + "&limit=1";
            StringRequest stringRequest = new StringRequest(Request.Method.GET, url, response -> {
                Gson gson = new Gson();
                loading1.setVisibility(View.GONE);
                SearchMovie newData = gson.fromJson(response, SearchMovie.class);
                if (adapterSearchMovies == null) {
                    adapterSearchMovies = new SearchAdapter(newData);
                    recyclerviewSearchMovies.setAdapter(adapterSearchMovies);
                } else {
                    ((SearchAdapter) adapterSearchMovies).addData(newData);
                }
            }, error -> {
                loading1.setVisibility(View.GONE);
                Log.i("UILover", "onErrorResponse: " + error.toString());
            });
            mRequestQueue.add(stringRequest);
        }
    }

    private void initView() {
        recyclerviewSearchMovies = findViewById(R.id.HistoryListView);
        recyclerviewSearchMovies.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        loading1 = findViewById(R.id.progressBar1);
    }
}