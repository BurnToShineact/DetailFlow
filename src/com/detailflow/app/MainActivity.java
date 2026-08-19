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
    private String calendarMode = "week";
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
        navigation.setPadding(dp(4), dp(5), dp(4), dp(5));
        navigation.setBackgroundColor(SURFACE);
        navigation.setElevation(dp(10));
        addNav("today", "Сегодня", R.drawable.ic_nav_today);
        addNav("calendar", "Календарь", R.drawable.ic_nav_calendar);
        addNav("orders", "Заказы", R.drawable.ic_nav_orders);
        addNav("finance", "Финансы", R.drawable.ic_nav_finance);
        addNav("more", "Ещё", R.drawable.ic_nav_more);
        root.addView(navigation, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));
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
        navigation.setVisibility(View.VISIBLE);
        for (Map.Entry<String, TextView> item : navLabels.entrySet()) {
            boolean selected = item.getKey().equals(nextRoute);
            item.getValue().setTextColor(selected ? BLUE : MUTED);
            item.getValue().setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            navIcons.get(item.getKey()).setImageTintList(ColorStateList.valueOf(selected ? BLUE : MUTED));
            navItems.get(item.getKey()).setBackground(selected ? rounded(Color.rgb(239, 246, 255), 16, 0, Color.TRANSPARENT) : null);
        }
        switch (nextRoute) {
            case "calendar": showCalendar(); break;
            case "orders": showOrders(); break;
            case "finance": showFinance(); break;
            case "more": showMore(); break;
            default: showToday();
        }
    }

    private void setPage(View page) {
        content.removeAllViews();
        content.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private LinearLayout page(String title, String subtitle, Runnable back) {
        currentBackAction = back;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(20), dp(14), dp(20), dp(10));
        if (back != null) {
            TextView backView = text("‹", 38, INK, Typeface.NORMAL);
            backView.setGravity(Gravity.CENTER);
            backView.setContentDescription("Назад");
            backView.setOnClickListener(view -> back.run());
            bar.addView(backView, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }
        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(title, 27, INK, Typeface.BOLD);
        titleBlock.addView(titleView);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subtitleView = text(subtitle, 14, MUTED, Typeface.NORMAL);
            titleBlock.addView(subtitleView, topMargin(-1, -2));
        }
        bar.addView(titleBlock, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(bar);
        return root;
    }

    private ScrollView scrollBody(LinearLayout root) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(8), dp(20), dp(28));
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
        stats.addView(metricCard("Выручка за месяц", money(monthRevenue), GREEN), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Space gap = new Space(this); stats.addView(gap, new LinearLayout.LayoutParams(dp(12), 1));
        stats.addView(metricCard("Заказов", String.valueOf(monthOrders), BLUE), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        body.addView(stats);

        body.addView(sectionTitle("Ближайшие записи"), topMargin(-1, 26));
        List<Models.Order> upcoming = upcomingOrders();
        if (upcoming.isEmpty()) body.addView(emptyCard("Записей пока нет", "Создайте первую запись — она появится здесь."));
        for (int i = 0; i < Math.min(4, upcoming.size()); i++) body.addView(orderCard(upcoming.get(i)), topMargin(-1, 12));

        Button add = primaryButton("+  Новая запись");
        add.setOnClickListener(view -> showNewOrderDialog());
        body.addView(add, topMargin(-1, 22));
        setPage(root);
    }

    private void showCalendar() {
        LinearLayout root = page("Календарь", "Неделя и месяц", null);
        LinearLayout body = bodyOf(scrollBody(root));

        LinearLayout switcher = new LinearLayout(this);
        switcher.setOrientation(LinearLayout.HORIZONTAL);
        switcher.setPadding(dp(4), dp(4), dp(4), dp(4));
        switcher.setBackground(rounded(Color.rgb(241, 245, 249), 14, 0, Color.TRANSPARENT));
        switcher.addView(calendarModeButton("Неделя", calendarMode.equals("week"), () -> {
            calendarMode = "week";
            showCalendar();
        }), new LinearLayout.LayoutParams(0, dp(48), 1));
        switcher.addView(calendarModeButton("Месяц", calendarMode.equals("month"), () -> {
            calendarMode = "month";
            showCalendar();
        }), new LinearLayout.LayoutParams(0, dp(48), 1));
        body.addView(switcher);

        LinearLayout period = new LinearLayout(this);
        period.setOrientation(LinearLayout.HORIZONTAL);
        period.setGravity(Gravity.CENTER_VERTICAL);
        Button previous = calendarArrowButton("‹", "Предыдущий период");
        previous.setOnClickListener(view -> shiftCalendar(-1));
        period.addView(previous, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView periodTitle = text(calendarPeriodTitle(), 17, INK, Typeface.BOLD);
        periodTitle.setGravity(Gravity.CENTER);
        period.addView(periodTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button next = calendarArrowButton("›", "Следующий период");
        next.setOnClickListener(view -> shiftCalendar(1));
        period.addView(next, new LinearLayout.LayoutParams(dp(48), dp(48)));
        body.addView(period, topMargin(-1, 12));

        if (calendarMode.equals("month")) renderMonthGrid(body);
        else renderWeekStrip(body);

        renderDaySchedule(body, calendarSelected.getTimeInMillis());
        setPage(root);
    }

    private void renderDaySchedule(LinearLayout body, long dayMillis) {
        body.addView(sectionTitle(capitalize(dayMonth.format(new Date(dayMillis)))), topMargin(-1, 24));
        List<Models.Order> dayOrders = ordersForDay(dayMillis);
        if (dayOrders.isEmpty()) body.addView(emptyCard("Весь день свободен", "Добавьте заказ на удобное время."), topMargin(-1, 10));
        int previousHour = 9;
        for (Models.Order order : dayOrders) {
            Calendar c = Calendar.getInstance(); c.setTimeInMillis(order.startAt);
            int hour = c.get(Calendar.HOUR_OF_DAY);
            if (hour - previousHour >= 2) body.addView(freeSlot(previousHour + ":00 — " + hour + ":00"), topMargin(-1, 12));
            body.addView(timelineCard(order), topMargin(-1, 12));
            Calendar deadline = Calendar.getInstance(); deadline.setTimeInMillis(order.deadlineAt);
            previousHour = Math.max(deadline.get(Calendar.HOUR_OF_DAY), hour + 1);
        }
        if (previousHour < 19) body.addView(freeSlot(previousHour + ":00 — 19:00"), topMargin(-1, 12));
        Button add = primaryButton("+  Добавить запись");
        add.setOnClickListener(view -> showNewOrderDialog());
        body.addView(add, topMargin(-1, 20));
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
        } else {
            calendarSelected.add(Calendar.DAY_OF_MONTH, direction * 7);
        }
        showCalendar();
    }

    private String calendarPeriodTitle() {
        if (calendarMode.equals("month")) return capitalize(monthYear.format(calendarSelected.getTime()));
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
            if (index > 0) week.addView(new Space(this), new LinearLayout.LayoutParams(dp(4), 1));
            int orders = ordersForDay(day.getTimeInMillis()).size();
            TextView cell = calendarDayCell(weekdays[index], day, orders, true);
            week.addView(cell, new LinearLayout.LayoutParams(0, dp(68), 1));
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
        body.addView(week, topMargin(-1, 10));
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
        LinearLayout root = page("Заказы", "Все работы и статусы", null);
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
        body.addView(actionCard("Услуги", "Названия, цены и длительность", () -> showServices()), topMargin(-1, 12));
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
        navigation.setVisibility(View.GONE);
        LinearLayout root = page(model.name, model.make, back);
        LinearLayout body = bodyOf(scrollBody(root));
        LinearLayout modelCard = cardWithColor(Color.rgb(239, 246, 255));
        modelCard.addView(text(model.make + " • " + model.name, 21, INK, Typeface.BOLD));
        modelCard.addView(text("Постоянное примечание к модели", 12, MUTED, Typeface.BOLD), topMargin(-1, 14));
        modelCard.addView(text(model.note.isEmpty() ? "Примечаний пока нет" : model.note, 15,
                model.note.isEmpty() ? MUTED : INK, Typeface.NORMAL), topMargin(-1, 5));
        body.addView(modelCard);
        int clients = 0; int orders = 0;
        for (Models.Client client : store.clients) if (client.carModelId.equals(model.id)) clients++;
        for (Models.Order order : store.orders) if (order.carModelId.equals(model.id)) orders++;
        body.addView(infoRow("Клиентов с этой моделью", String.valueOf(clients), BLUE), topMargin(-1, 14));
        body.addView(infoRow("Заказов с этой моделью", String.valueOf(orders), BLUE), topMargin(-1, 8));
        Button edit = primaryButton("Изменить карточку модели");
        edit.setOnClickListener(view -> editCarModel(model, model.make, back));
        body.addView(edit, topMargin(-1, 18));
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
        LinearLayout root = page("Услуги", "Настраивайте под свою работу", () -> showRoute("more"));
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
            item.addView(text(service.category + " • " + money(service.price) + " • " + duration(service.durationMinutes), 14, MUTED, Typeface.NORMAL), topMargin(-1, 6));
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
        navigation.setVisibility(View.GONE);
        LinearLayout root = page("Клиент", vehicle(client.car, client.carModel, client.plate), back);
        LinearLayout body = bodyOf(scrollBody(root));
        LinearLayout profile = cardWithColor(Color.rgb(239, 246, 255));
        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.HORIZONTAL);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        TextView avatar = text(client.name.trim().isEmpty() ? "?" : client.name.trim().substring(0, 1).toUpperCase(ru), 24, Color.WHITE, Typeface.BOLD);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(rounded(BLUE, 18, 0, Color.TRANSPARENT));
        identity.addView(avatar, new LinearLayout.LayoutParams(dp(56), dp(56)));
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.addView(text(client.name, 21, INK, Typeface.BOLD));
        details.addView(text(client.phone.isEmpty() ? "Телефон не указан" : client.phone, 15, MUTED, Typeface.NORMAL), topMargin(-1, 5));
        identity.addView(details, leftMargin(-1, 14, 1));
        profile.addView(identity);
        profile.addView(text(vehicle(client.car, client.carModel, client.plate), 15, BLUE_DARK, Typeface.BOLD), topMargin(-1, 14));
        body.addView(profile);
        Button editVehicle = outlineButton("Изменить автомобиль", BLUE);
        editVehicle.setOnClickListener(view -> editClientVehicle(client, back));
        body.addView(editVehicle, topMargin(-1, 12));
        if (store.carModelById(client.carModelId) != null) {
            Button modelCard = outlineButton("Открыть карточку модели", BLUE_DARK);
            modelCard.setOnClickListener(view -> showCarModelDetail(client.carModelId, () -> showClientDetail(client.id, back)));
            body.addView(modelCard, topMargin(-1, 10));
        }

        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button call = outlineButton("Позвонить", BLUE);
        call.setOnClickListener(view -> openIntent(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + client.phone))));
        Button sms = outlineButton("Написать", BLUE);
        sms.setOnClickListener(view -> openIntent(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + client.phone))));
        actions.addView(call, new LinearLayout.LayoutParams(0, dp(54), 1));
        actions.addView(new Space(this), new LinearLayout.LayoutParams(dp(12), 1));
        actions.addView(sms, new LinearLayout.LayoutParams(0, dp(54), 1));
        body.addView(actions, topMargin(-1, 14));
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
        summary.addView(metricCard("Заказов", String.valueOf(orderCount), BLUE), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        summary.addView(new Space(this), new LinearLayout.LayoutParams(dp(12), 1));
        summary.addView(metricCard("На сумму", money(orderTotal), GREEN), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        body.addView(summary, topMargin(-1, 18));
        body.addView(sectionTitle("История заказов"), topMargin(-1, 24));
        if (history.isEmpty()) body.addView(emptyCard("Заказов пока нет", "Создайте первую запись для этого клиента."), topMargin(-1, 10));
        for (Models.Order order : history) body.addView(orderCard(order), topMargin(-1, 10));
        Button add = primaryButton("+  Новый заказ");
        add.setOnClickListener(view -> showNewOrderDialog(client));
        body.addView(add, topMargin(-1, 20));
        setPage(root);
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
        navigation.setVisibility(View.GONE);
        LinearLayout root = page("Заказ #" + order.id, order.status, back);
        LinearLayout body = bodyOf(scrollBody(root));

        LinearLayout identity = card();
        identity.setPadding(dp(14), dp(14), dp(14), dp(15));
        LinearLayout links = new LinearLayout(this);
        links.setOrientation(LinearLayout.HORIZONTAL);
        Models.Client client = store.clientById(order.clientId);
        Button clientLink = compactLinkButton(order.clientName + "  ›");
        clientLink.setContentDescription("Открыть карточку клиента " + order.clientName);
        clientLink.setEnabled(client != null);
        if (client != null) clientLink.setOnClickListener(view -> showClientDetail(client.id, () -> showOrderDetail(order.id, back)));
        links.addView(clientLink, new LinearLayout.LayoutParams(0, dp(48), 1));
        Models.CarModel model = store.carModelById(order.carModelId);
        String modelCaption = vehicle(order.car, order.carModel, "");
        if (model != null) {
            links.addView(new Space(this), new LinearLayout.LayoutParams(dp(9), 1));
            Button modelLink = compactLinkButton(modelCaption + "  ›");
            modelLink.setContentDescription("Открыть карточку модели " + modelCaption);
            modelLink.setOnClickListener(view -> showCarModelDetail(model.id, () -> showOrderDetail(order.id, back)));
            links.addView(modelLink, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        identity.addView(links);
        String schedule = dateTime.format(new Date(order.startAt)) + "  →  " + dateTime.format(new Date(order.deadlineAt));
        identity.addView(text(schedule, 14,
                order.deadlineAt < System.currentTimeMillis() && !order.status.equals("Завершено") ? RED : MUTED,
                Typeface.NORMAL), topMargin(-1, 12));
        if (model == null && !modelCaption.isEmpty()) identity.addView(text(modelCaption, 13, MUTED, Typeface.BOLD), topMargin(-1, 4));
        if (!order.plate.isEmpty()) identity.addView(text(order.plate, 13, MUTED, Typeface.BOLD), topMargin(-1, 4));
        identity.addView(statusPill(order.status), topMargin(-1, 10));
        Button reschedule = compactLinkButton("Перенести дату  ›");
        reschedule.setContentDescription("Перенести дату заказа");
        reschedule.setOnClickListener(view -> rescheduleOrder(order, back));
        identity.addView(reschedule, topMargin(-1, 10));
        body.addView(identity);

        LinearLayout noteHeader = sectionHeaderWithAction("Примечание", "+");
        Button editNote = (Button) noteHeader.getChildAt(1);
        editNote.setContentDescription(order.orderNote.isEmpty() ? "Добавить примечание к заказу" : "Изменить примечание к заказу");
        editNote.setOnClickListener(view -> editOrderNote(order, back));
        body.addView(noteHeader, topMargin(-1, 18));
        if (!order.orderNote.isEmpty()) {
            LinearLayout noteCard = cardWithColor(Color.rgb(248, 250, 252));
            noteCard.setPadding(dp(15), dp(13), dp(15), dp(14));
            noteCard.addView(text(order.orderNote, 14, INK, Typeface.NORMAL));
            body.addView(noteCard, topMargin(-1, 5));
        }

        body.addView(sectionTitle("Работы"), topMargin(-1, 22));
        for (String serviceId : order.serviceIds) {
            Models.Service service = store.serviceById(serviceId);
            if (service != null) body.addView(infoRow(service.name, duration(service.durationMinutes), BLUE), topMargin(-1, 8));
        }
        body.addView(infoRow("Итого", money(order.total), INK), topMargin(-1, 10));

        long advance = orderAdvance(order);
        long balance = orderBalance(order);
        body.addView(sectionTitle("Оплата"), topMargin(-1, 22));
        LinearLayout paymentSummary = card();
        paymentSummary.addView(paymentPill(order));
        if (advance > 0) paymentSummary.addView(text("Внесено авансом: " + money(advance), 15, INK, Typeface.BOLD), topMargin(-1, 10));
        paymentSummary.addView(text(order.paid ? "Оплата по заказу закрыта" : "Осталось оплатить: " + money(balance),
                14, order.paid ? GREEN : MUTED, Typeface.NORMAL), topMargin(-1, 7));
        body.addView(paymentSummary, topMargin(-1, 8));
        if (!order.paid && balance > 0) {
            Button addAdvance = outlineButton(advance > 0 ? "+ Добавить аванс" : "+ Внести аванс", GREEN);
            addAdvance.setOnClickListener(view -> addAdvance(order, back));
            body.addView(addAdvance, topMargin(-1, 10));
            Button paid = outlineButton(advance > 0 ? "Остаток оплачен полностью" : "Отметить как оплаченный", BLUE);
            paid.setOnClickListener(view -> {
                order.paid = true;
                store.save();
                showOrderDetail(order.id, back);
            });
            body.addView(paid, topMargin(-1, 10));
        }

        body.addView(sectionTitle("Расходы по заказу"), topMargin(-1, 22));
        long linkedExpenses = 0;
        for (Models.Transaction transaction : store.transactions) {
            if (!transaction.income && order.id.equals(transaction.orderId)) {
                linkedExpenses += transaction.amount;
                body.addView(transactionCard(transaction), topMargin(-1, 8));
            }
        }
        if (linkedExpenses == 0) body.addView(text("Расходов пока нет", 14, MUTED, Typeface.NORMAL), topMargin(-1, 8));
        else body.addView(infoRow("Всего расходов", money(linkedExpenses), RED), topMargin(-1, 8));
        Button addExpense = outlineButton("+ Добавить расход", AMBER);
        addExpense.setOnClickListener(view -> addTransaction(false, order));
        body.addView(addExpense, topMargin(-1, 10));

        body.addView(sectionTitle("Фото до и после"), topMargin(-1, 22));
        LinearLayout photos = new LinearLayout(this); photos.setOrientation(LinearLayout.HORIZONTAL);
        photos.addView(photoTile(order, true, back), new LinearLayout.LayoutParams(0, dp(184), 1));
        photos.addView(new Space(this), new LinearLayout.LayoutParams(dp(12), 1));
        photos.addView(photoTile(order, false, back), new LinearLayout.LayoutParams(0, dp(184), 1));
        body.addView(photos);

        Button status = primaryButton(nextStatusCaption(order.status));
        status.setOnClickListener(view -> { advanceStatus(order); store.save(); showOrderDetail(order.id, back); });
        body.addView(status, topMargin(-1, 22));
        setPage(root);
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
        frame.setBackground(rounded(Color.rgb(241, 245, 249), 18, 1, BORDER));
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setContentDescription(before ? "Фото до работы" : "Фото после работы");
        if (!uriText.isEmpty()) {
            try { image.setImageURI(Uri.parse(uriText)); } catch (Exception ignored) { }
        }
        frame.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        String photoLabel = before ? "Фото до" : "Фото после";
        TextView label = text(uriText.isEmpty() ? "+  " + photoLabel : photoLabel + " • " + uris.size(), 14,
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
        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(4), dp(4), dp(4), dp(8));
        scroll.addView(form);

        EditText name = field("Имя клиента", InputType.TYPE_CLASS_TEXT);
        EditText phone = field("Телефон", InputType.TYPE_CLASS_PHONE);
        AutoCompleteTextView car = autoCompleteField("Марка автомобиля", store.carMakes);
        AutoCompleteTextView model = autoCompleteField("Модель автомобиля", new ArrayList<>());
        EditText plate = field("Госномер (если известен)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        EditText orderNote = multilineField("Что важно учесть в этом заказе");
        bindMakeToModel(car, model, "");

        LinearLayout clientSection = dialogSection("Клиент");
        TextView clientState = text("", 14, BLUE_DARK, Typeface.BOLD);
        clientState.setGravity(Gravity.CENTER_VERTICAL);
        clientState.setMinHeight(dp(48));
        clientState.setPadding(dp(12), dp(8), dp(12), dp(8));
        clientSection.addView(clientState, topMargin(-1, 8));
        clientSection.addView(labeled(name), topMargin(-1, 10));
        clientSection.addView(labeled(phone), topMargin(-1, 8));
        LinearLayout clientSuggestions = new LinearLayout(this);
        clientSuggestions.setOrientation(LinearLayout.VERTICAL);
        clientSection.addView(clientSuggestions, topMargin(-1, 4));
        form.addView(clientSection);

        LinearLayout vehicleSection = dialogSection("Автомобиль");
        vehicleSection.addView(text("Начните вводить марку или выберите её из справочника.", 13, MUTED, Typeface.NORMAL), topMargin(-1, 7));
        vehicleSection.addView(labeled(car), topMargin(-1, 10));
        vehicleSection.addView(labeled(model), topMargin(-1, 8));
        vehicleSection.addView(labeled(plate), topMargin(-1, 8));
        form.addView(vehicleSection, topMargin(-1, 12));

        Models.Client[] selectedClient = {preset};
        boolean[] updatingClient = {false};
        TextWatcher clientWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable value) {
                refreshClientMatch(name, phone, car, model, plate, clientState, clientSuggestions, selectedClient, updatingClient);
            }
        };
        name.addTextChangedListener(clientWatcher);
        phone.addTextChangedListener(clientWatcher);
        if (preset == null) {
            refreshClientMatch(name, phone, car, model, plate, clientState, clientSuggestions, selectedClient, updatingClient);
        } else {
            applyClientSelection(preset, name, phone, car, model, plate, clientState, clientSuggestions, selectedClient, updatingClient);
        }

        LinearLayout workSection = dialogSection("Работы");

        Map<CheckBox, Models.Service> selections = new HashMap<>();
        for (Models.Service service : store.services) {
            CheckBox check = new CheckBox(this);
            check.setText(service.name + "\n" + money(service.price) + " • " + duration(service.durationMinutes));
            check.setTextColor(INK); check.setTextSize(15); check.setPadding(dp(4), dp(7), dp(4), dp(7)); check.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{BLUE, MUTED}));
            workSection.addView(check, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
            selections.put(check, service);
        }
        if (store.services.isEmpty()) workSection.addView(text("Сначала добавьте хотя бы одну услугу в разделе «Ещё». ", 14, RED, Typeface.NORMAL), topMargin(-1, 8));
        form.addView(workSection, topMargin(-1, 12));

        Calendar selected = Calendar.getInstance(); selected.add(Calendar.HOUR_OF_DAY, 1); selected.set(Calendar.MINUTE, 0);
        Button when = outlineButton("Время: " + dateTime.format(selected.getTime()), BLUE);
        when.setOnClickListener(view -> chooseDateTime(selected, when));
        LinearLayout scheduleSection = dialogSection("Время записи");
        scheduleSection.addView(text("Начало и дедлайн рассчитываются по длительности выбранных работ.", 13, MUTED, Typeface.NORMAL));
        scheduleSection.addView(when, topMargin(-1, 9));
        form.addView(scheduleSection, topMargin(-1, 12));

        LinearLayout noteSection = dialogSection("Примечание к заказу");
        noteSection.addView(text("Это примечание сохранится только в создаваемом заказе.", 13, MUTED, Typeface.NORMAL), topMargin(-1, 7));
        noteSection.addView(labeled(orderNote), topMargin(-1, 9));
        form.addView(noteSection, topMargin(-1, 12));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Новая запись")
                .setView(scroll)
                .setNegativeButton("Отменить", null)
                .setPositiveButton("Создать запись", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String clientName = name.getText().toString().trim();
            String phoneText = phone.getText().toString().trim();
            String carText = car.getText().toString().trim();
            String modelText = model.getText().toString().trim();
            String plateText = plate.getText().toString().trim().toUpperCase(ru);
            String orderNoteText = orderNote.getText().toString().trim();
            List<Models.Service> chosen = new ArrayList<>();
            for (Map.Entry<CheckBox, Models.Service> entry : selections.entrySet()) if (entry.getKey().isChecked()) chosen.add(entry.getValue());
            if (clientName.isEmpty()) { name.setError("Укажите имя"); return; }
            if (carText.isEmpty()) { car.setError("Укажите автомобиль"); return; }
            if (modelText.isEmpty()) { model.setError("Укажите модель"); return; }
            if (chosen.isEmpty()) { toast("Выберите хотя бы одну работу"); return; }

            Models.CarModel selectedModel = store.ensureCarModel(carText, modelText);
            if (selectedModel == null) { model.setError("Укажите модель"); return; }

            Models.Client client = selectedClient[0] != null ? selectedClient[0] : store.findClientByNameOrPhone(clientName, phoneText);
            if (client == null) {
                client = new Models.Client(store.newId(), clientName, phoneText, carText, selectedModel.id, selectedModel.name, plateText);
                store.clients.add(client);
            } else {
                client.name = clientName; client.phone = phoneText; client.car = carText;
                client.carModelId = selectedModel.id; client.carModel = selectedModel.name; client.plate = plateText;
                if (!client.legacyCarNote.isEmpty() && selectedModel.note.isEmpty()) selectedModel.note = client.legacyCarNote;
                client.legacyCarNote = "";
            }
            store.addCarMake(carText);
            long total = 0; int minutes = 0;
            for (Models.Service service : chosen) { total += service.price; minutes += service.durationMinutes; }
            long start = selected.getTimeInMillis();
            String number = String.valueOf(100 + store.orders.size() + 1);
            Models.Order order = new Models.Order(number, client.id, client.name, client.phone, client.car,
                    client.carModelId, client.carModel, client.plate, orderNoteText,
                    total, start, start + minutes * 60000L, "Запланировано", false);
            for (Models.Service service : chosen) order.serviceIds.add(service.id);
            store.orders.add(order); store.sortOrders(); store.save();
            dialog.dismiss();
            showOrderDetail(order.id);
        }));
        showStyledDialog(dialog);
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
        EditText price = field("Цена, ₽", InputType.TYPE_CLASS_NUMBER);
        EditText hours = field("Длительность, часов", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (existing != null) {
            name.setText(existing.name); category.setText(existing.category); price.setText(String.valueOf(existing.price));
            hours.setText(String.format(ru, "%.1f", existing.durationMinutes / 60f));
        }
        form.addView(labeled(name)); form.addView(labeled(category), topMargin(-1, 8)); form.addView(labeled(price), topMargin(-1, 8)); form.addView(labeled(hours), topMargin(-1, 8));
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(existing == null ? "Новая услуга" : "Изменить услугу")
                .setView(form).setNegativeButton("Отмена", null).setPositiveButton("Сохранить", null);
        if (existing != null) builder.setNeutralButton("Удалить", null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String title = name.getText().toString().trim();
            long priceValue = parseLong(price.getText().toString());
            float hourValue = parseFloat(hours.getText().toString());
            if (title.isEmpty()) { name.setError("Укажите название"); return; }
            if (priceValue <= 0) { price.setError("Укажите цену"); return; }
            if (hourValue <= 0) { hours.setError("Укажите длительность"); return; }
            Models.Service service = existing;
            if (service == null) { service = new Models.Service(store.newId(), title, "Другое", priceValue, Math.round(hourValue * 60)); store.services.add(service); }
            service.name = title; service.category = category.getText().toString().trim().isEmpty() ? "Другое" : category.getText().toString().trim();
            service.price = priceValue; service.durationMinutes = Math.round(hourValue * 60);
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
        DatePickerDialog dateDialog = new DatePickerDialog(this, (view, year, month, day) -> {
            selected.set(Calendar.YEAR, year); selected.set(Calendar.MONTH, month); selected.set(Calendar.DAY_OF_MONTH, day);
            TimePickerDialog timeDialog = new TimePickerDialog(this, (picker, hour, minute) -> {
                selected.set(Calendar.HOUR_OF_DAY, hour); selected.set(Calendar.MINUTE, minute); selected.set(Calendar.SECOND, 0);
                button.setText(prefix + dateTime.format(selected.getTime()));
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
        card.setClickable(true);
        card.setOnClickListener(view -> showOrderDetail(order.id));
        addRipple(card);
        LinearLayout top = new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text("Заказ #" + order.id, 18, BLUE, Typeface.BOLD));
        TextView schedule = text(dateTime.format(new Date(order.startAt)), 17, INK, Typeface.BOLD);
        schedule.setGravity(Gravity.END);
        top.addView(schedule, leftMargin(0, 12, 1));
        card.addView(top);
        TextView services = text(serviceNames(order), 15, BLUE_DARK, Typeface.BOLD);
        services.setGravity(Gravity.END);
        card.addView(services, topMargin(-1, 7));
        String car = vehicle(order.car, order.carModel, order.plate);
        card.addView(text(car.isEmpty() ? "Автомобиль не указан" : car, 15, INK, Typeface.BOLD), topMargin(-1, 9));
        card.addView(text(order.clientName, 13, MUTED, Typeface.NORMAL), topMargin(-1, 4));
        card.addView(cardStatusRow(order), topMargin(-1, 11));
        return card;
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
        item.setMinimumHeight(dp(106));
        item.setPadding(dp(15), dp(14), dp(15), dp(14));
        item.addView(text(label, 12, MUTED, Typeface.NORMAL));
        item.addView(text(value, 21, accent, Typeface.BOLD), topMargin(-1, 7));
        return item;
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
        card.setPadding(dp(16), dp(16), dp(16), dp(16)); card.setBackground(rounded(SURFACE, 18, 1, BORDER)); card.setElevation(dp(1));
        card.setMinimumHeight(dp(56));
        return card;
    }

    private LinearLayout cardWithColor(int color) {
        LinearLayout item = card(); item.setBackground(rounded(color, 18, 0, Color.TRANSPARENT)); item.setPadding(dp(20), dp(20), dp(20), dp(20));
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

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(this); view.setText(value); view.setTextColor(color); view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        view.setTypeface(Typeface.DEFAULT, style); view.setLineSpacing(0, 1.08f); return view;
    }

    private GradientDrawable rounded(int fill, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(fill); drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), strokeColor); return drawable;
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
