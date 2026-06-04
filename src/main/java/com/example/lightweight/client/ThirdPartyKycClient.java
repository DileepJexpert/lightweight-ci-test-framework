package com.example.lightweight.client;

import com.example.lightweight.domain.KycRequest;
import com.example.lightweight.domain.KycResponse;

public interface ThirdPartyKycClient {
    KycResponse validate(KycRequest request);
}
