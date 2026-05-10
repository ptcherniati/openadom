package fr.inra.oresing.domain.user;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class CreateUserRequest {
    String login;
    String password;
    String email;
    String newPassword;
    String newPasswordConfirm;
    String verificationKey;
    String charte;
}
