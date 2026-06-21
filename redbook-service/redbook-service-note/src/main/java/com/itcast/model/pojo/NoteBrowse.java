package com.itcast.model.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("rb_note_browse")
public class NoteBrowse implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 笔记id
     */
    @TableField("note_id")
    private Long noteId;

    /**
     * 用户id
     */
    @TableField("user_id")
    private Integer userId;

    /**
     * 浏览时间
     */
    @TableField("time")
    private String time;
}
