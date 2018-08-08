package com.fota.trade.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserContractLeverDTO implements Serializable {

    private static final long serialVersionUID = -5889091325903283851L;
    public int assetId;
    public java.lang.String assetName;
    public int lever;
}