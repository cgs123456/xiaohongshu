package com.itcast.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itcast.annotation.AutoTime;
import com.itcast.model.pojo.Note;
import com.itcast.model.pojo.NoteBrowse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NoteScanMapper extends BaseMapper<NoteBrowse> {

    @AutoTime
    int insert(NoteBrowse entity);

    @Select("SELECT n.* FROM rb_note n " +
            "INNER JOIN rb_note_browse nb ON n.id = nb.note_id " +
            "WHERE nb.user_id = #{userId} AND n.user_id = #{userId}")
    List<Note> selectNotesByUserId(@Param("userId") Integer userId);
}
