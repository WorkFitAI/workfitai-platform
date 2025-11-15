package org.workfitai.userservice.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class HRResponse extends UserBaseResponse {
  private String department;
  private String address;
  private String companyId;
}
