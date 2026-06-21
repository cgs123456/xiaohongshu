package com.itcast.model.pojo;

import com.itcast.enums.LogType;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
public class BehaviorMessage implements Serializable {
    private Integer userId;
    private Long noteId;
    private Date viewTime;
    private Date leaveTime;
    private Date likeTime;
    private LogType logType;
}
