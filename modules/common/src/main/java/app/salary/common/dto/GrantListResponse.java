package app.salary.common.dto;

import app.salary.common.annotation.ExcludeFromCodeCoverage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@ExcludeFromCodeCoverage
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "The signed-in user's saved RSU grants, oldest first")
public class GrantListResponse {
    private List<RsuGrant> items;
}
