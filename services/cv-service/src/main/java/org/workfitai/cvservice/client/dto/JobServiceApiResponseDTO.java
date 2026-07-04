package org.workfitai.cvservice.client.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class JobServiceApiResponseDTO<T> {
    private int status;
    private String message;
    private T data;
}
