package org.workfitai.userservice.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Base interface để tái sử dụng cho update partial
 */
public interface BaseMapper<Req, Entity, Res> {
  Entity toEntity(Req dto);

  Res toResponse(Entity entity);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  void updateEntity(Req dto, @MappingTarget Entity entity);
}
