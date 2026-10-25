package com.skygrid.mission.mission;

import org.springframework.data.domain.Page;

import java.util.List;

public record MissionPageResponse(
        List<MissionResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    static MissionPageResponse from(Page<MissionResponse> result) {
        return new MissionPageResponse(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}
