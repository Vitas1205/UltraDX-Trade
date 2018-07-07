package com.fota.trade.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author Gavin Shen
 * @Date 2018/6/21
 */
@Data
public class AssetCategoryDO implements Serializable{
    private static final long serialVersionUID = 6541378108066358685L;
    private Integer id;
    private String name;
    private Date gmtCreate;
    private Date gmtModified;
}
