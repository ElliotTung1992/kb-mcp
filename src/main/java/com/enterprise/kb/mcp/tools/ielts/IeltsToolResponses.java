package com.enterprise.kb.mcp.tools.ielts;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class IeltsToolResponses {

    private IeltsToolResponses() {
    }

    static Map<String, Object> data(String label, JsonNode resp) {
        JsonNode data = resp.path("data");
        Map<String, Object> result = base(label, resp);
        result.put("empty", isEmpty(data));
        result.put("message", isEmpty(data) ? label + "：暂无数据。" : null);
        result.put("data", jsonValue(data));
        return result;
    }

    static Map<String, Object> paged(
            String label,
            JsonNode resp,
            Map<String, Object> filters,
            int requestedPage,
            int requestedPageSize) {
        return paged(label, resp, filters, requestedPage, requestedPageSize, IeltsToolResponses::objectValue);
    }

    static Map<String, Object> paged(
            String label,
            JsonNode resp,
            Map<String, Object> filters,
            int requestedPage,
            int requestedPageSize,
            Function<JsonNode, Map<String, Object>> itemMapper) {

        JsonNode data = resp.path("data");
        JsonNode itemsNode = pageItems(data);

        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode item : itemsNode) {
            items.add(itemMapper.apply(item));
        }

        Map<String, Object> result = base(label, resp);
        result.put("empty", items.isEmpty());
        result.put("message", items.isEmpty() ? "未找到符合条件的" + label + "。" : null);
        result.put("filters", filters);
        result.put("pagination", pagination(data, requestedPage, requestedPageSize, items.size()));
        result.put("items", items);
        return result;
    }

    static Map<String, Object> error(String label, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("label", label);
        result.put("message", message);
        return result;
    }

    static Map<String, Object> filters(Object... kvPairs) {
        Map<String, Object> filters = new LinkedHashMap<>();
        for (int i = 0; i < kvPairs.length; i += 2) {
            filters.put(String.valueOf(kvPairs[i]), kvPairs[i + 1]);
        }
        return filters;
    }

    static Map<String, Object> objectValue(JsonNode node) {
        Object value = jsonValue(node);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("value", value);
        return result;
    }

    private static Map<String, Object> base(String label, JsonNode resp) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", resp.path("success").asBoolean(true));
        result.put("label", label);
        return result;
    }

    private static Map<String, Object> pagination(
            JsonNode data,
            int requestedPage,
            int requestedPageSize,
            int returnedElements) {
        Map<String, Object> pagination = new LinkedHashMap<>();
        pagination.put("requestedPage", requestedPage);
        pagination.put("requestedPageSize", requestedPageSize);
        pagination.put("page", data.path("page").isMissingNode() ? requestedPage - 1 : data.path("page").asInt());
        pagination.put("size", data.path("size").isMissingNode() ? requestedPageSize : data.path("size").asInt());
        pagination.put("totalElements", totalElements(data));
        pagination.put("totalPages", data.path("totalPages").asInt(0));
        pagination.put("returnedElements", returnedElements);
        return pagination;
    }

    private static JsonNode pageItems(JsonNode data) {
        if (data.isArray()) {
            return data;
        }
        JsonNode content = data.path("content");
        if (!content.isMissingNode()) {
            return content;
        }
        return data.path("items");
    }

    private static long totalElements(JsonNode data) {
        if (data.isArray()) {
            return data.size();
        }
        JsonNode totalElements = data.path("totalElements");
        if (!totalElements.isMissingNode()) {
            return totalElements.asLong();
        }
        return data.path("total").asLong();
    }

    private static Object jsonValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isObject()) {
            Map<String, Object> result = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                result.put(field.getKey(), jsonValue(field.getValue()));
            }
            return result;
        }
        if (node.isArray()) {
            List<Object> result = new ArrayList<>();
            for (JsonNode item : node) {
                result.add(jsonValue(item));
            }
            return result;
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        return node.asText();
    }

    private static boolean isEmpty(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode() || node.isEmpty();
    }
}
