package com.domain.auth.dto.request;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegistrationRequest {

  String username;

  String name;

  String phone;

  String email;

  String password;

}
