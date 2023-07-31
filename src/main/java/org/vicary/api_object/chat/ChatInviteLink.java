package org.vicary.api_object.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.vicary.api_object.ApiObject;
import org.vicary.api_object.User;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatInviteLink implements ApiObject {
    @JsonProperty("invite_link")
    private String inviteLink;

    @JsonProperty("creator")
    private User creator;

    @JsonProperty("creates_join_request")
    private Boolean createsJoinRequest;

    @JsonProperty("is_primary")
    private Boolean isPrimary;

    @JsonProperty("is_revoked")
    private Boolean isRevoked;

    @JsonProperty("name")
    private String name;

    @JsonProperty("expire_date")
    private Integer expireDate;

    @JsonProperty("member_limit")
    private Integer memberLimit;

    @JsonProperty("pending_join_request_count")
    private Integer pendingJoinRequestCount;
}