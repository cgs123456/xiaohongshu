package com.itcast.handler.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itcast.handler.NoteHandler;
import com.itcast.mapper.TopicMapper;
import com.itcast.model.dto.NoteDto;
import com.itcast.model.pojo.Topic;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(6)
@Slf4j
public class GetTopicHandler extends NoteHandler {

    private final Pattern pattern = Pattern.compile("#(\\S+)\\s");

    // 记录创建的新话题ID和更新的旧话题（topicId -> oldHot），用于补偿
    private final ThreadLocal<Map<Integer, Integer>> topicCompensationData = ThreadLocal.withInitial(HashMap::new);

    @Autowired
    private TopicMapper topicMapper;

    @Override
    public void handle(NoteDto noteDto) throws IOException, InterruptedException {
        // 清空补偿数据
        topicCompensationData.get().clear();

        Matcher matcher = pattern.matcher(noteDto.getContent());
        StringBuilder sb = new StringBuilder();
        List<Integer> topicIdList = new ArrayList<>();
        while (matcher.find()) {
            // 获取匹配到的字符串
            String match = matcher.group(1);
            LambdaQueryWrapper<Topic> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Topic::getContent, match);
            Topic topic = topicMapper.selectOne(queryWrapper);

            // 保存话题
            if (topic == null) {
                // 新创建的话题，记录 topicId -> null（表示需要删除）
                Topic dbTopic = new Topic();
                dbTopic.setContent(match);
                dbTopic.setHot(1);
                topicMapper.insert(dbTopic);
                topicIdList.add(dbTopic.getId());
                topicCompensationData.get().put(dbTopic.getId(), null); // null 表示新创建，需要删除
            } else {
                // 更新的话题，记录 topicId -> oldHot（表示需要恢复 hot 值）
                Integer oldHot = topic.getHot();
                topic.setHot(topic.getHot() + 1);
                topicMapper.updateById(topic);
                topicIdList.add(topic.getId());
                topicCompensationData.get().put(topic.getId(), oldHot); // 记录旧的 hot 值
            }

            // 处理笔记内容中的话题
            matcher.appendReplacement(sb, "<span style='color: #687F9D;'>" + "#" + match + " " + "</span>");
            matcher.appendTail(sb);
        }
        if (sb.length() > 0) noteDto.setContent(sb.toString());
        noteDto.setTopicList(topicIdList);
        log.info("===已设置笔记话题{}===", topicIdList);
    }

    @Override
    public void compensate(NoteDto noteDto) {
        // 补偿：恢复话题状态
        Map<Integer, Integer> compensationData = topicCompensationData.get();
        try {
            for (Map.Entry<Integer, Integer> entry : compensationData.entrySet()) {
                Integer topicId = entry.getKey();
                Integer oldHot = entry.getValue();
                
                if (oldHot == null) {
                    // 新创建的话题，需要删除
                    topicMapper.deleteById(topicId);
                    log.info("补偿操作：已删除新创建的话题，topicId: {}", topicId);
                } else {
                    // 更新的话题，需要恢复 hot 值
                    Topic topic = topicMapper.selectById(topicId);
                    if (topic != null) {
                        topic.setHot(oldHot);
                        topicMapper.updateById(topic);
                        log.info("补偿操作：已恢复话题 hot 值，topicId: {}, oldHot: {}", topicId, oldHot);
                    }
                }
            }
            // 清空补偿数据
            compensationData.clear();
        } catch (Exception e) {
            log.error("补偿操作失败：恢复话题状态时出错", e);
        }
    }
}
