package com.detailflow.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

final class AppStore {
    final List<Models.Service> services = new ArrayList<>();
    final List<Models.Client> clients = new ArrayList<>();
    final List<Models.Order> orders = new ArrayList<>();
    final List<Models.Transaction> transactions = new ArrayList<>();
    private final SharedPreferences preferences;

    AppStore(Context context) {
        preferences = context.getSharedPreferences("detailflow_local_data", Context.MODE_PRIVATE);
        load();
    }

    private void load() {
        String raw = preferences.getString("data", "");
        if (raw.isEmpty()) {
            seed();
            save();
            return;
        }
        try {
            JSONObject root = new JSONObject(raw);
            JSONArray servicesJson = root.optJSONArray("services");
            JSONArray clientsJson = root.optJSONArray("clients");
            JSONArray ordersJson = root.optJSONArray("orders");
            JSONArray transactionsJson = root.optJSONArray("transactions");
            if (servicesJson != null) for (int i = 0; i < servicesJson.length(); i++) services.add(Models.Service.fromJson(servicesJson.getJSONObject(i)));
            if (clientsJson != null) for (int i = 0; i < clientsJson.length(); i++) clients.add(Models.Client.fromJson(clientsJson.getJSONObject(i)));
            if (ordersJson != null) for (int i = 0; i < ordersJson.length(); i++) orders.add(Models.Order.fromJson(ordersJson.getJSONObject(i)));
            if (transactionsJson != null) for (int i = 0; i < transactionsJson.length(); i++) transactions.add(Models.Transaction.fromJson(transactionsJson.getJSONObject(i)));
        } catch (Exception ignored) {
            services.clear(); clients.clear(); orders.clear(); transactions.clear();
            seed();
            save();
        }
        sortOrders();
    }

    void save() {
        try {
            JSONArray servicesJson = new JSONArray();
            JSONArray clientsJson = new JSONArray();
            JSONArray ordersJson = new JSONArray();
            JSONArray transactionsJson = new JSONArray();
            for (Models.Service item : services) servicesJson.put(item.toJson());
            for (Models.Client item : clients) clientsJson.put(item.toJson());
            for (Models.Order item : orders) ordersJson.put(item.toJson());
            for (Models.Transaction item : transactions) transactionsJson.put(item.toJson());
            JSONObject root = new JSONObject().put("services", servicesJson).put("clients", clientsJson)
                    .put("orders", ordersJson).put("transactions", transactionsJson);
            preferences.edit().putString("data", root.toString()).apply();
        } catch (Exception ignored) { }
    }

    String newId() {
        return UUID.randomUUID().toString();
    }

    Models.Service serviceById(String id) {
        for (Models.Service item : services) if (item.id.equals(id)) return item;
        return null;
    }

    Models.Order orderById(String id) {
        for (Models.Order item : orders) if (item.id.equals(id)) return item;
        return null;
    }

    Models.Client clientById(String id) {
        for (Models.Client item : clients) if (item.id.equals(id)) return item;
        return null;
    }

    Models.Client findClient(String phone, String car) {
        for (Models.Client item : clients) {
            if ((!phone.isEmpty() && item.phone.equals(phone)) || (!car.isEmpty() && item.car.equalsIgnoreCase(car))) return item;
        }
        return null;
    }

    void sortOrders() {
        orders.sort(Comparator.comparingLong(item -> item.startAt));
    }

    private void seed() {
        Models.Service polish = new Models.Service(newId(), "Полировка кузова", "Кузов", 18000, 360);
        Models.Service ceramic = new Models.Service(newId(), "Керамика", "Кузов", 25000, 240);
        Models.Service interior = new Models.Service(newId(), "Химчистка салона", "Салон", 15000, 300);
        services.add(polish); services.add(ceramic); services.add(interior);

        sortOrders();
    }
}
