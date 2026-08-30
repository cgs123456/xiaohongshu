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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Order(7)
@Slf4j
public class GetTopicHandler extends NoteHandler {

    // #(\S+)：话题之间无需强制空格分隔，其余文本交由后续 span 替换处理
    private final Pattern pattern = Pattern.compile("#(\\S+)");

    // 记录创建的新话题ID和更新的旧话题（topicId -> oldHot），用于补偿
    private final ThreadLocal<Map<Integer, Integer>> topicCompensationData = ThreadLocal.withInitial(HashMap::new);

    @Autowired
    private TopicMapper topicMapper;

    @Override
    public void handle(NoteDto noteDto) throws IOException, InterruptedException {
        // 清空补偿数据
        topicCompensationData.get().clear();

        Matcher matcher = pattern.matcher(noteDto.getContent() == null ? "" : noteDto.getContent());
        StringBuilder sb = new StringBuilder();
        // 1.先收集所有话题（去重，保持出现顺序）
        Set<String> topicContents = new LinkedHashSet<>();
        while (matcher.find()) {
            String match = matcher.group(1);
            topicContents.add(match);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(
                    "<span style='color: #687F9D;'>" + "#" + match + " " + "</span>"));
        }
        // 2.appendTail 移出 while 循环，只追加一次
        matcher.appendTail(sb);
        if (sb.length() > 0) noteDto.setContent(sb.toString());

        // 3.多个话题先收集，再一次性批量查询已存在话题
        List<Integer> topicIdList = new ArrayList<>();
        Map<Integer, Integer> compensationData = topicCompensationData.get();
        if (!topicContents.isEmpty()) {
            LambdaQueryWrapper<Topic> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.in(Topic::getContent, topicContents);
            List<Topic> existTopics = topicMapper.selectList(queryWrapper);
            Map<String, Topic> existMap = existTopics.stream()
                    .collect(Collectors.toMap(Topic::getContent, t -> t, (a, b) -> a));

            // 4.收集需要新建的话题，批量插入
            List<Topic> newTopics = new ArrayList<>();
            for (String content : topicContents) {
                if (!existMap.containsKey(content)) {
                    Topic dbTopic = new Topic();
                    dbTopic.setContent(content);
                    dbTopic.setHot(1);
                    newTopics.add(dbTopic);
                }
            }
            Map<String, Integer> newTopicIdMap = new HashMap<>();
            for (Topic dbTopic : newTopics) {
                topicMapper.insert(dbTopic);
                newTopicIdMap.put(dbTopic.getContent(), dbTopic.getId());
                // 新创建的话题，记录 topicId -> null（表示需要删除）
                compensationData.put(dbTopic.getId(), null);
            }

            // 5.逐个处理话题 id 与热度更新
            for (String content : topicContents) {
                Topic topic = existMap.get(content);
                if (topic == null) {
                    topicIdList.add(newTopicIdMap.get(content));
                    continue;
                }
                // 更新的话题，记录 topicId -> oldHot（表示需要恢复 hot 值）
                Integer oldHot = topic.getHot();
                topic.setHot(oldHot == null ? 1 : oldHot + 1);
                topicMapper.updateById(topic);
                topicIdList.add(topic.getId());
                compensationData.put(topic.getId(), oldHot);
            }
        }
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
