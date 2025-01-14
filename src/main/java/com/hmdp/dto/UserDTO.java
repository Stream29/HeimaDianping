package com.hmdp.dto;

import lombok.Builder;
import lombok.Data;
import lombok.Setter;

@Data
@Builder
public class UserDTO {
    private Long id;
    private String nickName;
    private String icon;
}
