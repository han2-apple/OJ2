package com.yupi.yuoj.common;

import com.yupi.yuoj.constant.CommonConstant;
import lombok.Data;

/**
 * @author han
 */
@Data
public class PageRequest {

    private long current = 1;

    private long pageSize = 10;

    private String sortField;

    private String sortOrder = CommonConstant.SORT_ORDER_ASC;
}
