package com.itcast.model.dto;

import com.itcast.model.pojo.Note;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class NoteDto extends Note {
    private MultipartFile file;
    private List<Integer> topicList;
}
