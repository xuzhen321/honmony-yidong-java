package com.example.demo.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "分页结果")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    @Schema(description = "当前页码，从 1 开始", example = "1")
    private long pageNum;

    @Schema(description = "每页条数", example = "10")
    private long pageSize;

    @Schema(description = "总记录数", example = "25")
    private long total;

    @Schema(description = "总页数", example = "3")
    private long pages;

    @Schema(description = "当前页数据")
    private List<T> records;
}
