package com.global.response;

import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PageResult<T> {

  final List<T> content;
  final Integer pageNumber;
  final Integer pageSize;
  final Long totalElements;
  final Integer totalPages;
  final Boolean isFirst;
  final Boolean isLast;

  @Builder
  public PageResult(Page<T> page) {
    this.content = page.getContent();
    this.pageNumber = page.getNumber();
    this.pageSize = page.getSize();
    this.totalElements = page.getTotalElements();
    this.totalPages = page.getTotalPages();
    this.isFirst = page.isFirst();
    this.isLast = page.isLast();
  }

}
