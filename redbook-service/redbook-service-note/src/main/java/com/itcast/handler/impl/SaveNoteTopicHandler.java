package com.itcast.handler.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itcast.handler.NoteHandler;
import com.itcast.mapper.NoteTopicMapper;
import com.itcast.model.dto.NoteDto;
import com.itcast.model.pojo.NoteTopic;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@Order(8)
@Slf4j
public class SaveNoteTopicHandler extends NoteHandler {

    @Autowired
    private NoteTopicMapper noteTopicMapper;

    @Override
    public void handle(NoteDto noteDto) throws IOException, InterruptedException {
        List<Integer> topicIdList = noteDto.getTopicList();
        for (Integer topicId : topicIdList) {
            NoteTopic noteTopic = new NoteTopic();
            noteTopic.setTopicId(topicId);
            noteTopic.setNoteId(noteDto.getId());
            noteTopicMapper.insert(noteTopic);
        }
        log.info("===已保存笔记话题关联，noteId: {}, topicIdList: {}===", noteDto.getId(), topicIdList);
    }

    @Override
    public void compensate(NoteDto noteDto) {
        // 补偿：删除已保存的笔记话题关联
        try {
            LambdaQueryWrapper<NoteTopic> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(NoteTopic::getNoteId, noteDto.getId());
            noteTopicMapper.delete(queryWrapper);
            log.info("补偿操作：已删除笔记话题关联，noteId: {}", noteDto.getId());
        } catch (Exception e) {
            log.error("补偿操作失败：删除笔记话题关联 noteId: {} 时出错", noteDto.getId(), e);
        }
    }
}
