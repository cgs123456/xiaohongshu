package com.itcast.model.pojo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Mapping;

import java.util.List;

@Data
@Document(indexName = "rb_note")
@Mapping(mappingPath = "rb_note-mapping.json")
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

    /** dense_vector 向量，用于 kNN 混合检索（1024 维，cosine 相似度） */
    @Field(type = FieldType.Dense_Vector, dims = 1024)
    private float[] embedding;

    /** AI 生成的 50 字摘要，用于搜索增强 */
    @Field(type = FieldType.Text)
    private String summary;

    /** AI 生成的 3 个话题标签，用于搜索增强 */
    @Field(type = FieldType.Keyword)
    private List<String> aiTags;
}
