package com.itcast.model.pojo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class CustomAttribute implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 自定义标签
     */
    private String label;

    /**
     * 标签对应的数据
     */
    private List<String> value;
}
