package com.fota.client.common;

import lombok.Data;

/**
 * @author Gavin Shen
 * @Date 2018/7/7
 */
@Data
public class PageQuery {

    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private Integer pageNo;
    private Integer pageSize;
    private Integer startRow;
    private Integer endRow;

    public void setPageNo(Integer pageNo) {
        if (pageNo == null || pageNo <= 0) {
            return;
        }
        this.pageNo = pageNo;
        if (pageSize == null || pageSize <= 0) {
            pageSize = DEFAULT_PAGE_SIZE;
        }
        this.startRow = (pageNo - 1) * pageSize;
        this.endRow = this.startRow + pageSize;
    }

    public void setPageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return;
        }
        this.pageSize = pageSize;
        if (pageNo == null || pageNo <= 0) {
            this.pageNo = DEFAULT_PAGE_NO;
        }
        this.startRow = (pageNo - 1) * pageSize;
        this.endRow = this.startRow + pageSize;
    }

}
