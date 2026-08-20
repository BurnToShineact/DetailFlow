package com.detailflow.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {
    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final int BLUE_DARK = Color.rgb(29, 78, 216);
    private static final int GREEN = Color.rgb(5, 150, 105);
    private static final int AMBER = Color.rgb(217, 119, 6);
    private static final int RED = Color.rgb(220, 38, 38);
    private static final int INK = Color.rgb(15, 23, 42);
    private static final int MUTED = Color.rgb(100, 116, 139);
    private static final int BORDER = Color.rgb(226, 232, 240);
    private static final int SURFACE = Color.WHITE;
    private static final int BACKGROUND = Color.rgb(248, 250, 252);

    private static final int REQ_CAMERA_BEFORE = 201;
    private static final int REQ_CAMERA_AFTER = 202;
    private static final int REQ_GALLERY_BEFORE = 211;
    private static final int REQ_GALLERY_AFTER = 212;
    private static final int REQ_EXPORT_DATABASE = 301;
    private static final int REQ_IMPORT_DATABASE = 302;
    private static final int MAX_DATABASE_ARCHIVE_BYTES = 20 * 1024 * 1024;

    private final Locale ru = new Locale("ru", "RU");
    private final NumberFormat moneyFormat = NumberFormat.getIntegerInstance(ru);
    private final SimpleDateFormat dayMonth = new SimpleDateFormat("d MMMM", ru);
    private final SimpleDateFormat shortDayMonth = new SimpleDateFormat("d MMM", ru);
    private final SimpleDateFormat monthYear = new SimpleDateFormat("LLLL yyyy", ru);
    private final SimpleDateFormat dateTime = new SimpleDateFormat("d MMM, HH:mm", ru);
    private final SimpleDateFormat time = new SimpleDateFormat("HH:mm", ru);
    private final SimpleDateFormat backupDate = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);

    private AppStore store;
    private UpdateManager updateManager;
    private FrameLayout content;
    private LinearLayout navigation;
    private String route = "today";
    private String photoOrderId;
    private Uri pendingCameraUri;
    private final Calendar calendarSelected = Calendar.getInstance();
    private String calendarMode = "day";
    private Runnable currentBackAction;
    private final Map<String, TextView> navLabels = new HashMap<>();
    private final Map<String, LinearLayout> navItems = new HashMap<>();
    private final Map<String, ImageView> navIcons = new HashMap<>();

    private static final class FinanceEntry {
        final Models.Transaction transaction;
        final Models.Order paidOrder;
        final long createdAt;

        FinanceEntry(Models.Transaction transaction) {
            this.transaction = transaction;
            this.paidOrder = null;
            this.createdAt = transaction.createdAt;
        }

        FinanceEntry(Models.Order paidOrder) {
            this.transaction = null;
            this.paidOrder = paidOrder;
            this.createdAt = paidOrder.startAt;
        }
    }

    private static final class NewOrderDraft {
        Models.Client selectedClient;
        String clientName = "";
        String phone = "";
        String car = "";
        String carModel = "";
        String plate = "";
        String note = "";
        boolean creatingClient;
        final List<String> serviceIds = new ArrayList<>();
        final Map<String, Long> servicePrices = new HashMap<>();
        final Calendar start = Calendar.getInstance();
        Runnable cancel;

        NewOrderDraft() {
            start.add(Calendar.HOUR_OF_DAY, 1);
            start.set(Calendar.MINUTE, 0);
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(BACKGROUND);
        window.setNavigationBarColor(SURFACE);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        store = new AppStore(this);
        updateManager = new UpdateManager(this);
        buildShell();
        showRoute("today");
        updateManager.maybeCheckAutomatically();
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets systemBars = insets.getInsets(WindowInsets.Type.systemBars());
                top = systemBars.top;
                bottom = systemBars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(0, top, 0, bottom);
            return insets;
        });

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setGravity(Gravity.CENTER);
        navigation.setPadding(dp(4), dp(4), dp(4), dp(4));
        navigation.setBackgroundColor(SURFACE);
        navigation.setElevation(dp(6));
        addNav("today", "Сегодня", R.drawable.ic_nav_today);
        addNav("calendar", "Календарь", R.drawable.ic_nav_calendar);
        addNav("orders", "Заказы", R.drawable.ic_nav_orders);
        addNav("finance", "Финансы", R.drawable.ic_nav_finance);
        addNav("more", "Ещё", R.drawable.ic_nav_more);
        root.addView(navigation, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));
        setContentView(root);
    }

    private void addNav(String key, String caption, int iconResource) {
        LinearLayout navItem = new LinearLayout(this);
        navItem.setOrientation(LinearLayout.VERTICAL);
        navItem.setGravity(Gravity.CENTER);
        navItem.setPadding(dp(4), dp(5), dp(4), dp(4));
        navItem.setMinimumHeight(dp(56));
        navItem.setClickable(true);
        navItem.setFocusable(true);
        navItem.setContentDescription("Открыть раздел «" + caption + "»");
        navItem.setOnClickListener(view -> showRoute(key));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResource);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        navItem.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));

        TextView label = text(caption, 11, MUTED, Typeface.NORMAL);
        label.setGravity(Gravity.CENTER);
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(3);
        navItem.addView(label, labelParams);

        navigation.addView(navItem, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        navItems.put(key, navItem);
        navLabels.put(key, label);
        navIcons.put(key, icon);
    }

    private void showRoute(String nextRoute) {
        route = nextRoute;
        showNavigationSelection(nextRoute);
        switch (nextRoute) {
            case "calendar": showCalendar(); break;
            case "orders": showOrders(); break;
            case "finance": showFinance(); break;
            case "more": showMore(); break;
            default: showToday();
        }
    }

    private void showNavigationSelection(String selectedRoute) {
        navigation.setVisibility(View.VISIBLE);
        for (Map.Entry<String, TextView> item : navLabels.entrySet()) {
            boolean selected = item.getKey().equals(selectedRoute);
            item.getValue().setTextColor(selected ? BLUE : MUTED);
            item.getValue().setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            navIcons.get(item.getKey()).setImageTintList(ColorStateList.valueOf(selected ? BLUE : MUTED));
            navItems.get(item.getKey()).setBackground(null);
        }
    }

    private void setPage(View page) {
        content.removeAllViews();
        content.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private LinearLayout page(String title, String subtitle, Runnable back) {
        return page(title, subtitle, back, null);
    }

    private LinearLayout page(String title, String subtitle, Runnable back, Runnable headerAction) {
        currentBackAction = back;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);

        FrameLayout bar = new FrameLayout(this);
        bar.setPadding(dp(20), dp(8), dp(20), dp(6));
        int barHeight = subtitle != null && !subtitle.isEmpty() ? 76 : 64;
        if (back != null) {
            TextView backView = text("‹", 36, INK, Typeface.NORMAL);
            backView.setGravity(Gravity.CENTER);
            backView.setContentDescription("Назад");
            backView.setOnClickListener(view -> back.run());
            FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.START | Gravity.TOP);
            bar.addView(backView, backParams);
        }
        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.setGravity(back == null ? Gravity.START : Gravity.CENTER_HORIZONTAL);
        TextView titleView = text(title, back == null ? 27 : 22, INK, Typeface.BOLD);
        titleView.setGravity(back == null ? Gravity.START : Gravity.CENTER);
        titleBlock.addView(titleView);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subtitleView = text(subtitle, 14, MUTED, Typeface.NORMAL);
            subtitleView.setGravity(back == null ? Gravity.START : Gravity.CENTER);
            titleBlock.addView(subtitleView, topMargin(-1, -2));
        }
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                back == null ? ViewGroup.LayoutParams.WRAP_CONTENT : dp(260), ViewGroup.LayoutParams.WRAP_CONTENT,
                back == null ? Gravity.START | Gravity.CENTER_VERTICAL : Gravity.CENTER);
        if (back == null) titleParams.leftMargin = dp(0);
        bar.addView(titleBlock, titleParams);

        int actionIcon = title.equals("Сегодня") ? R.drawable.ic_bell
                : title.equals("Календарь") ? R.drawable.ic_tune
                : back != null && (title.startsWith("Заказ #") || title.equals("Клиент") || title.equals("Карточка модели"))
                ? R.drawable.ic_nav_more : 0;
        if (actionIcon != 0) {
            ImageView action = iconView(actionIcon, title.equals("Календарь") ? "Настройки календаря" : "Дополнительные действия", INK);
            FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.END | Gravity.TOP);
            action.setPadding(dp(12), dp(12), dp(12), dp(12));
            if (headerAction != null) {
                action.setClickable(true);
                action.setFocusable(true);
                action.setOnClickListener(view -> headerAction.run());
                addRipple(action);
            }
            bar.addView(action, actionParams);
        }
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(barHeight)));
        return root;
    }

    private ScrollView scrollBody(LinearLayout root) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(6), dp(20), dp(24));
        scroll.addView(body, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return scroll;
    }

    private LinearLayout bodyOf(ScrollView scroll) {
        return (LinearLayout) scroll.getChildAt(0);
    }

    private void showToday() {
        LinearLayout root = page("Сегодня", capitalize(dayMonth.format(new Date())), null);
        LinearLayout body = bodyOf(scrollBody(root));

        long monthRevenue = monthRevenue();
        int monthOrders = monthOrders();
        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(metricCard("Выручка", money(monthRevenue), GREEN), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Space gap = new Space(this); stats.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
        stats.addView(metricCard("Заказов", String.valueOf(monthOrders), BLUE), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        body.addView(stats);

        body.addView(sectionTitle("Ближайшие записи"), topMargin(-1, 26));
        List<Models.Order> upcoming = upcomingOrders();
        if (upcoming.isEmpty()) body.addView(emptyCard("Записей пока нет", "Создайте первую запись — она появится здесь."));
        for (int i = 0; i < Math.min(4, upcoming.size()); i++) body.addView(todayOrderCard(upcoming.get(i)), topMargin(-1, 10));

        Button add = primaryButton("+  Новая запись");
        add.setOnClickListener(view -> showNewOrderDialog());
        body.addView(add, topMargin(-1, 22));
        setPage(root);
    }

    private void showCalendar() {
        LinearLayout root = page("Календарь", "", null);
        LinearLayout body = bodyOf(scrollBody(root));

        LinearLayout switcher = new LinearLayout(this);
        switcher.setOrientation(LinearLayout.HORIZONTAL);
        switcher.setPadding(dp(4), dp(4), dp(4), dp(4));
        switcher.setBackground(rounded(Color.rgb(241, 245, 249), 14, 0, Color.TRANSPARENT));
        switcher.addView(calendarModeButton("День", calendarMode.equals("day"), () -> {
            calendarMode = "day";
            showCalendar();
        }), new LinearLayout.LayoutParams(0, dp(44), 1));
        switcher.addView(calendarModeButton("Неделя", calendarMode.equals("week"), () -> {
            calendarMode = "week";
            showCalendar();
        }), new LinearLayout.LayoutParams(0, dp(44), 1));
        switcher.addView(calendarModeButton("Месяц", calendarMode.equals("month"), () -> {
            calendarMode = "month";
            showCalendar();
        }), new LinearLayout.LayoutParams(0, dp(44), 1));
        body.addView(switcher);

        if (calendarMode.equals("month")) {
            body.addView(calendarPeriodBar(), topMargin(-1, 10));
            renderMonthGrid(body);
            renderDaySchedule(body, calendarSelected.getTimeInMillis());
        } else {
            renderWeekStrip(body);
            if (calendarMode.equals("week")) body.addView(calendarPeriodBar(), topMargin(-1, 5));
            renderDaySchedule(body, calendarSelected.getTimeInMillis());
        }
        setPage(root);
    }

    private void renderDaySchedule(LinearLayout body, long dayMillis) {
        final int firstHour = 9;
        final int lastHour = 19;
        final int hourHeight = 48;
        FrameLayout timeline = new FrameLayout(this);
        timeline.setClipChildren(false);
        timeline.setClipToPadding(false);
        timeline.setPadding(0, dp(4), 0, dp(18));
        int timelineHeight = dp((lastHour - firstHour) * hourHeight + 28);

        for (int hour = firstHour; hour <= lastHour; hour++) {
            TextView hourLabel = text(String.format(ru, "%02d:00", hour), 11, MUTED, Typeface.NORMAL);
            hourLabel.setGravity(Gravity.TOP | Gravity.START);
            FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(dp(46), dp(24));
            labelParams.topMargin = dp(4 + (hour - firstHour) * hourHeight - 7);
            timeline.addView(hourLabel, labelParams);

            View divider = new View(this);
            divider.setBackgroundColor(BORDER);
            FrameLayout.LayoutParams dividerParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            dividerParams.leftMargin = dp(52);
            dividerParams.topMargin = dp(4 + (hour - firstHour) * hourHeight);
            timeline.addView(divider, dividerParams);
        }

        List<Models.Order> dayOrders = ordersForDay(dayMillis);
        for (Models.Order order : dayOrders) {
            Calendar start = Calendar.getInstance();
            start.setTimeInMillis(order.startAt);
            float offsetHours = start.get(Calendar.HOUR_OF_DAY) + start.get(Calendar.MINUTE) / 60f - firstHour;
            if (offsetHours < 0 || offsetHours > lastHour - firstHour) continue;
            int durationMinutes = (int) Math.max(60, (order.deadlineAt - order.startAt) / 60000L);
            int blockHeight = Math.max(dp(58), dp(hourHeight * durationMinutes / 60f - 4));
            LinearLayout block = calendarEventBlock(order);
            FrameLayout.LayoutParams eventParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, blockHeight);
            eventParams.leftMargin = dp(58);
            eventParams.rightMargin = dp(4);
            eventParams.topMargin = dp(7 + offsetHours * hourHeight);
            timeline.addView(block, eventParams);
        }

        if (dayOrders.isEmpty()) {
            TextView free = text("Свободное время", 13, MUTED, Typeface.BOLD);
            free.setGravity(Gravity.CENTER);
            free.setBackground(dashedRounded(Color.TRANSPARENT, 9, BORDER));
            free.setOnClickListener(view -> showNewOrderDialog());
            free.setClickable(true);
            FrameLayout.LayoutParams freeParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
            freeParams.leftMargin = dp(58);
            freeParams.rightMargin = dp(4);
            freeParams.topMargin = dp(7 + 7 * hourHeight);
            timeline.addView(free, freeParams);
        }

        Button add = new Button(this);
        add.setText("+");
        add.setTextSize(27);
        add.setTextColor(Color.WHITE);
        add.setGravity(Gravity.CENTER);
        add.setPadding(0, 0, 0, dp(3));
        add.setBackground(circleDrawable(BLUE));
        add.setStateListAnimator(null);
        add.setContentDescription("Добавить запись");
        add.setOnClickListener(view -> showNewOrderDialog());
        FrameLayout.LayoutParams addParams = new FrameLayout.LayoutParams(dp(54), dp(54), Gravity.END | Gravity.BOTTOM);
        addParams.rightMargin = dp(4);
        timeline.addView(add, addParams);
        body.addView(timeline, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, timelineHeight));
    }

    private LinearLayout calendarPeriodBar() {
        LinearLayout period = new LinearLayout(this);
        period.setOrientation(LinearLayout.HORIZONTAL);
        period.setGravity(Gravity.CENTER_VERTICAL);
        Button previous = calendarArrowButton("‹", "Предыдущий период");
        previous.setOnClickListener(view -> shiftCalendar(-1));
        period.addView(previous, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView periodTitle = text(calendarPeriodTitle(), 15, INK, Typeface.BOLD);
        periodTitle.setGravity(Gravity.CENTER);
        period.addView(periodTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button next = calendarArrowButton("›", "Следующий период");
        next.setOnClickListener(view -> shiftCalendar(1));
        period.addView(next, new LinearLayout.LayoutParams(dp(40), dp(40)));
        return period;
    }

    private LinearLayout calendarEventBlock(Models.Order order) {
        int color = order.status.equals("Завершено") ? GREEN : BLUE;
        int fill = order.status.equals("Завершено") ? Color.rgb(236, 253, 245) : Color.rgb(239, 246, 255);
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(11), dp(8), dp(10), dp(7));
        block.setBackground(rounded(fill, 9, 1, withAlpha(color, 110)));
        block.setClickable(true);
        block.setOnClickListener(view -> showOrderDetail(order.id));
        addRipple(block);
        String interval = time.format(new Date(order.startAt)) + "–" + time.format(new Date(order.deadlineAt));
        block.addView(text(interval + "   " + vehicle(order.car, order.carModel, order.plate), 12, color, Typeface.BOLD));
        TextView services = text(serviceNames(order), 13, INK, Typeface.BOLD);
        services.setSingleLine(true);
        services.setEllipsize(TextUtils.TruncateAt.END);
        block.addView(services, topMargin(-1, 3));
        return block;
    }

    private Button calendarModeButton(String caption, boolean selected, Runnable action) {
        Button button = new Button(this);
        button.setText(caption);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(selected ? BLUE_DARK : MUTED);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(selected ? SURFACE : Color.TRANSPARENT, 11, 0, Color.TRANSPARENT));
        button.setElevation(selected ? dp(1) : 0);
        button.setStateListAnimator(null);
        button.setOnClickListener(view -> action.run());
        button.setContentDescription("Показать календарь: " + caption.toLowerCase(ru));
        return button;
    }

    private Button calendarArrowButton(String caption, String description) {
        Button button = new Button(this);
        button.setText(caption);
        button.setTextSize(27);
        button.setTextColor(BLUE);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, dp(3));
        button.setBackground(rounded(Color.rgb(239, 246, 255), 12, 0, Color.TRANSPARENT));
        button.setStateListAnimator(null);
        button.setContentDescription(description);
        return button;
    }

    private void shiftCalendar(int direction) {
        if (calendarMode.equals("month")) {
            int desiredDay = calendarSelected.get(Calendar.DAY_OF_MONTH);
            calendarSelected.set(Calendar.DAY_OF_MONTH, 1);
            calendarSelected.add(Calendar.MONTH, direction);
            calendarSelected.set(Calendar.DAY_OF_MONTH,
                    Math.min(desiredDay, calendarSelected.getActualMaximum(Calendar.DAY_OF_MONTH)));
        } else if (calendarMode.equals("week")) {
            calendarSelected.add(Calendar.DAY_OF_MONTH, direction * 7);
        } else {
            calendarSelected.add(Calendar.DAY_OF_MONTH, direction);
        }
        showCalendar();
    }

    private String calendarPeriodTitle() {
        if (calendarMode.equals("month")) return capitalize(monthYear.format(calendarSelected.getTime()));
        if (calendarMode.equals("day")) return capitalize(dayMonth.format(calendarSelected.getTime()));
        Calendar start = weekStart(calendarSelected);
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_MONTH, 6);
        if (start.get(Calendar.MONTH) == end.get(Calendar.MONTH)) {
            return start.get(Calendar.DAY_OF_MONTH) + "–" + dayMonth.format(end.getTime());
        }
        return shortDayMonth.format(start.getTime()) + " — " + shortDayMonth.format(end.getTime());
    }

    private Calendar weekStart(Calendar source) {
        Calendar start = (Calendar) source.clone();
        int mondayOffset = (start.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        start.add(Calendar.DAY_OF_MONTH, -mondayOffset);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        return start;
    }

    private void renderWeekStrip(LinearLayout body) {
        String[] weekdays = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        LinearLayout week = new LinearLayout(this);
        week.setOrientation(LinearLayout.HORIZONTAL);
        week.setBaselineAligned(false);
        Calendar day = weekStart(calendarSelected);
        for (int index = 0; index < 7; index++) {
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView weekday = text(weekdays[index], 11, MUTED, Typeface.BOLD);
            weekday.setGravity(Gravity.CENTER);
            column.addView(weekday, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));
            boolean selected = sameDay(day.getTimeInMillis(), calendarSelected.getTimeInMillis());
            boolean today = sameDay(day.getTimeInMillis(), System.currentTimeMillis());
            TextView date = text(String.valueOf(day.get(Calendar.DAY_OF_MONTH)), 14,
                    selected ? Color.WHITE : INK, selected ? Typeface.BOLD : Typeface.NORMAL);
            date.setGravity(Gravity.CENTER);
            date.setBackground(selected ? circleDrawable(BLUE)
                    : rounded(Color.TRANSPARENT, 20, today ? 1 : 0, BLUE));
            date.setClickable(true);
            date.setFocusable(true);
            long selectedTime = day.getTimeInMillis();
            date.setOnClickListener(view -> {
                calendarSelected.setTimeInMillis(selectedTime);
                showCalendar();
            });
            addRipple(date);
            column.addView(date, new LinearLayout.LayoutParams(dp(38), dp(38)));
            week.addView(column, new LinearLayout.LayoutParams(0, dp(64), 1));
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
        body.addView(week, topMargin(-1, 12));
    }

    private void renderMonthGrid(LinearLayout body) {
        String[] weekdays = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.HORIZONTAL);
        for (int index = 0; index < weekdays.length; index++) {
            if (index > 0) labels.addView(new Space(this), new LinearLayout.LayoutParams(dp(4), 1));
            TextView label = text(weekdays[index], 12, MUTED, Typeface.BOLD);
            label.setGravity(Gravity.CENTER);
            labels.addView(label, new LinearLayout.LayoutParams(0, dp(32), 1));
        }
        body.addView(labels, topMargin(-1, 7));

        Calendar first = (Calendar) calendarSelected.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        int startOffset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        int rows = (int) Math.ceil((startOffset + daysInMonth) / 7.0);
        int dayNumber = 1;
        for (int rowIndex = 0; rowIndex < rows; rowIndex++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setBaselineAligned(false);
            for (int column = 0; column < 7; column++) {
                if (column > 0) row.addView(new Space(this), new LinearLayout.LayoutParams(dp(4), 1));
                int position = rowIndex * 7 + column;
                if (position < startOffset || dayNumber > daysInMonth) {
                    row.addView(new Space(this), new LinearLayout.LayoutParams(0, dp(58), 1));
                    continue;
                }
                Calendar day = (Calendar) first.clone();
                day.set(Calendar.DAY_OF_MONTH, dayNumber++);
                int orders = ordersForDay(day.getTimeInMillis()).size();
                row.addView(calendarDayCell("", day, orders, false), new LinearLayout.LayoutParams(0, dp(58), 1));
            }
            body.addView(row, topMargin(-1, rowIndex == 0 ? 0 : 4));
        }
    }

    private TextView calendarDayCell(String weekday, Calendar day, int orderCount, boolean showWeekday) {
        boolean selected = sameDay(day.getTimeInMillis(), calendarSelected.getTimeInMillis());
        boolean today = sameDay(day.getTimeInMillis(), System.currentTimeMillis());
        String value = (showWeekday ? weekday + "\n" : "") + day.get(Calendar.DAY_OF_MONTH)
                + (orderCount > 0 ? "\n• " + orderCount : "");
        TextView cell = text(value, showWeekday ? 13 : 14, selected ? Color.WHITE : orderCount > 0 ? BLUE_DARK : INK,
                selected || orderCount > 0 ? Typeface.BOLD : Typeface.NORMAL);
        cell.setGravity(Gravity.CENTER);
        cell.setClickable(true);
        cell.setFocusable(true);
        cell.setContentDescription(dayMonth.format(day.getTime()) + (orderCount == 0 ? ", заказов нет" : ", заказов: " + orderCount));
        int fill = selected ? BLUE : orderCount > 0 ? Color.rgb(239, 246, 255) : Color.TRANSPARENT;
        cell.setBackground(rounded(fill, 11, today && !selected ? 1 : 0, BLUE));
        long selectedTime = day.getTimeInMillis();
        cell.setOnClickListener(view -> {
            calendarSelected.setTimeInMillis(selectedTime);
            showCalendar();
        });
        addRipple(cell);
        return cell;
    }

    private void showOrders() {
        LinearLayout root = page("Заказы", "", null);
        LinearLayout body = bodyOf(scrollBody(root));
        Button add = primaryButton("+  Новая запись");
        add.setOnClickListener(view -> showNewOrderDialog());
        body.addView(add);

        List<Models.Order> copy = new ArrayList<>(store.orders);
        copy.sort((a, b) -> Long.compare(b.startAt, a.startAt));
        for (Models.Order order : copy) body.addView(orderCard(order), topMargin(-1, 12));
        setPage(root);
    }

    private void showClients() {
        navigation.setVisibility(View.GONE);
        LinearLayout root = page("Клиенты", "Контакты и история", () -> showRoute("more"));
        LinearLayout body = bodyOf(scrollBody(root));
        EditText search = field("Имя, телефон, марка, модель или номер", InputType.TYPE_CLASS_TEXT);
        body.addView(labeled(search));
        LinearLayout clientList = new LinearLayout(this);
        clientList.setOrientation(LinearLayout.VERTICAL);
        body.addView(clientList, topMargin(-1, 8));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                renderClients(clientList, value == null ? "" : value.toString());
            }
            @Override public void afterTextChanged(Editable value) { }
        });
        renderClients(clientList, "");
        setPage(root);
    }

    private void renderClients(LinearLayout clientList, String query) {
        clientList.removeAllViews();
        String normalized = query.trim().toLowerCase(ru);
        String digits = query.replaceAll("[^0-9]", "");
        int visible = 0;
        for (Models.Client client : store.clients) {
            String nameValue = client.name == null ? "" : client.name;
            String phoneValue = client.phone == null ? "" : client.phone;
            String carValue = client.car == null ? "" : client.car;
            String modelValue = client.carModel == null ? "" : client.carModel;
            String plateValue = client.plate == null ? "" : client.plate;
            boolean matches = normalized.isEmpty()
                    || nameValue.toLowerCase(ru).contains(normalized)
                    || carValue.toLowerCase(ru).contains(normalized)
                    || modelValue.toLowerCase(ru).contains(normalized)
                    || plateValue.toLowerCase(ru).contains(normalized)
                    || (!digits.isEmpty() && (phoneValue.replaceAll("[^0-9]", "").contains(digits)
                    || plateValue.replaceAll("[^0-9]", "").contains(digits)));
            if (!matches) continue;
            visible++;
            LinearLayout card = card();
            card.setClickable(true);
            card.setOnClickListener(view -> showClientDetail(client.id));
            addRipple(card);
            TextView name = text(client.name, 18, INK, Typeface.BOLD);
            card.addView(name);
            card.addView(text(vehicle(client.car, client.carModel, client.plate), 15, MUTED, Typeface.NORMAL), topMargin(-1, 5));
            card.addView(text(client.phone, 14, BLUE, Typeface.NORMAL), topMargin(-1, 10));
            int count = 0; long total = 0;
            for (Models.Order order : store.orders) if (order.clientId.equals(client.id)) { count++; total += order.total; }
            card.addView(text(count + " заказов • " + money(total), 13, MUTED, Typeface.NORMAL), topMargin(-1, 12));
            clientList.addView(card, topMargin(-1, 12));
        }
        if (visible == 0) {
            clientList.addView(emptyCard(normalized.isEmpty() ? "Клиентов пока нет" : "Ничего не найдено",
                    normalized.isEmpty() ? "Клиент добавится вместе с первым заказом." : "Проверьте имя, телефон, марку, модель или госномер."));
        }
    }

    private void showMore() {
        LinearLayout root = page("Ещё", "Настройки бизнеса", null);
        LinearLayout body = bodyOf(scrollBody(root));
        body.addView(actionCard("Клиенты", "Контакты и история заказов", () -> showClients()));
        body.addView(actionCard("История заказов", "Поиск по заказам и клиентам", () -> showOrderHistory()), topMargin(-1, 12));
        body.addView(actionCard("Услуги", "Названия и длительность работ", () -> showServices()), topMargin(-1, 12));
        body.addView(actionCard("Марки автомобилей", "Справочник для быстрого ввода", () -> showCarMakes()), topMargin(-1, 12));
        body.addView(actionCard("Обновления", "Проверка новых версий через GitHub", () -> showUpdates()), topMargin(-1, 12));
        body.addView(actionCard("О приложении", "Версия и перенос базы данных", this::showAbout), topMargin(-1, 12));
        setPage(root);
    }

    private void showAbout() {
        navigation.setVisibility(View.GONE);
        LinearLayout root = page("О приложении", "DetailFlow " + updateManager.currentVersion(), () -> showRoute("more"));
        LinearLayout body = bodyOf(scrollBody(root));

        LinearLayout summary = cardWithColor(Color.rgb(239, 246, 255));
        summary.addView(text("DetailFlow", 24, INK, Typeface.BOLD));
        summary.addView(text("Версия " + updateManager.currentVersion(), 14, BLUE_DARK, Typeface.BOLD), topMargin(-1, 7));
        summary.addView(text("Простое автономное приложение для управления детейлингом. Данные хранятся на этом телефоне.",
                14, MUTED, Typeface.NORMAL), topMargin(-1, 12));
        body.addView(summary);

        body.addView(sectionTitle("Резервная копия"), topMargin(-1, 24));
        body.addView(text("Архив содержит клиентов, заказы, услуги, доходы, расходы и справочники автомобилей. Его можно перенести на другое устройство или сохранить для будущего сервера.",
                14, MUTED, Typeface.NORMAL), topMargin(-1, 8));

        Button export = primaryButton("Выгрузить БД");
        export.setContentDescription("Выгрузить базу данных в ZIP архив");
        export.setOnClickListener(view -> chooseDatabaseExportLocation());
        body.addView(export, topMargin(-1, 18));

        Button importButton = outlineButton("Загрузить БД", BLUE);
        importButton.setContentDescription("Загрузить базу данных из ZIP архива");
        importButton.setOnClickListener(view -> chooseDatabaseArchive());
        body.addView(importButton, topMargin(-1, 10));

        LinearLayout warning = cardWithColor(Color.rgb(255, 247, 237));
        warning.setPadding(dp(15), dp(13), dp(15), dp(14));
        warning.addView(text("Фотографии не входят в архив", 14, AMBER, Typeface.BOLD));
        warning.addView(text("Они остаются в памяти исходного телефона. Перед загрузкой другой базы сначала выгрузите текущую.",
                13, MUTED, Typeface.NORMAL), topMargin(-1, 5));
        body.addView(warning, topMargin(-1, 14));
        setPage(root);
    }

    private void chooseDatabaseExportLocation() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, "DetailFlow-backup-" + backupDate.format(new Date()) + ".zip");
        startActivityForResult(intent, REQ_EXPORT_DATABASE);
    }

    private void chooseDatabaseArchive() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQ_IMPORT_DATABASE);
    }

    private void writeDatabaseArchive(Uri uri) {
        toast("Создаю архив…");
        new Thread(() -> {
            try {
                JSONObject database = new JSONObject(store.exportData());
                JSONArray orders = database.optJSONArray("orders");
                if (orders != null) for (int index = 0; index < orders.length(); index++) {
                    JSONObject order = orders.optJSONObject(index);
                    if (order == null) continue;
                    order.put("beforeUris", new JSONArray());
                    order.put("afterUris", new JSONArray());
                    order.remove("beforeUri");
                    order.remove("afterUri");
                }
                JSONObject manifest = new JSONObject()
                        .put("formatVersion", 1)
                        .put("appVersion", updateManager.currentVersion())
                        .put("exportedAt", System.currentTimeMillis())
                        .put("photosIncluded", false);
                try (OutputStream stream = getContentResolver().openOutputStream(uri, "w");
                     ZipOutputStream zip = stream == null ? null : new ZipOutputStream(stream)) {
                    if (zip == null) throw new IllegalStateException("Не удалось открыть файл");
                    writeZipEntry(zip, "manifest.json", manifest.toString(2));
                    writeZipEntry(zip, "database.json", database.toString());
                }
                runOnUiThread(() -> toast("Архив базы данных сохранён"));
            } catch (Exception error) {
                runOnUiThread(() -> message("Не удалось выгрузить БД", "Проверьте выбранную папку и попробуйте ещё раз."));
            }
        }).start();
    }

    private void writeZipEntry(ZipOutputStream zip, String name, String value) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String readDatabaseArchive(Uri uri) throws Exception {
        try (InputStream stream = getContentResolver().openInputStream(uri);
             ZipInputStream zip = stream == null ? null : new ZipInputStream(stream)) {
            if (zip == null) throw new IllegalStateException("Не удалось открыть архив");
            ZipEntry entry;
            int entryCount = 0;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entryCount > 3 || entry.isDirectory()) throw new IllegalStateException("Неподдерживаемая структура архива");
                if (!entry.isDirectory() && "database.json".equals(entry.getName())) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int total = 0;
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_DATABASE_ARCHIVE_BYTES) throw new IllegalStateException("База данных слишком большая");
                        output.write(buffer, 0, read);
                    }
                    return output.toString(StandardCharsets.UTF_8.name());
                }
                if (!"manifest.json".equals(entry.getName()) || entry.getSize() > 65536L) {
                    throw new IllegalStateException("Неподдерживаемая структура архива");
                }
                int manifestBytes = 0;
                byte[] manifestBuffer = new byte[4096];
                int manifestRead;
                while ((manifestRead = zip.read(manifestBuffer)) != -1) {
                    manifestBytes += manifestRead;
                    if (manifestBytes > 65536) throw new IllegalStateException("Manifest слишком большой");
                }
                zip.closeEntry();
            }
        }
        throw new IllegalStateException("В архиве нет database.json");
    }

    private void prepareDatabaseImport(Uri uri) {
        toast("Проверяю архив…");
        new Thread(() -> {
            try {
                String raw = readDatabaseArchive(uri);
                JSONObject root = new JSONObject(raw);
                JSONArray clients = root.optJSONArray("clients");
                JSONArray orders = root.optJSONArray("orders");
                JSONArray transactions = root.optJSONArray("transactions");
                JSONArray services = root.optJSONArray("services");
                if (clients == null || orders == null || transactions == null || services == null) {
                    throw new IllegalStateException("Неподдерживаемый формат базы данных");
                }
                String details = "В архиве: " + countCaption(clients.length(), "клиент", "клиента", "клиентов") + ", "
                        + countCaption(orders.length(), "заказ", "заказа", "заказов") + ", "
                        + countCaption(transactions.length(), "финансовая операция", "финансовые операции", "финансовых операций")
                        + ".\n\nТекущая база будет заменена. Фотографии не переносятся.";
                runOnUiThread(() -> {
                    AlertDialog dialog = new AlertDialog.Builder(this)
                            .setTitle("Загрузить эту базу?")
                            .setMessage(details)
                            .setNegativeButton("Отмена", null)
                            .setNeutralButton("Загрузить", (ignored, which) -> {
                                if (store.importData(raw)) {
                                    toast("База данных загружена");
                                    showAbout();
                                } else {
                                    message("Не удалось загрузить БД", "Архив повреждён или создан несовместимой версией приложения.");
                                }
                            }).create();
                    showStyledDialog(dialog);
                });
            } catch (Exception error) {
                runOnUiThread(() -> message("Не удалось открыть архив", "Выберите ZIP-архив, созданный в разделе «О приложении»."));
            }
        }).start();
    }

    private void showOrderHistory() {
        navigation.setVisibility(View.GONE);
        LinearLayout root = page("История заказов", "Все записи в одном месте", () -> showRoute("more"));
        LinearLayout body = bodyOf(scrollBody(root));
        EditText search = field("Клиент, марка, модель, номер или № заказа", InputType.TYPE_CLASS_TEXT);
        body.addView(labeled(search));
        LinearLayout historyList = new LinearLayout(this);
        historyList.setOrientation(LinearLayout.VERTICAL);
        body.addView(historyList, topMargin(-1, 8));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                renderOrderHistory(historyList, value == null ? "" : value.toString());
            }
            @Override public void afterTextChanged(Editable value) { }
        });
        renderOrderHistory(historyList, "");
        setPage(root);
    }

    private void renderOrderHistory(LinearLayout historyList, String query) {
        historyList.removeAllViews();
        String normalized = query.trim().toLowerCase(ru);
        String digits = query.replaceAll("[^0-9]", "");
        List<Models.Order> orders = new ArrayList<>(store.orders);
        orders.sort((a, b) -> Long.compare(b.startAt, a.startAt));
        int visible = 0;
        for (Models.Order order : orders) {
            String searchable = (order.id + " " + order.clientName + " " + order.phone + " " + order.car + " "
                    + order.carModel + " " + order.plate + " " + order.status).toLowerCase(ru);
            boolean matches = normalized.isEmpty() || searchable.contains(normalized)
                    || (!digits.isEmpty() && (order.phone.replaceAll("[^0-9]", "").contains(digits)
                    || order.plate.replaceAll("[^0-9]", "").contains(digits) || order.id.contains(digits)));
            if (!matches) continue;
            visible++;
            historyList.addView(orderHistoryCard(order), topMargin(-1, 12));
        }
        if (visible == 0) {
            historyList.addView(emptyCard(orders.isEmpty() ? "Заказов пока нет" : "Ничего не найдено",
                    orders.isEmpty() ? "Создайте первую запись — она появится здесь." : "Попробуйте имя, телефон, марку, модель, госномер или номер заказа."));
        }
    }

    private void showCarMakes() {
        navigation.setVisibility(View.GONE);
        LinearLayout root = page("Марки автомобилей", "Справочник для быстрого ввода", () -> showRoute("more"));
        LinearLayout body = bodyOf(scrollBody(root));
        Button add = primaryButton("+  Добавить марку");
        add.setOnClickListener(view -> editCarMake(null));
        body.addView(add);
        body.addView(text("Марки из справочника появляются подсказками при создании заказа.", 14, MUTED, Typeface.NORMAL), topMargin(-1, 14));
        if (store.carMakes.isEmpty()) {
            body.addView(emptyCard("Справочник пуст", "Добавьте первую марку автомобиля."), topMargin(-1, 12));
        }
        for (String carMake : new ArrayList<>(store.carMakes)) {
            LinearLayout item = card();
            item.setClickable(true);
            item.setFocusable(true);
            item.setContentDescription("Открыть модели " + carMake);
            item.setOnClickListener(view -> showCarModels(carMake));
            addRipple(item);
            item.addView(text(carMake, 17, INK, Typeface.BOLD));
            int count = store.carModelsForMake(carMake).size();
            item.addView(text(count + " " + modelCountCaption(count) + " • открыть", 13, BLUE, Typeface.NORMAL), topMargin(-1, 6));
            body.addView(item, topMargin(-1, 10));
        }
        setPage(root);
    }

    private void showCarModels(String make) {
        navigation.setVisibility(View.GONE);
        LinearLayout root = page(make, "Модели и постоянные примечания", this::showCarMakes);
        LinearLayout body = bodyOf(scrollBody(root));
        Button add = primaryButton("+  Добавить модель");
        add.setOnClickListener(view -> editCarModel(null, make, () -> showCarModels(make)));
        body.addView(add);
        Button editMake = outlineButton("Изменить марку", BLUE);
        editMake.setOnClickListener(view -> editCarMake(make));
        body.addView(editMake, topMargin(-1, 10));
        List<Models.CarModel> models = store.carModelsForMake(make);
        if (models.isEmpty()) body.addView(emptyCard("Моделей пока нет", "Добавьте первую модель этой марки."), topMargin(-1, 14));
        for (Models.CarModel model : models) {
            LinearLayout item = card();
            item.setClickable(true);
            item.setFocusable(true);
            item.setContentDescription("Открыть карточку модели " + model.name);
            item.setOnClickListener(view -> showCarModelDetail(model.id, () -> showCarModels(make)));
            addRipple(item);
            item.addView(text(model.name, 18, INK, Typeface.BOLD));
            item.addView(text(model.note.isEmpty() ? "Примечаний нет" : model.note, 14, MUTED, Typeface.NORMAL), topMargin(-1, 6));
            body.addView(item, topMargin(-1, 10));
        }
        setPage(root);
    }

    private void showCarModelDetail(String modelId, Runnable back) {
        Models.CarModel model = store.carModelById(modelId);
        if (model == null) { back.run(); return; }
        showNavigationSelection("more");
        LinearLayout root = page("Карточка модели", "", back, () -> editCarModel(model, model.make, back));
        LinearLayout body = bodyOf(scrollBody(root));

        LinearLayout modelCard = card();
        modelCard.setOrientation(LinearLayout.HORIZONTAL);
        modelCard.setGravity(Gravity.CENTER_VERTICAL);
        modelCard.setPadding(dp(14), dp(16), dp(14), dp(16));
        FrameLayout carBadge = new FrameLayout(this);
        carBadge.setBackground(circleWithStroke(Color.rgb(248, 250, 252), BORDER, 1));
        ImageView carIcon = iconView(R.drawable.ic_car, "Модель автомобиля", BLUE);
        carBadge.addView(carIcon, new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER));
        modelCard.addView(carBadge, new LinearLayout.LayoutParams(dp(78), dp(78)));
        LinearLayout modelCopy = new LinearLayout(this);
        modelCopy.setOrientation(LinearLayout.VERTICAL);
        modelCopy.addView(text(vehicle(model.make, model.name, ""), 22, INK, Typeface.BOLD));
        modelCopy.addView(text("Модель автомобиля", 14, MUTED, Typeface.NORMAL), topMargin(-1, 3));
        LinearLayout.LayoutParams makeChipParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        makeChipParams.topMargin = dp(9);
        modelCopy.addView(outlineChip(model.make), makeChipParams);
        modelCard.addView(modelCopy, leftMargin(-1, 16, 1));
        body.addView(modelCard);

        int clients = 0; int orders = 0;
        for (Models.Client client : store.clients) if (client.carModelId.equals(model.id)) clients++;
        for (Models.Order order : store.orders) if (order.carModelId.equals(model.id)) orders++;

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.addView(clientMetricCard(String.valueOf(clients),
                countCaption(clients, "клиент", "клиента", "клиентов").replaceFirst("^\\d+\\s+", ""),
                R.drawable.ic_user_outline), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        metrics.addView(new Space(this), new LinearLayout.LayoutParams(dp(10), 1));
        metrics.addView(clientMetricCard(String.valueOf(orders),
                countCaption(orders, "заказ", "заказа", "заказов").replaceFirst("^\\d+\\s+", ""),
                R.drawable.ic_nav_orders), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        body.addView(metrics, topMargin(-1, 14));

        body.addView(sectionTitle("Постоянное примечание"), topMargin(-1, 24));
        LinearLayout noteCard = card();
        noteCard.setOrientation(LinearLayout.HORIZONTAL);
        noteCard.setGravity(Gravity.CENTER_VERTICAL);
        noteCard.setPadding(dp(12), dp(13), dp(12), dp(13));
        FrameLayout infoBadge = new FrameLayout(this);
        infoBadge.setBackground(circleDrawable(Color.rgb(239, 246, 255)));
        ImageView infoIcon = iconView(R.drawable.ic_info_outline, "Постоянное примечание", BLUE);
        infoBadge.addView(infoIcon, new FrameLayout.LayoutParams(dp(27), dp(27), Gravity.CENTER));
        noteCard.addView(infoBadge, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView note = text(model.note.isEmpty() ? "Примечаний пока нет" : model.note, 14,
                model.note.isEmpty() ? MUTED : INK, Typeface.NORMAL);
        noteCard.addView(note, leftMargin(-1, 12, 1));
        TextView chevron = text("›", 27, INK, Typeface.NORMAL);
        chevron.setGravity(Gravity.CENTER);
        noteCard.addView(chevron, new LinearLayout.LayoutParams(dp(28), dp(48)));
        noteCard.setClickable(true);
        noteCard.setOnClickListener(view -> editCarModel(model, model.make, back));
        addRipple(noteCard);
        body.addView(noteCard, topMargin(-1, 10));

        Button edit = primaryButton("Изменить карточку модели");
        edit.setOnClickListener(view -> editCarModel(model, model.make, back));
        body.addView(edit, topMargin(-1, 180));
        setPage(root);
    }

    private void editCarModel(Models.CarModel existing, String make, Runnable back) {
        LinearLayout form = dialogForm();
        EditText name = field("Название модели", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText note = multilineField("Постоянное примечание к модели");
        if (existing != null) { name.setText(existing.name); note.setText(existing.note); }
        LinearLayout section = dialogSection(make);
        section.addView(labeled(name), topMargin(-1, 8));
        section.addView(labeled(note), topMargin(-1, 8));
        form.addView(section);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Новая модель" : "Изменить модель")
                .setView(form).setNegativeButton("Отмена", null).setPositiveButton("Сохранить", null);
        if (existing != null) builder.setNeutralButton("Удалить", null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String value = name.getText().toString().trim();
                if (value.isEmpty()) { name.setError("Укажите модель"); return; }
                Models.CarModel model = existing;
                if (model == null) {
                    if (store.findCarModel(make, value) != null) { name.setError("Такая модель уже есть"); return; }
                    model = store.ensureCarModel(make, value);
                }
                if (model == null || !store.updateCarModel(model, value, note.getText().toString())) {
                    name.setError("Такая модель уже есть"); return;
                }
                store.save(); dialog.dismiss();
                Models.CarModel savedModel = model;
                showCarModelDetail(savedModel.id, back);
            });
            if (existing != null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                if (store.carModelInUse(existing.id)) { toast("Модель используется в карточке клиента или заказе"); return; }
                store.carModels.remove(existing); store.save(); dialog.dismiss(); back.run();
            });
        });
        showStyledDialog(dialog);
    }

    private void editCarMake(String existing) {
        LinearLayout form = dialogForm();
        EditText make = field("Марка автомобиля", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (existing != null) make.setText(existing);
        form.addView(labeled(make));
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Новая марка" : "Изменить марку")
                .setView(form)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", null);
        if (existing != null) builder.setNeutralButton("Удалить", null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String value = make.getText().toString().trim();
                if (value.isEmpty()) { make.setError("Укажите марку"); return; }
                boolean saved = existing == null ? store.addCarMake(value) : store.renameCarMake(existing, value);
                if (!saved) { make.setError("Такая марка уже есть"); return; }
                store.save();
                dialog.dismiss();
                showCarMakes();
            });
            if (existing != null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                if (!store.carModelsForMake(existing).isEmpty()) { toast("Сначала удалите модели этой марки"); return; }
                store.carMakes.remove(existing);
                store.save();
                dialog.dismiss();
                showCarMakes();
            });
        });
        showStyledDialog(dialog);
    }

    private void showUpdates() {
        navigation.setVisibility(View.GONE);
        LinearLayout root = page("Обновления", "Новые версии из GitHub Releases", () -> showRoute("more"));
        LinearLayout body = bodyOf(scrollBody(root));

        body.addView(infoRow("Установленная версия", updateManager.currentVersion(), BLUE));
        TextView explanation = text("Укажите публичный репозиторий в формате владелец/репозиторий. В последнем релизе должен находиться APK, подписанный тем же ключом.", 14, MUTED, Typeface.NORMAL);
        body.addView(explanation, topMargin(-1, 16));

        EditText repository = field("Владелец/репозиторий", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        repository.setText(updateManager.getRepository());
        body.addView(labeled(repository), topMargin(-1, 16));

        TextView state = text(updateManager.getRepository().isEmpty() ? "Автопроверка включится после сохранения репозитория." : "Автопроверка выполняется каждые 6 часов.", 14, MUTED, Typeface.NORMAL);
        state.setMinHeight(dp(48));
        body.addView(state, topMargin(-1, 12));

        Button save = outlineButton("Сохранить репозиторий", BLUE);
        save.setOnClickListener(view -> {
            if (updateManager.setRepository(repository.getText().toString())) {
                repository.setText(updateManager.getRepository());
                state.setText("Репозиторий сохранён. Автопроверка выполняется каждые 6 часов.");
                state.setTextColor(GREEN);
            } else {
                repository.setError("Например: vadim/detailflow");
            }
        });
        body.addView(save, topMargin(-1, 8));

        Button check = primaryButton("Проверить обновления");
        check.setOnClickListener(view -> {
            if (!updateManager.setRepository(repository.getText().toString())) {
                repository.setError("Укажите владелец/репозиторий");
                return;
            }
            check.setEnabled(false);
            check.setText("Проверяю…");
            state.setText("Соединение с GitHub…");
            state.setTextColor(MUTED);
            updateManager.checkForUpdates((release, resultMessage, error) -> runOnUiThread(() -> {
                check.setEnabled(true);
                check.setText("Проверить обновления");
                state.setText(resultMessage);
                state.setTextColor(error ? RED : release == null ? GREEN : BLUE);
                if (release != null) showReleaseDialog(release);
            }));
        });
        body.addView(check, topMargin(-1, 12));
        setPage(root);
    }

    private void showReleaseDialog(UpdateManager.Release release) {
        String notes = release.notes == null ? "" : release.notes.trim();
        if (notes.length() > 700) notes = notes.substring(0, 700) + "…";
        String size = release.size > 0 ? String.format(ru, "%.1f МБ", release.size / 1048576f) : "APK";
        String description = "Версия " + release.tag + " • " + size + (notes.isEmpty() ? "" : "\n\n" + notes);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Доступно обновление").setMessage(description)
                .setNegativeButton("Позже", null)
                .setPositiveButton("Скачать", (ignoredDialog, which) -> updateManager.download(release)).create();
        showStyledDialog(dialog);
    }

    private void showServices() {
        navigation.setVisibility(View.GONE);
        LinearLayout root = page("Услуги", "Названия и длительность работ", () -> showRoute("more"));
        LinearLayout body = bodyOf(scrollBody(root));
        Button add = primaryButton("+  Новая услуга");
        add.setOnClickListener(view -> editService(null));
        body.addView(add);
        for (Models.Service service : store.services) {
            LinearLayout item = card();
            item.setClickable(true);
            item.setOnClickListener(view -> editService(service));
            addRipple(item);
            item.addView(text(service.name, 17, INK, Typeface.BOLD));
            item.addView(text(service.category + " • " + duration(service.durationMinutes), 14, MUTED, Typeface.NORMAL), topMargin(-1, 6));
            item.addView(text("Нажмите, чтобы изменить", 12, BLUE, Typeface.NORMAL), topMargin(-1, 9));
            body.addView(item, topMargin(-1, 12));
        }
        setPage(root);
    }

    private void showFinance() {
        LinearLayout root = page("Финансы", "Текущий месяц", null);
        LinearLayout body = bodyOf(scrollBody(root));
        long revenue = monthRevenue();
        long expenses = 0;
        long manualIncome = 0;
        for (Models.Transaction t : store.transactions) if (sameMonth(t.createdAt, System.currentTimeMillis())) {
            if (t.income) manualIncome += t.amount; else expenses += t.amount;
        }
        revenue += manualIncome;
        LinearLayout profit = cardWithColor(GREEN);
        profit.addView(text("Прибыль", 14, Color.WHITE, Typeface.NORMAL));
        profit.addView(text(money(revenue - expenses), 30, Color.WHITE, Typeface.BOLD), topMargin(-1, 5));
        body.addView(profit);

        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(metricCard("Выручка", money(revenue), GREEN), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(new Space(this), new LinearLayout.LayoutParams(dp(12), 1));
        row.addView(metricCard("Расходы", money(expenses), AMBER), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        body.addView(row, topMargin(-1, 12));

        long outstanding = 0;
        for (Models.Order order : store.orders) if (!order.paid) outstanding += orderBalance(order);
        body.addView(infoRow("Ожидается оплат", money(outstanding), AMBER), topMargin(-1, 14));
        body.addView(sectionTitle("Последние операции"), topMargin(-1, 24));
        List<FinanceEntry> entries = new ArrayList<>();
        for (Models.Transaction transaction : store.transactions) entries.add(new FinanceEntry(transaction));
        for (Models.Order order : store.orders) if (order.paid && orderBalance(order) > 0) entries.add(new FinanceEntry(order));
        entries.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
        if (entries.isEmpty()) body.addView(emptyCard("Операций пока нет", "Здесь появятся доходы и расходы."), topMargin(-1, 10));
        for (FinanceEntry entry : entries) {
            body.addView(entry.transaction != null ? transactionCard(entry.transaction) : paidOrderIncomeCard(entry.paidOrder), topMargin(-1, 10));
        }

        LinearLayout buttons = new LinearLayout(this); buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button income = outlineButton("+ Доход", GREEN); income.setOnClickListener(view -> addTransaction(true));
        Button expense = outlineButton("+ Расход", AMBER); expense.setOnClickListener(view -> addTransaction(false));
        buttons.addView(income, new LinearLayout.LayoutParams(0, dp(54), 1));
        buttons.addView(new Space(this), new LinearLayout.LayoutParams(dp(12), 1));
        buttons.addView(expense, new LinearLayout.LayoutParams(0, dp(54), 1));
        body.addView(buttons, topMargin(-1, 20));
        setPage(root);
    }

    private void showClientDetail(String clientId) {
        showClientDetail(clientId, this::showClients);
    }

    private void showClientDetail(String clientId, Runnable back) {
        Models.Client client = store.clientById(clientId);
        if (client == null) return;
        showNavigationSelection("orders");
        LinearLayout root = page("Клиент", "", back, () -> showClientActions(client, back));
        LinearLayout body = bodyOf(scrollBody(root));
        LinearLayout profile = new LinearLayout(this);
        profile.setOrientation(LinearLayout.HORIZONTAL);
        profile.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout avatar = new FrameLayout(this);
        avatar.setBackground(circleWithStroke(SURFACE, BORDER, 1));
        ImageView avatarIcon = iconView(R.drawable.ic_user_outline, "Клиент", MUTED);
        avatar.addView(avatarIcon, new FrameLayout.LayoutParams(dp(45), dp(45), Gravity.CENTER));
        profile.addView(avatar, new LinearLayout.LayoutParams(dp(78), dp(78)));
        LinearLayout clientCopy = new LinearLayout(this);
        clientCopy.setOrientation(LinearLayout.VERTICAL);
        clientCopy.addView(text(client.name, 20, INK, Typeface.BOLD));
        clientCopy.addView(profileInfoLine(R.drawable.ic_phone,
                client.phone.isEmpty() ? "Телефон не указан" : client.phone), topMargin(-1, 6));
        clientCopy.addView(profileInfoLine(R.drawable.ic_car,
                vehicle(client.car, client.carModel, client.plate)), topMargin(-1, 5));
        profile.addView(clientCopy, leftMargin(-1, 16, 1));
        body.addView(profile);
        int orderCount = 0;
        long orderTotal = 0;
        List<Models.Order> history = new ArrayList<>();
        for (Models.Order order : store.orders) {
            if (!order.clientId.equals(client.id)) continue;
            orderCount++;
            orderTotal += order.total;
            history.add(order);
        }
        history.sort((a, b) -> Long.compare(b.startAt, a.startAt));
        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        summary.addView(clientMetricCard(String.valueOf(orderCount),
                countCaption(orderCount, "заказ", "заказа", "заказов").replaceFirst("^\\d+\\s+", ""),
                R.drawable.ic_nav_orders), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        summary.addView(new Space(this), new LinearLayout.LayoutParams(dp(12), 1));
        summary.addView(clientMetricCard(money(orderTotal), "потрачено", R.drawable.ic_wallet), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        body.addView(summary, topMargin(-1, 20));

        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button call = iconButton("Позвонить", R.drawable.ic_phone);
        call.setOnClickListener(view -> openIntent(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + client.phone))));
        Button sms = iconButton("Написать", R.drawable.ic_message);
        sms.setOnClickListener(view -> openIntent(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + client.phone))));
        actions.addView(call, new LinearLayout.LayoutParams(0, dp(50), 1));
        actions.addView(new Space(this), new LinearLayout.LayoutParams(dp(10), 1));
        actions.addView(sms, new LinearLayout.LayoutParams(0, dp(50), 1));
        body.addView(actions, topMargin(-1, 12));

        body.addView(sectionTitle("История заказов"), topMargin(-1, 22));
        if (history.isEmpty()) body.addView(emptyCard("Заказов пока нет", "Создайте первую запись для этого клиента."), topMargin(-1, 10));
        if (!history.isEmpty()) body.addView(clientHistoryCard(history.get(0)), topMargin(-1, 10));
        if (history.size() > 1) {
            TextView showAll = text("Показать всю историю", 14, BLUE, Typeface.BOLD);
            showAll.setGravity(Gravity.CENTER);
            showAll.setPadding(0, dp(13), 0, dp(7));
            showAll.setOnClickListener(view -> showOrderHistory());
            showAll.setClickable(true);
            body.addView(showAll);
        }
        Button add = primaryButton("+  Новый заказ");
        add.setOnClickListener(view -> showNewOrderDialog(client));
        body.addView(add, topMargin(-1, 16));
        setPage(root);
    }

    private void showClientActions(Models.Client client, Runnable back) {
        Models.CarModel model = store.carModelById(client.carModelId);
        String[] actions = model == null
                ? new String[]{"Изменить автомобиль"}
                : new String[]{"Изменить автомобиль", "Открыть карточку модели"};
        AlertDialog dialog = new AlertDialog.Builder(this).setItems(actions, (ignored, which) -> {
            if (which == 0) editClientVehicle(client, back);
            else showCarModelDetail(model.id, () -> showClientDetail(client.id, back));
        }).create();
        showStyledDialog(dialog);
    }

    private void editClientVehicle(Models.Client client, Runnable back) {
        LinearLayout form = dialogForm();
        AutoCompleteTextView car = autoCompleteField("Марка автомобиля", store.carMakes);
        AutoCompleteTextView model = autoCompleteField("Модель автомобиля", carModelNames(client.car));
        EditText plate = field("Госномер (если известен)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        car.setText(client.car);
        model.setText(client.carModel);
        plate.setText(client.plate);
        bindMakeToModel(car, model, client.car);
        LinearLayout vehicleSection = dialogSection("Автомобиль клиента");
        vehicleSection.addView(labeled(car), topMargin(-1, 8));
        vehicleSection.addView(labeled(model), topMargin(-1, 8));
        vehicleSection.addView(labeled(plate), topMargin(-1, 8));
        form.addView(vehicleSection);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Изменить автомобиль")
                .setView(form)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String carValue = car.getText().toString().trim();
            String modelValue = model.getText().toString().trim();
            if (carValue.isEmpty()) { car.setError("Укажите марку"); return; }
            if (modelValue.isEmpty()) { model.setError("Укажите модель"); return; }
            Models.CarModel selectedModel = store.ensureCarModel(carValue, modelValue);
            if (selectedModel == null) { model.setError("Укажите модель"); return; }
            if (!client.legacyCarNote.isEmpty() && selectedModel.note.isEmpty()) selectedModel.note = client.legacyCarNote;
            client.car = carValue;
            client.carModelId = selectedModel.id;
            client.carModel = selectedModel.name;
            client.plate = plate.getText().toString().trim().toUpperCase(ru);
            client.legacyCarNote = "";
            store.addCarMake(carValue);
            store.save();
            dialog.dismiss();
            showClientDetail(client.id, back);
        }));
        showStyledDialog(dialog);
    }

    private void showOrderDetail(String orderId) {
        String sourceRoute = route;
        showOrderDetail(orderId, () -> showRoute(sourceRoute.equals("finance") ? "finance"
                : sourceRoute.equals("calendar") ? "calendar" : sourceRoute.equals("today") ? "today" : "orders"));
    }

    private void showOrderDetail(String orderId, Runnable back) {
        Models.Order order = store.orderById(orderId);
        if (order == null) return;
        showNavigationSelection("orders");
        LinearLayout root = page("Заказ #" + order.id, "", back, () -> showOrderActions(order, back));
        LinearLayout body = bodyOf(scrollBody(root));
        Models.Client client = store.clientById(order.clientId);
        Models.CarModel model = store.carModelById(order.carModelId);
        TextView statusTop = outlinedStatusPill(order.status);
        statusTop.setGravity(Gravity.CENTER);
        LinearLayout statusWrap = new LinearLayout(this);
        statusWrap.setGravity(Gravity.END);
        statusWrap.addView(statusTop);
        body.addView(statusWrap);

        LinearLayout clientCard = orderClientCard(order, client);
        if (client != null) {
            clientCard.setClickable(true);
            clientCard.setFocusable(true);
            clientCard.setContentDescription("Открыть карточку клиента " + client.name);
            clientCard.setOnClickListener(view -> showClientDetail(client.id, () -> showOrderDetail(order.id, back)));
            addRipple(clientCard);
        }
        body.addView(clientCard, topMargin(-1, 12));

        String schedule = dateTime.format(new Date(order.startAt)) + "  →  " + time.format(new Date(order.deadlineAt));
        LinearLayout scheduleRow = detailLine(R.drawable.ic_nav_calendar, schedule, "");
        scheduleRow.setClickable(true);
        scheduleRow.setOnClickListener(view -> rescheduleOrder(order, back));
        addRipple(scheduleRow);
        body.addView(scheduleRow, topMargin(-1, 10));

        body.addView(sectionTitle("Работы"), topMargin(-1, 20));
        LinearLayout works = card();
        works.setPadding(dp(14), dp(5), dp(14), dp(5));
        int serviceIndex = 0;
        for (String serviceId : order.serviceIds) {
            Models.Service service = store.serviceById(serviceId);
            if (service != null) {
                if (serviceIndex++ > 0) works.addView(thinDivider());
                LinearLayout serviceRow = compactValueRow(service.name, duration(service.durationMinutes), money(order.servicePrice(serviceId)));
                works.addView(serviceRow);
            }
        }
        works.addView(thinDivider());
        works.addView(compactValueRow("Итого", "", money(order.total)));
        body.addView(works, topMargin(-1, 9));

        body.addView(sectionTitle("Фото"), topMargin(-1, 20));
        LinearLayout photos = new LinearLayout(this); photos.setOrientation(LinearLayout.HORIZONTAL);
        photos.addView(photoTile(order, true, back), new LinearLayout.LayoutParams(0, dp(146), 1));
        photos.addView(new Space(this), new LinearLayout.LayoutParams(dp(10), 1));
        photos.addView(photoTile(order, false, back), new LinearLayout.LayoutParams(0, dp(146), 1));
        body.addView(photos, topMargin(-1, 9));

        Button status = primaryButton(nextStatusCaption(order.status));
        status.setOnClickListener(view -> { advanceStatus(order); store.save(); showOrderDetail(order.id, back); });
        body.addView(status, topMargin(-1, 18));
        setPage(root);
    }

    private void showOrderActions(Models.Order order, Runnable back) {
        List<String> captions = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        captions.add("Перенести дату");
        actions.add(() -> rescheduleOrder(order, back));
        captions.add(order.orderNote.isEmpty() ? "Добавить примечание" : "Примечание: " + order.orderNote);
        actions.add(() -> editOrderNote(order, back));
        long advance = orderAdvance(order);
        long balance = orderBalance(order);
        if (!order.paid && balance > 0) {
            captions.add((advance > 0 ? "Добавить аванс · осталось " : "Внести аванс · осталось ") + money(balance));
            actions.add(() -> addAdvance(order, back));
            captions.add(advance > 0 ? "Остаток оплачен полностью" : "Отметить как оплаченный");
            actions.add(() -> {
                order.paid = true;
                store.save();
                showOrderDetail(order.id, back);
            });
        } else {
            captions.add("Оплачено полностью");
            actions.add(() -> toast("Оплата по заказу закрыта"));
        }
        long linkedExpenses = 0;
        for (Models.Transaction transaction : store.transactions) {
            if (!transaction.income && order.id.equals(transaction.orderId)) linkedExpenses += transaction.amount;
        }
        captions.add(linkedExpenses == 0 ? "Добавить расход" : "Расходы: " + money(linkedExpenses) + " · добавить");
        actions.add(() -> addTransaction(false, order));
        Models.CarModel model = store.carModelById(order.carModelId);
        if (model != null) {
            captions.add("Открыть карточку модели");
            actions.add(() -> showCarModelDetail(model.id, () -> showOrderDetail(order.id, back)));
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setItems(captions.toArray(new String[0]), (ignored, which) -> actions.get(which).run()).create();
        showStyledDialog(dialog);
    }

    private void addAdvance(Models.Order order, Runnable back) {
        long balance = orderBalance(order);
        LinearLayout form = dialogForm();
        LinearLayout section = dialogSection("Оплата заказа #" + order.id);
        section.addView(text("Стоимость: " + money(order.total) + "  •  осталось: " + money(balance),
                14, MUTED, Typeface.NORMAL));
        EditText amount = field("Сумма аванса, ₽", InputType.TYPE_CLASS_NUMBER);
        section.addView(labeled(amount), topMargin(-1, 10));
        form.addView(section);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Внести аванс")
                .setView(form).setNegativeButton("Отмена", null).setPositiveButton("Внести", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            long value = parseLong(amount.getText().toString());
            if (value <= 0) { amount.setError("Укажите сумму аванса"); return; }
            if (value > balance) { amount.setError("Не больше " + money(balance)); return; }
            store.transactions.add(new Models.Transaction(store.newId(), "Аванс по заказу #" + order.id,
                    value, System.currentTimeMillis(), true, order.id, "advance"));
            if (value == balance) order.paid = true;
            store.save();
            dialog.dismiss();
            toast(value == balance ? "Заказ оплачен полностью" : "Аванс внесён");
            showOrderDetail(order.id, back);
        }));
        showStyledDialog(dialog);
    }

    private void editOrderNote(Models.Order order, Runnable back) {
        LinearLayout form = dialogForm();
        EditText note = multilineField("Примечание только к этому заказу");
        note.setText(order.orderNote);
        form.addView(labeled(note));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Примечание к заказу")
                .setView(form).setNegativeButton("Отмена", null).setPositiveButton("Сохранить", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            order.orderNote = note.getText().toString().trim();
            store.save(); dialog.dismiss(); showOrderDetail(order.id, back);
        }));
        showStyledDialog(dialog);
    }

    private void rescheduleOrder(Models.Order order, Runnable back) {
        Calendar selected = Calendar.getInstance();
        selected.setTimeInMillis(order.startAt);
        long existingDuration = order.deadlineAt - order.startAt;
        long durationMillis = existingDuration > 0 ? existingDuration : 60 * 60 * 1000L;
        LinearLayout form = dialogForm();
        LinearLayout schedule = dialogSection("Новая дата и время");
        schedule.addView(text("Сейчас: " + dateTime.format(new Date(order.startAt)), 13, MUTED, Typeface.NORMAL));
        Button when = outlineButton(dateTime.format(selected.getTime()), BLUE);
        when.setContentDescription("Выбрать новую дату и время заказа");
        when.setOnClickListener(view -> chooseDateTime(selected, when, ""));
        schedule.addView(when, topMargin(-1, 10));
        schedule.addView(text("Длительность работ сохранится: " + duration((int) (durationMillis / 60000L)),
                13, MUTED, Typeface.NORMAL), topMargin(-1, 8));
        form.addView(schedule);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Перенести заказ")
                .setView(form).setNegativeButton("Отмена", null).setPositiveButton("Перенести", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            order.startAt = selected.getTimeInMillis();
            order.deadlineAt = order.startAt + durationMillis;
            calendarSelected.setTimeInMillis(order.startAt);
            store.sortOrders();
            store.save();
            dialog.dismiss();
            toast("Дата заказа изменена");
            showOrderDetail(order.id, back);
        }));
        showStyledDialog(dialog);
    }

    private View photoTile(Models.Order order, boolean before, Runnable back) {
        List<String> uris = before ? order.beforeUris : order.afterUris;
        String uriText = uris.isEmpty() ? "" : uris.get(uris.size() - 1);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(rounded(Color.rgb(241, 245, 249), 12, 1, BORDER));
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setContentDescription(before ? "Фото до работы" : "Фото после работы");
        if (!uriText.isEmpty()) {
            try { image.setImageURI(Uri.parse(uriText)); } catch (Exception ignored) { }
        }
        frame.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (uriText.isEmpty()) {
            ImageView camera = iconView(R.drawable.ic_camera_outline, before ? "Добавить фото до" : "Добавить фото после", BLUE);
            FrameLayout.LayoutParams cameraParams = new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER);
            cameraParams.bottomMargin = dp(18);
            frame.addView(camera, cameraParams);
        }
        String photoLabel = before ? "До" : "После";
        TextView label = text(uriText.isEmpty() ? photoLabel : photoLabel + " • " + uris.size(), 13,
                uriText.isEmpty() ? BLUE : Color.WHITE, Typeface.BOLD);
        label.setGravity(Gravity.CENTER);
        label.setPadding(dp(10), 0, dp(10), 0);
        if (!uriText.isEmpty()) label.setBackground(rounded(Color.argb(175, 15, 23, 42), 10, 0, Color.TRANSPARENT));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(uriText.isEmpty() ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(44), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.setMargins(dp(8), dp(8), dp(8), dp(10));
        frame.addView(label, lp);
        frame.setContentDescription(uriText.isEmpty() ? "Добавить " + photoLabel.toLowerCase(ru)
                : "Открыть " + photoLabel.toLowerCase(ru) + ", фотографий: " + uris.size());
        frame.setOnClickListener(view -> {
            if (uris.isEmpty()) choosePhoto(order.id, before);
            else showPhotoGallery(order.id, before, uris.size() - 1, back);
        });
        frame.setClickable(true);
        addRipple(frame);
        return frame;
    }

    private void showPhotoGallery(String orderId, boolean before, int requestedIndex, Runnable back) {
        Models.Order order = store.orderById(orderId);
        if (order == null) { back.run(); return; }
        List<String> uris = before ? order.beforeUris : order.afterUris;
        if (uris.isEmpty()) { showOrderDetail(order.id, back); return; }
        int index = Math.max(0, Math.min(requestedIndex, uris.size() - 1));
        navigation.setVisibility(View.GONE);
        String title = before ? "Фото до" : "Фото после";
        LinearLayout root = page(title, (index + 1) + " из " + uris.size(), () -> showOrderDetail(order.id, back));
        LinearLayout body = bodyOf(scrollBody(root));

        FrameLayout preview = new FrameLayout(this);
        preview.setBackground(rounded(Color.rgb(15, 23, 42), 18, 0, Color.TRANSPARENT));
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription(title + ", " + (index + 1) + " из " + uris.size());
        try { image.setImageURI(Uri.parse(uris.get(index))); } catch (Exception ignored) { }
        preview.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        body.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(420)));

        if (uris.size() > 1) {
            LinearLayout paging = new LinearLayout(this);
            paging.setOrientation(LinearLayout.HORIZONTAL);
            paging.setGravity(Gravity.CENTER_VERTICAL);
            Button previous = calendarArrowButton("‹", "Предыдущее фото");
            previous.setEnabled(index > 0);
            previous.setOnClickListener(view -> showPhotoGallery(order.id, before, index - 1, back));
            paging.addView(previous, new LinearLayout.LayoutParams(dp(48), dp(48)));
            TextView counter = text((index + 1) + " / " + uris.size(), 14, MUTED, Typeface.BOLD);
            counter.setGravity(Gravity.CENTER);
            paging.addView(counter, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            Button next = calendarArrowButton("›", "Следующее фото");
            next.setEnabled(index < uris.size() - 1);
            next.setOnClickListener(view -> showPhotoGallery(order.id, before, index + 1, back));
            paging.addView(next, new LinearLayout.LayoutParams(dp(48), dp(48)));
            body.addView(paging, topMargin(-1, 12));
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button add = outlineButton("+ Добавить", BLUE);
        add.setOnClickListener(view -> choosePhoto(order.id, before));
        actions.addView(add, new LinearLayout.LayoutParams(0, dp(54), 1));
        actions.addView(new Space(this), new LinearLayout.LayoutParams(dp(12), 1));
        Button delete = outlineButton("Удалить", RED);
        delete.setContentDescription("Удалить текущее фото из заказа");
        delete.setOnClickListener(view -> confirmDeletePhoto(order, before, index, back));
        actions.addView(delete, new LinearLayout.LayoutParams(0, dp(54), 1));
        body.addView(actions, topMargin(-1, 18));
        body.addView(text("Удаление уберёт фото из заказа, но оставит оригинал на телефоне.",
                13, MUTED, Typeface.NORMAL), topMargin(-1, 10));
        setPage(root);
    }

    private void confirmDeletePhoto(Models.Order order, boolean before, int index, Runnable back) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Удалить фото?")
                .setMessage("Фото исчезнет из этого заказа. Оригинал в галерее телефона останется.")
                .setNegativeButton("Отмена", null)
                .setNeutralButton("Удалить", (ignored, which) -> {
                    List<String> uris = before ? order.beforeUris : order.afterUris;
                    if (index < 0 || index >= uris.size()) return;
                    uris.remove(index);
                    store.save();
                    toast("Фото удалено из заказа");
                    if (uris.isEmpty()) showOrderDetail(order.id, back);
                    else showPhotoGallery(order.id, before, Math.min(index, uris.size() - 1), back);
                }).create();
        showStyledDialog(dialog);
    }

    private void showNewOrderDialog() { showNewOrderDialog(null); }

    private void showNewOrderDialog(Models.Client preset) {
        NewOrderDraft draft = new NewOrderDraft();
        String sourceRoute = route;
        draft.cancel = preset == null ? () -> showRoute(sourceRoute) : () -> showClientDetail(preset.id);
        if (preset != null) selectDraftClient(draft, preset);
        showNewOrderClientStep(draft);
    }

    private LinearLayout newOrderPage(int step, Runnable back) {
        navigation.setVisibility(View.GONE);
        LinearLayout root = page("Новая запись", step + " из 4", back);
        LinearLayout progress = new LinearLayout(this);
        progress.setOrientation(LinearLayout.HORIZONTAL);
        progress.setPadding(dp(20), dp(4), dp(20), dp(8));
        for (int index = 1; index <= 4; index++) {
            View segment = new View(this);
            segment.setBackground(rounded(index <= step ? BLUE : Color.rgb(219, 228, 242), 2, 0, Color.TRANSPARENT));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(4), 1);
            if (index > 1) params.leftMargin = dp(4);
            progress.addView(segment, params);
        }
        root.addView(progress);
        return root;
    }

    private void addNewOrderFooter(LinearLayout root, String caption, Runnable action) {
        LinearLayout footer = new LinearLayout(this);
        footer.setPadding(dp(20), dp(8), dp(20), dp(12));
        footer.setBackgroundColor(SURFACE);
        Button button = primaryButton(caption);
        button.setOnClickListener(view -> action.run());
        footer.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        root.addView(footer);
    }

    private void showNewOrderClientStep(NewOrderDraft draft) {
        LinearLayout root = newOrderPage(1, draft.cancel);
        LinearLayout body = bodyOf(scrollBody(root));
        LinearLayout section = referenceSection("Клиент");
        EditText search = compactField("Поиск клиента", InputType.TYPE_CLASS_TEXT);
        search.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
        search.setCompoundDrawableTintList(ColorStateList.valueOf(MUTED));
        search.setCompoundDrawablePadding(dp(10));
        section.addView(search, topMargin(-1, 12));
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        section.addView(choices, topMargin(-1, 6));
        body.addView(section);

        Button useNew = textActionButton("+  Новый клиент");
        useNew.setOnClickListener(view -> {
            draft.selectedClient = null;
            draft.creatingClient = true;
            draft.clientName = "";
            draft.phone = "";
            showNewOrderClientStep(draft);
        });
        body.addView(useNew, topMargin(-1, 12));

        LinearLayout newClient = referenceSection("Новый клиент");
        EditText name = compactField("Имя клиента", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText phone = compactField("Телефон", InputType.TYPE_CLASS_PHONE);
        name.setText(draft.clientName);
        phone.setText(draft.phone);
        newClient.addView(labeled(name), topMargin(-1, 8));
        newClient.addView(labeled(phone), topMargin(-1, 8));
        newClient.setVisibility(draft.creatingClient ? View.VISIBLE : View.GONE);
        body.addView(newClient, topMargin(-1, 12));

        Runnable refresh = () -> renderDraftClientChoices(choices, search.getText().toString(), draft);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { refresh.run(); }
            @Override public void afterTextChanged(Editable value) { }
        });
        refresh.run();
        addNewOrderFooter(root, "Продолжить  ›", () -> {
            if (draft.selectedClient == null) {
                if (!draft.creatingClient) { toast("Выберите клиента или создайте нового"); return; }
                draft.clientName = name.getText().toString().trim();
                draft.phone = phone.getText().toString().trim();
                if (draft.clientName.isEmpty()) { name.setError("Укажите имя"); return; }
            }
            showNewOrderVehicleStep(draft);
        });
        setPage(root);
    }

    private void renderDraftClientChoices(LinearLayout choices, String query, NewOrderDraft draft) {
        choices.removeAllViews();
        String normalized = query == null ? "" : query.trim().toLowerCase(ru);
        String digits = query == null ? "" : query.replaceAll("[^0-9]", "");
        int shown = 0;
        for (Models.Client client : store.clients) {
            boolean selected = draft.selectedClient != null && draft.selectedClient.id.equals(client.id);
            if (draft.selectedClient != null && !selected) continue;
            String searchable = (client.name + " " + client.phone + " " + vehicle(client.car, client.carModel, client.plate)).toLowerCase(ru);
            if (!selected && !normalized.isEmpty() && !searchable.contains(normalized)
                    && (digits.isEmpty() || !client.phone.replaceAll("[^0-9]", "").contains(digits))) continue;
            if (!selected && normalized.isEmpty() && shown >= 3) continue;
            shown++;
            LinearLayout card = draftClientCard(client, selected);
            card.setClickable(true); card.setFocusable(true);
            card.setContentDescription("Выбрать клиента " + client.name);
            card.setOnClickListener(view -> { selectDraftClient(draft, client); showNewOrderClientStep(draft); });
            addRipple(card);
            choices.addView(card, topMargin(-1, 8));
        }
        if (shown == 0) choices.addView(text("Совпадений нет — заполните нового клиента ниже.", 13, MUTED, Typeface.NORMAL), topMargin(-1, 8));
    }

    private LinearLayout draftClientCard(Models.Client client, boolean selected) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(10), dp(10), dp(10));
        card.setBackground(rounded(selected ? Color.rgb(248, 250, 255) : SURFACE, 10, 1, selected ? Color.rgb(191, 219, 254) : BORDER));
        FrameLayout avatar = new FrameLayout(this);
        avatar.setBackground(circleDrawable(Color.rgb(219, 234, 254)));
        ImageView avatarIcon = iconView(R.drawable.ic_user_outline, "Клиент", BLUE);
        avatar.addView(avatarIcon, new FrameLayout.LayoutParams(dp(25), dp(25), Gravity.CENTER));
        card.addView(avatar, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(client.name, 15, INK, Typeface.BOLD));
        copy.addView(text(client.phone, 13, MUTED, Typeface.NORMAL), topMargin(-1, 3));
        card.addView(copy, leftMargin(-1, 12, 1));
        TextView chevron = text("›", 27, INK, Typeface.NORMAL);
        chevron.setGravity(Gravity.CENTER);
        card.addView(chevron, new LinearLayout.LayoutParams(dp(30), dp(42)));
        return card;
    }

    private void selectDraftClient(NewOrderDraft draft, Models.Client client) {
        draft.selectedClient = client;
        draft.creatingClient = false;
        draft.clientName = client.name;
        draft.phone = client.phone;
        draft.car = client.car;
        draft.carModel = client.carModel;
        draft.plate = client.plate;
    }

    private void showNewOrderVehicleStep(NewOrderDraft draft) {
        LinearLayout root = newOrderPage(2, () -> showNewOrderClientStep(draft));
        LinearLayout body = bodyOf(scrollBody(root));
        LinearLayout section = referenceSection("Автомобиль");
        if (draft.selectedClient != null) section.addView(draftClientCard(draft.selectedClient, true), topMargin(-1, 10));
        AutoCompleteTextView make = autoCompleteField("Марка", store.carMakes);
        AutoCompleteTextView model = autoCompleteField("Модель", carModelNames(draft.car));
        make.setMinHeight(dp(50)); model.setMinHeight(dp(50));
        EditText plate = compactField("Госномер", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        make.setText(draft.car); model.setText(draft.carModel); plate.setText(draft.plate);
        bindMakeToModel(make, model, draft.car);
        section.addView(referenceLabeled(make), topMargin(-1, 14));
        section.addView(referenceLabeled(model), topMargin(-1, 8));
        section.addView(referenceLabeled(plate), topMargin(-1, 8));
        section.addView(text("Данные можно изменить для этой записи.", 13, MUTED, Typeface.NORMAL), topMargin(-1, 12));
        body.addView(section);
        addNewOrderFooter(root, "Продолжить  ›", () -> {
            draft.car = make.getText().toString().trim();
            draft.carModel = model.getText().toString().trim();
            draft.plate = plate.getText().toString().trim().toUpperCase(ru);
            if (draft.car.isEmpty()) { make.setError("Укажите марку"); return; }
            if (draft.carModel.isEmpty()) { model.setError("Укажите модель"); return; }
            showNewOrderWorkStep(draft);
        });
        setPage(root);
    }

    private void showNewOrderWorkStep(NewOrderDraft draft) {
        LinearLayout root = newOrderPage(3, () -> showNewOrderVehicleStep(draft));
        LinearLayout body = bodyOf(scrollBody(root));
        LinearLayout section = referenceSection("Работы");
        section.addView(text("Выберите работы и укажите цену каждой в этом заказе.", 13, MUTED, Typeface.NORMAL), topMargin(-1, 7));
        Map<String, EditText> priceInputs = new HashMap<>();
        TextView selectedSummary = text("", 14, MUTED, Typeface.NORMAL);
        TextView totalSummary = text("", 22, INK, Typeface.BOLD);
        for (Models.Service service : store.services) {
            LinearLayout serviceCard = card();
            serviceCard.setPadding(dp(11), dp(5), dp(11), dp(7));
            CheckBox check = new CheckBox(this);
            check.setText(service.name + "  •  " + duration(service.durationMinutes));
            check.setTextColor(INK); check.setTextSize(15); check.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            check.setPadding(0, 0, 0, 0); check.setMinHeight(dp(42));
            check.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{BLUE, MUTED}));
            LinearLayout priceSlot = new LinearLayout(this); priceSlot.setOrientation(LinearLayout.VERTICAL);
            boolean selected = draft.serviceIds.contains(service.id);
            check.setChecked(selected);
            serviceCard.addView(check);
            serviceCard.addView(priceSlot);
            if (selected) addDraftPriceField(priceSlot, service, draft, priceInputs, selectedSummary, totalSummary);
            check.setOnCheckedChangeListener((button, checked) -> {
                if (checked) {
                    if (!draft.serviceIds.contains(service.id)) draft.serviceIds.add(service.id);
                    addDraftPriceField(priceSlot, service, draft, priceInputs, selectedSummary, totalSummary);
                } else {
                    draft.serviceIds.remove(service.id);
                    draft.servicePrices.remove(service.id);
                    priceInputs.remove(service.id);
                    priceSlot.removeAllViews();
                    updateDraftWorkSummary(draft, selectedSummary, totalSummary);
                }
            });
            section.addView(serviceCard, topMargin(-1, 8));
        }
        if (store.services.isEmpty()) section.addView(text("Сначала добавьте услугу в разделе «Ещё».", 14, RED, Typeface.NORMAL), topMargin(-1, 10));
        LinearLayout.LayoutParams workDividerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        workDividerParams.topMargin = dp(10);
        section.addView(thinDivider(), workDividerParams);
        selectedSummary.setPadding(0, dp(10), 0, dp(7));
        section.addView(selectedSummary);
        LinearLayout totalRow = new LinearLayout(this); totalRow.setOrientation(LinearLayout.HORIZONTAL); totalRow.setGravity(Gravity.CENTER_VERTICAL);
        totalRow.setPadding(0, dp(10), 0, dp(6));
        totalRow.addView(text("Итого", 16, INK, Typeface.BOLD), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        totalRow.addView(totalSummary);
        section.addView(totalRow);
        body.addView(section);
        updateDraftWorkSummary(draft, selectedSummary, totalSummary);
        addNewOrderFooter(root, "Продолжить  ›", () -> {
            if (draft.serviceIds.isEmpty()) { toast("Выберите хотя бы одну работу"); return; }
            for (String serviceId : draft.serviceIds) {
                EditText input = priceInputs.get(serviceId);
                long value = input == null ? draft.servicePrices.getOrDefault(serviceId, 0L) : parseLong(input.getText().toString());
                if (value <= 0) { if (input != null) input.setError("Укажите цену"); return; }
                draft.servicePrices.put(serviceId, value);
            }
            showNewOrderFinalStep(draft);
        });
        setPage(root);
    }

    private void addDraftPriceField(LinearLayout slot, Models.Service service, NewOrderDraft draft,
                                    Map<String, EditText> inputs, TextView selectedSummary, TextView totalSummary) {
        if (inputs.containsKey(service.id)) return;
        slot.removeAllViews();
        EditText price = compactField("Цена в этом заказе", InputType.TYPE_CLASS_NUMBER);
        Long saved = draft.servicePrices.get(service.id);
        if (saved != null && saved > 0) price.setText(String.valueOf(saved));
        inputs.put(service.id, price);
        slot.addView(currencyLabeled(price), topMargin(-1, 4));
        price.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                draft.servicePrices.put(service.id, parseLong(value == null ? "" : value.toString()));
                updateDraftWorkSummary(draft, selectedSummary, totalSummary);
            }
            @Override public void afterTextChanged(Editable value) { }
        });
        updateDraftWorkSummary(draft, selectedSummary, totalSummary);
    }

    private void updateDraftWorkSummary(NewOrderDraft draft, TextView selectedSummary, TextView totalSummary) {
        int minutes = draftDuration(draft);
        selectedSummary.setText(countCaption(draft.serviceIds.size(), "работа", "работы", "работ") + "  •  " + duration(minutes));
        totalSummary.setText(money(draftTotal(draft)));
    }

    private int draftDuration(NewOrderDraft draft) {
        int minutes = 0;
        for (String id : draft.serviceIds) { Models.Service service = store.serviceById(id); if (service != null) minutes += service.durationMinutes; }
        return minutes;
    }

    private long draftTotal(NewOrderDraft draft) {
        long total = 0;
        for (String id : draft.serviceIds) total += Math.max(0, draft.servicePrices.getOrDefault(id, 0L));
        return total;
    }

    private void showNewOrderFinalStep(NewOrderDraft draft) {
        LinearLayout root = newOrderPage(4, () -> showNewOrderWorkStep(draft));
        LinearLayout body = bodyOf(scrollBody(root));
        LinearLayout schedule = referenceSection("Дата и время");
        LinearLayout dateRow = new LinearLayout(this);
        dateRow.setOrientation(LinearLayout.HORIZONTAL);
        dateRow.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout calendarBadge = new FrameLayout(this);
        calendarBadge.setBackground(circleDrawable(Color.rgb(219, 234, 254)));
        ImageView calendarIcon = iconView(R.drawable.ic_nav_calendar, "Дата и время", BLUE);
        calendarBadge.addView(calendarIcon, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        dateRow.addView(calendarBadge, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout dateCopy = new LinearLayout(this);
        dateCopy.setOrientation(LinearLayout.VERTICAL);
        Button when = textActionButton(dateTime.format(draft.start.getTime()));
        when.setTextColor(INK);
        when.setTextSize(16);
        when.setPadding(0, 0, 0, 0);
        TextView deadline = text("Дедлайн: " + time.format(new Date(draft.start.getTimeInMillis() + draftDuration(draft) * 60000L)), 14, MUTED, Typeface.NORMAL);
        when.setOnClickListener(view -> chooseDateTime(draft.start, when, "", () -> deadline.setText("Дедлайн: " + time.format(new Date(draft.start.getTimeInMillis() + draftDuration(draft) * 60000L)))));
        dateCopy.addView(when, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));
        dateCopy.addView(deadline, topMargin(-1, 2));
        dateRow.addView(dateCopy, leftMargin(-1, 12, 1));
        schedule.addView(dateRow, topMargin(-1, 10));
        body.addView(schedule);

        LinearLayout noteSection = referenceSection("Примечание");
        TextView notePreview = text(draft.note.isEmpty() ? "Добавить примечание   ›" : draft.note + "   ›", 14,
                draft.note.isEmpty() ? BLUE : INK, draft.note.isEmpty() ? Typeface.BOLD : Typeface.NORMAL);
        notePreview.setPadding(0, dp(9), 0, dp(7));
        notePreview.setClickable(true);
        notePreview.setOnClickListener(view -> editDraftNote(draft, notePreview));
        noteSection.addView(notePreview, topMargin(-1, 3));
        body.addView(noteSection, topMargin(-1, 12));

        LinearLayout summary = referenceSection("");
        summary.addView(text(draft.clientName + "  •  " + vehicle(draft.car, draft.carModel, draft.plate), 15, INK, Typeface.BOLD));
        int summaryIndex = 0;
        for (String serviceId : draft.serviceIds) {
            Models.Service service = store.serviceById(serviceId);
            if (service != null) {
                if (summaryIndex++ > 0) summary.addView(thinDivider());
                summary.addView(compactValueRow(service.name, duration(service.durationMinutes), money(draft.servicePrices.getOrDefault(serviceId, 0L))));
            }
        }
        summary.addView(thinDivider());
        summary.addView(compactValueRow("Итого", "", money(draftTotal(draft))));
        body.addView(summary, topMargin(-1, 12));
        addNewOrderFooter(root, "Создать запись  ✓", () -> createOrder(draft));
        setPage(root);
    }

    private void editDraftNote(NewOrderDraft draft, TextView preview) {
        LinearLayout form = dialogForm();
        EditText note = multilineField("Что важно учесть в этом заказе");
        note.setText(draft.note);
        form.addView(labeled(note));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Примечание")
                .setView(form).setNegativeButton("Отмена", null).setPositiveButton("Сохранить", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            draft.note = note.getText().toString().trim();
            preview.setText(draft.note.isEmpty() ? "Добавить примечание   ›" : draft.note + "   ›");
            preview.setTextColor(draft.note.isEmpty() ? BLUE : INK);
            dialog.dismiss();
        }));
        showStyledDialog(dialog);
    }

    private void createOrder(NewOrderDraft draft) {
        Models.CarModel selectedModel = store.ensureCarModel(draft.car, draft.carModel);
        if (selectedModel == null) { toast("Не удалось сохранить модель автомобиля"); return; }
        Models.Client client = draft.selectedClient != null ? draft.selectedClient : store.findClientByNameOrPhone(draft.clientName, draft.phone);
        if (client == null) {
            client = new Models.Client(store.newId(), draft.clientName, draft.phone, draft.car, selectedModel.id, selectedModel.name, draft.plate);
            store.clients.add(client);
        } else {
            client.name = draft.clientName; client.phone = draft.phone; client.car = draft.car;
            client.carModelId = selectedModel.id; client.carModel = selectedModel.name; client.plate = draft.plate;
            if (!client.legacyCarNote.isEmpty() && selectedModel.note.isEmpty()) selectedModel.note = client.legacyCarNote;
            client.legacyCarNote = "";
        }
        store.addCarMake(draft.car);
        long start = draft.start.getTimeInMillis();
        String number = String.valueOf(100 + store.orders.size() + 1);
        Models.Order order = new Models.Order(number, client.id, client.name, client.phone, client.car,
                client.carModelId, client.carModel, client.plate, draft.note, draftTotal(draft), start,
                start + draftDuration(draft) * 60000L, "Запланировано", false);
        order.serviceIds.addAll(draft.serviceIds);
        for (String serviceId : draft.serviceIds) order.servicePrices.put(serviceId, draft.servicePrices.getOrDefault(serviceId, 0L));
        store.orders.add(order); store.sortOrders(); store.save();
        showOrderDetail(order.id);
    }

    private void refreshClientMatch(EditText name, EditText phone, EditText car, EditText model, EditText plate,
                                    TextView clientState, LinearLayout suggestions,
                                    Models.Client[] selectedClient, boolean[] updatingClient) {
        if (updatingClient[0]) return;
        String nameQuery = name.getText().toString().trim();
        String normalizedName = nameQuery.toLowerCase(ru);
        String phoneDigits = phone.getText().toString().replaceAll("[^0-9]", "");
        List<Models.Client> exact = new ArrayList<>();
        List<Models.Client> similar = new ArrayList<>();
        for (Models.Client client : store.clients) {
            String clientName = client.name == null ? "" : client.name.trim();
            String clientPhone = client.phone == null ? "" : client.phone.replaceAll("[^0-9]", "");
            boolean exactName = !normalizedName.isEmpty() && clientName.equalsIgnoreCase(nameQuery);
            boolean exactPhone = !phoneDigits.isEmpty() && clientPhone.equals(phoneDigits);
            if (exactName || exactPhone) exact.add(client);
            boolean similarName = normalizedName.length() >= 2 && clientName.toLowerCase(ru).contains(normalizedName);
            boolean similarPhone = phoneDigits.length() >= 3 && clientPhone.contains(phoneDigits);
            if (similarName || similarPhone) similar.add(client);
        }
        if (exact.size() == 1) {
            applyClientSelection(exact.get(0), name, phone, car, model, plate, clientState, suggestions, selectedClient, updatingClient);
            return;
        }

        selectedClient[0] = null;
        suggestions.removeAllViews();
        boolean started = !nameQuery.isEmpty() || !phoneDigits.isEmpty();
        clientState.setText(started ? "Новый клиент • будет добавлен в базу" : "Новый клиент • введите имя или телефон");
        clientState.setTextColor(BLUE_DARK);
        clientState.setBackground(rounded(Color.rgb(239, 246, 255), 12, 0, Color.TRANSPARENT));
        clientState.setContentDescription(clientState.getText());

        if (similar.isEmpty()) return;
        clientState.setText("Похожий клиент найден • выберите ниже");
        clientState.setTextColor(AMBER);
        clientState.setBackground(rounded(Color.rgb(255, 251, 235), 12, 0, Color.TRANSPARENT));
        clientState.setContentDescription(clientState.getText());
        suggestions.addView(text("Похожие клиенты", 13, MUTED, Typeface.BOLD), topMargin(-1, 8));
        int shown = 0;
        for (Models.Client client : similar) {
            if (shown++ >= 3) break;
            LinearLayout suggestion = card();
            suggestion.setPadding(dp(12), dp(11), dp(12), dp(11));
            suggestion.setClickable(true);
            suggestion.setFocusable(true);
            suggestion.setContentDescription("Выбрать клиента " + client.name);
            suggestion.setOnClickListener(view -> applyClientSelection(client, name, phone, car, model, plate,
                    clientState, suggestions, selectedClient, updatingClient));
            addRipple(suggestion);
            suggestion.addView(text(client.name, 15, INK, Typeface.BOLD));
            String vehicle = vehicle(client.car, client.carModel, client.plate);
            String detail = client.phone.isEmpty() ? vehicle : client.phone + (vehicle.isEmpty() ? "" : " • " + vehicle);
            if (!detail.isEmpty()) suggestion.addView(text(detail, 13, MUTED, Typeface.NORMAL), topMargin(-1, 4));
            suggestions.addView(suggestion, topMargin(-1, 8));
        }
    }

    private void applyClientSelection(Models.Client client, EditText name, EditText phone, EditText car,
                                      EditText model, EditText plate,
                                      TextView clientState, LinearLayout suggestions,
                                      Models.Client[] selectedClient, boolean[] updatingClient) {
        updatingClient[0] = true;
        selectedClient[0] = client;
        name.setText(client.name);
        phone.setText(client.phone);
        car.setText(client.car);
        model.setText(client.carModel);
        plate.setText(client.plate);
        updatingClient[0] = false;
        suggestions.removeAllViews();
        clientState.setText("Клиент из базы • " + client.name);
        clientState.setTextColor(GREEN);
        clientState.setBackground(rounded(Color.rgb(236, 253, 245), 12, 0, Color.TRANSPARENT));
        clientState.setContentDescription(clientState.getText());
    }

    private void editService(Models.Service existing) {
        LinearLayout form = dialogForm();
        EditText name = field("Название услуги", InputType.TYPE_CLASS_TEXT);
        EditText category = field("Категория", InputType.TYPE_CLASS_TEXT);
        EditText hours = field("Длительность, часов", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (existing != null) {
            name.setText(existing.name); category.setText(existing.category);
            hours.setText(String.format(ru, "%.1f", existing.durationMinutes / 60f));
        }
        form.addView(labeled(name)); form.addView(labeled(category), topMargin(-1, 8)); form.addView(labeled(hours), topMargin(-1, 8));
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(existing == null ? "Новая услуга" : "Изменить услугу")
                .setView(form).setNegativeButton("Отмена", null).setPositiveButton("Сохранить", null);
        if (existing != null) builder.setNeutralButton("Удалить", null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String title = name.getText().toString().trim();
            float hourValue = parseFloat(hours.getText().toString());
            if (title.isEmpty()) { name.setError("Укажите название"); return; }
            if (hourValue <= 0) { hours.setError("Укажите длительность"); return; }
            Models.Service service = existing;
            if (service == null) { service = new Models.Service(store.newId(), title, "Другое", Math.round(hourValue * 60)); store.services.add(service); }
            service.name = title; service.category = category.getText().toString().trim().isEmpty() ? "Другое" : category.getText().toString().trim();
            service.durationMinutes = Math.round(hourValue * 60);
            store.save(); dialog.dismiss(); showServices();
            });
            if (existing != null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                boolean used = false;
                for (Models.Order order : store.orders) if (order.serviceIds.contains(existing.id)) { used = true; break; }
                if (used) { toast("Услуга используется в заказе и не может быть удалена"); return; }
                store.services.remove(existing); store.save(); dialog.dismiss(); showServices();
            });
        });
        showStyledDialog(dialog);
    }

    private void addTransaction(boolean income) { addTransaction(income, null); }

    private void addTransaction(boolean income, Models.Order presetOrder) {
        LinearLayout form = dialogForm();
        EditText title = field(income ? "Источник дохода" : "На что потрачено", InputType.TYPE_CLASS_TEXT);
        EditText amount = field("Сумма, ₽", InputType.TYPE_CLASS_NUMBER);
        LinearLayout operationSection = dialogSection("Операция");
        operationSection.addView(labeled(title));
        operationSection.addView(labeled(amount), topMargin(-1, 8));
        form.addView(operationSection);

        List<String> orderIds = new ArrayList<>();
        Spinner orderSpinner = null;
        if (!income) {
            LinearLayout orderSection = dialogSection("Связь с заказом");
            TextView orderLabel = text("Связать с заказом", 13, MUTED, Typeface.BOLD);
            orderSection.addView(orderLabel);
            orderSpinner = new Spinner(this);
            orderSpinner.setId(View.generateViewId());
            orderSpinner.setContentDescription("Заказ для расхода");
            orderSpinner.setMinimumHeight(dp(56));
            orderSpinner.setPadding(dp(10), 0, dp(10), 0);
            orderSpinner.setBackground(rounded(SURFACE, 12, 1, BORDER));
            List<String> orderNames = new ArrayList<>();
            orderNames.add("Без заказа");
            orderIds.add("");
            List<Models.Order> orders = new ArrayList<>(store.orders);
            orders.sort((a, b) -> Long.compare(b.startAt, a.startAt));
            int selectedIndex = 0;
            for (Models.Order order : orders) {
                orderNames.add("Заказ #" + order.id + " • " + vehicle(order.car, order.carModel, order.plate));
                orderIds.add(order.id);
                if (presetOrder != null && presetOrder.id.equals(order.id)) selectedIndex = orderIds.size() - 1;
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, orderNames);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            orderSpinner.setAdapter(adapter);
            orderSpinner.setSelection(selectedIndex);
            orderLabel.setLabelFor(orderSpinner.getId());
            orderSection.addView(orderSpinner, topMargin(-1, 5));
            form.addView(orderSection, topMargin(-1, 12));
        }
        Spinner finalOrderSpinner = orderSpinner;
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(income ? "Добавить доход" : "Добавить расход")
                .setView(form).setNegativeButton("Отменить", null).setPositiveButton("Добавить", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String titleText = title.getText().toString().trim(); long amountValue = parseLong(amount.getText().toString());
            if (titleText.isEmpty()) { title.setError("Укажите название"); return; }
            if (amountValue <= 0) { amount.setError("Укажите сумму"); return; }
            String orderId = income || finalOrderSpinner == null ? "" : orderIds.get(finalOrderSpinner.getSelectedItemPosition());
            store.transactions.add(new Models.Transaction(store.newId(), titleText, amountValue, System.currentTimeMillis(), income, orderId));
            store.save(); dialog.dismiss();
            if (presetOrder == null) showFinance(); else showOrderDetail(presetOrder.id);
        }));
        showStyledDialog(dialog);
    }

    private void chooseDateTime(Calendar selected, Button button) {
        chooseDateTime(selected, button, "Время: ");
    }

    private void chooseDateTime(Calendar selected, Button button, String prefix) {
        chooseDateTime(selected, button, prefix, null);
    }

    private void chooseDateTime(Calendar selected, Button button, String prefix, Runnable changed) {
        DatePickerDialog dateDialog = new DatePickerDialog(this, (view, year, month, day) -> {
            selected.set(Calendar.YEAR, year); selected.set(Calendar.MONTH, month); selected.set(Calendar.DAY_OF_MONTH, day);
            TimePickerDialog timeDialog = new TimePickerDialog(this, (picker, hour, minute) -> {
                selected.set(Calendar.HOUR_OF_DAY, hour); selected.set(Calendar.MINUTE, minute); selected.set(Calendar.SECOND, 0);
                button.setText(prefix + dateTime.format(selected.getTime()));
                if (changed != null) changed.run();
            }, selected.get(Calendar.HOUR_OF_DAY), selected.get(Calendar.MINUTE), true);
            timeDialog.show();
        }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH));
        dateDialog.show();
    }

    private void choosePhoto(String orderId, boolean before) {
        photoOrderId = orderId;
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(before ? "Фото до работы" : "Фото после работы")
                .setItems(new String[]{"Снять камерой", "Выбрать из галереи"}, (ignoredDialog, which) -> {
                    if (which == 0) openCamera(before); else openGallery(before);
                }).create();
        showStyledDialog(dialog);
    }

    private void openCamera(boolean before) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "detailflow_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DetailFlow");
        pendingCameraUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (pendingCameraUri == null) { toast("Не удалось подготовить файл"); return; }
        Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        camera.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
        camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(camera, before ? REQ_CAMERA_BEFORE : REQ_CAMERA_AFTER); }
        catch (Exception error) { toast("Камера недоступна"); }
    }

    private void openGallery(boolean before) {
        Intent gallery = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        gallery.addCategory(Intent.CATEGORY_OPENABLE);
        gallery.setType("image/*");
        gallery.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(gallery, before ? REQ_GALLERY_BEFORE : REQ_GALLERY_AFTER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_EXPORT_DATABASE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) writeDatabaseArchive(data.getData());
            return;
        }
        if (requestCode == REQ_IMPORT_DATABASE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) prepareDatabaseImport(data.getData());
            return;
        }
        if (resultCode != RESULT_OK || photoOrderId == null) return;
        boolean before = requestCode == REQ_CAMERA_BEFORE || requestCode == REQ_GALLERY_BEFORE;
        Uri uri = (requestCode == REQ_CAMERA_BEFORE || requestCode == REQ_CAMERA_AFTER) ? pendingCameraUri : (data == null ? null : data.getData());
        if (uri == null) return;
        if (data != null && (requestCode == REQ_GALLERY_BEFORE || requestCode == REQ_GALLERY_AFTER)) {
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
        }
        Models.Order order = store.orderById(photoOrderId);
        if (order != null) {
            if (before) order.beforeUris.add(uri.toString()); else order.afterUris.add(uri.toString());
            store.save(); showOrderDetail(order.id);
        }
    }

    private LinearLayout orderCard(Models.Order order) {
        LinearLayout card = card();
        card.setPadding(dp(14), dp(10), dp(14), dp(10));
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription("Открыть заказ " + order.id);
        card.setOnClickListener(view -> showOrderDetail(order.id));
        addRipple(card);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text("Заказ #" + order.id, 18, BLUE, Typeface.BOLD),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView payment = paymentPill(order);
        payment.setTextSize(11.5f);
        payment.setPadding(dp(9), dp(4), dp(9), dp(4));
        payment.setMinHeight(dp(28));
        top.addView(payment);
        card.addView(top);

        String car = vehicle(order.car, order.carModel, order.plate);
        LinearLayout vehicleRow = new LinearLayout(this);
        vehicleRow.setOrientation(LinearLayout.HORIZONTAL);
        vehicleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView vehicle = text(car.isEmpty() ? "Автомобиль не указан" : car, 16, INK, Typeface.BOLD);
        vehicle.setSingleLine(true);
        vehicle.setEllipsize(TextUtils.TruncateAt.END);
        vehicleRow.addView(vehicle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView chevron = text("›", 26, INK, Typeface.NORMAL);
        chevron.setGravity(Gravity.CENTER);
        vehicleRow.addView(chevron, new LinearLayout.LayoutParams(dp(28), dp(32)));
        card.addView(vehicleRow, topMargin(-1, 6));

        LinearLayout clientRow = profileInfoLine(R.drawable.ic_user_outline, order.clientName);
        card.addView(clientRow, topMargin(-1, 2));

        String details = dateTime.format(new Date(order.startAt)) + "  •  " + serviceNames(order);
        TextView detail = text(details, 13, MUTED, Typeface.NORMAL);
        detail.setSingleLine(true);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(detail, topMargin(-1, 5));
        return card;
    }

    private LinearLayout todayOrderCard(Models.Order order) {
        int accent = order.status.equals("Запланировано") ? AMBER : BLUE;
        LinearLayout item = card();
        item.setPadding(dp(14), dp(14), dp(14), dp(13));
        item.setBackground(rounded(SURFACE, 12, 1, withAlpha(accent, 90)));
        item.setClickable(true); item.setFocusable(true);
        item.setContentDescription("Открыть заказ " + order.id);
        item.setOnClickListener(view -> showOrderDetail(order.id));
        addRipple(item);

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView timeView = text(time.format(new Date(order.startAt)), 19, accent, Typeface.BOLD);
        heading.addView(timeView, new LinearLayout.LayoutParams(dp(70), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView dot = text("•", 20, accent, Typeface.BOLD); dot.setGravity(Gravity.CENTER);
        heading.addView(dot, new LinearLayout.LayoutParams(dp(24), ViewGroup.LayoutParams.WRAP_CONTENT));
        String vehicle = vehicle(order.car, order.carModel, "");
        heading.addView(text(vehicle.isEmpty() ? "Автомобиль" : vehicle, 17, INK, Typeface.BOLD),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        heading.addView(text("›", 26, INK, Typeface.NORMAL));
        item.addView(heading);

        TextView services = text(serviceNames(order), 14, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams servicesParams = topMargin(-1, 5);
        servicesParams.leftMargin = dp(94);
        item.addView(services, servicesParams);
        TextView status = statusPill(order.status);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(8);
        statusParams.leftMargin = dp(94);
        item.addView(status, statusParams);
        return item;
    }

    private LinearLayout orderHistoryCard(Models.Order order) {
        LinearLayout card = card();
        card.setClickable(true);
        card.setOnClickListener(view -> showOrderDetail(order.id));
        addRipple(card);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text("Заказ #" + order.id, 18, BLUE, Typeface.BOLD));
        TextView schedule = text(dateTime.format(new Date(order.startAt)), 17, INK, Typeface.BOLD);
        schedule.setGravity(Gravity.END);
        top.addView(schedule, leftMargin(0, 12, 1));
        card.addView(top);

        TextView services = text(serviceNames(order), 15, BLUE_DARK, Typeface.BOLD);
        services.setGravity(Gravity.END);
        card.addView(services, topMargin(-1, 7));

        String vehicle = vehicle(order.car, order.carModel, order.plate);
        card.addView(text(vehicle.isEmpty() ? "Автомобиль не указан" : vehicle, 15, INK, Typeface.BOLD), topMargin(-1, 9));
        card.addView(text(order.clientName, 13, MUTED, Typeface.NORMAL), topMargin(-1, 4));
        if (!order.phone.isEmpty()) card.addView(text(order.phone, 13, MUTED, Typeface.NORMAL), topMargin(-1, 4));
        card.addView(cardStatusRow(order), topMargin(-1, 11));
        return card;
    }

    private LinearLayout clientHistoryCard(Models.Order order) {
        LinearLayout item = card();
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(12), dp(11), dp(12), dp(11));
        item.setClickable(true);
        item.setOnClickListener(view -> showOrderDetail(order.id));
        addRipple(item);

        TextView check = text(order.status.equals("Завершено") ? "✓" : "•", 25, Color.WHITE, Typeface.BOLD);
        check.setGravity(Gravity.CENTER);
        check.setBackground(circleDrawable(order.status.equals("Завершено") ? GREEN : BLUE));
        item.addView(check, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text("Заказ #" + order.id, 14, INK, Typeface.BOLD));
        TextView services = text(serviceNames(order), 12, MUTED, Typeface.NORMAL);
        services.setSingleLine(true);
        services.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(services, topMargin(-1, 3));
        copy.addView(text(dateTime.format(new Date(order.startAt)), 11, MUTED, Typeface.NORMAL), topMargin(-1, 3));
        item.addView(copy, leftMargin(-1, 11, 1));

        LinearLayout value = new LinearLayout(this);
        value.setOrientation(LinearLayout.VERTICAL);
        value.setGravity(Gravity.END);
        TextView total = text(money(order.total), 13, INK, Typeface.BOLD);
        total.setGravity(Gravity.END);
        value.addView(total);
        TextView payment = paymentPill(order);
        payment.setTextSize(11);
        payment.setPadding(dp(8), dp(4), dp(8), dp(4));
        payment.setMinHeight(dp(28));
        value.addView(payment, topMargin(-1, 5));
        item.addView(value, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return item;
    }

    private LinearLayout timelineCard(Models.Order order) {
        LinearLayout item = card();
        item.setBackground(rounded(Color.rgb(239, 246, 255), 16, 1, Color.rgb(147, 197, 253)));
        item.setOnClickListener(view -> showOrderDetail(order.id)); item.setClickable(true);
        addRipple(item);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text("Заказ #" + order.id, 17, BLUE, Typeface.BOLD));
        TextView schedule = text(time.format(new Date(order.startAt)), 18, INK, Typeface.BOLD);
        schedule.setGravity(Gravity.END);
        top.addView(schedule, leftMargin(0, 12, 1));
        item.addView(top);
        TextView services = text(serviceNames(order), 15, BLUE_DARK, Typeface.BOLD);
        services.setGravity(Gravity.END);
        item.addView(services, topMargin(-1, 7));
        String car = vehicle(order.car, order.carModel, order.plate);
        item.addView(text(car.isEmpty() ? "Автомобиль не указан" : car, 15, INK, Typeface.BOLD), topMargin(-1, 8));
        item.addView(text(order.clientName, 13, MUTED, Typeface.NORMAL), topMargin(-1, 4));
        item.addView(cardStatusRow(order), topMargin(-1, 10));
        return item;
    }

    private LinearLayout cardStatusRow(Models.Order order) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(statusPill(order.status));
        row.addView(new Space(this), new LinearLayout.LayoutParams(dp(8), 1));
        row.addView(paymentPill(order));
        return row;
    }

    private LinearLayout freeSlot(String range) {
        LinearLayout item = card();
        item.setBackground(rounded(Color.TRANSPARENT, 16, 1, Color.rgb(148, 163, 184)));
        item.addView(text("Свободно  •  " + range, 14, MUTED, Typeface.NORMAL));
        item.setOnClickListener(view -> showNewOrderDialog()); item.setClickable(true);
        addRipple(item);
        return item;
    }

    private TextView statusPill(String status) {
        int color = status.equals("Завершено") ? GREEN : status.equals("В работе") ? BLUE : AMBER;
        TextView pill = text(status, 13, color, Typeface.BOLD);
        pill.setGravity(Gravity.CENTER);
        pill.setBackground(rounded(withAlpha(color, 22), 12, 0, Color.TRANSPARENT));
        pill.setPadding(dp(12), dp(7), dp(12), dp(7));
        pill.setMinHeight(dp(36));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pill.setLayoutParams(lp);
        return pill;
    }

    private TextView outlinedStatusPill(String status) {
        int color = status.equals("Завершено") ? GREEN : status.equals("В работе") ? BLUE : AMBER;
        TextView pill = text(status, 12, color, Typeface.BOLD);
        if (status.equals("В работе")) {
            pill.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_wrench, 0, 0, 0);
            pill.setCompoundDrawableTintList(ColorStateList.valueOf(color));
            pill.setCompoundDrawablePadding(dp(6));
        }
        pill.setPadding(dp(13), dp(6), dp(13), dp(6));
        pill.setBackground(rounded(SURFACE, 14, 1, withAlpha(color, 145)));
        pill.setMinHeight(dp(30));
        return pill;
    }

    private TextView outlineChip(String value) {
        TextView chip = text(value, 12, BLUE, Typeface.NORMAL);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), dp(4), dp(10), dp(4));
        chip.setBackground(rounded(SURFACE, 8, 1, BLUE));
        chip.setMinHeight(dp(28));
        chip.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return chip;
    }

    private LinearLayout detailLine(int iconResource, String title, String subtitle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(4), dp(2), dp(4));
        ImageView icon = iconView(iconResource, subtitle, BLUE);
        row.addView(icon, new LinearLayout.LayoutParams(dp(25), dp(25)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView heading = text(title, 15, INK, Typeface.BOLD);
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(heading);
        if (subtitle != null && !subtitle.isEmpty()) copy.addView(text(subtitle, 12, MUTED, Typeface.NORMAL), topMargin(-1, 1));
        row.addView(copy, leftMargin(-1, 12, 1));
        return row;
    }

    private LinearLayout orderClientCard(Models.Order order, Models.Client client) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(12));
        card.setBackground(rounded(SURFACE, 12, 1, BORDER));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout avatar = new FrameLayout(this);
        avatar.setBackground(circleWithStroke(Color.rgb(248, 250, 252), BORDER, 1));
        ImageView avatarIcon = iconView(R.drawable.ic_user_outline, "Клиент", MUTED);
        avatar.addView(avatarIcon, new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER));
        top.addView(avatar, new LinearLayout.LayoutParams(dp(58), dp(58)));

        String clientName = client == null ? order.clientName : client.name;
        String clientPhone = client == null || client.phone.isEmpty() ? order.phone : client.phone;
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(clientName, 16, INK, Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(name);
        copy.addView(profileInfoLine(R.drawable.ic_phone,
                clientPhone.isEmpty() ? "Телефон не указан" : clientPhone), topMargin(-1, 4));
        copy.addView(profileInfoLine(R.drawable.ic_car,
                vehicle(order.car, order.carModel, order.plate)), topMargin(-1, 3));
        top.addView(copy, leftMargin(-1, 13, 1));
        TextView chevron = text("›", 27, INK, Typeface.NORMAL);
        chevron.setGravity(Gravity.CENTER);
        top.addView(chevron, new LinearLayout.LayoutParams(dp(28), dp(50)));
        card.addView(top);

        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dividerParams.topMargin = dp(11);
        card.addView(thinDivider(), dividerParams);

        int orderCount = 0;
        long orderTotal = 0;
        for (Models.Order item : store.orders) {
            if (!item.clientId.equals(order.clientId)) continue;
            orderCount++;
            orderTotal += item.total;
        }
        TextView meta = text(countCaption(orderCount, "заказ", "заказа", "заказов") + "  •  " + money(orderTotal),
                13, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.leftMargin = dp(71);
        metaParams.topMargin = dp(9);
        card.addView(meta, metaParams);
        return card;
    }

    private LinearLayout compactValueRow(String title, String subtitle, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(title, 14, INK, Typeface.BOLD));
        if (subtitle != null && !subtitle.isEmpty()) copy.addView(text(subtitle, 12, MUTED, Typeface.NORMAL), topMargin(-1, 2));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(text(value + (subtitle == null || subtitle.isEmpty() ? "" : "   ›"), 14, INK, Typeface.BOLD));
        return row;
    }

    private View thinDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(BORDER);
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return divider;
    }

    private TextView paymentPill(Models.Order order) {
        long advance = orderAdvance(order);
        String caption = order.paid ? "Оплачено" : advance > 0 ? "Аванс " + money(advance) : "Не оплачено";
        int color = order.paid ? GREEN : advance > 0 ? BLUE_DARK : MUTED;
        TextView pill = text(caption, 13, color, Typeface.BOLD);
        pill.setGravity(Gravity.CENTER);
        pill.setBackground(rounded(withAlpha(color, 20), 12, 0, Color.TRANSPARENT));
        pill.setPadding(dp(12), dp(7), dp(12), dp(7));
        pill.setMinHeight(dp(36));
        pill.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return pill;
    }

    private LinearLayout metricCard(String label, String value, int accent) {
        LinearLayout item = card();
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setMinimumHeight(dp(92));
        item.setPadding(dp(14), dp(12), dp(14), dp(12));
        int iconResource = label.toLowerCase(ru).contains("выруч") || label.toLowerCase(ru).contains("сумм")
                ? R.drawable.ic_wallet : R.drawable.ic_nav_orders;
        ImageView icon = iconView(iconResource, label, BLUE);
        item.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(label, 12, MUTED, Typeface.NORMAL));
        copy.addView(text(value, 20, INK, Typeface.BOLD), topMargin(-1, 3));
        item.addView(copy, leftMargin(-1, 10, 1));
        return item;
    }

    private LinearLayout clientMetricCard(String value, String label, int iconResource) {
        LinearLayout item = card();
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setMinimumHeight(dp(86));
        item.setPadding(dp(13), dp(11), dp(12), dp(11));
        ImageView icon = iconView(iconResource, label, BLUE);
        item.addView(icon, new LinearLayout.LayoutParams(dp(35), dp(35)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(value, 17, INK, Typeface.BOLD));
        copy.addView(text(label, 12, MUTED, Typeface.NORMAL), topMargin(-1, 2));
        item.addView(copy, leftMargin(-1, 10, 1));
        return item;
    }

    private LinearLayout profileInfoLine(int iconResource, String value) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = iconView(iconResource, value, INK);
        line.addView(icon, new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView copy = text(value, 13, MUTED, Typeface.NORMAL);
        copy.setSingleLine(true);
        copy.setEllipsize(TextUtils.TruncateAt.END);
        line.addView(copy, leftMargin(-1, 7, 1));
        return line;
    }

    private LinearLayout emptyCard(String title, String subtitle) {
        LinearLayout item = card(); item.setGravity(Gravity.CENTER); item.setPadding(dp(18), dp(24), dp(18), dp(24));
        TextView heading = text(title, 17, INK, Typeface.BOLD); heading.setGravity(Gravity.CENTER); item.addView(heading);
        TextView description = text(subtitle, 14, MUTED, Typeface.NORMAL); description.setGravity(Gravity.CENTER); item.addView(description, topMargin(-1, 7));
        return item;
    }

    private LinearLayout infoRow(String label, String value, int accent) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14)); row.setBackground(rounded(SURFACE, 14, 1, BORDER));
        row.addView(text(label, 15, INK, Typeface.NORMAL), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(text(value, 15, accent, Typeface.BOLD));
        return row;
    }

    private LinearLayout transactionCard(Models.Transaction transaction) {
        LinearLayout item = card();
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text(transaction.title, 15, INK, Typeface.BOLD), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(text((transaction.income ? "+" : "−") + money(transaction.amount), 15,
                transaction.income ? GREEN : RED, Typeface.BOLD));
        item.addView(row);
        String detail = dateTime.format(new Date(transaction.createdAt));
        if (!transaction.orderId.isEmpty()) {
            Models.Order order = store.orderById(transaction.orderId);
            detail += order == null ? " • Заказ #" + transaction.orderId : " • Заказ #" + order.id + " • " + vehicle(order.car, order.carModel, order.plate);
        } else if (!transaction.income) {
            detail += " • Без заказа";
        }
        item.addView(text(detail, 13, MUTED, Typeface.NORMAL), topMargin(-1, 7));
        return item;
    }

    private LinearLayout paidOrderIncomeCard(Models.Order order) {
        LinearLayout item = card();
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription("Открыть оплаченный заказ " + order.id);
        item.setOnClickListener(view -> showOrderDetail(order.id, this::showFinance));
        addRipple(item);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text("Заказ #" + order.id + " • " + order.clientName, 15, INK, Typeface.BOLD),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(text("+" + money(orderBalance(order)), 15, GREEN, Typeface.BOLD));
        item.addView(row);
        String detail = dateTime.format(new Date(order.startAt)) + " • Оплаченный заказ";
        String car = vehicle(order.car, order.carModel, order.plate);
        if (!car.isEmpty()) detail += " • " + car;
        item.addView(text(detail, 13, MUTED, Typeface.NORMAL), topMargin(-1, 7));
        return item;
    }

    private LinearLayout actionCard(String title, String subtitle, Runnable action) {
        LinearLayout item = card(); item.setClickable(true); item.setOnClickListener(view -> action.run());
        addRipple(item);
        item.addView(text(title, 18, INK, Typeface.BOLD));
        item.addView(text(subtitle, 14, MUTED, Typeface.NORMAL), topMargin(-1, 6));
        return item;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14)); card.setBackground(rounded(SURFACE, 12, 1, BORDER)); card.setElevation(0);
        card.setMinimumHeight(dp(56));
        return card;
    }

    private LinearLayout cardWithColor(int color) {
        LinearLayout item = card(); item.setBackground(rounded(color, 12, 0, Color.TRANSPARENT)); item.setPadding(dp(16), dp(16), dp(16), dp(16));
        return item;
    }

    private TextView sectionTitle(String value) { return text(value, 19, INK, Typeface.BOLD); }

    private Button primaryButton(String caption) {
        Button button = new Button(this); button.setText(caption); button.setTextColor(Color.WHITE); button.setTextSize(16); button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false); button.setGravity(Gravity.CENTER); button.setMinHeight(dp(54)); button.setBackground(rounded(BLUE, 14, 0, Color.TRANSPARENT));
        button.setStateListAnimator(null); return button;
    }

    private Button outlineButton(String caption, int color) {
        Button button = new Button(this); button.setText(caption); button.setTextColor(color); button.setTextSize(15); button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false); button.setGravity(Gravity.CENTER); button.setMinHeight(dp(52)); button.setBackground(rounded(SURFACE, 14, 1, color));
        button.setStateListAnimator(null); return button;
    }

    private Button iconButton(String caption, int iconResource) {
        Button button = outlineButton(caption, BLUE);
        button.setCompoundDrawablesWithIntrinsicBounds(iconResource, 0, 0, 0);
        button.setCompoundDrawableTintList(ColorStateList.valueOf(BLUE));
        button.setCompoundDrawablePadding(dp(8));
        return button;
    }

    private Button compactLinkButton(String caption) {
        Button button = new Button(this);
        button.setText(caption);
        button.setTextColor(BLUE_DARK);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setPadding(dp(13), 0, dp(10), 0);
        button.setMinHeight(dp(48));
        button.setBackground(rounded(Color.rgb(239, 246, 255), 12, 0, Color.TRANSPARENT));
        button.setStateListAnimator(null);
        return button;
    }

    private Button textActionButton(String caption) {
        Button button = new Button(this);
        button.setText(caption);
        button.setTextColor(BLUE);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setPadding(0, 0, 0, 0);
        button.setMinHeight(dp(42));
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setStateListAnimator(null);
        return button;
    }

    private LinearLayout sectionHeaderWithAction(String title, String action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(sectionTitle(title), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button button = new Button(this);
        button.setText(action);
        button.setTextColor(BLUE);
        button.setTextSize(21);
        button.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, dp(2));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setBackground(rounded(Color.rgb(239, 246, 255), 11, 0, Color.TRANSPARENT));
        button.setStateListAnimator(null);
        row.addView(button, new LinearLayout.LayoutParams(dp(42), dp(42)));
        return row;
    }

    private EditText field(String hint, int inputType) {
        EditText input = new EditText(this); input.setHint(hint); input.setTextColor(INK); input.setHintTextColor(MUTED); input.setTextSize(16); input.setSingleLine(true);
        input.setId(View.generateViewId());
        input.setInputType(inputType); input.setPadding(dp(14), 0, dp(14), 0); input.setBackground(rounded(SURFACE, 12, 1, BORDER)); input.setMinHeight(dp(56));
        return input;
    }

    private EditText compactField(String hint, int inputType) {
        EditText input = field(hint, inputType);
        input.setMinHeight(dp(50));
        input.setTextSize(15);
        input.setPadding(dp(12), 0, dp(12), 0);
        return input;
    }

    private AutoCompleteTextView autoCompleteField(String hint, List<String> values) {
        AutoCompleteTextView input = new AutoCompleteTextView(this);
        input.setHint(hint); input.setTextColor(INK); input.setHintTextColor(MUTED); input.setTextSize(16); input.setSingleLine(true);
        input.setId(View.generateViewId());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setPadding(dp(14), 0, dp(14), 0); input.setBackground(rounded(SURFACE, 12, 1, BORDER)); input.setMinHeight(dp(56));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>(values));
        input.setAdapter(adapter);
        input.setThreshold(0);
        input.setDropDownHeight(dp(112));
        input.setDropDownBackgroundDrawable(rounded(SURFACE, 12, 1, BORDER));
        input.setOnClickListener(view -> input.postDelayed(input::showDropDown, 80));
        input.setOnFocusChangeListener((view, focused) -> {
            if (focused) input.postDelayed(input::showDropDown, 120);
        });
        return input;
    }

    private List<String> carModelNames(String make) {
        List<String> names = new ArrayList<>();
        for (Models.CarModel model : store.carModelsForMake(make == null ? "" : make)) names.add(model.name);
        return names;
    }

    private void bindMakeToModel(AutoCompleteTextView make, AutoCompleteTextView model, String initialMake) {
        String[] activeMake = {initialMake == null ? "" : initialMake.trim()};
        make.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable value) {
                String newMake = value == null ? "" : value.toString().trim();
                if (!newMake.equalsIgnoreCase(activeMake[0])) model.setText("");
                activeMake[0] = newMake;
                ArrayAdapter<String> adapter = new ArrayAdapter<>(MainActivity.this,
                        android.R.layout.simple_dropdown_item_1line, carModelNames(newMake));
                model.setAdapter(adapter);
            }
        });
    }

    private EditText multilineField(String hint) {
        EditText input = field(hint, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setSingleLine(false);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinHeight(dp(88));
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        return input;
    }

    private LinearLayout labeled(EditText input) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        CharSequence hint = input.getHint();
        TextView label = text(hint == null ? "Поле" : hint.toString(), 13, MUTED, Typeface.BOLD);
        label.setLabelFor(input.getId());
        input.setHint("");
        wrapper.addView(label);
        wrapper.addView(input, topMargin(-1, 5));
        return wrapper;
    }

    private LinearLayout referenceLabeled(EditText input) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        CharSequence hint = input.getHint();
        TextView label = text(hint == null ? "Поле" : hint.toString(), 12, MUTED, Typeface.NORMAL);
        label.setLabelFor(input.getId());
        input.setHint("");
        input.setTextSize(20);
        input.setPadding(0, 0, 0, 0);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setMinHeight(dp(42));
        wrapper.addView(label);
        wrapper.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(43)));
        wrapper.addView(thinDivider());
        return wrapper;
    }

    private LinearLayout currencyLabeled(EditText input) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        TextView label = text("Цена в этом заказе", 12, MUTED, Typeface.NORMAL);
        label.setLabelFor(input.getId());
        input.setHint("");
        input.setPadding(dp(12), 0, dp(42), 0);
        FrameLayout fieldFrame = new FrameLayout(this);
        fieldFrame.addView(input, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        TextView currency = text("₽", 16, MUTED, Typeface.NORMAL);
        currency.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams currencyParams = new FrameLayout.LayoutParams(dp(40), dp(50), Gravity.END | Gravity.CENTER_VERTICAL);
        fieldFrame.addView(currency, currencyParams);
        wrapper.addView(label);
        wrapper.addView(fieldFrame, topMargin(-1, 5));
        return wrapper;
    }

    private LinearLayout dialogForm() {
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(4), dp(6), dp(4), dp(6)); return form;
    }

    private LinearLayout dialogSection(String title) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(12), dp(12), dp(12), dp(14));
        section.setBackground(rounded(Color.rgb(248, 250, 252), 18, 1, BORDER));
        section.addView(text(title, 16, INK, Typeface.BOLD));
        return section;
    }

    private LinearLayout referenceSection(String title) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(14), dp(12), dp(14), dp(13));
        section.setBackground(rounded(SURFACE, 12, 1, BORDER));
        if (title != null && !title.isEmpty()) section.addView(text(title, 15, INK, Typeface.BOLD));
        return section;
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(this); view.setText(value); view.setTextColor(color); view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        view.setTypeface(Typeface.DEFAULT, style); view.setLineSpacing(0, 1.08f); return view;
    }

    private ImageView iconView(int resource, String description, int tint) {
        ImageView icon = new ImageView(this);
        icon.setImageResource(resource);
        icon.setImageTintList(ColorStateList.valueOf(tint));
        icon.setContentDescription(description);
        return icon;
    }

    private GradientDrawable rounded(int fill, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(fill); drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), strokeColor); return drawable;
    }

    private GradientDrawable dashedRounded(int fill, int radiusDp, int strokeColor) {
        GradientDrawable drawable = rounded(fill, radiusDp, 0, Color.TRANSPARENT);
        drawable.setStroke(dp(1), strokeColor, dp(5), dp(4));
        return drawable;
    }

    private GradientDrawable circleDrawable(int fill) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fill);
        return drawable;
    }

    private GradientDrawable circleWithStroke(int fill, int strokeColor, int strokeDp) {
        GradientDrawable drawable = circleDrawable(fill);
        drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    private void addRipple(View view) {
        view.setForeground(new RippleDrawable(ColorStateList.valueOf(withAlpha(BLUE, 28)), null, null));
    }

    private LinearLayout.LayoutParams topMargin(int width, int margin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width < 0 ? ViewGroup.LayoutParams.MATCH_PARENT : width, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(margin); return lp;
    }

    private LinearLayout.LayoutParams leftMargin(int height, int margin, float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, height <= 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : dp(height), weight);
        lp.leftMargin = dp(margin); return lp;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private String money(long value) { return moneyFormat.format(value) + " ₽"; }

    private String modelCountCaption(int count) {
        int mod100 = count % 100;
        int mod10 = count % 10;
        if (mod100 >= 11 && mod100 <= 14) return "моделей";
        if (mod10 == 1) return "модель";
        if (mod10 >= 2 && mod10 <= 4) return "модели";
        return "моделей";
    }

    private String countCaption(int count, String one, String few, String many) {
        int mod100 = count % 100;
        int mod10 = count % 10;
        String caption = mod100 >= 11 && mod100 <= 14 ? many : mod10 == 1 ? one : mod10 >= 2 && mod10 <= 4 ? few : many;
        return count + " " + caption;
    }

    private String vehicle(String make, String model, String plate) {
        String safeMake = make == null ? "" : make.trim();
        String safeModel = model == null ? "" : model.trim();
        String safePlate = plate == null ? "" : plate.trim();
        String title = safeMake;
        if (!safeModel.isEmpty()) title = title.isEmpty() ? safeModel : title + " " + safeModel;
        if (title.isEmpty()) return safePlate;
        return safePlate.isEmpty() ? title : title + " • " + safePlate;
    }

    private String duration(int minutes) {
        int hours = minutes / 60; int rest = minutes % 60;
        return rest == 0 ? hours + " ч" : hours + " ч " + rest + " мин";
    }

    private String serviceNames(Models.Order order) {
        StringBuilder result = new StringBuilder();
        for (String id : order.serviceIds) {
            Models.Service service = store.serviceById(id); if (service == null) continue;
            if (result.length() > 0) result.append(" + "); result.append(service.name);
        }
        return result.length() == 0 ? "Работы не указаны" : result.toString();
    }

    private List<Models.Order> upcomingOrders() {
        List<Models.Order> result = new ArrayList<>();
        long threshold = System.currentTimeMillis() - 12 * 60 * 60 * 1000L;
        for (Models.Order order : store.orders) if (order.startAt >= threshold && !order.status.equals("Завершено")) result.add(order);
        result.sort((a, b) -> Long.compare(a.startAt, b.startAt)); return result;
    }

    private List<Models.Order> ordersForDay(long millis) {
        List<Models.Order> result = new ArrayList<>();
        for (Models.Order order : store.orders) if (sameDay(order.startAt, millis)) result.add(order);
        result.sort((a, b) -> Long.compare(a.startAt, b.startAt)); return result;
    }

    private long monthRevenue() {
        long value = 0;
        for (Models.Order order : store.orders) if (order.paid && sameMonth(order.startAt, System.currentTimeMillis())) value += orderBalance(order);
        return value;
    }

    private long orderAdvance(Models.Order order) {
        long value = 0;
        for (Models.Transaction transaction : store.transactions) {
            if (transaction.income && order.id.equals(transaction.orderId) && "advance".equals(transaction.kind)) value += transaction.amount;
        }
        return Math.min(value, order.total);
    }

    private long orderBalance(Models.Order order) {
        return Math.max(0, order.total - orderAdvance(order));
    }

    private int monthOrders() {
        int count = 0; for (Models.Order order : store.orders) if (sameMonth(order.startAt, System.currentTimeMillis())) count++; return count;
    }

    private boolean sameDay(long first, long second) {
        Calendar a = Calendar.getInstance(); a.setTimeInMillis(first); Calendar b = Calendar.getInstance(); b.setTimeInMillis(second);
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private boolean sameMonth(long first, long second) {
        Calendar a = Calendar.getInstance(); a.setTimeInMillis(first); Calendar b = Calendar.getInstance(); b.setTimeInMillis(second);
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.MONTH) == b.get(Calendar.MONTH);
    }

    private String nextStatusCaption(String status) {
        if (status.equals("Запланировано")) return "Начать работу";
        if (status.equals("В работе")) return "Завершить работу";
        return "Работа завершена";
    }

    private void advanceStatus(Models.Order order) {
        if (order.status.equals("Запланировано")) order.status = "В работе";
        else if (order.status.equals("В работе")) order.status = "Завершено";
    }

    private int withAlpha(int color, int alpha) { return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)); }
    private String capitalize(String text) { return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1); }
    private long parseLong(String value) { try { return Long.parseLong(value.replace(" ", "")); } catch (Exception ignored) { return 0; } }
    private float parseFloat(String value) { try { return Float.parseFloat(value.replace(',', '.')); } catch (Exception ignored) { return 0; } }

    private void openIntent(Intent intent) {
        try { startActivity(intent); } catch (Exception error) { toast("Подходящее приложение не найдено"); }
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
    private void message(String title, String body) {
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setMessage(body)
                .setPositiveButton("Понятно", null).create();
        showStyledDialog(dialog);
    }

    private void showStyledDialog(AlertDialog dialog) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(rounded(SURFACE, 24, 1, BORDER));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.46f;
            window.setAttributes(attributes);
            int width = Math.min(Math.round(getResources().getDisplayMetrics().widthPixels * 0.92f), dp(520));
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        int alertTitleId = getResources().getIdentifier("alertTitle", "id", "android");
        TextView title = alertTitleId == 0 ? null : dialog.findViewById(alertTitleId);
        if (title != null) {
            title.setTextColor(INK);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextColor(MUTED);
            message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            message.setLineSpacing(0, 1.12f);
        }
        styleDialogButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE), MUTED);
        styleDialogButton(dialog.getButton(AlertDialog.BUTTON_NEUTRAL), RED);
        styleDialogButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE), BLUE);
        if (dialog.getListView() != null) {
            dialog.getListView().setDivider(null);
            dialog.getListView().setPadding(dp(8), dp(4), dp(8), dp(8));
        }
    }

    private void styleDialogButton(Button button, int color) {
        if (button == null) return;
        button.setAllCaps(false);
        button.setTextColor(color);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(dp(48));
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setStateListAnimator(null);
    }

    @Override
    public void onBackPressed() {
        if (currentBackAction != null) {
            Runnable action = currentBackAction;
            currentBackAction = null;
            action.run();
        }
        else if (navigation.getVisibility() == View.GONE) showRoute(route);
        else if (!route.equals("today")) showRoute("today");
        else super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (updateManager != null) updateManager.resumePendingInstall();
    }

    @Override
    protected void onDestroy() {
        if (updateManager != null) updateManager.destroy();
        super.onDestroy();
    }
}
