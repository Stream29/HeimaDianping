package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    private Boolean success;
    private String errorMsg;
    private Object data;
    private Long total;

    public static @NotNull Result ok(){
        return new Result(true, null, null, null);
    }
    public static @NotNull Result ok(Object data){
        return new Result(true, null, data, null);
    }
    public static @NotNull Result ok(List<?> data, Long total){
        return new Result(true, null, data, total);
    }
    public static @NotNull Result fail(String errorMsg){
        return new Result(false, errorMsg, null, null);
    }
}
