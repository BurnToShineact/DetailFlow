package com.detailflow.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class Models {
    private Models() {}

    static final class Service {
        String id;
        String name;
        String category;
        long price;
        int durationMinutes;

        Service(String id, String name, String category, long price, int durationMinutes) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.price = price;
            this.durationMinutes = durationMinutes;
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("id", id).put("name", name).put("category", category)
                    .put("price", price).put("durationMinutes", durationMinutes);
        }

        static Service fromJson(JSONObject json) {
            return new Service(json.optString("id"), json.optString("name"),
                    json.optString("category", "Другое"), json.optLong("price"),
                    json.optInt("durationMinutes", 60));
        }
    }

    static final class Client {
        String id;
        String name;
        String phone;
        String car;

        Client(String id, String name, String phone, String car) {
            this.id = id;
            this.name = name;
            this.phone = phone;
            this.car = car;
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("id", id).put("name", name).put("phone", phone).put("car", car);
        }

        static Client fromJson(JSONObject json) {
            return new Client(json.optString("id"), json.optString("name"),
                    json.optString("phone"), json.optString("car"));
        }
    }

    static final class Order {
        String id;
        String clientId;
        String clientName;
        String phone;
        String car;
        List<String> serviceIds = new ArrayList<>();
        long total;
        long startAt;
        long deadlineAt;
        String status;
        boolean paid;
        List<String> beforeUris = new ArrayList<>();
        List<String> afterUris = new ArrayList<>();

        Order(String id, String clientId, String clientName, String phone, String car,
              long total, long startAt, long deadlineAt, String status, boolean paid) {
            this.id = id;
            this.clientId = clientId;
            this.clientName = clientName;
            this.phone = phone;
            this.car = car;
            this.total = total;
            this.startAt = startAt;
            this.deadlineAt = deadlineAt;
            this.status = status;
            this.paid = paid;
        }

        JSONObject toJson() throws JSONException {
            JSONArray ids = new JSONArray();
            for (String serviceId : serviceIds) ids.put(serviceId);
            JSONArray before = new JSONArray();
            JSONArray after = new JSONArray();
            for (String uri : beforeUris) before.put(uri);
            for (String uri : afterUris) after.put(uri);
            return new JSONObject().put("id", id).put("clientId", clientId)
                    .put("clientName", clientName).put("phone", phone).put("car", car)
                    .put("serviceIds", ids).put("total", total).put("startAt", startAt)
                    .put("deadlineAt", deadlineAt).put("status", status).put("paid", paid)
                    .put("beforeUris", before).put("afterUris", after);
        }

        static Order fromJson(JSONObject json) {
            Order order = new Order(json.optString("id"), json.optString("clientId"),
                    json.optString("clientName"), json.optString("phone"), json.optString("car"),
                    json.optLong("total"), json.optLong("startAt"), json.optLong("deadlineAt"),
                    json.optString("status", "Запланировано"), json.optBoolean("paid"));
            JSONArray ids = json.optJSONArray("serviceIds");
            if (ids != null) for (int i = 0; i < ids.length(); i++) order.serviceIds.add(ids.optString(i));
            JSONArray before = json.optJSONArray("beforeUris");
            JSONArray after = json.optJSONArray("afterUris");
            if (before != null) for (int i = 0; i < before.length(); i++) order.beforeUris.add(before.optString(i));
            if (after != null) for (int i = 0; i < after.length(); i++) order.afterUris.add(after.optString(i));
            String legacyBefore = json.optString("beforeUri");
            String legacyAfter = json.optString("afterUri");
            if (!legacyBefore.isEmpty() && order.beforeUris.isEmpty()) order.beforeUris.add(legacyBefore);
            if (!legacyAfter.isEmpty() && order.afterUris.isEmpty()) order.afterUris.add(legacyAfter);
            return order;
        }
    }

    static final class Transaction {
        String id;
        String title;
        long amount;
        long createdAt;
        boolean income;

        Transaction(String id, String title, long amount, long createdAt, boolean income) {
            this.id = id;
            this.title = title;
            this.amount = amount;
            this.createdAt = createdAt;
            this.income = income;
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("id", id).put("title", title).put("amount", amount)
                    .put("createdAt", createdAt).put("income", income);
        }

        static Transaction fromJson(JSONObject json) {
            return new Transaction(json.optString("id"), json.optString("title"),
                    json.optLong("amount"), json.optLong("createdAt"), json.optBoolean("income"));
        }
    }
}
