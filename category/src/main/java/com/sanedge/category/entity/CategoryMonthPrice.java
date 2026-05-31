package com.sanedge.category.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryMonthPrice {
    private String month;
    private Integer categoryId;
    private String categoryName;
    private Integer orderCount;
    private Integer itemsSold;
    private Long totalRevenue;
}
