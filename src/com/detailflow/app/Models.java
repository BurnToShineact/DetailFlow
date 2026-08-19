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
        String carModelId;
        String carModel;
        String plate;
        String legacyCarNote;

        Client(String id, String name, String phone, String car, String carModelId, String carModel, String plate) {
            this.id = id;
            this.name = name;
            this.phone = phone;
            this.car = car;
            this.carModelId = carModelId;
            this.carModel = carModel;
            this.plate = plate;
            this.legacyCarNote = "";
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("id", id).put("name", name).put("phone", phone)
                    .put("car", car).put("carModelId", carModelId).put("carModel", carModel)
                    .put("plate", plate).put("legacyCarNote", legacyCarNote);
        }

        static Client fromJson(JSONObject json) {
            Client client = new Client(json.optString("id"), json.optString("name"),
                    json.optString("phone"), json.optString("car"),
                    json.optString("carModelId"), json.optString("carModel"), json.optString("plate"));
            client.legacyCarNote = json.optString("legacyCarNote", json.optString("carNote"));
            return client;
        }
    }

    static final class CarModel {
        String id;
        String make;
        String name;
        String note;

        CarModel(String id, String make, String name, String note) {
            this.id = id;
            this.make = make;
            this.name = name;
            this.note = note;
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("id", id).put("make", make).put("name", name).put("note", note);
        }

        static CarModel fromJson(JSONObject json) {
            return new CarModel(json.optString("id"), json.optString("make"),
                    json.optString("name"), json.optString("note"));
        }
    }

    static final class Order {
        String id;
        String clientId;
        String clientName;
        String phone;
        String car;
        String carModelId;
        String carModel;
        String plate;
        String orderNote;
        List<String> serviceIds = new ArrayList<>();
        long total;
        long startAt;
        long deadlineAt;
        String status;
        boolean paid;
        List<String> beforeUris = new ArrayList<>();
        List<String> afterUris = new ArrayList<>();

        Order(String id, String clientId, String clientName, String phone, String car,
              String carModelId, String carModel, String plate, String orderNote,
              long total, long startAt, long deadlineAt, String status, boolean paid) {
            this.id = id;
            this.clientId = clientId;
            this.clientName = clientName;
            this.phone = phone;
            this.car = car;
            this.carModelId = carModelId;
            this.carModel = carModel;
            this.plate = plate;
            this.orderNote = orderNote == null ? "" : orderNote;
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
                    .put("carModelId", carModelId).put("carModel", carModel)
                    .put("plate", plate).put("orderNote", orderNote)
                    .put("serviceIds", ids).put("total", total).put("startAt", startAt)
                    .put("deadlineAt", deadlineAt).put("status", status).put("paid", paid)
                    .put("beforeUris", before).put("afterUris", after);
        }

        static Order fromJson(JSONObject json) {
            Order order = new Order(json.optString("id"), json.optString("clientId"),
                    json.optString("clientName"), json.optString("phone"), json.optString("car"),
                    json.optString("carModelId"), json.optString("carModel"), json.optString("plate"),
                    json.optString("orderNote", json.optString("carNote")),
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
        String orderId;

        Transaction(String id, String title, long amount, long createdAt, boolean income, String orderId) {
            this.id = id;
            this.title = title;
            this.amount = amount;
            this.createdAt = createdAt;
            this.income = income;
            this.orderId = orderId;
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("id", id).put("title", title).put("amount", amount)
                    .put("createdAt", createdAt).put("income", income).put("orderId", orderId);
        }

        static Transaction fromJson(JSONObject json) {
            return new Transaction(json.optString("id"), json.optString("title"),
                    json.optLong("amount"), json.optLong("createdAt"), json.optBoolean("income"),
                    json.optString("orderId"));
        }
    }
}
