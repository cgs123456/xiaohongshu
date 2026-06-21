package com.itcast.model.pojo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class TimeMessage implements Serializable {
    private String className;
    private String methodName;
    private Long processTime;
}
