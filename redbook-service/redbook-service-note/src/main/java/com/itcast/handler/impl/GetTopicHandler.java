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

    @Autowired
    private TopicMapper topicMapper;

    @Override
    public void handle(NoteDto noteDto) throws IOException, InterruptedException {
        // 记录本次新建的话题 ID（补偿时删除）与被自增的存量话题 ID（补偿时原子回退热度）。
        // 存放在请求级 NoteDto 上（而非 ThreadLocal）：成功路径随 dto 一起销毁无线程残留，
        // 失败路径 compensate 仍能取到数据完成回滚
        java.util.Set<Integer> created =
                noteDto.getCreatedTopicIds() == null
                        ? new java.util.HashSet<>()
                        : noteDto.getCreatedTopicIds();
        created.clear();
        noteDto.setCreatedTopicIds(created);
        java.util.Set<Integer> incremented =
                noteDto.getIncrementedTopicIds() == null
                        ? new java.util.HashSet<>()
                        : noteDto.getIncrementedTopicIds();
        incremented.clear();
        noteDto.setIncrementedTopicIds(incremented);

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
                created.add(dbTopic.getId());
            }

            // 5.逐个处理话题 id 与热度更新（原子自增，替代读改写避免并发丢失更新）
            for (String content : topicContents) {
                Topic topic = existMap.get(content);
                if (topic == null) {
                    topicIdList.add(newTopicIdMap.get(content));
                    continue;
                }
                topicMapper.incrementHot(topic.getId());
                topicIdList.add(topic.getId());
                incremented.add(topic.getId());
            }
        }
        noteDto.setTopicList(topicIdList);
        log.info("===已设置笔记话题{}===", topicIdList);
    }

    @Override
    public void compensate(NoteDto noteDto) {
        // 补偿：新建话题删除、存量话题原子回退热度
        java.util.Set<Integer> created = noteDto.getCreatedTopicIds();
        java.util.Set<Integer> incremented = noteDto.getIncrementedTopicIds();
        if ((created == null || created.isEmpty()) && (incremented == null || incremented.isEmpty())) {
            return;
        }
        try {
            if (created != null) {
                for (Integer topicId : created) {
                    topicMapper.deleteById(topicId);
                    log.info("补偿操作：已删除新创建的话题，topicId: {}", topicId);
                }
                created.clear();
            }
            if (incremented != null) {
                for (Integer topicId : incremented) {
                    // 原子回退热度（此前读改写恢复旧值会吞掉并发其他笔记的自增）
                    topicMapper.decrementHot(topicId);
                    log.info("补偿操作：已回退话题 hot 值，topicId: {}", topicId);
                }
                incremented.clear();
            }
        } catch (Exception e) {
            log.error("补偿操作失败：恢复话题状态时出错", e);
        }
    }
}
