package cz.petr.bezpecnevideo;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** One scrolling, recycling surface for headings, videos and pagination. */
final class VideoGallery extends RecyclerView {
    private final View header, footer;
    private final Consumer<VideoStore.Video> play, options;
    private final List<Item> videos = new ArrayList<>();
    private final Map<String, WatchHistory.Entry> progress = new HashMap<>();
    private List<WatchHistory.Entry> continuing = new ArrayList<>();
    private List<Row> rows = new ArrayList<>();
    private final Map<String, Long> stableIds = new HashMap<>();
    private long nextStableId = 1;
    private final GalleryAdapter adapter = new GalleryAdapter();
    private String focusKey;

    VideoGallery(Context context, View header, View footer, Consumer<VideoStore.Video> play,
                 Consumer<VideoStore.Video> options) {
        super(context);
        this.header = header; this.footer = footer; this.play = play; this.options = options;
        setPadding(dp(20), dp(12), dp(20), dp(20));
        setClipToPadding(false);
        setBackgroundColor(Color.rgb(14, 16, 21));
        setItemAnimator(null);
        setItemViewCacheSize(4);
        GridLayoutManager layout = new GridLayoutManager(context, 4);
        layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) { return rows.get(position).video == null ? 4 : 1; }
        });
        setLayoutManager(layout);
        setAdapter(adapter);
        rebuild();
    }

    void addVideos(List<VideoStore.Video> additions, int priority) {
        for (VideoStore.Video video : additions) {
            boolean exists = false;
            for (Item item : videos) if (item.video.id.equals(video.id)) { exists = true; break; }
            if (!exists) videos.add(new Item(video, priority));
        }
        videos.sort((a, b) -> Integer.compare(a.priority, b.priority));
        rebuild();
    }

    void history(List<WatchHistory.Entry> all, List<WatchHistory.Entry> allowedContinuing) {
        progress.clear();
        for (WatchHistory.Entry entry : all) progress.put(entry.video.id, entry);
        continuing = new ArrayList<>(allowedContinuing.subList(0, Math.min(8, allowedContinuing.size())));
        rebuild();
    }

    void focusVideo(String id) {
        focusKey = "video:" + id;
        for (int i = 0; i < rows.size(); i++) if (focusKey.equals(rows.get(i).key)) {
            scrollToPosition(i);
            final int position = i;
            post(() -> {
                ViewHolder holder = findViewHolderForAdapterPosition(position);
                if (holder != null && focusKey != null) { holder.itemView.requestFocus(); focusKey = null; }
            });
            return;
        }
        focusKey = null;
    }

    private void rebuild() {
        List<Row> next = new ArrayList<>();
        next.add(new Row("header", null, "", null));
        if (!continuing.isEmpty()) {
            next.add(new Row("continueTitle", null, "Pokračovat ve sledování", null));
            for (WatchHistory.Entry entry : continuing)
                next.add(new Row("continue:" + entry.video.id, entry.video,
                        "Pokračovat od " + time(entry.position), entry));
        }
        next.add(new Row("allTitle", null, "Videa z povolených kanálů", null));
        for (Item item : videos) {
            WatchHistory.Entry entry = progress.get(item.video.id);
            String[] title = item.video.title.split("  •  ", 2);
            String subtitle = entry == null ? (title.length > 1 ? title[1] : "")
                    : entry.completed ? "Zhlédnuto" : "Pokračovat od " + time(entry.position);
            next.add(new Row("video:" + item.video.id, item.video, subtitle, entry));
        }
        next.add(new Row("footer", null, "", null));
        List<Row> previous = rows;
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            public int getOldListSize() { return previous.size(); }
            public int getNewListSize() { return next.size(); }
            public boolean areItemsTheSame(int oldPosition, int newPosition) {
                return previous.get(oldPosition).key.equals(next.get(newPosition).key);
            }
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                Row a = previous.get(oldPosition), b = next.get(newPosition);
                return a.subtitle.equals(b.subtitle) && a.percent == b.percent
                        && (a.video == null || a.video.title.equals(b.video.title));
            }
        });
        rows = next;
        diff.dispatchUpdatesTo(adapter);
    }

    private final class GalleryAdapter extends Adapter<Holder> {
        GalleryAdapter() { setHasStableIds(true); }
        @Override public long getItemId(int position) {
            String key = rows.get(position).key;
            Long id = stableIds.get(key);
            if (id == null) { id = nextStableId++; stableIds.put(key, id); }
            return id;
        }
        @Override public int getItemCount() { return rows.size(); }
        @Override public int getItemViewType(int position) {
            Row row = rows.get(position);
            if (row.key.equals("header")) return 0;
            if (row.key.equals("footer")) return 1;
            return row.video == null ? 2 : 3;
        }
        @Override public Holder onCreateViewHolder(ViewGroup parent, int type) {
            if (type == 0 || type == 1) {
                LinearLayout wrapper = new LinearLayout(getContext());
                wrapper.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
                View child = type == 0 ? header : footer;
                if (child.getParent() instanceof ViewGroup) ((ViewGroup) child.getParent()).removeView(child);
                wrapper.addView(child, new LinearLayout.LayoutParams(-1, -2));
                return new Holder(wrapper);
            }
            if (type == 2) {
                TextView heading = label(24);
                heading.setPadding(dp(7), dp(18), dp(7), dp(12));
                heading.setTypeface(null, Typeface.BOLD);
                heading.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
                return new Holder(heading);
            }
            LinearLayout card = new LinearLayout(getContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(7), dp(7), dp(7), dp(8));
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, -2);
            params.setMargins(dp(7), dp(7), dp(7), dp(12));
            card.setLayoutParams(params);
            card.setFocusable(true);
            card.setClickable(true);
            Holder holder = new Holder(card);
            holder.image = new ImageView(getContext());
            holder.image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            holder.image.setBackgroundColor(Color.rgb(45, 47, 52));
            int width = (getResources().getDisplayMetrics().widthPixels - dp(40)) / 4 - dp(28);
            card.addView(holder.image, new LinearLayout.LayoutParams(-1, Math.max(dp(60), width * 9 / 16)));
            holder.bar = new ProgressBar(getContext(), null, android.R.attr.progressBarStyleHorizontal);
            holder.bar.setMax(1000);
            holder.bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.RED));
            card.addView(holder.bar, new LinearLayout.LayoutParams(-1, dp(4)));
            holder.title = label(17);
            holder.title.setMaxLines(2);
            holder.title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(holder.title, new LinearLayout.LayoutParams(-1, dp(52)));
            holder.subtitle = label(13);
            holder.subtitle.setSingleLine(true);
            holder.subtitle.setTextColor(Color.LTGRAY);
            card.addView(holder.subtitle, new LinearLayout.LayoutParams(-1, dp(24)));
            card.setOnFocusChangeListener((v, focused) -> {
                v.setBackgroundColor(focused ? Color.rgb(92, 61, 10) : Color.rgb(28, 31, 38));
                v.animate().scaleX(focused ? 1.025f : 1).scaleY(focused ? 1.025f : 1).setDuration(100).start();
            });
            card.setOnClickListener(v -> { if (holder.video != null) play.accept(holder.video); });
            card.setOnLongClickListener(v -> { if (holder.video != null) options.accept(holder.video); return true; });
            return holder;
        }
        @Override public void onBindViewHolder(Holder holder, int position) {
            Row row = rows.get(position);
            if (row.video == null) {
                if (holder.itemView instanceof TextView) ((TextView) holder.itemView).setText(row.subtitle);
                return;
            }
            if (holder.video == null || !holder.video.id.equals(row.video.id)) ThumbnailLoader.load(holder.image, row.video.id);
            holder.video = row.video;
            holder.title.setText(row.video.title.split("  •  ", 2)[0]);
            holder.subtitle.setText(row.subtitle);
            holder.bar.setProgress(row.percent);
            holder.bar.setVisibility(row.percent > 0 ? View.VISIBLE : View.INVISIBLE);
            holder.itemView.setContentDescription(holder.title.getText() + ". " + row.subtitle);
            holder.itemView.setBackgroundColor(holder.itemView.hasFocus() ? Color.rgb(92, 61, 10) : Color.rgb(28, 31, 38));
            if (!holder.itemView.hasFocus()) { holder.itemView.setScaleX(1); holder.itemView.setScaleY(1); }
            if (row.key.equals(focusKey)) holder.itemView.post(() -> {
                if (row.key.equals(focusKey)) { holder.itemView.requestFocus(); focusKey = null; }
            });
        }
        @Override public void onViewRecycled(Holder holder) {
            if (holder.image != null) ThumbnailLoader.clear(holder.image);
            holder.video = null;
        }
    }

    private TextView label(int size) {
        TextView label = new TextView(getContext()); label.setTextColor(Color.WHITE); label.setTextSize(size); return label;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    static String time(double seconds) {
        int total = (int) seconds;
        return total >= 3600 ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", total / 3600, total / 60 % 60, total % 60)
                : String.format(java.util.Locale.ROOT, "%d:%02d", total / 60, total % 60);
    }
    private static final class Holder extends ViewHolder {
        ImageView image; TextView title, subtitle; ProgressBar bar; VideoStore.Video video;
        Holder(View view) { super(view); }
    }
    private static final class Item {
        final VideoStore.Video video; final int priority;
        Item(VideoStore.Video video, int priority) { this.video = video; this.priority = priority; }
    }
    private static final class Row {
        final String key, subtitle; final VideoStore.Video video; final int percent;
        Row(String key, VideoStore.Video video, String subtitle, WatchHistory.Entry entry) {
            this.key = key; this.video = video; this.subtitle = subtitle;
            percent = entry == null ? 0 : (int) Math.min(1000, entry.position / entry.duration * 1000);
        }
    }
}
