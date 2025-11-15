package org.workfitai.userservice.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class AdminResponse extends UserBaseResponse {
  // Nếu sau này admin có thêm quyền quản trị hệ thống thì thêm field sau
  private String note;
}
