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
    final List<String> carMakes = new ArrayList<>();
    final List<Models.CarModel> carModels = new ArrayList<>();
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
            JSONArray carMakesJson = root.optJSONArray("carMakes");
            JSONArray carModelsJson = root.optJSONArray("carModels");
            boolean carMakesReady = root.optBoolean("carMakesReady", false);
            if (servicesJson != null) for (int i = 0; i < servicesJson.length(); i++) services.add(Models.Service.fromJson(servicesJson.getJSONObject(i)));
            if (clientsJson != null) for (int i = 0; i < clientsJson.length(); i++) clients.add(Models.Client.fromJson(clientsJson.getJSONObject(i)));
            if (ordersJson != null) for (int i = 0; i < ordersJson.length(); i++) orders.add(Models.Order.fromJson(ordersJson.getJSONObject(i)));
            if (transactionsJson != null) for (int i = 0; i < transactionsJson.length(); i++) transactions.add(Models.Transaction.fromJson(transactionsJson.getJSONObject(i)));
            if (carMakesReady && carMakesJson != null) for (int i = 0; i < carMakesJson.length(); i++) addCarMake(carMakesJson.optString(i));
            else addDefaultCarMakes();
            if (carModelsJson != null) for (int i = 0; i < carModelsJson.length(); i++) carModels.add(Models.CarModel.fromJson(carModelsJson.getJSONObject(i)));
        } catch (Exception ignored) {
            services.clear(); clients.clear(); orders.clear(); transactions.clear(); carMakes.clear(); carModels.clear();
            seed();
            save();
        }
        linkCarModels();
        migrateOrderServicePrices();
        sortOrders();
        save();
    }

    void save() {
        try {
            JSONArray servicesJson = new JSONArray();
            JSONArray clientsJson = new JSONArray();
            JSONArray ordersJson = new JSONArray();
            JSONArray transactionsJson = new JSONArray();
            JSONArray carMakesJson = new JSONArray();
            JSONArray carModelsJson = new JSONArray();
            for (Models.Service item : services) servicesJson.put(item.toJson());
            for (Models.Client item : clients) clientsJson.put(item.toJson());
            for (Models.Order item : orders) ordersJson.put(item.toJson());
            for (Models.Transaction item : transactions) transactionsJson.put(item.toJson());
            for (String item : carMakes) carMakesJson.put(item);
            for (Models.CarModel item : carModels) carModelsJson.put(item.toJson());
            JSONObject root = new JSONObject().put("services", servicesJson).put("clients", clientsJson)
                    .put("orders", ordersJson).put("transactions", transactionsJson)
                    .put("carMakes", carMakesJson).put("carMakesReady", true).put("carModels", carModelsJson);
            preferences.edit().putString("data", root.toString()).apply();
        } catch (Exception ignored) { }
    }

    String exportData() {
        return preferences.getString("data", "");
    }

    boolean importData(String raw) {
        if (raw == null || raw.trim().isEmpty()) return false;
        try {
            JSONObject root = new JSONObject(raw);
            JSONArray servicesJson = root.optJSONArray("services");
            JSONArray clientsJson = root.optJSONArray("clients");
            JSONArray ordersJson = root.optJSONArray("orders");
            JSONArray transactionsJson = root.optJSONArray("transactions");
            JSONArray carModelsJson = root.optJSONArray("carModels");
            if (servicesJson == null || clientsJson == null || ordersJson == null || transactionsJson == null) return false;
            for (int i = 0; i < servicesJson.length(); i++) Models.Service.fromJson(servicesJson.getJSONObject(i));
            for (int i = 0; i < clientsJson.length(); i++) Models.Client.fromJson(clientsJson.getJSONObject(i));
            for (int i = 0; i < ordersJson.length(); i++) Models.Order.fromJson(ordersJson.getJSONObject(i));
            for (int i = 0; i < transactionsJson.length(); i++) Models.Transaction.fromJson(transactionsJson.getJSONObject(i));
            if (carModelsJson != null) for (int i = 0; i < carModelsJson.length(); i++) Models.CarModel.fromJson(carModelsJson.getJSONObject(i));
            String current = preferences.getString("data", "");
            SharedPreferences.Editor editor = preferences.edit().putString("data", root.toString());
            if (!current.isEmpty()) editor.putString("data_before_last_import", current);
            if (!editor.commit()) return false;
            services.clear();
            clients.clear();
            orders.clear();
            transactions.clear();
            carMakes.clear();
            carModels.clear();
            load();
            return true;
        } catch (Exception ignored) {
            return false;
        }
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

    Models.CarModel carModelById(String id) {
        for (Models.CarModel item : carModels) if (item.id.equals(id)) return item;
        return null;
    }

    List<Models.CarModel> carModelsForMake(String make) {
        List<Models.CarModel> result = new ArrayList<>();
        for (Models.CarModel item : carModels) if (item.make.equalsIgnoreCase(make)) result.add(item);
        result.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return result;
    }

    Models.CarModel findCarModel(String make, String name) {
        for (Models.CarModel item : carModels) {
            if (item.make.equalsIgnoreCase(make.trim()) && item.name.equalsIgnoreCase(name.trim())) return item;
        }
        return null;
    }

    Models.CarModel ensureCarModel(String make, String name) {
        String safeMake = make == null ? "" : make.trim();
        String safeName = name == null ? "" : name.trim();
        if (safeMake.isEmpty() || safeName.isEmpty()) return null;
        Models.CarModel existing = findCarModel(safeMake, safeName);
        if (existing != null) return existing;
        addCarMake(safeMake);
        Models.CarModel model = new Models.CarModel(newId(), safeMake, safeName, "");
        carModels.add(model);
        return model;
    }

    boolean updateCarModel(Models.CarModel model, String name, String note) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) return false;
        Models.CarModel duplicate = findCarModel(model.make, normalized);
        if (duplicate != null && !duplicate.id.equals(model.id)) return false;
        model.name = normalized;
        model.note = note == null ? "" : note.trim();
        for (Models.Client client : clients) if (client.carModelId.equals(model.id)) client.carModel = normalized;
        for (Models.Order order : orders) if (order.carModelId.equals(model.id)) order.carModel = normalized;
        return true;
    }

    boolean carModelInUse(String modelId) {
        for (Models.Client client : clients) if (client.carModelId.equals(modelId)) return true;
        for (Models.Order order : orders) if (order.carModelId.equals(modelId)) return true;
        return false;
    }

    Models.Client findClientByNameOrPhone(String name, String phone) {
        String normalizedName = name == null ? "" : name.trim();
        String normalizedPhone = phone == null ? "" : phone.replaceAll("[^0-9]", "");
        Models.Client match = null;
        for (Models.Client item : clients) {
            boolean sameName = !normalizedName.isEmpty() && item.name.trim().equalsIgnoreCase(normalizedName);
            boolean samePhone = !normalizedPhone.isEmpty()
                    && item.phone.replaceAll("[^0-9]", "").equals(normalizedPhone);
            if (!sameName && !samePhone) continue;
            if (match != null && !match.id.equals(item.id)) return null;
            match = item;
        }
        return match;
    }

    void sortOrders() {
        orders.sort(Comparator.comparingLong(item -> item.startAt));
    }

    boolean addCarMake(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) return false;
        for (String item : carMakes) if (item.equalsIgnoreCase(normalized)) return false;
        carMakes.add(normalized);
        carMakes.sort(String.CASE_INSENSITIVE_ORDER);
        return true;
    }

    boolean renameCarMake(String oldValue, String newValue) {
        String normalized = newValue == null ? "" : newValue.trim();
        if (normalized.isEmpty()) return false;
        for (String item : carMakes) {
            if (!item.equalsIgnoreCase(oldValue) && item.equalsIgnoreCase(normalized)) return false;
        }
        int index = carMakes.indexOf(oldValue);
        if (index < 0) return false;
        carMakes.set(index, normalized);
        for (Models.Client client : clients) if (client.car.equalsIgnoreCase(oldValue)) client.car = normalized;
        for (Models.Order order : orders) if (order.car.equalsIgnoreCase(oldValue)) order.car = normalized;
        for (Models.CarModel model : carModels) if (model.make.equalsIgnoreCase(oldValue)) model.make = normalized;
        carMakes.sort(String.CASE_INSENSITIVE_ORDER);
        return true;
    }

    private void addDefaultCarMakes() {
        String[] defaults = {"Audi", "BMW", "Chery", "Chevrolet", "Exeed", "Ford", "Geely", "Haval",
                "Hyundai", "Kia", "Lada", "Lexus", "Mazda", "Mercedes-Benz", "Mitsubishi", "Nissan",
                "Renault", "Skoda", "Toyota", "Volkswagen"};
        for (String value : defaults) addCarMake(value);
    }

    private void linkCarModels() {
        for (Models.Client client : clients) {
            if (client.carModel.isEmpty()) continue;
            Models.CarModel model = carModelById(client.carModelId);
            if (model == null) model = ensureCarModel(client.car, client.carModel);
            if (model != null) client.carModelId = model.id;
        }
        for (Models.Order order : orders) {
            if (order.carModel.isEmpty()) continue;
            Models.CarModel model = carModelById(order.carModelId);
            if (model == null) model = ensureCarModel(order.car, order.carModel);
            if (model != null) order.carModelId = model.id;
        }
    }

    private void migrateOrderServicePrices() {
        for (Models.Order order : orders) {
            if (order.serviceIds.isEmpty() || order.servicePrices.size() >= order.serviceIds.size()) continue;
            long assigned = 0;
            long legacySum = 0;
            List<String> missing = new ArrayList<>();
            for (String serviceId : order.serviceIds) {
                if (order.servicePrices.containsKey(serviceId)) assigned += order.servicePrice(serviceId);
                else {
                    missing.add(serviceId);
                    Models.Service service = serviceById(serviceId);
                    if (service != null) legacySum += Math.max(0, service.legacyPrice);
                }
            }
            long remaining = Math.max(0, order.total - assigned);
            for (int index = 0; index < missing.size(); index++) {
                String serviceId = missing.get(index);
                long price;
                if (index == missing.size() - 1) price = remaining;
                else {
                    Models.Service service = serviceById(serviceId);
                    long weight = service == null ? 0 : Math.max(0, service.legacyPrice);
                    price = legacySum > 0 ? Math.round((double) remaining * weight / legacySum)
                            : remaining / Math.max(1, missing.size());
                    remaining -= price;
                    legacySum -= weight;
                }
                order.servicePrices.put(serviceId, Math.max(0, price));
            }
        }
    }

    private void seed() {
        Models.Service polish = new Models.Service(newId(), "Полировка кузова", "Кузов", 360);
        Models.Service ceramic = new Models.Service(newId(), "Керамика", "Кузов", 240);
        Models.Service interior = new Models.Service(newId(), "Химчистка салона", "Салон", 300);
        services.add(polish); services.add(ceramic); services.add(interior);

        addDefaultCarMakes();

        sortOrders();
    }
}
