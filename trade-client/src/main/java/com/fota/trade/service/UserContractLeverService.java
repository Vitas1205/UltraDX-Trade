package com.fota.trade.service;

import com.fota.trade.domain.UserContractLeverDTO;

import java.util.List;


public interface UserContractLeverService {

    List<UserContractLeverDTO> listUserContractLever(long userId);

    boolean setUserContractLever(long userId, List<UserContractLeverDTO> userContractLeverDTOs);

    UserContractLeverDTO getLeverByAssetId(long userId, int assetId);

    UserContractLeverDTO getLeverByContractId(long userId, long contractId);

  }
