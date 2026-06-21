package com.itcast.model.vo;

import com.itcast.model.pojo.Like;
import com.itcast.model.pojo.Note;
import com.itcast.model.pojo.User;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LikeVo {
    private Like like;
    private Note note;
    private User user;
}
