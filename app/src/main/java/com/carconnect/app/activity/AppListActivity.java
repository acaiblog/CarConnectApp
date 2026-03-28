package com.carconnect.app.activity;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.carconnect.app.R;
import com.carconnect.app.adapter.AppListAdapter;
import com.carconnect.app.model.AppInfo;
import com.carconnect.app.utils.LogManager;
import com.carconnect.app.utils.SharedPrefsManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AppListActivity extends AppCompatActivity {

    private static final String TAG = "AppList";

    private RecyclerView rvApps;
    private EditText etSearch;
    private ProgressBar progressBar;
    private TextView tvCount;
    private AppListAdapter adapter;

    private List<AppInfo> allApps = new ArrayList<>();
    private List<AppInfo> filteredApps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (SharedPrefsManager.isWhiteTheme()) setTheme(R.style.AppThemeWhite);
        setContentView(R.layout.activity_app_list);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle("绑定应用");

        rvApps      = findViewById(R.id.rv_apps);
        etSearch    = findViewById(R.id.et_search_app);
        progressBar = findViewById(R.id.progress_apps);
        tvCount     = findViewById(R.id.tv_app_count);

        adapter = new AppListAdapter(app -> onAppClicked(app));
        adapter.setBoundPackage(SharedPrefsManager.getBoundAppPackage());

        rvApps.setLayoutManager(new LinearLayoutManager(this));
        rvApps.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterApps(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // 异步加载应用列表
        new LoadAppsTask().execute();
    }

    /** 点击应用：弹出确认对话框绑定 */
    private void onAppClicked(AppInfo app) {
        String currentBound = SharedPrefsManager.getBoundAppPackage();
        if (app.getPackageName().equals(currentBound)) {
            // 再次点击已绑定的应用 → 解绑
            new AlertDialog.Builder(this)
                    .setTitle("解除绑定")
                    .setMessage("解除与 " + app.getAppName() + " 的绑定？\n解绑后连接成功将不会自动启动应用。")
                    .setPositiveButton("解除绑定", (d, w) -> {
                        SharedPrefsManager.clearBoundApp();
                        adapter.setBoundPackage("");
                        Toast.makeText(this, "已解除绑定", Toast.LENGTH_SHORT).show();
                        LogManager.i(TAG, "解除绑定: " + app.getPackageName());
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } else {
            // 绑定新应用
            new AlertDialog.Builder(this)
                    .setTitle("绑定应用")
                    .setMessage("将 [" + app.getAppName() + "] 设为绑定应用？\n\n蓝牙和Wi-Fi同时连接成功后将自动启动此应用。")
                    .setPositiveButton("绑定", (d, w) -> {
                        SharedPrefsManager.saveBoundApp(app.getPackageName(), app.getAppName());
                        adapter.setBoundPackage(app.getPackageName());
                        Toast.makeText(this, "已绑定: " + app.getAppName(), Toast.LENGTH_SHORT).show();
                        LogManager.i(TAG, "绑定应用: " + app.getAppName() + " (" + app.getPackageName() + ")");
                    })
                    .setNegativeButton("取消", null)
                    .show();
        }
    }

    private void filterApps(String query) {
        filteredApps.clear();
        for (AppInfo app : allApps) {
            // 只显示用户可启动应用（排除系统应用）
            if (app.isSystemApp()) continue;
            if (query == null || query.isEmpty()) {
                filteredApps.add(app);
            } else {
                String q = query.toLowerCase();
                if (app.getAppName().toLowerCase().contains(q)
                        || app.getPackageName().toLowerCase().contains(q)) {
                    filteredApps.add(app);
                }
            }
        }
        adapter.setApps(filteredApps);
        tvCount.setText("共 " + filteredApps.size() + " 个应用");
    }

    private class LoadAppsTask extends AsyncTask<Void, Void, List<AppInfo>> {

        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(android.view.View.VISIBLE);
            tvCount.setText("加载中...");
        }

        @Override
        protected List<AppInfo> doInBackground(Void... voids) {
            List<AppInfo> result = new ArrayList<>();
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

            for (ApplicationInfo appInfo : apps) {
                try {
                    String name = pm.getApplicationLabel(appInfo).toString();
                    boolean isSystem = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    android.graphics.drawable.Drawable icon = pm.getApplicationIcon(appInfo);
                    // 只列出有 Launcher Intent 的应用（可启动的）
                    if (pm.getLaunchIntentForPackage(appInfo.packageName) != null) {
                        result.add(new AppInfo(name, appInfo.packageName, icon, isSystem));
                    }
                } catch (Exception ignored) {}
            }

            Collections.sort(result, (a, b) -> a.getAppName().compareToIgnoreCase(b.getAppName()));

            return result;
        }

        @Override
        protected void onPostExecute(List<AppInfo> result) {
            progressBar.setVisibility(android.view.View.GONE);
            allApps = result;
            filterApps(etSearch.getText().toString());
            LogManager.i(TAG, "共加载 " + result.size() + " 个应用");
        }
    }
}
