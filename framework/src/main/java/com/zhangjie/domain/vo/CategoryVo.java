package com.zhangjie.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CategoryVo {
    private Long id;

    //分类名
    private String name;
    //父分类id，如果没有父分类为-1
}
