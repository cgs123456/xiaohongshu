package com.itcast.model.pojo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

@Data
@Document(indexName = "rb_note")
public class NoteEs {
    @Id
    private Integer id;
    private String title;
    private String content;
    private String image;
    private String time;
    private String address;
    private Double longitude;
    private Double latitude;
    private Integer userId;
}
