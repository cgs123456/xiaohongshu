package com.itcast.util;

import java.util.Map;
import java.util.TreeMap;

/**
 * SKU 规格键规范化：attributes map -> "label:value; ..."（label 排序，"; " 分隔）。
 * 与订单明细 attributes 快照同格式 —— 同一函数生成保证两端一致。
 */
public final class SkuKeyUtil {

    private SkuKeyUtil() {
    }

    /** null/空 map 返回 ""（无属性商品的默认 SKU） */
    public static String build(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return "";
        }
        // TreeMap 按 label 排序，保证同组合不同入参顺序得到同一 key
        Map<String, String> sorted = new TreeMap<>(attributes);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null || e.getValue().isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(e.getKey().trim()).append(":").append(e.getValue().trim());
        }
        return sb.toString();
    }
}
