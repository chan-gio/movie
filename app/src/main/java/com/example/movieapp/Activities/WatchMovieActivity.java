package com.example.movieapp.Activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;
import com.example.movieapp.Adapters.EpisodeAdapter;
import com.example.movieapp.Domain.movieDetail.Episode;
import com.example.movieapp.Domain.movieDetail.LinkFilm;
import com.example.movieapp.Domain.movieDetail.ServerDatum;
import com.example.movieapp.R;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;


import java.util.HashMap;
import java.util.List;

public class WatchMovieActivity extends AppCompatActivity {
    private ProgressBar progressBar;
    private ExoPlayer player;
    private TextView titleTxt, oMovieName;
    private String idFilm, tap, movieType;
    private ImageView pic2;
    private ScrollView scrollView;
    private Context mContext;
    private PlayerView playerView;
    private HlsMediaSource.Factory mediaSourceFactory;
    ImageView bt_fullscreen;
    boolean isFullScreen=false;
    boolean isLock = false;
    private int originalPlayerViewHeight;
    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch_movie);
        TextView tvTap = findViewById(R.id.tvTap);
        PlayerView playerView = findViewById(R.id.videoView2);
        ProgressBar progressBar = findViewById(R.id.progressBarWatch);
        bt_fullscreen = findViewById(R.id.bt_fullscreen);
        ImageView bt_lockscreen = findViewById(R.id.exo_lock);

        bt_fullscreen.setOnClickListener(view -> {
            isFullScreen = !isFullScreen;
            adjustPlayerViewSize(isFullScreen);

            if (isFullScreen) {
                bt_fullscreen.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_baseline_fullscreen_exit));
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                ((LockedScrollViewActivity) scrollView).setFullScreen(true);
            } else {
                bt_fullscreen.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_baseline_fullscreen));
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                ((LockedScrollViewActivity) scrollView).setFullScreen(false);
            }
        });

        bt_lockscreen.setOnClickListener(view -> {
            //change icon base on toggle lock screen or unlock screen
            if (!isLock)
            {
                bt_lockscreen.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_baseline_lock));
            } else
            {
                bt_lockscreen.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_outline_lock_open));
            }
            isLock = !isLock;
            //method for toggle will do next
            lockScreen(isLock);
        });

        player = new ExoPlayer.Builder(this)
                .setSeekBackIncrementMs(5000)
                .setSeekForwardIncrementMs(5000)
                .build();
        playerView.setPlayer(player);
        playerView.setKeepScreenOn(true);

        tap = getIntent().getStringExtra("tap");
        idFilm = getIntent().getStringExtra("slug");
        movieType = getIntent().getStringExtra("movieType");
        tvTap.setText(tap);
        initView();
        initializePlayerComponents();
        sendRequest();
    }

    private void saveWatchedMovie(TextView titleTxt, String userId, String tap, String movieType) {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference().child("users").child(userId);
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean isAlreadySaved = false;
                if (snapshot.hasChild("watchedMovies")) {
                    for (DataSnapshot movieSnapshot : snapshot.child("watchedMovies").getChildren()) {
                        String existingSlug = movieSnapshot.child("slug").getValue(String.class);
                        if (existingSlug != null && existingSlug.equals(titleTxt.getText().toString())) {
                            isAlreadySaved = true;
                            movieSnapshot.getRef().child("addTime").setValue(ServerValue.TIMESTAMP);
                            break;
                        }
                    }
                }
                if (!isAlreadySaved) {
                    String movieNodeKey = userRef.child("watchedMovies").push().getKey();
                    if (!TextUtils.isEmpty(movieNodeKey)) {
                        HashMap<String, Object> movieData = new HashMap<>();
                        movieData.put("slug", titleTxt.getText().toString());
                        movieData.put("addTime", ServerValue.TIMESTAMP);
                        if (movieType != null && (movieType.equals("series") || movieType.equals("hoathinh") || movieType.equals("tvshows"))) {
                            movieData.put("tap", tap);
                        }
                        userRef.child("watchedMovies").child(movieNodeKey).setValue(movieData);
                        Toast.makeText(WatchMovieActivity.this, "Lưu thành công", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(WatchMovieActivity.this, "Không lưu được dữ liệu", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(WatchMovieActivity.this, "Phim đã được lưu trước đó", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w("saveWatchedMovie", "Error saving data", error.toException());
            }
        });
    }

    private void initializePlayerComponents() {
        player = new SimpleExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        // Khởi tạo factory cho dataSource
        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this, Util.getUserAgent(this, "YourApplicationName"));

        // Khởi tạo factory cho tệp m3u8 (HLS)
        mediaSourceFactory = new HlsMediaSource.Factory(dataSourceFactory);
    }
    private void initializePlayer(Uri videoUri) {
        // Tạo một MediaItem từ Uri của videoUri
        MediaItem mediaItem = MediaItem.fromUri(videoUri);

        // Tạo một HlsMediaSource từ MediaItem đã tạo và HlsMediaSource.Factory đã khởi tạo trước đó
        HlsMediaSource mediaSource = mediaSourceFactory.createMediaSource(mediaItem);

        // Chuẩn bị ExoPlayer với HlsMediaSource
        player.setMediaSource(mediaSource);

        // Bắt đầu phát video khi đã chuẩn bị xong
        player.prepare();
    }

    // Hàm để điều chỉnh kích thước của PlayerView
    private void adjustPlayerViewSize(boolean isFullScreen) {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) playerView.getLayoutParams();
        if (isFullScreen) {
            originalPlayerViewHeight = params.height;
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            int screenWidth = displayMetrics.widthPixels;
            params.height = screenWidth;
            hideSystemUI();

            scrollView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    scrollView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    // Cuộn đến vị trí của PlayerView
                    int[] location = new int[2];
                    playerView.getLocationOnScreen(location);
                    scrollView.scrollTo(0, location[1]);
                }
            });
        } else {
            params.height = originalPlayerViewHeight;
            showSystemUI();
        }
        playerView.setLayoutParams(params);
    }

    // Hàm để ẩn thanh trạng thái và thanh điều hướng của hệ thống
    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    // Hàm để hiện lại thanh trạng thái và thanh điều hướng của hệ thống
    private void showSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    void lockScreen(boolean lock)
    {
        //just hide the control for lock screen and vise versa
        LinearLayout sec_mid = findViewById(R.id.sec_controlvid1);
        LinearLayout sec_bottom = findViewById(R.id.sec_controlvid2);
        if(lock)
        {
            sec_mid.setVisibility(View.INVISIBLE);
            sec_bottom.setVisibility(View.INVISIBLE);
        }
        else
        {
            sec_mid.setVisibility(View.VISIBLE);
            sec_bottom.setVisibility(View.VISIBLE);
        }
    }

    //when is in lock screen we not accept for backpress button
    @Override
    public void onBackPressed()
    {
        //on lock screen back press button not work
        if(isLock) return;

        //if user is in landscape mode we turn to portriat mode first then we can exit the app.
        if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
        {
            bt_fullscreen.performClick();
        }
        else super.onBackPressed();
    }

    // pause or release the player prevent memory leak
    @Override
    protected void onStop()
    {
        super.onStop();
        player.stop();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        player.release();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        player.pause();
    }

    private void sendRequest() {
        RequestQueue mRequestQueue = Volley.newRequestQueue(this);
        progressBar.setVisibility(View.VISIBLE);
        scrollView.setVisibility(View.GONE);
        StringRequest mStringRequest = new StringRequest(Request.Method.GET, "https://phimapi.com/phim/" + idFilm, response -> {
            Gson gson = new Gson();
            progressBar.setVisibility(View.GONE);
            scrollView.setVisibility(View.VISIBLE);

            LinkFilm item = gson.fromJson(response, LinkFilm.class);

            Glide.with(WatchMovieActivity.this)
                    .load(item.getMovie().getPosterUrl())
                    .into(pic2);
            movieType = item.getMovie().getType();
            titleTxt.setText(item.getMovie().getName());
            oMovieName.setText(item.getMovie().getOriginName());

            List<Episode> episodes = item.getEpisodes();

            boolean foundLinkEmbed = false;
            if (item.getMovie().getType().equals("series") || item.getMovie().getType().equals("hoathinh") || item.getMovie().getType().equals("tvshows")) {
                if (episodes != null && !episodes.isEmpty()) {
                    for (Episode episode : episodes) {
                        List<ServerDatum> serverDataList = episode.getServerData();
                        if (serverDataList != null && !serverDataList.isEmpty()) {
                            for (ServerDatum serverDatum : serverDataList) {
                                if (serverDatum.getName().equalsIgnoreCase(tap)) {
                                    String linkM3u8 = serverDatum.getLinkM3u8();
                                    if (linkM3u8 != null && !linkM3u8.isEmpty()) {
                                        Uri videoUri = Uri.parse(linkM3u8);
                                        initializePlayer(videoUri);
                                        foundLinkEmbed = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (foundLinkEmbed) {
                            break;
                        }
                    }
                }
                List<Episode> Cepisodes = item.getEpisodes();
                if (Cepisodes != null && !Cepisodes.isEmpty()) {
                    RecyclerView episodeRecyclerView = findViewById(R.id.episodeRecyclerView);
                    EpisodeAdapter episodeAdapter = new EpisodeAdapter(this, Cepisodes, idFilm);
                    episodeRecyclerView.setLayoutManager(new LinearLayoutManager(this));
                    episodeRecyclerView.setAdapter(episodeAdapter);
                }
                else {
                    RecyclerView episodeRecyclerView = findViewById(R.id.episodeRecyclerView);
                    episodeRecyclerView.setVisibility(View.GONE);
                }
            }
            else {
                if (episodes != null && !episodes.isEmpty()) {
                    for (Episode episode : episodes) {
                        List<ServerDatum> serverDataList = episode.getServerData();
                        if (serverDataList != null && !serverDataList.isEmpty()) {
                            for (ServerDatum serverDatum : serverDataList) {
                                String linkEmbed = serverDatum.getLinkM3u8();
                                if (linkEmbed != null) {
                                    Uri videoUri = Uri.parse(linkEmbed);
                                    initializePlayer(videoUri);
                                    foundLinkEmbed = true;
                                    break;
                                }
                            }
                        }
                        if (foundLinkEmbed) {
                            break;
                        }
                    }
                }
                TextView textView = findViewById(R.id.episodeCountTextView);
                textView.setVisibility(View.GONE);
            }
            if (!foundLinkEmbed) {
                showAlertDialog("Không tìm thấy link_embed phù hợp với slug");
            }
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            String userId = null;
            if (currentUser != null) {
                userId = currentUser.getUid();
            }
            saveWatchedMovie(titleTxt, userId, tap, movieType);
        }, error -> {
            progressBar.setVisibility(View.GONE);
            showAlertDialog("Đã xảy ra lỗi khi truy cập dữ liệu từ máy chủ");
        });
        mRequestQueue.add(mStringRequest);
    }

    private void showAlertDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, id) -> dialog.dismiss());
        AlertDialog alert = builder.create();
        alert.show();
    }

    private void initView() {
        titleTxt = findViewById(R.id.movieName);
        progressBar = findViewById(R.id.progressBarWatch);
        scrollView = findViewById(R.id.scrollViewW);
        pic2 = findViewById(R.id.watchImage);
        oMovieName = findViewById(R.id.originalMovieName);
        playerView = findViewById(R.id.videoView2);
        ImageView backImg = findViewById(R.id.backimg);
        backImg.setOnClickListener(v -> finish());
    }
}

