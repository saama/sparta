package com.domain.user.entity;

import com.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Table
@Entity
@Getter
@DynamicInsert
@DynamicUpdate
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false)
  String username;

  @Column(nullable = false)
  String password;

  @Column(nullable = false, unique = true)
  String email;

  @Column(nullable = false, unique = true)
  String name;

  @Column(nullable = false)
  String phone;

  @Builder
  public User(
      String username,
      String password,
      String email,
      String name,
      String phone) {
    this.username = username;
    this.name = name;
    this.phone = phone;
    this.password = password;
    this.email = email;
  }

}
