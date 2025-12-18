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
  private String companyId; // UUID as string
  private String companyNo; // Company registration number/code
  private String companyName; // Company name (TODO: get from company service)
}
