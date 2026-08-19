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
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ArrayAdapter;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    private final Locale ru = new Locale("ru", "RU");
    private final NumberFormat moneyFormat = NumberFormat.getIntegerInstance(ru);
    private final SimpleDateFormat dayMonth = new SimpleDateFormat("d MMMM", ru);
    private final SimpleDateFormat dateTime = new SimpleDateFormat("d MMM, HH:mm", ru);
    private final SimpleDateFormat time = new SimpleDateFormat("HH:mm", ru);

    private AppStore store;
    private UpdateManager updateManager;
    private FrameLayout content;
    private LinearLayout navigation;
    private String route = "today";
    private String photoOrderId;
    private Uri pendingCameraUri;
    private final Map<String, TextView> navLabels = new HashMap<>();
    private final Map<String, LinearLayout> navItems = new HashMap<>();
    private final Map<String, ImageView> navIcons = new HashMap<>();

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
        LinearLayout root = page("Календарь", "Расписание на сегодня", null);
        LinearLayout body = bodyOf(scrollBody(root));

        LinearLayout dateCard = card();
        TextView today = text(capitalize(dayMonth.format(new Date())), 19, BLUE, Typeface.BOLD);
        dateCard.addView(today);
        dateCard.addView(text("Свободные интервалы видны между заказами", 13, MUTED, Typeface.NORMAL), topMargin(-1, 5));
        body.addView(dateCard);

        List<Models.Order> todayOrders = ordersForDay(System.currentTimeMillis());
        if (todayOrders.isEmpty()) body.addView(emptyCard("Весь день свободен", "Добавьте заказ на удобное время."), topMargin(-1, 16));
        int previousHour = 9;
        for (Models.Order order : todayOrders) {
            Calendar c = Calendar.getInstance(); c.setTimeInMillis(order.startAt);
            int hour = c.get(Calendar.HOUR_OF_DAY);
            if (hour - previousHour >= 2) body.addView(freeSlot(previousHour + ":00 — " + hour + ":00"), topMargin(-1, 12));
            body.addView(timelineCard(order), topMargin(-1, 12));
            previousHour = Math.max(hour + 1, previousHour);
        }
        if (previousHour < 19) body.addView(freeSlot(previousHour + ":00 — 19:00"), topMargin(-1, 12));
        Button add = primaryButton("+  Добавить запись");
        add.setOnClickListener(view -> showNewOrderDialog());
        body.addView(add, topMargin(-1, 20));
        setPage(root);
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
        EditText search = field("Имя или телефон", InputType.TYPE_CLASS_TEXT | InputType.TYPE_CLASS_PHONE);
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
            boolean matches = normalized.isEmpty()
                    || nameValue.toLowerCase(ru).contains(normalized)
                    || (!digits.isEmpty() && phoneValue.replaceAll("[^0-9]", "").contains(digits));
            if (!matches) continue;
            visible++;
            LinearLayout card = card();
            card.setClickable(true);
            card.setOnClickListener(view -> showClientDetail(client.id));
            addRipple(card);
            TextView name = text(client.name, 18, INK, Typeface.BOLD);
            card.addView(name);
            card.addView(text(client.car, 15, MUTED, Typeface.NORMAL), topMargin(-1, 5));
            card.addView(text(client.phone, 14, BLUE, Typeface.NORMAL), topMargin(-1, 10));
            int count = 0; long total = 0;
            for (Models.Order order : store.orders) if (order.clientId.equals(client.id)) { count++; total += order.total; }
            card.addView(text(count + " заказов • " + money(total), 13, MUTED, Typeface.NORMAL), topMargin(-1, 12));
            clientList.addView(card, topMargin(-1, 12));
        }
        if (visible == 0) {
            clientList.addView(emptyCard(normalized.isEmpty() ? "Клиентов пока нет" : "Ничего не найдено",
                    normalized.isEmpty() ? "Клиент добавится вместе с первым заказом." : "Проверьте имя или номер телефона."));
        }
    }

    private void showMore() {
        LinearLayout root = page("Ещё", "Настройки бизнеса", null);
        LinearLayout body = bodyOf(scrollBody(root));
        body.addView(actionCard("Клиенты", "Контакты и история заказов", () -> showClients()));
        body.addView(actionCard("Услуги", "Названия, цены и длительность", () -> showServices()), topMargin(-1, 12));
        body.addView(actionCard("Обновления", "Проверка новых версий через GitHub", () -> showUpdates()), topMargin(-1, 12));
        body.addView(actionCard("О приложении", "DetailFlow " + updateManager.currentVersion() + " • данные хранятся на телефоне", () ->
                message("DetailFlow", "Автономное приложение для управления детейлингом. Интернет и регистрация не требуются.")), topMargin(-1, 12));
        setPage(root);
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

        TextView state = text(updateManager.getRepository().isEmpty() ? "Автопроверка включится после сохранения репозитория." : "Автопроверка выполняется раз в сутки.", 14, MUTED, Typeface.NORMAL);
        state.setMinHeight(dp(48));
        body.addView(state, topMargin(-1, 12));

        Button save = outlineButton("Сохранить репозиторий", BLUE);
        save.setOnClickListener(view -> {
            if (updateManager.setRepository(repository.getText().toString())) {
                repository.setText(updateManager.getRepository());
                state.setText("Репозиторий сохранён. Автопроверка выполняется раз в сутки.");
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
        for (Models.Order order : store.orders) if (!order.paid) outstanding += order.total;
        body.addView(infoRow("Ожидается оплат", money(outstanding), AMBER), topMargin(-1, 14));
        body.addView(sectionTitle("Последние операции"), topMargin(-1, 24));
        List<Models.Transaction> txs = new ArrayList<>(store.transactions);
        txs.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
        if (txs.isEmpty()) body.addView(emptyCard("Операций пока нет", "Добавьте первый доход или расход."), topMargin(-1, 10));
        for (Models.Transaction transaction : txs) body.addView(transactionCard(transaction), topMargin(-1, 10));

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
        Models.Client client = store.clientById(clientId);
        if (client == null) return;
        navigation.setVisibility(View.GONE);
        LinearLayout root = page("Клиент", client.car, () -> showClients());
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
        profile.addView(text(client.car, 15, BLUE_DARK, Typeface.BOLD), topMargin(-1, 14));
        body.addView(profile);

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

    private void showOrderDetail(String orderId) {
        Models.Order order = store.orderById(orderId);
        if (order == null) return;
        navigation.setVisibility(View.GONE);
        LinearLayout root = page("Заказ #" + order.id, order.status, () -> showRoute(route.equals("today") ? "today" : "orders"));
        LinearLayout body = bodyOf(scrollBody(root));

        LinearLayout identity = card();
        identity.addView(text(order.clientName + " • " + order.car, 18, INK, Typeface.BOLD));
        identity.addView(text("Начало: " + dateTime.format(new Date(order.startAt)), 14, MUTED, Typeface.NORMAL), topMargin(-1, 8));
        identity.addView(text("Дедлайн: " + dateTime.format(new Date(order.deadlineAt)), 14,
                order.deadlineAt < System.currentTimeMillis() && !order.status.equals("Завершено") ? RED : MUTED, Typeface.NORMAL), topMargin(-1, 4));
        identity.addView(statusPill(order.status), topMargin(-1, 12));
        body.addView(identity);

        body.addView(sectionTitle("Работы"), topMargin(-1, 22));
        for (String serviceId : order.serviceIds) {
            Models.Service service = store.serviceById(serviceId);
            if (service != null) body.addView(infoRow(service.name, duration(service.durationMinutes), BLUE), topMargin(-1, 8));
        }
        body.addView(infoRow("Итого", money(order.total), INK), topMargin(-1, 10));

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
        photos.addView(photoTile(order, true), new LinearLayout.LayoutParams(0, dp(184), 1));
        photos.addView(new Space(this), new LinearLayout.LayoutParams(dp(12), 1));
        photos.addView(photoTile(order, false), new LinearLayout.LayoutParams(0, dp(184), 1));
        body.addView(photos);

        Button status = primaryButton(nextStatusCaption(order.status));
        status.setOnClickListener(view -> { advanceStatus(order); store.save(); showOrderDetail(order.id); });
        body.addView(status, topMargin(-1, 22));
        Button paid = outlineButton(order.paid ? "Оплата получена" : "Отметить как оплаченный", order.paid ? GREEN : BLUE);
        paid.setEnabled(!order.paid);
        paid.setOnClickListener(view -> { order.paid = true; store.save(); showOrderDetail(order.id); });
        body.addView(paid, topMargin(-1, 10));
        setPage(root);
    }

    private View photoTile(Models.Order order, boolean before) {
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
        if (!uriText.isEmpty()) label.setBackground(rounded(Color.argb(175, 15, 23, 42), 10, 0, Color.TRANSPARENT));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(uriText.isEmpty() ? ViewGroup.LayoutParams.MATCH_PARENT : dp(78), dp(44), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.setMargins(dp(8), dp(8), dp(8), dp(10));
        frame.addView(label, lp);
        frame.setOnClickListener(view -> choosePhoto(order.id, before));
        frame.setClickable(true);
        addRipple(frame);
        return frame;
    }

    private void showNewOrderDialog() { showNewOrderDialog(null); }

    private void showNewOrderDialog(Models.Client preset) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(4), dp(4), dp(4), dp(8));
        scroll.addView(form);

        EditText name = field("Имя клиента", InputType.TYPE_CLASS_TEXT);
        EditText phone = field("Телефон", InputType.TYPE_CLASS_PHONE);
        EditText car = field("Автомобиль и номер", InputType.TYPE_CLASS_TEXT);

        List<Models.Client> clientChoices = new ArrayList<>();
        clientChoices.add(null);
        List<String> clientNames = new ArrayList<>();
        clientNames.add("Новый клиент — заполнить вручную");
        List<Models.Client> sortedClients = new ArrayList<>(store.clients);
        sortedClients.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        int presetIndex = 0;
        for (Models.Client client : sortedClients) {
            clientChoices.add(client);
            clientNames.add(client.name + (client.phone.isEmpty() ? "" : " • " + client.phone));
            if (preset != null && preset.id.equals(client.id)) presetIndex = clientChoices.size() - 1;
        }

        LinearLayout clientSection = dialogSection("Клиент");
        TextView clientLabel = text("Клиент из базы", 13, MUTED, Typeface.BOLD);
        clientSection.addView(clientLabel);
        Spinner clientSpinner = new Spinner(this);
        clientSpinner.setId(View.generateViewId());
        clientSpinner.setContentDescription("Клиент из базы");
        clientSpinner.setMinimumHeight(dp(56));
        clientSpinner.setPadding(dp(10), 0, dp(10), 0);
        clientSpinner.setBackground(rounded(SURFACE, 12, 1, BORDER));
        ArrayAdapter<String> clientAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, clientNames);
        clientAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        clientSpinner.setAdapter(clientAdapter);
        clientLabel.setLabelFor(clientSpinner.getId());
        clientSection.addView(clientSpinner, topMargin(-1, 5));
        clientSection.addView(labeled(name), topMargin(-1, 10));
        clientSection.addView(labeled(phone), topMargin(-1, 8));
        clientSection.addView(labeled(car), topMargin(-1, 8));
        form.addView(clientSection);

        Models.Client[] selectedClient = {preset};
        clientSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Models.Client selected = clientChoices.get(position);
                selectedClient[0] = selected;
                boolean editable = selected == null;
                name.setEnabled(editable); phone.setEnabled(editable); car.setEnabled(editable);
                if (selected != null) {
                    name.setText(selected.name); phone.setText(selected.phone); car.setText(selected.car);
                } else if (preset == null || position == 0) {
                    name.setText(""); phone.setText(""); car.setText("");
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        clientSpinner.setSelection(presetIndex);

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
            List<Models.Service> chosen = new ArrayList<>();
            for (Map.Entry<CheckBox, Models.Service> entry : selections.entrySet()) if (entry.getKey().isChecked()) chosen.add(entry.getValue());
            if (clientName.isEmpty()) { name.setError("Укажите имя"); return; }
            if (carText.isEmpty()) { car.setError("Укажите автомобиль"); return; }
            if (chosen.isEmpty()) { toast("Выберите хотя бы одну работу"); return; }

            Models.Client client = selectedClient[0] != null ? selectedClient[0] : store.findClient(phoneText, carText);
            if (client == null) {
                client = new Models.Client(store.newId(), clientName, phoneText, carText);
                store.clients.add(client);
            } else {
                client.name = clientName; client.phone = phoneText; client.car = carText;
            }
            long total = 0; int minutes = 0;
            for (Models.Service service : chosen) { total += service.price; minutes += service.durationMinutes; }
            long start = selected.getTimeInMillis();
            String number = String.valueOf(100 + store.orders.size() + 1);
            Models.Order order = new Models.Order(number, client.id, client.name, client.phone, client.car,
                    total, start, start + minutes * 60000L, "Запланировано", false);
            for (Models.Service service : chosen) order.serviceIds.add(service.id);
            store.orders.add(order); store.sortOrders(); store.save();
            dialog.dismiss();
            showOrderDetail(order.id);
        }));
        showStyledDialog(dialog);
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
                orderNames.add("Заказ #" + order.id + " • " + order.car);
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
        DatePickerDialog dateDialog = new DatePickerDialog(this, (view, year, month, day) -> {
            selected.set(Calendar.YEAR, year); selected.set(Calendar.MONTH, month); selected.set(Calendar.DAY_OF_MONTH, day);
            TimePickerDialog timeDialog = new TimePickerDialog(this, (picker, hour, minute) -> {
                selected.set(Calendar.HOUR_OF_DAY, hour); selected.set(Calendar.MINUTE, minute); selected.set(Calendar.SECOND, 0);
                button.setText("Время: " + dateTime.format(selected.getTime()));
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
        top.addView(text(time.format(new Date(order.startAt)), 20, BLUE, Typeface.BOLD));
        TextView car = text(order.car, 17, INK, Typeface.BOLD); top.addView(car, leftMargin(0, 14, 1));
        card.addView(top);
        card.addView(text(serviceNames(order), 14, MUTED, Typeface.NORMAL), topMargin(-1, 7));
        card.addView(statusPill(order.status), topMargin(-1, 11));
        return card;
    }

    private LinearLayout timelineCard(Models.Order order) {
        LinearLayout item = card();
        item.setBackground(rounded(Color.rgb(239, 246, 255), 16, 1, Color.rgb(147, 197, 253)));
        item.setOnClickListener(view -> showOrderDetail(order.id)); item.setClickable(true);
        addRipple(item);
        item.addView(text(time.format(new Date(order.startAt)) + "  •  " + order.car, 17, INK, Typeface.BOLD));
        item.addView(text(serviceNames(order), 14, MUTED, Typeface.NORMAL), topMargin(-1, 6));
        return item;
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        pill.setLayoutParams(lp);
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
            detail += order == null ? " • Заказ #" + transaction.orderId : " • Заказ #" + order.id + " • " + order.car;
        } else if (!transaction.income) {
            detail += " • Без заказа";
        }
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

    private EditText field(String hint, int inputType) {
        EditText input = new EditText(this); input.setHint(hint); input.setTextColor(INK); input.setHintTextColor(MUTED); input.setTextSize(16); input.setSingleLine(true);
        input.setId(View.generateViewId());
        input.setInputType(inputType); input.setPadding(dp(14), 0, dp(14), 0); input.setBackground(rounded(SURFACE, 12, 1, BORDER)); input.setMinHeight(dp(56));
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
        for (Models.Order order : store.orders) if (order.paid && sameMonth(order.startAt, System.currentTimeMillis())) value += order.total;
        return value;
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
        if (navigation.getVisibility() == View.GONE) showRoute(route);
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
