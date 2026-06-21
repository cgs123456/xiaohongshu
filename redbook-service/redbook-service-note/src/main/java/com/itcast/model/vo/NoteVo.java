package com.itcast.model.vo;

import com.itcast.model.pojo.Note;
import com.itcast.model.pojo.User;
import lombok.Data;

import java.io.Serializable;

/**
 * 笔记视图对象
 */
@Data
public class NoteVo implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 笔记基础信息（组合方式）
     */
    private Note note;

    /**
     * 发布笔记用户信息
     */
    private User user;

    /**
     * 处理后的时间（如：2小时前）
     */
    private String dealTime;

    /**
     * 距离（如：1.2km）
     */
    private String distance;

    /**
     * 评论数
     */
    private Integer comment;
}
