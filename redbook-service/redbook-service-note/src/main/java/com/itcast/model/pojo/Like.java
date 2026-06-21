package com.itcast.model.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("rb_like")
public class Like implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 笔记id
     */
    @TableField("note_id")
    private Long noteId;

    @TableField("own_id")
    private Integer ownId;

    /**
     * 用户id
     */
    @TableField("user_id")
    private Integer userId;

    @TableField("create_time")
    private Date createTime;
}
