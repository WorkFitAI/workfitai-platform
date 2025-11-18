package org.workfitai.userservice.dto.response;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class AdminResponse extends UserBaseResponse {
  // Nếu sau này admin có thêm quyền quản trị hệ thống thì thêm field sau
  private String note;
}
