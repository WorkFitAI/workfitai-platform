package org.workfitai.userservice.dto.response;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class HRResponse extends UserBaseResponse {
  private String department;
  private String address;
}
