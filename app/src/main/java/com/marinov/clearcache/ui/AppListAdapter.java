package com.marinov.clearcache.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.marinov.clearcache.R;
import com.marinov.clearcache.data.AppInfo;
import java.util.List;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.AppViewHolder> {
    private final List<AppInfo> appList;

    public AppListAdapter(List<AppInfo> appList) {
        this.appList = appList;
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_app, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppInfo appInfo = appList.get(position);
        holder.appName.setText(appInfo.getAppName());
        holder.appIcon.setImageDrawable(appInfo.getIcon());
        holder.appPackage.setText(appInfo.getPackageName());

        holder.appCheckbox.setOnCheckedChangeListener(null);
        holder.appCheckbox.setChecked(appInfo.isChecked());
        holder.appCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> appInfo.setChecked(isChecked));
        holder.itemView.setOnClickListener(v -> holder.appCheckbox.setChecked(!holder.appCheckbox.isChecked()));
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    public void updateList(List<AppInfo> newList) {
        appList.clear();
        appList.addAll(newList);
        notifyDataSetChanged();
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        ImageView appIcon;
        TextView appName;
        TextView appPackage;
        CheckBox appCheckbox;

        AppViewHolder(@NonNull View itemView) {
            super(itemView);
            appIcon = itemView.findViewById(R.id.app_icon);
            appName = itemView.findViewById(R.id.app_name);
            appPackage = itemView.findViewById(R.id.app_package);
            appCheckbox = itemView.findViewById(R.id.app_checkbox);
        }
    }
}