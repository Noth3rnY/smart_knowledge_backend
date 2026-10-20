package com.smartknowledge.backend.backup.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartknowledge.backend.backup.entity.Backups;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BackupsMapper extends BaseMapper<Backups> {}
