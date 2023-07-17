package org.example.api_object.keyboard;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.example.api_object.ApiObject;

@Data
@RequiredArgsConstructor
@AllArgsConstructor
@Builder
public class ReplyKeyboardRemove implements ApiObject, ReplyMarkup {
    @JsonProperty("remove_keyboard")
    private Boolean removeKeyboard;

    @JsonProperty("selective")
    private Boolean selective;
}
