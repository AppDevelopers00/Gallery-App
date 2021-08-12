package com.example.galleryapp.activities;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.galleryapp.ApiCalls;
import com.example.galleryapp.App;
import com.example.galleryapp.ImagesViewModel;
import com.example.galleryapp.R;
import com.example.galleryapp.classes.FireBaseCount;
import com.example.galleryapp.databinding.ActivityMainBinding;
import com.example.galleryapp.fragments.FavoritesFragment;
import com.example.galleryapp.fragments.FoldersFragment;
import com.example.galleryapp.fragments.HomeFragment;
import com.example.galleryapp.fragments.RecentFragment;
import com.example.galleryapp.fragments.SettingsFragment;
import com.google.android.material.navigation.NavigationBarView;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private DatabaseReference databaseReference;
    private List<FireBaseCount> list = new ArrayList<>();
    private RequestQueue queue;
    private String TAG = "Drive";
    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;
    private FireBaseCount fireBaseCount = new FireBaseCount();
    private ProgressDialog progressDialog;
    private ApiCalls apiCalls ;
    private ImagesViewModel imagesViewModel;
    private Context context = MainActivity.this;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        App.setContext(MainActivity.this);

        this.imagesViewModel = new ViewModelProvider(this).get(ImagesViewModel.class);
        imagesViewModel.initializeModel();

        apiCalls = new ApiCalls(MainActivity.this);
        apiCalls.get_bearer_token();
        sharedPreferences = getSharedPreferences("Drive", Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();

        if (sharedPreferences.getString("firstLogin","yes").equalsIgnoreCase("yes"))
        {
            editor.putString("firstLogin","no");
            editor.commit();
        }


        if ( !sharedPreferences.getString("fetch","").equalsIgnoreCase("no") ) {

             
            fetchingAllPhotos();
            editor.putString("fetch","no");
        }
        binding.bottomNavigationView.setSelectedItemId(R.id.navigation_home);
        binding.bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull @NotNull MenuItem item) {
                switch (item.getItemId()) {

                    case R.id.navigation_favourite:
                        getSupportFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, new FavoritesFragment(MainActivity.this))
                                .commit();
                        editor.putInt("FragmentId",R.id.navigation_favourite).commit();
                        break;
                    case R.id.navigation_recent:
                        getSupportFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, new RecentFragment())
                                .commit();

                        editor.putInt("FragmentId",R.id.navigation_recent).commit();
                        break;
                    case R.id.navigation_home:
                        getSupportFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, new HomeFragment(MainActivity.this))
                                .commit();
                        editor.putInt("FragmentId",R.id.navigation_home).commit();
                        break;
                    case R.id.navigation_files:
                        getSupportFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, new FoldersFragment(MainActivity.this, getLayoutInflater(), getApplication()))
                                .commit();
                        editor.putInt("FragmentId",R.id.navigation_files).commit();
                        break;
                    case R.id.navigation_setting:
                        getSupportFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, new SettingsFragment())
                                .commit();
                        editor.putInt("FragmentId",R.id.navigation_setting).commit();
                        break;
                }
                return true;
            }
        });

    }

    private void fetchingAllPhotos() {

        progressDialog = new ProgressDialog(MainActivity.this);
        progressDialog.setTitle("Fetching all photos from drive");
        progressDialog.setMessage("This May take some time but will occur only once .");
        progressDialog.show();

        queue = Volley.newRequestQueue(MainActivity.this);
        this.databaseReference = FirebaseDatabase.getInstance().getReference("Photos"+"Tarun");
        pngFileSearch();
    }

    private void uploadToFirebase(FireBaseCount fireBaseCount) {

        if (!sharedPreferences.getString("clear","").equalsIgnoreCase("done"))
        {
            databaseReference.removeValue();
            editor.putString("clear","done");
            editor.commit();
        }
        databaseReference.child(fireBaseCount.getId()).setValue(fireBaseCount);
    }

    private void pngFileSearch() {

        StringRequest request = new StringRequest(Request.Method.GET, "https://www.googleapis.com/drive/v3/files?fields=kind,incompleteSearch,nextPageToken, files(id, name,webContentLink,parents)&q=mimeType='image/png'",

                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.d(TAG, response);
                        try {
                            JSONObject jsonObject = new JSONObject(response);
                            JSONArray jsonArray = jsonObject.getJSONArray("files");

                            for (int i = 0; i < jsonArray.length(); i++) {
                                fireBaseCount.setId(jsonArray.getJSONObject(i).get("id").toString());
                                fireBaseCount.setName(jsonArray.getJSONObject(i).get("name").toString());

                                fireBaseCount.setUrl(jsonArray.getJSONObject(i).get("webContentLink").toString());
                                try {
                                    fireBaseCount.setParentsId(jsonArray.getJSONObject(i).get("parents").toString());
                                }catch (JSONException e)
                                {
                                    fireBaseCount.setParentsId("Drive");
                                }

                                list.add(fireBaseCount);
                                uploadToFirebase(fireBaseCount);
                            }
                            jpgFileSearch();

                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d(TAG, "Error : " + error.toString());
            }
        }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {

                Map<String, String> map = new HashMap<String, String>();
                map.put("Authorization", "Bearer " + sharedPreferences.getString("bearer token", ""));
                return map;
            }
        };
        queue.add(request);
    }

    private void jpgFileSearch() {

        StringRequest request = new StringRequest(Request.Method.GET, "https://www.googleapis.com/drive/v3/files?fields=kind,incompleteSearch,nextPageToken, files(id, name,webContentLink,parents)&q=mimeType='image/jpg'",

                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {

                        Log.d(TAG, response);
                        try {
                            JSONObject jsonObject = new JSONObject(response);
                            JSONArray jsonArray = jsonObject.getJSONArray("files");

                            for (int i = 0; i < jsonArray.length(); i++) {
                                fireBaseCount.setId(jsonArray.getJSONObject(i).get("id").toString());
                                fireBaseCount.setName(jsonArray.getJSONObject(i).get("name").toString());

                                fireBaseCount.setUrl(jsonArray.getJSONObject(i).get("webContentLink").toString());
                                try {
                                    fireBaseCount.setParentsId(jsonArray.getJSONObject(i).get("parents").toString());
                                }catch (JSONException e)
                                {
                                    fireBaseCount.setParentsId("Drive");
                                }

                                list.add(fireBaseCount);
                                uploadToFirebase(fireBaseCount);
                            }
                            jpegFileSearch();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d(TAG, "Error : " + error.toString());
            }
        }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {

                Map<String, String> map = new HashMap<String, String>();
                map.put("Authorization", "Bearer " + sharedPreferences.getString("bearer token", ""));
                return map;
            }
        };
        queue.add(request);
    }

    private void jpegFileSearch() {

        StringRequest request = new StringRequest(Request.Method.GET, "https://www.googleapis.com/drive/v3/files?fields=kind,incompleteSearch,nextPageToken, files(id, name,webContentLink,parents)&q=mimeType='image/jpeg'",

                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {

                        Log.d(TAG, response);
                        try {
                            JSONObject jsonObject = new JSONObject(response);
                            JSONArray jsonArray = jsonObject.getJSONArray("files");

                            for (int i = 0; i < jsonArray.length(); i++) {
                                fireBaseCount.setId(jsonArray.getJSONObject(i).get("id").toString());
                                fireBaseCount.setName(jsonArray.getJSONObject(i).get("name").toString());

                                fireBaseCount.setUrl(jsonArray.getJSONObject(i).get("webContentLink").toString());
                                try {
                                    fireBaseCount.setParentsId(jsonArray.getJSONObject(i).get("parents").toString());
                                }catch (JSONException e)
                                {
                                    fireBaseCount.setParentsId("Drive");
                                }

                                list.add(fireBaseCount);
                                uploadToFirebase(fireBaseCount);
                            }
                            progressDialog.dismiss();
                            Toast.makeText(MainActivity.this, "Profile fetching completed", Toast.LENGTH_SHORT).show();

                            Thread.sleep(1000);
                            getSupportFragmentManager().beginTransaction()
                                    .replace(R.id.fragment_container, new HomeFragment(MainActivity.this))
                                    .commit();
                        } catch (JSONException | InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d(TAG, "Error : " + error.toString());
            }
        }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {

                Map<String, String> map = new HashMap<String, String>();
                map.put("Authorization", "Bearer " + sharedPreferences.getString("bearer token", ""));
                return map;
            }
        };
        queue.add(request);
    }

    @Override
    protected void onResume() {
        super.onResume();
        switch (sharedPreferences.getInt("FragmentId",0)){
            case R.id.navigation_favourite:
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new FavoritesFragment(MainActivity.this))
                        .commit();
                break;
            case R.id.navigation_recent:
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new RecentFragment())
                        .commit();
                break;
            case R.id.navigation_home:
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new HomeFragment(MainActivity.this))
                        .commit();
                break;
            case R.id.navigation_files:
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new FoldersFragment(MainActivity.this, getLayoutInflater(), getApplication()))
                        .commit();
                break;
            case R.id.navigation_setting:
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new SettingsFragment())
                        .commit();
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        editor.putString("fetch","yes");
        editor.putString("clear","");
        editor.putInt("FragmentId",0);
        editor.commit();
  
    }
}