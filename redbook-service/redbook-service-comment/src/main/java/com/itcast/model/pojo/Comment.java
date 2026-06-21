package com.itcast.model.pojo;

import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;

@Data
@Document(collection = "rb_comment")
public class Comment implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @Id
    private ObjectId id;

    /**
     * 内容
     */
    private String content;

    /**
     * 时间
     */
    private String time;

    /**
     * 父id--0代表是评论，非0代表回复，parentId代表回复哪个评论或回复
     */
    @Indexed
    private String parentId;

    /**
     * 笔记id
     */
    @Indexed
    private Long noteId;

    /**
     * 用户id
     */
    private Integer userId;
}
