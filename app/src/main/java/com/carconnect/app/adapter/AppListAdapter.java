package com.carconnect.app.adapter;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.carconnect.app.R;
import com.carconnect.app.model.AppInfo;

import java.util.ArrayList;
import java.util.List;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {

    public interface OnAppClickListener {
        void onAppClick(AppInfo app);
    }

    private List<AppInfo> apps = new ArrayList<>();
    private final OnAppClickListener listener;
    /** 当前已绑定的包名，用于高亮显示 */
    private String boundPackage = "";

    public AppListAdapter(OnAppClickListener listener) {
        this.listener = listener;
    }

    public void setBoundPackage(String pkg) {
        this.boundPackage = pkg == null ? "" : pkg;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo app = apps.get(position);
        holder.tvName.setText(app.getAppName());
        holder.tvPackage.setText(app.getPackageName());
        if (app.getIcon() != null) {
            holder.ivIcon.setImageDrawable(app.getIcon());
        }

        boolean isBound = app.getPackageName().equals(boundPackage);
        Context ctx = holder.itemView.getContext();
        if (isBound) {
            // 绑定状态：背景轻微高亮，名称使用主题主色
            holder.itemView.setBackgroundColor(0x2200AA44);
            // 读取主题 colorPrimary 作为绑定名称颜色
            TypedValue tv = new TypedValue();
            ctx.getTheme().resolveAttribute(android.R.attr.colorPrimary, tv, true);
            holder.tvName.setTextColor(tv.data != 0 ? tv.data : 0xFF1565C0);
        } else {
            holder.itemView.setBackgroundColor(0);
            // 使用主题 textColorPrimary
            TypedValue tv = new TypedValue();
            ctx.getTheme().resolveAttribute(android.R.attr.textColorPrimary, tv, true);
            if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT
                    && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                holder.tvName.setTextColor(tv.data);
            } else {
                // 若是 ColorStateList 引用，直接让 XML 属性生效（不手动设置）
                holder.tvName.setTextAppearance(android.R.style.TextAppearance);
                holder.tvName.setTextColor(
                        ctx.getResources().getColorStateList(tv.resourceId, ctx.getTheme()));
            }
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onAppClick(app);
        });
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    public void setApps(List<AppInfo> newApps) {
        apps.clear();
        if (newApps != null) apps.addAll(newApps);
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName, tvPackage;

        ViewHolder(View v) {
            super(v);
            ivIcon    = v.findViewById(R.id.iv_app_icon);
            tvName    = v.findViewById(R.id.tv_app_name);
            tvPackage = v.findViewById(R.id.tv_package_name);
        }
    }
}
