package app.salary.rulepack.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.*;

@Data
@AllArgsConstructor
public class RulePackListResponse {
    private List<RulePackDto> rulePacks;
    private Long total;
    private Integer page;
    private Integer size;
}
