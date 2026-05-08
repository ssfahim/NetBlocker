package com.netblocker.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.netblocker.R;
import com.netblocker.models.AppInfo;
import com.netblocker.utils.NetworkUtils;

import java.util.ArrayList;
import java.util.List;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {

    public interface OnAppToggleListener {
        void onAppToggled(AppInfo app, boolean blocked);
        void onAppClicked(AppInfo app);
    }

    private List<AppInfo> apps;
    private List<AppInfo> filteredApps;
    private OnAppToggleListener listener;
    private String searchQuery = "";

    public AppListAdapter(List<AppInfo> apps, OnAppToggleListener listener) {
        this.apps = apps;
        this.filteredApps = new ArrayList<>(apps);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo app = filteredApps.get(position);

        holder.appIcon.setImageDrawable(app.getIcon());
        holder.appName.setText(app.getAppName());
        holder.packageName.setText(app.getPackageName());

        // Show data usage if available
        if (app.getDataUsage() > 0) {
            holder.dataUsage.setVisibility(View.VISIBLE);
            holder.dataUsage.setText(NetworkUtils.formatBytes(app.getDataUsage()));
        } else {
            holder.dataUsage.setVisibility(View.GONE);
        }

        // Show system app badge
        if (app.isSystemApp()) {
            holder.systemBadge.setVisibility(View.VISIBLE);
        } else {
            holder.systemBadge.setVisibility(View.GONE);
        }

        // Block toggle - set without triggering listener
        holder.blockToggle.setOnCheckedChangeListener(null);
        holder.blockToggle.setChecked(app.isBlocked());
        holder.blockToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            app.setBlocked(isChecked);
            if (listener != null) {
                listener.onAppToggled(app, isChecked);
            }
        });

        // Row click for details
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAppClicked(app);
            }
        });

        // Visual feedback for blocked state
        holder.itemView.setAlpha(app.isBlocked() ? 0.7f : 1.0f);
        holder.blockedIndicator.setVisibility(app.isBlocked() ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemCount() {
        return filteredApps.size();
    }

    public void updateApps(List<AppInfo> newApps) {
        this.apps = newApps;
        filter(searchQuery);
    }

    public void filter(String query) {
        this.searchQuery = query.toLowerCase().trim();
        filteredApps.clear();

        if (searchQuery.isEmpty()) {
            filteredApps.addAll(apps);
        } else {
            for (AppInfo app : apps) {
                if (app.getAppName().toLowerCase().contains(searchQuery) ||
                        app.getPackageName().toLowerCase().contains(searchQuery)) {
                    filteredApps.add(app);
                }
            }
        }
        notifyDataSetChanged();
    }

    public int getBlockedCount() {
        int count = 0;
        for (AppInfo app : apps) {
            if (app.isBlocked()) count++;
        }
        return count;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView appIcon;
        TextView appName;
        TextView packageName;
        TextView dataUsage;
        TextView systemBadge;
        MaterialSwitch blockToggle;
        View blockedIndicator;

        ViewHolder(View itemView) {
            super(itemView);
            appIcon = itemView.findViewById(R.id.app_icon);
            appName = itemView.findViewById(R.id.app_name);
            packageName = itemView.findViewById(R.id.package_name);
            dataUsage = itemView.findViewById(R.id.data_usage);
            systemBadge = itemView.findViewById(R.id.system_badge);
            blockToggle = itemView.findViewById(R.id.block_toggle);
            blockedIndicator = itemView.findViewById(R.id.blocked_indicator);
        }
    }
}
