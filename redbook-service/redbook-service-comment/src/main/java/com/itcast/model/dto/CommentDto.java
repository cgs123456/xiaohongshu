package com.itcast.model.dto;

import com.itcast.model.pojo.Comment;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class CommentDto extends Comment {
}
